package com.tvkumanda.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tvkumanda.app.tv.Keys
import com.tvkumanda.app.tv.QuickSender
import kotlin.concurrent.thread

private const val ACTION_KEY = "com.tvkumanda.app.KEY"
private const val ACTION_SLEEP = "com.tvkumanda.app.SLEEP"
private const val PREFS = "tv_kumanda"

/** Receives button presses from the widget/notification and the sleep timer alarm. */
class KeyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val app = ctx.applicationContext
        thread {
            try {
                when (intent.action) {
                    ACTION_KEY -> QuickSender.send(app, listOf(intent.getIntExtra("key", 0)))
                    ACTION_SLEEP -> {
                        SleepTimer.clear(app)
                        QuickSender.send(app, listOf(Keys.POWER), onlyIfOn = true)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun keyIntent(ctx: Context, key: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, key,
            Intent(ctx, KeyReceiver::class.java).setAction(ACTION_KEY).putExtra("key", key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

/** Shared mini remote: power, vol-, mute, vol+, play/pause, home. */
fun remoteBar(ctx: Context): RemoteViews = RemoteViews(ctx.packageName, R.layout.remote_bar).apply {
    setOnClickPendingIntent(R.id.btn_power, KeyReceiver.keyIntent(ctx, Keys.POWER))
    setOnClickPendingIntent(R.id.btn_vol_down, KeyReceiver.keyIntent(ctx, Keys.VOLUME_DOWN))
    setOnClickPendingIntent(R.id.btn_mute, KeyReceiver.keyIntent(ctx, Keys.MUTE))
    setOnClickPendingIntent(R.id.btn_vol_up, KeyReceiver.keyIntent(ctx, Keys.VOLUME_UP))
    setOnClickPendingIntent(R.id.btn_play, KeyReceiver.keyIntent(ctx, Keys.PLAY_PAUSE))
    setOnClickPendingIntent(R.id.btn_home, KeyReceiver.keyIntent(ctx, Keys.HOME))
}

private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
    ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

class RemoteWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(ctx.packageName, R.layout.widget_remote).apply {
            removeAllViews(R.id.widget_bar)
            addView(R.id.widget_bar, remoteBar(ctx))
            setOnClickPendingIntent(R.id.widget_title, openApp(ctx))
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

object RemoteNotification {
    private const val CHANNEL = "remote"
    private const val ID = 7

    fun show(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Bildirim kumandası", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        val bar = remoteBar(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("Apollo Kumanda")
            .setCustomContentView(bar)
            .setCustomBigContentView(remoteBar(ctx))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID, n) }
    }

    fun hide(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}

object SleepTimer {
    private fun intent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 99, Intent(ctx, KeyReceiver::class.java).setAction(ACTION_SLEEP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun set(ctx: Context, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val am = ctx.getSystemService(AlarmManager::class.java)
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(ctx))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(ctx))
        ctx.getSharedPreferences(PREFS, 0).edit().putLong("sleep_at", at).apply()
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(intent(ctx))
        clear(ctx)
    }

    fun clear(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).edit().remove("sleep_at").apply()

    /** Time the TV will be turned off, or null. */
    fun endsAt(ctx: Context): Long? =
        ctx.getSharedPreferences(PREFS, 0).getLong("sleep_at", 0L).takeIf { it > System.currentTimeMillis() }
}

fun refreshWidgets(ctx: Context) {
    val m = AppWidgetManager.getInstance(ctx)
    val ids = m.getAppWidgetIds(ComponentName(ctx, RemoteWidget::class.java))
    if (ids.isNotEmpty()) RemoteWidget().onUpdate(ctx, m, ids)
}
