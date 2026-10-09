package com.eepiemi.materialbook.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.eepiemi.materialbook.MainActivity
import com.eepiemi.materialbook.R
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Set by MainActivity: no notifications while the app is on screen (the badges are right there). */
object AppVisibility {
    @Volatile var background = true
}

private const val JOB_ID = 4242
private const val CHANNEL = "alerts"
private const val HOME = "https://m.facebook.com/"

private val KEYS = listOf("messages", "notifications", "friends")
private val TEXTS = listOf(R.string.notif_new_messages, R.string.notif_new_notifications, R.string.notif_new_friends)
private val PAGES = listOf("messages/", "notifications/", "friends/")

/** Check about every 15 minutes (the shortest a periodic job can be) while the setting is on. */
fun scheduleNotificationPoll(context: Context, enabled: Boolean) {
    val scheduler = context.getSystemService(JobScheduler::class.java)
    if (!enabled) return scheduler.cancel(JOB_ID)
    if (scheduler.getPendingJob(JOB_ID) != null) return
    scheduler.schedule(
        JobInfo.Builder(JOB_ID, ComponentName(context, NotificationPollService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(true)
            .build()
    )
}

class NotificationPollService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread { runCatching { pollNotifications(applicationContext) }.onFailure { Log.d("NotifPoll", "failed: $it") }; jobFinished(params, false) }
        return true
    }

    override fun onStopJob(params: JobParameters) = false
}

/**
 * Counts on the tab bar (messages, notifications, friend requests), read from the tab labels in
 * the HTML: aria-label="messages, 1 new, 3 of 6". A tab with no "new" has no count.
 */
internal fun parseCounts(html: String): List<Int> = KEYS.map { key ->
    val m = Regex("aria-label=\"$key, ([\\d.,]+)\\+? new").find(html)
    m?.groupValues?.get(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
}

private fun pollNotifications(context: Context) {
    val cookies = CookieManager.getInstance().getCookie(HOME)
    if (cookies == null || !cookies.contains("c_user=")) return // not signed in
    // The notifications page is the lightest one that carries the tab bar with its counts.
    val html = (URL(HOME + "notifications/").openConnection() as HttpURLConnection).run {
        setRequestProperty("Cookie", cookies)
        setRequestProperty("User-Agent", android.webkit.WebSettings.getDefaultUserAgent(context))
        setRequestProperty("Accept-Language", "en-US,en;q=0.9") // the "new" in the labels is English
        setRequestProperty("Upgrade-Insecure-Requests", "1")
        setRequestProperty("Sec-Fetch-Dest", "document")
        setRequestProperty("Sec-Fetch-Mode", "navigate")
        setRequestProperty("Sec-Fetch-Site", "none")
        setRequestProperty("Sec-Fetch-User", "?1")
        connectTimeout = 15000
        readTimeout = 20000
        try { if (responseCode == 200) inputStream.bufferedReader().readText() else "" } finally { disconnect() }
    }
    if (!html.contains("role=\"tablist\"")) return // an error page: keep what we had
    val counts = parseCounts(html)
    val prefs = context.getSharedPreferences("notif_poll", Context.MODE_PRIVATE)
    val primed = prefs.contains("messages")
    counts.forEachIndexed { i, n ->
        if (primed && AppVisibility.background && n > prefs.getInt(KEYS[i], 0)) postAlert(context, 2000 + i, TEXTS[i], HOME + PAGES[i])
        prefs.edit().putInt(KEYS[i], n).apply()
    }
}

private fun postAlert(context: Context, id: Int, text: Int, url: String) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT))
    val open = PendingIntent.getActivity(
        context, id, Intent(Intent.ACTION_VIEW, url.toUri(), context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val n = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    runCatching { NotificationManagerCompat.from(context).notify(id, n) }.onFailure { Log.d("NotifPoll", "notify failed: $it") }
}
