package co.yooz.hamresan.crypto

import android.util.Base64
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

/** Ed25519 helpers. Keys are exchanged as base64 of the raw 32 bytes. */
object Crypto {
    private val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s.trim(), Base64.DEFAULT)

    fun newSeed(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun publicFromSeed(seed: ByteArray): ByteArray {
        val priv = EdDSAPrivateKeySpec(seed, spec)
        return EdDSAPublicKey(EdDSAPublicKeySpec(priv.a, spec)).abyte
    }

    fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        val key = EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, spec))
        val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
        engine.initSign(key)
        engine.update(message)
        return engine.sign()
    }

    fun verify(pub: ByteArray, message: ByteArray, sig: ByteArray): Boolean {
        return try {
            if (pub.size != 32 || sig.size != 64) return false
            val key = EdDSAPublicKey(EdDSAPublicKeySpec(pub, spec))
            val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
            engine.initVerify(key)
            engine.update(message)
            engine.verify(sig)
        } catch (e: Exception) {
            false
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }

    /** Short human-readable id for a public key (first 8 hex chars of its hash). */
    fun shortId(pubB64: String): String = sha256Hex(unb64(pubB64)).take(8)
}
