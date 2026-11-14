package com.studyhub.app.focus

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.studyhub.app.work.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** Growth stages by total minutes focused in one sitting. */
object Growth {
    val milestones = listOf(25.0 to "Plant 🌱", 50.0 to "Tree 🌳", 80.0 to "Grove 🌳🌳", 110.0 to "Forest 🌲🌳🌲")

    fun title(m: Double): String = when {
        m < 5 -> "A seed is planted"
        m < 15 -> "Sprouting"
        m < 25 -> "Growing, leaf by leaf"
        m < 50 -> "Your plant 🌱"
        m < 80 -> "Your tree 🌳"
        m < 110 -> "A little grove 🌳🌳"
        else -> "A forest 🌲🌳🌲"
    }

    fun next(m: Double): String {
        val target = milestones.firstOrNull { it.first > m } ?: return "Keep growing your forest"
        return "${ceil(target.first - m).toInt()} min until ${target.second}"
    }

    val quotes = listOf(
        "Every minute of focus is a drop of water for your tree.",
        "Small, steady focus grows into something big.",
        "You're planting the future, one minute at a time.",
        "Stay with it. Roots grow before branches.",
        "Deep work now, free time later.",
        "Your future self is cheering for you.",
        "Quiet focus. Strong roots. Big results.",
        "The forest is built one seed at a time.",
    )

    val breakQuotes = listOf(
        "Rest is part of the work. Stretch, breathe, drink some water.",
        "Even the tallest trees need sunlight and rest.",
        "Step away from the screen. Your brain is filing everything away.",
        "A short break now means a sharper mind for the next round.",
    )
}

data class FocusState(
    val active: Boolean = false,
    val blockMinutes: Int = 25,
    val accumulatedMs: Long = 0,
    val runningSince: Long? = null,
    val priorMs: Long = 0,
    val breakEnd: Long? = null,
    val breakOffered: Boolean = false,
    val showBreakPrompt: Boolean = false,
) {
    val blockMs: Long get() = blockMinutes * 60_000L
    val running: Boolean get() = runningSince != null

    fun blockElapsed(now: Long): Long = minOf(blockMs, accumulatedMs + (runningSince?.let { now - it } ?: 0L))
    fun blockLeft(now: Long): Long = maxOf(0L, blockMs - blockElapsed(now))
    fun total(now: Long): Long = priorMs + blockElapsed(now)
    fun finished(now: Long): Boolean = blockElapsed(now) >= blockMs

    fun paused(now: Long): FocusState {
        val since = runningSince ?: return this
        return copy(accumulatedMs = minOf(blockMs, accumulatedMs + (now - since)), runningSince = null)
    }
}

/** The focus timer. Lives in the ViewModel so it survives rotating the phone, and is saved so it survives closing the app. */
class FocusController(private val context: Context) {
    companion object {
        const val BREAK_AFTER_MS = 110 * 60_000L
        const val BREAK_LENGTH_MS = 10 * 60_000L
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<FocusState> = _state

    private val now: Long get() = System.currentTimeMillis()

    private fun set(s: FocusState) {
        _state.value = s
        save(s)
        FocusAlarms.schedule(context, s)
    }

    fun start(minutes: Int) = set(FocusState(active = true, blockMinutes = minutes, runningSince = now))

    fun pause() = set(_state.value.paused(now))

    fun resume() {
        val s = _state.value
        if (s.running || s.finished(now) || s.breakEnd != null) return
        set(s.copy(runningSince = now))
    }

    /** After a block ends: keep growing the same tree for another block. */
    fun extend(minutes: Int) {
        val s = _state.value
        set(s.copy(priorMs = s.priorMs + s.blockElapsed(now), blockMinutes = minutes, accumulatedMs = 0, runningSince = now))
    }

    fun startBreak() = set(_state.value.paused(now).copy(showBreakPrompt = false, breakEnd = now + BREAK_LENGTH_MS))

    fun keepFocusing() {
        set(_state.value.copy(showBreakPrompt = false))
        resume()
    }

    fun backFromBreak() {
        set(_state.value.copy(breakEnd = null))
        if (_state.value.finished(now)) extend(25) else resume()
    }

    fun finish() {
        val minutes = (_state.value.total(now) / 60_000L).toInt()
        addMinutesToday(minutes)
        set(FocusState())
    }

    /** Called every second while the focus screen is showing. */
    fun tick(at: Long) {
        var s = _state.value
        val since = s.runningSince
        if (since != null && s.accumulatedMs + (at - since) >= s.blockMs) {
            s = s.copy(accumulatedMs = s.blockMs, runningSince = null)
            set(s)
        }
        if (!s.breakOffered && s.total(at) >= BREAK_AFTER_MS) {
            set(s.paused(at).copy(breakOffered = true, showBreakPrompt = true))
        }
    }

    fun minutesToday(): Int {
        val p = prefs()
        return if (p.getString("focusDay", "") == LocalDate.now().toString()) p.getInt("focusMinutesToday", 0) else 0
    }

    private fun addMinutesToday(minutes: Int) {
        if (minutes <= 0) return
        prefs().edit()
            .putString("focusDay", LocalDate.now().toString())
            .putInt("focusMinutesToday", minutesToday() + minutes)
            .apply()
    }

    private fun prefs() = context.getSharedPreferences("focus", Context.MODE_PRIVATE)

    private fun save(s: FocusState) {
        val e = prefs().edit()
        if (!s.active) {
            e.putBoolean("active", false).apply()
            return
        }
        e.putBoolean("active", true)
            .putInt("blockMinutes", s.blockMinutes)
            .putLong("accumulatedMs", s.accumulatedMs)
            .putLong("runningSince", s.runningSince ?: -1L)
            .putLong("priorMs", s.priorMs)
            .putLong("breakEnd", s.breakEnd ?: -1L)
            .putBoolean("breakOffered", s.breakOffered)
            .putBoolean("showBreakPrompt", s.showBreakPrompt)
            .apply()
    }

    private fun load(): FocusState {
        val p = prefs()
        if (!p.getBoolean("active", false)) return FocusState()
        return FocusState(
            active = true,
            blockMinutes = p.getInt("blockMinutes", 25),
            accumulatedMs = p.getLong("accumulatedMs", 0L),
            runningSince = p.getLong("runningSince", -1L).takeIf { it > 0 },
            priorMs = p.getLong("priorMs", 0L),
            breakEnd = p.getLong("breakEnd", -1L).takeIf { it > 0 },
            breakOffered = p.getBoolean("breakOffered", false),
            showBreakPrompt = p.getBoolean("showBreakPrompt", false),
        )
    }
}

/** Notifications for when a block ends, at 1 h 50 min, and when a break is over. */
object FocusAlarms {
    private val names = listOf("focus-end", "focus-break", "focus-break-end")

    fun schedule(c: Context, s: FocusState) {
        val wm = WorkManager.getInstance(c)
        names.forEach { wm.cancelUniqueWork(it) }
        if (!s.active) return
        val now = System.currentTimeMillis()
        val breakEnd = s.breakEnd
        if (breakEnd != null) {
            enqueue(c, "focus-break-end", breakEnd - now, "Break's over 🌱", "Ready for another round? Your tree is waiting.")
            return
        }
        if (!s.running) return
        val left = s.blockLeft(now)
        val totalAtEnd = (s.total(now) + left) / 60_000L
        enqueue(c, "focus-end", left, "Focus session complete 🎉", "$totalAtEnd minutes of focus. Add 25 more to keep growing.")
        if (!s.breakOffered) {
            val toBreak = FocusController.BREAK_AFTER_MS - s.total(now)
            if (toBreak in 0..left) {
                enqueue(
                    c, "focus-break", toBreak,
                    "You've been focusing for 1 hour 50 minutes 🌲",
                    "Take a 10-minute break. " + Growth.breakQuotes.random(),
                )
            }
        }
    }

    private fun enqueue(c: Context, name: String, delayMs: Long, title: String, body: String) {
        val request = OneTimeWorkRequestBuilder<FocusAlarmWorker>()
            .setInitialDelay(maxOf(1_000L, delayMs), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("title" to title, "body" to body))
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }
}

class FocusAlarmWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Notifier.postFocus(
            applicationContext,
            inputData.getString("title") ?: "Focus",
            inputData.getString("body") ?: "",
        )
        return Result.success()
    }
}
