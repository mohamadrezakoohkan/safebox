package com.calcplus.calculator.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.R
import com.calcplus.calculator.core.ui.components.ConfirmDeleteTitle
import com.calcplus.calculator.core.ui.components.SelectionCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The single count-bearing confirm dialog a bulk delete shows (decisions
 * §6/§10): one dialog for the whole selection, singular copy at exactly one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class SelectionCopyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun resolve(title: ConfirmDeleteTitle): String =
        if (title.count == null) context.getString(title.res)
        else context.getString(title.res, title.count)

    @Test
    fun oneSelectedNoteUsesTheSingularStringWithNoArgument() {
        val title = SelectionCopy.confirmDeleteNotes(1)
        assertEquals(R.string.confirm_delete_note, title.res)
        assertNull(title.count)
    }

    @Test
    fun severalSelectedNotesUseThePluralStringWithTheCount() {
        val title = SelectionCopy.confirmDeleteNotes(4)
        assertEquals(R.string.confirm_delete_notes, title.res)
        assertEquals(4, title.count)
    }

    @Test
    fun oneSelectedContactUsesTheSingularStringWithNoArgument() {
        val title = SelectionCopy.confirmDeleteContacts(1)
        assertEquals(R.string.confirm_delete_contact, title.res)
        assertNull(title.count)
    }

    @Test
    fun severalSelectedContactsUseThePluralStringWithTheCount() {
        val title = SelectionCopy.confirmDeleteContacts(9)
        assertEquals(R.string.confirm_delete_contacts, title.res)
        assertEquals(9, title.count)
    }

    @Test
    fun resolvedTitlesAndTheSelectionBarReadAsTheSharedCopyTable() {
        assertEquals("Delete this note?", resolve(SelectionCopy.confirmDeleteNotes(1)))
        assertEquals("Delete 3 notes?", resolve(SelectionCopy.confirmDeleteNotes(3)))
        assertEquals("Delete this contact?", resolve(SelectionCopy.confirmDeleteContacts(1)))
        assertEquals("Delete 2 contacts?", resolve(SelectionCopy.confirmDeleteContacts(2)))
        assertEquals(
            "You can restore it from Recently deleted for 30 days.",
            context.getString(R.string.confirm_delete_body_trash),
        )
        assertEquals("5 selected", context.getString(R.string.selection_count, 5))
        assertEquals("Cancel", context.getString(R.string.cancel_action))
        assertEquals("Delete", context.getString(R.string.delete_action))
    }
}
