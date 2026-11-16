package com.tvkumanda.app.tv

import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Calendar
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * The TV identifies this phone by a client certificate. We keep an RSA key and a
 * self-signed certificate in the Android Keystore; the TV remembers it after pairing.
 */
object CertManager {
    private const val ALIAS = "tv_kumanda_client"

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    fun ensureKey() {
        if (keyStore().containsAlias(ALIAS)) return
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, 25) }.time
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            .setDigests(
                KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512
            )
            .setSignaturePaddings(
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS
            )
            .setCertificateSubject(X500Principal("CN=TV Kumanda"))
            .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
            .setCertificateNotBefore(Date())
            .setCertificateNotAfter(end)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
            initialize(spec)
            generateKeyPair()
        }
    }

    val certificate: X509Certificate
        get() { ensureKey(); return keyStore().getCertificate(ALIAS) as X509Certificate }

    private val privateKey: PrivateKey
        get() { ensureKey(); return keyStore().getKey(ALIAS, null) as PrivateKey }

    fun keyManager(): X509ExtendedKeyManager {
        val key = privateKey
        val chain = arrayOf(certificate)
        return object : X509ExtendedKeyManager() {
            override fun chooseClientAlias(k: Array<out String>?, i: Array<out Principal>?, s: Socket?) = ALIAS
            override fun chooseEngineClientAlias(k: Array<out String>?, i: Array<out Principal>?, e: SSLEngine?) = ALIAS
            override fun getClientAliases(k: String?, i: Array<out Principal>?) = arrayOf(ALIAS)
            override fun getServerAliases(k: String?, i: Array<out Principal>?): Array<String>? = null
            override fun chooseServerAlias(k: String?, i: Array<out Principal>?, s: Socket?): String? = null
            override fun getCertificateChain(alias: String?) = chain
            override fun getPrivateKey(alias: String?) = key
        }
    }
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
