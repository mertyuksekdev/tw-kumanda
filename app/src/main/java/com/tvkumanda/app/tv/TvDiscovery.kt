package com.tvkumanda.app.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager

data class TvDevice(val name: String, val host: String)

/** Finds Google TV / Android TV devices on the local network via mDNS. */
class TvDiscovery(context: Context, private val onFound: (TvDevice) -> Unit) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var lock: WifiManager.MulticastLock? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var running = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) = enqueue(info)
        override fun onServiceLost(info: NsdServiceInfo) {}
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) {}
        override fun onStartDiscoveryFailed(type: String, code: Int) {}
        override fun onStopDiscoveryFailed(type: String, code: Int) {}
    }

    fun start() {
        if (running) return
        running = true
        runCatching {
            lock = wifi.createMulticastLock("tv-kumanda").apply { setReferenceCounted(false); acquire() }
        }
        runCatching { nsd.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { nsd.stopServiceDiscovery(discoveryListener) }
        runCatching { lock?.release() }
    }

    @Synchronized
    private fun enqueue(info: NsdServiceInfo) {
        queue.addLast(info)
        if (!resolving) resolveNext()
    }

    @Synchronized
    private fun resolveNext() {
        val next = queue.removeFirstOrNull()
        if (next == null) { resolving = false; return }
        resolving = true
        @Suppress("DEPRECATION")
        nsd.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) = resolveNext()
            override fun onServiceResolved(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                info.host?.hostAddress?.let { onFound(TvDevice(info.serviceName, it)) }
                resolveNext()
            }
        })
    }
}
