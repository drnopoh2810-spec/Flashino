package com.eduspecial.data.repository

import android.util.Log
import com.eduspecial.data.local.dao.PendingSubmissionDao
import com.eduspecial.data.local.dao.QADao
import com.eduspecial.data.local.entities.PendingSubmissionEntity
import com.eduspecial.data.local.entities.QAAnswerEntity
import com.eduspecial.data.local.entities.QAQuestionEntity
import com.eduspecial.data.remote.api.BackendQAAnswer
import com.eduspecial.data.remote.api.BackendQAQuestion
import com.eduspecial.data.remote.api.QABackendClient
import com.eduspecial.data.remote.api.QABackendException
import com.eduspecial.data.remote.moderation.ModerationDecision
import com.eduspecial.domain.model.DuplicateCheckResult
import com.eduspecial.domain.model.FlashcardCategory
import com.eduspecial.domain.model.LeaderboardPeriod
import com.eduspecial.domain.model.QAAnswer
import com.eduspecial.domain.model.QAQuestion
import com.eduspecial.utils.UserPreferencesDataStore
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class QARepository @Inject constructor(
    private val qaDao: QADao,
    private val pendingDao: PendingSubmissionDao,
    private val qaBackendClient: QABackendClient,
    private val leaderboardRepository: LeaderboardRepository,
    private val moderationRepository: ModerationRepository,
    private val prefs: UserPreferencesDataStore,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "QARepository"
        private const val DUPLICATE_CHECK_TIMEOUT_MS = 1_500L
        private const val MODERATION_TIMEOUT_MS = 2_500L
        private const val POLL_INTERVAL_MS = 15_000L
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var pollJob: Job? = null

    fun startRealtimeListeners() {
        if (pollJob?.isActive == true) return
        pollJob = repoScope.launch {
            while (isActive) {
                runCatching { refreshFromServer() }
                    .onFailure { Log.w(TAG, "Q&A refresh loop failed", it) }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun getAllQuestions(): Flow<List<QAQuestion>> =
        combineQuestionsWithAnswers(qaDao.getAllQuestions())

    fun getUnansweredQuestions(): Flow<List<QAQuestion>> =
        combineQuestionsWithAnswers(qaDao.getUnansweredQuestions())

    fun getAnswersForQuestion(questionId: String): Flow<List<QAAnswer>> =
        qaDao.getAnswersForQuestion(questionId).map { answers -> answers.map { it.toDomain() } }

    suspend fun searchLocal(query: String): List<QAQuestion> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()
        return qaDao.searchQuestionsFlow(normalizedQuery).first().map { it.toDomain() }
    }

    suspend fun search(
        query: String,
        category: FlashcardCategory?,
        unansweredOnly: Boolean,
        useAlgolia: Boolean
    ): List<QAQuestion> {
        return searchLocal(query)
            .filter { category == null || it.category == category }
            .filter { !unansweredOnly || !it.isAnswered }
    }

    suspend fun checkDuplicate(question: String): DuplicateCheckResult {
        val normalizedQuestion = question.trim()
        if (normalizedQuestion.isBlank()) return DuplicateCheckResult.NotDuplicate
        if (qaDao.countByQuestion(normalizedQuestion) > 0) {
            return DuplicateCheckResult.IsDuplicate(emptyList())
        }
        return DuplicateCheckResult.NotDuplicate
    }

    suspend fun createQuestion(
        question: String,
        contributorId: String,
        hashtags: List<String>,
        passedId: String? = null
    ): Result<QAQuestion> {
        val normalizedQuestion = question.trim()
        val moderationResult = moderationRepository.moderateQuestion(
            question = normalizedQuestion,
            authorId = contributorId,
        )
        if (moderationResult.decision == ModerationDecision.REJECT) {
            return Result.failure(Exception("السؤال مرفوض: ${moderationResult.reason}"))
        }

        val id = passedId ?: UUID.randomUUID().toString()
        val contributorKey = resolveContributorKey(contributorId)
        val author = resolveCurrentAuthorSnapshot(contributorKey)
        val localEntity = QAQuestionEntity(
            id = id,
            question = normalizedQuestion,
            category = FlashcardCategory.ABA_THERAPY.name,
            contributor = contributorKey,
            contributorName = author.displayName,
            contributorVerified = author.isVerified,
            contributorAvatarUrl = author.avatarUrl,
            hashtags = hashtags.joinToString(","),
            isPendingSync = true
        )

        qaDao.insertQuestion(localEntity)
        return try {
            val remoteQuestion = qaBackendClient.createQuestion(
                id = id,
                question = normalizedQuestion,
                category = FlashcardCategory.ABA_THERAPY.name,
                hashtags = hashtags
            ) ?: throw IllegalStateException("Q&A backend createQuestion failed")
            val synced = remoteQuestion.toEntity()
            qaDao.insertQuestion(synced)
            repoScope.launch {
                runCatching { leaderboardRepository.awardQuestionPoints(contributorKey) }
                    .onFailure { Log.w(TAG, "Question points side-effect failed for $id", it) }
                runCatching { refreshFromServer() }
            }
            Result.success(synced.toDomain())
        } catch (error: Exception) {
            Log.w(TAG, "createQuestion failed for id=$id", error)
            if (isRetryableSyncError(error)) {
                if (passedId == null) {
                    enqueueQuestionCreate(id, normalizedQuestion, contributorKey, hashtags)
                    return Result.success(localEntity.toDomain())
                }
                return Result.failure(error)
            }
            deleteLocalQuestionTree(id)
            Result.failure(toUserFacingSyncError(error, "تعذر نشر السؤال"))
        }
    }

    suspend fun createAnswer(
        questionId: String,
        content: String,
        contributorId: String,
        parentAnswerId: String? = null,
        passedId: String? = null
    ): Result<QAAnswer> {
        val normalizedContent = content.trim()
        val moderationResult = moderationRepository.moderateAnswer(
            answer = normalizedContent,
            authorId = contributorId,
            questionId = questionId
        )
        if (moderationResult.decision == ModerationDecision.REJECT) {
            return Result.failure(Exception("الإجابة مرفوضة: ${moderationResult.reason}"))
        }

        val id = passedId ?: UUID.randomUUID().toString()
        val contributorKey = resolveContributorKey(contributorId)
        val author = resolveCurrentAuthorSnapshot(contributorKey)
        val localEntity = QAAnswerEntity(
            id = id,
            questionId = questionId,
            content = normalizedContent,
            contributor = contributorKey,
            contributorName = author.displayName,
            contributorVerified = author.isVerified,
            contributorAvatarUrl = author.avatarUrl,
            parentAnswerId = parentAnswerId
        )

        qaDao.insertAnswer(localEntity)
        return try {
            val remoteAnswer = qaBackendClient.createAnswer(
                id = id,
                questionId = questionId,
                content = normalizedContent,
                parentAnswerId = parentAnswerId
            ) ?: throw IllegalStateException("Q&A backend createAnswer failed")
            val synced = remoteAnswer.toEntity()
            qaDao.insertAnswer(synced)
            refreshLocalQuestionState(questionId)
            repoScope.launch {
                runCatching { leaderboardRepository.awardAnswerPoints(contributorKey) }
                    .onFailure { Log.w(TAG, "Answer points side-effect failed for $id", it) }
                runCatching { refreshFromServer() }
            }
            Result.success(synced.toDomain())
        } catch (error: Exception) {
            Log.w(TAG, "createAnswer failed for id=$id", error)
            if (isRetryableSyncError(error)) {
                if (passedId == null) {
                    enqueueAnswerCreate(id, questionId, normalizedContent, contributorKey, parentAnswerId)
                    return Result.success(localEntity.toDomain())
                }
                return Result.failure(error)
            }
            qaDao.deleteAnswerById(id)
            Result.failure(toUserFacingSyncError(error, "تعذر نشر الإجابة"))
        }
    }

    suspend fun toggleQuestionUpvote(id: String): Result<Boolean> {
        if (authRepository.getCurrentUserId().isNullOrBlank()) {
            return Result.failure(Exception("يجب تسجيل الدخول أولًا"))
        }
        val existing = qaDao.getQuestionById(id) ?: return Result.failure(Exception("السؤال غير موجود"))
        val likedIds = prefs.likedQaQuestionIds.first()
        val wasLiked = likedIds.contains(id)
        val optimisticLiked = !wasLiked
        val optimisticDelta = if (optimisticLiked) 1 else -1

        qaDao.insertQuestion(existing.copy(upvotes = maxOf(existing.upvotes + optimisticDelta, 0)))
        prefs.setQuestionLiked(id, optimisticLiked)

        return try {
            val result = qaBackendClient.toggleQuestionVote(id)
                ?: throw IllegalStateException("Q&A backend toggleQuestionVote failed")
            qaDao.getQuestionById(id)?.let { qaDao.insertQuestion(it.copy(upvotes = result.upvotes)) }
            prefs.setQuestionLiked(id, result.liked)
            Result.success(result.liked)
        } catch (error: Exception) {
            Log.w(TAG, "toggleQuestionUpvote failed for id=$id", error)
            qaDao.getQuestionById(id)?.let { qaDao.insertQuestion(it.copy(upvotes = existing.upvotes)) }
            prefs.setQuestionLiked(id, wasLiked)
            Result.failure(toUserFacingSyncError(error, "تعذر تحديث الإعجاب"))
        }
    }

    suspend fun toggleAnswerUpvote(answerId: String): Result<Boolean> {
        if (authRepository.getCurrentUserId().isNullOrBlank()) {
            return Result.failure(Exception("يجب تسجيل الدخول أولًا"))
        }
        val existing = qaDao.getAnswerById(answerId) ?: return Result.failure(Exception("الإجابة غير موجودة"))
        val likedIds = prefs.likedQaAnswerIds.first()
        val wasLiked = likedIds.contains(answerId)
        val optimisticLiked = !wasLiked
        val optimisticDelta = if (optimisticLiked) 1 else -1

        qaDao.insertAnswer(existing.copy(upvotes = maxOf(existing.upvotes + optimisticDelta, 0)))
        prefs.setAnswerLiked(answerId, optimisticLiked)

        return try {
            val result = qaBackendClient.toggleAnswerVote(answerId)
                ?: throw IllegalStateException("Q&A backend toggleAnswerVote failed")
            qaDao.getAnswerById(answerId)?.let { qaDao.insertAnswer(it.copy(upvotes = result.upvotes)) }
            prefs.setAnswerLiked(answerId, result.liked)
            Result.success(result.liked)
        } catch (error: Exception) {
            Log.w(TAG, "toggleAnswerUpvote failed for id=$answerId", error)
            qaDao.getAnswerById(answerId)?.let { qaDao.insertAnswer(it.copy(upvotes = existing.upvotes)) }
            prefs.setAnswerLiked(answerId, wasLiked)
            Result.failure(toUserFacingSyncError(error, "تعذر تحديث الإعجاب"))
        }
    }

    suspend fun acceptAnswer(answerId: String, questionId: String): Result<Unit> {
        val previousQuestion = qaDao.getQuestionById(questionId)
            ?: return Result.failure(Exception("السؤال غير موجود"))
        val previousAnswers = qaDao.getAnswersForQuestion(questionId).first()

        previousAnswers.forEach { answer ->
            qaDao.insertAnswer(answer.copy(isAccepted = answer.id == answerId))
        }
        qaDao.markQuestionAnswered(questionId)

        return try {
            val accepted = qaBackendClient.acceptAnswer(answerId, questionId)
            if (!accepted) throw IllegalStateException("Q&A backend acceptAnswer failed")
            val acceptedAnswer = previousAnswers.firstOrNull { it.id == answerId }
            if (acceptedAnswer != null) {
                repoScope.launch {
                    runCatching { leaderboardRepository.awardAcceptedAnswerPoints(acceptedAnswer.contributor) }
                        .onFailure { Log.w(TAG, "Accepted answer points side-effect failed for $answerId", it) }
                }
            }
            repoScope.launch { runCatching { refreshFromServer() } }
            Result.success(Unit)
        } catch (error: Exception) {
            Log.w(TAG, "acceptAnswer failed for answerId=$answerId", error)
            previousAnswers.forEach { qaDao.insertAnswer(it) }
            qaDao.insertQuestion(previousQuestion)
            Result.failure(toUserFacingSyncError(error, "تعذر اعتماد الإجابة"))
        }
    }

    suspend fun editQuestion(id: String, question: String, hashtags: List<String>): Result<QAQuestion> {
        val existing = qaDao.getQuestionById(id) ?: return Result.failure(Exception("السؤال غير موجود"))
        val localPending = existing.copy(
            question = question.trim(),
            hashtags = hashtags.joinToString(","),
            isPendingSync = true
        )
        qaDao.insertQuestion(localPending)

        return try {
            val remoteQuestion = qaBackendClient.updateQuestion(id, question.trim(), hashtags)
                ?: throw IllegalStateException("Q&A backend updateQuestion failed")
            val synced = remoteQuestion.toEntity()
            qaDao.insertQuestion(synced)
            repoScope.launch { runCatching { refreshFromServer() } }
            Result.success(synced.toDomain())
        } catch (error: Exception) {
            if (error is QABackendException && error.statusCode == 404) {
                deleteLocalQuestionTree(id)
                return Result.failure(Exception("هذا السؤال لم يعد موجودًا وتمت إزالته من جهازك"))
            }
            if (isRetryableSyncError(error)) {
                enqueueQuestionEdit(id, question.trim(), hashtags)
                return Result.success(localPending.toDomain())
            }
            qaDao.insertQuestion(existing)
            Result.failure(toUserFacingSyncError(error, "تعذر تعديل السؤال"))
        }
    }

    suspend fun editAnswer(id: String, content: String): Result<QAAnswer> {
        val existing = qaDao.getAnswerById(id) ?: return Result.failure(Exception("الإجابة غير موجودة"))
        val localPending = existing.copy(content = content.trim())
        qaDao.insertAnswer(localPending)

        return try {
            val remoteAnswer = qaBackendClient.updateAnswer(id, content.trim())
                ?: throw IllegalStateException("Q&A backend updateAnswer failed")
            val synced = remoteAnswer.toEntity()
            qaDao.insertAnswer(synced)
            repoScope.launch { runCatching { refreshFromServer() } }
            Result.success(synced.toDomain())
        } catch (error: Exception) {
            if (error is QABackendException && error.statusCode == 404) {
                qaDao.deleteAnswerById(id)
                return Result.failure(Exception("هذه الإجابة لم تعد موجودة وتمت إزالتها من جهازك"))
            }
            if (isRetryableSyncError(error)) {
                enqueueAnswerEdit(id, content.trim())
                return Result.success(localPending.toDomain())
            }
            qaDao.insertAnswer(existing)
            Result.failure(toUserFacingSyncError(error, "تعذر تعديل الإجابة"))
        }
    }

    suspend fun deleteAnswer(id: String): Result<Unit> {
        val existingAnswer = qaDao.getAnswerById(id) ?: return Result.failure(Exception("الإجابة غير موجودة"))
        val replies = qaDao.getAllAnswers().first().filter { it.parentAnswerId == id }
        val previousQuestion = qaDao.getQuestionById(existingAnswer.questionId)
        val previousAnswers = qaDao.getAnswersForQuestion(existingAnswer.questionId).first()

        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_ANSWER, "\"id\":\"$id\"")
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_ANSWER_EDIT, "\"id\":\"$id\"")
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_ANSWER_DELETE, "\"id\":\"$id\"")

        qaDao.deleteRepliesForAnswer(id)
        qaDao.deleteAnswerById(id)
        refreshLocalQuestionState(existingAnswer.questionId)

        return try {
            val deleted = qaBackendClient.deleteAnswer(id)
            if (!deleted) throw IllegalStateException("Q&A backend deleteAnswer failed")
            repoScope.launch { runCatching { refreshFromServer() } }
            Result.success(Unit)
        } catch (error: Exception) {
            if (isRetryableSyncError(error)) {
                enqueueAnswerDelete(id, existingAnswer.questionId)
                return Result.success(Unit)
            }
            qaDao.insertAnswer(existingAnswer)
            replies.forEach { qaDao.insertAnswer(it) }
            previousQuestion?.let { qaDao.insertQuestion(it) }
            previousAnswers.filter { it.id != existingAnswer.id }.forEach { qaDao.insertAnswer(it) }
            Result.failure(toUserFacingSyncError(error, "تعذر حذف الإجابة"))
        }
    }

    suspend fun deleteQuestion(id: String): Result<Unit> {
        val existingQuestion = qaDao.getQuestionById(id) ?: return Result.failure(Exception("السؤال غير موجود"))
        val previousAnswers = qaDao.getAnswersForQuestion(id).first()

        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_QUESTION, "\"id\":\"$id\"")
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_QUESTION_EDIT, "\"id\":\"$id\"")
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_QUESTION_DELETE, "\"id\":\"$id\"")
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_ANSWER, "\"questionId\":\"$id\"")

        deleteLocalQuestionTree(id)

        return try {
            val deleted = qaBackendClient.deleteQuestion(id)
            if (!deleted) throw IllegalStateException("Q&A backend deleteQuestion failed")
            repoScope.launch { runCatching { refreshFromServer() } }
            Result.success(Unit)
        } catch (error: Exception) {
            if (isRetryableSyncError(error)) {
                enqueueQuestionDelete(id)
                return Result.success(Unit)
            }
            qaDao.insertQuestion(existingQuestion)
            previousAnswers.forEach { qaDao.insertAnswer(it) }
            Result.failure(toUserFacingSyncError(error, "تعذر حذف السؤال"))
        }
    }

    suspend fun refreshFromServer() {
        syncMutex.withLock {
            val feed = qaBackendClient.fetchFeed() ?: return
            val pending = pendingDao.getAll()
            val pendingQuestionCreateIds = pending.extractPendingIds(PendingSubmissionEntity.TYPE_QUESTION)
            val pendingQuestionEditIds = pending.extractPendingIds(PendingSubmissionEntity.TYPE_QUESTION_EDIT)
            val pendingQuestionDeleteIds = pending.extractPendingIds(PendingSubmissionEntity.TYPE_QUESTION_DELETE)
            val pendingAnswerCreateIds = pending.extractPendingIds(PendingSubmissionEntity.TYPE_ANSWER)
            val pendingAnswerEditIds = pending.extractPendingIds(PendingSubmissionEntity.TYPE_ANSWER_EDIT)
            val pendingAnswerDeleteIds = pending.extractPendingIds(PendingSubmissionEntity.TYPE_ANSWER_DELETE)

            val protectedQuestionIds = pendingQuestionCreateIds + pendingQuestionEditIds + pendingQuestionDeleteIds
            val protectedAnswerIds = pendingAnswerCreateIds + pendingAnswerEditIds + pendingAnswerDeleteIds

            val remoteQuestionEntities = feed.questions.map { it.toEntity() }
            val remoteAnswerEntities = feed.answers.map { it.toEntity() }
            val remoteQuestionIds = remoteQuestionEntities.map { it.id }.toSet()
            val remoteAnswerIds = remoteAnswerEntities.map { it.id }.toSet()

            qaDao.getAllAnswers().first()
                .filter { it.id !in remoteAnswerIds && it.id !in protectedAnswerIds }
                .forEach { qaDao.deleteAnswerById(it.id) }

            qaDao.getAllQuestions().first()
                .filter { it.id !in remoteQuestionIds && it.id !in protectedQuestionIds }
                .forEach { deleteLocalQuestionTree(it.id) }

            remoteQuestionEntities.forEach { question ->
                if (question.id in pendingQuestionDeleteIds) return@forEach
                if (question.id in pendingQuestionCreateIds || question.id in pendingQuestionEditIds) return@forEach
                qaDao.insertQuestion(question)
            }

            remoteAnswerEntities.forEach { answer ->
                if (answer.id in pendingAnswerDeleteIds) return@forEach
                if (answer.id in pendingAnswerCreateIds || answer.id in pendingAnswerEditIds) return@forEach
                qaDao.insertAnswer(answer)
            }
        }
    }

    suspend fun syncFromServer(since: Long) {
        refreshFromServer()
    }

    suspend fun applyPendingQuestionEdit(id: String, question: String, hashtags: List<String>): Result<Unit> {
        return try {
            val remoteQuestion = qaBackendClient.updateQuestion(id, question.trim(), hashtags)
                ?: throw IllegalStateException("Q&A backend updateQuestion failed")
            qaDao.insertQuestion(remoteQuestion.toEntity())
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is QABackendException && error.statusCode == 404) {
                deleteLocalQuestionTree(id)
                return Result.failure(NonRetryableSyncException("Question $id no longer exists remotely", error))
            }
            Result.failure(error)
        }
    }

    suspend fun applyPendingAnswerEdit(id: String, content: String): Result<Unit> {
        return try {
            val remoteAnswer = qaBackendClient.updateAnswer(id, content.trim())
                ?: throw IllegalStateException("Q&A backend updateAnswer failed")
            qaDao.insertAnswer(remoteAnswer.toEntity())
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is QABackendException && error.statusCode == 404) {
                qaDao.deleteAnswerById(id)
                return Result.failure(NonRetryableSyncException("Answer $id no longer exists remotely", error))
            }
            Result.failure(error)
        }
    }

    suspend fun applyPendingQuestionDelete(id: String): Result<Unit> {
        return try {
            val deleted = qaBackendClient.deleteQuestion(id)
            if (!deleted) throw IllegalStateException("Q&A backend deleteQuestion failed")
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is QABackendException && error.statusCode == 404) {
                return Result.failure(NonRetryableSyncException("Question $id no longer exists remotely", error))
            }
            Result.failure(error)
        }
    }

    suspend fun applyPendingAnswerDelete(id: String, questionId: String): Result<Unit> {
        return try {
            val deleted = qaBackendClient.deleteAnswer(id)
            if (!deleted) throw IllegalStateException("Q&A backend deleteAnswer failed")
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is QABackendException && error.statusCode == 404) {
                return Result.failure(NonRetryableSyncException("Answer $id no longer exists remotely", error))
            }
            Result.failure(error)
        }
    }

    private suspend fun resolveCurrentAuthorSnapshot(contributorKey: String): AuthorSnapshot {
        val displayName = authRepository.getCurrentDisplayName()
            ?.takeIf { it.isNotBlank() }
            ?: contributorKey.substringBefore('@').ifBlank { contributorKey.take(10) }
        val avatarUrl = authRepository.getCurrentAvatarUrl()
        val email = authRepository.getCurrentUserEmail()
        return AuthorSnapshot(
            displayName = displayName,
            avatarUrl = avatarUrl,
            isVerified = email.equals("mahmoudnabihsaleh@gmail.com", ignoreCase = true)
        )
    }

    private fun resolveContributorKey(originalContributorId: String): String {
        val currentUserId = authRepository.getCurrentUserId()
        val currentEmail = authRepository.getCurrentUserEmail()
            ?.trim()
            ?.lowercase()
        return when {
            originalContributorId.contains("@") -> originalContributorId.trim().lowercase()
            !currentEmail.isNullOrBlank() && currentUserId == originalContributorId -> currentEmail
            else -> originalContributorId
        }
    }

    private suspend fun refreshLocalQuestionState(questionId: String) {
        val question = qaDao.getQuestionById(questionId) ?: return
        val answers = qaDao.getAnswersForQuestion(questionId).first()
        qaDao.insertQuestion(
            question.copy(isAnswered = answers.any { it.parentAnswerId == null || it.isAccepted })
        )
    }

    private suspend fun deleteLocalQuestionTree(id: String) {
        qaDao.deleteAnswersForQuestion(id)
        qaDao.deleteQuestionById(id)
    }

    private suspend fun enqueueQuestionCreate(
        id: String,
        question: String,
        contributorId: String,
        hashtags: List<String>
    ) {
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_QUESTION, "\"id\":\"$id\"")
        pendingDao.insert(
            PendingSubmissionEntity(
                localId = UUID.randomUUID().toString(),
                type = PendingSubmissionEntity.TYPE_QUESTION,
                payload = JSONObject().apply {
                    put("id", id)
                    put("question", question)
                    put("category", FlashcardCategory.ABA_THERAPY.name)
                    put("contributorId", contributorId)
                    put("hashtags", JSONArray(hashtags))
                }.toString()
            )
        )
    }

    private suspend fun enqueueAnswerCreate(
        id: String,
        questionId: String,
        content: String,
        contributorId: String,
        parentAnswerId: String?
    ) {
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_ANSWER, "\"id\":\"$id\"")
        pendingDao.insert(
            PendingSubmissionEntity(
                localId = UUID.randomUUID().toString(),
                type = PendingSubmissionEntity.TYPE_ANSWER,
                payload = JSONObject().apply {
                    put("id", id)
                    put("questionId", questionId)
                    put("content", content)
                    put("contributorId", contributorId)
                    put("parentAnswerId", parentAnswerId)
                }.toString()
            )
        )
    }

    private suspend fun enqueueQuestionEdit(id: String, question: String, hashtags: List<String>) {
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_QUESTION_EDIT, "\"id\":\"$id\"")
        pendingDao.insert(
            PendingSubmissionEntity(
                localId = UUID.randomUUID().toString(),
                type = PendingSubmissionEntity.TYPE_QUESTION_EDIT,
                payload = JSONObject().apply {
                    put("id", id)
                    put("question", question)
                    put("hashtags", JSONArray(hashtags))
                }.toString()
            )
        )
    }

    private suspend fun enqueueAnswerEdit(id: String, content: String) {
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_ANSWER_EDIT, "\"id\":\"$id\"")
        pendingDao.insert(
            PendingSubmissionEntity(
                localId = UUID.randomUUID().toString(),
                type = PendingSubmissionEntity.TYPE_ANSWER_EDIT,
                payload = JSONObject().apply {
                    put("id", id)
                    put("content", content)
                }.toString()
            )
        )
    }

    private suspend fun enqueueQuestionDelete(id: String) {
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_QUESTION_DELETE, "\"id\":\"$id\"")
        pendingDao.insert(
            PendingSubmissionEntity(
                localId = UUID.randomUUID().toString(),
                type = PendingSubmissionEntity.TYPE_QUESTION_DELETE,
                payload = JSONObject().apply {
                    put("id", id)
                }.toString()
            )
        )
    }

    private suspend fun enqueueAnswerDelete(id: String, questionId: String) {
        pendingDao.deleteByTypeAndPayloadMatch(PendingSubmissionEntity.TYPE_ANSWER_DELETE, "\"id\":\"$id\"")
        pendingDao.insert(
            PendingSubmissionEntity(
                localId = UUID.randomUUID().toString(),
                type = PendingSubmissionEntity.TYPE_ANSWER_DELETE,
                payload = JSONObject().apply {
                    put("id", id)
                    put("questionId", questionId)
                }.toString()
            )
        )
    }

    private fun isRetryableSyncError(error: Throwable): Boolean {
        return when (error) {
            is IOException,
            is SocketTimeoutException -> true
            is IllegalStateException -> error.message?.contains("failed", ignoreCase = true) == true
            is QABackendException -> error.statusCode >= 500 || error.statusCode == 408 || error.statusCode == 429
            else -> false
        }
    }

    private fun toUserFacingSyncError(error: Throwable, fallback: String): Exception {
        return when (error) {
            is QABackendException -> when (error.statusCode) {
                401 -> Exception("يجب تسجيل الدخول أولًا", error)
                403 -> Exception("لا تملك صلاحية تنفيذ هذا الإجراء", error)
                404 -> Exception("العنصر المطلوب لم يعد موجودًا", error)
                else -> Exception(fallback, error)
            }
            else -> Exception(fallback, error)
        }
    }

    private fun combineQuestionsWithAnswers(
        questionsFlow: Flow<List<QAQuestionEntity>>
    ): Flow<List<QAQuestion>> {
        return combine(questionsFlow, qaDao.getAllAnswers()) { questions, answers ->
            val answersByQuestionId = answers.groupBy { it.questionId }
            questions.map { question ->
                question.toDomain(answersByQuestionId[question.id].orEmpty())
            }
        }
    }

    private fun List<PendingSubmissionEntity>.extractPendingIds(type: String): Set<String> {
        return asSequence()
            .filter { it.type == type }
            .mapNotNull { item ->
                runCatching {
                    JSONObject(item.payload).optString("id").takeIf { it.isNotBlank() }
                }.getOrNull()
            }
            .toSet()
    }
}

private fun BackendQAQuestion.toEntity() = QAQuestionEntity(
    id = id,
    question = question,
    category = category,
    contributor = contributor,
    contributorName = contributorName,
    contributorVerified = contributorVerified,
    contributorAvatarUrl = contributorAvatarUrl,
    upvotes = upvotes,
    createdAt = createdAt,
    isAnswered = isAnswered,
    hashtags = hashtags.joinToString(","),
    isPendingSync = false
)

private fun BackendQAAnswer.toEntity() = QAAnswerEntity(
    id = id,
    questionId = questionId,
    content = content,
    contributor = contributor,
    contributorName = contributorName,
    contributorVerified = contributorVerified,
    contributorAvatarUrl = contributorAvatarUrl,
    parentAnswerId = parentAnswerId,
    upvotes = upvotes,
    isAccepted = isAccepted,
    createdAt = createdAt
)

fun QAQuestionEntity.toDomain(answers: List<QAAnswerEntity> = emptyList()) = QAQuestion(
    id = id,
    question = question,
    answers = answers
        .sortedWith(qaAnswerSortComparator)
        .map { it.toDomain() },
    category = runCatching { FlashcardCategory.valueOf(category) }.getOrDefault(FlashcardCategory.ABA_THERAPY),
    contributor = contributor,
    contributorName = contributorName,
    contributorVerified = contributorVerified,
    contributorAvatarUrl = contributorAvatarUrl,
    upvotes = upvotes,
    createdAt = Date(createdAt),
    isAnswered = isAnswered,
    hashtags = if (hashtags.isBlank()) emptyList() else hashtags.split(",").map { it.trim() }.filter { it.isNotBlank() }
)

private val qaAnswerSortComparator = Comparator<QAAnswerEntity> { left, right ->
    when {
        left.isAccepted != right.isAccepted -> if (left.isAccepted) -1 else 1
        left.upvotes != right.upvotes -> right.upvotes.compareTo(left.upvotes)
        else -> right.createdAt.compareTo(left.createdAt)
    }
}

fun QAAnswerEntity.toDomain() = QAAnswer(
    id = id,
    questionId = questionId,
    content = content,
    contributor = contributor,
    contributorName = contributorName,
    contributorVerified = contributorVerified,
    contributorAvatarUrl = contributorAvatarUrl,
    parentAnswerId = parentAnswerId,
    upvotes = upvotes,
    isAccepted = isAccepted,
    createdAt = Date(createdAt)
)

private data class AuthorSnapshot(
    val displayName: String,
    val avatarUrl: String?,
    val isVerified: Boolean
)
