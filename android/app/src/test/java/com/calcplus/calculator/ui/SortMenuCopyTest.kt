package com.calcplus.calculator.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.R
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.ui.components.labelRes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the sort menu reads (decisions §4/§10).
 *
 * `sort_title` is the menu's visible header on both platforms — iOS renders it
 * as the inline picker's section label, Android as the `DropdownMenu`'s header
 * row — so it must be the same string the icon announces, not an
 * Android-only content description.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class SortMenuCopyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theMenuHeaderIsTheSharedSortTitle() {
        assertEquals("Sort by", context.getString(R.string.sort_title))
    }

    @Test
    fun everyAlbumModeReadsItsDecidedLabelInMenuOrder() {
        assertEquals(
            listOf("Manual", "Name", "Date created", "Photo count"),
            AlbumSort.entries.map { context.getString(it.labelRes) },
        )
    }

    @Test
    fun everyNoteModeReadsItsDecidedLabelInMenuOrder() {
        assertEquals(
            listOf("Date modified", "Date created", "Title"),
            NoteSort.entries.map { context.getString(it.labelRes) },
        )
    }

    @Test
    fun theTwoMenusShareTheDateCreatedLabel() {
        // One §10 ID (`sort_date_created`) serves both lists — a second string
        // with the same English would drift on translation.
        assertEquals(AlbumSort.DATE_CREATED.labelRes, NoteSort.DATE_CREATED.labelRes)
    }
}
