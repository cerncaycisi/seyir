package dev.iptv.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import dev.iptv.player.data.Stream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Palette {
    val Background = Color(0xFF080B10)
    val Surface = Color(0xFF111620)
    val SurfaceHigh = Color(0xFF1B2230)
    val Border = Color(0xFF2A3344)
    val Primary = Color(0xFF5B8CFF)
    val Accent = Color(0xFF34D1BF)
    val Live = Color(0xFFFF4D5E)
    val Gold = Color(0xFFFFC857)
    val Text = Color(0xFFE8ECF2)
    val TextMuted = Color(0xFF9AA5B4)
    val TextFaint = Color(0xFF6B7687)
}

private val cardShape = RoundedCornerShape(12.dp)
private val tileGradients = listOf(
    Color(0xFF2B3A67) to Color(0xFF161E36),
    Color(0xFF1F4E5A) to Color(0xFF10282E),
    Color(0xFF4A2B5E) to Color(0xFF22142E),
    Color(0xFF5A3B1F) to Color(0xFF2C1C0E),
    Color(0xFF23503A) to Color(0xFF11281C),
)

fun tileBrush(seed: String): Brush {
    val (a, b) = tileGradients[(seed.hashCode() and 0x7fffffff) % tileGradients.size]
    return Brush.linearGradient(listOf(a, b))
}

fun initials(name: String): String =
    name.split(' ', '-', '_', '.')
        .filter { w -> w.firstOrNull()?.isLetterOrDigit() == true }
        .take(2)
        .joinToString("") { it.take(1) }
        .uppercase()
        .ifEmpty { "TV" }

fun formatClock(epochSeconds: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))

@Composable
fun BrandMark(size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Palette.Primary, Palette.Accent))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Palette.SurfaceHigh, contentColor: Color = Palette.Text) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Palette.Primary) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
fun LogoTile(name: String, url: String?, modifier: Modifier = Modifier, textStyle: TextStyle = MaterialTheme.typography.titleMedium) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(if (loaded) Brush.linearGradient(listOf(Palette.SurfaceHigh, Palette.SurfaceHigh)) else tileBrush(name)),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) Text(initials(name), color = Color.White.copy(alpha = 0.85f), style = textStyle, fontWeight = FontWeight.Bold)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        }
    }
}

@Composable
fun PosterCard(
    stream: Stream,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 100.dp,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(width).aspectRatio(2f / 3f).onFocusChanged { if (it.isFocused || it.hasFocus) onFocused() },
        shape = CardDefaults.shape(cardShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Palette.Primary), shape = cardShape)),
        colors = CardDefaults.colors(containerColor = Palette.Surface),
    ) {
        var loaded by remember(stream.logo) { mutableStateOf(false) }
        Box(Modifier.fillMaxSize()) {
            if (!loaded) {
                Box(Modifier.fillMaxSize().background(tileBrush(stream.name)).padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stream.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (stream.logo != null) {
                AsyncImage(
                    model = stream.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { loaded = it is AsyncImagePainter.State.Success },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            stream.rating?.let { Pill("★ $it", Modifier.align(Alignment.TopStart).padding(6.dp), color = Color(0xCC000000), contentColor = Palette.Gold) }
            if (favorite) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = Palette.Gold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp),
                )
            }
        }
    }
}

@Composable
fun ChannelCard(
    stream: Stream,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(168.dp).aspectRatio(16f / 10f).onFocusChanged { if (it.isFocused || it.hasFocus) onFocused() },
        shape = CardDefaults.shape(cardShape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Palette.Primary), shape = cardShape)),
        colors = CardDefaults.colors(containerColor = Palette.Surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            LogoTile(stream.name, stream.logo, Modifier.fillMaxSize(), MaterialTheme.typography.headlineMedium)
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(stream.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ChannelRow(
    number: Int,
    stream: Stream,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(56.dp).onFocusChanged { if (it.isFocused || it.hasFocus) onFocused() },
        shape = ClickableSurfaceDefaults.shape(cardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.SurfaceHigh,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.Primary), shape = cardShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$number",
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextFaint,
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.width(8.dp))
            LogoTile(stream.name, stream.logo, Modifier.size(width = 60.dp, height = 36.dp), MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(12.dp))
            Text(
                stream.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (favorite) Icon(Icons.Rounded.Star, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun CategoryItem(name: String, count: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(42.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.Surface else Color.Transparent,
            contentColor = if (selected) Palette.Text else Palette.TextMuted,
            focusedContainerColor = Palette.Primary,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (selected) Palette.Primary else Color.Transparent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            count?.let { Text("$it", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
fun Hero(title: String, image: String?, meta: List<String>, plot: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(190.dp)) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.62f),
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to Palette.Background,
                    0.4f to Palette.Background,
                    0.75f to Palette.Background.copy(alpha = 0.4f),
                    1f to Palette.Background.copy(alpha = 0.1f),
                ),
            ),
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Palette.Background)))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.55f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meta.forEach { Pill(it) }
                }
            }
            plot?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = Palette.TextFaint)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Palette.Surface), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ErrorState(message: String) = EmptyState(Icons.Rounded.ErrorOutline, "Yüklenemedi", message)

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp)) {
            drawArc(Color(0x22FFFFFF), 0f, 360f, false, style = Stroke(5.dp.toPx()))
            drawArc(Palette.Primary, angle, 90f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
