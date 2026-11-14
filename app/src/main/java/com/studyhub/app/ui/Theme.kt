package com.studyhub.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyhub.app.data.Fmt
import com.studyhub.app.data.ItemKind
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

object Palette {
    val BgTop = Color(0xFF0F0D24)
    val BgBottom = Color(0xFF26144A)
    val Violet = Color(0xFF8C6BFF)
    val Coral = Color(0xFFFF6B78)
    val Amber = Color(0xFFFFBD4D)
    val Sky = Color(0xFF5CB8FF)
    val Mint = Color(0xFF5CE6B3)
    val Card = Color.White.copy(alpha = 0.07f)
    val Stroke = Color.White.copy(alpha = 0.08f)
    val Muted = Color.White.copy(alpha = 0.6f)
}

val ItemKind.icon: ImageVector
    get() = when (this) {
        ItemKind.EXAM -> Icons.Filled.Whatshot
        ItemKind.ASSIGNMENT -> Icons.Filled.Description
        ItemKind.CLASS_EVENT -> Icons.Filled.Book
    }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Violet,
            onPrimary = Color.White,
            secondary = Palette.Sky,
            background = Palette.BgTop,
            surface = Color(0xFF1C1638),
            onSurface = Color.White,
            onBackground = Color.White,
        ),
        content = content,
    )
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.BgBottom)))
    ) { content() }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, Palette.Stroke, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun ScreenHeader(title: String, onSettings: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
        actions()
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Palette.Violet)
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$count",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun KindChip(kind: ItemKind) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(kind.color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(kind.icon, contentDescription = null, tint = kind.color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(kind.label, color = kind.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Live "now", ticking every [everyMs] milliseconds. */
@Composable
fun rememberNow(everyMs: Long = 60_000): Instant {
    val now by produceState(Instant.now(), everyMs) {
        while (true) {
            delay(everyMs)
            value = Instant.now()
        }
    }
    return now
}

@Composable
fun DueBadge(due: Instant) {
    val now = rememberNow()
    val seconds = Duration.between(now, due).seconds
    val color = when {
        seconds < 0 -> Palette.Coral
        seconds < 86_400 -> Palette.Amber
        seconds < 3 * 86_400 -> Palette.Sky
        else -> Color.White.copy(alpha = 0.6f)
    }
    Text(
        Fmt.short(seconds),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun ProgressRing(progress: Float, size: Dp, stroke: Dp, colors: List<Color> = listOf(Palette.Violet, Palette.Sky)) {
    Canvas(Modifier.size(size)) {
        val w = stroke.toPx()
        val arcSize = Size(this.size.width - w, this.size.height - w)
        val topLeft = Offset(w / 2, w / 2)
        drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0f) {
            drawArc(
                Brush.sweepGradient(colors + colors.first()),
                -90f, sweep, false, topLeft, arcSize,
                style = Stroke(w, cap = StrokeCap.Round),
            )
        }
    }
}
