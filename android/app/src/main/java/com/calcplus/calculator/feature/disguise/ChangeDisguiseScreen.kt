package com.calcplus.calculator.feature.disguise

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.R
import com.calcplus.calculator.core.disguise.DisguiseSurfaceHost
import com.calcplus.calculator.core.ui.theme.DisguiseTheme
import com.calcplus.calculator.di.AppContainer
import com.calcplus.calculator.feature.settings.ChangePasscodeViewModel

/**
 * Settings → Change disguise (decisions §5). Mirrors `ChangePasscodeRoute`:
 * the same generalized state machine, with a picker step after verification
 * and the new face used from `ENTER_NEW` on.
 *
 * `VERIFY_CURRENT → PICK → CAPTURE_NEW → CONFIRM_NEW → COMMIT`. Cancel is
 * visible in every phase, and abandoning at any step leaves the old code and
 * the old face fully intact — the single `set()` at the end is the only write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeDisguiseScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val theme = if (isSystemInDarkTheme()) DisguiseTheme.Dark else DisguiseTheme.Light
    val currentFace by container.lockManager.activeDisguise.collectAsStateWithLifecycle()
    val viewModel: ChangePasscodeViewModel = viewModel {
        ChangePasscodeViewModel(
            passcodeRepository = container.passcodeRepository,
            registry = container.disguiseRegistry,
            currentFace = currentFace,
            switchDisguise = true,
            onDisguiseChanged = { id -> container.lockManager.setActiveDisguise(id) },
        )
    }
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val caption by viewModel.caption.collectAsStateWithLifecycle()
    val failedAttemptToken by viewModel.failedAttemptToken.collectAsStateWithLifecycle()
    val targetId by viewModel.targetDisguiseId.collectAsStateWithLifecycle()
    var showSuccess by remember { mutableStateOf(false) }

    if (phase == ChangePasscodeViewModel.Phase.Done && !showSuccess) {
        showSuccess = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_change_disguise_title)) },
                navigationIcon = {
                    TextButton(onClick = onDone) {
                        Text(stringResource(R.string.change_cancel))
                    }
                },
            )
        },
    ) { padding ->
        if (phase == ChangePasscodeViewModel.Phase.PickDisguise) {
            PickPage(
                container = container,
                currentFace = currentFace,
                selectedId = targetId,
                onSelectedChange = viewModel::selectTargetDisguise,
                onConfirm = viewModel::confirmPick,
                theme = theme,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            val face = viewModel.faceForPhase(phase)
            // The face identity change PICK → CAPTURE_NEW is a fresh surface
            // (§1.5); a phase change within one face is not.
            key(face.id) {
                DisguiseSurfaceHost(
                    face = face,
                    mode = viewModel.modeForPhase(phase),
                    caption = caption,
                    failedAttemptToken = failedAttemptToken,
                    onCommit = { tokens, overflowed -> viewModel.commit(tokens, overflowed) },
                    onInput = { viewModel.inputReceived() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }
    }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.disguise_switch_success_title)) },
            text = { Text(stringResource(R.string.disguise_switch_success_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showSuccess = false
                    onDone()
                }) {
                    Text(stringResource(R.string.ok_action))
                }
            },
        )
    }
}

@Composable
private fun PickPage(
    container: AppContainer,
    currentFace: com.calcplus.calculator.core.disguise.DisguiseProvider,
    selectedId: String,
    onSelectedChange: (String) -> Unit,
    onConfirm: () -> Unit,
    theme: DisguiseTheme,
    modifier: Modifier,
) {
    // The card, the disclosure and the explainer together overflow a phone
    // screen, so they scroll — but the primary action must NOT scroll with
    // them. Pinned below the scroll area it is always visible; inside it, it
    // sat just past the bottom edge with nothing hinting there was more, and
    // the switch looked like a dead end.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            DisguiseCarousel(
                registry = container.disguiseRegistry,
                mode = CarouselMode.PICK,
                current = currentFace,
                selectedId = selectedId,
                onSelectedChange = onSelectedChange,
                theme = theme,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(
                    R.string.disguise_switch_explainer,
                    stringResource(currentFace.displayName),
                    stringResource(currentFace.commitGesture),
                ),
                color = theme.caption,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        Button(
            // The current face cannot be picked, so there is no no-op path.
            enabled = selectedId != currentFace.id,
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.keyOp,
                contentColor = theme.keyLabelOnOp,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp),
        ) {
            Text(stringResource(R.string.disguise_pick_action), fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
