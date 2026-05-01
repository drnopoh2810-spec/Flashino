package com.eduspecial.data.remote.api

import android.util.Log
import com.eduspecial.BuildConfig
import com.eduspecial.data.repository.AuthRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class AudioRequestKind(val wireValue: String) {
    TERM("term"),
    DEFINITION("definition")
}

data class ResolvedAudio(
    val audioUrl: String,
    val publicId: String,
    val source: String
)

@Singleton
class AudioBackendClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "AudioBackendClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun resolveAudio(text: String, kind: AudioRequestKind): ResolvedAudio? = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.AUDIO_BACKEND_BASE_URL.trim().takeIf { it.isNotBlank() } ?: return@withContext null
        val body = JSONObject()
            .put("text", text.trim())
            .put("kind", kind.wireValue)
            .put("language", "en")
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val token = authRepository.getIdToken()
        val builder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/audio/resolve")
            .post(body)
            .addHeader("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }

        runCatching {
            okHttpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Audio backend resolve failed code=${response.code}")
                    return@use null
                }
                val payload = JSONObject(response.body?.string().orEmpty())
                val audioUrl = payload.optString("audio_url").trim()
                val publicId = payload.optString("public_id").trim()
                if (audioUrl.isBlank() || publicId.isBlank()) return@use null
                ResolvedAudio(
                    audioUrl = audioUrl,
                    publicId = publicId,
                    source = payload.optString("source", "cloudinary")
                )
            }
        }.onFailure {
            Log.w(TAG, "Audio backend request failed", it)
        }.getOrNull()
    }

    suspend fun downloadAudio(url: String, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            targetFile.parentFile?.mkdirs()
            okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val bytes = response.body?.bytes() ?: return@use false
                if (bytes.isEmpty()) return@use false
                targetFile.writeBytes(bytes)
                true
            }
        }.onFailure {
            Log.w(TAG, "Audio download failed for url=$url", it)
        }.getOrDefault(false)
    }
}
