package com.calcplus.calculator.core.disguise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.calcplus.calculator.core.lock.TokenRecorder

/**
 * The one host object per surface instance (§1.7): it owns the
 * [TokenRecorder], translates the face's event stream into a
 * `commit(tokens, overflowed)` for the state machine, and implements the
 * §1.1 overt-face buffer clear.
 *
 * The face itself never sees a buffer, a length rule or a verification result.
 * Nothing in here is logged.
 *
 * @param onInput called for every non-commit event, so the caller can apply the
 *   §1.3 revert rule (a `WRONG_CODE` caption reverts to `PROMPT_CURRENT` on the
 *   next token / clear / removeLast, never on a commit).
 */
@Composable
fun DisguiseSurfaceHost(
    face: DisguiseProvider,
    mode: DisguiseMode,
    caption: CaptionState?,
    failedAttemptToken: Int,
    onCommit: (tokens: List<String>, overflowed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onInput: () -> Unit = {},
) {
    val recorder = remember { TokenRecorder() }

    // §1.1: whenever the pulse bumps for an OVERT face, the buffer and the
    // overflow flag go with it. The face visibly resets its entry, so anything
    // typed during the verification window must not linger in a buffer the face
    // no longer depicts. The calculator (covert) keeps its buffer in
    // verifyCurrent — that is iteration-1 behavior and it stays.
    //
    // Keyed on the token, so it skips nothing on the initial composition beyond
    // one harmless clear of an already-empty buffer.
    LaunchedEffect(failedAttemptToken) {
        if (!face.isCovert) recorder.clear()
    }

    face.Surface(
        mode = mode,
        caption = caption,
        failedAttemptToken = failedAttemptToken,
        events = { event ->
            val commit = recorder.handle(event)
            if (commit != null) {
                onCommit(commit.tokens, commit.overflowed)
            } else {
                onInput()
            }
        },
        modifier = modifier,
    )
}
