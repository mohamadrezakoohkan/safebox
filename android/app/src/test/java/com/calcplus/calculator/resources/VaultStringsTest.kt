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
}
