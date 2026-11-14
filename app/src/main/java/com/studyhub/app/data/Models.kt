package com.studyhub.app.data

import androidx.compose.ui.graphics.Color
import com.studyhub.app.ui.Palette
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class ItemKind(val label: String, val color: Color) {
    EXAM("Exam", Palette.Coral),
    ASSIGNMENT("Assignment", Palette.Amber),
    CLASS_EVENT("Class / Event", Palette.Sky);

    companion object {
        private val examWords = Regex("\\b(exam|midterm|quiz|test|final exam)\\b")
        private val homeworkWords = Regex(
            "\\b(assignment|homework|hw|due|submit|submission|project|paper|essay|case|problem set|pset|report|presentation|discussion|lab)\\b"
        )

        /** Guesses what an item is from its title and link. */
        fun classify(title: String, url: String?): ItemKind {
            val t = title.lowercase(Locale.US)
            if (examWords.containsMatchIn(t)) return EXAM
            val u = url?.lowercase(Locale.US)
            if (u != null) {
                if (u.contains("/quizzes/")) return EXAM
                if (u.contains("assignment") || u.contains("/discussion_topics/")) return ASSIGNMENT
            }
            if (homeworkWords.containsMatchIn(t)) return ASSIGNMENT
            return CLASS_EVENT
        }
    }
}

data class StudyItem(
    val id: String,
    val title: String,
    val course: String?,
    val start: Instant,
    val isAllDay: Boolean,
    val kind: ItemKind,
    val url: String?,
    val notes: String?,
    val location: String?,
) {
    /** All-day items are due at the end of their day. */
    val due: Instant
        get() = if (!isAllDay) start else
            localDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusSeconds(60)

    /** Items that get a ✓ checkbox. */
    val tracksCompletion: Boolean get() = kind == ItemKind.EXAM || kind == ItemKind.ASSIGNMENT

    val localDate: LocalDate get() = start.atZone(ZoneId.systemDefault()).toLocalDate()
}

/** Weeks always run Monday 12:00 AM → Sunday 11:59 PM. */
object Week {
    fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun current(today: LocalDate = LocalDate.now()): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val monday = mondayOf(today)
        return monday.atStartOfDay(zone).toInstant() to monday.plusDays(7).atStartOfDay(zone).toInstant()
    }
}

object Fmt {
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
    private val shortDayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val fullFormat = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.US)

    fun short(seconds: Long): String = when {
        seconds < 0 -> "Overdue"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        else -> "${seconds / 86_400}d"
    }

    fun countdown(to: Instant, now: Instant = Instant.now()): String {
        val s = Duration.between(now, to).seconds
        if (s <= 0) {
            val a = -s
            return when {
                a < 3600 -> "Just passed"
                a < 86_400 -> "${a / 3600}h overdue"
                else -> "${a / 86_400}d overdue"
            }
        }
        val d = s / 86_400
        val h = (s % 86_400) / 3600
        val m = (s % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h left"
            h > 0 -> "${h}h ${m}m left"
            else -> "${m}m left"
        }
    }

    fun dayHeader(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> dayFormat.format(d)
        }
    }

    fun time(item: StudyItem): String =
        if (item.isAllDay) "All day" else timeFormat.format(item.start.atZone(ZoneId.systemDefault()))

    fun shortDay(i: Instant): String = shortDayFormat.format(i.atZone(ZoneId.systemDefault()))

    fun full(i: Instant): String = fullFormat.format(i.atZone(ZoneId.systemDefault()))

    fun clock(i: Instant): String = timeFormat.format(i.atZone(ZoneId.systemDefault()))

    fun ago(i: Instant?): String {
        if (i == null) return ""
        val s = Duration.between(i, Instant.now()).seconds
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60}m ago"
            s < 86_400 -> "${s / 3600}h ago"
            else -> "${s / 86_400}d ago"
        }
    }

    fun greeting(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}
