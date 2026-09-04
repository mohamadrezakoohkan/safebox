package com.calcplus.calculator.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.R
import com.calcplus.calculator.core.disguise.DisguiseSurfaceHost
import com.calcplus.calculator.di.AppContainer

/**
 * Hosts the lock face driven by the change-passcode state machine, with a
 * Cancel action visible in every phase. Same face as the lock screen, in a
 * different mode — and the face is preserved by the single `set()` at the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasscodeScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val currentFace by container.lockManager.activeDisguise.collectAsStateWithLifecycle()
    val viewModel: ChangePasscodeViewModel = viewModel {
        ChangePasscodeViewModel(
            passcodeRepository = container.passcodeRepository,
            registry = container.disguiseRegistry,
            currentFace = currentFace,
        )
    }
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val caption by viewModel.caption.collectAsStateWithLifecycle()
    val failedAttemptToken by viewModel.failedAttemptToken.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val changedMessage = stringResource(R.string.change_success)

    LaunchedEffect(phase) {
        if (phase == ChangePasscodeViewModel.Phase.Done) {
            Toast.makeText(context, changedMessage, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_change_title)) },
                navigationIcon = {
                    TextButton(onClick = onDone) {
                        Text(stringResource(R.string.change_cancel))
                    }
                },
            )
        },
    ) { padding ->
        val face = viewModel.faceForPhase(phase)
        // Keyed on the face id only: a phase change within the flow keeps the
        // surface (and the calculator's display) exactly as it was (§1.5).
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
