package com.calcplus.calculator.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the iteration-2 shared string table (iteration-2-decisions §10): the IDs must exist,
 * resolve to the agreed English source, and positional format arguments must substitute correctly.
 * Pinned to en-rUS so number formatting in the format-argument checks is deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class VaultStringsTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun plainKeysResolveToEnglishSource() {
        assertEquals("No albums yet", context.getString(R.string.empty_albums_title))
        assertEquals("Recently deleted", context.getString(R.string.trash_title))
        assertEquals("Done", context.getString(R.string.onboarding_done))
    }

    @Test
    fun positionalFormatArgumentsSubstitute() {
        assertEquals("3 selected", context.getString(R.string.selection_count, 3))
        assertEquals("Delete album and its 12 photos?", context.getString(R.string.confirm_delete_album, 12))
        assertEquals("Importing 2/5…", context.getString(R.string.import_progress, 2, 5))
    }

    /**
     * Iteration-3 §7 renames: the calculator's guide copy moved to the
     * `calculator_guide_*` scheme, word for word.
     */
    @Test
    fun renamedGuideStringsKeepTheirTextVerbatim() {
        assertEquals(
            "Your code is a key sequence",
            context.getString(R.string.calculator_guide_page3_title),
        )
        assertEquals(
            "Pick 4 to 32 calculator keys — digits and symbols all count, and order matters. " +
                "Try one here. This is just practice; nothing is saved.",
            context.getString(R.string.calculator_guide_page3_body),
        )
        assertEquals("Tap at least 4 keys", context.getString(R.string.calculator_guide_try))
        assertEquals(
            "That would work — symbols make it stronger",
            context.getString(R.string.calculator_guide_ok),
        )
        assertEquals("Press = to enter", context.getString(R.string.calculator_guide_page4_title))
        assertEquals(
            "To unlock, type your code on the calculator and press =. A wrong code just " +
                "calculates — no error, no hint that anything is hidden.",
            context.getString(R.string.calculator_guide_page4_body),
        )
    }

    /** …while the shared page chrome around them is untouched. */
    @Test
    fun sharedGuideChromeIsUnchanged() {
        assertEquals("Reset", context.getString(R.string.onboarding_page3_clear))
        assertEquals(
            "There is no recovery. If you forget your code, the vault stays locked forever.",
            context.getString(R.string.onboarding_page4_warning),
        )
        assertEquals("Skip", context.getString(R.string.onboarding_skip))
        assertEquals("Set my code", context.getString(R.string.onboarding_start))
    }

    /**
     * Retired IDs (§7): `onboarding_page1_title` / `_body` are gone, replaced by
     * the carousel's own copy. Resolved by name so the test compiles either way
     * and fails loudly if either is reintroduced.
     */
    @Test
    fun retiredPageOneStringsAreDeleted() {
        listOf("onboarding_page1_title", "onboarding_page1_body").forEach { name ->
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            assertEquals("$name must no longer exist", 0, id)
        }
    }

    @Test
    fun theCarouselCopyReplacesIt() {
        assertEquals("Pick a disguise", context.getString(R.string.onboarding_disguise_title))
        assertEquals(
            "Anyone who opens Calculator+ sees only this screen. You can change it later in Settings.",
            context.getString(R.string.onboarding_disguise_body),
        )
        assertEquals(
            "This is your current disguise. You can change it in Settings → Change disguise.",
            context.getString(R.string.onboarding_disguise_revisit_hint),
        )
        assertEquals("Current", context.getString(R.string.disguise_current_badge))
        assertEquals(
            "Matches the app's name and icon",
            context.getString(R.string.disguise_grade_native),
        )
        assertEquals(
            "Doesn't match the app's name and icon",
            context.getString(R.string.disguise_grade_incoherent),
        )
        assertEquals("Change disguise", context.getString(R.string.settings_change_disguise_title))
        assertEquals("Use this disguise", context.getString(R.string.disguise_pick_action))
        assertEquals("Not usable with a screen reader", context.getString(R.string.pattern_a11y_note))
    }
}
