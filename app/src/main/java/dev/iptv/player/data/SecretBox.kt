package dev.iptv.player.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Reversible string protection. Values written by [encrypt] carry [PREFIX]; anything without it is legacy plain text. */
interface Codec {
    fun encrypt(plain: String): String

    /** Plain (un-prefixed) input is returned unchanged, which is what makes migration transparent. */
    fun decrypt(stored: String): String

    companion object {
        const val PREFIX = "enc:v1:"
        fun isEncrypted(value: String) = value.startsWith(PREFIX)
    }
}

/** AES-256/GCM with a random 12 byte IV per value, stored as `enc:v1:<hex(iv + ciphertext)>`. */
class AesGcmCodec(private val key: SecretKey) : Codec {
    private val random = SecureRandom()

    override fun encrypt(plain: String): String {
        if (plain.isEmpty() || Codec.isEncrypted(plain)) return plain
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Software keys need an explicit IV; AndroidKeyStore keys refuse caller-supplied IVs, so only generate for those.
        if (key.format == null) {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, ByteArray(IV_BYTES).also(random::nextBytes)))
        }
        val encrypted = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Codec.PREFIX + Hex.encode(encrypted)
    }

    override fun decrypt(stored: String): String {
        if (!Codec.isEncrypted(stored)) return stored
        val bytes = Hex.decode(stored.removePrefix(Codec.PREFIX))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}

internal object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) out.append(DIGITS[(b.toInt() shr 4) and 0xF]).append(DIGITS[b.toInt() and 0xF])
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        require(text.length % 2 == 0) { "odd hex length" }
        return ByteArray(text.length / 2) { ((DIGITS.indexOf(text[it * 2]) shl 4) or DIGITS.indexOf(text[it * 2 + 1])).toByte() }
    }
}

/**
 * Credential protection backed by an AES/GCM key that lives in the Android Keystore and never leaves it.
 * If the Keystore is unusable on a device the value is stored unchanged rather than lost; a value that
 * cannot be decrypted (key wiped, e.g. after an app-data restore on another device) reads as empty.
 */
object SecretBox : Codec {
    private const val ALIAS = "iptv_credentials_v1"

    private val delegate: Codec? by lazy { runCatching { AesGcmCodec(keystoreKey()) }.getOrNull() }

    override fun encrypt(plain: String): String =
        if (plain.isEmpty() || Codec.isEncrypted(plain)) plain else runCatching { delegate?.encrypt(plain) }.getOrNull() ?: plain

    override fun decrypt(stored: String): String =
        if (!Codec.isEncrypted(stored)) stored else runCatching { delegate?.decrypt(stored) }.getOrNull().orEmpty()

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /**
     * Fast codec for bulk data (tens of thousands of stream URLs). A random data key is generated once and
     * stored wrapped by the Keystore key, so per-row work is plain JCE AES-GCM, not a Keystore round trip.
     */
    fun bulk(context: Context): Codec = synchronized(this) {
        bulkCache ?: run {
            val prefs = context.applicationContext.getSharedPreferences("iptv_secrets", Context.MODE_PRIVATE)
            val stored = prefs.getString("dek", null)?.let { decrypt(it) }?.takeIf { it.length == 64 }
            val keyHex = stored ?: Hex.encode(ByteArray(32).also(SecureRandom()::nextBytes)).also {
                prefs.edit().putString("dek", encrypt(it)).apply()
            }
            AesGcmCodec(SecretKeySpec(Hex.decode(keyHex), "AES")).also { bulkCache = it }
        }
    }

    @Volatile
    private var bulkCache: Codec? = null
}
