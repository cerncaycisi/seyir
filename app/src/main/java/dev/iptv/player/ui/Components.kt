package dev.iptv.player.ui

import dev.iptv.player.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
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
    // Design v2 accent (amber); was blue 0xFF5B8CFF.
    val Primary = Color(0xFFF2B544)
    // Text on an amber or white fill.
    val OnLight = Color(0xFF07080A)
    val Accent = Color(0xFF34D1BF)
    val Live = Color(0xFFFF4D5E)
    val Gold = Color(0xFFFFC857)
    val Text = Color(0xFFE8ECF2)
    val TextMuted = Color(0xFF9AA5B4)
    val TextFaint = Color(0xFF6B7687)
}

const val WATCHED_FRACTION = 0.95f

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

@Composable
fun formatClock(epochSeconds: Long): String {
    val locale = LocalContext.current.resources.configuration.locales[0]
    val pattern = if (LocalClock24h.current) "HH:mm" else "h:mm a"
    return SimpleDateFormat(pattern, locale).format(Date(epochSeconds * 1000))
}

@Composable
fun BrandMark(size: Dp = 40.dp) {
    // Seyir mark: dark obsidian squircle with warm amber border, golden cinema screen portal arc, and ivory-gold rounded play wedge.
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val cr = androidx.compose.ui.geometry.CornerRadius(s * 0.28f)
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF181E2C), Color(0xFF090C12))),
            cornerRadius = cr,
        )
        drawRoundRect(
            color = Color(0x5AF2B544),
            cornerRadius = cr,
            style = Stroke(width = (s * 0.032f).coerceAtLeast(1f)),
        )
        val fx = s * 0.21f
        val fy = s * 0.25f
        val fw = s * 0.50f
        val fh = s * 0.50f
        val fr = s * 0.13f
        val endX = fx + fw * 0.76f
        val portal = androidx.compose.ui.graphics.Path().apply {
            moveTo(endX, fy)
            lineTo(fx + fr, fy)
            quadraticTo(fx, fy, fx, fy + fr)
            lineTo(fx, fy + fh - fr)
            quadraticTo(fx, fy + fh, fx + fr, fy + fh)
            lineTo(endX, fy + fh)
        }
        drawPath(
            path = portal,
            brush = Brush.linearGradient(listOf(Color(0xFFFFCF60), Color(0xFFD98A1B))),
            style = Stroke(
                width = s * 0.092f,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        // Normalized from the 60-unit safe zone in ic_launcher_foreground.xml
        fun nx(v: Float) = ((v - 24f) / 60f) * s
        fun ny(v: Float) = ((v - 24f) / 60f) * s
        val play = androidx.compose.ui.graphics.Path().apply {
            moveTo(nx(50.39f), ny(45.79f))
            quadraticTo(nx(50.39f), ny(42.07f), nx(53.66f), ny(43.83f))
            lineTo(nx(69.29f), ny(52.24f))
            quadraticTo(nx(72.57f), ny(54.0f), nx(69.29f), ny(55.76f))
            lineTo(nx(53.66f), ny(64.17f))
            quadraticTo(nx(50.39f), ny(65.93f), nx(50.39f), ny(62.21f))
            close()
        }
        drawPath(
            path = play,
            brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFDF9E))),
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Palette.SurfaceHigh, contentColor: Color = Palette.Text) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF2B544),
    trackColor: Color = Color(0x33FFFFFF),
) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(trackColor)) {
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
    /** Watch progress 0..1 (null = never watched); >= 0.95 shows a "watched" check instead of the bar. */
    progress: Float? = null,
    /** Small label at the bottom start (e.g. last watched episode). */
    badge: String? = null,
) {
    val uiContext = LocalContext.current
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(width).aspectRatio(2f / 3f).pointerClick(onClick, onLongClick).onFocusChanged { if (it.isFocused || it.hasFocus) onFocused() },
        shape = CardDefaults.shape(cardShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color(0xFFF5F5F7)), shape = cardShape)),
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
            if (progress != null) {
                if (progress >= WATCHED_FRACTION) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = uiContext.getString(R.string.ui_watched),
                        tint = Palette.Accent,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp),
                    )
                } else if (progress > 0.01f) {
                    ProgressBar(progress, Modifier.align(Alignment.BottomCenter).padding(horizontal = 6.dp, vertical = 6.dp))
                }
            }
            if (badge != null) Pill(badge, Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = if (progress != null && progress > 0.01f && progress < WATCHED_FRACTION) 14.dp else 6.dp), color = Color(0xCC000000), contentColor = Color.White)
            if (favorite) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = uiContext.getString(R.string.ui_favorite),
                    tint = Palette.Gold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp),
                )
            }
        }
    }
}

/** List-view cell for Movies/Series: small poster, title, year · rating · genre, plot and progress. */
@Composable
fun PosterListRow(
    stream: Stream,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    badge: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(112.dp).pointerClick(onClick, onLongClick).onFocusChanged { if (it.isFocused || it.hasFocus) onFocused() },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.03f),
        colors = CardDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Color(0xFF07080A),
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            var loaded by remember(stream.logo) { mutableStateOf(false) }
            Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(tileBrush(stream.name))) {
                if (stream.logo != null) {
                    AsyncImage(
                        model = stream.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onState = { loaded = it is AsyncImagePainter.State.Success },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (!loaded) {
                    Text(initials(stream.name), Modifier.align(Alignment.Center), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        titleWithoutYear(stream.name, stream.year),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (favorite) Icon(Icons.Rounded.Star, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(16.dp))
                    if (progress != null && progress >= WATCHED_FRACTION) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
                }
                val meta = listOfNotNull(stream.year, stream.rating?.let { "★ $it" }, stream.genre?.substringBefore(',')?.trim(), badge)
                    .filter { it.isNotBlank() }.joinToString("  ·  ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                stream.plot?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (progress != null && progress > 0.01f && progress < WATCHED_FRACTION) {
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(progress, Modifier.fillMaxWidth(0.6f))
                }
            }
        }
    }
}

/** Resolves [stream]'s provider logo, falling back to XMLTV `<channel><icon src="..."/>` only when missing or broken. */
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun rememberChannelLogo(stream: Stream): Pair<String?, () -> Unit> {
    val context = LocalContext.current
    val primary = stream.logo?.takeIf { it.isNotBlank() }
    var primaryFailed by remember(stream.id, primary) { mutableStateOf(primary == null) }
    val epgLogo by androidx.compose.runtime.produceState<String?>(null, stream.id, primaryFailed) {
        value = if (primaryFailed) runCatching { dev.iptv.player.epg.EpgService.get(context).channelIcon(stream) }.getOrNull() else null
    }
    val active = if (!primaryFailed) primary else epgLogo
    return active to { if (!primaryFailed) primaryFailed = true }
}

/** 16:9 live channel card for the Home row: clean dark surface with centered logo (or dim initials badge) and channel name below. */
@Composable
fun ChannelCard(
    stream: Stream,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiContext = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val (logoUrl, onLogoError) = rememberChannelLogo(stream)
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .pointerClick(onClick, onLongClick)
                .onFocusChanged {
                    focused = it.isFocused || it.hasFocus
                    if (focused) onFocused()
                },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Palette.SurfaceHigh, focusedContainerColor = Palette.SurfaceHigh),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color(0xFFF5F5F7)), shape = shape)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!logoLoaded) {
                    Box(
                        Modifier
                            .size(width = 72.dp, height = 42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x1AF5F5F7)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initials(stream.name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xB3F5F5F7),
                            maxLines = 1,
                        )
                    }
                }
                if (!logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onState = {
                            if (it is AsyncImagePainter.State.Success) logoLoaded = true
                            else if (it is AsyncImagePainter.State.Error) onLogoError()
                        },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                }
                if (focused) {
                    Pill(
                        uiContext.getString(R.string.ui_live),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        color = Palette.Live,
                        contentColor = Color.White,
                    )
                }
            }
        }
        Text(
            stream.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) Palette.Text else Palette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    nowTitle: String? = null,
    progress: Float? = null,
) {
    val uiContext = LocalContext.current
    var hasFocus by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(60.dp).pointerClick(onClick, onLongClick).onFocusChanged {
            val focused = it.isFocused || it.hasFocus
            hasFocus = focused
            if (focused) onFocused()
        },
        shape = ClickableSurfaceDefaults.shape(rowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.Text,
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Color(0xFF07080A),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$number",
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (hasFocus) FontWeight.Bold else FontWeight.SemiBold,
                color = LocalContentColor.current.copy(alpha = 0.5f),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            // Missing or broken logo: try XMLTV channel icon, else show a dim initials tile.
            val (logoUrl, onLogoError) = rememberChannelLogo(stream)
            var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
            Box(
                Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(6.dp))
                    .then(if (logoLoaded) Modifier else Modifier.background(LocalContentColor.current.copy(alpha = 0.10f))),
                contentAlignment = Alignment.Center,
            ) {
                if (!logoLoaded) {
                    Text(
                        initials(stream.name),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LocalContentColor.current.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
                if (!logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onState = {
                            if (it is AsyncImagePainter.State.Success) logoLoaded = true
                            else if (it is AsyncImagePainter.State.Error) onLogoError()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stream.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (hasFocus) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (hasFocus) {
                        Pill(uiContext.getString(R.string.ui_live), color = Palette.Live, contentColor = Color.White)
                    }
                }
                if (nowTitle != null) {
                    Text(
                        nowTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (progress != null) {
                        Spacer(Modifier.height(3.dp))
                        ProgressBar(
                            fraction = progress,
                            modifier = Modifier.height(3.dp),
                            color = if (hasFocus) Color(0xFF07080A) else Color(0xFFF2B544),
                            trackColor = if (hasFocus) Color(0xFFD9D9DE) else Color(0x33FFFFFF),
                        )
                    }
                }
            }
            if (favorite) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Star, contentDescription = uiContext.getString(R.string.ui_favorite), tint = Color(0xFFF2B544), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun CategoryItem(
    name: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    locked: Boolean = false,
) {
    val uiContext = LocalContext.current
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(48.dp).pointerClick(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.Surface else Color.Transparent,
            contentColor = if (selected) Palette.Text else Palette.TextMuted,
            focusedContainerColor = Color(0xFFF5F5F7),
            focusedContentColor = Color(0xFF07080A),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (selected) Color(0xFFF2B544) else Color.Transparent),
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
            if (locked) Icon(Icons.Rounded.Lock, contentDescription = uiContext.getString(R.string.ui_locked), modifier = Modifier.size(16.dp))
            count?.let { Text("$it", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
fun Hero(title: String, image: String?, meta: List<String>, plot: String?, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(modifier.fillMaxWidth().height(if (compact) 136.dp else 190.dp)) {
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
                    meta.take(if (compact) 3 else meta.size).forEach { Pill(it) }
                }
            }
            plot?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    maxLines = if (compact) 1 else 2,
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
fun ErrorState(message: String) = EmptyState(Icons.Rounded.ErrorOutline, stringResource(R.string.ui_load_failed), message)

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

/** 16:9 still card for "continue watching": backdrop, thin amber progress, title (and episode badge) below. */
@Composable
fun LandscapeCard(
    stream: Stream,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    badge: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).pointerClick(onClick, onLongClick)
                .onFocusChanged { focused = it.isFocused || it.hasFocus; if (focused) onFocused() },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Palette.SurfaceHigh, focusedContainerColor = Palette.SurfaceHigh),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        ) {
            Box(Modifier.fillMaxSize()) {
                val image = stream.backdrop ?: stream.logo
                if (image != null) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(tileBrush(stream.name)), contentAlignment = Alignment.Center) {
                        Text(stream.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
                    }
                }
                badge?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (progress != null && progress > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x40FFFFFF))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Color(0xFFF2B544)))
                    }
                }
            }
        }
        Text(stream.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = if (focused) Palette.Text else Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Design v2 facts line: amber "★ 7.8" then muted, dot-separated facts (no pills). */
@Composable
fun MetaLine(rating: String?, parts: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        rating?.takeIf { it.isNotBlank() }?.let {
            Text("★ $it", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFF2B544))
        }
        Text(parts.filter { it.isNotBlank() }.joinToString("  ·  "), style = MaterialTheme.typography.labelLarge,
            color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Displays a transparent TMDB title logo when decoded cleanly; shows styled title text immediately and on miss. */
@Composable
fun TitleLogoOrText(
    title: String,
    logoUrl: String?,
    textStyle: TextStyle,
    maxLogoHeight: Dp,
    maxLogoWidth: Dp,
    maxLines: Int = 2,
    modifier: Modifier = Modifier,
) {
    var loaded by remember(logoUrl) { mutableStateOf(false) }
    var failed by remember(logoUrl) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (!loaded) {
            Text(
                title,
                style = textStyle,
                fontWeight = FontWeight.ExtraBold,
                color = Palette.Text,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (logoUrl != null && !failed) {
            AsyncImage(
                model = logoUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onSuccess = { loaded = true },
                onError = { failed = true; loaded = false },
                modifier = Modifier
                    .widthIn(max = maxLogoWidth)
                    .heightIn(max = maxLogoHeight)
                    .alpha(if (loaded) 1f else 0f),
            )
        }
    }
}

/** "Göz Açıp Kapayıncaya Kadar (2021)" → "Göz Açıp Kapayıncaya Kadar" when [year] is shown next to it anyway. */
internal fun titleWithoutYear(name: String, year: String?): String {
    val y = year?.trim()?.takeIf { it.length == 4 } ?: return name
    return name.replace(Regex("""\s*[(\[]\s*$y\s*[)\]]\s*$"""), "").ifBlank { name }
}
