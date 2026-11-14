@file:OptIn(ExperimentalMaterial3Api::class)

package com.studyhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyhub.app.MainViewModel
import com.studyhub.app.UiState
import com.studyhub.app.data.Fmt
import com.studyhub.app.data.ItemKind
import com.studyhub.app.data.StudyItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(state: UiState, vm: MainViewModel, onOpen: (StudyItem) -> Unit, onSettings: () -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val dayItems = state.itemsOn(selected)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Calendar", onSettings) {
            TextButton(onClick = {
                monthText = YearMonth.now().toString()
                selectedText = LocalDate.now().toString()
            }) { Text("Today", color = Palette.Violet) }
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    GlassCard {
                        MonthGrid(
                            month = month,
                            selected = selected,
                            state = state,
                            onPrev = { monthText = month.minusMonths(1).toString() },
                            onNext = { monthText = month.plusMonths(1).toString() },
                            onSelect = { selectedText = it.toString() },
                        )
                    }
                }
                item { SectionHeader(Fmt.dayHeader(selected), dayItems.size) }
                if (dayItems.isEmpty()) {
                    item { Text("Nothing on this day.", color = Palette.Muted, fontSize = 14.sp) }
                }
                items(dayItems, key = { it.id }) { ItemRow(it, state, vm, onOpen) }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    state: UiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val title = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US).format(month)
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1 // Monday-first
    val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val weeks = cells.chunked(7)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month", tint = Palette.Text)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month", tint = Palette.Text)
        }
    }
    Spacer(Modifier.height(6.dp))
    Row {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Text(it, color = Palette.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(4.dp))
    weeks.forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            for (i in 0 until 7) {
                val day = week.getOrNull(i)
                Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                    if (day != null) DayCell(day, day == selected, state.itemsOn(day)) { onSelect(day) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, isSelected: Boolean, items: List<StudyItem>, onClick: () -> Unit) {
    val isToday = day == LocalDate.now()
    val kinds: List<ItemKind> = items.map { it.kind }.distinct().take(3)
    val shape = RoundedCornerShape(12.dp)
    var modifier = Modifier
        .fillMaxSize()
        .padding(2.dp)
        .clip(shape)
    modifier = when {
        isSelected -> modifier.background(Palette.Violet)
        isToday -> modifier.border(1.dp, Palette.Violet.copy(alpha = 0.6f), shape)
        else -> modifier
    }
    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${day.dayOfMonth}",
            color = when {
                isSelected -> Color.White
                isToday -> Palette.Violet
                else -> Palette.Text
            },
            fontSize = 15.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp), verticalAlignment = Alignment.CenterVertically) {
            kinds.forEach { k -> Box(Modifier.size(5.dp).clip(CircleShape).background(k.color)) }
        }
    }
}
