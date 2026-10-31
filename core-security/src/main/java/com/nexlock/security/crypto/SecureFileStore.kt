package com.nexlock.security.crypto

import android.content.Context
import com.nexlock.common.util.NexConstants
import com.nexlock.common.util.NexLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted, app-private file storage used by the vault and intruder incidents.
 *
 * * Files live in the app sandbox (`filesDir`), never on shared storage.
 * * File names are random UUIDs so directory listings leak nothing.
 * * Deletion overwrites the ciphertext before unlinking (best effort: flash translation layers may
 *   retain old blocks - this is documented and never overstated in the UI).
 */
@Singleton
class SecureFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: CryptoManager,
) {

    private val random = SecureRandom()

    fun vaultDirectory(): File = ensureDirectory(File(context.filesDir, NexConstants.VAULT_DIR))

    fun intruderDirectory(): File = ensureDirectory(File(context.filesDir, NexConstants.INTRUDER_DIR))

    fun tempDirectory(): File = ensureDirectory(File(context.cacheDir, NexConstants.TEMP_DIR))

    /** Streams [input] into the vault, returning the generated file name. */
    fun importStream(input: InputStream, originalName: String, directory: File = vaultDirectory()): StoredFile {
        val safeExtension = originalName.substringAfterLast('.', "").take(8)
            .filter { it.isLetterOrDigit() }
        val fileName = buildString {
            append(UUID.randomUUID().toString().replace("-", ""))
            if (safeExtension.isNotEmpty()) append('.').append(safeExtension.lowercase())
        }
        val target = File(directory, fileName)
        var written = 0L
        try {
            val counting = CountingInputStream(input)
            FileOutputStream(target).use { fileOut -> crypto.encryptStream(counting, fileOut) }
            written = counting.count
        } catch (e: Exception) {
            target.delete()
            throw CryptoException("Unable to store file securely", e)
        }
        return StoredFile(fileName = fileName, sizeBytes = written)
    }

    fun importBytes(bytes: ByteArray, originalName: String, directory: File = vaultDirectory()): StoredFile =
        importStream(bytes.inputStream(), originalName, directory)

    /** Decrypts a stored file into a short-lived cache file for in-app rendering/playback. */
    fun decryptToTempFile(fileName: String, directory: File = vaultDirectory()): File {
        val source = resolve(directory, fileName) ?: throw CryptoException("File not found")
        val temp = File(tempDirectory(), "tmp_${System.currentTimeMillis()}_${random.nextInt(1_000_000)}")
        try {
            openSource(source).use { input ->
                FileOutputStream(temp).use { output -> crypto.decryptStream(input, output) }
            }
        } catch (e: Exception) {
            temp.delete()
            throw CryptoException("Unable to open protected file", e)
        }
        return temp
    }

    /** Decrypts at most [maxBytes] into memory - used for encrypted note exports and thumbnails. */
    fun readBytes(fileName: String, maxBytes: Long, directory: File = vaultDirectory()): ByteArray {
        val source = resolve(directory, fileName) ?: throw CryptoException("File not found")
        val buffer = ByteArrayOutputStream()
        openSource(source).use { input ->
            val chunk = ByteArray(16 * 1024)
            var total = 0L
            val limited = object : OutputStream() {
                override fun write(b: Int) {
                    if (total >= maxBytes) throw CryptoException("File exceeds the in-memory limit")
                    buffer.write(b)
                    total++
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (total + len > maxBytes) throw CryptoException("File exceeds the in-memory limit")
                    buffer.write(b, off, len)
                    total += len
                }
            }
            crypto.decryptStream(input, limited)
        }
        return buffer.toByteArray()
    }

    /** Writes raw bytes through the same authenticated container (used for intruder captures). */
    fun writeBytes(bytes: ByteArray, directory: File = vaultDirectory(), extension: String = "bin"): String {
        val fileName = "${UUID.randomUUID().toString().replace("-", "")}.$extension"
        FileOutputStream(File(directory, fileName)).use { output ->
            crypto.encryptStream(bytes.inputStream(), output)
        }
        return fileName
    }

    fun exportTo(fileName: String, output: OutputStream, directory: File = vaultDirectory()) {
        val source = resolve(directory, fileName) ?: throw CryptoException("File not found")
        openSource(source).use { input -> crypto.decryptStream(input, output) }
    }

    fun delete(fileName: String, directory: File = vaultDirectory()): Boolean {
        val target = resolve(directory, fileName) ?: return false
        return deleteSecurely(target)
    }

    fun deleteSecurely(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val length = file.length()
            if (length > 0) {
                FileOutputStream(file).use { out ->
                    val buffer = ByteArray(8 * 1024)
                    var remaining = length
                    while (remaining > 0) {
                        random.nextBytes(buffer)
                        val toWrite = minOf(buffer.size.toLong(), remaining).toInt()
                        out.write(buffer, 0, toWrite)
                        remaining -= toWrite
                    }
                    out.flush()
                    out.fd.sync()
                }
            }
            file.delete()
        } catch (e: Exception) {
            NexLog.w(TAG, "Secure delete failed, falling back to plain delete", e)
            file.delete()
        }
    }

    fun totalVaultBytes(): Long = vaultDirectory().walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun exists(fileName: String, directory: File = vaultDirectory()): Boolean =
        resolve(directory, fileName)?.exists() == true

    /** Guards against path traversal: only file names inside the known directory are accepted. */
    fun resolve(directory: File, fileName: String): File? {
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return null
        val candidate = File(directory, fileName)
        val parent = candidate.canonicalFile.parentFile ?: return null
        return if (parent == directory.canonicalFile) candidate else null
    }

    private fun openSource(file: File): InputStream = file.inputStream().buffered()

    private fun ensureDirectory(dir: File): File {
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    data class StoredFile(val fileName: String, val sizeBytes: Long)

    private class CountingInputStream(private val delegate: InputStream) : InputStream() {
        var count: Long = 0
            private set

        override fun read(): Int = delegate.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            delegate.read(b, off, len).also { if (it > 0) count += it }

        override fun close() = delegate.close()
    }

    companion object {
        private const val TAG = "SecureFileStore"
    }
}
