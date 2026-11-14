@file:OptIn(ExperimentalMaterial3Api::class)

package com.studyhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyhub.app.MainViewModel
import com.studyhub.app.UiState
import com.studyhub.app.data.Fmt
import com.studyhub.app.data.ItemKind
import com.studyhub.app.data.StudyItem
import java.time.Instant

private enum class Filter(val label: String) { UPCOMING("Upcoming"), OVERDUE("Overdue"), DONE("Done"), ALL("All") }

@Composable
fun DeadlinesScreen(state: UiState, vm: MainViewModel, onOpen: (StudyItem) -> Unit, onSettings: () -> Unit) {
    var filterName by rememberSaveable { mutableStateOf(Filter.UPCOMING.name) }
    var kindName by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val filter = Filter.valueOf(filterName)
    val kind = kindName?.let { ItemKind.valueOf(it) }

    val now = Instant.now()
    var list = when (filter) {
        Filter.UPCOMING -> state.deadlines.filter { !state.isDone(it) && !it.due.isBefore(now) }
        Filter.OVERDUE -> state.overdue
        Filter.DONE -> state.deadlines.filter { state.isDone(it) }
        Filter.ALL -> state.deadlines
    }
    if (kind != null) list = list.filter { it.kind == kind }
    val q = search.trim()
    if (q.isNotEmpty()) list = list.filter { it.title.contains(q, true) || (it.course ?: "").contains(q, true) }
    var groups = list.groupBy { it.localDate }.toSortedMap().toList()
    if (filter == Filter.DONE) groups = groups.reversed()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Deadlines", onSettings)
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Filter.entries.forEach { f ->
                            Chip(f.label, null, Palette.Violet, f == filter) { filterName = f.name }
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("All types", Icons.Filled.GridView, Palette.Sky, kind == null) { kindName = null }
                        ItemKind.entries.forEach { k ->
                            Chip(k.label, k.icon, k.color, kind == k) { kindName = if (kind == k) null else k.name }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Search deadlines or courses") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (groups.isEmpty()) {
                    item {
                        val (icon, title) = when (filter) {
                            Filter.UPCOMING -> Icons.Filled.Celebration to "Nothing coming up"
                            Filter.OVERDUE -> Icons.Filled.Celebration to "Nothing overdue"
                            Filter.DONE -> Icons.Filled.Inbox to "Nothing checked off yet"
                            Filter.ALL -> Icons.Filled.Inbox to "No deadlines yet"
                        }
                        EmptyState(
                            icon, title,
                            if (state.feed.isBlank()) "Add your Canvas link on the Today tab." else "Enjoy the free time!",
                        )
                    }
                }
                groups.forEach { (day, dayItems) ->
                    item(key = "h-$day") { SectionHeader(Fmt.dayHeader(day), dayItems.size) }
                    items(dayItems.sortedBy { it.start }, key = { "d-" + it.id }) { ItemRow(it, state, vm, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, icon: ImageVector?, color: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.85f) else color.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = if (selected) Color.White else color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
