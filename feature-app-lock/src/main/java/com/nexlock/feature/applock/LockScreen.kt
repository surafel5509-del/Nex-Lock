package com.nexlock.feature.applock

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexlock.common.model.AuthMode
import com.nexlock.common.policy.CredentialValidator
import com.nexlock.common.util.Formatters
import com.nexlock.design.components.AppIcon
import com.nexlock.design.components.NexPrimaryButton
import com.nexlock.design.components.NexSecondaryButton
import com.nexlock.design.components.NexTextField
import com.nexlock.design.components.PasswordField
import com.nexlock.design.components.PatternLock
import com.nexlock.design.components.PinDots
import com.nexlock.design.components.PinPad
import com.nexlock.design.components.clickableNoRipple
import com.nexlock.design.strings.LocalStrings
import com.nexlock.design.theme.LocalNexColors
import com.nexlock.security.intruder.IntruderCaptureHost
import com.nexlock.security.intruder.hasCameraPermission
import com.nexlock.security.privacy.SecureScreen
import kotlinx.coroutines.delay

@Composable
fun LockRoute(
    onUnlocked: () -> Unit,
    onBackToHome: () -> Unit,
    onRequestBiometric: (LockViewModel) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                LockUiEvent.Unlocked -> onUnlocked()
                LockUiEvent.BackToHome -> onBackToHome()
            }
        }
    }

    LockScreen(
        state = state,
        modifier = modifier,
        onSubmit = viewModel::submit,
        onBiometric = { onRequestBiometric(viewModel) },
        onSubmitRecovery = viewModel::recoverWithCode,
        onToggleRecovery = viewModel::showRecovery,
        onDismissError = viewModel::dismissError,
        onCaptureSaved = viewModel::onCaptureSaved,
        onCaptureFailed = viewModel::onCaptureFailed,
        onBackToHome = viewModel::requestHome,
    )
}

@Composable
fun LockScreen(
    state: LockUiState,
    modifier: Modifier = Modifier,
    onSubmit: (String) -> Unit = {},
    onBiometric: () -> Unit = {},
    onSubmitRecovery: (String, String) -> Unit = { _, _ -> },
    onToggleRecovery: (Boolean) -> Unit = {},
    onDismissError: () -> Unit = {},
    onCaptureSaved: (java.io.File) -> Unit = {},
    onCaptureFailed: (String) -> Unit = {},
    onBackToHome: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val colors = LocalNexColors.current
    val context = androidx.compose.ui.platform.LocalContext.current

    SecureScreen(enabled = state.screenshotsBlocked)
    BackHandler { onBackToHome() }

    var pin by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var patternReset by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lockedOut = state.lockoutUntilMillis > now
    val remaining = (state.lockoutUntilMillis - now).coerceAtLeast(0L)

    LaunchedEffect(state.lockoutUntilMillis) {
        while (state.lockoutUntilMillis > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            pin = ""
            password = ""
            patternReset += 1
        }
    }

    // Intruder capture: only after repeated failures, only when opt-in, only with permission.
    var cameraGranted by remember { mutableStateOf(hasCameraPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }

    LaunchedEffect(state.captureRequest) {
        if (state.captureRequest > 0L && state.intruderPhotoEnabled && !cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    if (state.captureRequest > 0L && state.intruderPhotoEnabled && cameraGranted) {
        IntruderCaptureHost(
            request = state.captureRequest,
            onCaptured = onCaptureSaved,
            onError = onCaptureFailed,
        )
    }

    val pulse = rememberInfiniteTransition(label = "lockPulse")
    val glowScale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lockGlow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(colors.glow.copy(alpha = 0.22f), Color.Transparent)
                            )
                        )
                )
                AppIcon(packageName = state.packageName, label = state.appName, size = 74.dp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = state.appName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = colors.glow,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Locked by NexLock",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(26.dp))

            when {
                state.busy -> {
                    CircularProgressIndicator(color = colors.glow, strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(strings.loading, style = MaterialTheme.typography.bodySmall)
                }
                state.unlocked -> {
                    Text(
                        strings.unlockedTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.success,
                    )
                }
                else -> {
                    Text(
                        text = when (state.mode) {
                            AuthMode.PIN -> strings.enterPin
                            AuthMode.PATTERN -> strings.enterPattern
                            AuthMode.PASSWORD -> strings.enterPassword
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))

                    if (lockedOut) {
                        LockoutCard(remaining = remaining)
                        Spacer(Modifier.height(16.dp))
                    }

                    when (state.mode) {
                        AuthMode.PIN -> {
                            PinDots(
                                filledCount = pin.length,
                                maxDots = CredentialValidator.MAX_PIN_LENGTH,
                                error = state.error != null,
                            )
                            Spacer(Modifier.height(20.dp))
                            PinPad(
                                onDigit = { digit -> if (!lockedOut && pin.length < CredentialValidator.MAX_PIN_LENGTH) pin += digit },
                                onBackspace = { pin = pin.dropLast(1) },
                                enabled = !lockedOut,
                                showBiometric = state.biometricEnabled,
                                onBiometric = onBiometric,
                            )
                            Spacer(Modifier.height(18.dp))
                            NexPrimaryButton(
                                text = strings.unlockedTitle,
                                onClick = { onSubmit(pin) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !lockedOut && pin.length >= CredentialValidator.MIN_PIN_LENGTH,
                            )
                        }
                        AuthMode.PATTERN -> {
                            PatternLock(
                                enabled = !lockedOut,
                                error = state.error != null,
                                resetSignal = patternReset,
                                minCells = CredentialValidator.MIN_PATTERN_CELLS,
                                onComplete = { encoded -> onSubmit(encoded) },
                            )
                            Spacer(Modifier.height(16.dp))
                            if (state.biometricEnabled) {
                                BiometricButton(onBiometric)
                            }
                        }
                        AuthMode.PASSWORD -> {
                            PasswordField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Password",
                                enabled = !lockedOut,
                                isError = state.error != null,
                            )
                            Spacer(Modifier.height(16.dp))
                            NexPrimaryButton(
                                text = strings.unlockedTitle,
                                onClick = { onSubmit(password) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !lockedOut && password.isNotBlank(),
                            )
                            if (state.biometricEnabled) {
                                Spacer(Modifier.height(12.dp))
                                BiometricButton(onBiometric)
                            }
                        }
                    }

                    if (state.error != null) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.danger,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickableNoRipple(onClick = onDismissError),
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "${strings.forgotCredential} ${strings.emergencyRecovery}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickableNoRipple { onToggleRecovery(true) },
                    )
                }
            }
        }
    }

    if (state.recoveryVisible) {
        RecoveryDialog(
            busy = state.busy,
            error = state.error,
            newRecoveryCode = state.recoveryNewCode,
            onSubmit = onSubmitRecovery,
            onDismiss = { onToggleRecovery(false) },
        )
    }
}

@Composable
private fun BiometricButton(onClick: () -> Unit) {
    val colors = LocalNexColors.current
    NexSecondaryButton(
        text = "Use biometrics",
        onClick = onClick,
        icon = Icons.Rounded.Fingerprint,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LockoutCard(remaining: Long) {
    val colors = LocalNexColors.current
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.danger.copy(alpha = 0.10f))
            .border(1.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${strings.tryAgainIn} ${Formatters.countdown(remaining)}",
            style = MaterialTheme.typography.titleSmall,
            color = colors.danger,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (remaining / 30_000f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = colors.danger,
            trackColor = colors.danger.copy(alpha = 0.15f),
        )
    }
}

@Composable
private fun RecoveryDialog(
    busy: Boolean,
    error: String?,
    newRecoveryCode: String?,
    onSubmit: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    val colors = LocalNexColors.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recover access") },
        text = {
            Column {
                Text(
                    "Enter the recovery code you saved when you set up NexLock. " +
                        "The code is single-use and will be replaced afterwards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                NexTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = "Recovery code",
                    enabled = !busy,
                )
                Spacer(Modifier.height(10.dp))
                PasswordField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = "New unlock credential",
                    enabled = !busy,
                )
                if (newRecoveryCode != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "New recovery code: $newRecoveryCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.success,
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onSubmit(code, secret) },
                enabled = !busy && code.isNotBlank() && secret.isNotBlank(),
            ) { Text("Recover") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
