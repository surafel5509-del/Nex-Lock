package com.nexlock.security.crypto

import com.nexlock.common.policy.LockoutPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Security regression suite: guards the invariants we never want to break silently.
 * Runs on the JVM using a software key to exercise the same chunked-container code path.
 */
class SecurityRegressionTest {

    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    private val stream = AeadChunkedStream { key }

    @Test
    fun `chunked stream round trip preserves multi-chunk payloads`() {
        val plaintext = ByteArray(200_000) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()
        stream.encrypt(ByteArrayInputStream(plaintext), encrypted)

        val decrypted = ByteArrayOutputStream()
        stream.decrypt(ByteArrayInputStream(encrypted.toByteArray()), decrypted)
        assertEquals(plaintext.toList(), decrypted.toByteArray().toList())
        assertNotEquals(plaintext.toList(), encrypted.toByteArray().toList())
    }

    @Test
    fun `ciphertext cannot be decrypted with a different key`() {
        val encrypted = ByteArrayOutputStream()
        stream.encrypt(ByteArrayInputStream("secret".toByteArray()), encrypted)
        val otherKey = SecretKeySpec(ByteArray(32) { (it + 9).toByte() }, "AES")
        val otherStream = AeadChunkedStream { otherKey }
        try {
            otherStream.decrypt(ByteArrayInputStream(encrypted.toByteArray()), ByteArrayOutputStream())
            throw AssertionError("Decryption with the wrong key must fail")
        } catch (expected: CryptoException) {
            // expected: GCM tag mismatch
        }
    }

    @Test
    fun `flipping a ciphertext bit is detected`() {
        val encrypted = ByteArrayOutputStream()
        stream.encrypt(ByteArrayInputStream(ByteArray(1024) { 7 }), encrypted)
        val tampered = encrypted.toByteArray().also { it[it.size - 5] = (it[it.size - 5].toInt() xor 0x10).toByte() }
        try {
            stream.decrypt(ByteArrayInputStream(tampered), ByteArrayOutputStream())
            throw AssertionError("Tampered ciphertext must fail authentication")
        } catch (expected: CryptoException) {
            // expected
        }
    }

    @Test
    fun `lockout policy stays conservative`() {
        assertEquals(0L, LockoutPolicy.delayMillisFor(0))
        assertFalse(LockoutPolicy.requiresLockout(LockoutPolicy.FREE_ATTEMPTS - 1))
        assertEquals(3_600_000L, LockoutPolicy.delayMillisFor(99))
    }

    @Test
    fun `base64 encoded keystore payload never equals plaintext`() {
        val crypto = SoftwareCryptoManager(key)
        val plaintext = "1234"
        val ciphertext = crypto.encryptString(plaintext)
        assertFalse(ciphertext.contains(plaintext))
        assertEquals(plaintext, crypto.decryptString(ciphertext))
        assertFalse(Base64.getDecoder().decode(ciphertext).contentEquals(plaintext.toByteArray()))
    }
}
