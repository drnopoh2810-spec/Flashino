package com.eduspecial.data.repository

import com.eduspecial.data.local.dao.FlashcardDao
import com.eduspecial.data.local.dao.BookmarkDao
import com.eduspecial.data.local.dao.QADao
import com.eduspecial.data.local.entities.BookmarkEntity
import com.eduspecial.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val flashcardDao: FlashcardDao,
    private val qaDao: QADao
) {
    fun getAllBookmarks(): Flow<BookmarkCollection> {
        return combine(
            bookmarkDao.getBookmarksByType("FLASHCARD"),
            bookmarkDao.getBookmarksByType("QUESTION"),
            flashcardDao.getAllFlashcards(),
            qaDao.getAllQuestions()
        ) { flashcardBookmarks, questionBookmarks, flashcards, questions ->
            val flashcardsById = flashcards.associateBy { it.id }
            val questionsById = questions.associateBy { it.id }

            BookmarkCollection(
                flashcards = flashcardBookmarks.mapNotNull { bookmark ->
                    flashcardsById[bookmark.itemId]?.toDomain()
                },
                questions = questionBookmarks.mapNotNull { bookmark ->
                    questionsById[bookmark.itemId]?.toDomain()
                }
            )
        }
    }

    fun getBookmarkedFlashcardIds(): Flow<Set<String>> =
        bookmarkDao.getBookmarksByType("FLASHCARD").map { list -> list.map { it.itemId }.toSet() }

    fun getBookmarkedQuestionIds(): Flow<Set<String>> =
        bookmarkDao.getBookmarksByType("QUESTION").map { list -> list.map { it.itemId }.toSet() }

    fun isBookmarked(itemId: String, type: BookmarkType): Flow<Boolean> =
        bookmarkDao.isBookmarked(itemId, type.name)

    suspend fun toggle(itemId: String, type: BookmarkType): Boolean {
        val currentlyBookmarked = bookmarkDao.isBookmarked(itemId, type.name).first()
        return if (currentlyBookmarked) {
            bookmarkDao.delete(itemId, type.name)
            false
        } else {
            bookmarkDao.insert(BookmarkEntity(itemId = itemId, itemType = type.name))
            true
        }
    }

    suspend fun removeOrphans(validIds: List<String>) {
        if (validIds.isNotEmpty()) {
            // Get all bookmarked IDs and delete those not in validIds
            val allBookmarks = bookmarkDao.getAllBookmarks().first()
            val orphanIds = allBookmarks.map { it.itemId }.filter { it !in validIds }
            if (orphanIds.isNotEmpty()) {
                bookmarkDao.deleteOrphans(orphanIds)
            }
        }
    }
}
