package com.calcplus.calculator.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calcplus.calculator.BuildConfig
import com.calcplus.calculator.R
import com.calcplus.calculator.di.AppContainer
import kotlinx.coroutines.launch

/** Iteration-1 Settings = Change passcode, Lock now, Erase everything, About.
 *  No auto-lock row, no biometric UI of any kind. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onChangePasscode: () -> Unit,
) {
    var showEraseConfirm by remember { mutableStateOf(false) }
    var showEraseFinal by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("Security")
            SettingsRow(title = stringResource(R.string.settings_change_title), onClick = onChangePasscode)
            SettingsRow(title = "Lock now", onClick = { container.lockManager.lock() })
            SettingsRow(
                title = stringResource(R.string.nuke_row_title),
                subtitle = stringResource(R.string.nuke_row_subtitle),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showEraseConfirm = true },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionLabel("About")
            SettingsRow(title = "Version", subtitle = BuildConfig.VERSION_NAME)
            SettingsRow(
                title = "Privacy",
                subtitle = "All data stays on this device. SafeBox has no servers and sends nothing anywhere.",
            )
            SettingsRow(
                title = "How it works",
                subtitle = "The calculator is the lock screen. Type your secret key sequence and press = to open the vault. The vault locks the moment the app leaves the foreground.",
            )
            SettingsRow(
                title = "No recovery",
                subtitle = stringResource(R.string.setup_no_recovery_body),
            )
            SettingsRow(
                title = "Open-source licenses",
                subtitle = "Jetpack (Apache 2.0), Coil (Apache 2.0), Kotlin & kotlinx libraries (Apache 2.0).",
            )
            // Future placeholders (decoy passcode, break-in alerts, disguise
            // themes) are deliberately not shown.
        }
    }

    // Two-step destructive confirm; the nuke itself runs in applicationScope
    // because this screen is torn down the moment the lock state resets.
    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            title = { Text(stringResource(R.string.nuke_confirm_title)) },
            text = { Text(stringResource(R.string.nuke_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showEraseConfirm = false
                    showEraseFinal = true
                }) {
                    Text(stringResource(R.string.nuke_confirm_continue), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirm = false }) {
                    Text(stringResource(R.string.change_cancel))
                }
            },
        )
    }
    if (showEraseFinal) {
        AlertDialog(
            onDismissRequest = { showEraseFinal = false },
            title = { Text(stringResource(R.string.nuke_final_title)) },
            text = { Text(stringResource(R.string.nuke_final_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showEraseFinal = false
                    container.applicationScope.launch { container.vaultNuker.nuke() }
                }) {
                    Text(stringResource(R.string.nuke_final_erase), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseFinal = false }) {
                    Text(stringResource(R.string.change_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
