package com.tvkumanda.app.tv

import android.annotation.SuppressLint
import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * The TV identifies this phone by a client certificate. We generate an RSA key and a
 * self-signed certificate once, store them in the app's private storage, and reuse them.
 */
object CertManager {
    private lateinit var dir: File
    private var key: PrivateKey? = null
    private var cert: X509Certificate? = null

    fun init(context: Context) { dir = context.filesDir }

    @Synchronized
    fun ensureKey() {
        if (key != null && cert != null) return
        val keyFile = File(dir, "client_key.der")
        val certFile = File(dir, "client_cert.der")
        if (keyFile.exists() && certFile.exists()) {
            runCatching {
                key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certFile.inputStream()) as X509Certificate
                return
            }
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=atvremote/TvKumanda, O=TvKumanda, C=TR")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(now), Date(now - 86_400_000L),
            Date(now + 25L * 365 * 86_400_000L), name, kp.public
        )
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(kp.private)
        val c = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        keyFile.writeBytes(kp.private.encoded)
        certFile.writeBytes(c.encoded)
        key = kp.private
        cert = c
    }

    val certificate: X509Certificate
        get() { ensureKey(); return cert!! }

    private val privateKey: PrivateKey
        get() { ensureKey(); return key!! }

    fun keyManager(): X509ExtendedKeyManager {
        val k = privateKey
        val chain = arrayOf(certificate)
        return object : X509ExtendedKeyManager() {
            override fun chooseClientAlias(t: Array<out String>?, i: Array<out Principal>?, s: Socket?) = ALIAS
            override fun chooseEngineClientAlias(t: Array<out String>?, i: Array<out Principal>?, e: SSLEngine?) = ALIAS
            override fun getClientAliases(t: String?, i: Array<out Principal>?) = arrayOf(ALIAS)
            override fun getServerAliases(t: String?, i: Array<out Principal>?): Array<String>? = null
            override fun chooseServerAlias(t: String?, i: Array<out Principal>?, s: Socket?): String? = null
            override fun getCertificateChain(alias: String?) = chain
            override fun getPrivateKey(alias: String?) = k
        }
    }

    private const val ALIAS = "client"
}

object Tls {
    /** TVs use self-signed certificates, so we accept the server certificate as-is. */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun connect(host: String, port: Int): SSLSocket {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(arrayOf(CertManager.keyManager()), arrayOf(trustAll), SecureRandom())
        val socket = ctx.socketFactory.createSocket() as SSLSocket
        socket.connect(InetSocketAddress(host, port), 5000)
        socket.soTimeout = 10_000
        socket.startHandshake()
        return socket
    }
}
