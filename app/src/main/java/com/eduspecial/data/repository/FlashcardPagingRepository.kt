package com.eduspecial.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.eduspecial.data.local.dao.FlashcardDao
import com.eduspecial.domain.model.Flashcard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlashcardPagingRepository @Inject constructor(
    private val flashcardDao: FlashcardDao
) {
    companion object {
        const val PAGE_SIZE = 20
        const val PREFETCH_DISTANCE = 5
    }

    fun getFlashcardsPaged(groupName: String? = null): Flow<PagingData<Flashcard>> {
        val pagingSourceFactory = if (groupName != null) {
            { flashcardDao.getFlashcardsPagedByGroup(groupName) }
        } else {
            { flashcardDao.getFlashcardsPaged() }
        }

        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false,
                initialLoadSize = PAGE_SIZE * 2
            ),
            pagingSourceFactory = pagingSourceFactory
        ).flow.map { pagingData ->
            pagingData.map { entity -> entity.toDomain() }
        }
    }
}
