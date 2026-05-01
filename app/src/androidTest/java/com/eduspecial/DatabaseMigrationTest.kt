package com.eduspecial

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eduspecial.data.local.EduSpecialDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 22.1 — Verifies Room migration 1→2 creates `bookmarks` and `daily_review_logs` tables.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EduSpecialDatabase::class.java
    )

    @Test
    fun migration1To2_createsBookmarksTable() {
        // Create version 1 database
        helper.createDatabase("test_db", 1).apply { close() }

        // Run migration to version 2
        val db = helper.runMigrationsAndValidate("test_db", 2, true)

        // Verify bookmarks table exists by querying it
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='bookmarks'")
        assert(cursor.count == 1) { "bookmarks table should exist after migration 1→2" }
        cursor.close()
        db.close()
    }

    @Test
    fun migration1To2_createsDailyReviewLogsTable() {
        helper.createDatabase("test_db_2", 1).apply { close() }

        val db = helper.runMigrationsAndValidate("test_db_2", 2, true)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='daily_review_logs'")
        assert(cursor.count == 1) { "daily_review_logs table should exist after migration 1→2" }
        cursor.close()
        db.close()
    }

    @Test
    fun migration1To2_bookmarksTableHasCorrectSchema() {
        helper.createDatabase("test_db_3", 1).apply { close() }

        val db = helper.runMigrationsAndValidate("test_db_3", 2, true)

        // Verify columns exist
        val cursor = db.query("PRAGMA table_info(bookmarks)")
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndex("name")))
        }
        cursor.close()
        db.close()

        assert("id" in columns) { "bookmarks should have id column" }
        assert("itemId" in columns) { "bookmarks should have itemId column" }
        assert("itemType" in columns) { "bookmarks should have itemType column" }
        assert("createdAt" in columns) { "bookmarks should have createdAt column" }
    }
}
