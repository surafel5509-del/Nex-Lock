package com.nexlock.security.crypto

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chunked AES-256-GCM stream format.
 *
 * ```
 * header : "NXLK" | version(1) | chunkSize(4)
 * chunk  : ivLength(1) | iv | ciphertextLength(4) | ciphertext || tag
 * ```
 *
 * Each chunk is authenticated independently, so a 200 MB video can be decrypted progressively
 * with bounded memory and corruption is detected on the affected chunk instead of silently
 * producing garbage.
 */
internal class AeadChunkedStream(private val keyProvider: () -> SecretKey) {

    private val random = SecureRandom()

    fun encrypt(input: InputStream, output: OutputStream) {
        output.write(MAGIC)
        output.write(VERSION.toInt())
        writeInt(output, CHUNK_SIZE)
        val buffer = ByteArray(CHUNK_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
            }
            val ciphertext = cipher.doFinal(buffer, 0, read)
            output.write(iv.size)
            output.write(iv)
            writeInt(output, ciphertext.size)
            output.write(ciphertext)
        }
        output.flush()
    }

    fun decrypt(input: InputStream, output: OutputStream) {
        val magic = ByteArray(MAGIC.size)
        readFully(input, magic)
        if (!magic.contentEquals(MAGIC)) throw CryptoException("Unsupported encrypted container")
        val version = input.read()
        if (version != VERSION.toInt()) throw CryptoException("Unsupported container version $version")
        val declaredChunk = readInt(input)
        if (declaredChunk !in 1..(4 * 1024 * 1024)) throw CryptoException("Invalid chunk size")

        while (true) {
            val ivLength = input.read()
            if (ivLength == -1) break // clean end of stream
            if (ivLength !in 1..32) throw CryptoException("Invalid IV length")
            val iv = ByteArray(ivLength).also { readFully(input, it) }
            val ciphertextLength = readInt(input)
            if (ciphertextLength <= 0 || ciphertextLength > declaredChunk + 64) {
                throw CryptoException("Invalid chunk length")
            }
            val ciphertext = ByteArray(ciphertextLength).also { readFully(input, it) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
            }
            val plaintext = try {
                cipher.doFinal(ciphertext)
            } catch (e: Exception) {
                throw CryptoException("Authentication failed - file may be corrupted or tampered", e)
            }
            output.write(plaintext)
        }
        output.flush()
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) throw EOFException("Unexpected end of encrypted stream")
            offset += read
        }
    }

    private fun readInt(input: InputStream): Int {
        val bytes = ByteArray(4)
        readFully(input, bytes)
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    private fun writeInt(output: OutputStream, value: Int) {
        output.write((value ushr 24) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    companion object {
        private val MAGIC = byteArrayOf('N'.code.toByte(), 'X'.code.toByte(), 'L'.code.toByte(), 'K'.code.toByte())
        private const val VERSION = 1.toByte()
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_LENGTH = 12
        private const val CHUNK_SIZE = 64 * 1024

        fun isEncryptedStream(input: InputStream): Boolean = try {
            input.mark(MAGIC.size + 1)
            val head = ByteArray(MAGIC.size)
            val read = input.read(head)
            input.reset()
            read == MAGIC.size && head.contentEquals(MAGIC)
        } catch (e: IOException) {
            false
        }
    }
}
