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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.R
import com.calcplus.calculator.app.bannerString
import com.calcplus.calculator.di.AppContainer
import com.calcplus.calculator.feature.calculator.CalculatorScreen
import com.calcplus.calculator.feature.calculator.CalculatorSession
import com.calcplus.calculator.feature.calculator.CaptionState

/**
 * Hosts the calculator surface driven by the change-passcode state machine,
 * with a Cancel action visible in every phase. Same component as the lock
 * screen, in a different mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasscodeScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val viewModel: ChangePasscodeViewModel = viewModel {
        ChangePasscodeViewModel(container.passcodeRepository)
    }
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val bannerIsError by viewModel.bannerIsError.collectAsStateWithLifecycle()
    val shakeToken by viewModel.shakeToken.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val changedMessage = stringResource(R.string.change_success)

    val session = remember {
        CalculatorSession(
            onKeyPress = { viewModel.keyPressed() },
            onCommit = { keys, overflowed -> viewModel.commit(keys, overflowed) },
        )
    }

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
        CalculatorScreen(
            session = session,
            caption = CaptionState(
                primary = bannerString(banner.primary),
                secondary = banner.secondary?.let { bannerString(it) },
                isError = bannerIsError,
            ),
            shakeToken = shakeToken,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
