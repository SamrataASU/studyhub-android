@file:OptIn(ExperimentalMaterial3Api::class)

package com.studyhub.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyhub.app.MainViewModel
import com.studyhub.app.UiState
import com.studyhub.app.data.Fmt
import com.studyhub.app.data.StudyItem
import kotlinx.coroutines.delay

/** A web page shown inside the app (news stories, Canvas links). */
data class WebPage(val title: String, val source: String, val url: String)

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Today", Icons.Filled.WbSunny),
    Tab("Calendar", Icons.Filled.CalendarMonth),
    Tab("Deadlines", Icons.Filled.Checklist),
    Tab("News", Icons.Filled.Newspaper),
)

@Composable
fun StudyHubApp(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var page by remember { mutableStateOf<WebPage?>(null) }
    var detail by remember { mutableStateOf<StudyItem?>(null) }

    // Keep things fresh while the app is open.
    LaunchedEffect(state.onboarded) {
        while (state.onboarded) {
            delay(15 * 60 * 1000L)
            vm.refresh()
        }
    }

    AppTheme {
        AppBackground {
            when {
                !state.onboarded -> OnboardingScreen(onDone = vm::finishOnboarding)
                page != null -> {
                    BackHandler { page = null }
                    WebPageScreen(page!!, onClose = { page = null })
                }
                showSettings -> {
                    BackHandler { showSettings = false }
                    SettingsScreen(state, vm, onClose = { showSettings = false })
                }
                else -> Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF15112E)) {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Palette.Violet,
                                        selectedTextColor = Palette.Violet,
                                        indicatorColor = Palette.Violet.copy(alpha = 0.18f),
                                        unselectedIconColor = Palette.Muted,
                                        unselectedTextColor = Palette.Muted,
                                    ),
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        val openSettings = { showSettings = true }
                        val open: (StudyItem) -> Unit = { detail = it }
                        when (tab) {
                            0 -> TodayScreen(state, vm, open, openSettings)
                            1 -> CalendarScreen(state, vm, open, openSettings)
                            2 -> DeadlinesScreen(state, vm, open, openSettings)
                            else -> NewsScreen(onOpen = { page = it }, onSettings = openSettings)
                        }
                    }
                }
            }

            detail?.let { item ->
                ItemDetailSheet(
                    item = item,
                    state = state,
                    onToggle = { vm.toggleDone(item) },
                    onOpenLink = { url ->
                        detail = null
                        page = WebPage(item.title, "Canvas", url)
                    },
                    onDismiss = { detail = null },
                )
            }
        }
    }
}

@Composable
fun ItemRow(item: StudyItem, state: UiState, vm: MainViewModel, onOpen: (StudyItem) -> Unit, showDate: Boolean = false) {
    val done = state.isDone(item)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { onOpen(item) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (item.tracksCompletion) {
            IconButton(onClick = { vm.toggleDone(item) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (done) "Mark as not done" else "Mark as done",
                    tint = if (done) Palette.Mint else item.kind.color,
                )
            }
        } else {
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .size(width = 5.dp, height = 34.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(item.kind.color)
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                item.title,
                color = if (done) Palette.Muted else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            val parts = listOfNotNull(item.course, if (showDate) Fmt.shortDay(item.start) else null, Fmt.time(item))
            Text(parts.joinToString(" · "), color = Palette.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.tracksCompletion && !done) {
            Spacer(Modifier.width(8.dp))
            DueBadge(item.due)
        }
    }
}

@Composable
private fun ItemDetailSheet(
    item: StudyItem,
    state: UiState,
    onToggle: () -> Unit,
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1C1638),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KindChip(item.kind)
            Text(item.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            item.course?.let { DetailLine(Icons.Filled.School, it) }
            GlassCard {
                DetailLine(Icons.Filled.AccessTime, if (item.isAllDay) Fmt.shortDay(item.start) + " · All day" else Fmt.full(item.start))
                if (item.tracksCompletion) {
                    Spacer(Modifier.height(8.dp))
                    DetailLine(Icons.Filled.HourglassBottom, Fmt.countdown(item.due))
                }
                item.location?.let {
                    Spacer(Modifier.height(8.dp))
                    DetailLine(Icons.Filled.Place, it)
                }
            }
            item.notes?.let { notes ->
                GlassCard { Text(notes, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp) }
            }
            if (item.tracksCompletion) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Violet),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isDone(item)) "Mark as not done" else "Mark as done")
                }
            }
            item.url?.let { url ->
                OutlinedButton(onClick = { onOpenLink(url) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open in Canvas")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Violet, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(4.dp))
        Text(message, color = Palette.Muted, fontSize = 14.sp)
    }
}

/** Fills the screen with the background so pull-to-refresh content lines up. */
@Composable
fun ScreenColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}
