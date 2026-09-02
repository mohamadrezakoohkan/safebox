package com.calcplus.calculator.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val sortIndex: Int,
    // No coverPhotoId: the album cover is derived (first photo by sortIndex).
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("albumId")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val albumId: String,       // required, non-null: every photo belongs to one album
    val fileName: String,      // <uuid>.<real extension>
    val thumbFileName: String, // <uuid>.jpg
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteCount: Long,
    val importedAt: Long,
    val sortIndex: Int,        // import order; grid ordering key
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val body: String,    // raw markdown — the single source of truth
    val title: String,   // DERIVED, denormalized (NoteDerivation)
    val snippet: String, // DERIVED, denormalized (NoteDerivation)
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorIndex: Int,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("tagId")],
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagId: String,
)

@Serializable
data class LabeledValue(
    val label: String,
    val value: String,
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val firstName: String?,
    val lastName: String?,
    val organization: String?,
    val phones: List<LabeledValue>, // JSON column via converter
    val emails: List<LabeledValue>, // JSON column via converter
    val address: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // Invariant (enforced in the edit ViewModel/repository): at least one of
    // firstName / lastName / organization is non-blank.
)
