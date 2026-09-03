package com.calcplus.calculator.core.data

import com.calcplus.calculator.core.database.SafeBoxDatabase
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.database.entity.PhotoEntity
import com.calcplus.calculator.core.domain.repository.AlbumRepository
import com.calcplus.calculator.core.domain.repository.ContactRepository
import com.calcplus.calculator.core.domain.repository.NoteRepository
import com.calcplus.calculator.core.domain.repository.PhotoRepository
import com.calcplus.calculator.core.domain.repository.TrashContents
import com.calcplus.calculator.core.domain.repository.TrashItemId
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import com.calcplus.calculator.core.domain.repository.TrashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * "Recently deleted" (decisions §3). It owns no rows and no files: it reads the
 * trashed rows through the DAOs and routes every restore / purge back to the
 * repository that owns that entity, so the file-deletion and stamp rules live
 * in exactly one place. Mirrors iOS `SwiftDataTrashRepository`.
 */
class TrashRepositoryImpl(
    private val database: SafeBoxDatabase,
    private val albumRepository: AlbumRepository,
    private val photoRepository: PhotoRepository,
    private val noteRepository: NoteRepository,
    private val contactRepository: ContactRepository,
) : TrashRepository {

    /**
     * Trashed albums carry the count of the photos that share the album's OWN
     * `deletedAt` stamp — exactly the set Restore brings back (decisions §3),
     * so the row can never promise more than it returns. A photo trashed
     * individually *before* its album keeps its earlier stamp, stays trashed
     * through the album's restore, and is therefore not counted here (an
     * album purge still takes it). iOS `TrashViewModel.trashedPhotoCount(in:)`
     * applies the identical rule.
     *
     * The cover is the first trashed photo by `sortIndex` whatever its stamp
     * (iOS `coverPhoto(for:)`): a row with a leftover photo and nothing else
     * still shows a picture rather than a blank tile.
     *
     * The photos section lists only photos whose album is still live; a photo
     * trashed together with its album is represented by the album row.
     */
    override fun observeTrash(): Flow<TrashContents> = combine(
        database.albumDao().observeAllAlbums(),
        database.photoDao().observeTrashedPhotos(),
        database.noteDao().observeTrashedNotes(),
        database.contactDao().observeTrashedContacts(),
    ) { albums, trashedPhotos, notes, contacts ->
        val trashedByAlbum: Map<String, List<PhotoEntity>> = trashedPhotos.groupBy { it.albumId }
        val liveAlbumIds = albums.filter { it.deletedAt == null }.map { it.id }.toSet()
        TrashContents(
            albums = albums
                .filter { it.deletedAt != null }
                .sortedWith(
                    compareByDescending<AlbumEntity> { it.deletedAt ?: 0L }.thenBy { it.sortIndex }
                )
                .map { album ->
                    val photos = trashedByAlbum[album.id].orEmpty()
                        .sortedWith(compareBy({ it.sortIndex }, { it.importedAt }))
                    // Only the album's own stamp counts — see the KDoc above.
                    val restorable = photos.count { it.deletedAt == album.deletedAt }
                    album.toDomain(restorable, photos.firstOrNull()?.thumbFileName)
                },
            photos = trashedPhotos
                .filter { it.albumId in liveAlbumIds }
                .map { it.toDomain() },
            notes = notes.map { it.toDomain() },
            contacts = contacts.map { it.toDomain() },
            albumNames = albums.associate { it.id to it.name },
        )
    }

    override suspend fun restore(items: List<TrashItemId>) {
        // Albums first: restoring an album already clears the stamp of every
        // photo that went into the trash with it, so a photo selected as well
        // becomes a no-op instead of resurrecting a trashed album twice.
        albumRepository.restore(items.ids(TrashItemKind.ALBUM))
        photoRepository.restore(items.ids(TrashItemKind.PHOTO))
        noteRepository.restore(items.ids(TrashItemKind.NOTE))
        contactRepository.restore(items.ids(TrashItemKind.CONTACT))
    }

    override suspend fun purge(items: List<TrashItemId>) {
        // Albums first for the same reason: an album purge takes all of its
        // photo rows and files with it, so the remaining photo ids are filtered
        // against what actually survived.
        albumRepository.purge(items.ids(TrashItemKind.ALBUM))
        val remainingPhotoIds = items.ids(TrashItemKind.PHOTO).let { ids ->
            if (ids.isEmpty()) ids else database.photoDao().photosByIds(ids).map { it.id }
        }
        photoRepository.purge(remainingPhotoIds)
        noteRepository.purge(items.ids(TrashItemKind.NOTE))
        contactRepository.purge(items.ids(TrashItemKind.CONTACT))
    }

    override suspend fun emptyAll() {
        albumRepository.purge(database.albumDao().trashedAlbums().map { it.id })
        // Re-read: the album purge already removed the photos that were trashed
        // under those albums.
        photoRepository.purge(database.photoDao().trashedPhotos().map { it.id })
        noteRepository.purge(database.noteDao().trashedNotes().map { it.note.id })
        contactRepository.purge(database.contactDao().trashedContacts().map { it.id })
    }

    override suspend fun purgeExpired(now: Long) {
        albumRepository.purgeExpired(now)
        photoRepository.purgeExpired(now)
        noteRepository.purgeExpired(now)
        contactRepository.purgeExpired(now)
    }
}

private fun List<TrashItemId>.ids(kind: TrashItemKind): List<String> =
    filter { it.kind == kind }.map { it.id }
