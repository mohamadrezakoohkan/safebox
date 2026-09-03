package com.calcplus.calculator.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The single iteration-2 migration (decisions §0): purely additive, so every
 * v1 row survives untouched. Adds
 *  - `deletedAt INTEGER` (nullable) to albums, photos, notes, contacts — the
 *    "Recently deleted" stamp (P3);
 *  - `mediaType TEXT NOT NULL DEFAULT 'photo'` and `durationMs INTEGER`
 *    (nullable) to photos — reserved for video (N3), so N3 needs no migration.
 *
 * Verified by `MigrationTest` against the exported `schemas/…/1.json` and
 * `2.json`. Never pair this database with `fallbackToDestructiveMigration`.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE albums ADD COLUMN deletedAt INTEGER")
        connection.execSQL("ALTER TABLE photos ADD COLUMN deletedAt INTEGER")
        connection.execSQL("ALTER TABLE photos ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'photo'")
        connection.execSQL("ALTER TABLE photos ADD COLUMN durationMs INTEGER")
        connection.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER")
        connection.execSQL("ALTER TABLE contacts ADD COLUMN deletedAt INTEGER")
    }
}
