package com.nexlock.common.policy

/** Context inputs that may change the protection policy for the current moment. */
data class AutomationContext(
    val automationEnabled: Boolean = true,
    val insideLockSchedule: Boolean = false,
    val onTrustedNetwork: Boolean = false,
    val insideTrustedLocation: Boolean = false,
    val temporaryUnlockActive: Boolean = false,
)

sealed interface AutomationDecision {
    data object None : AutomationDecision
    data class ForceUnlock(val reason: String) : AutomationDecision
    data class ForceLock(val reason: String) : AutomationDecision
}

/**
 * Deterministic precedence rules:
 * 1. Automation disabled -> no override.
 * 2. Explicit temporary unlock wins over everything for its duration.
 * 3. Trusted Wi-Fi / trusted location are "safe zones" and relax protection.
 * 4. A lock schedule forces protection on.
 * 5. Otherwise no override.
 */
object AutomationEvaluator {

    fun evaluate(context: AutomationContext): AutomationDecision {
        if (!context.automationEnabled) return AutomationDecision.None
        if (context.temporaryUnlockActive) {
            return AutomationDecision.ForceUnlock("Temporary unlock session is active")
        }
        if (context.insideTrustedLocation) {
            return AutomationDecision.ForceUnlock("Inside a trusted location")
        }
        if (context.onTrustedNetwork) {
            return AutomationDecision.ForceUnlock("Connected to a trusted Wi-Fi network")
        }
        if (context.insideLockSchedule) {
            return AutomationDecision.ForceLock("Inside a scheduled lock window")
        }
        return AutomationDecision.None
    }

    fun shouldEnforceLock(decision: AutomationDecision): Boolean = decision !is AutomationDecision.ForceUnlock
}
