package com.nexlock.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexlock.design.components.GlassCard
import com.nexlock.design.components.NexPrimaryButton
import com.nexlock.design.components.NexSecondaryButton
import com.nexlock.design.components.NexTextField
import com.nexlock.design.components.NexTopBar
import com.nexlock.design.components.PasswordField
import com.nexlock.design.components.SettingsGroup
import com.nexlock.design.components.SettingsInfoRow
import com.nexlock.design.components.clickableNoRipple
import com.nexlock.design.strings.LocalStrings
import com.nexlock.design.theme.LocalNexColors

@Composable
fun BackupRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let(viewModel::saveBackupTo) }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadRestoreFile(uri)
            showRestoreDialog = true
        }
    }

    BackupScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onCreateBackup = { showCreateDialog = true },
        onRestoreBackup = { openLauncher.launch(arrayOf("application/octet-stream", "application/*")) },
        onDismissCreate = { showCreateDialog = false },
        onDismissRestore = {
            showRestoreDialog = false
            viewModel.dismissRestore()
        },
        onConsumeMessage = viewModel::consumeMessage,
        onConsumeError = viewModel::consumeError,
        createDialogVisible = showCreateDialog,
        restoreDialogVisible = showRestoreDialog,
        onCreateConfirmed = { passphrase ->
            showCreateDialog = false
            viewModel.createBackup(passphrase)
            // The SAF picker is launched once the encrypted payload is ready.
        },
        onRestoreConfirmed = { passphrase ->
            showRestoreDialog = false
            viewModel.restore(passphrase)
        },
    )

    // Launch the document picker as soon as the payload exists.
    androidx.compose.runtime.LaunchedEffect(state.pendingBackup != null) {
        if (state.pendingBackup != null) {
            saveLauncher.launch("nexlock-backup-${System.currentTimeMillis()}.nlb")
        }
    }
}

@Composable
fun BackupScreen(
    state: BackupUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onCreateBackup: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onDismissCreate: () -> Unit = {},
    onDismissRestore: () -> Unit = {},
    onConsumeMessage: () -> Unit = {},
    onConsumeError: () -> Unit = {},
    createDialogVisible: Boolean = false,
    restoreDialogVisible: Boolean = false,
    onCreateConfirmed: (String) -> Unit = {},
    onRestoreConfirmed: (String) -> Unit = {},
) {
    val colors = LocalNexColors.current
    val strings = LocalStrings.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(title = strings.backup, onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            GlassCard(borderColor = colors.glow.copy(alpha = 0.3f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = colors.glow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Your backup is encrypted with a passphrase you choose (AES-256-GCM with " +
                            "PBKDF2 key derivation). NexLock cannot open it without that passphrase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (state.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Working…", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
            }

            NexPrimaryButton(
                text = "Create encrypted backup",
                onClick = onCreateBackup,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Backup,
                enabled = !state.busy,
            )
            Spacer(Modifier.height(10.dp))
            NexSecondaryButton(
                text = "Restore from backup",
                onClick = onRestoreBackup,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Restore,
                enabled = !state.busy,
            )

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Included in a backup") {
                SettingsInfoRow("Protected app list", "Yes")
                SettingsInfoRow("Preferences and theme", "Yes")
                SettingsInfoRow("Encrypted notes", "Yes (inside the encrypted file)")
                SettingsInfoRow("Schedules and trusted networks", "Yes")
                SettingsInfoRow("Vault media files", "No - device-bound and potentially huge")
                SettingsInfoRow("Credential hashes", "No - device-bound by design")
            }

            if (state.message != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.success,
                    modifier = Modifier.clickableNoRipple(onClick = onConsumeMessage),
                )
            }
            if (state.restoreSummary != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.restoreSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                    modifier = Modifier.clickableNoRipple(onClick = onConsumeError),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (createDialogVisible) {
        var passphrase by remember { mutableStateOf("") }
        var confirmation by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        Dialog(onDismissRequest = onDismissCreate) {
            GlassCard {
                Text("Choose a backup passphrase", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "At least 8 characters. Without it the backup cannot be restored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                PasswordField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = "Passphrase",
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = "Confirm passphrase",
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexPrimaryButton(
                        text = "Create",
                        onClick = {
                            when {
                                passphrase.length < 8 -> error = "Use at least 8 characters"
                                passphrase != confirmation -> error = "Passphrases do not match"
                                else -> onCreateConfirmed(passphrase)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    NexPrimaryButton(text = "Cancel", onClick = onDismissCreate, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (restoreDialogVisible) {
        var passphrase by remember { mutableStateOf("") }
        Dialog(onDismissRequest = onDismissRestore) {
            GlassCard {
                Text("Restore backup", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter the passphrase used when the backup was created. Existing data is merged, not deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                PasswordField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = "Passphrase",
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexPrimaryButton(
                        text = "Restore",
                        onClick = { onRestoreConfirmed(passphrase) },
                        modifier = Modifier.weight(1f),
                        enabled = passphrase.isNotBlank(),
                    )
                    NexPrimaryButton(text = "Cancel", onClick = onDismissRestore, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun AboutRoute(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }
    val strings = LocalStrings.current
    val colors = LocalNexColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(title = strings.about, onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, null, tint = colors.glow, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("NexLock", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Version $version · offline by design",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "Security model") {
                SettingsInfoRow("Internet permission", "Not declared")
                SettingsInfoRow(
                    "Vault encryption",
                    "AES-256-GCM (Keystore)",
                )
                SettingsInfoRow(
                    "Credential hashing",
                    "PBKDF2-SHA256 · 210k iterations",
                )
                SettingsInfoRow(
                    "Key protection",
                    if (state.posture?.hardwareBackedKey == true) "Hardware-backed" else "Software-backed",
                )
                SettingsInfoRow("Analytics / trackers", "None")
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "Legal") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableNoRipple(onClick = onOpenPrivacy)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(strings.privacyPolicy, style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableNoRipple(onClick = onOpenTerms)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(strings.termsOfService, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "NexLock protects apps with an authentication gate. It is not a substitute for device " +
                    "encryption and cannot defend against a rooted or physically compromised device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun LegalScreen(
    title: String,
    content: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NexTopBar(title = title, onBack = onBack)
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}
