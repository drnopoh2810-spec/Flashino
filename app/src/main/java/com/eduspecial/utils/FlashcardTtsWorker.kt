package com.eduspecial.utils

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.eduspecial.data.remote.api.AudioBackendClient
import com.eduspecial.data.remote.api.AudioRequestKind
import com.eduspecial.data.repository.FlashcardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class FlashcardTtsWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val flashcardRepository: FlashcardRepository,
    private val cloudinaryService: com.eduspecial.data.remote.api.CloudinaryService,
    private val networkMonitor: NetworkMonitor,
    private val audioBackendClient: AudioBackendClient
) : androidx.work.CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FlashcardTtsWorker"
        private const val KEY_FLASHCARD_ID = "flashcard_id"

        fun enqueue(context: Context, flashcardId: String) {
            val request = OneTimeWorkRequestBuilder<FlashcardTtsWorker>()
                .setInputData(workDataOf(KEY_FLASHCARD_ID to flashcardId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "flashcard_tts_$flashcardId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val flashcardId = inputData.getString(KEY_FLASHCARD_ID) ?: return Result.failure()
        Log.d(TAG, "Starting audio hydration for flashcardId=$flashcardId")
        val flashcard = flashcardRepository.getFlashcard(flashcardId) ?: return Result.failure()
        if (flashcard.definition.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' }) {
            Log.d(TAG, "Skipping remote hydration for Arabic definition flashcardId=$flashcardId")
            return Result.success()
        }

        if (!flashcard.audioUrl.isNullOrBlank()) {
            return Result.success()
        }

        val reusableAudio = flashcardRepository.findReusableDefinitionAudio(flashcard.definition)
            ?.takeIf { it.id != flashcard.id }
        val localFile = flashcard.localAudioPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.length() > 0L }
            ?: reusableAudio?.localAudioPath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.exists() && it.length() > 0L }

        if (networkMonitor.isCurrentlyOnline()) {
            val resolved = audioBackendClient.resolveAudio(flashcard.definition, AudioRequestKind.DEFINITION)
            if (resolved != null) {
                val targetFile = File(
                    context.filesDir,
                    "tts_cache/definition_${DefinitionAudioFingerprint.buildCacheKey(flashcard.definition)}.mp3"
                )
                val localPath = if (audioBackendClient.downloadAudio(resolved.audioUrl, targetFile)) {
                    targetFile.absolutePath
                } else {
                    localFile?.absolutePath ?: flashcard.localAudioPath
                }
                flashcardRepository.updateAudioMetadata(
                    id = flashcard.id,
                    audioUrl = resolved.audioUrl,
                    localAudioPath = localPath,
                    isAudioReady = true
                )
                Log.d(TAG, "Hydrated backend audio for flashcardId=$flashcardId")
                return Result.success()
            }

            val publicId = DefinitionAudioFingerprint.buildPublicId(flashcard.definition)
            val remoteUrl = cloudinaryService.findAudioUrl(publicId)
            if (!remoteUrl.isNullOrBlank()) {
                flashcardRepository.updateAudioMetadata(
                    id = flashcard.id,
                    audioUrl = remoteUrl,
                    localAudioPath = localFile?.absolutePath ?: flashcard.localAudioPath,
                    isAudioReady = true
                )
                Log.d(TAG, "Hydrated Cloudinary audio for flashcardId=$flashcardId")
                return Result.success()
            }
        }

        if (localFile != null) {
            flashcardRepository.updateAudioMetadata(
                id = flashcard.id,
                audioUrl = reusableAudio?.audioUrl ?: flashcard.audioUrl,
                localAudioPath = localFile.absolutePath,
                isAudioReady = true
            )
            Log.d(TAG, "Hydrated local audio metadata for flashcardId=$flashcardId")
        }

        return Result.success()
    }
}
