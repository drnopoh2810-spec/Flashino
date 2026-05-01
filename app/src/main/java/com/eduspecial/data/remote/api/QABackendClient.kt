package com.eduspecial.data.remote.api

import android.util.Log
import com.eduspecial.BuildConfig
import com.eduspecial.data.repository.AuthRepository
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class QABackendException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

data class BackendQAQuestion(
    val id: String,
    val question: String,
    val category: String,
    val contributor: String,
    val contributorName: String,
    val contributorVerified: Boolean,
    val contributorAvatarUrl: String?,
    val upvotes: Int,
    val createdAt: Long,
    val isAnswered: Boolean,
    val hashtags: List<String>
)

data class BackendQAAnswer(
    val id: String,
    val questionId: String,
    val content: String,
    val contributor: String,
    val contributorName: String,
    val contributorVerified: Boolean,
    val contributorAvatarUrl: String?,
    val parentAnswerId: String?,
    val upvotes: Int,
    val isAccepted: Boolean,
    val createdAt: Long
)

data class QAFeedPayload(
    val questions: List<BackendQAQuestion>,
    val answers: List<BackendQAAnswer>
)

data class VoteTogglePayload(
    val liked: Boolean,
    val upvotes: Int
)

@Singleton
class QABackendClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "QABackendClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun fetchFeed(unansweredOnly: Boolean = false): QAFeedPayload? = withContext(Dispatchers.IO) {
        val url = buildUrl("/v1/qa/feed", if (unansweredOnly) mapOf("unanswered_only" to "true") else emptyMap())
            ?: return@withContext null
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()
        executeJsonObject(request)?.let(::parseFeed)
    }

    suspend fun createQuestion(
        id: String,
        question: String,
        category: String,
        hashtags: List<String>
    ): BackendQAQuestion? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("id", id)
            .put("question", question)
            .put("category", category)
            .put("hashtags", JSONArray(hashtags))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        executeAuthorizedObject("/v1/qa/questions", "POST", body)?.let(::parseQuestion)
    }

    suspend fun updateQuestion(
        id: String,
        question: String,
        hashtags: List<String>
    ): BackendQAQuestion? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("question", question)
            .put("hashtags", JSONArray(hashtags))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        executeAuthorizedObject("/v1/qa/questions/$id", "PATCH", body)?.let(::parseQuestion)
    }

    suspend fun deleteQuestion(id: String): Boolean = withContext(Dispatchers.IO) {
        executeAuthorizedObject("/v1/qa/questions/$id", "DELETE", null) != null
    }

    suspend fun createAnswer(
        id: String,
        questionId: String,
        content: String,
        parentAnswerId: String?
    ): BackendQAAnswer? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("id", id)
            .put("question_id", questionId)
            .put("content", content)
            .put("parent_answer_id", parentAnswerId)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        executeAuthorizedObject("/v1/qa/answers", "POST", body)?.let(::parseAnswer)
    }

    suspend fun updateAnswer(id: String, content: String): BackendQAAnswer? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("content", content)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        executeAuthorizedObject("/v1/qa/answers/$id", "PATCH", body)?.let(::parseAnswer)
    }

    suspend fun deleteAnswer(id: String): Boolean = withContext(Dispatchers.IO) {
        executeAuthorizedObject("/v1/qa/answers/$id", "DELETE", null) != null
    }

    suspend fun acceptAnswer(answerId: String, questionId: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("question_id", questionId)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        executeAuthorizedObject("/v1/qa/answers/$answerId/accept", "POST", body) != null
    }

    suspend fun toggleQuestionVote(questionId: String): VoteTogglePayload? = withContext(Dispatchers.IO) {
        executeAuthorizedObject("/v1/qa/questions/$questionId/vote", "POST", emptyJsonBody())?.let(::parseVote)
    }

    suspend fun toggleAnswerVote(answerId: String): VoteTogglePayload? = withContext(Dispatchers.IO) {
        executeAuthorizedObject("/v1/qa/answers/$answerId/vote", "POST", emptyJsonBody())?.let(::parseVote)
    }

    private fun emptyJsonBody() = "{}".toRequestBody(JSON_MEDIA_TYPE)

    private suspend fun executeAuthorizedObject(
        path: String,
        method: String,
        body: okhttp3.RequestBody?
    ): JSONObject? = withContext(Dispatchers.IO) {
        val url = buildUrl(path, emptyMap()) ?: return@withContext null
        val token = authRepository.getIdToken()
        if (token.isNullOrBlank()) {
            Log.w(TAG, "Skipping backend request without Firebase token for $path")
            return@withContext null
        }
        val builder = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")

        when (method) {
            "POST" -> builder.post(body ?: emptyJsonBody())
            "PATCH" -> builder.patch(body ?: emptyJsonBody())
            "DELETE" -> builder.delete(body)
            else -> builder.get()
        }

        executeJsonObject(builder.build())
    }

    private suspend fun executeJsonObject(request: Request): JSONObject? = withContext(Dispatchers.IO) {
        okHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                val message = raw.ifBlank { "HTTP ${response.code}" }
                Log.w(TAG, "QA backend request failed code=${response.code} path=${request.url.encodedPath} body=$message")
                throw QABackendException(response.code, message)
            }
            if (raw.isBlank()) return@use JSONObject()
            JSONObject(raw)
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): String? {
        val baseUrl = BuildConfig.AUDIO_BACKEND_BASE_URL.trim().takeIf { it.isNotBlank() } ?: return null
        val encodedQuery = query.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { entry ->
                "${URLEncoder.encode(entry.key, Charsets.UTF_8.name())}=${URLEncoder.encode(entry.value, Charsets.UTF_8.name())}"
            }
        return buildString {
            append(baseUrl.trimEnd('/'))
            append(path)
            if (encodedQuery.isNotBlank()) {
                append('?')
                append(encodedQuery)
            }
        }
    }

    private fun parseFeed(payload: JSONObject): QAFeedPayload {
        val questions = payload.optJSONArray("questions") ?: JSONArray()
        val answers = payload.optJSONArray("answers") ?: JSONArray()
        return QAFeedPayload(
            questions = buildList {
                for (index in 0 until questions.length()) {
                    add(parseQuestion(questions.getJSONObject(index)))
                }
            },
            answers = buildList {
                for (index in 0 until answers.length()) {
                    add(parseAnswer(answers.getJSONObject(index)))
                }
            }
        )
    }

    private fun parseQuestion(payload: JSONObject): BackendQAQuestion {
        val hashtagsArray = payload.optJSONArray("hashtags") ?: JSONArray()
        return BackendQAQuestion(
            id = payload.optString("id"),
            question = payload.optString("question"),
            category = payload.optString("category", "ABA_THERAPY"),
            contributor = payload.optString("contributor"),
            contributorName = payload.optString("contributor_name").ifBlank { payload.optString("contributor") },
            contributorVerified = payload.optBoolean("contributor_verified"),
            contributorAvatarUrl = payload.optString("contributor_avatar_url").ifBlank { null },
            upvotes = payload.optInt("upvotes", 0),
            createdAt = payload.optLong("created_at", System.currentTimeMillis()),
            isAnswered = payload.optBoolean("is_answered", false),
            hashtags = buildList {
                for (index in 0 until hashtagsArray.length()) {
                    val tag = hashtagsArray.optString(index).trim()
                    if (tag.isNotBlank()) add(tag)
                }
            }
        )
    }

    private fun parseAnswer(payload: JSONObject): BackendQAAnswer {
        return BackendQAAnswer(
            id = payload.optString("id"),
            questionId = payload.optString("question_id"),
            content = payload.optString("content"),
            contributor = payload.optString("contributor"),
            contributorName = payload.optString("contributor_name").ifBlank { payload.optString("contributor") },
            contributorVerified = payload.optBoolean("contributor_verified"),
            contributorAvatarUrl = payload.optString("contributor_avatar_url").ifBlank { null },
            parentAnswerId = payload.optString("parent_answer_id").ifBlank { null },
            upvotes = payload.optInt("upvotes", 0),
            isAccepted = payload.optBoolean("is_accepted", false),
            createdAt = payload.optLong("created_at", System.currentTimeMillis())
        )
    }

    private fun parseVote(payload: JSONObject): VoteTogglePayload {
        return VoteTogglePayload(
            liked = payload.optBoolean("liked"),
            upvotes = payload.optInt("upvotes", 0)
        )
    }
}
