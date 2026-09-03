package com.calcplus.calculator.sort

import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.domain.model.VaultSorting
import com.calcplus.calculator.core.domain.model.VaultTextFold
import org.junit.Assert.assertEquals
import org.junit.Test

private fun album(
    id: String,
    name: String = id,
    createdAt: Long = 0,
    sortIndex: Int = 0,
    photoCount: Int = 0,
) = Album(
    id = id,
    name = name,
    createdAt = createdAt,
    sortIndex = sortIndex,
    photoCount = photoCount,
    coverThumbFileName = null,
)

private fun sortedNote(
    id: String,
    title: String = id,
    createdAt: Long = 0,
    updatedAt: Long = 0,
) = Note(
    id = id,
    body = title,
    title = title,
    snippet = "",
    createdAt = createdAt,
    updatedAt = updatedAt,
    tags = emptyList(),
)

private fun ids(albums: List<Album>) = albums.map { it.id }
private fun noteIds(notes: List<Note>) = notes.map { it.id }

/**
 * Every sort mode's order INCLUDING every tie-breaker (decisions §4). The
 * tie-breakers are the same on iOS — a change here is a cross-platform change.
 */
class VaultSortingTest {

    // ---- albums ----------------------------------------------------------

    @Test
    fun manualOrdersBySortIndexThenId() {
        val albums = listOf(
            album("c", sortIndex = 2),
            album("b", sortIndex = 0),
            album("a", sortIndex = 0),
        )
        assertEquals(listOf("a", "b", "c"), ids(VaultSorting.sortAlbums(albums, AlbumSort.MANUAL)))
    }

    @Test
    fun nameOrdersAlphabeticallyThenByCreatedAtThenId() {
        val albums = listOf(
            album("3", name = "Trips", createdAt = 10),
            album("1", name = "Trips", createdAt = 5),
            album("2", name = "Trips", createdAt = 5),
            album("0", name = "Archive", createdAt = 99),
        )
        assertEquals(
            listOf("0", "1", "2", "3"),
            ids(VaultSorting.sortAlbums(albums, AlbumSort.NAME)),
        )
    }

    @Test
    fun nameComparisonIgnoresCaseAndDiacritics() {
        val albums = listOf(
            album("z", name = "zebra"),
            album("a", name = "Ångström"),
            album("b", name = "apple"),
        )
        // "Ångström" folds to "angstrom", which sorts before "apple".
        assertEquals(
            listOf("a", "b", "z"),
            ids(VaultSorting.sortAlbums(albums, AlbumSort.NAME)),
        )
    }

    @Test
    fun nameIgnoresSurroundingWhitespace() {
        val albums = listOf(album("b", name = "  beta"), album("a", name = "alpha "))
        assertEquals(listOf("a", "b"), ids(VaultSorting.sortAlbums(albums, AlbumSort.NAME)))
    }

    @Test
    fun dateCreatedIsNewestFirstThenId() {
        val albums = listOf(
            album("b", createdAt = 100),
            album("a", createdAt = 100),
            album("c", createdAt = 200),
        )
        assertEquals(
            listOf("c", "a", "b"),
            ids(VaultSorting.sortAlbums(albums, AlbumSort.DATE_CREATED)),
        )
    }

    @Test
    fun photoCountIsMostFirstThenNameThenId() {
        val albums = listOf(
            album("x", name = "Zulu", photoCount = 3),
            album("y", name = "Alpha", photoCount = 3),
            album("z", name = "Alpha", photoCount = 3),
            album("w", name = "Huge", photoCount = 9),
        )
        assertEquals(
            listOf("w", "y", "z", "x"),
            ids(VaultSorting.sortAlbums(albums, AlbumSort.PHOTO_COUNT)),
        )
    }

    @Test
    fun everyAlbumModeIsATotalOrderOnIdenticalRows() {
        // Same everything but the id: the id tie-break must still produce one
        // stable, reproducible order under every mode.
        val albums = listOf(album("b"), album("a"), album("c"))
        for (mode in AlbumSort.entries) {
            assertEquals(
                "mode $mode",
                listOf("a", "b", "c"),
                ids(VaultSorting.sortAlbums(albums, mode)),
            )
        }
    }

    @Test
    fun sortingAnEmptyOrSingleListIsSafeInEveryMode() {
        for (mode in AlbumSort.entries) {
            assertEquals(emptyList<String>(), ids(VaultSorting.sortAlbums(emptyList(), mode)))
            assertEquals(listOf("a"), ids(VaultSorting.sortAlbums(listOf(album("a")), mode)))
        }
    }

    // ---- notes -----------------------------------------------------------

    @Test
    fun dateModifiedIsNewestFirstThenId() {
        val notes = listOf(
            sortedNote("b", updatedAt = 5),
            sortedNote("a", updatedAt = 5),
            sortedNote("c", updatedAt = 9),
        )
        assertEquals(
            listOf("c", "a", "b"),
            noteIds(VaultSorting.sortNotes(notes, NoteSort.DATE_MODIFIED)),
        )
    }

    @Test
    fun noteDateCreatedIsNewestFirstThenId() {
        val notes = listOf(
            sortedNote("b", createdAt = 1, updatedAt = 99),
            sortedNote("a", createdAt = 1, updatedAt = 0),
            sortedNote("c", createdAt = 7),
        )
        assertEquals(
            listOf("c", "a", "b"),
            noteIds(VaultSorting.sortNotes(notes, NoteSort.DATE_CREATED)),
        )
    }

    @Test
    fun titleOrdersAlphabeticallyThenByUpdatedAtDescendingThenId() {
        val notes = listOf(
            sortedNote("3", title = "Milk", updatedAt = 1),
            sortedNote("1", title = "Milk", updatedAt = 9),
            sortedNote("2", title = "Milk", updatedAt = 9),
            sortedNote("0", title = "Bread"),
        )
        assertEquals(
            listOf("0", "1", "2", "3"),
            noteIds(VaultSorting.sortNotes(notes, NoteSort.TITLE)),
        )
    }

    @Test
    fun titleComparisonIgnoresCaseAndDiacritics() {
        val notes = listOf(
            sortedNote("z", title = "zebra"),
            sortedNote("e", title = "Éclair"),
            sortedNote("f", title = "focus"),
        )
        assertEquals(
            listOf("e", "f", "z"),
            noteIds(VaultSorting.sortNotes(notes, NoteSort.TITLE)),
        )
    }

    @Test
    fun notesWithAnEmptyDerivedTitleSortLast() {
        val notes = listOf(
            sortedNote("empty2", title = ""),
            sortedNote("zebra", title = "Zebra"),
            sortedNote("empty1", title = "   "),
            sortedNote("apple", title = "Apple"),
        )
        assertEquals(
            listOf("apple", "zebra", "empty1", "empty2"),
            noteIds(VaultSorting.sortNotes(notes, NoteSort.TITLE)),
        )
    }

    @Test
    fun everyNoteModeIsATotalOrderOnIdenticalRows() {
        val notes = listOf(sortedNote("b", title = "x"), sortedNote("a", title = "x"))
        for (mode in NoteSort.entries) {
            assertEquals(
                "mode $mode",
                listOf("a", "b"),
                noteIds(VaultSorting.sortNotes(notes, mode)),
            )
        }
    }

    @Test
    fun sortingAnEmptyNoteListIsSafeInEveryMode() {
        for (mode in NoteSort.entries) {
            assertEquals(emptyList<String>(), noteIds(VaultSorting.sortNotes(emptyList(), mode)))
        }
    }

    // ---- the shared fold -------------------------------------------------

    @Test
    fun theFoldIsExactlyTheOneContactSortKeyUses() {
        // One helper, one behavior: sorting a contact by name and sorting an
        // album by name must never disagree about what "Ångström" means.
        val raw = " Ångström "
        val contact = Contact(
            id = "c",
            firstName = null,
            lastName = raw,
            organization = null,
            phones = emptyList(),
            emails = emptyList(),
            address = null,
            notes = null,
            createdAt = 0,
            updatedAt = 0,
        )
        assertEquals(contact.sortKey, VaultTextFold.fold(raw))
        assertEquals("angstrom", VaultTextFold.fold(raw))
    }

    @Test
    fun theFoldLeavesNonLatinTextIntact() {
        assertEquals("日本語", VaultTextFold.fold(" 日本語 "))
        assertEquals("", VaultTextFold.fold("   "))
    }
}
