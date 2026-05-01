package com.eduspecial.data.repository

import android.util.Log
import com.eduspecial.core.user.VerificationRules
import com.eduspecial.data.local.dao.QADao
import com.eduspecial.data.local.entities.QAAnswerEntity
import com.eduspecial.data.local.entities.QAQuestionEntity
import com.eduspecial.domain.model.LeaderboardEntry
import com.eduspecial.domain.model.LeaderboardPeriod
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class LeaderboardRepository @Inject constructor(
    private val qaDao: QADao,
    private val authRepository: AuthRepository
) {
    companion object {
        const val POINTS_FLASHCARD = 10
        const val POINTS_QUESTION = 5
        const val POINTS_ANSWER = 3
        const val POINTS_ACCEPTED = 15
        const val LEADERBOARD_LIMIT = 50
        private const val TAG = "LeaderboardRepo"
    }

    suspend fun getLeaderboard(period: LeaderboardPeriod): Result<List<LeaderboardEntry>> {
        return runCatching {
            val fromTime = when (period) {
                LeaderboardPeriod.ALL_TIME -> 0L
                LeaderboardPeriod.THIS_MONTH -> startOfCurrentMonth()
                LeaderboardPeriod.THIS_WEEK -> startOfCurrentWeek()
            }

            val questions = qaDao.getAllQuestions().first().filter { it.createdAt >= fromTime }
            val answers = qaDao.getAllAnswers().first().filter { it.createdAt >= fromTime }
            val userRows = buildUserDirectory(questions, answers)
            val currentUid = authRepository.getCurrentUserId()
            val currentEmail = authRepository.getCurrentUserEmail()?.trim()?.lowercase()
            val currentDisplayName = authRepository.getCurrentDisplayName()
            val currentAvatarUrl = authRepository.getCurrentAvatarUrl()

            val contributorIds = buildSet {
                questions.forEach { add(it.contributor) }
                answers.forEach { add(it.contributor) }
                currentUid?.let { add(it) }
                currentEmail?.let { add(it) }
            }.filter { it.isNotBlank() }

            val entries = contributorIds.map { userId ->
                val userQuestions = questions.count { it.contributor == userId }
                val userAnswers = answers.count { it.contributor == userId }
                val acceptedAnswers = answers.count { it.contributor == userId && it.isAccepted }
                val points = userQuestions * POINTS_QUESTION +
                    userAnswers * POINTS_ANSWER +
                    acceptedAnswers * POINTS_ACCEPTED

                val userRow = userRows[userId]
                val isCurrentUser = userId == currentUid ||
                    (!currentEmail.isNullOrBlank() && userId.equals(currentEmail, ignoreCase = true))
                val displayName = if (isCurrentUser) {
                    currentDisplayName ?: userRow?.name ?: "أنت"
                } else {
                    userRow?.name ?: "مستخدم"
                }
                val avatarUrl = if (isCurrentUser) {
                    currentAvatarUrl ?: userRow?.avatarUrl
                } else {
                    userRow?.avatarUrl
                }

                LeaderboardEntry(
                    userId = userId,
                    displayName = displayName,
                    avatarUrl = avatarUrl,
                    points = points,
                    isVerified = VerificationRules.isVerified(
                        userId = userId,
                        email = if (isCurrentUser) authRepository.getCurrentUserEmail() else null,
                        points = points
                    ),
                    isCurrentUser = isCurrentUser,
                    flashcardsAdded = 0,
                    questionsAsked = userQuestions,
                    answersGiven = userAnswers,
                    acceptedAnswers = acceptedAnswers
                )
            }
                .filter { it.points > 0 || it.isCurrentUser }
                .sortedWith(
                    compareByDescending<LeaderboardEntry> { it.points }
                        .thenByDescending { it.questionsAsked + it.answersGiven }
                        .thenBy { it.displayName }
                )
                .take(LEADERBOARD_LIMIT)
                .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

            if (entries.none { it.isCurrentUser } && (!currentUid.isNullOrBlank() || !currentEmail.isNullOrBlank())) {
                entries + LeaderboardEntry(
                    userId = currentUid ?: currentEmail.orEmpty(),
                    displayName = currentDisplayName ?: "أنت",
                    avatarUrl = currentAvatarUrl,
                    isVerified = VerificationRules.isVerified(
                        userId = currentUid ?: currentEmail,
                        email = authRepository.getCurrentUserEmail(),
                        points = 0
                    ),
                    rank = entries.size + 1,
                    isCurrentUser = true
                )
            } else {
                entries
            }
        }.onFailure {
            Log.e(TAG, "Failed to build leaderboard", it)
        }
    }

    suspend fun awardFlashcardPoints(userId: String) = Unit

    suspend fun awardQuestionPoints(userId: String) = Unit

    suspend fun awardAnswerPoints(userId: String) = Unit

    suspend fun awardAcceptedAnswerPoints(userId: String) = Unit

    private fun buildUserDirectory(
        questions: List<QAQuestionEntity>,
        answers: List<QAAnswerEntity>
    ): Map<String, LeaderboardUserRow> {
        val rows = mutableMapOf<String, LeaderboardUserRow>()

        fun merge(userId: String, name: String?, avatarUrl: String?, createdAt: Long) {
            if (userId.isBlank()) return
            val normalizedName = name?.takeIf { it.isNotBlank() } ?: userId.take(10)
            val incoming = LeaderboardUserRow(
                name = normalizedName,
                avatarUrl = avatarUrl,
                createdAt = createdAt
            )
            val existing = rows[userId]
            rows[userId] = when {
                existing == null -> incoming
                incoming.createdAt >= existing.createdAt && (
                    incoming.name.isNotBlank() || !incoming.avatarUrl.isNullOrBlank()
                ) -> incoming
                else -> existing
            }
        }

        questions.forEach { question ->
            merge(
                userId = question.contributor,
                name = question.contributorName,
                avatarUrl = question.contributorAvatarUrl,
                createdAt = question.createdAt
            )
        }
        answers.forEach { answer ->
            merge(
                userId = answer.contributor,
                name = answer.contributorName,
                avatarUrl = answer.contributorAvatarUrl,
                createdAt = answer.createdAt
            )
        }
        return rows
    }

    private fun startOfCurrentWeek(): Long {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.SATURDAY
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun startOfCurrentMonth(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private data class LeaderboardUserRow(
        val name: String,
        val avatarUrl: String?,
        val createdAt: Long
    )
}
