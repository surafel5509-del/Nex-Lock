package com.nexlock.feature.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexlock.common.model.AuthMode
import com.nexlock.common.policy.CredentialError
import com.nexlock.common.policy.CredentialValidator
import com.nexlock.core.auth.AuthState
import com.nexlock.design.components.GlassCard
import com.nexlock.design.components.NexFilterChip
import com.nexlock.design.components.NexPrimaryButton
import com.nexlock.design.components.NexSecondaryButton
import com.nexlock.design.components.NexTopBar
import com.nexlock.design.components.PasswordField
import com.nexlock.design.components.SettingsGroup
import com.nexlock.design.components.SettingsInfoRow
import com.nexlock.design.components.SettingsNavRow
import com.nexlock.design.components.SettingsToggleRow
import com.nexlock.design.components.StatusPill
import com.nexlock.design.strings.LocalStrings
import com.nexlock.design.theme.LocalNexColors
import com.nexlock.security.privacy.findActivity

@Composable
fun AuthSetupRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    showSkip: Boolean = false,
    viewModel: AuthSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) { if (state.completed) onDone() }

    AuthSetupScreen(
        state = state,
        modifier = modifier,
        onSelectMode = viewModel::selectMode,
        onSubmit = viewModel::submit,
        onComplete = { viewModel.complete(onboarding = true) },
        onSkip = if (showSkip) {
            { viewModel.complete(onboarding = false) }
        } else {
            null
        },
    )
}

@Composable
fun AuthSetupScreen(
    state: SetupUiState,
    modifier: Modifier = Modifier,
    onSelectMode: (AuthMode) -> Unit = {},
    onSubmit: (String, String) -> Unit = { _, _ -> },
    onComplete: () -> Unit = {},
    onSkip: (() -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val colors = LocalNexColors.current
    val clipboard = LocalClipboardManager.current

    var stage by remember { mutableIntStateOf(0) }
    var secret by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var resetSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.mode) {
        secret = ""
        confirmation = ""
        stage = 0
        resetSignal += 1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        NexTopBar(
            title = if (state.recoveryCode == null) "Secure your apps" else "Save your recovery code",
            subtitle = if (state.recoveryCode == null) "Step ${stage + 1} of 2" else "Shown only once",
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            when {
                state.recoveryCode != null -> {
                    GlassCard(borderColor = colors.success.copy(alpha = 0.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Key,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Recovery code",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = state.recoveryCode,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            color = colors.success,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NexSecondaryButton(
                                text = "Copy",
                                onClick = { clipboard.setText(AnnotatedString(state.recoveryCode)) },
                                icon = Icons.Rounded.ContentCopy,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Store this code somewhere safe. It is the only way to regain access if you " +
                                "forget your credential. It will be replaced after each use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    NexPrimaryButton(
                        text = "I saved my recovery code",
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                stage == 0 -> {
                    Text(
                        "Choose how you want to unlock protected apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuthMode.entries.forEach { mode ->
                            NexFilterChip(
                                label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                selected = state.mode == mode,
                                onClick = { onSelectMode(mode) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    CredentialInput(
                        mode = state.mode,
                        value = secret,
                        onValueChange = { secret = it },
                        enabled = !state.busy,
                        isError = state.error != null,
                    )
                    Spacer(Modifier.height(16.dp))
                    NexPrimaryButton(
                        text = strings.continueLabel,
                        onClick = { stage = 1 },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = CredentialValidator.validate(state.mode, secret).valid,
                    )
                    if (onSkip != null) {
                        Spacer(Modifier.height(10.dp))
                        NexSecondaryButton(
                            text = "Set up later",
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                else -> {
                    Text(
                        "Enter the same credential again to confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    CredentialInput(
                        mode = state.mode,
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        enabled = !state.busy,
                        isError = state.error != null,
                        resetSignal = resetSignal,
                    )
                    Spacer(Modifier.height(16.dp))
                    NexPrimaryButton(
                        text = "Create credential",
                        onClick = { onSubmit(secret, confirmation) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy && CredentialValidator.validate(state.mode, confirmation).valid,
                        loading = state.busy,
                    )
                    Spacer(Modifier.height(8.dp))
                    NexSecondaryButton(
                        text = strings.back,
                        onClick = {
                            stage = 0
                            confirmation = ""
                            resetSignal += 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.danger,
                        )
                    }
                }
            }
        }
    }
}

fun credentialErrorText(error: CredentialError?): String = when (error) {
    CredentialError.EMPTY -> "Enter a credential"
    CredentialError.TOO_SHORT -> "Too short"
    CredentialError.TOO_LONG -> "Too long"
    CredentialError.NON_NUMERIC -> "PIN must contain digits only"
    CredentialError.WEAK_PASSWORD -> "Add a number or symbol"
    CredentialError.PATTERN_TOO_SHORT -> "Connect at least 4 dots"
    CredentialError.PATTERN_INVALID -> "Invalid pattern"
    CredentialError.MISMATCH -> "Current credential is incorrect"
    null -> "Invalid credential"
}

@Composable
fun AuthManageRoute(
    onBack: () -> Unit,
    onChangeCredential: () -> Unit,
    modifier: Modifier = Modifier,
    onSetupMissingCredential: () -> Unit = {},
    viewModel: AuthManageViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val local by viewModel.local.collectAsStateWithLifecycle()
    val activity = LocalContextHolder.fragmentActivity()

    AuthManageScreen(
        authState = authState,
        local = local,
        modifier = modifier,
        onBack = onBack,
        onChangeCredential = onChangeCredential,
        onEnableBiometric = { activity?.let(viewModel::enableBiometric) },
        onDisableBiometric = viewModel::disableBiometric,
        onSetupMissingCredential = onSetupMissingCredential,
    )
}

/** Small helper so the screen stays previewable/testable without an Activity. */
object LocalContextHolder {
    @Composable
    fun fragmentActivity(): androidx.fragment.app.FragmentActivity? =
        androidx.compose.ui.platform.LocalContext.current.findActivity()
            as? androidx.fragment.app.FragmentActivity
}

@Composable
fun AuthManageScreen(
    authState: AuthState?,
    local: AuthManageUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onChangeCredential: () -> Unit = {},
    onEnableBiometric: () -> Unit = {},
    onDisableBiometric: () -> Unit = {},
    onSetupMissingCredential: () -> Unit = {},
) {
    val colors = LocalNexColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(title = "Authentication", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            GlassCard(
                borderColor = if (authState?.configured == true) colors.success.copy(alpha = 0.35f) else colors.warning.copy(alpha = 0.35f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Brush.linearGradient(colors.accentGradient.map { it.copy(alpha = 0.18f) })),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.glow)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Unlock method",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            authState?.mode?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Not configured",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    StatusPill(
                        text = if (authState?.configured == true) "Active" else "Missing",
                        positive = authState?.configured == true,
                    )
                }
                if (authState != null && authState.failedAttempts > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${authState.failedAttempts} failed attempt(s) recorded · resets after a successful unlock",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (authState?.configured != true) {
                Spacer(Modifier.height(12.dp))
                NexPrimaryButton(
                    text = "Set up an unlock credential",
                    onClick = onSetupMissingCredential,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Lock,
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Biometrics") {
                SettingsToggleRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = "Biometric unlock",
                    subtitle = if (local.biometricAvailable) {
                        "Use fingerprint or face to unlock apps"
                    } else {
                        "No enrolled biometric hardware detected"
                    },
                    checked = authState?.biometricEnabled == true,
                    enabled = local.biometricAvailable && !local.biometricEnabling,
                    onCheckedChange = { enabled ->
                        if (enabled) onEnableBiometric() else onDisableBiometric()
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Credential") {
                SettingsNavRow(
                    icon = Icons.Rounded.Password,
                    title = "Change unlock credential",
                    subtitle = "Switch between PIN, pattern and password",
                    onClick = onChangeCredential,
                )
                SettingsInfoRow(
                    label = "Recovery code",
                    value = if (authState?.recoveryConfigured == true) "Configured" else "Not configured",
                )
            }

            Spacer(Modifier.height(16.dp))

            GlassCard(borderColor = colors.warning.copy(alpha = 0.35f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Your credential is hashed with PBKDF2-SHA256 and a device-bound pepper. " +
                            "NexLock cannot recover it without your recovery code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (local.message != null) {
                Spacer(Modifier.height(12.dp))
                Text(local.message, style = MaterialTheme.typography.bodySmall, color = colors.success)
            }
            if (local.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(local.error, style = MaterialTheme.typography.bodySmall, color = colors.danger)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ChangeCredentialRoute(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChangeCredentialViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.success) { if (state.success) onSuccess() }
    ChangeCredentialScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onSelectMode = viewModel::selectMode,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun ChangeCredentialScreen(
    state: ChangeCredentialUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSelectMode: (AuthMode) -> Unit = {},
    onSubmit: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val colors = LocalNexColors.current
    var currentSecret by remember { mutableStateOf("") }
    var newSecret by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(title = "Change credential", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            PasswordField(
                value = currentSecret,
                onValueChange = { currentSecret = it },
                label = "Current credential",
                enabled = !state.busy,
            )
            Spacer(Modifier.height(18.dp))
            Text("New unlock method", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthMode.entries.forEach { mode ->
                    NexFilterChip(
                        label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = state.mode == mode,
                        onClick = { onSelectMode(mode) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            CredentialInput(
                mode = state.mode,
                value = newSecret,
                onValueChange = { newSecret = it },
                enabled = !state.busy,
                isError = state.error != null,
            )
            Spacer(Modifier.height(14.dp))
            CredentialInput(
                mode = state.mode,
                value = confirmation,
                onValueChange = { confirmation = it },
                enabled = !state.busy,
                isError = state.error != null,
            )
            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = colors.danger)
            }
            Spacer(Modifier.height(18.dp))
            NexPrimaryButton(
                text = "Save new credential",
                onClick = { onSubmit(currentSecret, newSecret, confirmation) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy &&
                    currentSecret.isNotBlank() &&
                    CredentialValidator.validate(state.mode, newSecret).valid &&
                    CredentialValidator.validate(state.mode, confirmation).valid,
                loading = state.busy,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
