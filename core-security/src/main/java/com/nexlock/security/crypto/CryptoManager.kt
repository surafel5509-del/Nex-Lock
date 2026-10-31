package com.nexlock.security.crypto

import java.io.InputStream
import java.io.OutputStream

/**
 * Symmetric encryption backed by the Android Keystore.
 *
 * Implementations must:
 * * use AES-256-GCM with a random IV per message/chunk,
 * * never expose raw key material outside the Keystore,
 * * stream large payloads without loading them fully into memory.
 */
interface CryptoManager {

    /** Encrypts a small payload (preferences, notes, metadata). */
    fun encrypt(plaintext: ByteArray): ByteArray

    /** Decrypts a payload produced by [encrypt]; throws [CryptoException] on tampering. */
    fun decrypt(ciphertext: ByteArray): ByteArray

    /** Base64 convenience wrapper used for Room columns and DataStore values. */
    fun encryptString(plaintext: String): String

    fun decryptString(ciphertext: String): String

    /** Encrypts an arbitrarily large stream using independently authenticated chunks. */
    fun encryptStream(input: InputStream, output: OutputStream)

    /** Decrypts a stream written by [encryptStream]; throws [CryptoException] if a chunk fails. */
    fun decryptStream(input: InputStream, output: OutputStream)

    /** True when the key lives in a TEE/StrongBox rather than software. */
    fun isHardwareBacked(): Boolean

    /** Short, non-reversible identifier of the current key (safe to display/log). */
    fun keyFingerprint(): String
}

class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
