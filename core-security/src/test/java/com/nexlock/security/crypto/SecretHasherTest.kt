package com.nexlock.security.crypto

import com.nexlock.common.util.NexConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses a deterministic pepper so the real Keystore is not required on the JVM. */
private class FakePepper(private val pepper: ByteArray = "device-pepper".toByteArray()) : PepperProvider {
    override fun pepper(input: ByteArray): ByteArray {
        val out = ByteArray(input.size + pepper.size)
        pepper.copyInto(out)
        input.copyInto(out, pepper.size)
        return out
    }
}

class SecretHasherTest {

    private val hasher = Pbkdf2SecretHasher(FakePepper())

    @Test
    fun `hash never contains the plaintext secret`() {
        val secret = "482913"
        val hash = hasher.hash(secret)
        assertFalse("hash must not embed the secret", hash.contains(secret))
        assertTrue(hash.startsWith("pbkdf2-sha256$"))
        assertTrue(hash.contains(NexConstants.PBKDF2_ITERATIONS.toString()))
    }

    @Test
    fun `verification accepts the right secret and rejects others`() {
        val hash = hasher.hash("Sup3rSecret!")
        assertTrue(hasher.verify("Sup3rSecret!", hash))
        assertFalse(hasher.verify("sup3rsecret!", hash))
        assertFalse(hasher.verify("Sup3rSecret", hash))
        assertFalse(hasher.verify("", hash))
    }

    @Test
    fun `identical secrets produce different hashes because of random salts`() {
        val first = hasher.hash("123456")
        val second = hasher.hash("123456")
        assertNotEquals(first, second)
        assertTrue(hasher.verify("123456", first))
        assertTrue(hasher.verify("123456", second))
    }

    @Test
    fun `pepper is required to verify a hash`() {
        val hash = hasher.hash("pattern:0-1-2-5")
        val differentPepperHasher = Pbkdf2SecretHasher(FakePepper("another-device".toByteArray()))
        assertFalse(differentPepperHasher.verify("pattern:0-1-2-5", hash))
    }

    @Test
    fun `malformed hashes are rejected without throwing`() {
        assertFalse(hasher.verify("1234", "garbage"))
        assertFalse(hasher.verify("1234", "pbkdf2-sha256\$100000\$***\$***"))
        assertFalse(hasher.verify("1234", "pbkdf2-sha256\$1\$AAAA\$BBBB"))
        assertFalse(hasher.isSupportedHash("bcrypt\$whatever"))
        assertTrue(hasher.isSupportedHash(hasher.hash("1234")))
    }
}
