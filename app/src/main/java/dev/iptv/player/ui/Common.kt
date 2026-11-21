package dev.iptv.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlin.coroutines.cancellation.CancellationException

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

suspend fun <T> loadCatching(block: suspend () -> T): Load<T> = try {
    Load.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Load.Err(e.message ?: e.javaClass.simpleName)
}

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Primary,
            onPrimary = Color.White,
            secondary = Palette.Accent,
            onSurface = Palette.Text,
            onSurfaceVariant = Palette.TextMuted,
            surfaceVariant = Palette.SurfaceHigh,
            border = Palette.Border,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Surface,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        ) { content() }
    }
}

@Composable
fun StatusText(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: ImageVector? = null,
) {
    // On TV a focused text field traps D-pad focus and pops the keyboard, so the field is only
    // editable after OK is pressed; otherwise it behaves like a normal focusable row.
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    var sessionOpen by remember { mutableStateOf(false) }
    val view = LocalView.current
    val rowFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val borderColor = if (focused || editing) Palette.Primary else Palette.Border
    val textStyle = TextStyle(color = Palette.Text, fontSize = 18.sp)
    val transformation = if (password) PasswordVisualTransformation() else VisualTransformation.None

    fun finish(direction: FocusDirection?) {
        editing = false
        restoreFocus = direction == null
        keyboard?.hide()
        if (direction != null) focusManager.moveFocus(direction)
    }

    LaunchedEffect(editing) {
        if (editing) {
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
            sessionOpen = true
        } else {
            if (restoreFocus) runCatching { rowFocus.requestFocus() }
            if (sessionOpen) {
                // Android TV's keyboard keeps its input connection after the field is gone and
                // swallows the remote's OK key (DPAD_CENTER) until the connection is restarted.
                withFrameNanos { }
                view.context.getSystemService(InputMethodManager::class.java)?.restartInput(view)
                sessionOpen = false
            }
        }
    }
    BackHandler(enabled = editing) { finish(null) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused || editing) Palette.SurfaceHigh else Palette.Surface)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .focusRequester(rowFocus)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { editing = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = if (focused || editing) Palette.Primary else Palette.TextFaint)
            Spacer(Modifier.width(12.dp))
        }
        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(Color.White),
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onAny = { finish(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { finish(FocusDirection.Down); true }
                            Key.DirectionUp -> { finish(FocusDirection.Up); true }
                            else -> false
                        }
                    },
            )
        } else {
            val shown = transformation.filter(AnnotatedString(value)).text.text
            Text(shown.ifEmpty { placeholder }, style = textStyle, color = if (value.isEmpty()) Palette.TextFaint else Palette.Text, maxLines = 1)
        }
      }
    }
}
