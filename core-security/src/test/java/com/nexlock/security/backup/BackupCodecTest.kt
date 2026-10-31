package com.nexlock.security.backup

import com.nexlock.security.crypto.CryptoException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val passphrase = "backup-passphrase-1".toCharArray()

    private fun payload() = BackupPayload(
        createdAt = 1_767_000_000_000,
        appVersion = "1.0.0",
        settings = mapOf("theme" to "DARK", "authMode" to "PIN"),
        lockedApps = listOf(LockedAppBackup("com.example.bank", "Bank", "FINANCE", 100)),
        notes = listOf(NoteBackup("Recovery", "keep this safe", 1, 2)),
        schedules = listOf(ScheduleBackup("Night", 1320, 420, 127, true)),
        trustedNetworks = listOf("HomeWiFi"),
        locationRules = listOf(LocationRuleBackup("Home", 9.0, 38.7, 150f, true)),
    )

    @Test
    fun `round trip preserves every field`() {
        val encoded = BackupCodec.encode(payload(), passphrase)
        val decoded = BackupCodec.decode(encoded, passphrase).getOrThrow()
        assertEquals(payload(), decoded)
    }

    @Test
    fun `backup file does not leak plaintext`() {
        val encoded = BackupCodec.encode(payload(), passphrase)
        val asText = encoded.toString(Charsets.ISO_8859_1)
        assertFalse(asText.contains("com.example.bank"))
        assertFalse(asText.contains("HomeWiFi"))
        assertFalse(asText.contains("keep this safe"))
    }

    @Test
    fun `wrong passphrase is reported as a failure result`() {
        val encoded = BackupCodec.encode(payload(), passphrase)
        val result = BackupCodec.decode(encoded, "not-the-passphrase".toCharArray())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CryptoException)
    }

    @Test
    fun `foreign files are rejected`() {
        val result = BackupCodec.decode("hello world".toByteArray(), passphrase)
        assertTrue(result.isFailure)
    }
}
