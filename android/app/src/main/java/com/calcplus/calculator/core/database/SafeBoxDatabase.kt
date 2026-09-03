package com.calcplus.calculator.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.calcplus.calculator.core.database.dao.AlbumDao
import com.calcplus.calculator.core.database.dao.ContactDao
import com.calcplus.calculator.core.database.dao.NoteDao
import com.calcplus.calculator.core.database.dao.PhotoDao
import com.calcplus.calculator.core.database.dao.TagDao
import com.calcplus.calculator.core.database.entity.AlbumEntity
import com.calcplus.calculator.core.database.entity.ContactEntity
import com.calcplus.calculator.core.database.entity.NoteEntity
import com.calcplus.calculator.core.database.entity.NoteTagCrossRef
import com.calcplus.calculator.core.database.entity.PhotoEntity
import com.calcplus.calculator.core.database.entity.TagEntity

/**
 * Version history (exported under `app/schemas/…/SafeBoxDatabase/`):
 *  - 1: iteration 1.
 *  - 2: iteration 2 — `deletedAt` on albums/photos/notes/contacts, and
 *    `mediaType` / `durationMs` on photos ([MIGRATION_1_2]).
 */
@Database(
    entities = [
        AlbumEntity::class,
        PhotoEntity::class,
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        ContactEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SafeBoxDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun photoDao(): PhotoDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun contactDao(): ContactDao

    companion object {
        fun build(context: Context): SafeBoxDatabase =
            Room.databaseBuilder(context, SafeBoxDatabase::class.java, "safebox.db")
                .addMigrations(MIGRATION_1_2)
                // Never fallbackToDestructiveMigration: it silently deletes the vault.
                .build()
    }
}
