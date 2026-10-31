package com.nexlock.security.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * JVM-only [CryptoManager] used by unit tests. It follows the exact same envelope format as
 * [KeystoreCryptoManager] but takes an in-memory key, because AndroidKeyStore is not available
 * outside the platform (instrumented tests cover the real implementation).
 */
internal class SoftwareCryptoManager(private val key: SecretKey) : CryptoManager {

    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val body = cipher.doFinal(plaintext)
        return byteArrayOf(1, iv.size.toByte()) + iv + body
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val ivLength = ciphertext[1].toInt()
        val iv = ciphertext.copyOfRange(2, 2 + ivLength)
        val body = ciphertext.copyOfRange(2 + ivLength, ciphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    override fun encryptString(plaintext: String): String =
        Base64.getEncoder().encodeToString(encrypt(plaintext.toByteArray()))

    override fun decryptString(ciphertext: String): String =
        decrypt(Base64.getDecoder().decode(ciphertext)).toString(Charsets.UTF_8)

    override fun encryptStream(input: InputStream, output: OutputStream) =
        AeadChunkedStream { key }.encrypt(input, output)

    override fun decryptStream(input: InputStream, output: OutputStream) =
        AeadChunkedStream { key }.decrypt(input, output)

    override fun isHardwareBacked(): Boolean = false

    override fun keyFingerprint(): String = "software-test-key"
}
