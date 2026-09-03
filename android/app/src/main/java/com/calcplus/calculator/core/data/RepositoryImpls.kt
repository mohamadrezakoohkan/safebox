package com.calcplus.calculator.core.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.database.dao.NoteWithTags
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.database.entity.ContactEntity
import com.calcplus.calculator.core.database.entity.NoteEntity
import com.calcplus.calculator.core.database.entity.PhotoEntity
import com.calcplus.calculator.core.database.entity.TagEntity
import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.model.MediaType
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.domain.model.Photo
import com.calcplus.calculator.core.domain.model.SearchHaystacks
import com.calcplus.calculator.core.domain.model.SearchNormalizer
import com.calcplus.calculator.core.domain.model.Tag
import com.calcplus.calculator.core.domain.model.TrashPolicy
import com.calcplus.calculator.core.domain.model.VaultSorting
import com.calcplus.calculator.core.domain.repository.AlbumRepository
import com.calcplus.calculator.core.domain.repository.ContactRepository
import com.calcplus.calculator.core.domain.repository.ImportProgress
import com.calcplus.calculator.core.domain.repository.NoteRepository
import com.calcplus.calculator.core.domain.repository.PasscodeRepository
import com.calcplus.calculator.core.domain.repository.PhotoRepository
import com.calcplus.calculator.core.markdown.NoteDerivation
import com.calcplus.calculator.feature.calculator.CalcKey
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Deletion model (iteration-2-decisions §3). `delete…` stamps `deletedAt` and
// nothing else — no byte on disk changes. `purge…` is the only hard delete, and
// it always removes ROWS FIRST and files second: a crash between the two leaves
// orphan files, which the startup sweep collects, never a row whose files are
// already gone (which would render as a broken cell forever).

internal fun AlbumEntity.toDomain(photoCount: Int, coverThumb: String?) =
    Album(id, name, createdAt, sortIndex, photoCount, coverThumb, deletedAt)

internal fun PhotoEntity.toDomain() = Photo(
    id = id,
    albumId = albumId,
    fileName = fileName,
    thumbFileName = thumbFileName,
    mimeType = mimeType,
    width = width,
    height = height,
    byteCount = byteCount,
    importedAt = importedAt,
    sortIndex = sortIndex,
    deletedAt = deletedAt,
    mediaType = mediaType,
    durationMs = durationMs,
)

internal fun TagEntity.toDomain() = Tag(id, name, colorIndex)

internal fun NoteWithTags.toDomain() = Note(
    id = note.id,
    body = note.body,
    title = note.title,
    snippet = note.snippet,
    createdAt = note.createdAt,
    updatedAt = note.updatedAt,
    tags = tags.map { it.toDomain() }.sortedBy { it.name },
    deletedAt = note.deletedAt,
)

internal fun ContactEntity.toDomain() = Contact(
    id = id,
    firstName = firstName,
    lastName = lastName,
    organization = organization,
    phones = phones,
    emails = emails,
    address = address,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun Contact.toEntity() = ContactEntity(
    id = id,
    firstName = firstName,
    lastName = lastName,
    organization = organization,
    phones = phones,
    emails = emails,
    address = address,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

class AlbumRepositoryImpl(
    private val database: SafeBoxDatabase,
    private val fileStore: PhotoFileStore,
    private val now: () -> Long = System::currentTimeMillis,
) : AlbumRepository {
    /**
     * `photoCount` here is the LIVE count — `observeAlbumsWithCounts` excludes
     * trashed photos — so the `photo_count` mode never ranks an album by items
     * sitting in "Recently deleted".
     */
    override fun observeAlbums(sort: AlbumSort): Flow<List<Album>> =
        database.albumDao().observeAlbumsWithCounts().map { rows ->
            VaultSorting.sortAlbums(
                rows.map { it.album.toDomain(it.photoCount, it.coverThumbFileName) },
                sort,
            )
        }

    override suspend fun createAlbum(name: String) {
        val dao = database.albumDao()
        dao.insert(
            AlbumEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = now(),
                sortIndex = dao.nextSortIndex(),
            )
        )
    }

    override suspend fun renameAlbum(id: String, name: String) {
        database.albumDao().rename(id, name)
    }

    /**
     * One stamp for the album and every one of its LIVE photos, so restore can
     * tell "went to the trash with the album" from "was trashed earlier".
     */
    override suspend fun deleteAlbum(id: String) {
        val album = database.albumDao().album(id) ?: return
        if (album.deletedAt != null) return // already in the trash; keep its stamp
        val stamp = now()
        database.albumDao().softDelete(id, stamp)
        database.photoDao().softDeleteLiveInAlbum(id, stamp)
    }

    override suspend fun restore(ids: List<String>) {
        for (id in ids) {
            val album = database.albumDao().album(id) ?: continue
            val stamp = album.deletedAt ?: continue
            database.albumDao().restore(listOf(id))
            // Only the photos that carry the album's stamp — anything trashed
            // individually before the album stays in the trash.
            database.photoDao().restoreWithStamp(id, stamp)
        }
    }

    override suspend fun purge(ids: List<String>) = withContext(Dispatchers.IO) {
        for (id in ids) {
            // EVERY photo of the album, whatever its stamp: the FK cascade is
            // about to remove all of their rows.
            val photos = database.photoDao().allPhotosInAlbum(id)
            database.albumDao().purge(listOf(id))
            photos.forEach { fileStore.delete(it.fileName, it.thumbFileName) }
        }
    }

    override suspend fun purgeExpired(now: Long) {
        purge(database.albumDao().expiredIds(TrashPolicy.expiryCutoff(now)))
    }
}

class PhotoRepositoryImpl(
    private val database: SafeBoxDatabase,
    private val fileStore: PhotoFileStore,
    private val contentResolver: ContentResolver,
    private val applicationScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : PhotoRepository {
    private val _importProgress = MutableStateFlow(ImportProgress(0, 0))
    override val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    // No replay: the notice belongs to the import that just ran. extraBufferCapacity
    // keeps the emit non-suspending on the import coroutine when nobody listens
    // (the vault may have locked while the copy was still going).
    private val _videoImportFailures = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 4)

    /**
     * Narrowed to `SharedFlow` (the interface asks only for a `Flow`) so a test
     * can wait on `onSubscription` before triggering the import — with no replay
     * there is otherwise no way to subscribe without racing the emission.
     */
    override val videoImportFailures: SharedFlow<Int> = _videoImportFailures.asSharedFlow()

    override fun observePhotos(albumId: String): Flow<List<Photo>> =
        database.photoDao().observePhotos(albumId).map { list -> list.map { it.toDomain() } }

    override fun import(albumId: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        // Lock-surviving: applicationScope, keyed by albumId — completes even
        // if the vault locked during the picker round-trip.
        applicationScope.launch(Dispatchers.IO) {
            // Item-count based, one item per file whatever its size (decisions §9).
            _importProgress.value = ImportProgress(0, uris.size)
            var completed = 0
            var failedVideos = 0
            for (uri in uris) {
                val mimeType = contentResolver.getType(uri)
                val stored = fileStore.store(
                    openStream = { contentResolver.openInputStream(uri) },
                    mimeType = mimeType,
                )
                if (stored == null) {
                    if (looksLikeVideo(uri, mimeType)) failedVideos += 1
                } else {
                    try {
                        // An import finishing after its album was trashed joins
                        // the album in the trash instead of becoming a live
                        // photo of an invisible album.
                        val albumStamp = database.albumDao().album(albumId)?.deletedAt
                        database.photoDao().insert(
                            PhotoEntity(
                                id = stored.id,
                                albumId = albumId,
                                fileName = stored.fileName,
                                thumbFileName = stored.thumbFileName,
                                mimeType = stored.mimeType,
                                width = stored.width,
                                height = stored.height,
                                byteCount = stored.byteCount,
                                importedAt = now(),
                                sortIndex = database.photoDao().nextSortIndex(albumId),
                                deletedAt = albumStamp,
                                mediaType = stored.mediaType,
                                durationMs = stored.durationMs,
                            )
                        )
                    } catch (_: Exception) {
                        // No orphan row, no orphan file: atomic per photo.
                        fileStore.delete(stored.fileName, stored.thumbFileName)
                        if (stored.mediaType == MediaType.VIDEO) failedVideos += 1
                    }
                }
                completed += 1
                _importProgress.value = ImportProgress(completed, uris.size)
            }
            _importProgress.value = ImportProgress(0, 0)
            if (failedVideos > 0) _videoImportFailures.emit(failedVideos)
        }
    }

    /**
     * Classifies a picker item that FAILED to import, so the
     * `video_import_failed` notice (decisions §9) is raised for every dropped
     * video and for no dropped photo.
     *
     * `getType` is authoritative when it answers `video/…`, but a picker or a
     * document provider may hand a clip over with a missing or generic MIME
     * type; such an item takes the store's image path, fails the bitmap decode
     * and would otherwise vanish silently. The item's name — its display name
     * when the provider offers one, else the URI's last path segment — is the
     * fallback. A declared image is never re-classified.
     */
    private fun looksLikeVideo(uri: Uri, mimeType: String?): Boolean =
        PhotoFileStore.looksLikeVideo(mimeType, displayName(uri) ?: uri.lastPathSegment)

    /** Best-effort `OpenableColumns.DISPLAY_NAME`; null whenever the provider will not say. */
    private fun displayName(uri: Uri): String? = try {
        contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    /** Soft delete of the whole batch under ONE stamp. Files are untouched. */
    override suspend fun deletePhotos(ids: List<String>) {
        if (ids.isEmpty()) return
        database.photoDao().softDelete(ids, now())
    }

    override suspend fun movePhotos(ids: List<String>, toAlbumId: String) {
        val dao = database.photoDao()
        for (id in ids) {
            dao.move(id, toAlbumId, dao.nextSortIndex(toAlbumId))
        }
    }

    override suspend fun restore(ids: List<String>) {
        if (ids.isEmpty()) return
        val photos = database.photoDao().photosByIds(ids)
        database.photoDao().restore(ids)
        // A restored photo must be reachable: if its album is still trashed,
        // the ALBUM ROW comes back too — without its other trashed photos.
        val trashedAlbumIds = photos.map { it.albumId }.distinct()
            .filter { database.albumDao().album(it)?.deletedAt != null }
        if (trashedAlbumIds.isNotEmpty()) database.albumDao().restore(trashedAlbumIds)
    }

    override suspend fun purge(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val photos = database.photoDao().photosByIds(ids)
        // Rows first, then bytes: a crash in between leaves orphan files for
        // the sweep, never a row pointing at a file that is already gone.
        database.photoDao().purge(photos.map { it.id })
        photos.forEach { fileStore.delete(it.fileName, it.thumbFileName) }
    }

    override suspend fun purgeExpired(now: Long) {
        val expired = database.photoDao().expired(TrashPolicy.expiryCutoff(now))
        purge(expired.map { it.id })
    }

    override suspend fun sweepOrphans() = withContext(Dispatchers.IO) {
        // UNFILTERED on purpose: a trashed photo keeps its files until purge,
        // so filtering this by `deletedAt IS NULL` would destroy the trash.
        val all = database.photoDao().allPhotos()
        fileStore.sweepOrphans(
            knownFileNames = all.map { it.fileName }.toSet(),
            knownThumbFileNames = all.map { it.thumbFileName }.toSet(),
        )
    }
}

class NoteRepositoryImpl(
    private val database: SafeBoxDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : NoteRepository {
    /**
     * The query is matched in **Kotlin**, not in SQLite (decisions §7).
     *
     * The old `title LIKE '%…%' OR body LIKE '%…%'` was ASCII-case-only,
     * diacritic-sensitive ("Zoë" never matched "zoe") and passed `%` and `_`
     * straight through as wildcards. `SearchNormalizer` — the same fold global
     * search uses — fixes all three, and is the reason a per-tab result and a
     * global result can no longer disagree. An empty query means "no filter".
     */
    override fun observeNotes(query: String, tagId: String?, sort: NoteSort): Flow<List<Note>> {
        val dao = database.noteDao()
        val flow = if (tagId == null) dao.observeLiveNotes() else dao.observeLiveNotesWithTag(tagId)
        val folded = SearchNormalizer.foldedQuery(query)
        return flow.map { list ->
            val notes = list.map { it.toDomain() }
            val matched = if (folded.isEmpty()) {
                notes
            } else {
                notes.filter {
                    SearchNormalizer.foldedContainsAny(SearchHaystacks.noteInList(it), folded)
                }
            }
            VaultSorting.sortNotes(matched, sort)
        }
    }

    override fun observeNote(id: String): Flow<Note?> =
        database.noteDao().observeNoteWithTags(id).map { it?.toDomain() }

    override fun observeTags(): Flow<List<Tag>> =
        database.tagDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun createNote(): String {
        val id = UUID.randomUUID().toString()
        val timestamp = now()
        database.noteDao().upsert(
            NoteEntity(
                id = id,
                body = "",
                title = "",
                snippet = "",
                createdAt = timestamp,
                updatedAt = timestamp,
            )
        )
        return id
    }

    override suspend fun saveBody(id: String, body: String) {
        val dao = database.noteDao()
        val existing = dao.note(id) ?: return
        if (existing.body == body) return // updatedAt bumps only on real change
        val derived = NoteDerivation.derive(body)
        dao.upsert(
            existing.copy(
                body = body,
                title = derived.title,
                snippet = derived.snippet,
                updatedAt = now(),
            )
        )
    }

    override suspend fun delete(id: String) = delete(listOf(id))

    /** ONE call, ONE stamp, however many ids (P6 bulk delete rides on this). */
    override suspend fun delete(ids: List<String>) {
        if (ids.isEmpty()) return
        database.noteDao().softDelete(ids, now())
    }

    override suspend fun restore(ids: List<String>) {
        if (ids.isEmpty()) return
        database.noteDao().restore(ids)
    }

    /** Notes own no files, so the purge is the row (and its tag cross-refs). */
    override suspend fun purge(ids: List<String>) {
        if (ids.isEmpty()) return
        database.noteDao().purge(ids)
    }

    override suspend fun purgeExpired(now: Long) {
        purge(database.noteDao().expiredIds(TrashPolicy.expiryCutoff(now)))
    }

    override suspend fun getOrCreateTag(name: String): Tag {
        val trimmed = name.trim()
        val dao = database.tagDao()
        dao.byName(trimmed)?.let { return it.toDomain() }
        val tag = TagEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            colorIndex = dao.count() % 6, // round-robin from the shared palette
        )
        dao.insert(tag)
        // INSERT IGNORE may have lost a race with an equal name; re-read.
        return (dao.byName(trimmed) ?: tag).toDomain()
    }

    override suspend fun setTags(noteId: String, tagIds: List<String>) {
        database.noteDao().setTags(noteId, tagIds)
    }
}

class ContactRepositoryImpl(
    private val database: SafeBoxDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : ContactRepository {
    /**
     * As with notes, the query is matched in **Kotlin** through
     * `SearchNormalizer` (decisions §7) rather than by SQLite `LIKE`: case- and
     * diacritic-insensitive, wildcard-safe, and matching phone/email VALUES
     * instead of the stored JSON blob. An empty query means "no filter".
     */
    override fun observeContacts(query: String): Flow<List<Contact>> {
        val folded = SearchNormalizer.foldedQuery(query)
        return database.contactDao().observeLiveContacts().map { list ->
            list.map { it.toDomain() }
                .filter {
                    folded.isEmpty() ||
                        SearchNormalizer.foldedContainsAny(SearchHaystacks.contact(it), folded)
                }
                .sortedWith(
                    compareBy({ it.sortKey.isEmpty() }, { it.sortKey }, { it.displayName })
                )
        }
    }

    override fun observeContact(id: String): Flow<Contact?> =
        database.contactDao().observeContact(id).map { it?.toDomain() }

    override suspend fun upsert(contact: Contact) {
        database.contactDao().upsert(contact.toEntity())
    }

    override suspend fun delete(id: String) = delete(listOf(id))

    /** ONE call, ONE stamp, however many ids (P6 bulk delete rides on this). */
    override suspend fun delete(ids: List<String>) {
        if (ids.isEmpty()) return
        database.contactDao().softDelete(ids, now())
    }

    override suspend fun restore(ids: List<String>) {
        if (ids.isEmpty()) return
        database.contactDao().restore(ids)
    }

    override suspend fun purge(ids: List<String>) {
        if (ids.isEmpty()) return
        database.contactDao().purge(ids)
    }

    override suspend fun purgeExpired(now: Long) {
        purge(database.contactDao().expiredIds(TrashPolicy.expiryCutoff(now)))
    }
}

class PasscodeRepositoryImpl(
    private val store: PasscodeStore,
) : PasscodeRepository {
    override suspend fun set(sequence: List<CalcKey>) = store.set(sequence)
    override suspend fun matches(sequence: List<CalcKey>): Boolean = store.matches(sequence)
}
