package com.calcplus.calculator.data

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.database.MIGRATION_1_2
import com.calcplus.calculator.core.database.SafeBoxDatabase
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The single iteration-2 migration (decisions §0).
 *
 * A v1 database is built from the **committed** `1.json` — its `createSql`,
 * index statements, `setupQueries` (which carry v1's identity hash) and its
 * version — then reopened through the real [SafeBoxDatabase] with
 * [MIGRATION_1_2] registered. Room's own open path runs the migration and then
 * validates the resulting schema against the compiled v2 entities, so a column
 * that Room and the ALTER TABLE statements disagree about fails here rather
 * than on a user's device.
 *
 * Why not `MigrationTestHelper`: it loads the schemas from the *assets*, and
 * Robolectric only ever reads the app variant's merged assets — which means the
 * exported schemas, and therefore the vault's table and column names, would
 * have to be packaged into the debug APK. `app/build.gradle.kts` puts them on
 * the unit-test classpath instead, where nothing can package them.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The exported v1 schema, read from the unit-test classpath. */
    private fun v1Schema(): JSONObject {
        val path = "${SafeBoxDatabase::class.qualifiedName}/1.json"
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "$path is not on the unit-test classpath — see the schemas resources srcDir " +
                "in app/build.gradle.kts"
        }
        return JSONObject(stream.bufferedReader().use { it.readText() }).getJSONObject("database")
    }

    /**
     * A populated v1 database file, created from raw SQL so it cannot drift with
     * the current entity classes.
     */
    private fun createV1Database(seed: (SQLiteConnection) -> Unit): String {
        val name = "safebox-migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = v1Schema()
        AndroidSQLiteDriver().open(file.absolutePath).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(TABLE_NAME_PLACEHOLDER, table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(
                        indices.getJSONObject(j).getString("createSql")
                            .replace(TABLE_NAME_PLACEHOLDER, table)
                    )
                }
            }
            // room_master_table + v1's identity hash, exactly as Room writes it.
            val setupQueries = schema.getJSONArray("setupQueries")
            for (i in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(i))
            db.execSQL("PRAGMA user_version = ${schema.getInt("version")}")
            seed(db)
        }
        return name
    }

    /**
     * Opens the real database on [name]; Room runs [MIGRATION_1_2] and validates
     * the migrated schema against the compiled entities while doing so.
     */
    private fun migrate(name: String): SafeBoxDatabase =
        Room.databaseBuilder(context, SafeBoxDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
            .also { it.openHelper.writableDatabase } // forces the open, and the migration

    private fun SafeBoxDatabase.query(sql: String): Cursor = openHelper.writableDatabase.query(sql)

    @Test
    fun v1DataSurvivesTheV2MigrationWithTheNewColumnsAtTheirDefaults() {
        val name = createV1Database { db ->
            db.execSQL(
                "INSERT INTO albums (id, name, createdAt, sortIndex) VALUES ('a1', 'Trips', 100, 0)"
            )
            db.execSQL(
                """
                INSERT INTO photos
                  (id, albumId, fileName, thumbFileName, mimeType, width, height, byteCount, importedAt, sortIndex)
                VALUES ('p1', 'a1', 'p1.jpg', 'p1-thumb.jpg', 'image/jpeg', 40, 30, 1234, 200, 0)
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO notes (id, body, title, snippet, createdAt, updatedAt) " +
                    "VALUES ('n1', '# Milk', 'Milk', '', 300, 400)"
            )
            db.execSQL("INSERT INTO tags (id, name, colorIndex) VALUES ('t1', 'work', 2)")
            db.execSQL("INSERT INTO note_tags (noteId, tagId) VALUES ('n1', 't1')")
            db.execSQL(
                """
                INSERT INTO contacts
                  (id, firstName, lastName, organization, phones, emails, address, notes, createdAt, updatedAt)
                VALUES ('c1', 'Grace', 'Hopper', 'Navy', '[]', '[]', NULL, NULL, 500, 600)
                """.trimIndent()
            )
        }

        val db = migrate(name)
        try {
            db.query("SELECT name, sortIndex, deletedAt FROM albums WHERE id = 'a1'").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("Trips", row.getString(0))
                assertEquals(0, row.getInt(1))
                assertTrue("deletedAt must migrate in as NULL", row.isNull(2))
            }
            db.query(
                "SELECT fileName, byteCount, deletedAt, mediaType, durationMs FROM photos WHERE id = 'p1'"
            ).use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("p1.jpg", row.getString(0))
                assertEquals(1234L, row.getLong(1))
                assertTrue(row.isNull(2))
                // NOT NULL with a SQL default: every migrated row is a photo.
                assertEquals("photo", row.getString(3))
                assertTrue(row.isNull(4))
            }
            db.query("SELECT title, deletedAt FROM notes WHERE id = 'n1'").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("Milk", row.getString(0))
                assertTrue(row.isNull(1))
            }
            db.query("SELECT COUNT(*) FROM note_tags WHERE noteId = 'n1' AND tagId = 't1'").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(1, row.getInt(0)) // the join row survives
            }
            db.query("SELECT firstName, organization, deletedAt FROM contacts WHERE id = 'c1'").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("Grace", row.getString(0))
                assertEquals("Navy", row.getString(1))
                assertTrue(row.isNull(2))
            }
            // Nothing was dropped and recreated: exactly one row per table.
            db.query("SELECT COUNT(*) FROM photos").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(1, row.getInt(0))
            }
            // Room accepted the migrated schema and stamped the file as v2.
            assertEquals(2, db.openHelper.writableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun theMigrationIsAdditiveOnly() {
        val name = createV1Database { db ->
            db.execSQL("INSERT INTO albums (id, name, createdAt, sortIndex) VALUES ('a1', 'Keep', 1, 0)")
        }

        val db = migrate(name)
        try {
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(photos)").use { row ->
                while (row.moveToNext()) columns.add(row.getString(1))
            }
            // The v1 columns are all still there…
            assertTrue(
                columns.containsAll(
                    listOf(
                        "id", "albumId", "fileName", "thumbFileName", "mimeType",
                        "width", "height", "byteCount", "importedAt", "sortIndex",
                    )
                )
            )
            // …plus exactly the three the decision table adds.
            assertTrue(columns.containsAll(listOf("deletedAt", "mediaType", "durationMs")))
            assertEquals(13, columns.size)
            assertFalse(columns.contains("coverPhotoId"))
        } finally {
            db.close()
        }
    }

    @Test
    fun theExportedV1SchemaIsTheOneTheMigrationStartsFrom() {
        // The fixture above is only honest while 1.json really is v1 and really
        // describes the six tables the migration alters.
        val schema = v1Schema()
        assertEquals(1, schema.getInt("version"))
        val entities = schema.getJSONArray("entities")
        val tables = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        assertEquals(
            listOf("albums", "photos", "notes", "tags", "note_tags", "contacts").sorted(),
            tables.sorted(),
        )
    }

    private companion object {
        const val TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"
    }
}
