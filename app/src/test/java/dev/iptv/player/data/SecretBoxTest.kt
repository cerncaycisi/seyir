package dev.iptv.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class SecretBoxTest {
    private val codec = AesGcmCodec(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())

    @Test
    fun roundTripHidesPlainText() {
        val secret = "http://prov.example/get.php?username=u&password=p%C3%A4ss"
        val stored = codec.encrypt(secret)
        assertTrue(Codec.isEncrypted(stored))
        assertFalse(stored.contains("prov.example"))
        assertEquals(secret, codec.decrypt(stored))
    }

    @Test
    fun ivIsRandomPerValue() {
        assertNotEquals(codec.encrypt("same"), codec.encrypt("same"))
    }

    @Test
    fun legacyPlainValuesPassThrough() {
        assertEquals("plain-password", codec.decrypt("plain-password"))
        assertEquals("", codec.encrypt(""))
        val once = codec.encrypt("x")
        assertEquals(once, codec.encrypt(once))
    }

    @Test(expected = Exception::class)
    fun tamperedCiphertextIsRejected() {
        val stored = codec.encrypt("secret")
        val last = stored.last()
        codec.decrypt(stored.dropLast(1) + if (last == '0') '1' else '0')
    }
}
