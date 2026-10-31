package com.nexlock.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexlock.common.model.AuthMode
import com.nexlock.common.policy.CredentialValidator
import com.nexlock.design.components.PasswordField
import com.nexlock.design.components.PatternLock
import com.nexlock.design.components.PinDots
import com.nexlock.design.components.PinPad
import com.nexlock.design.theme.LocalNexColors

/**
 * Mode-aware credential input used by setup, change-credential and recovery flows.
 * The caller owns the resulting secret string; it is never persisted here.
 */
@Composable
fun CredentialInput(
    mode: AuthMode,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Credential",
    enabled: Boolean = true,
    isError: Boolean = false,
    resetSignal: Int = 0,
) {
    val colors = LocalNexColors.current
    when (mode) {
        AuthMode.PIN -> Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PinDots(
                filledCount = value.length,
                maxDots = CredentialValidator.MAX_PIN_LENGTH,
                error = isError,
            )
            Spacer(Modifier.height(20.dp))
            PinPad(
                onDigit = { digit ->
                    if (value.length < CredentialValidator.MAX_PIN_LENGTH) onValueChange(value + digit)
                },
                onBackspace = { onValueChange(value.dropLast(1)) },
                enabled = enabled,
            )
            if (value.length < CredentialValidator.MIN_PIN_LENGTH) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "PIN must be at least ${CredentialValidator.MIN_PIN_LENGTH} digits",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AuthMode.PATTERN -> Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var localReset by remember { mutableStateOf(resetSignal) }
            LaunchedEffect(resetSignal) { localReset = resetSignal }
            PatternLock(
                enabled = enabled,
                error = isError,
                resetSignal = localReset,
                onComplete = { encoded ->
                    onValueChange(encoded)
                    localReset += 1
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Connect at least ${CredentialValidator.MIN_PATTERN_CELLS} dots",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AuthMode.PASSWORD -> Column(modifier = modifier.fillMaxWidth()) {
            PasswordField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                enabled = enabled,
                isError = isError,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "At least ${CredentialValidator.MIN_PASSWORD_LENGTH} characters with a letter and a number or symbol",
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
