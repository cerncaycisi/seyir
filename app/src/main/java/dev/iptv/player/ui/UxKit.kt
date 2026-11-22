package dev.iptv.player.ui

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.iptv.player.data.ProfileStore

/**
 * tv-material's Surface/Card/Button react to D-pad only. This adds pointer (mouse / touch) tap and
 * long-press on top without touching key handling.
 */
fun Modifier.pointerClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = { click() },
            onLongPress = { long?.invoke() },
        )
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    primary: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    val base = if (destructive) Palette.Live else Palette.Primary
    Surface(
        onClick = onClick,
        modifier = modifier.height(46.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) base.copy(alpha = 0.85f) else Palette.SurfaceHigh,
            contentColor = if (primary && !destructive) Palette.OnLight else Palette.Text,
            // Design v2: white focus with dark text; destructive stays red.
            focusedContainerColor = if (destructive) base else Color(0xFFF5F5F7),
            focusedContentColor = if (destructive) Color.White else Palette.OnLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp).height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

class CardAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Long-press action card. The OK release of the long press that opened it is swallowed (a click needs a
 * fresh OK press), so focus can start on the first action; with a destructive action it starts on Cancel.
 */
@Composable
fun ActionCard(
    title: String,
    subtitle: String? = null,
    actions: List<CardAction>,
    cancelLabel: String = stringResource(R.string.ui_cancel),
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    val firstIndex = actions.indexOfFirst { it.enabled }
    val startOnCancel = firstIndex < 0 || actions.any { it.destructive }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { (if (startOnCancel) cancelFocus else firstFocus).requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(380.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Surface).padding(24.dp)
                .onPreviewKeyEvent { e ->
                    val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    // Auto-repeat downs of the still-held long press must not arm it.
                    if (ok && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) armed = true
                    ok && e.type == KeyEventType.KeyUp && !armed
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 2)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            Spacer(Modifier.height(4.dp))
            actions.forEachIndexed { i, a ->
                if (a.enabled) {
                    TvButton(a.label, { a.onClick() }, Modifier.fillMaxWidth().then(if (i == firstIndex) Modifier.focusRequester(firstFocus) else Modifier), a.icon, a.destructive)
                } else {
                    Text(a.label, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            TvButton(cancelLabel, onDismiss, Modifier.fillMaxWidth().focusRequester(cancelFocus))
        }
    }
}

/** Destructive confirmation; focus starts on Cancel. */
@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ActionCard(
        title = title,
        subtitle = message,
        actions = listOf(CardAction(confirmLabel, destructive = true, onClick = onConfirm)),
        onDismiss = onDismiss,
    )
}

/** 4-digit PIN pad; works with D-pad (grid of keys) and with the number keys of a remote/keyboard. */
@Composable
fun PinDialog(title: String, subtitle: String? = null, error: String? = null, onComplete: (String) -> Unit, onDismiss: () -> Unit) {
    val uiContext = LocalContext.current
    var digits by remember { mutableStateOf("") }
    val start = remember { FocusRequester() }
    val onCompleteNow by rememberUpdatedState(onComplete)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { start.requestFocus() }
    }
    fun press(d: Char) {
        if (digits.length >= 4) return
        digits += d
        if (digits.length == 4) {
            val pin = digits
            digits = ""
            onCompleteNow(pin)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Surface)
                .padding(24.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val d = digitOf(e.key)
                    when {
                        d != null -> { press(d); true }
                        e.key == Key.Backspace || e.key == Key.Delete -> { digits = digits.dropLast(1); true }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Palette.Text, textAlign = TextAlign.Center)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.semantics { contentDescription = uiContext.getString(R.string.ui_pin_entered, digits.length) },
            ) {
                repeat(4) { i ->
                    Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < digits.length) Palette.Primary else Palette.SurfaceHigh))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(error ?: " ", style = MaterialTheme.typography.labelMedium, color = Palette.Live)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("123", "456", "789").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c -> PinKey(c.toString(), { press(c) }, if (c == '5') Modifier.focusRequester(start) else Modifier) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinKey(null, { digits = digits.dropLast(1) }, icon = Icons.AutoMirrored.Rounded.Backspace, description = uiContext.getString(R.string.ui_delete))
                    PinKey("0", { press('0') })
                    PinKey(uiContext.getString(R.string.ui_cancel_short), onDismiss, small = true)
                }
            }
        }
    }
}

private fun digitOf(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

@Composable
private fun PinKey(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    small: Boolean = false,
    description: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 78.dp, height = 52.dp).pointerClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.SurfaceHigh,
            contentColor = Palette.Text,
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Color(0xFF07080A),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(Modifier.size(width = 78.dp, height = 52.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
            } else {
                Text(
                    label.orEmpty(),
                    style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ---- Settings rows -------------------------------------------------------------------------------

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .pointerClick(onClick)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.DirectionLeft && onLeft != null -> { onLeft(); true }
                    e.key == Key.DirectionRight && onRight != null -> { onRight(); true }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            // Design v2: focused rows turn white with dark text (was a blue ring).
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Palette.OnLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = LocalContentColor.current.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 2)
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val uiContext = LocalContext.current
    SettingRow(
        title, modifier, subtitle, onClick = { onChange(!checked) },
        trailing = {
            Box(
                Modifier.size(width = 46.dp, height = 26.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (checked) Palette.Primary else Palette.Border)
                    .semantics { contentDescription = if (checked) uiContext.getString(R.string.ui_on) else uiContext.getString(R.string.ui_off) },
            ) {
                Box(
                    Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(3.dp).size(20.dp).clip(CircleShape).background(Color.White),
                )
            }
        },
    )
}

@Composable
fun <T> PickerRow(
    title: String,
    options: List<Pair<T, String>>,
    value: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val label = options.firstOrNull { it.first == value }?.second
        ?: options.firstOrNull()?.second.orEmpty()
    var open by remember { mutableStateOf(false) }
    // Open a modal dropdown list on OK/tap instead of cycling on Left/Right so D-pad Left returns to the sidebar.
    SettingRow(
        title,
        modifier,
        subtitle,
        onClick = { open = true },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Amber on the dark row; on the white focused row amber is unreadable, so use the row's dark text.
                Text(label, style = MaterialTheme.typography.titleSmall, color = accentOn(LocalContentColor.current), fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(22.dp))
            }
        },
    )
    if (open) ListPickerDialog(title, options, value, onSelect = { onChange(it); open = false }, onDismiss = { open = false })
}

@Composable
fun AvatarBubble(name: String, colorIndex: Int, size: Dp = 44.dp) {
    val colors = ProfileStore.avatarColors
    Box(Modifier.size(size).clip(CircleShape).background(Color(colors[colorIndex.mod(colors.size)])), contentAlignment = Alignment.Center) {
        Text(initials(name).take(2), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** The amber accent on dark content, or [content] itself when it is dark (a white, focused surface). */
internal fun accentOn(content: Color): Color = if (content.luminance() > 0.5f) Palette.Primary else content

/** Asks for a short name (e.g. a new channel group). The field opens the keyboard on OK, like other TV fields. */
@Composable
fun NameInputDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
) {
    val uiContext = LocalContext.current
    var name by remember(initialValue) { mutableStateOf(initialValue) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { fieldFocus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(420.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Surface).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Palette.Text)
            TvTextField(name, { name = it.take(40) }, placeholder, Modifier.fillMaxWidth().focusRequester(fieldFocus))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(confirmLabel, { if (name.isNotBlank()) onConfirm(name.trim()) }, Modifier.weight(1f), primary = true)
                TvButton(uiContext.getString(R.string.ui_cancel), onDismiss, Modifier.weight(1f))
            }
        }
    }
}
