package com.tvkumanda.app.tv

import android.os.Build
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/** Android KeyEvent codes understood by the TV. */
object Keys {
    const val POWER = 26
    const val HOME = 3
    const val BACK = 4
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val OK = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val MUTE = 164
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val PLAY_PAUSE = 85
    const val NEXT = 87
    const val PREVIOUS = 88
    const val REWIND = 89
    const val FAST_FORWARD = 90
    const val SETTINGS = 176
    const val INPUT = 178
    const val SEARCH = 84
    const val DEL = 67
    const val MENU = 82
    const val INFO = 165
    const val GUIDE = 172
    const val CAPTIONS = 175
    const val LIVE_TV = 170
    const val RED = 183
    const val GREEN = 184
    const val YELLOW = 185
    const val BLUE = 186
    const val AUDIO_TRACK = 222
    const val LAST_CHANNEL = 229
    const val ALL_APPS = 284
    const val PROFILE = 288
    const val ASSIST = 219
    const val EXIT = 111
    const val RECORD = 130
    const val STOP = 86
    const val TELETEXT = 233
    const val ENTER = 66
    fun digit(n: Int) = 7 + n // KEYCODE_0 = 7
}

/** Command connection on port 6466. Works only after the phone has been paired. */
class RemoteClient(private val host: String, private val listener: Listener) {

    interface Listener {
        fun onPower(on: Boolean)
        fun onVolume(level: Int, max: Int, muted: Boolean)
        fun onDisconnected(error: Throwable?)
        fun onCurrentApp(pkg: String, label: String?) {}
        fun onImeReady() {}
    }

    @Volatile private var imeCounter = 0L
    @Volatile private var fieldCounter = 0L

    private var socket: SSLSocket? = null

    /** Called when the TV opens a voice session (after KEYCODE_SEARCH). */
    @Volatile var onVoiceSession: ((Int) -> Unit)? = null
    @Volatile private var closed = false

    /** Throws if the TV refuses us (usually means pairing is needed). */
    fun connect() {
        val s = Tls.connect(host, 6466)
        socket = s
        // An unpaired client is dropped right after the handshake, so read the first message here.
        handle(ProtoMsg.parse(Framing.read(s.inputStream)))
        s.soTimeout = 30_000 // TV pings every few seconds
        thread(isDaemon = true, name = "tv-remote") {
            var error: Throwable? = null
            try {
                while (!closed) handle(ProtoMsg.parse(Framing.read(s.inputStream)))
            } catch (e: Throwable) {
                error = e
            }
            if (!closed) {
                close()
                listener.onDisconnected(error)
            }
        }
    }

    fun sendKey(keyCode: Int, direction: Int = 3 /* SHORT */) =
        send { msg(10) { int(1, keyCode); int(2, direction) } }

    fun voiceBegin(session: Int) = send { msg(30) { int(1, session.toLong()) } }
    fun voicePayload(session: Int, samples: ByteArray) =
        send { msg(31) { int(1, session.toLong()); bytes(2, samples) } }
    fun voiceEnd(session: Int) = send { msg(32) { int(1, session.toLong()) } }

    /** Replaces the focused TV text field's content with [text]. */
    fun sendText(text: String) {
        if (text.isEmpty()) return
        val pos = (text.length - 1).toLong()
        send {
            msg(21) {
                int(1, imeCounter)
                int(2, fieldCounter)
                msg(3) { int(1, 1); msg(2) { int(1, pos); int(2, pos); string(3, text) } }
            }
        }
    }

    fun launchApp(link: String) = send { msg(90) { string(1, link) } }

    fun close() {
        closed = true
        runCatching { socket?.close() }
    }

    private fun handle(m: ProtoMsg) {
        when {
            m.has(1) -> send { // remote_configure
                msg(1) {
                    int(1, 622)
                    msg(2) {
                        string(1, Build.MODEL)
                        string(2, Build.MANUFACTURER)
                        int(3, 1)
                        string(4, "1")
                        string(5, "com.tvkumanda.app")
                        string(6, "1.0")
                    }
                }
            }
            m.has(2) -> send { msg(2) { int(1, 622) } } // remote_set_active
            m.has(8) -> send { msg(9) { int(1, m.msg(8)?.long(1) ?: 0L) } } // ping -> pong
            m.has(20) -> m.msg(20)?.msg(1)?.let { info ->
                info.string(12)?.takeIf { it.isNotBlank() }?.let { listener.onCurrentApp(it, info.string(10)) }
            }
            m.has(21) -> m.msg(21)?.let {
                imeCounter = it.long(1) ?: 0L
                fieldCounter = it.long(2) ?: 0L
                listener.onImeReady()
            }
            m.has(30) -> onVoiceSession?.invoke((m.msg(30)?.long(1) ?: 0L).toInt())
            m.has(40) -> listener.onPower(m.msg(40)?.long(1) == 1L)
            m.has(50) -> m.msg(50)?.let {
                listener.onVolume(
                    (it.long(7) ?: 0).toInt(), (it.long(6) ?: 100).toInt(), it.long(8) == 1L
                )
            }
        }
    }

    private fun send(block: ProtoWriter.() -> Unit) {
        val s = socket ?: return
        Framing.write(s.outputStream, ProtoWriter().apply(block).toByteArray())
    }
}
