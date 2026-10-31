package com.nexlock.common.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEvaluatorTest {

    @Test
    fun `disabled automation never overrides`() {
        val decision = AutomationEvaluator.evaluate(
            AutomationContext(automationEnabled = false, insideLockSchedule = true, temporaryUnlockActive = true)
        )
        assertEquals(AutomationDecision.None, decision)
    }

    @Test
    fun `temporary unlock wins over a lock schedule`() {
        val decision = AutomationEvaluator.evaluate(
            AutomationContext(insideLockSchedule = true, temporaryUnlockActive = true)
        )
        assertTrue(decision is AutomationDecision.ForceUnlock)
        assertFalse(AutomationEvaluator.shouldEnforceLock(decision))
    }

    @Test
    fun `trusted network and location relax protection`() {
        assertTrue(
            AutomationEvaluator.evaluate(AutomationContext(onTrustedNetwork = true)) is AutomationDecision.ForceUnlock
        )
        assertTrue(
            AutomationEvaluator.evaluate(AutomationContext(insideTrustedLocation = true)) is AutomationDecision.ForceUnlock
        )
    }

    @Test
    fun `schedule forces protection when no safe zone applies`() {
        val decision = AutomationEvaluator.evaluate(AutomationContext(insideLockSchedule = true))
        assertTrue(decision is AutomationDecision.ForceLock)
        assertTrue(AutomationEvaluator.shouldEnforceLock(decision))
    }

    @Test
    fun `default context leaves normal locking in charge`() {
        val decision = AutomationEvaluator.evaluate(AutomationContext())
        assertEquals(AutomationDecision.None, decision)
        assertTrue(AutomationEvaluator.shouldEnforceLock(decision))
    }
}
