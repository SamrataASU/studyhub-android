package com.studyhub.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studyhub.app.data.CanvasChange
import com.studyhub.app.data.CanvasChanges
import com.studyhub.app.data.StudyItem
import com.studyhub.app.focus.FocusController
import com.studyhub.app.data.Store
import com.studyhub.app.data.Week
import com.studyhub.app.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class UiState(
    val items: List<StudyItem> = emptyList(),
    val done: Set<String> = emptySet(),
    val name: String = "",
    val feed: String = "",
    val onboarded: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val lastSynced: Instant? = null,
    val reminders: Boolean = true,
    val dailyNews: Boolean = true,
    val changes: List<CanvasChange> = emptyList(),
    val changeAlerts: Boolean = true,
    val askedName: Boolean = false,
) {
    val deadlines: List<StudyItem> get() = items.filter { it.tracksCompletion }

    fun isDone(item: StudyItem) = item.id in done

    val upcoming: List<StudyItem>
        get() {
            val now = Instant.now()
            return deadlines.filter { !isDone(it) && !it.due.isBefore(now) }.sortedBy { it.due }
        }

    val overdue: List<StudyItem>
        get() {
            val now = Instant.now()
            val cutoff = now.minusSeconds(21L * 86_400)
            return deadlines.filter { !isDone(it) && it.due.isBefore(now) && it.due.isAfter(cutoff) }
        }

    fun itemsOn(day: LocalDate): List<StudyItem> = items.filter { it.localDate == day }

    /** Done / total for this Monday–Sunday. */
    val weekProgress: Pair<Int, Int>
        get() {
            val (start, end) = Week.current()
            val week = deadlines.filter { !it.due.isBefore(start) && it.due.isBefore(end) }
            return week.count { isDone(it) } to week.size
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()

    /** The focus timer (kept here so it survives rotating the phone). */
    val focus = FocusController(app)

    private val _state = MutableStateFlow(
        UiState(
            items = inRange(Store.cachedItems(app)),
            done = Store.done(app),
            name = Store.name(app),
            askedName = Store.askedName(app),
            feed = Store.feed(app),
            onboarded = Store.onboarded(app),
            reminders = Store.reminders(app),
            dailyNews = Store.dailyNews(app),
            changes = CanvasChanges.recent(app),
            changeAlerts = CanvasChanges.alertsEnabled(app),
        )
    )
    val state: StateFlow<UiState> = _state

    /** Downloads the Canvas calendar. Runs on open, every 15 minutes while open, and on pull-to-refresh. */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, done = Store.done(ctx)) }
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val fresh = Store.refreshCanvas(ctx)
                    CanvasChanges.process(ctx, fresh)
                    fresh
                }
                _state.update { it.copy(items = inRange(items), error = null, changes = CanvasChanges.recent(ctx)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Couldn't reach Canvas. Check your connection.") }
            } finally {
                _state.update { it.copy(refreshing = false, lastSynced = Instant.now()) }
                Widgets.updateAll(ctx)
            }
        }
    }

    fun toggleDone(item: StudyItem) {
        Store.toggleDone(ctx, item.id)
        _state.update { it.copy(done = Store.done(ctx)) }
        viewModelScope.launch { Widgets.updateAll(ctx) }
    }

    fun finishOnboarding(feed: String) {
        Store.setFeed(ctx, feed)
        Store.setOnboarded(ctx)
        _state.update { it.copy(feed = feed.trim(), onboarded = true) }
        refresh()
    }

    /** Answer from the first-launch "What's your first name?" box. */
    fun answerName(name: String?) {
        if (!name.isNullOrBlank()) Store.setName(ctx, name)
        Store.setAskedName(ctx)
        _state.update { it.copy(name = if (name.isNullOrBlank()) it.name else name.trim(), askedName = true) }
    }

    fun setName(name: String) {
        Store.setName(ctx, name)
        _state.update { it.copy(name = name.trim()) }
    }

    fun setFeed(feed: String) {
        Store.setFeed(ctx, feed)
        CanvasChanges.reset(ctx)
        _state.update { it.copy(feed = feed.trim(), error = null, changes = emptyList()) }
        refresh()
    }

    fun clearChanges() {
        CanvasChanges.clearLog(ctx)
        _state.update { it.copy(changes = emptyList()) }
    }

    fun setChangeAlerts(on: Boolean) {
        CanvasChanges.setAlertsEnabled(ctx, on)
        _state.update { it.copy(changeAlerts = on) }
    }

    fun setReminders(on: Boolean) {
        Store.setReminders(ctx, on)
        _state.update { it.copy(reminders = on) }
    }

    fun setDailyNews(on: Boolean) {
        Store.setDailyNews(ctx, on)
        _state.update { it.copy(dailyNews = on) }
    }

    private fun inRange(list: List<StudyItem>): List<StudyItem> {
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now().minusDays(45).atStartOfDay(zone).toInstant()
        val to = LocalDate.now().plusDays(200).atStartOfDay(zone).toInstant()
        return list.filter { !it.start.isBefore(from) && !it.start.isAfter(to) }.sortedBy { it.start }
    }
}
