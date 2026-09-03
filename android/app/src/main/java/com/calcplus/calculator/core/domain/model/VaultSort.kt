package com.calcplus.calculator.core.domain.model

import java.text.Normalizer

/**
 * The user-selectable sort modes (decisions §4). [raw] is the persisted value
 * and is shared verbatim with iOS — renaming one without the other silently
 * resets that platform's preference to the default, so don't.
 *
 * `entries` order is menu order on both platforms.
 */
enum class AlbumSort(val raw: String) {
    MANUAL("manual"),
    NAME("name"),
    DATE_CREATED("date_created"),
    PHOTO_COUNT("photo_count");

    companion object {
        val DEFAULT = MANUAL

        /** An unknown, absent or corrupt stored value falls back to [DEFAULT]. */
        fun fromRaw(raw: String?): AlbumSort = entries.firstOrNull { it.raw == raw } ?: DEFAULT
    }
}

enum class NoteSort(val raw: String) {
    DATE_MODIFIED("date_modified"),
    DATE_CREATED("date_created"),
    TITLE("title");

    companion object {
        val DEFAULT = DATE_MODIFIED

        /** An unknown, absent or corrupt stored value falls back to [DEFAULT]. */
        fun fromRaw(raw: String?): NoteSort = entries.firstOrNull { it.raw == raw } ?: DEFAULT
    }
}

/**
 * The one case- and diacritic-insensitive fold used for every human-name
 * comparison in the vault: Unicode NFD → strip combining marks → lowercase,
 * on the trimmed string.
 *
 * [Contact.sortKey] and the name/title sorts share it so "Ångström", "angstrom"
 * and " Ångström " never land in three different places. N1's search fold
 * (decisions §7) is the same transformation — build `SearchFold` on this rather
 * than writing a second copy.
 */
object VaultTextFold {
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    fun fold(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()
}

/**
 * Sorting for the album and note lists (decisions §4), applied in the
 * repository on the fetched list — Room cannot parameterize `ORDER BY`, and a
 * composable body is the wrong place to order anything.
 *
 * Every comparator is a TOTAL order: the last tie-break is always the id, so
 * the same data always renders in the same sequence. Fold keys are computed
 * once per row, never inside the comparator.
 */
object VaultSorting {
    /**
     * @param albums live albums; [Album.photoCount] is already the LIVE count
     *   (the DAO excludes trashed photos), which is what `photo_count` sorts on.
     */
    fun sortAlbums(albums: List<Album>, mode: AlbumSort): List<Album> {
        val folded = albums.associate { it.id to VaultTextFold.fold(it.name) }
        fun key(album: Album) = folded.getValue(album.id)
        return when (mode) {
            AlbumSort.MANUAL ->
                albums.sortedWith(compareBy<Album> { it.sortIndex }.thenBy { it.id })
            AlbumSort.NAME ->
                albums.sortedWith(compareBy<Album> { key(it) }.thenBy { it.createdAt }.thenBy { it.id })
            AlbumSort.DATE_CREATED ->
                albums.sortedWith(compareByDescending<Album> { it.createdAt }.thenBy { it.id })
            AlbumSort.PHOTO_COUNT ->
                albums.sortedWith(
                    compareByDescending<Album> { it.photoCount }.thenBy { key(it) }.thenBy { it.id }
                )
        }
    }

    fun sortNotes(notes: List<Note>, mode: NoteSort): List<Note> {
        val folded = notes.associate { it.id to VaultTextFold.fold(it.title) }
        fun key(note: Note) = folded.getValue(note.id)
        return when (mode) {
            NoteSort.DATE_MODIFIED ->
                notes.sortedWith(compareByDescending<Note> { it.updatedAt }.thenBy { it.id })
            NoteSort.DATE_CREATED ->
                notes.sortedWith(compareByDescending<Note> { it.createdAt }.thenBy { it.id })
            NoteSort.TITLE ->
                // A note whose derived title is empty has nothing to sort by, so
                // it goes LAST rather than to the top of the alphabet.
                notes.sortedWith(
                    compareBy<Note> { key(it).isEmpty() }
                        .thenBy { key(it) }
                        .thenByDescending { it.updatedAt }
                        .thenBy { it.id }
                )
        }
    }
}
