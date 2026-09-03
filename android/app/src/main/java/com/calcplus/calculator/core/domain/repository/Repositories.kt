package com.calcplus.calculator.core.domain.repository

import android.net.Uri
import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.domain.model.Photo
import com.calcplus.calculator.core.domain.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// Deletion model (iteration-2-decisions §3): `delete…` is a SOFT delete that
// stamps `deletedAt` and hides the item from every list; nothing on disk
// changes. `restore` clears the stamp in place. `purge` / `purgeExpired` are
// the hard deletes — rows AND (for photos) both files — and are reached only
// through "Recently deleted" (TrashRepository), the 30-day expiry, or erase
// everything (VaultNuker).

interface AlbumRepository {
    /**
     * Live albums only, with live photo counts and covers, ordered by [sort]
     * (decisions §4). The DAO's `sortIndex` order is the base order; the mode
     * is applied in the repository because Room cannot parameterize `ORDER BY`.
     */
    fun observeAlbums(sort: AlbumSort = AlbumSort.DEFAULT): Flow<List<Album>>
    suspend fun createAlbum(name: String)
    suspend fun renameAlbum(id: String, name: String)
    /**
     * Soft delete: stamps the album and all of its LIVE photos with one and the
     * same `deletedAt` instant. Photos trashed individually earlier keep their
     * own stamp. No file is touched.
     */
    suspend fun deleteAlbum(id: String)
    /**
     * Restore: clears the album's stamp and the stamp of exactly those photos
     * that carry the album's stamp (they went into the trash with it).
     */
    suspend fun restore(ids: List<String>)
    /** Hard delete: files of EVERY photo of the album (any stamp), then the photo rows, then the album. */
    suspend fun purge(ids: List<String>)
    /** Hard-deletes every trashed album whose stamp is at least [TrashPolicy] retention old at [now]. */
    suspend fun purgeExpired(now: Long)
}

data class ImportProgress(val completed: Int, val total: Int) {
    val isActive: Boolean get() = total > 0 && completed < total
}

interface PhotoRepository {
    /** Live photos of the album only. */
    fun observePhotos(albumId: String): Flow<List<Photo>>
    val importProgress: StateFlow<ImportProgress>
    /**
     * How many videos an import batch could not store (decisions §9). Emitted
     * once per batch, only when the count is non-zero, so the gallery can raise
     * the `video_import_failed` notice. A hot flow with no replay: the notice
     * belongs to the import that just happened, not to whoever subscribes next.
     */
    val videoImportFailures: Flow<Int>
    /**
     * Lock-surviving import: runs in applicationScope keyed by albumId; the
     * copy-into-vault must not depend on vault UI being composed.
     */
    fun import(albumId: String, uris: List<Uri>)
    /** Soft delete (one stamp for the batch). Files stay until purge. */
    suspend fun deletePhotos(ids: List<String>)
    suspend fun movePhotos(ids: List<String>, toAlbumId: String)
    /**
     * Restore in place (original `sortIndex`, original album). If a photo's
     * album is itself trashed, the ALBUM ROW is restored too — without its
     * other trashed photos — so the photo is reachable again.
     */
    suspend fun restore(ids: List<String>)
    /** Hard delete: full-size file + thumbnail, then the row. */
    suspend fun purge(ids: List<String>)
    /** Hard-deletes every trashed photo whose stamp is at least [TrashPolicy] retention old at [now]. */
    suspend fun purgeExpired(now: Long)
    /**
     * Startup orphan sweep (backstop). Enumerates EVERY photo row, trashed
     * included — trashed photos keep their files until purge.
     */
    suspend fun sweepOrphans()
}

interface NoteRepository {
    /** Live notes only, ordered by [sort] (decisions §4). */
    fun observeNotes(
        query: String,
        tagId: String?,
        sort: NoteSort = NoteSort.DEFAULT,
    ): Flow<List<Note>>
    fun observeNote(id: String): Flow<Note?>
    fun observeTags(): Flow<List<Tag>>
    suspend fun createNote(): String
    /** Recomputes derived title/snippet; bumps updatedAt only on real change. */
    suspend fun saveBody(id: String, body: String)
    /** Soft delete of one note. */
    suspend fun delete(id: String)
    /** Soft delete of N notes in ONE call with one shared stamp (P6 bulk delete). */
    suspend fun delete(ids: List<String>)
    suspend fun restore(ids: List<String>)
    suspend fun purge(ids: List<String>)
    suspend fun purgeExpired(now: Long)
    suspend fun getOrCreateTag(name: String): Tag
    suspend fun setTags(noteId: String, tagIds: List<String>)
}

interface ContactRepository {
    /** Live contacts only. */
    fun observeContacts(query: String): Flow<List<Contact>>
    fun observeContact(id: String): Flow<Contact?>
    suspend fun upsert(contact: Contact)
    /** Soft delete of one contact. */
    suspend fun delete(id: String)
    /** Soft delete of N contacts in ONE call with one shared stamp (P6 bulk delete). */
    suspend fun delete(ids: List<String>)
    suspend fun restore(ids: List<String>)
    suspend fun purge(ids: List<String>)
    suspend fun purgeExpired(now: Long)
}

interface PasscodeRepository {
    suspend fun set(sequence: List<com.calcplus.calculator.feature.calculator.CalcKey>)
    suspend fun matches(sequence: List<com.calcplus.calculator.feature.calculator.CalcKey>): Boolean
}
