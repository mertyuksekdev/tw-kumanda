package com.tvkumanda.app.tv

import android.os.Build
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingException(message: String) : Exception(message)

/**
 * Pairing on port 6467. After [start], the TV shows a 6-character hex code;
 * [finish] sends the secret derived from that code and both certificates.
 */
class PairingClient(private val host: String) {
    private lateinit var socket: SSLSocket

    fun start() {
        socket = Tls.connect(host, 6467)
        socket.soTimeout = 15_000
        send { msg(10) { string(1, "atvremote"); string(2, "${Build.MANUFACTURER} ${Build.MODEL}") } }
        expect(11)
        // HEXADECIMAL encoding (3), 6 symbols, we are the input device (role 1)
        send { msg(20) { msg(1) { int(1, 3); int(2, 6) }; int(3, 1) } }
        expect(20)
        send { msg(30) { msg(1) { int(1, 3); int(2, 6) }; int(2, 1) } }
        expect(31)
        socket.soTimeout = 0 // the user may take a while to read the code
    }

    fun finish(rawCode: String) {
        val code = rawCode.trim().uppercase()
        if (!code.matches(Regex("[0-9A-F]{6}"))) throw PairingException("Kod 6 karakter olmalı (0-9, A-F)")
        val secret = computeSecret(code)
        socket.soTimeout = 15_000
        send { msg(40) { bytes(1, secret) } }
        expect(41)
        close()
    }

    fun close() = runCatching { socket.close() }

    private fun computeSecret(code: String): ByteArray {
        val client = CertManager.certificate.publicKey as RSAPublicKey
        val server = (socket.session.peerCertificates[0] as X509Certificate).publicKey as RSAPublicKey
        val md = MessageDigest.getInstance("SHA-256")
        md.update(client.modulus.unsignedBytes())
        md.update(client.publicExponent.unsignedBytes())
        md.update(server.modulus.unsignedBytes())
        md.update(server.publicExponent.unsignedBytes())
        md.update(hex(code.substring(2)))
        val hash = md.digest()
        if (hash[0] != hex(code.substring(0, 2))[0]) throw PairingException("Kod yanlış, tekrar deneyin")
        return hash
    }

    private fun send(block: ProtoWriter.() -> Unit) {
        val w = ProtoWriter()
        w.int(1, 2)   // protocol_version
        w.int(2, 200) // status OK
        w.block()
        Framing.write(socket.outputStream, w.toByteArray())
    }

    private fun expect(field: Int): ProtoMsg {
        val m = ProtoMsg.parse(Framing.read(socket.inputStream))
        val status = m.long(2)
        if (status != 200L) throw PairingException("TV eşleştirmeyi reddetti (durum: $status)")
        if (!m.has(field)) throw PairingException("TV'den beklenmeyen yanıt")
        return m
    }

    private fun BigInteger.unsignedBytes(): ByteArray {
        val b = toByteArray()
        return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
