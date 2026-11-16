package com.tvkumanda.app.tv

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Voice search like the physical remote's mic button:
 * KEYCODE_SEARCH -> TV sends voice_begin(session) -> we echo it ->
 * stream 16-bit PCM, mono, 8 kHz in 20 KB payloads -> voice_end.
 * Recording stops on [stop], after ~1.5 s of silence following speech, or after 12 s.
 */
class VoiceStreamer(private val remote: RemoteClient) {
    @Volatile private var running = false

    fun stop() { running = false }

    @SuppressLint("MissingPermission")
    suspend fun run() {
        val session = CompletableDeferred<Int>()
        remote.onVoiceSession = { if (!session.isCompleted) session.complete(it) }
        val rate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate * 2)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); remote.onVoiceSession = null
            throw IllegalStateException("Mikrofon açılamadı")
        }
        running = true
        val pcm8k = ByteArrayOutputStream()
        var sent = 0
        var sid: Int? = null
        val start = System.currentTimeMillis()
        var heardSpeech = false
        var quietReads = 0
        try {
            rec.startRecording()
            remote.sendKey(Keys.SEARCH)
            val buf = ShortArray(1600) // 100 ms at 16 kHz
            while (running && currentCoroutineContext().isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    var sum = 0.0
                    var i = 0
                    while (i + 1 < n) { // 16 kHz -> 8 kHz by averaging pairs
                        val v = (buf[i] + buf[i + 1]) / 2
                        pcm8k.write(v and 0xFF); pcm8k.write((v shr 8) and 0xFF)
                        sum += v.toDouble() * v
                        i += 2
                    }
                    val rms = sqrt(sum / (n / 2).coerceAtLeast(1))
                    if (rms > 1200) { heardSpeech = true; quietReads = 0 } else quietReads++
                }
                val elapsed = System.currentTimeMillis() - start
                if (sid == null && session.isCompleted) { sid = session.await(); remote.voiceBegin(sid) }
                if (sid == null && elapsed > 5000) throw IllegalStateException("TV sesli aramayı başlatmadı")
                if (sid != null) {
                    val all = pcm8k.toByteArray()
                    while (all.size - sent >= CHUNK) {
                        remote.voicePayload(sid, all.copyOfRange(sent, sent + CHUNK)); sent += CHUNK
                    }
                }
                if (heardSpeech && quietReads >= 15) break
                if (!heardSpeech && elapsed > 7000) break
                if (elapsed > 12_000) break
            }
            if (sid == null) sid = withTimeoutOrNull(3000) { session.await() }?.also { remote.voiceBegin(it) }
            val id = sid ?: throw IllegalStateException("TV sesli aramayı başlatmadı")
            val all = pcm8k.toByteArray()
            while (sent < all.size) {
                val chunk = ByteArray(CHUNK) // last one padded with silence
                val len = minOf(CHUNK, all.size - sent)
                System.arraycopy(all, sent, chunk, 0, len)
                remote.voicePayload(id, chunk); sent += len
            }
            remote.voiceEnd(id)
        } finally {
            running = false
            runCatching { rec.stop() }
            rec.release()
            remote.onVoiceSession = null
        }
    }

    private companion object { const val CHUNK = 20 * 1024 }
}
