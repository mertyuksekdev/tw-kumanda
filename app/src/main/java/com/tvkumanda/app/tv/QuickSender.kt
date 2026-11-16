package com.tvkumanda.app.tv

import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The connection the app currently has open, shared with the widget, notification and timer. */
object Live {
    @Volatile var client: RemoteClient? = null
    @Volatile var powerOn: Boolean? = null
}

/**
 * Sends keys from outside the app screen (widget, notification, sleep timer).
 * Uses the open connection if there is one, otherwise opens a short one to the saved TV.
 * Blocks: call off the main thread.
 */
object QuickSender {
    fun send(ctx: Context, keys: List<Int>, onlyIfOn: Boolean = false): Boolean {
        Live.client?.let { c ->
            if (onlyIfOn && Live.powerOn == false) return false
            return runCatching { keys.forEach { c.sendKey(it); Thread.sleep(60) } }.isSuccess
        }
        val host = ctx.getSharedPreferences("tv_kumanda", 0).getString("host", null) ?: return false
        CertManager.init(ctx.applicationContext)
        var on: Boolean? = null
        val powerKnown = CountDownLatch(1)
        val client = RemoteClient(host, object : RemoteClient.Listener {
            override fun onPower(on: Boolean) = setOn(on)
            private fun setOn(v: Boolean) { on = v; powerKnown.countDown() }
            override fun onVolume(level: Int, max: Int, muted: Boolean) {}
            override fun onDisconnected(error: Throwable?) {}
        })
        return try {
            client.connect()
            if (onlyIfOn) {
                powerKnown.await(3, TimeUnit.SECONDS)
                if (on != true) return false
            }
            keys.forEach { client.sendKey(it); Thread.sleep(60) }
            Thread.sleep(400)
            true
        } catch (e: Exception) {
            false
        } finally {
            client.close()
        }
    }
}
