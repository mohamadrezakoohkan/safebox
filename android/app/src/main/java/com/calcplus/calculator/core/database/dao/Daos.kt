package com.calcplus.calculator.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
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

data class AlbumWithCount(
    @Embedded val album: AlbumEntity,
    val photoCount: Int,
    val coverThumbFileName: String?,
)

@Dao
interface AlbumDao {
    @Query(
        """
        SELECT a.*,
          (SELECT COUNT(*) FROM photos p WHERE p.albumId = a.id) AS photoCount,
          (SELECT p2.thumbFileName FROM photos p2 WHERE p2.albumId = a.id
             ORDER BY p2.sortIndex ASC, p2.importedAt ASC LIMIT 1) AS coverThumbFileName
        FROM albums a ORDER BY a.sortIndex ASC
        """
    )
    fun observeAlbumsWithCounts(): Flow<List<AlbumWithCount>>

    @Query("SELECT * FROM albums ORDER BY sortIndex ASC")
    suspend fun albums(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun album(id: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumEntity)

    @Query("UPDATE albums SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM albums")
    suspend fun nextSortIndex(): Int
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY sortIndex ASC, importedAt ASC")
    fun observePhotos(albumId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY sortIndex ASC, importedAt ASC")
    suspend fun photos(albumId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos")
    suspend fun allPhotos(): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("UPDATE photos SET albumId = :albumId, sortIndex = :sortIndex WHERE id = :id")
    suspend fun move(id: String, albumId: String, sortIndex: Int)

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
        WHERE title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun observeNotes(query: String): Flow<List<NoteWithTags>>

    @Transaction
    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN note_tags nt ON nt.noteId = n.id
        WHERE nt.tagId = :tagId
          AND (n.title LIKE '%' || :query || '%' OR n.body LIKE '%' || :query || '%')
        ORDER BY n.updatedAt DESC
        """
    )
    fun observeNotesWithTag(query: String, tagId: String): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNoteWithTags(id: String): Flow<NoteWithTags?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun note(id: String): NoteEntity?

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

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
        WHERE firstName LIKE '%' || :query || '%'
           OR lastName LIKE '%' || :query || '%'
           OR organization LIKE '%' || :query || '%'
           OR phones LIKE '%' || :query || '%'
           OR emails LIKE '%' || :query || '%'
        """
    )
    fun observeContacts(query: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observeContact(id: String): Flow<ContactEntity?>

    @Query("SELECT * FROM contacts")
    suspend fun all(): List<ContactEntity>

    @Upsert
    suspend fun upsert(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: String)
}
