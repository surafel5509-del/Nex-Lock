package com.nexlock.security.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based AES-256-GCM used exclusively for user-initiated encrypted backups.
 *
 * Unlike [KeystoreCryptoManager] the key is derived from a passphrase the user types, so a backup
 * file can be restored on another device. Runs on plain JCE, therefore fully unit-testable on the
 * JVM without Robolectric or a device.
 *
 * Format: "NXLBK1" | iterations(4) | saltLength(1) | salt | iv(12) | ciphertext||tag
 */
object PortableCrypto {

    const val ITERATIONS = 150_000
    const val MIN_PASSPHRASE_LENGTH = 8

    private val MAGIC = "NXLBK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(passphrase: CharArray, plaintext: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "Passphrase too short" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt, ITERATIONS)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val ciphertext = try {
            cipher.doFinal(plaintext)
        } finally {
            wipe(key.encoded)
        }
        return buildList {
            addAll(MAGIC.toList())
            addAll(intToBytes(ITERATIONS).toList())
            add(salt.size.toByte())
            addAll(salt.toList())
            addAll(iv.toList())
            addAll(ciphertext.toList())
        }.toByteArray()
    }

    fun decrypt(passphrase: CharArray, payload: ByteArray): ByteArray {
        require(payload.size > MAGIC.size + 4 + 1 + SALT_BYTES + IV_BYTES) { "Payload too short" }
        val magic = payload.copyOfRange(0, MAGIC.size)
        if (!magic.contentEquals(MAGIC)) throw CryptoException("Not a NexLock backup file")
        var offset = MAGIC.size
        val iterations = bytesToInt(payload, offset)
        offset += 4
        if (iterations !in 10_000..2_000_000) throw CryptoException("Unsupported key stretching parameters")
        val saltLength = payload[offset].toInt() and 0xFF
        offset += 1
        if (saltLength !in 16..64) throw CryptoException("Malformed salt")
        val salt = payload.copyOfRange(offset, offset + saltLength)
        offset += saltLength
        val iv = payload.copyOfRange(offset, offset + IV_BYTES)
        offset += IV_BYTES
        val ciphertext = payload.copyOfRange(offset, payload.size)
        val key = deriveKey(passphrase, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            }
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw CryptoException("Wrong passphrase or corrupted backup", e)
        } finally {
            wipe(key.encoded)
            wipe(salt)
        }
    }

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun isBackupFile(payload: ByteArray): Boolean =
        payload.size > MAGIC.size && payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun intToBytes(value: Int) = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun bytesToInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    internal fun wipe(bytes: ByteArray) = bytes.fill(0)
}

/** Best-effort in-memory scrubbing of sensitive byte arrays. */
fun ByteArray.wipe() = fill(0)
