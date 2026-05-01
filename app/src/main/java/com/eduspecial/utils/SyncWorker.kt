package com.eduspecial.utils

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.eduspecial.data.local.dao.PendingSubmissionDao
import com.eduspecial.data.local.entities.PendingSubmissionEntity
import com.eduspecial.data.repository.FlashcardRepository
import com.eduspecial.data.repository.NonRetryableSyncException
import com.eduspecial.data.repository.QARepository
import com.eduspecial.domain.model.MediaType
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val flashcardRepository: FlashcardRepository,
    private val qaRepository: QARepository,
    private val pendingDao: PendingSubmissionDao,
    private val prefs: UserPreferencesDataStore,
    private val networkMonitor: NetworkMonitor
) : CoroutineWorker(context, workerParams) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        if (!networkMonitor.isCurrentlyOnline()) return Result.retry()

        return try {
            // FIX: Clean up stale failed submissions first to prevent infinite accumulation.
            pendingDao.deleteFailedSubmissions()

            val pending = pendingDao.getAll()
            for (submission in pending) {
                val syncResult: kotlin.Result<Unit> = try {
                    when (submission.type) {
                        PendingSubmissionEntity.TYPE_FLASHCARD -> {
                            val data = gson.fromJson(submission.payload, FlashcardCreatePayload::class.java)
                            flashcardRepository.createFlashcard(
                                term = data.term,
                                definition = data.definition,
                                groupName = data.groupName.orEmpty(),
                                mediaUrl = data.mediaUrl,
                                mediaType = MediaType.valueOf(data.mediaType ?: "NONE"),
                                contributorId = data.contributorId,
                                passedId = data.id
                            ).map { Unit }
                        }
                        PendingSubmissionEntity.TYPE_QUESTION -> {
                            val data = gson.fromJson(submission.payload, QuestionCreatePayload::class.java)
                            qaRepository.createQuestion(
                                question = data.question,
                                contributorId = data.contributorId,
                                hashtags = data.hashtags ?: emptyList(),
                                passedId = data.id
                            ).map { Unit }
                        }
                        PendingSubmissionEntity.TYPE_ANSWER -> {
                            val data = gson.fromJson(submission.payload, AnswerCreatePayload::class.java)
                            qaRepository.createAnswer(
                                questionId = data.questionId,
                                content = data.content,
                                contributorId = data.contributorId,
                                parentAnswerId = data.parentAnswerId,
                                passedId = data.id
                            ).map { Unit }
                        }
                        PendingSubmissionEntity.TYPE_QUESTION_EDIT -> {
                            val data = gson.fromJson(submission.payload, QuestionEditPayload::class.java)
                            qaRepository.applyPendingQuestionEdit(
                                id = data.id,
                                question = data.question,
                                hashtags = data.hashtags ?: emptyList()
                            )
                        }
                        PendingSubmissionEntity.TYPE_ANSWER_EDIT -> {
                            val data = gson.fromJson(submission.payload, AnswerEditPayload::class.java)
                            qaRepository.applyPendingAnswerEdit(
                                id = data.id,
                                content = data.content
                            )
                        }
                        PendingSubmissionEntity.TYPE_QUESTION_DELETE -> {
                            val data = gson.fromJson(submission.payload, QuestionDeletePayload::class.java)
                            qaRepository.applyPendingQuestionDelete(data.id)
                        }
                        PendingSubmissionEntity.TYPE_ANSWER_DELETE -> {
                            val data = gson.fromJson(submission.payload, AnswerDeletePayload::class.java)
                            qaRepository.applyPendingAnswerDelete(
                                id = data.id,
                                questionId = data.questionId
                            )
                        }
                        else -> kotlin.Result.success(Unit)
                    }
                } catch (e: Exception) {
                    kotlin.Result.failure(e)
                }

                when {
                    syncResult.isSuccess -> pendingDao.delete(submission)
                    syncResult.exceptionOrNull() is NonRetryableSyncException -> pendingDao.delete(submission)
                    else -> pendingDao.incrementRetry(submission.localId)
                }
            }
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }

    private data class FlashcardCreatePayload(
        val id: String, val term: String, val definition: String, val groupName: String? = null,
        val mediaUrl: String?, val mediaType: String?, val contributorId: String
    )

    private data class QuestionCreatePayload(
        val id: String,
        val question: String,
        val category: String,
        val contributorId: String,
        val hashtags: List<String>? = emptyList()
    )

    private data class AnswerCreatePayload(
        val id: String,
        val questionId: String,
        val content: String,
        val contributorId: String,
        val parentAnswerId: String? = null
    )

    private data class QuestionEditPayload(
        val id: String,
        val question: String,
        val hashtags: List<String>? = emptyList()
    )

    private data class AnswerEditPayload(
        val id: String,
        val content: String
    )

    private data class QuestionDeletePayload(
        val id: String
    )

    private data class AnswerDeletePayload(
        val id: String,
        val questionId: String
    )

    companion object {
        const val WORK_NAME = "EduSpecial_SyncWorker"
        
        fun schedulePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun triggerImmediateSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ImmediateSync", ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
