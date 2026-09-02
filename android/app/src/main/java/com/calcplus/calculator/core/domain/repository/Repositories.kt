package com.calcplus.calculator.core.domain.repository

import android.net.Uri
import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.Photo
import com.calcplus.calculator.core.domain.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AlbumRepository {
    fun observeAlbums(): Flow<List<Album>>
    suspend fun createAlbum(name: String)
    suspend fun renameAlbum(id: String, name: String)
    /** Deletes rows AND files (enumerates photos before the cascade). */
    suspend fun deleteAlbum(id: String)
}

data class ImportProgress(val completed: Int, val total: Int) {
    val isActive: Boolean get() = total > 0 && completed < total
}

interface PhotoRepository {
    fun observePhotos(albumId: String): Flow<List<Photo>>
    val importProgress: StateFlow<ImportProgress>
    /**
     * Lock-surviving import: runs in applicationScope keyed by albumId; the
     * copy-into-vault must not depend on vault UI being composed.
     */
    fun import(albumId: String, uris: List<Uri>)
    suspend fun deletePhotos(ids: List<String>)
    suspend fun movePhotos(ids: List<String>, toAlbumId: String)
    /** Startup orphan sweep (backstop). */
    suspend fun sweepOrphans()
}

interface NoteRepository {
    fun observeNotes(query: String, tagId: String?): Flow<List<Note>>
    fun observeNote(id: String): Flow<Note?>
    fun observeTags(): Flow<List<Tag>>
    suspend fun createNote(): String
    /** Recomputes derived title/snippet; bumps updatedAt only on real change. */
    suspend fun saveBody(id: String, body: String)
    suspend fun delete(id: String)
    suspend fun getOrCreateTag(name: String): Tag
    suspend fun setTags(noteId: String, tagIds: List<String>)
}

interface ContactRepository {
    fun observeContacts(query: String): Flow<List<Contact>>
    fun observeContact(id: String): Flow<Contact?>
    suspend fun upsert(contact: Contact)
    suspend fun delete(id: String)
}

interface PasscodeRepository {
    suspend fun set(sequence: List<com.calcplus.calculator.feature.calculator.CalcKey>)
    suspend fun matches(sequence: List<com.calcplus.calculator.feature.calculator.CalcKey>): Boolean
}
