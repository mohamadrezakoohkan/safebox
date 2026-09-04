package com.calcplus.calculator.core.disguise

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The host↔disguise contract (iteration-3-decisions §1.7, amending the
 * disguise-skeleton-plan §3 six-item contract).
 *
 * A disguise is a **lock face**: a renderer and an input device, nothing more.
 * Every security decision — buffering, length rules, the KDF, storage, the lock
 * state machine, mode assignment — lives in the host. The review heuristic from
 * the skeleton plan still holds: if a line of code makes a decision that would
 * appear in a security audit, it does not belong in this package's
 * implementations.
 *
 * The event stream is one-way and semantic. A face never learns whether an
 * attempt matched, which token was wrong, or that a vault exists at all; the
 * only thing that ever flows back is [DisguiseMode], a [CaptionState] of
 * semantic kinds, and the failed-attempt pulse (§1.1).
 */

/**
 * Everything a face can say to the host. `removeLast` was added in iteration 3
 * (§1.2) and is emitted by the PIN pad only; the calculator and the pattern
 * never emit it.
 */
sealed interface DisguiseEvent {
    /** One alphabet token was entered. [id] is opaque to the host. */
    data class Token(val id: String) : DisguiseEvent

    /** Backspace: drop the most recent token (no-op on an empty buffer). */
    data object RemoveLast : DisguiseEvent

    /** The face's commit gesture fired (= key, ✓ key, finger lift). */
    data object Commit : DisguiseEvent

    /** Start over: the buffer and the overflow flag are both reset. */
    data object Clear : DisguiseEvent
}

/** Which job the surface is doing right now. Assigned by the host, always. */
enum class DisguiseMode {
    /** The lock screen proper. No caption slot; overt faces show a static title. */
    DISGUISE,

    /** First-run setup / change flow: enter a new code. */
    CAPTURE_NEW,

    /** Re-enter the code just captured. */
    CONFIRM_NEW,

    /** Prove the current code (change passcode, switch disguise). */
    VERIFY_CURRENT,
}

/**
 * A face's token alphabet. [tokenSetId] equals the disguise id; the serialized
 * form is the universal `|`-join and is version- and set-invariant by rule —
 * it is the exact input to the KDF on both platforms.
 */
data class AlphabetDescriptor(
    val tokenSetId: String,
    val alphabetVersion: Int,
    val tokens: List<String>,
) {
    init {
        require(tokens.none { it.contains('|') }) { "token ids must not contain the separator" }
    }

    companion object {
        /** The one serialization used by every alphabet, forever. */
        fun serialize(tokens: List<String>): String = tokens.joinToString("|")
    }
}

/**
 * Semantic caption content (§1.3). The host never carries literal strings; each
 * face maps a kind to its own copy through [DisguiseProvider.captionRes].
 */
enum class CaptionKind {
    PROMPT_NEW_SETUP,
    PROMPT_NEW_CHANGE,
    STRENGTH_HINT,
    TOO_SHORT,
    TOO_LONG,
    PROMPT_CONFIRM_SETUP,
    PROMPT_CONFIRM_CHANGE,
    MISMATCH,
    TRIVIAL_WARNING,
    PROMPT_CURRENT,
    WRONG_CODE,
}

/**
 * Caption strip content: a primary line and an optional secondary hint line.
 * [WRONG_CODE][CaptionKind.WRONG_CODE] is the only kind rendered in the error
 * color, so [isError] is derived rather than carried.
 */
data class CaptionState(
    val primary: CaptionKind,
    val secondary: CaptionKind? = null,
) {
    val isError: Boolean get() = primary == CaptionKind.WRONG_CODE
}

/** How well the face matches the app's shipped identity (§1.4, §6). */
enum class IdentityGrade { NATIVE, PLAUSIBLE, INCOHERENT }

/**
 * The guide/picker content slot (§1.4). The card thumbnail is NOT part of this
 * slot — it is [DisguiseProvider.CoverFace] rendered at scale (§6).
 */
interface DisguiseGuideContent {
    val identityGrade: IdentityGrade

    @get:StringRes val page3Title: Int

    @get:StringRes val page3Body: Int

    @get:StringRes val page3Try: Int

    @get:StringRes val page3Ok: Int

    @get:StringRes val page4Title: Int

    @get:StringRes val page4Body: Int

    /** Pattern only: the screen-reader disclosure. Null everywhere else. */
    @get:StringRes val a11yNote: Int?

    /**
     * A small interactive demo for guide page 3. It reports a tap/node count
     * and NOTHING else — no token ids ever leave it, and nothing is saved.
     * [resetToken] is bumped by the page's shared "Reset" button
     * (`onboarding_page3_clear`), so a playground never has to expose its own
     * state to the page chrome.
     */
    @Composable fun Playground(resetToken: Int, onCountChanged: (Int) -> Unit)

    /** A looping, non-interactive illustration of the commit gesture. */
    @Composable fun CommitHero()
}

/**
 * One lock face. Implementations live in `feature/<id>/` and are compiled in;
 * the registry order is append-only (§1.6).
 */
interface DisguiseProvider {
    /** Stable, permanent identifier; equals `alphabet.tokenSetId`. */
    val id: String

    val alphabet: AlphabetDescriptor

    /** Covert faces stay silent on a non-match; overt faces shake and clear. */
    val isCovert: Boolean

    @get:StringRes val displayName: Int

    @get:StringRes val tagline: Int

    @get:StringRes val commitGesture: Int

    val guide: DisguiseGuideContent

    /** This face's copy for a semantic caption kind. */
    @StringRes fun captionRes(kind: CaptionKind): Int

    /**
     * The live surface. [failedAttemptToken] is monotonic per surface instance
     * and starts at 0; the face reacts only to increments observed AFTER its
     * first render (§1.1).
     */
    @Composable fun Surface(
        mode: DisguiseMode,
        caption: CaptionState?,
        failedAttemptToken: Int,
        events: (DisguiseEvent) -> Unit,
        modifier: Modifier,
    )

    /**
     * The resting face with no entry — used for carousel and picker thumbnails
     * (§6). Non-interactive by contract: callers render it inert.
     */
    @Composable fun CoverFace()
}
