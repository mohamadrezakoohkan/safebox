package com.calcplus.calculator.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.database.entity.ContactEntity
import com.calcplus.calculator.core.database.entity.NoteEntity
import com.calcplus.calculator.core.database.entity.NoteTagCrossRef
import com.calcplus.calculator.core.database.entity.PhotoEntity
import com.calcplus.calculator.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

// Soft delete (iteration-2-decisions §3): `delete` on every user table is an
// UPDATE that stamps `deletedAt`; every list / observe / search query filters
// `deletedAt IS NULL`; `purge…` is the only hard DELETE. Lookups by id and the
// explicitly named `all…` / `trashed…` queries are unfiltered on purpose (the
// trash screen, restore/purge, the orphan sweep and erase-everything need to
// see trashed rows).

data class AlbumWithCount(
    @Embedded val album: AlbumEntity,
    val photoCount: Int,
    val coverThumbFileName: String?,
)

@Dao
interface AlbumDao {
    /** Live albums with their LIVE photo count and cover — trashed photos count for nothing here. */
    @Query(
        """
        SELECT a.*,
          (SELECT COUNT(*) FROM photos p WHERE p.albumId = a.id AND p.deletedAt IS NULL) AS photoCount,
          (SELECT p2.thumbFileName FROM photos p2 WHERE p2.albumId = a.id AND p2.deletedAt IS NULL
             ORDER BY p2.sortIndex ASC, p2.importedAt ASC LIMIT 1) AS coverThumbFileName
        FROM albums a
        WHERE a.deletedAt IS NULL
        ORDER BY a.sortIndex ASC
        """
    )
    fun observeAlbumsWithCounts(): Flow<List<AlbumWithCount>>

    @Query("SELECT * FROM albums WHERE deletedAt IS NULL ORDER BY sortIndex ASC")
    suspend fun albums(): List<AlbumEntity>

    /** Every album row, live or trashed (erase-everything verification, trash bookkeeping). */
    @Query("SELECT * FROM albums ORDER BY sortIndex ASC")
    suspend fun allAlbums(): List<AlbumEntity>

    @Query("SELECT * FROM albums ORDER BY sortIndex ASC")
    fun observeAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, sortIndex ASC")
    suspend fun trashedAlbums(): List<AlbumEntity>

    /** Unfiltered lookup: restore/purge and the trash screen read trashed rows through it. */
    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun album(id: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumEntity)

    @Query("UPDATE albums SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    /** Soft delete. A no-op for an album that is already trashed (its stamp is kept). */
    @Query("UPDATE albums SET deletedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE albums SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<String>)

    /** Hard delete. The FK cascade removes the photo ROWS; files are the repository's job. */
    @Query("DELETE FROM albums WHERE id IN (:ids)")
    suspend fun purge(ids: List<String>)

    @Query("SELECT id FROM albums WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun expiredIds(cutoff: Long): List<String>

    /** Over ALL albums, trashed included, so a restored album never collides with a newer one. */
    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM albums")
    suspend fun nextSortIndex(): Int
}

@Dao
interface PhotoDao {
    @Query(
        "SELECT * FROM photos WHERE albumId = :albumId AND deletedAt IS NULL ORDER BY sortIndex ASC, importedAt ASC"
    )
    fun observePhotos(albumId: String): Flow<List<PhotoEntity>>

    @Query(
        "SELECT * FROM photos WHERE albumId = :albumId AND deletedAt IS NULL ORDER BY sortIndex ASC, importedAt ASC"
    )
    suspend fun photos(albumId: String): List<PhotoEntity>

    /**
     * EVERY photo row, trashed included. `sweepOrphans` builds its keep-set from
     * this — filtering it would delete the files of every trashed photo.
     */
    @Query("SELECT * FROM photos")
    suspend fun allPhotos(): List<PhotoEntity>

    /** Every photo of an album regardless of `deletedAt` (album purge enumerates all its files). */
    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY sortIndex ASC, importedAt ASC")
    suspend fun allPhotosInAlbum(albumId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun photosByIds(ids: List<String>): List<PhotoEntity>

    /** All trashed photos, including those trashed together with their album. */
    @Query("SELECT * FROM photos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, sortIndex ASC")
    suspend fun trashedPhotos(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, sortIndex ASC")
    fun observeTrashedPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun expired(cutoff: Long): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    /** Soft delete; already-trashed rows keep their earlier stamp. */
    @Query("UPDATE photos SET deletedAt = :now WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun softDelete(ids: List<String>, now: Long)

    /** Album soft delete: every LIVE photo of the album gets the album's stamp. */
    @Query("UPDATE photos SET deletedAt = :now WHERE albumId = :albumId AND deletedAt IS NULL")
    suspend fun softDeleteLiveInAlbum(albumId: String, now: Long)

    /** Restore in place: only `deletedAt` changes, `sortIndex` and `albumId` are untouched. */
    @Query("UPDATE photos SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<String>)

    /** Album restore: only the photos that went into the trash WITH the album (same stamp). */
    @Query("UPDATE photos SET deletedAt = NULL WHERE albumId = :albumId AND deletedAt = :stamp")
    suspend fun restoreWithStamp(albumId: String, stamp: Long)

    /** Hard delete of the rows only; the repository deletes both files first. */
    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun purge(ids: List<String>)

    @Query("UPDATE photos SET albumId = :albumId, sortIndex = :sortIndex WHERE id = :id")
    suspend fun move(id: String, albumId: String, sortIndex: Int)

    /** Over ALL photos of the album, trashed included, so a restored photo keeps a unique index. */
    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM photos WHERE albumId = :albumId")
    suspend fun nextSortIndex(albumId: String): Int
}

data class NoteWithTags(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NoteTagCrossRef::class,
            parentColumn = "noteId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

@Dao
interface NoteDao {
    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL
          AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
        """
    )
    fun observeNotes(query: String): Flow<List<NoteWithTags>>

    @Transaction
    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN note_tags nt ON nt.noteId = n.id
        WHERE n.deletedAt IS NULL
          AND nt.tagId = :tagId
          AND (n.title LIKE '%' || :query || '%' OR n.body LIKE '%' || :query || '%')
        ORDER BY n.updatedAt DESC
        """
    )
    fun observeNotesWithTag(query: String, tagId: String): Flow<List<NoteWithTags>>

    /**
     * Every LIVE note, unfiltered by query — the notes list and global search
     * both read through this and match in Kotlin with `SearchNormalizer`
     * (decisions §7). The `LIKE` variants above stay for the DAO tests that pin
     * the SQL; nothing in the app filters by query in SQLite any more.
     */
    @Transaction
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeLiveNotes(): Flow<List<NoteWithTags>>

    /** Every LIVE note carrying [tagId], unfiltered by query. */
    @Transaction
    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN note_tags nt ON nt.noteId = n.id
        WHERE n.deletedAt IS NULL AND nt.tagId = :tagId
        ORDER BY n.updatedAt DESC
        """
    )
    fun observeLiveNotesWithTag(tagId: String): Flow<List<NoteWithTags>>

    /** Unfiltered lookup by id (the editor navigates away on delete; restore reads through it). */
    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNoteWithTags(id: String): Flow<NoteWithTags?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun note(id: String): NoteEntity?

    /** Every note row, live or trashed (erase-everything verification). */
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun allNotes(): List<NoteEntity>

    @Transaction
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, updatedAt DESC")
    suspend fun trashedNotes(): List<NoteWithTags>

    @Transaction
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, updatedAt DESC")
    fun observeTrashedNotes(): Flow<List<NoteWithTags>>

    @Query("SELECT id FROM notes WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun expiredIds(cutoff: Long): List<String>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    /** Soft delete (one stamp for the whole batch — P6 bulk delete). Cross-refs survive. */
    @Query("UPDATE notes SET deletedAt = :now WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun softDelete(ids: List<String>, now: Long)

    @Query("UPDATE notes SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<String>)

    /** Hard delete; the FK cascade removes the note's cross-refs, tags survive. */
    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun purge(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearCrossRefs(noteId: String)

    @Transaction
    suspend fun setTags(noteId: String, tagIds: List<String>) {
        clearCrossRefs(noteId)
        insertCrossRefs(tagIds.map { NoteTagCrossRef(noteId, it) })
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun all(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int
}

@Dao
interface ContactDao {
    @Query(
        """
        SELECT * FROM contacts
        WHERE deletedAt IS NULL
          AND (firstName LIKE '%' || :query || '%'
            OR lastName LIKE '%' || :query || '%'
            OR organization LIKE '%' || :query || '%'
            OR phones LIKE '%' || :query || '%'
            OR emails LIKE '%' || :query || '%')
        """
    )
    fun observeContacts(query: String): Flow<List<ContactEntity>>

    /**
     * Every LIVE contact, unfiltered by query. The contacts list and global
     * search read through this and match in Kotlin with `SearchNormalizer`
     * (decisions §7) — which also means phones and emails are matched on their
     * VALUES, not on the stored JSON the `LIKE` variant above scanned.
     */
    @Query("SELECT * FROM contacts WHERE deletedAt IS NULL")
    fun observeLiveContacts(): Flow<List<ContactEntity>>

    /** Unfiltered lookup by id (the detail screen navigates away on delete). */
    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observeContact(id: String): Flow<ContactEntity?>

    /** Every contact row, live or trashed (erase-everything verification). */
    @Query("SELECT * FROM contacts")
    suspend fun all(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun trashedContacts(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashedContacts(): Flow<List<ContactEntity>>

    @Query("SELECT id FROM contacts WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun expiredIds(cutoff: Long): List<String>

    @Upsert
    suspend fun upsert(contact: ContactEntity)

    /** Soft delete (one stamp for the whole batch — P6 bulk delete). */
    @Query("UPDATE contacts SET deletedAt = :now WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun softDelete(ids: List<String>, now: Long)

    @Query("UPDATE contacts SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<String>)

    /** Hard delete. */
    @Query("DELETE FROM contacts WHERE id IN (:ids)")
    suspend fun purge(ids: List<String>)
}
