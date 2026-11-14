package com.studyhub.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.studyhub.app.MainActivity
import com.studyhub.app.R
import com.studyhub.app.data.StudyItem
import com.studyhub.app.data.Store
import com.studyhub.app.widget.Widgets
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Runs about once an hour in the background: refreshes Canvas, updates widgets, sends reminders. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val items = try {
            Store.refreshCanvas(c)
        } catch (e: Exception) {
            Store.cachedItems(c)
        }
        Widgets.updateAll(c)
        Notifier.checkDeadlines(c, items)
        Notifier.checkDailyNews(c)
        return Result.success()
    }

    companion object {
        fun schedule(c: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(c)
                .enqueueUniquePeriodicWork("studyhub-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

object Notifier {
    private const val DEADLINES = "deadlines"
    private const val NEWS = "news"

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(DEADLINES, "Deadline reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(NEWS, "Daily finance news", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun canPost(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(c: Context, id: Int, channel: String, title: String, body: String) {
        if (!canPost(c)) return
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications not allowed.
        }
    }

    /** About 24 hours and 3 hours before each unfinished deadline. */
    fun checkDeadlines(c: Context, items: List<StudyItem>) {
        if (!Store.reminders(c)) return
        val done = Store.done(c)
        val sent = Store.notified(c).toMutableSet()
        val now = Instant.now()
        for (item in items) {
            if (!item.tracksCompletion || item.id in done) continue
            val left = Duration.between(now, item.due).seconds
            if (left <= 0) continue
            val key24 = "${item.id}|24"
            val key3 = "${item.id}|3"
            val title = if (item.kind.name == "EXAM") "📚 Exam coming up" else "⏰ Deadline reminder"
            if (left <= 3 * 3600 && key3 !in sent) {
                post(c, item.id.hashCode(), DEADLINES, title, "${item.title} is due in less than 3 hours.")
                sent += key3
                sent += key24
            } else if (left <= 24 * 3600 && key24 !in sent) {
                post(c, item.id.hashCode(), DEADLINES, title, "${item.title} is due within 24 hours.")
                sent += key24
            }
        }
        Store.setNotified(c, sent)
    }

    /** Once a day, after 8 AM. */
    fun checkDailyNews(c: Context) {
        if (!Store.dailyNews(c)) return
        val now = LocalDateTime.now()
        if (now.hour < 8) return
        val today = now.toLocalDate().toString()
        if (Store.lastNewsDay(c) == today) return
        post(c, 8800, NEWS, "📈 Your daily finance briefing", "Today's market, economy and company headlines are ready.")
        Store.setLastNewsDay(c, today)
    }
}
