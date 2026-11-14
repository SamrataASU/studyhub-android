package com.studyhub.app.data

import android.content.Context
import java.io.File

/** Everything is saved on this phone only. Nothing is synced or sent anywhere except the Canvas download. */
object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("studyhub", Context.MODE_PRIVATE)
    private fun cacheFile(c: Context) = File(c.filesDir, "canvas.ics")

    fun name(c: Context): String = prefs(c).getString("name", "") ?: ""
    fun setName(c: Context, v: String) = prefs(c).edit().putString("name", v.trim()).apply()

    fun feed(c: Context): String = prefs(c).getString("feed", "") ?: ""
    fun setFeed(c: Context, v: String) = prefs(c).edit().putString("feed", v.trim()).apply()

    fun onboarded(c: Context): Boolean = prefs(c).getBoolean("onboarded", false)
    fun setOnboarded(c: Context) = prefs(c).edit().putBoolean("onboarded", true).apply()

    fun done(c: Context): Set<String> = prefs(c).getStringSet("done", emptySet())?.toSet() ?: emptySet()

    fun toggleDone(c: Context, id: String) {
        val set = done(c).toMutableSet()
        if (!set.add(id)) set.remove(id)
        prefs(c).edit().putStringSet("done", set).commit()
    }

    fun reminders(c: Context): Boolean = prefs(c).getBoolean("reminders", true)
    fun setReminders(c: Context, on: Boolean) = prefs(c).edit().putBoolean("reminders", on).apply()

    fun dailyNews(c: Context): Boolean = prefs(c).getBoolean("dailyNews", true)
    fun setDailyNews(c: Context, on: Boolean) = prefs(c).edit().putBoolean("dailyNews", on).apply()

    fun notified(c: Context): Set<String> = prefs(c).getStringSet("notified", emptySet())?.toSet() ?: emptySet()
    fun setNotified(c: Context, v: Set<String>) = prefs(c).edit().putStringSet("notified", v).apply()

    fun lastNewsDay(c: Context): String = prefs(c).getString("lastNewsDay", "") ?: ""
    fun setLastNewsDay(c: Context, v: String) = prefs(c).edit().putString("lastNewsDay", v).apply()

    fun focusCountToday(c: Context, today: String): Int =
        if (prefs(c).getString("focusDay", "") == today) prefs(c).getInt("focusCount", 0) else 0

    fun addFocusSession(c: Context, today: String) {
        val count = focusCountToday(c, today) + 1
        prefs(c).edit().putString("focusDay", today).putInt("focusCount", count).apply()
    }

    fun cachedItems(c: Context): List<StudyItem> =
        runCatching { IcsParser.items(cacheFile(c).readText()) }.getOrDefault(emptyList())

    /** Downloads a fresh Canvas calendar (call off the main thread). Throws if it fails. */
    fun refreshCanvas(c: Context): List<StudyItem> {
        val feed = feed(c)
        if (feed.isBlank()) return emptyList()
        val text = Canvas.download(feed)
        cacheFile(c).writeText(text)
        return IcsParser.items(text)
    }
}
