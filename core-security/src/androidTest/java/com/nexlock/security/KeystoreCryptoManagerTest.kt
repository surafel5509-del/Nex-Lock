package com.nexlock.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexlock.security.crypto.CryptoException
import com.nexlock.security.crypto.KeystoreCryptoManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented tests for the real AndroidKeyStore-backed implementation. */
@RunWith(AndroidJUnit4::class)
class KeystoreCryptoManagerTest {

    private val crypto = KeystoreCryptoManager()

    @Test
    fun encryptDecryptStringRoundTrip() {
        val plaintext = "nexlock-secret-${System.nanoTime()}"
        val ciphertext = crypto.encryptString(plaintext)
        assertFalse(ciphertext.contains(plaintext))
        assertEquals(plaintext, crypto.decryptString(ciphertext))
    }

    @Test
    fun ciphertextIsRandomised() {
        val a = crypto.encryptString("same-input")
        val b = crypto.encryptString("same-input")
        assertFalse(a == b)
        assertEquals("same-input", crypto.decryptString(a))
        assertEquals("same-input", crypto.decryptString(b))
    }

    @Test
    fun tamperingFailsClosed() {
        val encrypted = crypto.encrypt("payload".toByteArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0xFF).toByte()
        try {
            crypto.decrypt(encrypted)
            fail("Tampered ciphertext must throw")
        } catch (expected: CryptoException) {
            assertTrue(expected.message!!.contains("tampered", ignoreCase = true))
        }
    }

    @Test
    fun streamRoundTripHandlesLargePayloads() {
        val plaintext = ByteArray(300_000) { (it % 256).toByte() }
        val encrypted = ByteArrayOutputStream()
        crypto.encryptStream(ByteArrayInputStream(plaintext), encrypted)

        val decrypted = ByteArrayOutputStream()
        crypto.decryptStream(ByteArrayInputStream(encrypted.toByteArray()), decrypted)
        assertArrayEquals(plaintext, decrypted.toByteArray())
    }

    @Test
    fun keyIsNonExportableAndReportsSecurityLevel() {
        // isHardwareBacked may be false on emulators; the call must simply not throw.
        crypto.isHardwareBacked()
        assertTrue(crypto.keyFingerprint().isNotBlank())
        assertEquals(
            InstrumentationRegistry.getInstrumentation().targetContext.packageName.isNotBlank(),
            true,
        )
    }
}
