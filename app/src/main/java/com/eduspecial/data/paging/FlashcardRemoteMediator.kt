package com.eduspecial.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.eduspecial.data.local.dao.FlashcardDao
import com.eduspecial.data.local.entities.FlashcardEntity
import com.eduspecial.data.remote.api.SupabaseFlashcardRow
import com.eduspecial.data.remote.api.SupabaseRestService
import com.eduspecial.utils.CircuitBreaker
import com.eduspecial.utils.CircuitOpenException
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalPagingApi::class)
class FlashcardRemoteMediator(
    private val category: String?,
    private val supabaseRestService: SupabaseRestService,
    private val flashcardDao: FlashcardDao,
    private val circuitBreaker: CircuitBreaker
) : RemoteMediator<Int, FlashcardEntity>() {
    private var currentPage = 0

    override suspend fun initialize() = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, FlashcardEntity>
    ): MediatorResult {
        return try {
            val apiPage = when (loadType) {
                LoadType.REFRESH -> 1
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> currentPage + 1
            }
            val limit = state.config.pageSize
            val offset = (apiPage - 1) * limit
            val params = linkedMapOf(
                "select" to "*",
                "order" to "created_at.desc",
                "limit" to limit.toString(),
                "offset" to offset.toString()
            )
            if (!category.isNullOrBlank()) {
                params["category"] = "eq.$category"
            }

            val apiResponse = supabaseRestService.getFlashcards(params)
            val rows = apiResponse.takeIf { it.isSuccessful }?.body()
            if (rows != null) {
                if (loadType == LoadType.REFRESH) {
                    currentPage = 0
                    if (category != null) flashcardDao.deleteByCategoryIfNotPending(category)
                    else flashcardDao.deleteAllNotPending()
                }

                val entities = rows.map { it.toEntity() }
                flashcardDao.insertAll(entities)
                currentPage = apiPage
                return MediatorResult.Success(endOfPaginationReached = entities.size < limit)
            }

            MediatorResult.Error(IOException("Supabase flashcards unavailable"))
        } catch (e: CircuitOpenException) {
            MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}

private fun SupabaseFlashcardRow.toEntity() = FlashcardEntity(
    id = id,
    term = term,
    definition = definition,
    category = category,
    mediaUrl = mediaUrl,
    mediaType = mediaType,
    contributor = userId,
    createdAt = runCatching { createdAt?.let(Instant::parse)?.toEpochMilli() }.getOrNull()
        ?: System.currentTimeMillis(),
    reviewState = "NEW",
    isPendingSync = false
)
