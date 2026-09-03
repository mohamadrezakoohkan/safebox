package com.calcplus.calculator.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcplus.calculator.core.update.UpdateCheckResult
import com.calcplus.calculator.core.update.UpdateChecker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The "Check for updates" row's subtitle, as a state machine (decisions §13). */
sealed interface UpdateState {
    /** Nothing asked yet: the row has no subtitle. */
    data object Idle : UpdateState

    /** A request is in flight. */
    data object Checking : UpdateState

    /** Manifest says this build is current (or newer than the manifest). */
    data object UpToDate : UpdateState

    /** A newer [version] exists; tapping the row now opens [releasesUrl]. */
    data class Available(val version: String, val releasesUrl: String) : UpdateState

    /** Offline or unreadable manifest. Tapping retries. */
    data object Failed : UpdateState
}

/**
 * Owns the app's only network request (decisions §13).
 *
 * Lives in `viewModelScope`, **never** `applicationScope`: the request must die
 * with the vault, so locking (which tears the Settings tab down) abandons an
 * in-flight check. The blocking work runs on [ioDispatcher] (`Dispatchers.IO`
 * in production, the test dispatcher in tests).
 *
 * [start] is called from a tap handler only — nothing here is triggered at app
 * start, on unlock or on a timer.
 */
class UpdateCheckViewModel(
    private val currentVersion: String,
    private val checker: UpdateChecker = UpdateChecker(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * Runs one check. Re-entrant taps while [UpdateState.Checking] is showing
     * are ignored, so a row mash cannot fan out into parallel requests.
     */
    fun check() {
        if (_state.value == UpdateState.Checking) return
        _state.value = UpdateState.Checking
        viewModelScope.launch(ioDispatcher) {
            _state.value = when (val result = checker.check(currentVersion)) {
                UpdateCheckResult.UpToDate -> UpdateState.UpToDate
                is UpdateCheckResult.Available -> UpdateState.Available(result.version, result.releasesUrl)
                UpdateCheckResult.Failed -> UpdateState.Failed
            }
        }
    }
}
