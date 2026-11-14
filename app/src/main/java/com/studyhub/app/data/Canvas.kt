package com.studyhub.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Reads the iCalendar (.ics) feed that Canvas provides. */
object IcsParser {
    private class Event {
        var uid: String? = null
        var summary: String? = null
        var description: String? = null
        var url: String? = null
        var location: String? = null
        var start: Instant? = null
        var isAllDay = false
    }

    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateFormat = DateTimeFormatter.BASIC_ISO_DATE
    private val coursePattern = Regex("\\s*\\[([^\\]]+)]\\s*$")

    fun items(text: String): List<StudyItem> = parse(text).mapNotNull { ev ->
        val start = ev.start ?: return@mapNotNull null
        val (title, course) = splitCourse(ev.summary ?: "Untitled")
        val kind = ItemKind.classify(title, ev.url)
        StudyItem(
            id = "canvas-" + (ev.uid ?: "$title-${start.epochSecond}"),
            title = title,
            course = course,
            start = start,
            isAllDay = ev.isAllDay,
            kind = kind,
            url = ev.url,
            notes = ev.description?.takeIf { it.isNotBlank() },
            location = ev.location?.takeIf { it.isNotBlank() },
        )
    }

    /** Canvas titles look like "Homework 5 [FIN 525 Fall 2026]". Split off the course. */
    fun splitCourse(summary: String): Pair<String, String?> {
        val match = coursePattern.find(summary) ?: return summary to null
        val title = summary.substring(0, match.range.first).ifBlank { summary }
        return title to match.groupValues[1].trim().ifBlank { null }
    }

    private fun parse(text: String): List<Event> {
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        val lines = mutableListOf<String>()
        for (raw in normalized.split("\n")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.size - 1] = lines.last() + raw.substring(1)
            } else if (raw.isNotEmpty()) {
                lines.add(raw)
            }
        }

        val events = mutableListOf<Event>()
        var current: Event? = null
        var nested = 0
        for (line in lines) {
            val upper = line.uppercase()
            if (upper == "BEGIN:VEVENT") { current = Event(); nested = 0; continue }
            if (upper == "END:VEVENT") { current?.let { events.add(it) }; current = null; continue }
            val ev = current ?: continue
            if (upper.startsWith("BEGIN:")) { nested++; continue }
            if (upper.startsWith("END:")) { nested = maxOf(0, nested - 1); continue }
            if (nested > 0) continue

            val colon = line.indexOf(':')
            if (colon < 0) continue
            val head = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val parts = head.split(";")
            val name = parts[0].uppercase()
            val params = HashMap<String, String>()
            for (p in parts.drop(1)) {
                val kv = p.split("=", limit = 2)
                if (kv.size == 2) params[kv[0].uppercase()] = kv[1].trim('"')
            }

            when (name) {
                "UID" -> ev.uid = value
                "SUMMARY" -> ev.summary = unescape(value)
                "DESCRIPTION" -> ev.description = unescape(value)
                "URL" -> ev.url = value.trim()
                "LOCATION" -> ev.location = unescape(value)
                "DTSTART" -> {
                    val (date, allDay) = parseDate(value, params)
                    ev.start = date
                    ev.isAllDay = allDay
                }
            }
        }
        return events
    }

    private fun parseDate(raw: String, params: Map<String, String>): Pair<Instant?, Boolean> {
        val v = raw.trim()
        return try {
            if (params["VALUE"]?.uppercase() == "DATE" || v.length == 8) {
                LocalDate.parse(v.take(8), dateFormat).atStartOfDay(ZoneId.systemDefault()).toInstant() to true
            } else if (v.endsWith("Z")) {
                LocalDateTime.parse(v.dropLast(1), dateTimeFormat).toInstant(ZoneOffset.UTC) to false
            } else {
                val zone = params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
                LocalDateTime.parse(v.take(15), dateTimeFormat).atZone(zone).toInstant() to false
            }
        } catch (e: Exception) {
            null to false
        }
    }

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                out.append(if (n == 'n' || n == 'N') '\n' else n)
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString().trim()
    }
}

/** Downloads the Canvas calendar feed. */
object Canvas {
    fun normalize(raw: String): String? {
        var t = raw.trim()
        if (t.lowercase().startsWith("webcal://")) t = "https://" + t.substring("webcal://".length)
        return if (t.startsWith("http://") || t.startsWith("https://")) t else null
    }

    fun download(feed: String): String {
        val link = normalize(feed) ?: throw IOException("That doesn't look like a valid link.")
        val conn = URL(link).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.useCaches = false
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Canvas answered with error $code. Try again later.")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (!text.contains("BEGIN:VCALENDAR")) {
                throw IOException("That link didn't return a calendar. Copy the “Calendar Feed” link from Canvas.")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
