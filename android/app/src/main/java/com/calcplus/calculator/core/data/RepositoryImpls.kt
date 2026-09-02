package com.calcplus.calculator.core.data

import android.content.ContentResolver
import android.net.Uri
import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.database.dao.NoteWithTags
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.database.entity.ContactEntity
import com.calcplus.calculator.core.database.entity.NoteEntity
import com.calcplus.calculator.core.database.entity.PhotoEntity
import com.calcplus.calculator.core.database.entity.TagEntity
import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.Photo
import com.calcplus.calculator.core.domain.model.Tag
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun AlbumEntity.toDomain(photoCount: Int, coverThumb: String?) =
    Album(id, name, createdAt, sortIndex, photoCount, coverThumb)

private fun PhotoEntity.toDomain() =
    Photo(id, albumId, fileName, thumbFileName, mimeType, width, height, byteCount, importedAt, sortIndex)

private fun TagEntity.toDomain() = Tag(id, name, colorIndex)

private fun NoteWithTags.toDomain() = Note(
    id = note.id,
    body = note.body,
    title = note.title,
    snippet = note.snippet,
    createdAt = note.createdAt,
    updatedAt = note.updatedAt,
    tags = tags.map { it.toDomain() }.sortedBy { it.name },
)

private fun ContactEntity.toDomain() =
    Contact(id, firstName, lastName, organization, phones, emails, address, notes, createdAt, updatedAt)

private fun Contact.toEntity() =
    ContactEntity(id, firstName, lastName, organization, phones, emails, address, notes, createdAt, updatedAt)

class AlbumRepositoryImpl(
    private val database: SafeBoxDatabase,
    private val fileStore: PhotoFileStore,
) : AlbumRepository {
    override fun observeAlbums(): Flow<List<Album>> =
        database.albumDao().observeAlbumsWithCounts().map { rows ->
            rows.map { it.album.toDomain(it.photoCount, it.coverThumbFileName) }
        }

    override suspend fun createAlbum(name: String) {
        val dao = database.albumDao()
        dao.insert(
            AlbumEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis(),
                sortIndex = dao.nextSortIndex(),
            )
        )
    }

    override suspend fun renameAlbum(id: String, name: String) {
        database.albumDao().rename(id, name)
    }

    override suspend fun deleteAlbum(id: String) = withContext(Dispatchers.IO) {
        // Enumerate and delete each photo's files FIRST — the cascade removes
        // rows without touching the file store.
        val photos = database.photoDao().photos(id)
        photos.forEach { fileStore.delete(it.fileName, it.thumbFileName) }
        database.albumDao().delete(id)
    }
}

class PhotoRepositoryImpl(
    private val database: SafeBoxDatabase,
    private val fileStore: PhotoFileStore,
    private val contentResolver: ContentResolver,
    private val applicationScope: CoroutineScope,
) : PhotoRepository {
    private val _importProgress = MutableStateFlow(ImportProgress(0, 0))
    override val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    override fun observePhotos(albumId: String): Flow<List<Photo>> =
        database.photoDao().observePhotos(albumId).map { list -> list.map { it.toDomain() } }

    override fun import(albumId: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        // Lock-surviving: applicationScope, keyed by albumId — completes even
        // if the vault locked during the picker round-trip.
        applicationScope.launch(Dispatchers.IO) {
            _importProgress.value = ImportProgress(0, uris.size)
            var completed = 0
            for (uri in uris) {
                val stored = fileStore.store(
                    openStream = { contentResolver.openInputStream(uri) },
                    mimeType = contentResolver.getType(uri),
                )
                if (stored != null) {
                    try {
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
                                importedAt = System.currentTimeMillis(),
                                sortIndex = database.photoDao().nextSortIndex(albumId),
                            )
                        )
                    } catch (_: Exception) {
                        // No orphan row, no orphan file: atomic per photo.
                        fileStore.delete(stored.fileName, stored.thumbFileName)
                    }
                }
                completed += 1
                _importProgress.value = ImportProgress(completed, uris.size)
            }
            _importProgress.value = ImportProgress(0, 0)
        }
    }

    override suspend fun deletePhotos(ids: List<String>) = withContext(Dispatchers.IO) {
        val all = database.photoDao().allPhotos().filter { it.id in ids.toSet() }
        all.forEach { fileStore.delete(it.fileName, it.thumbFileName) }
        database.photoDao().delete(ids)
    }

    override suspend fun movePhotos(ids: List<String>, toAlbumId: String) {
        val dao = database.photoDao()
        for (id in ids) {
            dao.move(id, toAlbumId, dao.nextSortIndex(toAlbumId))
        }
    }

    override suspend fun sweepOrphans() = withContext(Dispatchers.IO) {
        val all = database.photoDao().allPhotos()
        fileStore.sweepOrphans(
            knownFileNames = all.map { it.fileName }.toSet(),
            knownThumbFileNames = all.map { it.thumbFileName }.toSet(),
        )
    }
}

class NoteRepositoryImpl(
    private val database: SafeBoxDatabase,
) : NoteRepository {
    override fun observeNotes(query: String, tagId: String?): Flow<List<Note>> {
        val dao = database.noteDao()
        val flow = if (tagId == null) dao.observeNotes(query) else dao.observeNotesWithTag(query, tagId)
        return flow.map { list -> list.map { it.toDomain() } }
    }

    override fun observeNote(id: String): Flow<Note?> =
        database.noteDao().observeNoteWithTags(id).map { it?.toDomain() }

    override fun observeTags(): Flow<List<Tag>> =
        database.tagDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun createNote(): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.noteDao().upsert(
            NoteEntity(id = id, body = "", title = "", snippet = "", createdAt = now, updatedAt = now)
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
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun delete(id: String) {
        database.noteDao().delete(id)
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
) : ContactRepository {
    override fun observeContacts(query: String): Flow<List<Contact>> =
        database.contactDao().observeContacts(query).map { list ->
            list.map { it.toDomain() }.sortedWith(
                compareBy({ it.sortKey.isEmpty() }, { it.sortKey }, { it.displayName })
            )
        }

    override fun observeContact(id: String): Flow<Contact?> =
        database.contactDao().observeContact(id).map { it?.toDomain() }

    override suspend fun upsert(contact: Contact) {
        database.contactDao().upsert(contact.toEntity())
    }

    override suspend fun delete(id: String) {
        database.contactDao().deleteById(id)
    }
}

class PasscodeRepositoryImpl(
    private val store: PasscodeStore,
) : PasscodeRepository {
    override suspend fun set(sequence: List<CalcKey>) = store.set(sequence)
    override suspend fun matches(sequence: List<CalcKey>): Boolean = store.matches(sequence)
}
