package com.eduspecial.data.remote.search

import android.util.Log
import com.algolia.search.client.ClientSearch
import com.algolia.search.model.APIKey
import com.algolia.search.model.ApplicationID
import com.algolia.search.model.IndexName
import com.algolia.search.model.search.Query
import com.eduspecial.data.repository.ConfigRepository
import com.eduspecial.domain.model.Flashcard
import com.eduspecial.domain.model.FlashcardCategory
import com.eduspecial.domain.model.MediaType
import com.eduspecial.domain.model.QAQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AlgoliaSearchService @Inject constructor(
    private val configRepository: ConfigRepository
) {
    
    companion object {
        private const val TAG = "AlgoliaSearchService"
        private const val FLASHCARDS_INDEX = "flashcards"
        private const val QUESTIONS_INDEX = "questions"
    }
    
    private var client: ClientSearch? = null
    private var isInitialized = false
    private val initializationMutex = Mutex()
    
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val config = configRepository.getAlgoliaConfig()
                
                if (config.appId.isEmpty() || config.searchKey.isEmpty()) {
                    Log.w(TAG, "⚠️ Algolia config is empty, search will be disabled")
                    return@withContext false
                }
                
                client = ClientSearch(
                    applicationID = ApplicationID(config.appId),
                    apiKey = APIKey(config.searchKey)
                )
                
                isInitialized = true
                Log.d(TAG, "✅ Algolia Search initialized successfully")
                Log.d(TAG, "📊 App ID: ${config.appId}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize Algolia Search: ${e.message}")
                false
            }
        }
    }

    private suspend fun ensureInitialized(): Boolean {
        if (isInitialized && client != null) return true
        return initializationMutex.withLock {
            if (isInitialized && client != null) true else initialize()
        }
    }
    
    suspend fun searchFlashcards(
        query: String,
        category: FlashcardCategory? = null,
        limit: Int = 20
    ): Result<List<Flashcard>> {
        if (!ensureInitialized()) {
            Log.w(TAG, "🔍 Algolia not initialized, returning empty results")
            return Result.success(emptyList())
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val index = client!!.initIndex(IndexName(FLASHCARDS_INDEX))
                
                val searchQuery = Query(
                    query = query,
                    hitsPerPage = limit
                ).apply {
                    // Add category filter if specified
                    category?.let { 
                        filters = "category:${it.name}"
                    }
                }
                
                val response = index.search(searchQuery)
                val flashcards = response.hits.mapNotNull { hit ->
                    try {
                        parseFlashcardHit(hit.json)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse flashcard hit: ${e.message}")
                        null
                    }
                }
                
                Log.d(TAG, "🔍 Algolia search for '$query': ${flashcards.size} results")
                Result.success(flashcards)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Algolia flashcard search failed: ${e.message}")
                Result.failure(e)
            }
        }
    }
    
    suspend fun searchQuestions(
        query: String,
        category: FlashcardCategory? = null,
        unansweredOnly: Boolean = false,
        limit: Int = 20
    ): Result<List<QAQuestion>> {
        if (!ensureInitialized()) {
            Log.w(TAG, "🔍 Algolia not initialized, returning empty results")
            return Result.success(emptyList())
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val index = client!!.initIndex(IndexName(QUESTIONS_INDEX))
                
                val filters = mutableListOf<String>()
                category?.let { filters.add("category:${it.name}") }
                if (unansweredOnly) filters.add("isAnswered:false")
                
                val searchQuery = Query(
                    query = query,
                    hitsPerPage = limit
                ).apply {
                    if (filters.isNotEmpty()) {
                        this.filters = filters.joinToString(" AND ")
                    }
                }
                
                val response = index.search(searchQuery)
                val questions = response.hits.mapNotNull { hit ->
                    try {
                        parseQuestionHit(hit.json)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse question hit: ${e.message}")
                        null
                    }
                }
                
                Log.d(TAG, "🔍 Algolia question search for '$query': ${questions.size} results")
                Result.success(questions)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Algolia question search failed: ${e.message}")
                Result.failure(e)
            }
        }
    }
    
    suspend fun getSuggestions(query: String, limit: Int = 5): List<String> {
        if (query.length < 2 || !ensureInitialized()) {
            return emptyList()
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val index = client!!.initIndex(IndexName(FLASHCARDS_INDEX))
                val searchQuery = Query(
                    query = query,
                    hitsPerPage = limit
                )
                
                val response = index.search(searchQuery)
                response.hits.mapNotNull { hit ->
                    try {
                        val json = Json.parseToJsonElement(hit.json.toString())
                        json.jsonObject["term"]?.jsonPrimitive?.content
                    } catch (e: Exception) {
                        null
                    }
                }.distinct().take(limit)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get suggestions: ${e.message}")
                emptyList()
            }
        }
    }
    
    private fun parseFlashcardHit(json: kotlinx.serialization.json.JsonElement): Flashcard {
        val jsonObj = json.jsonObject
        
        return Flashcard(
            id = jsonObj["objectID"]?.jsonPrimitive?.content ?: "",
            term = jsonObj["term"]?.jsonPrimitive?.content ?: "",
            definition = jsonObj["definition"]?.jsonPrimitive?.content ?: "",
            category = try {
                FlashcardCategory.valueOf(
                    jsonObj["category"]?.jsonPrimitive?.content ?: "ABA_THERAPY"
                )
            } catch (e: Exception) { FlashcardCategory.ABA_THERAPY },
            mediaUrl = jsonObj["mediaUrl"]?.jsonPrimitive?.content,
            mediaType = try {
                MediaType.valueOf(
                    jsonObj["mediaType"]?.jsonPrimitive?.content ?: "NONE"
                )
            } catch (e: Exception) { MediaType.NONE },
            contributor = jsonObj["contributor"]?.jsonPrimitive?.content ?: "",
            createdAt = Date(jsonObj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis())
        )
    }
    
    private fun parseQuestionHit(json: kotlinx.serialization.json.JsonElement): QAQuestion {
        val jsonObj = json.jsonObject
        
        return QAQuestion(
            id = jsonObj["objectID"]?.jsonPrimitive?.content ?: "",
            question = jsonObj["question"]?.jsonPrimitive?.content ?: "",
            category = try {
                FlashcardCategory.valueOf(
                    jsonObj["category"]?.jsonPrimitive?.content ?: "ABA_THERAPY"
                )
            } catch (e: Exception) { FlashcardCategory.ABA_THERAPY },
            contributor = jsonObj["contributor"]?.jsonPrimitive?.content ?: "",
            upvotes = jsonObj["upvotes"]?.jsonPrimitive?.int ?: 0,
            createdAt = Date(jsonObj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis()),
            isAnswered = jsonObj["isAnswered"]?.jsonPrimitive?.boolean ?: false,
            hashtags = try {
                jsonObj["tags"]?.jsonArray?.map { 
                    it.jsonPrimitive.content 
                } ?: emptyList()
            } catch (e: Exception) { emptyList() },
            answers = emptyList() // Answers loaded separately
        )
    }
    
    fun isAvailable(): Boolean = isInitialized && client != null
}

@Serializable
data class AlgoliaFlashcardHit(
    val objectID: String,
    val term: String,
    val definition: String,
    val category: String,
    val mediaUrl: String? = null,
    val mediaType: String = "NONE",
    val contributor: String,
    val createdAt: Long
)

@Serializable
data class AlgoliaQuestionHit(
    val objectID: String,
    val question: String,
    val category: String,
    val contributor: String,
    val upvotes: Int = 0,
    val createdAt: Long,
    val isAnswered: Boolean = false,
    val tags: List<String> = emptyList()
)
