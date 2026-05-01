package com.eduspecial.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.database.Cursor
import com.eduspecial.data.local.dao.AnalyticsDao
import com.eduspecial.data.local.dao.BookmarkDao
import com.eduspecial.data.local.dao.FlashcardDao
import com.eduspecial.data.local.dao.PendingSubmissionDao
import com.eduspecial.data.local.dao.QADao
import com.eduspecial.data.local.entities.BookmarkEntity
import com.eduspecial.data.local.entities.DailyReviewLogEntity
import com.eduspecial.data.local.entities.FlashcardEntity
import com.eduspecial.data.local.entities.PendingSubmissionEntity
import com.eduspecial.data.local.entities.QAAnswerEntity
import com.eduspecial.data.local.entities.QAQuestionEntity

@Database(
    entities = [
        FlashcardEntity::class,
        QAQuestionEntity::class,
        QAAnswerEntity::class,
        PendingSubmissionEntity::class,
        BookmarkEntity::class,
        DailyReviewLogEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class EduSpecialDatabase : RoomDatabase() {
    abstract fun flashcardDao(): FlashcardDao
    abstract fun qaDao(): QADao
    abstract fun pendingSubmissionDao(): PendingSubmissionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun analyticsDao(): AnalyticsDao

    companion object {
        const val DATABASE_NAME = "eduspecial_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS bookmarks (
                        id TEXT NOT NULL PRIMARY KEY,
                        itemId TEXT NOT NULL,
                        itemType TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_itemId_itemType 
                    ON bookmarks (itemId, itemType)
                """)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_review_logs (
                        dayEpoch INTEGER NOT NULL PRIMARY KEY,
                        reviewCount INTEGER NOT NULL DEFAULT 0,
                        archivedCount INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE flashcards ADD COLUMN audioUrl TEXT"
                )
                database.execSQL(
                    "ALTER TABLE flashcards ADD COLUMN localAudioPath TEXT"
                )
                database.execSQL(
                    "ALTER TABLE flashcards ADD COLUMN isAudioReady INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE flashcards ADD COLUMN groupName TEXT NOT NULL DEFAULT ''"
                )

                val hasLegacyTags = database.hasColumn("qa_questions", "tags")
                val hasHashtags = database.hasColumn("qa_questions", "hashtags")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS qa_questions_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        question TEXT NOT NULL,
                        category TEXT NOT NULL,
                        contributor TEXT NOT NULL,
                        contributorName TEXT NOT NULL,
                        contributorVerified INTEGER NOT NULL,
                        contributorAvatarUrl TEXT,
                        upvotes INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isAnswered INTEGER NOT NULL,
                        hashtags TEXT NOT NULL,
                        isPendingSync INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                val hashtagsExpression = when {
                    hasHashtags -> "hashtags"
                    hasLegacyTags -> "tags"
                    else -> "''"
                }
                database.execSQL(
                    """
                    INSERT INTO qa_questions_new (
                        id, question, category, contributor, contributorName, contributorVerified,
                        contributorAvatarUrl, upvotes, createdAt, isAnswered, hashtags, isPendingSync
                    )
                    SELECT
                        id, question, category, contributor, contributorName, contributorVerified,
                        contributorAvatarUrl, upvotes, createdAt, isAnswered, $hashtagsExpression, isPendingSync
                    FROM qa_questions
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE qa_questions")
                database.execSQL("ALTER TABLE qa_questions_new RENAME TO qa_questions")

                val hasParentAnswerId = database.hasColumn("qa_answers", "parentAnswerId")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS qa_answers_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        questionId TEXT NOT NULL,
                        content TEXT NOT NULL,
                        contributor TEXT NOT NULL,
                        contributorName TEXT NOT NULL,
                        contributorVerified INTEGER NOT NULL,
                        contributorAvatarUrl TEXT,
                        parentAnswerId TEXT,
                        upvotes INTEGER NOT NULL,
                        isAccepted INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                val parentAnswerExpression = if (hasParentAnswerId) "parentAnswerId" else "NULL"
                database.execSQL(
                    """
                    INSERT INTO qa_answers_new (
                        id, questionId, content, contributor, contributorName, contributorVerified,
                        contributorAvatarUrl, parentAnswerId, upvotes, isAccepted, createdAt
                    )
                    SELECT
                        id, questionId, content, contributor, contributorName, contributorVerified,
                        contributorAvatarUrl, $parentAnswerExpression, upvotes, isAccepted, createdAt
                    FROM qa_answers
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE qa_answers")
                database.execSQL("ALTER TABLE qa_answers_new RENAME TO qa_answers")
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
            query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
