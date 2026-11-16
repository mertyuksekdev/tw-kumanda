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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { DEVICES, PAIRING, REMOTE }

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

    private var pairing: PairingClient? = null
    private var remote: RemoteClient? = null

    private val discovery = TvDiscovery(app) { device ->
        viewModelScope.launch {
            if (devices.none { it.host == device.host }) devices.add(device)
        }
    }

    init {
        CertManager.init(app)
        discovery.start()
        viewModelScope.launch(Dispatchers.IO) { runCatching { CertManager.ensureKey() } }
        prefs.getString("host", null)?.let { connect(it, prefs.getString("name", null), autoPair = false) }
    }

    fun connect(target: String, name: String? = null, autoPair: Boolean = true) {
        if (busy) return
        busy = true
        status = "Bağlanıyor…"
        host = target
        tvName = name ?: target
        viewModelScope.launch {
            val client = RemoteClient(target, remoteListener)
            val result = withContext(Dispatchers.IO) { runCatching { client.connect() } }
            if (result.isSuccess) {
                remote = client
                prefs.edit().putString("host", target).putString("name", tvName).apply()
                status = null
                screen = Screen.REMOTE
                busy = false
            } else if (autoPair) {
                busy = false
                startPairing(target)
            } else {
                busy = false
                status = "Kayıtlı TV'ye ulaşılamadı. Listeden seçin veya IP girin.\n(${result.exceptionOrNull().describe()})"
            }
        }
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
                error == null -> { pairing = null; connect(target, tvName, autoPair = false) }
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

    fun app(link: String) {
        val r = remote ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { r.launchApp(link) } }
    }

    val isConnected get() = remote != null && screen == Screen.REMOTE

    private val remoteListener = object : RemoteClient.Listener {
        override fun onPower(on: Boolean) { viewModelScope.launch { powerOn = on } }
        override fun onVolume(level: Int, max: Int, muted: Boolean) {
            viewModelScope.launch { volume = if (muted) 0 to max else level to max }
        }
        override fun onDisconnected(error: Throwable?) {
            viewModelScope.launch {
                remote = null
                screen = Screen.DEVICES
                status = "TV ile bağlantı koptu.\n(${error.describe()})"
            }
        }
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
