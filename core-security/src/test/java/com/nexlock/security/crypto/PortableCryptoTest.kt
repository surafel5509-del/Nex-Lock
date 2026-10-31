package com.nexlock.security.crypto

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PortableCryptoTest {

    private val payload = "{\"notes\":\"private\"}".toByteArray()
    private val passphrase = "correct horse battery".toCharArray()

    @Test
    fun `round trip recovers the exact plaintext`() {
        val encrypted = PortableCrypto.encrypt(passphrase, payload)
        assertFalse(encrypted.contentEquals(payload))
        assertTrue(PortableCrypto.isBackupFile(encrypted))
        assertArrayEquals(payload, PortableCrypto.decrypt(passphrase, encrypted))
    }

    @Test
    fun `wrong passphrase fails with a crypto exception`() {
        val encrypted = PortableCrypto.encrypt(passphrase, payload)
        try {
            PortableCrypto.decrypt("wrong passphrase!".toCharArray(), encrypted)
            fail("Expected CryptoException")
        } catch (expected: CryptoException) {
            assertTrue(expected.message!!.contains("Wrong passphrase", ignoreCase = true))
        }
    }

    @Test
    fun `tampering with ciphertext is detected`() {
        val encrypted = PortableCrypto.encrypt(passphrase, payload)
        encrypted[encrypted.size - 2] = (encrypted[encrypted.size - 2].toInt() xor 0x01).toByte()
        try {
            PortableCrypto.decrypt(passphrase, encrypted)
            fail("Tampered payload must not decrypt")
        } catch (expected: CryptoException) {
            // expected
        }
    }

    @Test
    fun `each encryption uses fresh salt and iv`() {
        val a = PortableCrypto.encrypt(passphrase, payload)
        val b = PortableCrypto.encrypt(passphrase, payload)
        assertNotEquals(a.toList(), b.toList())
        assertArrayEquals(payload, PortableCrypto.decrypt(passphrase, a))
        assertArrayEquals(payload, PortableCrypto.decrypt(passphrase, b))
    }

    @Test
    fun `short passphrases are rejected`() {
        try {
            PortableCrypto.encrypt("short".toCharArray(), payload)
            fail("Short passphrase must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `random bytes are not mistaken for a backup`() {
        val noise = ByteArray(64).also { SecureRandom().nextBytes(it) }
        assertFalse(PortableCrypto.isBackupFile(noise))
    }
}
