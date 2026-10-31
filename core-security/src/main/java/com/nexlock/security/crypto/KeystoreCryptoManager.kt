package com.nexlock.security.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.nexlock.common.util.NexConstants
import com.nexlock.common.util.NexLog
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM implementation backed by an AndroidKeyStore key that never leaves secure hardware
 * when the device supports it (StrongBox preferred, TEE fallback, software last resort).
 *
 * The key is created on first use and persists across app restarts. It is intentionally *not*
 * exportable and is destroyed when the user clears app data or uninstalls NexLock - which makes
 * previously exported ciphertext unrecoverable, by design.
 */
@Singleton
class KeystoreCryptoManager @Inject constructor() : CryptoManager {

    private val random = SecureRandom()

    @Volatile
    private var cachedKey: SecretKey? = null

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        return synchronized(this) {
            cachedKey ?: run {
                val existing = try {
                    keyStore.getKey(NexConstants.KEYSTORE_MASTER_ALIAS, null) as? SecretKey
                } catch (e: UnrecoverableKeyException) {
                    NexLog.w(TAG, "Keystore key is unrecoverable, regenerating", e)
                    null
                }
                val key = existing ?: generateKey()
                cachedKey = key
                key
            }
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            NexConstants.KEYSTORE_MASTER_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                generator.init(
                    KeyGenParameterSpec.Builder(
                        NexConstants.KEYSTORE_MASTER_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .setIsStrongBoxBacked(true)
                        .build()
                )
                generator.generateKey()
            } else {
                generator.init(spec)
                generator.generateKey()
            }
        } catch (e: StrongBoxUnavailableException) {
            NexLog.i(TAG, "StrongBox unavailable, using TEE/software key")
            generator.init(spec)
            generator.generateKey()
        } catch (e: Exception) {
            NexLog.w(TAG, "StrongBox key generation failed, falling back", e)
            runCatching { keyStore.deleteEntry(NexConstants.KEYSTORE_MASTER_ALIAS) }
            generator.init(spec)
            generator.generateKey()
        }
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(2 + iv.size + ciphertext.size).also { out ->
            out[0] = VERSION
            out[1] = iv.size.toByte()
            iv.copyInto(out, destinationOffset = 2)
            ciphertext.copyInto(out, destinationOffset = 2 + iv.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size < 3) throw CryptoException("Ciphertext too short")
        if (ciphertext[0] != VERSION) throw CryptoException("Unsupported ciphertext version")
        val ivLength = ciphertext[1].toInt()
        if (ivLength !in 12..32 || ciphertext.size <= 2 + ivLength) throw CryptoException("Malformed ciphertext")
        val iv = ciphertext.copyOfRange(2, 2 + ivLength)
        val body = ciphertext.copyOfRange(2 + ivLength, ciphertext.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            }
            cipher.doFinal(body)
        } catch (e: Exception) {
            throw CryptoException("Decryption failed - data is corrupted or tampered", e)
        }
    }

    override fun encryptString(plaintext: String): String =
        Base64.getEncoder().encodeToString(encrypt(plaintext.toByteArray(Charsets.UTF_8)))

    override fun decryptString(ciphertext: String): String {
        val bytes = try {
            Base64.getDecoder().decode(ciphertext)
        } catch (e: IllegalArgumentException) {
            throw CryptoException("Value is not valid ciphertext", e)
        }
        return decrypt(bytes).toString(Charsets.UTF_8)
    }

    override fun encryptStream(input: InputStream, output: OutputStream) =
        AeadChunkedStream(::secretKey).encrypt(input, output)

    override fun decryptStream(input: InputStream, output: OutputStream) =
        AeadChunkedStream(::secretKey).decrypt(input, output)

    @Suppress("DEPRECATION")
    override fun isHardwareBacked(): Boolean = try {
        val key = secretKey()
        val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.securityLevel != KeyInfo.SECURITY_LEVEL_SOFTWARE
        } else {
            info.isInsideSecureHardware
        }
    } catch (e: Exception) {
        NexLog.w(TAG, "Unable to query key security level", e)
        false
    }

    override fun keyFingerprint(): String = try {
        val encoded = secretKey().encoded
        if (encoded == null) {
            "keystore:non-exportable"
        } else {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(encoded)
                .take(6)
                .joinToString("") { "%02x".format(it) }
        }
    } catch (e: Exception) {
        "keystore:unknown"
    }

    companion object {
        private const val TAG = "Crypto"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_LENGTH = 12
        private const val VERSION: Byte = 1
    }
}
