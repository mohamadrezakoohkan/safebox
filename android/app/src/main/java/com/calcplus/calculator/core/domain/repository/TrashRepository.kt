package com.calcplus.calculator.core.domain.repository

import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.Photo
import kotlinx.coroutines.flow.Flow

/** The four kinds of row that can sit in "Recently deleted". Mirrors iOS `TrashItemKind`. */
enum class TrashItemKind { ALBUM, PHOTO, NOTE, CONTACT }

/**
 * Type-tagged id of one trashed row. Undo actions and the trash screen carry
 * these (plain ids) rather than model objects, so nothing holds on to a row
 * that a purge may have removed. Mirrors iOS `TrashItemID`.
 */
data class TrashItemId(val kind: TrashItemKind, val id: String)

/**
 * Everything currently in the trash, grouped by type, most recently deleted
 * first. [albums] carry the count of the photos their restore will bring back —
 * the ones stamped with the album's own `deletedAt`, not every trashed photo
 * they contain — and a cover derived from their trashed photos; [photos] holds
 * only photos whose album is live — a photo trashed together with its album is
 * represented by the album row (decisions §3). Mirrors iOS `TrashContents`.
 */
data class TrashContents(
    val albums: List<Album> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val notes: List<Note> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    /**
     * Album id → album name for every album, live or trashed. A photo row in
     * the trash is labelled with the album it will return to, and that album is
     * live by construction (a photo under a trashed album is listed as part of
     * the album row instead), so its name is not in [albums].
     */
    val albumNames: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = albums.isEmpty() && photos.isEmpty() && notes.isEmpty() && contacts.isEmpty()

    val itemCount: Int
        get() = albums.size + photos.size + notes.size + contacts.size
}

/**
 * "Recently deleted" (decisions §3): the one place that lists trash across all
 * four entity types and routes restore / purge back to the entity
 * repositories, which own the rows and the files.
 */
interface TrashRepository {
    /** Live view of the trash; re-emits on any change to the underlying tables. */
    fun observeTrash(): Flow<TrashContents>
    suspend fun restore(items: List<TrashItemId>)
    /** Hard delete: rows and, for photos/albums, both files. */
    suspend fun purge(items: List<TrashItemId>)
    /** Hard-deletes everything in the trash (albums first, then remaining photos, notes, contacts). */
    suspend fun emptyAll()
    /** Hard-deletes every trashed item whose stamp is at least the retention period old at [now]. */
    suspend fun purgeExpired(now: Long)
}
