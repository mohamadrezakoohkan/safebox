package com.calcplus.calculator.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.BuildConfig
import com.calcplus.calculator.R
import com.calcplus.calculator.core.update.UpdateEndpoints
import com.calcplus.calculator.di.AppContainer
import kotlinx.coroutines.launch

/**
 * Vault settings (decisions §5): Security → Data → About. No auto-lock setting
 * (backgrounding always locks immediately) and no biometric UI of any kind.
 * Every string is a shared-ID resource; nothing here is hardcoded.
 *
 * @param onOpenGuide "How it works": opens the onboarding guide in revisit
 *   mode (a full-screen route in the Settings tab graph, so it exists only
 *   inside the unlocked vault — the guide's vault vocabulary is never reachable
 *   from the locked calculator).
 * @param onOpenPrivacy pushes [PrivacyScreen].
 * @param onOpenTrash pushes the "Recently deleted" screen (decisions §3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onChangePasscode: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    var showEraseConfirm by remember { mutableStateOf(false) }
    var showEraseFinal by remember { mutableStateOf(false) }

    // The app's only network request (decisions §13). Scoped to this screen's
    // ViewModel, so a lock tears the Settings tab down and abandons any
    // in-flight check; nothing here runs until the row is tapped.
    val context = LocalContext.current
    val updateViewModel: UpdateCheckViewModel = viewModel {
        UpdateCheckViewModel(currentVersion = BuildConfig.VERSION_NAME)
    }
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(stringResource(R.string.settings_section_security))
            SettingsRow(title = stringResource(R.string.settings_change_title), onClick = onChangePasscode)
            SettingsRow(title = stringResource(R.string.settings_lock_now), onClick = { container.lockManager.lock() })

            SectionDivider()
            SectionLabel(stringResource(R.string.settings_section_data))
            // "Recently deleted" (decisions §3/§5): the one entry point to the
            // trash, above Erase everything, which stays the red last row.
            SettingsRow(
                title = stringResource(R.string.trash_title),
                subtitle = stringResource(R.string.trash_subtitle),
                onClick = onOpenTrash,
            )
            SettingsRow(
                title = stringResource(R.string.nuke_row_title),
                subtitle = stringResource(R.string.nuke_row_subtitle),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showEraseConfirm = true },
            )

            SectionDivider()
            SectionLabel(stringResource(R.string.settings_section_about))
            SettingsRow(title = stringResource(R.string.settings_version), value = BuildConfig.VERSION_NAME)
            // Source code + manual update check (decisions §13). Both leave the
            // app for the browser, which backgrounds the vault and therefore
            // locks it — expected, and called out in the manual checks.
            SettingsRow(
                title = stringResource(R.string.settings_source_code),
                subtitle = stringResource(R.string.settings_source_code_subtitle),
                onClick = { openUrl(context, UpdateEndpoints.SOURCE_URL) },
            )
            // The subtitle IS the result; the tap target changes to the release
            // page only once an update is actually available.
            val available = updateState as? UpdateState.Available
            val updateSubtitle = when (val state = updateState) {
                UpdateState.Idle -> null
                UpdateState.Checking -> stringResource(R.string.settings_update_checking)
                UpdateState.UpToDate -> stringResource(R.string.settings_update_up_to_date)
                is UpdateState.Available -> stringResource(R.string.settings_update_available, state.version)
                UpdateState.Failed -> stringResource(R.string.settings_update_failed)
            }
            SettingsRow(
                title = stringResource(R.string.settings_check_updates),
                subtitle = updateSubtitle,
                onClick = {
                    if (available != null) {
                        openUrl(context, available.releasesUrl)
                    } else {
                        updateViewModel.check()
                    }
                },
            )
            // An action, not a paragraph: re-opens the real guide in revisit mode.
            SettingsRow(
                title = stringResource(R.string.settings_how_it_works),
                subtitle = stringResource(R.string.settings_how_it_works_subtitle),
                onClick = onOpenGuide,
            )
            SettingsRow(
                title = stringResource(R.string.settings_privacy_title),
                subtitle = stringResource(R.string.settings_privacy_subtitle),
                onClick = onOpenPrivacy,
            )
            // Android-only row (decisions §5, accepted asymmetry). N3 appends
            // Media3 to `settings_licenses_body`.
            SettingsRow(
                title = stringResource(R.string.settings_licenses),
                subtitle = stringResource(R.string.settings_licenses_body),
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
                    Text(stringResource(R.string.cancel_action))
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
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }
}

/**
 * Hands [url] to whatever handles web links. Swallows the "no browser at all"
 * case: an [android.content.ActivityNotFoundException] must not crash Settings,
 * and there is nothing useful to say about it (and nothing is logged — the URL
 * names the repo).
 */
private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

/**
 * One Settings row: title, optional secondary [subtitle] under it, optional
 * inline [value] at the trailing edge (Version), optionally clickable.
 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        value?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
