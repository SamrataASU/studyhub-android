@file:OptIn(ExperimentalMaterial3Api::class)

package com.studyhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyhub.app.MainViewModel
import com.studyhub.app.UiState
import com.studyhub.app.data.Fmt
import com.studyhub.app.data.StudyItem
import com.studyhub.app.data.Store
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(state: UiState, vm: MainViewModel, onOpen: (StudyItem) -> Unit, onSettings: () -> Unit) {
    val today = LocalDate.now()
    val todayItems = state.itemsOn(today)
    val weekLimit = Instant.now().plusSeconds(7L * 86_400)
    val nextSeven = state.upcoming.filter { it.due.isBefore(weekLimit) && it.localDate != today }
    val dueSoon = state.upcoming.count { it.due.isBefore(weekLimit) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Today", onSettings)
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { Header(state) }
                if (state.feed.isBlank()) item { SetupCard(vm) }
                item {
                    val next = state.upcoming.firstOrNull()
                    if (next != null) NextUpCard(next) { vm.toggleDone(next) } else AllClearCard()
                }
                item { StatsRow(state, dueSoon) }
                item { FocusTimerCard() }
                item { SectionHeader("Today", todayItems.size) }
                if (todayItems.isEmpty()) item { Muted("Nothing scheduled today.") }
                items(todayItems, key = { "t-" + it.id }) { ItemRow(it, state, vm, onOpen) }
                item { SectionHeader("Next 7 days", nextSeven.size) }
                if (nextSeven.isEmpty()) item { Muted("No deadlines this week. Get ahead!") }
                items(nextSeven, key = { "n-" + it.id }) { ItemRow(it, state, vm, onOpen, showDate = true) }
                if (state.overdue.isNotEmpty()) {
                    item { SectionHeader("Overdue", state.overdue.size) }
                    items(state.overdue, key = { "o-" + it.id }) { ItemRow(it, state, vm, onOpen, showDate = true) }
                }
                item { QuoteCard() }
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun Header(state: UiState) {
    val date = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US).format(LocalDate.now()).uppercase()
    Column(Modifier.padding(top = 4.dp)) {
        Text(date, color = Palette.Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        val greeting = if (state.name.isBlank()) Fmt.greeting() else "${Fmt.greeting()}, ${state.name}"
        Text(greeting, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                state.refreshing -> Text("Syncing…", color = Palette.Muted, fontSize = 12.sp)
                state.error != null -> {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Palette.Amber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Canvas: ${state.error}", color = Palette.Muted, fontSize = 12.sp, maxLines = 1)
                }
                state.lastSynced != null -> {
                    Icon(Icons.Filled.CloudDone, contentDescription = null, tint = Palette.Mint, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Synced ${Fmt.clock(state.lastSynced)}", color = Palette.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SetupCard(vm: MainViewModel) {
    var link by rememberSaveable { mutableStateOf("") }
    GlassCard {
        Text("Connect your Canvas", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Paste your Canvas calendar link (Canvas → Calendar → “Calendar Feed”).",
            color = Palette.Muted, fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            singleLine = true,
            placeholder = { Text("https://…/feeds/calendars/…") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.setFeed(link) },
            enabled = link.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Violet),
        ) { Text("Save") }
    }
}

@Composable
private fun NextUpCard(item: StudyItem, onDone: () -> Unit) {
    val now = rememberNow(30_000)
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Palette.Violet, spotColor = Palette.Violet)
            .clip(shape)
            .background(Brush.linearGradient(listOf(item.kind.color.copy(alpha = 0.95f), Palette.Violet)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NEXT DEADLINE", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f),
            )
            Text(
                item.kind.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(item.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 3)
        item.course?.let { Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1) }
        Spacer(Modifier.height(8.dp))
        Text(Fmt.countdown(item.due, now), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.full(item.due), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AllClearCard() {
    GlassCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = Palette.Mint, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("All caught up", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("No upcoming deadlines. A great time to review notes or read ahead.", color = Palette.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StatsRow(state: UiState, dueSoon: Int) {
    val (done, total) = state.weekProgress
    val status = when {
        total == 0 -> "Free week"
        done == total -> "All done 🎉"
        else -> "${total - done} to go"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        GlassCard(modifier = Modifier.weight(1f).height(90.dp), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    ProgressRing(if (total == 0) 0f else done.toFloat() / total, 56.dp, 8.dp)
                    Text("$done/$total", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("This week", color = Palette.Muted, fontSize = 12.sp)
                    Text(status, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        StatTile("$dueSoon", "Due in 7 days", Palette.Amber)
        StatTile("${state.overdue.size}", "Overdue", if (state.overdue.isEmpty()) Palette.Mint else Palette.Coral)
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color) {
    GlassCard(modifier = Modifier.width(92.dp).height(90.dp), padding = 14.dp) {
        Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(label, color = Palette.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
private fun FocusTimerCard() {
    val context = LocalContext.current
    val todayKey = LocalDate.now().toString()
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var endAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var pausedLeft by rememberSaveable { mutableStateOf<Long?>(null) }
    var count by remember { mutableIntStateOf(Store.focusCountToday(context, todayKey)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(endAt) {
        while (endAt != null) {
            now = System.currentTimeMillis()
            val end = endAt ?: break
            if (now >= end) {
                endAt = null
                pausedLeft = null
                Store.addFocusSession(context, todayKey)
                count = Store.focusCountToday(context, todayKey)
                break
            }
            delay(1000)
        }
    }

    val total = minutes * 60_000L
    val left = endAt?.let { maxOf(0L, it - now) } ?: pausedLeft ?: total
    val secs = (left + 999) / 1000

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(1f - left.toFloat() / total, 72.dp, 7.dp, listOf(Palette.Mint, Palette.Sky))
                Text(String.format(Locale.US, "%d:%02d", secs / 60, secs % 60), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Focus session", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (count == 0) "Start a $minutes-minute sprint" else "$count session${if (count == 1) "" else "s"} done today 🔥",
                    color = Palette.Muted, fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val end = endAt
                            if (end != null) {
                                pausedLeft = maxOf(0L, end - System.currentTimeMillis())
                                endAt = null
                            } else {
                                endAt = System.currentTimeMillis() + (pausedLeft ?: total)
                                pausedLeft = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Violet),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    ) { Text(if (endAt != null) "Pause" else if (pausedLeft != null) "Resume" else "Start", fontSize = 13.sp) }
                    if (endAt != null || pausedLeft != null) {
                        OutlinedButton(
                            onClick = { endAt = null; pausedLeft = null },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) { Text("Reset", fontSize = 13.sp) }
                    } else {
                        OutlinedButton(
                            onClick = { minutes = when (minutes) { 15 -> 25; 25 -> 45; 45 -> 50; else -> 15 } },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) { Text("$minutes min", fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

private val quotes = listOf(
    "Small steps today, big wins at the end of the semester.",
    "You don't need to feel ready. You just need to start.",
    "One focused hour beats three distracted ones.",
    "Future you is going to be so grateful for this.",
    "Progress, not perfection. Open the file and begin.",
    "Discipline is choosing what you want most over what you want now.",
    "Every deadline you beat is proof you can do hard things.",
    "Done is a feeling worth chasing. Go get it.",
    "Ten minutes of work is better than zero minutes of worry.",
    "Start before you're motivated. Motivation shows up later.",
    "Make today the day the list gets shorter.",
    "Your effort compounds, just like interest.",
    "Hard now, easy later. Easy now, hard later.",
)

@Composable
private fun QuoteCard() {
    val quote = quotes[LocalDate.now().dayOfYear % quotes.size]
    GlassCard {
        Row {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Palette.Amber)
            Spacer(Modifier.width(12.dp))
            Text(quote, color = Color.White, fontSize = 15.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif)
        }
    }
}
