package com.eduspecial.data.repository

import com.eduspecial.data.local.dao.FlashcardDao
import com.eduspecial.data.local.entities.FlashcardEntity
import com.eduspecial.domain.model.CategoryMastery
import com.eduspecial.domain.model.DuplicateCheckResult
import com.eduspecial.domain.model.Flashcard
import com.eduspecial.domain.model.FlashcardCategory
import com.eduspecial.domain.model.MediaType
import com.eduspecial.domain.model.ReviewState
import com.eduspecial.domain.model.SRSResult
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class FlashcardRepository @Inject constructor(
    private val flashcardDao: FlashcardDao
) {
    fun startRealtimeListener() = Unit

    fun getAllFlashcards(): Flow<List<Flashcard>> =
        flashcardDao.getAllFlashcards().map { items -> items.map { it.toDomain() } }

    fun getByCategory(category: FlashcardCategory): Flow<List<Flashcard>> =
        flashcardDao.getByGroup(category.name).map { items -> items.map { it.toDomain() } }

    fun getByGroup(groupName: String): Flow<List<Flashcard>> =
        flashcardDao.getByGroup(groupName).map { items -> items.map { it.toDomain() } }

    fun getStudyQueue(): Flow<List<Flashcard>> =
        flashcardDao.getStudyQueue().map { items -> items.map { it.toDomain() } }

    fun getStudyQueue(groupNames: List<String>): Flow<List<Flashcard>> =
        if (groupNames.isEmpty()) getStudyQueue()
        else flashcardDao.getStudyQueueForGroups(groupNames).map { items -> items.map { it.toDomain() } }

    fun getGroupNames(): Flow<List<String>> = flashcardDao.getGroupNames()

    suspend fun getDueCount(): Int = flashcardDao.getDueCount()

    suspend fun getFlashcard(id: String): Flashcard? =
        flashcardDao.getFlashcardById(id)?.toDomain()

    suspend fun findReusableDefinitionAudio(definition: String): Flashcard? =
        flashcardDao.findReusableAudioByDefinition(definition.trim())?.toDomain()

    suspend fun updateAudioMetadata(
        id: String,
        audioUrl: String?,
        localAudioPath: String?,
        isAudioReady: Boolean
    ) = withContext(Dispatchers.IO) {
        flashcardDao.updateAudioMetadata(id, audioUrl, localAudioPath, isAudioReady)
    }

    suspend fun checkDuplicate(term: String): DuplicateCheckResult {
        val normalizedTerm = term.trim()
        if (normalizedTerm.isBlank()) return DuplicateCheckResult.NotDuplicate
        return if (flashcardDao.countByTerm(normalizedTerm) > 0) {
            DuplicateCheckResult.IsDuplicate(listOf(normalizedTerm))
        } else {
            DuplicateCheckResult.NotDuplicate
        }
    }

    suspend fun createFlashcard(
        term: String,
        definition: String,
        groupName: String,
        mediaUrl: String?,
        mediaType: MediaType,
        contributorId: String,
        passedId: String? = null
    ): Result<Flashcard> = withContext(Dispatchers.IO) {
        val normalizedTerm = term.trim()
        val normalizedDefinition = definition.trim()
        if (normalizedTerm.isBlank() || normalizedDefinition.isBlank()) {
            return@withContext Result.failure(Exception("يجب إدخال المصطلح والتعريف"))
        }
        if (flashcardDao.countByTerm(normalizedTerm) > 0) {
            return@withContext Result.failure(Exception("هذا المصطلح موجود بالفعل محليًا"))
        }
        val entity = FlashcardEntity(
            id = passedId ?: UUID.randomUUID().toString(),
            term = normalizedTerm,
            definition = normalizedDefinition,
            category = FlashcardCategory.ABA_THERAPY.name,
            groupName = groupName.trim(),
            mediaUrl = mediaUrl,
            mediaType = mediaType.name,
            contributor = contributorId,
            createdAt = System.currentTimeMillis(),
            isPendingSync = false
        )
        flashcardDao.insert(entity)
        Result.success(entity.toDomain())
    }

    suspend fun editFlashcard(
        id: String,
        term: String,
        definition: String,
        groupName: String,
        mediaUrl: String?,
        mediaType: MediaType
    ): Result<Flashcard> = withContext(Dispatchers.IO) {
        val existing = flashcardDao.getFlashcardById(id)
            ?: return@withContext Result.failure(Exception("Not found"))
        val updated = existing.copy(
            term = term.trim(),
            definition = definition.trim(),
            groupName = groupName.trim(),
            mediaUrl = mediaUrl,
            mediaType = mediaType.name
        )
        flashcardDao.insert(updated)
        Result.success(updated.toDomain())
    }

    suspend fun deleteFlashcard(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = flashcardDao.getFlashcardById(id)
        if (entity != null) flashcardDao.delete(entity)
        Result.success(Unit)
    }

    suspend fun getCategoryMastery(): List<CategoryMastery> = withContext(Dispatchers.IO) {
        flashcardDao.getCategoryMastery().map { row ->
            CategoryMastery(
                category = runCatching { FlashcardCategory.valueOf(row.category) }.getOrDefault(FlashcardCategory.ABA_THERAPY),
                total = row.total,
                archived = row.archived
            )
        }
    }

    suspend fun processReview(flashcard: Flashcard, result: SRSResult) = withContext(Dispatchers.IO) {
        val (newState, newEase, newInterval) = calculateNextSRS(flashcard, result)
        val nextReview = System.currentTimeMillis() + (newInterval * 86_400_000L)
        flashcardDao.updateReviewState(flashcard.id, newState.name, newEase, newInterval, nextReview)
    }

    suspend fun searchLocal(query: String): List<Flashcard> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()
        return flashcardDao.searchFlashcards(normalizedQuery).map { it.toDomain() }
    }

    suspend fun refreshFromServer() = Unit

    suspend fun syncFromServer(since: Long) = Unit

    suspend fun importFlashcards(
        flashcards: List<Flashcard>,
        contributorId: String
    ): Int = withContext(Dispatchers.IO) {
        var imported = 0
        flashcards.forEach { flashcard ->
            if (flashcardDao.countByTerm(flashcard.term.trim()) == 0) {
                flashcardDao.insert(
                    FlashcardEntity(
                        id = flashcard.id,
                        term = flashcard.term.trim(),
                        definition = flashcard.definition.trim(),
                        category = FlashcardCategory.ABA_THERAPY.name,
                        groupName = flashcard.groupName.trim(),
                        mediaUrl = flashcard.mediaUrl,
                        mediaType = flashcard.mediaType.name,
                        audioUrl = flashcard.audioUrl,
                        localAudioPath = flashcard.localAudioPath,
                        isAudioReady = flashcard.isAudioReady,
                        contributor = contributorId,
                        createdAt = System.currentTimeMillis(),
                        reviewState = flashcard.reviewState.name,
                        easeFactor = flashcard.easeFactor,
                        interval = flashcard.interval,
                        nextReviewDate = flashcard.nextReviewDate.time,
                        isOfflineCached = true,
                        isPendingSync = false
                    )
                )
                imported++
            }
        }
        imported
    }

    suspend fun exportGroupToCsv(groupName: String): String = withContext(Dispatchers.IO) {
        val cards = flashcardDao.getByGroupOnce(groupName)
        buildString {
            appendLine("term,definition,group,media_url,media_type,audio_url")
            cards.forEach { card ->
                appendLine(
                    listOf(
                        card.term,
                        card.definition,
                        card.groupName.ifBlank { groupName },
                        card.mediaUrl.orEmpty(),
                        card.mediaType,
                        card.audioUrl.orEmpty()
                    )
                        .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
                )
            }
        }
    }

    private fun calculateNextSRS(card: Flashcard, result: SRSResult): Triple<ReviewState, Float, Int> =
        when (result) {
            is SRSResult.Easy -> Triple(
                ReviewState.ARCHIVED,
                minOf(card.easeFactor + 0.15f, 2.5f),
                card.interval * 4
            )
            is SRSResult.Good -> Triple(
                ReviewState.REVIEW,
                card.easeFactor,
                (card.interval * card.easeFactor).toInt().coerceAtLeast(1)
            )
            is SRSResult.Hard -> Triple(
                ReviewState.REVIEW,
                maxOf(card.easeFactor - 0.15f, 1.3f),
                (card.interval * 1.2f).toInt().coerceAtLeast(1)
            )
            is SRSResult.Again -> Triple(
                ReviewState.LEARNING,
                maxOf(card.easeFactor - 0.2f, 1.3f),
                1
            )
        }
}

fun FlashcardEntity.toDomain() = Flashcard(
    id = id,
    term = term,
    definition = definition,
    category = runCatching { FlashcardCategory.valueOf(category) }.getOrDefault(FlashcardCategory.ABA_THERAPY),
    groupName = groupName,
    mediaUrl = mediaUrl,
    mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.NONE),
    audioUrl = audioUrl,
    localAudioPath = localAudioPath,
    isAudioReady = isAudioReady,
    contributor = contributor,
    createdAt = Date(createdAt),
    reviewState = ReviewState.valueOf(reviewState),
    easeFactor = easeFactor,
    interval = interval,
    nextReviewDate = Date(nextReviewDate),
    isOfflineCached = true
)
