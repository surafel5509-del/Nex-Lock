package com.nexlock.common.policy

import com.nexlock.common.model.AuthMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialValidatorTest {

    @Test
    fun `pin accepts four to eight digits`() {
        assertTrue(CredentialValidator.validatePin("1234").valid)
        assertTrue(CredentialValidator.validatePin("12345678").valid)
        assertFalse(CredentialValidator.validatePin("123").valid)
        assertFalse(CredentialValidator.validatePin("123456789").valid)
        assertFalse(CredentialValidator.validatePin("12a4").valid)
        assertEquals(CredentialError.NON_NUMERIC, CredentialValidator.validatePin("12a4").error)
    }

    @Test
    fun `password requires length and character variety`() {
        assertFalse(CredentialValidator.validatePassword("short1").valid)
        assertFalse(CredentialValidator.validatePassword("onlyletters").valid)
        assertTrue(CredentialValidator.validatePassword("Sup3rSecret!").valid)
        assertEquals(CredentialError.WEAK_PASSWORD, CredentialValidator.validatePassword("onlyletters").error)
    }

    @Test
    fun `pattern requires at least four unique cells within the grid`() {
        assertTrue(CredentialValidator.validatePattern("0-1-2-5").valid)
        assertFalse(CredentialValidator.validatePattern("0-1-2").valid)
        assertFalse(CredentialValidator.validatePattern("0-1-1-2").valid)
        assertFalse(CredentialValidator.validatePattern("0-1-2-9").valid)
        assertFalse(CredentialValidator.validatePattern("").valid)
    }

    @Test
    fun `validate dispatches on the authentication mode`() {
        assertTrue(CredentialValidator.validate(AuthMode.PIN, "1234").valid)
        assertTrue(CredentialValidator.validate(AuthMode.PATTERN, "0-4-8-5").valid)
        assertTrue(CredentialValidator.validate(AuthMode.PASSWORD, "NexLock!23").valid)
    }

    @Test
    fun `display length reflects pattern cells`() {
        assertEquals(4, CredentialValidator.displayLength(AuthMode.PATTERN, "0-1-2-3"))
        assertEquals(6, CredentialValidator.displayLength(AuthMode.PIN, "123456"))
    }
}
