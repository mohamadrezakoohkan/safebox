package com.calcplus.calculator.disguise

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.R
import com.calcplus.calculator.core.disguise.CaptionKind
import com.calcplus.calculator.feature.calculator.CalculatorDisguise
import com.calcplus.calculator.feature.numpad.NumpadDisguise
import com.calcplus.calculator.feature.pattern.PatternDisguise
import com.calcplus.calculator.lock.testRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every semantic caption kind must render, on every face, as the exact copy the
 * shared string table pins (iteration-3-decisions §2, §7). The calculator's
 * eleven are the iteration-1 strings VERBATIM — the whole point of re-homing
 * the calculator without changing a word of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class FaceCaptionMappingTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun caption(faceId: String, kind: CaptionKind): String =
        context.getString(testRegistry().resolve(faceId).captionRes(kind))

    @Test
    fun calculatorMapsToThePinnedIterationOneStrings() {
        assertEquals(R.string.setup_entry_banner, CalculatorDisguise.captionRes(CaptionKind.PROMPT_NEW_SETUP))
        assertEquals(R.string.change_enter_new_caption, CalculatorDisguise.captionRes(CaptionKind.PROMPT_NEW_CHANGE))
        assertEquals(R.string.setup_entry_hint, CalculatorDisguise.captionRes(CaptionKind.STRENGTH_HINT))
        assertEquals(R.string.setup_too_short, CalculatorDisguise.captionRes(CaptionKind.TOO_SHORT))
        assertEquals(R.string.setup_too_long, CalculatorDisguise.captionRes(CaptionKind.TOO_LONG))
        assertEquals(R.string.setup_confirm_banner, CalculatorDisguise.captionRes(CaptionKind.PROMPT_CONFIRM_SETUP))
        assertEquals(R.string.change_confirm_caption, CalculatorDisguise.captionRes(CaptionKind.PROMPT_CONFIRM_CHANGE))
        assertEquals(R.string.setup_mismatch, CalculatorDisguise.captionRes(CaptionKind.MISMATCH))
        assertEquals(R.string.setup_trivial_warning, CalculatorDisguise.captionRes(CaptionKind.TRIVIAL_WARNING))
        assertEquals(R.string.verify_current_caption, CalculatorDisguise.captionRes(CaptionKind.PROMPT_CURRENT))
        assertEquals(R.string.verify_error, CalculatorDisguise.captionRes(CaptionKind.WRONG_CODE))
    }

    @Test
    fun calculatorCopyIsUnchangedWordForWord() {
        assertEquals(
            "Set your secret code: type it on the keypad, then press =",
            caption("calculator", CaptionKind.PROMPT_NEW_SETUP),
        )
        assertEquals("Incorrect code — try again", caption("calculator", CaptionKind.WRONG_CODE))
        assertEquals(
            "Re-enter the same code, then press =",
            caption("calculator", CaptionKind.PROMPT_CONFIRM_SETUP),
        )
    }

    @Test
    fun numpadNamesItsOwnCommitGesture() {
        assertEquals("Enter your current PIN, then tap ✓", caption("numpad", CaptionKind.PROMPT_CURRENT))
        assertEquals("Incorrect PIN — try again", caption("numpad", CaptionKind.WRONG_CODE))
        assertEquals("Too long — start again (max 32 digits)", caption("numpad", CaptionKind.TOO_LONG))
    }

    @Test
    fun patternNamesItsOwnCommitGesture() {
        assertEquals("Draw your current pattern", caption("pattern", CaptionKind.PROMPT_CURRENT))
        assertEquals("Wrong pattern — try again", caption("pattern", CaptionKind.WRONG_CODE))
    }

    @Test
    fun patternMapsItsUnreachableKindsDefensively() {
        // TOO_LONG and TRIVIAL_WARNING cannot occur on a 9-node, no-repeat
        // alphabet; they must still resolve to something sensible rather than
        // leaving the strip empty.
        assertEquals(R.string.pattern_prompt_new, PatternDisguise.captionRes(CaptionKind.TOO_LONG))
        assertEquals(R.string.pattern_hint, PatternDisguise.captionRes(CaptionKind.TRIVIAL_WARNING))
    }

    @Test
    fun everyKindResolvesOnEveryFace() {
        testRegistry().faces.forEach { face ->
            CaptionKind.entries.forEach { kind ->
                val text = context.getString(face.captionRes(kind))
                assertFalse("${face.id}/$kind must not be blank", text.isBlank())
            }
        }
    }

    /**
     * §7: no string reachable from a LOCKED face may leak vault vocabulary.
     * Face titles and every caption are checked; guide, picker and alert copy
     * (which render only pre-setup or inside the unlocked vault) are not.
     */
    @Test
    fun noLockScreenStringLeaksVaultVocabulary() {
        val forbidden = listOf("passcode", "vault", "unlock", "safebox")
        val lockScreenStrings = buildList {
            add(context.getString(R.string.numpad_face_title))
            add(context.getString(R.string.pattern_face_title))
            testRegistry().faces.forEach { face ->
                CaptionKind.entries.forEach { kind -> add(context.getString(face.captionRes(kind))) }
            }
        }
        lockScreenStrings.forEach { text ->
            forbidden.forEach { word ->
                assertFalse("\"$text\" contains \"$word\"", text.lowercase().contains(word))
            }
        }
    }

    @Test
    fun eachFaceNamesItselfAndItsGesture() {
        assertEquals("Calculator", context.getString(CalculatorDisguise.displayName))
        assertEquals("the = key", context.getString(CalculatorDisguise.commitGesture))
        assertEquals("PIN pad", context.getString(NumpadDisguise.displayName))
        assertEquals("the ✓ key", context.getString(NumpadDisguise.commitGesture))
        assertEquals("Pattern", context.getString(PatternDisguise.displayName))
        assertEquals("a finger lift", context.getString(PatternDisguise.commitGesture))
    }

    @Test
    fun theSwitchExplainerSubstitutesBothArguments() {
        assertEquals(
            "Your current code belongs to the Calculator disguise and is confirmed with the = key. " +
                "The new disguise needs a new code, set on its own keys. " +
                "Your photos, notes, and contacts are unchanged.",
            context.getString(
                R.string.disguise_switch_explainer,
                context.getString(CalculatorDisguise.displayName),
                context.getString(CalculatorDisguise.commitGesture),
            ),
        )
    }
}
