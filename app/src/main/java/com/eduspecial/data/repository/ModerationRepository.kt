package com.eduspecial.data.repository

import android.util.Log
import com.eduspecial.data.remote.moderation.ContentModerationService
import com.eduspecial.data.remote.moderation.ContentType
import com.eduspecial.data.remote.moderation.ModerationDecision
import com.eduspecial.data.remote.moderation.ModerationFlag
import com.eduspecial.data.remote.moderation.ModerationResult
import com.eduspecial.data.remote.moderation.ReportReason
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ModerationRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val contentModerationService: ContentModerationService
) {
    companion object {
        private const val TAG = "ModerationRepository"
    }

    private val stateLock = Mutex()
    private val pendingReviewItems = mutableListOf<PendingReviewItem>()
    private val moderationLogs = mutableListOf<ModerationLogEntry>()

    suspend fun moderateFlashcard(
        term: String,
        definition: String,
        authorId: String
    ): FlashcardModerationResult {
        return try {
            val termResult = contentModerationService.moderateContent(
                content = term,
                contentType = ContentType.FLASHCARD_TERM,
                authorId = authorId
            )
            val definitionResult = contentModerationService.moderateContent(
                content = definition,
                contentType = ContentType.FLASHCARD_DEFINITION,
                authorId = authorId
            )

            val overallDecision = when {
                termResult.decision == ModerationDecision.REJECT ||
                    definitionResult.decision == ModerationDecision.REJECT -> ModerationDecision.REJECT
                termResult.decision == ModerationDecision.APPROVE_WITH_REVIEW ||
                    definitionResult.decision == ModerationDecision.APPROVE_WITH_REVIEW -> ModerationDecision.APPROVE_WITH_REVIEW
                else -> ModerationDecision.APPROVE
            }

            val combinedFlags = (termResult.flags + definitionResult.flags).distinct()
            val averageConfidence = (termResult.confidence + definitionResult.confidence) / 2
            val result = FlashcardModerationResult(
                decision = overallDecision,
                termResult = termResult,
                definitionResult = definitionResult,
                overallConfidence = averageConfidence,
                combinedFlags = combinedFlags,
                requiresReview = overallDecision == ModerationDecision.APPROVE_WITH_REVIEW
            )

            if (result.requiresReview) {
                addToPendingReview(
                    contentId = "flashcard:${term.hashCode()}:${definition.hashCode()}",
                    contentType = ContentType.FLASHCARD_DEFINITION,
                    content = "$term\n$definition",
                    authorId = authorId,
                    moderationResult = definitionResult
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Flashcard moderation failed: ${e.message}")
            FlashcardModerationResult(
                decision = ModerationDecision.APPROVE_WITH_REVIEW,
                termResult = ModerationResult(ModerationDecision.APPROVE_WITH_REVIEW, 0.0f, "Error"),
                definitionResult = ModerationResult(ModerationDecision.APPROVE_WITH_REVIEW, 0.0f, "Error"),
                overallConfidence = 0.0f,
                combinedFlags = listOf(ModerationFlag.SYSTEM_ERROR),
                requiresReview = true
            )
        }
    }

    suspend fun moderateQuestion(
        question: String,
        authorId: String
    ): ModerationResult {
        val result = contentModerationService.moderateContent(
            content = question,
            contentType = ContentType.QUESTION,
            authorId = authorId
        )
        if (result.decision == ModerationDecision.APPROVE_WITH_REVIEW) {
            addToPendingReview(
                contentId = "question:${question.hashCode()}",
                contentType = ContentType.QUESTION,
                content = question,
                authorId = authorId,
                moderationResult = result
            )
        }
        return result
    }

    suspend fun moderateAnswer(
        answer: String,
        authorId: String,
        questionId: String
    ): ModerationResult {
        val result = contentModerationService.moderateContent(
            content = answer,
            contentType = ContentType.ANSWER,
            authorId = authorId,
            additionalContext = mapOf("questionId" to questionId)
        )
        if (result.decision == ModerationDecision.APPROVE_WITH_REVIEW) {
            addToPendingReview(
                contentId = "answer:${questionId}:${answer.hashCode()}",
                contentType = ContentType.ANSWER,
                content = answer,
                authorId = authorId,
                moderationResult = result
            )
        }
        return result
    }

    suspend fun reportContent(
        contentId: String,
        contentType: ContentType,
        reason: ReportReason,
        additionalInfo: String = ""
    ): Boolean {
        val reporterId = authRepository.getCurrentUserId() ?: return false
        return contentModerationService.reportContent(
            contentId = contentId,
            contentType = contentType,
            reporterId = reporterId,
            reason = reason,
            additionalInfo = additionalInfo
        )
    }

    suspend fun addToPendingReview(
        contentId: String,
        contentType: ContentType,
        content: String,
        authorId: String,
        moderationResult: ModerationResult
    ): Boolean {
        return runCatching {
            stateLock.withLock {
                pendingReviewItems.add(
                    PendingReviewItem(
                        id = "${contentType.name.lowercase()}-${System.currentTimeMillis()}",
                        contentId = contentId,
                        contentType = contentType,
                        content = content,
                        authorId = authorId,
                        priority = calculateReviewPriority(moderationResult),
                        createdAt = System.currentTimeMillis(),
                        moderationReason = moderationResult.reason,
                        flags = moderationResult.flags
                    )
                )
                moderationLogs.add(
                    ModerationLogEntry(
                        decision = moderationResult.decision,
                        confidence = moderationResult.confidence,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            true
        }.getOrElse {
            Log.e(TAG, "Failed to add content to pending review: ${it.message}")
            false
        }
    }

    suspend fun getPendingReviewItems(limit: Int = 20): List<PendingReviewItem> {
        return stateLock.withLock {
            pendingReviewItems
                .sortedWith(compareByDescending<PendingReviewItem> { it.priority }.thenBy { it.createdAt })
                .take(limit)
        }
    }

    suspend fun reviewContent(
        reviewItemId: String,
        decision: ReviewDecision,
        reviewerNotes: String = ""
    ): Boolean {
        return stateLock.withLock {
            val removed = pendingReviewItems.removeAll { it.id == reviewItemId }
            if (removed) {
                moderationLogs.add(
                    ModerationLogEntry(
                        decision = when (decision) {
                            ReviewDecision.APPROVE -> ModerationDecision.APPROVE
                            ReviewDecision.REJECT -> ModerationDecision.REJECT
                            ReviewDecision.REQUEST_CHANGES -> ModerationDecision.APPROVE_WITH_REVIEW
                        },
                        confidence = 1.0f,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            removed
        }
    }

    suspend fun getModerationStats(days: Int = 7): ModerationStats {
        val cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
        return stateLock.withLock {
            val logs = moderationLogs.filter { it.timestamp >= cutoffTime }
            val approvedCount = logs.count { it.decision == ModerationDecision.APPROVE }
            val rejectedCount = logs.count { it.decision == ModerationDecision.REJECT }
            val reviewCount = logs.count { it.decision == ModerationDecision.APPROVE_WITH_REVIEW }
            ModerationStats(
                totalItemsModerated = logs.size,
                approvedCount = approvedCount,
                rejectedCount = rejectedCount,
                pendingReviewCount = reviewCount,
                currentPendingCount = pendingReviewItems.size,
                averageConfidence = if (logs.isEmpty()) 0.0f else logs.map { it.confidence }.average().toFloat(),
                periodDays = days
            )
        }
    }

    suspend fun updateBlacklist(
        terms: List<String>,
        patterns: List<String>
    ): Boolean {
        return runCatching {
            contentModerationService.updateBlacklist(terms, patterns)
            true
        }.getOrElse {
            Log.e(TAG, "Failed to update blacklist: ${it.message}")
            false
        }
    }

    private fun calculateReviewPriority(result: ModerationResult): Int {
        return when {
            result.flags.contains(ModerationFlag.BLACKLISTED_CONTENT) -> 10
            result.flags.contains(ModerationFlag.SPAM_INDICATORS) -> 8
            result.riskScore > 0.7f -> 7
            result.flags.contains(ModerationFlag.OFF_TOPIC) -> 5
            result.userReputationScore < 0.3f -> 4
            else -> 3
        }
    }
}

data class FlashcardModerationResult(
    val decision: ModerationDecision,
    val termResult: ModerationResult,
    val definitionResult: ModerationResult,
    val overallConfidence: Float,
    val combinedFlags: List<ModerationFlag>,
    val requiresReview: Boolean
)

data class PendingReviewItem(
    val id: String,
    val contentId: String,
    val contentType: ContentType,
    val content: String,
    val authorId: String,
    val priority: Int,
    val createdAt: Long,
    val moderationReason: String,
    val flags: List<ModerationFlag>
)

data class ModerationStats(
    val totalItemsModerated: Int = 0,
    val approvedCount: Int = 0,
    val rejectedCount: Int = 0,
    val pendingReviewCount: Int = 0,
    val currentPendingCount: Int = 0,
    val averageConfidence: Float = 0.0f,
    val periodDays: Int = 7
)

enum class ReviewDecision {
    APPROVE,
    REJECT,
    REQUEST_CHANGES
}

private data class ModerationLogEntry(
    val decision: ModerationDecision,
    val confidence: Float,
    val timestamp: Long
)
