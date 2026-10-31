package com.nexlock.security.crypto

import com.nexlock.common.util.NexConstants
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds a device-bound secret ("pepper") to credential material before it is stretched, so a stolen
 * preferences file cannot be brute-forced offline without the Keystore key.
 */
interface PepperProvider {
    fun pepper(input: ByteArray): ByteArray
}

interface SecretHasher {
    /** @return a self-describing hash string safe to persist. */
    fun hash(secret: String): String

    /** Constant-time verification of [secret] against a persisted hash. */
    fun verify(secret: String, encodedHash: String): Boolean

    /** True when [encodedHash] was produced by a supported scheme. */
    fun isSupportedHash(encodedHash: String): Boolean
}

/**
 * PBKDF2-HMAC-SHA256 with a random 128-bit salt, 210k iterations and an HMAC-SHA256 pepper.
 * PINs, passwords and pattern encodings are *never* stored or logged in plaintext.
 */
@Singleton
class Pbkdf2SecretHasher @Inject constructor(
    private val pepperProvider: PepperProvider,
) : SecretHasher {

    private val random = SecureRandom()

    override fun hash(secret: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(secret, salt, NexConstants.PBKDF2_ITERATIONS)
        val encoder = Base64.getEncoder()
        return listOf(
            SCHEME,
            NexConstants.PBKDF2_ITERATIONS.toString(),
            encoder.encodeToString(salt),
            encoder.encodeToString(derived),
        ).joinToString(SEPARATOR)
    }

    override fun verify(secret: String, encodedHash: String): Boolean {
        val parts = encodedHash.split(SEPARATOR)
        if (parts.size != 4 || parts[0] != SCHEME) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        if (salt.size < 16 || expected.isEmpty()) return false
        val actual = try {
            derive(secret, salt, iterations)
        } catch (e: Exception) {
            return false
        }
        return MessageDigest.isEqual(expected, actual)
    }

    override fun isSupportedHash(encodedHash: String): Boolean {
        val parts = encodedHash.split(SEPARATOR)
        return parts.size == 4 && parts[0] == SCHEME
    }

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val peppered = pepperProvider.pepper(secret.toByteArray(Charsets.UTF_8))
        // Base64 keeps the PBEKeySpec input ASCII, so hashing is stable across JCE providers.
        val chars = Base64.getEncoder().encodeToString(peppered).toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, NexConstants.PBKDF2_KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
            peppered.fill(0)
        }
    }

    companion object {
        private const val SCHEME = "pbkdf2-sha256"
        private const val SEPARATOR = "$"
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_BYTES = 16
        private const val MIN_ITERATIONS = 10_000
        private const val MAX_ITERATIONS = 2_000_000
    }
}

/** Keystore-backed HMAC pepper. Fails closed if the Keystore key is unavailable. */
@Singleton
class KeystorePepperProvider @Inject constructor() : PepperProvider {

    private val keyStore: java.security.KeyStore by lazy {
        java.security.KeyStore.getInstance(KEYSTORE).apply { load(null) }
    }

    override fun pepper(input: ByteArray): ByteArray = try {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(secretKey())
        mac.doFinal(input)
    } catch (e: Exception) {
        throw CryptoException("Credential pepper is unavailable", e)
    }

    private fun secretKey(): javax.crypto.SecretKey = synchronized(this) {
        (keyStore.getKey(NexConstants.KEYSTORE_PEPPER_ALIAS, null) as? javax.crypto.SecretKey)
            ?: generateKey()
    }

    private fun generateKey(): javax.crypto.SecretKey {
        @Suppress("DEPRECATION")
        val generator = javax.crypto.KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            KEYSTORE,
        )
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            NexConstants.KEYSTORE_PEPPER_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_SIGN or
                android.security.keystore.KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALGORITHM = "HmacSHA256"
    }
}
