package dev.iptv.player.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.ButtonDefaults

/** TV Material handles remote keys; add the same action for mouse and touchscreen input. */
@Composable
fun PointerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.tv.material3.Button(
        onClick = onClick,
        modifier = modifier.then(if (enabled) Modifier.pointerClick(onClick) else Modifier),
        enabled = enabled,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun PointerOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.tv.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.then(if (enabled) Modifier.pointerClick(onClick) else Modifier),
        enabled = enabled,
        contentPadding = contentPadding,
        content = content,
    )
}
