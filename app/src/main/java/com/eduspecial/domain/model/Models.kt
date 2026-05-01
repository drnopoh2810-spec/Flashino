package com.eduspecial.domain.model

import java.util.Date

// Flashcard & Common Models
data class Flashcard(
    val id: String,
    val term: String,
    val definition: String,
    val category: FlashcardCategory,
    val groupName: String = "",
    val mediaUrl: String? = null,
    val mediaType: MediaType = MediaType.NONE,
    val audioUrl: String? = null,
    val localAudioPath: String? = null,
    val isAudioReady: Boolean = false,
    val contributor: String,
    val createdAt: Date = Date(),
    val reviewState: ReviewState = ReviewState.NEW,
    val easeFactor: Float = 2.5f,
    val interval: Int = 1,
    val nextReviewDate: Date = Date(),
    val isOfflineCached: Boolean = false,
    val isPendingSync: Boolean = false
)

enum class FlashcardCategory {
    ABA_THERAPY, AUTISM_SPECTRUM, SENSORY_PROCESSING, SPEECH_LANGUAGE, 
    OCCUPATIONAL_THERAPY, BEHAVIORAL_INTERVENTION, INCLUSIVE_EDUCATION, 
    DEVELOPMENTAL_DISABILITIES, ASSESSMENT_TOOLS, FAMILY_SUPPORT
}

enum class MediaType { NONE, IMAGE, VIDEO, AUDIO }
enum class ReviewState { NEW, LEARNING, REVIEW, ARCHIVED }

sealed class SRSResult {
    data object Easy : SRSResult()
    data object Good : SRSResult()
    data object Hard : SRSResult()
    data object Again : SRSResult()
}

sealed class DuplicateCheckResult {
    data object NotDuplicate : DuplicateCheckResult()
    data class IsDuplicate(val existingItems: List<String>) : DuplicateCheckResult()
}

// Q&A Models
data class QAQuestion(
    val id: String,
    val question: String,
    val answers: List<QAAnswer> = emptyList(),
    val category: FlashcardCategory,
    val contributor: String,
    val contributorName: String = contributor,
    val contributorVerified: Boolean = false,
    val contributorAvatarUrl: String? = null,
    val upvotes: Int = 0,
    val createdAt: Date = Date(),
    val isAnswered: Boolean = false,
    val hashtags: List<String> = emptyList()
)

data class QAAnswer(
    val id: String,
    val questionId: String,
    val content: String,
    val contributor: String,
    val contributorName: String = contributor,
    val contributorVerified: Boolean = false,
    val contributorAvatarUrl: String? = null,
    val parentAnswerId: String? = null,
    val upvotes: Int = 0,
    val isAccepted: Boolean = false,
    val createdAt: Date = Date()
)

// Analytics & Search
data class DailyProgress(val dayEpoch: Long, val reviewCount: Int)
data class CategoryMastery(val category: FlashcardCategory, val total: Int, val archived: Int) {
    val percentage: Float get() = if (total == 0) 0f else archived.toFloat() / total
}

data class SearchResult(val id: String, val type: SearchResultType, val title: String, val subtitle: String)
enum class SearchResultType { FLASHCARD, QUESTION }

// Bookmark & User
enum class BookmarkType { FLASHCARD, QUESTION }
data class BookmarkCollection(val flashcards: List<Flashcard>, val questions: List<QAQuestion>)

data class User(
    val id: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null,
    val contributionCount: Int = 0,
    val joinedAt: Date = Date()
)

data class LeaderboardEntry(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val points: Int = 0,
    val isVerified: Boolean = false,
    val rank: Int = 0,
    val isCurrentUser: Boolean = false,
    val flashcardsAdded: Int = 0,
    val questionsAsked: Int = 0,
    val answersGiven: Int = 0,
    val acceptedAnswers: Int = 0
)

enum class LeaderboardPeriod(val label: String) {
    ALL_TIME("كل الوقت"), THIS_MONTH("هذا الشهر"), THIS_WEEK("هذا الأسبوع")
}
