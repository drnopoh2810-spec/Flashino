package com.eduspecial.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eduspecial.domain.model.FlashcardCategory
import com.eduspecial.domain.model.MediaType
import com.eduspecial.domain.model.ReviewState
import java.util.Date

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val term: String,
    val definition: String,
    val category: String,              // stored as string enum name
    val groupName: String = "",
    val mediaUrl: String? = null,
    val mediaType: String = "NONE",
    val audioUrl: String? = null,
    val localAudioPath: String? = null,
    val isAudioReady: Boolean = false,
    val contributor: String,
    val createdAt: Long = System.currentTimeMillis(),
    val reviewState: String = "NEW",
    val easeFactor: Float = 2.5f,
    val interval: Int = 1,
    val nextReviewDate: Long = System.currentTimeMillis(),
    val isOfflineCached: Boolean = false,
    val isPendingSync: Boolean = false  // for offline-first sync
)

@Entity(tableName = "qa_questions")
data class QAQuestionEntity(
    @PrimaryKey val id: String,
    val question: String,
    val category: String,
    val contributor: String,
    val contributorName: String = contributor,
    val contributorVerified: Boolean = false,
    val contributorAvatarUrl: String? = null,
    val upvotes: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isAnswered: Boolean = false,
    val hashtags: String = "",
    val isPendingSync: Boolean = false
)

@Entity(tableName = "qa_answers")
data class QAAnswerEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val content: String,
    val contributor: String,
    val contributorName: String = contributor,
    val contributorVerified: Boolean = false,
    val contributorAvatarUrl: String? = null,
    val parentAnswerId: String? = null,
    val upvotes: Int = 0,
    val isAccepted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pending_submissions")
data class PendingSubmissionEntity(
    @PrimaryKey val localId: String,
    val type: String,                  // "FLASHCARD" | "QUESTION" | "ANSWER"
    val payload: String,               // JSON serialized submission
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
) {
    companion object {
        const val TYPE_FLASHCARD = "FLASHCARD"
        const val TYPE_QUESTION = "QUESTION"
        const val TYPE_ANSWER = "ANSWER"
        const val TYPE_FLASHCARD_EDIT = "FLASHCARD_EDIT"
        const val TYPE_QUESTION_EDIT = "QUESTION_EDIT"
        const val TYPE_ANSWER_EDIT = "ANSWER_EDIT"
        const val TYPE_QUESTION_DELETE = "QUESTION_DELETE"
        const val TYPE_ANSWER_DELETE = "ANSWER_DELETE"
    }
}

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["itemId", "itemType"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val itemId: String,
    val itemType: String,          // "FLASHCARD" | "QUESTION"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_review_logs")
data class DailyReviewLogEntity(
    @PrimaryKey val dayEpoch: Long,   // LocalDate.toEpochDay()
    val reviewCount: Int = 0,
    val archivedCount: Int = 0
)
