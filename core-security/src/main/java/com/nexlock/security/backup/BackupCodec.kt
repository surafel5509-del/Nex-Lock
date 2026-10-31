package com.nexlock.security.backup

import com.nexlock.security.crypto.CryptoException
import com.nexlock.security.crypto.PortableCrypto
import kotlinx.serialization.json.Json

/**
 * JSON + AES-256-GCM (passphrase derived) backup codec.
 *
 * The exported file is opaque without the passphrase; the passphrase never leaves the device.
 */
object BackupCodec {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: BackupPayload, passphrase: CharArray): ByteArray {
        require(passphrase.size >= PortableCrypto.MIN_PASSPHRASE_LENGTH) {
            "Backup passphrase must be at least ${PortableCrypto.MIN_PASSPHRASE_LENGTH} characters"
        }
        val serialized = json.encodeToString(BackupPayload.serializer(), payload)
        return PortableCrypto.encrypt(passphrase, serialized.toByteArray(Charsets.UTF_8))
    }

    fun decode(payload: ByteArray, passphrase: CharArray): Result<BackupPayload> = runCatching {
        if (!PortableCrypto.isBackupFile(payload)) {
            throw CryptoException("This file is not a NexLock backup")
        }
        val decrypted = try {
            PortableCrypto.decrypt(passphrase, payload)
        } catch (e: CryptoException) {
            throw CryptoException("Wrong passphrase or corrupted backup", e)
        }
        val text = decrypted.toString(Charsets.UTF_8)
        decrypted.fill(0)
        val parsed = json.decodeFromString(BackupPayload.serializer(), text)
        if (parsed.schemaVersion > BackupPayload.CURRENT_SCHEMA) {
            throw CryptoException("Backup was created by a newer NexLock version")
        }
        parsed
    }
}

/** Serializes settings to and from the string map used in backups. */
fun Map<String, String>.toSettingsMap(): Map<String, String> = this
