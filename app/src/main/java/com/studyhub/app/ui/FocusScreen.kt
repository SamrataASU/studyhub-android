package com.studyhub.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.studyhub.app.focus.FocusController
import com.studyhub.app.focus.FocusState
import com.studyhub.app.focus.Growth
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// MARK: Card on the Today screen

@Composable
fun FocusCard(focus: FocusController) {
    var preset by rememberSaveable { mutableIntStateOf(25) }
    var custom by rememberSaveable { mutableIntStateOf(40) }
    val minutes = if (preset == 0) custom else preset
    val today = remember { focus.minutesToday() }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Eco, contentDescription = null, tint = Palette.Mint)
            Spacer(Modifier.width(8.dp))
            Text("Focus", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (today > 0) Text("$today min today 🔥", color = Palette.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Plant a seed and watch it grow while you study. 25 min grows a plant, 50 a tree, keep going for a forest.",
            color = Palette.Muted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetChip("25 min · Pomodoro", preset == 25) { preset = 25 }
            PresetChip("50 min", preset == 50) { preset = 50 }
            PresetChip("Custom", preset == 0) { preset = 0 }
        }
        if (preset == 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$custom minutes", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { custom = maxOf(5, custom - 5) }) { Text("−5") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { custom = minOf(180, custom + 5) }) { Text("+5") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { focus.start(minutes) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Mint.copy(alpha = 0.85f), contentColor = Color(0xFF0B2A1E)),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Start $minutes-minute focus", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else Palette.Mint,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Mint.copy(alpha = 0.55f) else Palette.Mint.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

// MARK: Full-screen focus

@Composable
fun FocusScreen(focus: FocusController, s: FocusState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            focus.tick(now)
            delay(1000)
        }
    }
    BackHandler { /* Stay in focus. Use End to leave. */ }

    // Full screen, screen stays on.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val transition = rememberInfiniteTransition(label = "breeze")
    val sway by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )

    val totalMinutes = s.total(now) / 60_000.0

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D0F29), Color(0xFF1F1A4D).copy(alpha = 1f)),
                )
            )
    ) {
        val landscape = maxWidth > maxHeight
        SkyDecor(Modifier.fillMaxSize())
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                GrowthScene(totalMinutes, sway, Modifier.fillMaxHeight().weight(0.6f))
                Box(Modifier.fillMaxHeight().weight(0.4f), contentAlignment = Alignment.Center) {
                    FocusPanel(focus, s, now, totalMinutes, compact = true)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                GrowthScene(totalMinutes, sway, Modifier.fillMaxWidth().weight(0.58f))
                Box(Modifier.fillMaxWidth().weight(0.42f), contentAlignment = Alignment.Center) {
                    FocusPanel(focus, s, now, totalMinutes, compact = false)
                }
            }
        }

        val breakEnd = s.breakEnd
        when {
            breakEnd != null -> BreakCard(focus, breakEnd, now)
            s.showBreakPrompt -> PromptCard(
                emoji = "🌲",
                title = "You've been focusing for 1 hour 50 minutes",
                message = "Amazing work. Take a 10-minute break so you come back sharper.\n\n“${Growth.breakQuotes[(now / 60_000L % Growth.breakQuotes.size).toInt()]}”",
                primary = "Start 10-minute break" to { focus.startBreak() },
                secondary = "Keep focusing" to { focus.keepFocusing() },
            )
            s.finished(now) -> PromptCard(
                emoji = "🎉",
                title = "Session complete!",
                message = "You focused for ${totalMinutes.toInt()} minutes. ${Growth.title(totalMinutes)}. Keep going to grow it bigger.",
                primary = "+25 more minutes" to { focus.extend(25) },
                secondary = "Finish" to { focus.finish() },
                extra = if (s.blockMinutes != 25) "+${s.blockMinutes} more minutes" to { focus.extend(s.blockMinutes) } else null,
            )
        }
    }
}

@Composable
private fun FocusPanel(focus: FocusController, s: FocusState, now: Long, totalMinutes: Double, compact: Boolean) {
    val quote = Growth.quotes[(totalMinutes.toInt() / 5) % Growth.quotes.size]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(Growth.title(totalMinutes), color = Color.White, fontSize = if (compact) 20.sp else 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(Growth.next(totalMinutes), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Text(
            clock(s.blockLeft(now)),
            color = Color.White.copy(alpha = 0.42f),
            fontSize = if (compact) 52.sp else 64.sp,
            fontWeight = FontWeight.Light,
        )
        Text(
            "${s.blockMinutes}-min block · ${totalMinutes.toInt()} min focused",
            color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp,
        )
        Text(
            quote, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp,
            fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.alpha(0.75f)) {
            if (!s.finished(now)) {
                OutlinedButton(onClick = { if (s.running) focus.pause() else focus.resume() }) {
                    Text(if (s.running) "Pause" else "Resume", color = Color.White)
                }
            }
            OutlinedButton(onClick = { focus.finish() }) { Text("End", color = Color.White) }
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms + 999) / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
private fun PromptCard(
    emoji: String,
    title: String,
    message: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
    extra: Pair<String, () -> Unit>? = null,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF221B45))
                .padding(24.dp),
        ) {
            Text(emoji, fontSize = 42.sp)
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, color = Palette.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            Button(
                onClick = primary.second,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Mint.copy(alpha = 0.85f), contentColor = Color(0xFF0B2A1E)),
            ) { Text(primary.first, fontWeight = FontWeight.Bold) }
            if (extra != null) {
                OutlinedButton(onClick = extra.second, modifier = Modifier.fillMaxWidth()) { Text(extra.first, color = Color.White) }
            }
            TextButton(onClick = secondary.second) { Text(secondary.first, color = Palette.Muted) }
        }
    }
}

@Composable
private fun BreakCard(focus: FocusController, breakEnd: Long, now: Long) {
    val left = maxOf(0L, breakEnd - now)
    PromptCard(
        emoji = "☕",
        title = if (left > 0) "Break time · ${clock(left)}" else "Break's over!",
        message = "“${Growth.breakQuotes[((breakEnd / 1000) % Growth.breakQuotes.size).toInt()]}”",
        primary = (if (left > 0) "I'm ready, back to focus" else "Back to focus") to { focus.backFromBreak() },
        secondary = "Finish for now" to { focus.finish() },
    )
}

// MARK: The scene

@Composable
private fun SkyDecor(modifier: Modifier) {
    Canvas(modifier) {
        val stars = listOf(
            Triple(0.12f, 0.08f, 1.6f), Triple(0.3f, 0.15f, 1.1f), Triple(0.55f, 0.06f, 1.4f), Triple(0.78f, 0.12f, 1.8f),
            Triple(0.9f, 0.22f, 1.2f), Triple(0.22f, 0.28f, 1.0f), Triple(0.66f, 0.24f, 1.3f), Triple(0.43f, 0.32f, 0.9f),
        )
        stars.forEach { (x, y, r) ->
            drawCircle(Color.White.copy(alpha = 0.55f), radius = r * density, center = Offset(x * size.width, y * size.height))
        }
        val moon = Offset(size.width * 0.85f, size.height * 0.09f)
        drawCircle(Color.White.copy(alpha = 0.04f), radius = 34.dp.toPx(), center = moon)
        drawCircle(Color(0xFFFFF2D9).copy(alpha = 0.85f), radius = 17.dp.toPx(), center = moon)
    }
}

private data class Extra(val x: Float, val scale: Float, val start: Double)

private val extras = listOf(
    Extra(-0.22f, 0.72f, 54.0), Extra(0.22f, 0.75f, 64.0), Extra(-0.36f, 0.6f, 78.0),
    Extra(0.36f, 0.62f, 90.0), Extra(-0.08f, 0.5f, 104.0), Extra(0.1f, 0.52f, 112.0),
)

@Composable
private fun GrowthScene(minutes: Double, sway: Float, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ground = h * 0.82f

        // Extra trees: tree → grove → forest (behind the hill line)
        extras.forEach { e ->
            val g = ((minutes - e.start) / 24.0).coerceIn(0.0, 1.0)
            if (g > 0) {
                val base = Offset(w / 2 + e.x * w, ground - h * 0.05f * (1 - e.scale))
                drawPlant(base, h * 0.62f * e.scale, g, sway, 0.78f)
            }
        }

        // Hill
        val hill = Path().apply {
            moveTo(0f, ground + h * 0.02f)
            quadraticTo(w / 2, ground - h * 0.1f, w, ground + h * 0.02f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            hill,
            Brush.verticalGradient(listOf(Color(0xFF33805C), Color(0xFF0F3329)), startY = ground - h * 0.1f, endY = h),
        )

        // Main plant: seed → plant (25 min) → tree (50 min)
        val p = (minutes / 50.0).coerceIn(0.0, 1.0)
        drawPlant(Offset(w / 2, ground - h * 0.055f), h * 0.68f, p, sway, 1f)
    }
}

/** p: 0 = seed, 0.5 = full plant (25 min), 1 = full tree (50 min). */
private fun DrawScope.drawPlant(base: Offset, maxH: Float, p: Double, sway: Float, dim: Float) {
    val soil = Color(0xFF52382A)
    val leaf = Color(0xFF5CD980).copy(alpha = dim)
    val leafDark = Color(0xFF298C52).copy(alpha = dim)
    val trunk = Color(0xFF734D2E).copy(alpha = dim)

    val moundW = maxH * 0.22f
    drawOval(soil.copy(alpha = dim), topLeft = Offset(base.x - moundW / 2, base.y - moundW * 0.12f), size = Size(moundW, moundW * 0.24f))

    if (p < 0.06) {
        val s = maxH * 0.05f
        val bob = sway * 1.5f
        drawOval(Color(0xFFB38047).copy(alpha = dim), topLeft = Offset(base.x - s / 2, base.y - s * 0.9f + bob), size = Size(s, s * 0.75f))
        return
    }

    val breeze = sway * 0.025f
    val height = if (p < 0.5) maxH * (0.08f + 0.42f * ((p - 0.06) / 0.44).toFloat())
    else maxH * (0.5f + 0.35f * ((p - 0.5) / 0.5).toFloat())
    val top = Offset(base.x + height * breeze, base.y - height)

    val stem = Path().apply {
        moveTo(base.x, base.y)
        quadraticTo(base.x - height * 0.04f, base.y - height * 0.55f, top.x, top.y)
    }
    val stemWidth = if (p < 0.5) maxOf(5f, maxH * 0.012f) else maxH * (0.014f + 0.05f * ((p - 0.5) / 0.5).toFloat())
    drawPath(stem, if (p < 0.5) leafDark else trunk, style = Stroke(width = stemWidth, cap = StrokeCap.Round))

    if (p < 0.5) {
        val pairs = 1 + ((p - 0.06) / 0.44 * 5).toInt()
        for (i in 0 until pairs) {
            val t = (i + 1).toFloat() / (pairs + 1)
            val at = Offset(base.x + (top.x - base.x) * t, base.y - height * t)
            val len = maxH * 0.09f * (0.7f + (1 - t) * 0.5f)
            drawLeaf(at, -0.6 + breeze * 4, len, if (i % 2 == 0) leaf else leafDark)
            drawLeaf(at, PI + 0.6 + breeze * 4, len, if (i % 2 == 0) leafDark else leaf)
        }
        drawLeaf(top, -PI / 2 + breeze * 4, maxH * 0.07f, leaf)
    } else {
        val c = ((p - 0.5) / 0.5).toFloat()
        val r = maxH * (0.1f + 0.2f * c)
        listOf(-1f, 1f).forEach { side ->
            val from = Offset(base.x + (top.x - base.x) * 0.7f, base.y - height * 0.7f)
            drawLine(trunk, from, Offset(from.x + side * r * 0.7f, from.y - r * 0.5f), strokeWidth = stemWidth * 0.5f, cap = StrokeCap.Round)
        }
        val blobs = listOf(
            Triple(Offset(-0.65f, 0.2f), 0.75f, leafDark), Triple(Offset(0.65f, 0.2f), 0.75f, leafDark),
            Triple(Offset(0f, 0.35f), 0.8f, leafDark), Triple(Offset(-0.35f, -0.25f), 0.8f, leaf),
            Triple(Offset(0.35f, -0.25f), 0.8f, leaf), Triple(Offset(0f, -0.05f), 0.95f, leaf),
            Triple(Offset(0f, -0.55f), 0.65f, leaf),
        )
        blobs.forEach { (o, k, color) ->
            drawCircle(color, radius = r * k, center = Offset(top.x + o.x * r, top.y + o.y * r))
        }
        if (c > 0.85f) {
            listOf(Offset(-0.5f, -0.2f), Offset(0.4f, -0.45f), Offset(0.1f, 0.25f), Offset(-0.15f, -0.6f), Offset(0.6f, 0.1f)).forEach { o ->
                drawCircle(Color(0xFFFF9EBF).copy(alpha = dim), radius = 3.5f * density, center = Offset(top.x + o.x * r, top.y + o.y * r))
            }
        }
    }
}

private fun DrawScope.drawLeaf(from: Offset, angle: Double, length: Float, color: Color) {
    val tip = Offset(from.x + cos(angle).toFloat() * length, from.y + sin(angle).toFloat() * length)
    val mid = Offset((from.x + tip.x) / 2, (from.y + tip.y) / 2)
    val nx = -sin(angle).toFloat() * length * 0.32f
    val ny = cos(angle).toFloat() * length * 0.32f
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(mid.x + nx, mid.y + ny, tip.x, tip.y)
        quadraticTo(mid.x - nx, mid.y - ny, from.x, from.y)
    }
    drawPath(path, color)
}
