package com.calcplus.calculator.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.ui.components.EmptyStateContent
import com.calcplus.calculator.core.ui.components.VaultEmptyStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P2 empty-state catalog (iteration-2-decisions §2): every vault empty state resolves to the
 * decided English title and one-line body, an action exists exactly where the §2 table lists
 * one, and the notes/contacts selectors switch to "No results" under any active search or filter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class EmptyStateContentTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    /** (title, body, action-or-null) as the user would read them. */
    private fun EmptyStateContent.resolved(): Triple<String, String, String?> = Triple(
        context.getString(title),
        context.getString(body),
        action?.let(context::getString),
    )

    // The four "nothing yet" states: title + body + action.

    @Test
    fun albumsState() {
        assertEquals(
            Triple("No albums yet", "Albums keep your imported photos and videos organized.", "Create album"),
            VaultEmptyStates.albums.resolved(),
        )
    }

    @Test
    fun photosState() {
        assertEquals(
            Triple("No photos yet", "Imports are copies — the originals stay in your library.", "Import photos"),
            VaultEmptyStates.photos.resolved(),
        )
    }

    @Test
    fun notesState() {
        assertEquals(
            Triple("No notes yet", "Notes support markdown with a live preview.", "New note"),
            VaultEmptyStates.notes.resolved(),
        )
    }

    @Test
    fun contactsState() {
        assertEquals(
            Triple("No contacts yet", "Contacts live only in this vault.", "Add contact"),
            VaultEmptyStates.contacts.resolved(),
        )
    }

    // Filtered / search states: title + body, no action.

    @Test
    fun noResultsStateHasDescriptionAndNoAction() {
        assertEquals(
            Triple("No results", "Check the spelling or try a different search.", null),
            VaultEmptyStates.noResults.resolved(),
        )
    }

    @Test
    fun searchNoQueryStateHasDescriptionAndNoAction() {
        assertEquals(
            Triple("Search your vault", "Find notes, contacts, and albums by name or content.", null),
            VaultEmptyStates.searchNoQuery.resolved(),
        )
    }

    @Test
    fun trashStateHasDescriptionAndNoAction() {
        assertEquals(
            Triple("Nothing here", "Deleted items appear here for 30 days.", null),
            VaultEmptyStates.trash.resolved(),
        )
        assertEquals(Icons.Filled.Delete, VaultEmptyStates.trash.icon)
    }

    @Test
    fun everyStateHasASingleLineBody() {
        val all = with(VaultEmptyStates) {
            listOf(albums, photos, notes, contacts, noResults, searchNoQuery, trash)
        }
        all.forEach { state ->
            val body = context.getString(state.body)
            assertFalse(body, body.isBlank())
            assertFalse(body, body.contains('\n'))
        }
    }

    // Glyphs: a "nothing yet" state shows its tab's glyph; every search state shows the magnifier
    // (mirrors the iOS presets photo / note.text / person.crop.circle / magnifyingglass).

    @Test
    fun nothingYetStatesShowTheirTabGlyph() {
        assertEquals(Icons.Filled.Photo, VaultEmptyStates.albums.icon)
        assertEquals(Icons.Filled.Photo, VaultEmptyStates.photos.icon)
        assertEquals(Icons.AutoMirrored.Filled.Note, VaultEmptyStates.notes.icon)
        assertEquals(Icons.Filled.Person, VaultEmptyStates.contacts.icon)
    }

    @Test
    fun searchStatesShowTheMagnifier() {
        assertEquals(Icons.Filled.Search, VaultEmptyStates.noResults.icon)
        assertEquals(Icons.Filled.Search, VaultEmptyStates.searchNoQuery.icon)
        // So filtering a tab swaps its glyph for the magnifier along with the copy.
        assertEquals(Icons.Filled.Search, VaultEmptyStates.forNotes("groceries", null).icon)
        assertEquals(Icons.Filled.Search, VaultEmptyStates.forNotes("", "tag-1").icon)
        assertEquals(Icons.Filled.Search, VaultEmptyStates.forContacts("ann").icon)
    }

    // Notes selector: any active query or tag filter means "No results".

    @Test
    fun notesWithoutQueryOrFilterIsNoNotesYet() {
        assertSame(VaultEmptyStates.notes, VaultEmptyStates.forNotes("", null))
        assertSame(VaultEmptyStates.notes, VaultEmptyStates.forNotes("   ", null))
    }

    @Test
    fun notesWithQueryIsNoResults() {
        assertSame(VaultEmptyStates.noResults, VaultEmptyStates.forNotes("groceries", null))
    }

    @Test
    fun notesWithTagFilterIsNoResults() {
        assertSame(VaultEmptyStates.noResults, VaultEmptyStates.forNotes("", "tag-1"))
        assertSame(VaultEmptyStates.noResults, VaultEmptyStates.forNotes("groceries", "tag-1"))
    }

    // Contacts selector: an active query means "No results".

    @Test
    fun contactsWithoutQueryIsNoContactsYet() {
        assertSame(VaultEmptyStates.contacts, VaultEmptyStates.forContacts(""))
        assertSame(VaultEmptyStates.contacts, VaultEmptyStates.forContacts("  "))
    }

    @Test
    fun contactsWithQueryIsNoResults() {
        assertSame(VaultEmptyStates.noResults, VaultEmptyStates.forContacts("ann"))
    }
}
