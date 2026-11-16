package com.tvkumanda.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvkumanda.app.tv.CertManager
import com.tvkumanda.app.tv.PairingClient
import com.tvkumanda.app.tv.PairingException
import com.tvkumanda.app.tv.RemoteClient
import com.tvkumanda.app.tv.TvDevice
import com.tvkumanda.app.tv.TvDiscovery
import com.tvkumanda.app.tv.VoiceStreamer
import com.tvkumanda.app.tv.Live
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { DEVICES, PAIRING, REMOTE }

data class CustomApp(val name: String, val target: String)

data class Channel(val number: Int, val name: String, val favorite: Boolean = false)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tv_kumanda", 0)

    var screen by mutableStateOf(Screen.DEVICES); private set
    var status by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var host by mutableStateOf<String?>(null); private set
    var tvName by mutableStateOf<String?>(null); private set
    var powerOn by mutableStateOf<Boolean?>(null); private set
    var volume by mutableStateOf<Pair<Int, Int>?>(null); private set
    val devices = mutableStateListOf<TvDevice>()
    val customApps = mutableStateListOf<CustomApp>()
    val channels = mutableStateListOf<Channel>()
    var switchToLiveFirst by mutableStateOf(true); private set

    // ---------- settings ----------
    var haptics by mutableStateOf(true); private set
    var keepScreenOn by mutableStateOf(false); private set
    var notificationRemote by mutableStateOf(false); private set
    var sleepAt by mutableStateOf<Long?>(null); private set
    val savedTvs = mutableStateListOf<TvDevice>()
    var currentApp by mutableStateOf<Pair<String, String?>?>(null); private set
    var imeReady by mutableStateOf(false); private set

    private var pairing: PairingClient? = null
    private var remote: RemoteClient? = null
        set(value) { field = value; Live.client = value }

    /** Set when connecting to an already-paired TV failed: UI offers "retry" or "pair again". */
    var failedHost by mutableStateOf<String?>(null); private set
    var reconnecting by mutableStateOf(false); private set

    private val discovery = TvDiscovery(app) { device ->
        viewModelScope.launch {
            if (devices.none { it.host == device.host }) devices.add(device)
            // TV got a new IP from the router: follow it, pairing does not depend on the IP.
            val savedName = prefs.getString("name", null)
            val savedHost = prefs.getString("host", null)
            if (device.name == savedName && savedHost != null && savedHost != device.host) {
                prefs.edit().putString("host", device.host).apply()
                if (failedHost == savedHost) failedHost = device.host
                if (remote == null && !busy && screen == Screen.DEVICES) connect(device.host, device.name, autoPair = false)
            }
        }
    }

    private fun isPaired(target: String, name: String?): Boolean {
        val set = prefs.getStringSet("paired", emptySet()) ?: emptySet()
        return target in set || (name != null && "name:$name" in set)
    }

    private fun markPaired(target: String, name: String?) {
        val set = (prefs.getStringSet("paired", emptySet()) ?: emptySet()).toMutableSet()
        set.add(target)
        if (name != null) set.add("name:$name")
        prefs.edit().putStringSet("paired", set).apply()
    }

    /** Tries the command port a few times; null on success, else the last error. */
    private suspend fun tryConnect(target: String, attempts: Int, quietDelayMs: Long = 0): Throwable? {
        var last: Throwable? = null
        for (i in 0 until attempts) {
            if (i > 0 || quietDelayMs > 0) delay(if (i == 0) quietDelayMs else 1500L * i)
            val client = RemoteClient(target, remoteListener)
            val r = withContext(Dispatchers.IO) { runCatching { client.connect() } }
            if (r.isSuccess) {
                remote = client
                host = target
                markPaired(target, tvName)
                prefs.edit().putString("host", target).putString("name", tvName).apply()
                rememberTv(tvName ?: target, target)
                return null
            }
            last = r.exceptionOrNull()
        }
        return last ?: IllegalStateException("bilinmeyen hata")
    }

    fun connect(target: String, name: String? = null, autoPair: Boolean = true) {
        if (busy) return
        busy = true
        failedHost = null
        status = "Bağlanıyor…"
        host = target
        tvName = name ?: tvName?.takeIf { prefs.getString("host", null) == target } ?: target
        viewModelScope.launch {
            val paired = isPaired(target, tvName)
            val error = tryConnect(target, attempts = if (paired) 3 else 1)
            busy = false
            when {
                error == null -> { status = null; screen = Screen.REMOTE }
                // Only a TV we have never controlled needs the code.
                !paired && autoPair -> startPairing(target)
                else -> {
                    failedHost = target
                    status = "TV'ye bağlanılamadı. TV açık ve aynı Wi-Fi'de mi?\n(${error.describe()})"
                }
            }
        }
    }

    fun retry() { failedHost?.let { connect(it, autoPair = false) } }

    fun pairAgain() {
        val target = failedHost ?: host ?: return
        failedHost = null
        startPairing(target)
    }

    /** Silent reconnect after the link dropped (TV slept, Wi-Fi blip, app was in background). */
    private fun reconnect() {
        val target = host ?: prefs.getString("host", null) ?: return
        if (reconnecting || busy) return
        reconnecting = true
        viewModelScope.launch {
            val error = tryConnect(target, attempts = 4, quietDelayMs = 700)
            reconnecting = false
            if (error == null) {
                status = null
            } else {
                screen = Screen.DEVICES
                failedHost = target
                status = "TV ile bağlantı koptu.\n(${error.describe()})"
            }
        }
    }

    /** Called when the app comes back to the screen. */
    fun onForeground() {
        sleepAt = SleepTimer.endsAt(getApplication())
        if (notificationRemote) RemoteNotification.show(getApplication())
        if (remote != null || busy || reconnecting || screen == Screen.PAIRING) return
        val saved = prefs.getString("host", null) ?: return
        if (screen == Screen.REMOTE) reconnect() else connect(saved, prefs.getString("name", null), autoPair = false)
    }

    private fun startPairing(target: String) {
        busy = true
        status = "Eşleştirme başlatılıyor…"
        viewModelScope.launch {
            val client = PairingClient(target)
            val result = withContext(Dispatchers.IO) { runCatching { client.start() } }
            busy = false
            if (result.isSuccess) {
                pairing = client
                status = null
                screen = Screen.PAIRING
            } else {
                status = "Eşleştirme başlatılamadı.\n(${result.exceptionOrNull().describe()})"
            }
        }
    }

    fun submitCode(code: String) {
        val client = pairing ?: return
        val target = host ?: return
        busy = true
        status = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { client.finish(code) } }
            busy = false
            val error = result.exceptionOrNull()
            when {
                error == null -> { pairing = null; markPaired(target, tvName); connect(target, tvName, autoPair = false) }
                error is PairingException && error.message?.startsWith("Kod") == true -> status = error.message
                else -> { pairing = null; screen = Screen.DEVICES; status = "Eşleştirme başarısız.\n(${error.describe()})" }
            }
        }
    }

    fun cancelPairing() {
        pairing?.close(); pairing = null
        screen = Screen.DEVICES
        status = null
    }

    fun disconnect() {
        remote?.close(); remote = null
        prefs.edit().remove("host").apply()
        screen = Screen.DEVICES
        powerOn = null
        volume = null
    }

    fun refresh() {
        devices.clear()
        discovery.stop()
        discovery.start()
    }

    fun key(code: Int) {
        val r = remote ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { r.sendKey(code) } }
    }

    var listening by mutableStateOf(false); private set
    private var voice: VoiceStreamer? = null

    fun toggleVoice() {
        if (listening) { voice?.stop(); return }
        val r = remote ?: return
        val v = VoiceStreamer(r)
        voice = v
        listening = true
        status = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { v.run() } }
            listening = false
            voice = null
            result.exceptionOrNull()?.let { status = "Sesli komut: ${it.message}" }
        }
    }

    fun micDenied() { status = "Sesli komut için mikrofon izni gerekiyor." }

    fun clearStatus() { status = null }

    fun addApp(name: String, target: String) {
        val n = name.trim().ifEmpty { target.trim() }
        val t = target.trim()
        if (t.isEmpty() || customApps.any { it.target == t }) return
        customApps.add(CustomApp(n, t)); saveApps()
    }

    fun removeApp(app: CustomApp) { customApps.remove(app); saveApps() }

    private fun saveApps() {
        prefs.edit().putString("apps", customApps.joinToString("\n") { "${it.name}\t${it.target}" }).apply()
    }

    private fun rememberTv(name: String, target: String) {
        savedTvs.removeAll { it.name == name || it.host == target }
        savedTvs.add(0, TvDevice(name, target))
        prefs.edit().putString("tvs", savedTvs.joinToString("\n") { "${it.name}\t${it.host}" }).apply()
    }

    /** Switch to another TV that was paired before (no code needed). */
    fun switchTo(tv: TvDevice) {
        if (tv.host == host && remote != null) return
        remote?.close(); remote = null
        powerOn = null; volume = null
        connect(tv.host, tv.name, autoPair = true)
    }

    fun forgetTv(tv: TvDevice) {
        savedTvs.remove(tv)
        prefs.edit().putString("tvs", savedTvs.joinToString("\n") { "${it.name}\t${it.host}" }).apply()
    }

    fun changeHaptics(on: Boolean) { haptics = on; prefs.edit().putBoolean("haptics", on).apply() }
    fun changeKeepScreen(on: Boolean) { keepScreenOn = on; prefs.edit().putBoolean("keep_screen", on).apply() }

    fun changeNotificationRemote(on: Boolean) {
        notificationRemote = on
        prefs.edit().putBoolean("notif_remote", on).apply()
        val ctx = getApplication<Application>()
        if (on) RemoteNotification.show(ctx) else RemoteNotification.hide(ctx)
    }

    fun startSleepTimer(minutes: Int) {
        val ctx = getApplication<Application>()
        SleepTimer.set(ctx, minutes)
        sleepAt = SleepTimer.endsAt(ctx)
    }

    fun cancelSleepTimer() {
        SleepTimer.cancel(getApplication())
        sleepAt = null
    }

    // ---------- channel list ----------

    private fun saveChannels() {
        channels.sortBy { it.number }
        prefs.edit().putString(
            "channels", channels.joinToString("\n") { "${it.number}\t${it.name}\t${if (it.favorite) 1 else 0}" }
        ).apply()
    }

    /** Adds or replaces (when [old] is given) a channel. Returns an error message or null. */
    fun saveChannel(old: Channel?, number: Int, name: String): String? {
        val n = name.trim().ifEmpty { "Kanal $number" }
        if (number <= 0) return "Kanal numarası 1 veya daha büyük olmalı"
        if (channels.any { it.number == number && it != old }) return "$number numarası zaten listede"
        val fav = old?.favorite ?: false
        if (old != null) channels.remove(old)
        channels.add(Channel(number, n, fav))
        saveChannels()
        return null
    }

    fun deleteChannel(c: Channel) { channels.remove(c); saveChannels() }

    fun toggleFavorite(c: Channel) {
        val i = channels.indexOf(c)
        if (i >= 0) { channels[i] = c.copy(favorite = !c.favorite); saveChannels() }
    }

    fun addChannelTemplate(names: List<String>) {
        var next = (channels.maxOfOrNull { it.number } ?: 0) + 1
        names.forEach { n ->
            if (channels.none { it.name.equals(n, ignoreCase = true) }) channels.add(Channel(next++, n))
        }
        saveChannels()
    }

    fun setLiveFirst(on: Boolean) { switchToLiveFirst = on; prefs.edit().putBoolean("live_first", on).apply() }

    /** Switches the TV to the channel by typing its number, like the number keys on the remote. */
    fun tune(c: Channel) {
        val r = remote ?: return
        val live = switchToLiveFirst
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (live) { r.sendKey(com.tvkumanda.app.tv.Keys.LIVE_TV); delay(1500) }
                c.number.toString().forEach { d ->
                    r.sendKey(com.tvkumanda.app.tv.Keys.digit(d - '0')); delay(250)
                }
            }
        }
        status = null
    }

    /** A package name, an https link, or a scheme like spotify://. */
    fun openApp(target: String) =
        app(if (target.contains("://") || target.startsWith("intent:")) target else "market://launch?id=$target")

    fun sendText(text: String) {
        val r = remote ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { if (text.isEmpty()) r.sendKey(com.tvkumanda.app.tv.Keys.DEL) else r.sendText(text) }
        }
    }

    fun app(link: String) {
        val r = remote ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { r.launchApp(link) } }
    }

    val isConnected get() = remote != null && screen == Screen.REMOTE

    private val remoteListener = object : RemoteClient.Listener {
        override fun onPower(on: Boolean) { Live.powerOn = on; viewModelScope.launch { powerOn = on } }
        override fun onVolume(level: Int, max: Int, muted: Boolean) {
            viewModelScope.launch { volume = if (muted) 0 to max else level to max }
        }
        override fun onCurrentApp(pkg: String, label: String?) {
            viewModelScope.launch { currentApp = pkg to label }
        }
        override fun onImeReady() { viewModelScope.launch { imeReady = true } }
        override fun onDisconnected(error: Throwable?) {
            viewModelScope.launch {
                remote = null
                reconnect()
            }
        }
    }

    // Must stay below all properties: it starts connecting, which uses remoteListener.
    init {
        prefs.getString("apps", null)?.lines()?.forEach { line ->
            val parts = line.split('\t')
            if (parts.size == 2) customApps.add(CustomApp(parts[0], parts[1]))
        }
        prefs.getString("channels", null)?.lines()?.forEach { line ->
            val p = line.split('\t')
            val n = p.getOrNull(0)?.toIntOrNull()
            if (n != null && p.size >= 2) channels.add(Channel(n, p[1], p.getOrNull(2) == "1"))
        }
        switchToLiveFirst = prefs.getBoolean("live_first", true)
        haptics = prefs.getBoolean("haptics", true)
        keepScreenOn = prefs.getBoolean("keep_screen", false)
        notificationRemote = prefs.getBoolean("notif_remote", false)
        sleepAt = SleepTimer.endsAt(app)
        prefs.getString("tvs", null)?.lines()?.forEach { line ->
            val p = line.split('\t')
            if (p.size == 2) savedTvs.add(TvDevice(p[0], p[1]))
        }
        // TVs saved by older versions were already paired.
        prefs.getString("host", null)?.let { h ->
            val n = prefs.getString("name", null)
            markPaired(h, n)
            if (savedTvs.none { it.host == h }) rememberTv(n ?: h, h)
        }
        CertManager.init(app)
        discovery.start()
        viewModelScope.launch(Dispatchers.IO) { runCatching { CertManager.ensureKey() } }
        prefs.getString("host", null)?.let { connect(it, prefs.getString("name", null), autoPair = false) }
    }

    override fun onCleared() {
        discovery.stop()
        remote?.close()
        pairing?.close()
    }
}

private fun Throwable?.describe(): String {
    if (this == null) return "bilinmeyen hata"
    val root = generateSequence(this) { it.cause }.last()
    return "${root.javaClass.simpleName}: ${root.message ?: "-"}"
}
