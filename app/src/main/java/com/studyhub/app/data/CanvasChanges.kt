package com.studyhub.app.data

import android.content.Context
import com.studyhub.app.work.Notifier
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.abs

/** Something that changed in Canvas since the last check. */
data class CanvasChange(
    val kind: Kind,
    val title: String,
    val course: String?,
    val due: Instant,
    val oldDue: Instant?,
    val isExam: Boolean,
    val detected: Instant,
) {
    enum class Kind { ADDED, EARLIER, LATER }

    val headline: String
        get() = when (kind) {
            Kind.ADDED -> if (isExam) "New exam" else "New assignment"
            Kind.EARLIER -> "Due date moved earlier"
            Kind.LATER -> "Due date postponed"
        }

    val emoji: String
        get() = when (kind) {
            Kind.ADDED -> if (isExam) "📚" else "📝"
            Kind.EARLIER -> "⚠️"
            Kind.LATER -> "🗓️"
        }

    val detail: String
        get() {
            val parts = mutableListOf(title)
            course?.let { parts.add(it) }
            val old = oldDue
            parts.add(if (old != null) "now due ${Fmt.full(due)} (was ${Fmt.full(old)})" else "due ${Fmt.full(due)}")
            return parts.joinToString(" · ")
        }

    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind.name)
        .put("title", title)
        .put("course", course ?: "")
        .put("due", due.toEpochMilli())
        .put("oldDue", oldDue?.toEpochMilli() ?: -1L)
        .put("isExam", isExam)
        .put("detected", detected.toEpochMilli())

    companion object {
        fun fromJson(o: JSONObject) = CanvasChange(
            kind = Kind.valueOf(o.getString("kind")),
            title = o.getString("title"),
            course = o.optString("course").ifEmpty { null },
            due = Instant.ofEpochMilli(o.getLong("due")),
            oldDue = o.optLong("oldDue", -1L).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) },
            isExam = o.optBoolean("isExam"),
            detected = Instant.ofEpochMilli(o.getLong("detected")),
        )
    }
}

/** Compares each Canvas download with the previous one to spot new assignments/exams and moved due dates. */
object CanvasChanges {
    private fun prefs(c: Context) = c.getSharedPreferences("canvas_changes", Context.MODE_PRIVATE)

    fun alertsEnabled(c: Context): Boolean = prefs(c).getBoolean("alerts", true)
    fun setAlertsEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("alerts", on).apply()

    /** Call after every successful Canvas download. The very first download only records what's there. */
    @Synchronized
    fun process(c: Context, items: List<StudyItem>): List<CanvasChange> {
        val deadlines = items.filter { it.tracksCompletion }
        val current = JSONObject()
        deadlines.forEach { current.put(it.id, it.due.toEpochMilli()) }

        val saved = prefs(c).getString("snapshot", null)
        if (saved == null) {
            prefs(c).edit().putString("snapshot", current.toString()).apply()
            return emptyList()
        }
        val previous = runCatching { JSONObject(saved) }.getOrDefault(JSONObject())

        val now = Instant.now()
        val changes = mutableListOf<CanvasChange>()
        for (item in deadlines) {
            if (!item.due.isAfter(now)) continue
            if (previous.has(item.id)) {
                val before = previous.getLong(item.id)
                val moved = item.due.toEpochMilli() - before
                if (abs(moved) >= 60_000L) {
                    changes.add(
                        CanvasChange(
                            if (moved < 0) CanvasChange.Kind.EARLIER else CanvasChange.Kind.LATER,
                            item.title, item.course, item.due, Instant.ofEpochMilli(before),
                            item.kind == ItemKind.EXAM, now,
                        )
                    )
                }
            } else {
                changes.add(CanvasChange(CanvasChange.Kind.ADDED, item.title, item.course, item.due, null, item.kind == ItemKind.EXAM, now))
            }
        }
        prefs(c).edit().putString("snapshot", current.toString()).apply()
        if (changes.isEmpty()) return emptyList()

        val log = JSONArray()
        (changes + recent(c)).take(30).forEach { log.put(it.toJson()) }
        prefs(c).edit().putString("log", log.toString()).apply()

        if (alertsEnabled(c)) notify(c, changes)
        return changes
    }

    /** Changes from the last 7 days, newest first (for the card on the Today screen). */
    fun recent(c: Context): List<CanvasChange> {
        val raw = prefs(c).getString("log", null) ?: return emptyList()
        val cutoff = Instant.now().minusSeconds(7L * 86_400)
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { CanvasChange.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList()).filter { it.detected.isAfter(cutoff) }
    }

    fun clearLog(c: Context) = prefs(c).edit().remove("log").apply()

    /** Start fresh, e.g. when a different Canvas link is entered (so nothing is reported as "new"). */
    fun reset(c: Context) = prefs(c).edit().remove("snapshot").remove("log").apply()

    private fun notify(c: Context, changes: List<CanvasChange>) {
        if (changes.size > 3) {
            val body = changes.take(3).joinToString("\n") { "${it.headline}: ${it.title}" } + "\nOpen the app to see all."
            Notifier.postChange(c, "canvas-summary-${System.currentTimeMillis()}".hashCode(), "📚 ${changes.size} updates from Canvas", body)
            return
        }
        changes.forEach { ch ->
            Notifier.postChange(c, "${ch.title}|${ch.due}".hashCode(), "${ch.emoji} ${ch.headline}", ch.detail)
        }
    }
}
