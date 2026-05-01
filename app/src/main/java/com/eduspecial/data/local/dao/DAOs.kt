package com.eduspecial.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.eduspecial.data.local.entities.*
import kotlinx.coroutines.flow.Flow

data class CategoryMasteryRow(
    val category: String,
    val total: Int,
    val archived: Int
)

data class GroupProgressRow(
    val groupName: String,
    val total: Int,
    val archived: Int
)

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards ORDER BY createdAt DESC")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE groupName = :groupName ORDER BY createdAt DESC")
    fun getByGroup(groupName: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE groupName = :groupName ORDER BY createdAt DESC")
    suspend fun getByGroupOnce(groupName: String): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE reviewState IN ('NEW', 'LEARNING', 'REVIEW') ORDER BY nextReviewDate ASC")
    fun getStudyQueue(): Flow<List<FlashcardEntity>>

    @Query("""
        SELECT * FROM flashcards
        WHERE reviewState IN ('NEW', 'LEARNING', 'REVIEW')
          AND groupName IN (:groupNames)
        ORDER BY nextReviewDate ASC
    """)
    fun getStudyQueueForGroups(groupNames: List<String>): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getFlashcardById(id: String): FlashcardEntity?

    @Query("""
        SELECT * FROM flashcards
        WHERE LOWER(TRIM(definition)) = LOWER(TRIM(:definition))
          AND (
            (audioUrl IS NOT NULL AND TRIM(audioUrl) != '')
            OR (localAudioPath IS NOT NULL AND TRIM(localAudioPath) != '')
          )
        ORDER BY isAudioReady DESC, createdAt DESC
        LIMIT 1
    """)
    suspend fun findReusableAudioByDefinition(definition: String): FlashcardEntity?

    @Query("SELECT COUNT(*) FROM flashcards WHERE nextReviewDate <= :now AND reviewState IN ('NEW', 'LEARNING', 'REVIEW')")
    suspend fun getDueCount(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM flashcards WHERE LOWER(term) = LOWER(:term)")
    suspend fun countByTerm(term: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flashcard: FlashcardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(flashcards: List<FlashcardEntity>)

    @Update
    suspend fun update(flashcard: FlashcardEntity)

    @Query("UPDATE flashcards SET reviewState = :state, easeFactor = :easeFactor, interval = :interval, nextReviewDate = :nextReviewDate WHERE id = :id")
    suspend fun updateReviewState(id: String, state: String, easeFactor: Float, interval: Int, nextReviewDate: Long)

    @Query("UPDATE flashcards SET term = :term, definition = :definition, category = :category, groupName = :groupName, mediaUrl = :mediaUrl, mediaType = :mediaType WHERE id = :id")
    suspend fun updateContent(id: String, term: String, definition: String, category: String, groupName: String, mediaUrl: String?, mediaType: String)

    @Query("UPDATE flashcards SET audioUrl = :audioUrl, localAudioPath = :localAudioPath, isAudioReady = :isAudioReady WHERE id = :id")
    suspend fun updateAudioMetadata(id: String, audioUrl: String?, localAudioPath: String?, isAudioReady: Boolean)

    @Query("""
        SELECT * FROM flashcards
        WHERE LOWER(term) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(definition) LIKE '%' || LOWER(:query) || '%'
        ORDER BY
            CASE WHEN LOWER(term) LIKE LOWER(:query) || '%' THEN 0 ELSE 1 END,
            createdAt DESC
        LIMIT 30
    """)
    suspend fun searchFlashcards(query: String): List<FlashcardEntity>

    @Delete
    suspend fun delete(flashcard: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE category = :category AND isPendingSync = 0")
    suspend fun deleteByCategoryIfNotPending(category: String)

    @Query("DELETE FROM flashcards WHERE isPendingSync = 0")
    suspend fun deleteAllNotPending()

    @Query("SELECT category, COUNT(*) AS total, SUM(CASE WHEN reviewState = 'ARCHIVED' THEN 1 ELSE 0 END) AS archived FROM flashcards GROUP BY category")
    suspend fun getCategoryMastery(): List<CategoryMasteryRow>

    @Query("""
        SELECT groupName, COUNT(*) AS total, SUM(CASE WHEN reviewState = 'ARCHIVED' THEN 1 ELSE 0 END) AS archived
        FROM flashcards
        WHERE TRIM(groupName) != ''
        GROUP BY groupName
        ORDER BY groupName COLLATE NOCASE ASC
    """)
    suspend fun getGroupProgress(): List<GroupProgressRow>

    @Query("""
        SELECT DISTINCT groupName FROM flashcards
        WHERE TRIM(groupName) != ''
        ORDER BY groupName COLLATE NOCASE ASC
    """)
    fun getGroupNames(): Flow<List<String>>

    @Query("SELECT * FROM flashcards ORDER BY createdAt DESC")
    fun getFlashcardsPaged(): PagingSource<Int, FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE groupName = :groupName ORDER BY createdAt DESC")
    fun getFlashcardsPagedByGroup(groupName: String): PagingSource<Int, FlashcardEntity>
}

@Dao
interface QADao {
    @Query("SELECT * FROM qa_questions ORDER BY createdAt DESC")
    fun getAllQuestions(): Flow<List<QAQuestionEntity>>

    @Query("SELECT * FROM qa_answers")
    fun getAllAnswers(): Flow<List<QAAnswerEntity>>

    @Query("SELECT * FROM qa_questions WHERE id = :id")
    suspend fun getQuestionById(id: String): QAQuestionEntity?

    @Query("SELECT * FROM qa_questions WHERE isAnswered = 0 ORDER BY upvotes DESC")
    fun getUnansweredQuestions(): Flow<List<QAQuestionEntity>>

    @Query("SELECT * FROM qa_answers WHERE questionId = :questionId ORDER BY isAccepted DESC, upvotes DESC")
    fun getAnswersForQuestion(questionId: String): Flow<List<QAAnswerEntity>>

    @Query("SELECT * FROM qa_answers WHERE id = :id")
    suspend fun getAnswerById(id: String): QAAnswerEntity?

    @Query("SELECT COUNT(*) FROM qa_questions WHERE LOWER(question) = LOWER(:question)")
    suspend fun countByQuestion(question: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: QAQuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QAQuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswer(answer: QAAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswers(answers: List<QAAnswerEntity>)

    @Query("UPDATE qa_questions SET upvotes = MAX(upvotes + :delta, 0) WHERE id = :id")
    suspend fun updateQuestionUpvotes(id: String, delta: Int)

    @Query("UPDATE qa_answers SET upvotes = MAX(upvotes + :delta, 0) WHERE id = :id")
    suspend fun updateAnswerUpvotes(id: String, delta: Int)

    @Query("UPDATE qa_answers SET isAccepted = 1 WHERE id = :id")
    suspend fun acceptAnswer(id: String)

    @Query("UPDATE qa_questions SET isAnswered = 1 WHERE id = :questionId")
    suspend fun markQuestionAnswered(questionId: String)

    @Query("UPDATE qa_questions SET isAnswered = 0 WHERE id = :questionId")
    suspend fun markQuestionUnanswered(questionId: String)

    @Query("UPDATE qa_questions SET question = :question, category = :category, hashtags = :hashtags WHERE id = :id")
    suspend fun updateQuestion(id: String, question: String, category: String, hashtags: String)

    @Query("UPDATE qa_answers SET content = :content WHERE id = :id")
    suspend fun updateAnswer(id: String, content: String)

    @Query("DELETE FROM qa_questions WHERE id = :id")
    suspend fun deleteQuestionById(id: String)

    @Query("DELETE FROM qa_answers WHERE id = :id")
    suspend fun deleteAnswerById(id: String)

    @Query("DELETE FROM qa_answers WHERE parentAnswerId = :answerId")
    suspend fun deleteRepliesForAnswer(answerId: String)

    @Query("DELETE FROM qa_answers WHERE questionId = :questionId")
    suspend fun deleteAnswersForQuestion(questionId: String)

    @Query("""
        SELECT * FROM qa_questions
        WHERE LOWER(question) LIKE '%' || LOWER(:query) || '%'
        ORDER BY upvotes DESC, createdAt DESC
        LIMIT 20
    """)
    fun searchQuestionsFlow(query: String): Flow<List<QAQuestionEntity>>
}

@Dao
interface PendingSubmissionDao {
    @Query("SELECT * FROM pending_submissions ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingSubmissionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(submission: PendingSubmissionEntity)

    @Delete
    suspend fun delete(submission: PendingSubmissionEntity)

    @Query("DELETE FROM pending_submissions WHERE type = :type AND payload LIKE '%' || :match || '%'")
    suspend fun deleteByTypeAndPayloadMatch(type: String, match: String)

    @Query("UPDATE pending_submissions SET retryCount = retryCount + 1 WHERE localId = :localId")
    suspend fun incrementRetry(localId: String)

    @Query("DELETE FROM pending_submissions WHERE retryCount >= 5")
    suspend fun deleteFailedSubmissions()

    @Query("SELECT COUNT(*) FROM pending_submissions")
    suspend fun countPending(): Int
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE itemType = :type ORDER BY createdAt DESC")
    fun getBookmarksByType(type: String): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE itemId = :itemId AND itemType = :type)")
    fun isBookmarked(itemId: String, type: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE itemId = :itemId AND itemType = :type")
    suspend fun delete(itemId: String, type: String)

    @Query("DELETE FROM bookmarks WHERE itemId IN (:itemIds)")
    suspend fun deleteOrphans(itemIds: List<String>)
}

@Dao
interface AnalyticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: DailyReviewLogEntity)

    @Query("UPDATE daily_review_logs SET reviewCount = reviewCount + :delta, archivedCount = archivedCount + :archived WHERE dayEpoch = :dayEpoch")
    suspend fun incrementLog(dayEpoch: Long, delta: Int, archived: Int)

    @Query("SELECT * FROM daily_review_logs WHERE dayEpoch >= :fromDay ORDER BY dayEpoch ASC")
    suspend fun getLogsFrom(fromDay: Long): List<DailyReviewLogEntity>
    
    @Query("SELECT * FROM daily_review_logs ORDER BY dayEpoch DESC LIMIT 7")
    suspend fun getLast7Days(): List<DailyReviewLogEntity>

    @Query("SELECT * FROM daily_review_logs WHERE dayEpoch >= :sinceDayEpoch ORDER BY dayEpoch ASC")
    suspend fun getLogsSince(sinceDayEpoch: Long): List<DailyReviewLogEntity>
}
