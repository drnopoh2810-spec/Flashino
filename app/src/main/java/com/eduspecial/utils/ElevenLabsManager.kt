package com.eduspecial.utils

import android.content.Context
import android.util.Log
import com.eduspecial.data.repository.ConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class ElevenLabsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ConfigRepository
) {
    companion object {
        private const val TAG = "ElevenLabsManager"
        private const val VOICE_ID = "21m00Tcm4TlvDq8ikWAM"
        private const val MODEL_ID = "eleven_flash_v2_5"
        private const val MIN_AUDIO_FILE_SIZE_BYTES = 512L
    }

    private var currentKeyIndex = 0
    private val sessionBlacklistedKeys = linkedSetOf<String>()

    // Use a dedicated client here so ElevenLabs failures do not inherit app API retry delays.
    private val elevenLabsClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun generateSpeech(text: String): File? = withContext(Dispatchers.IO) {
        val keys = configRepository.getElevenLabsKeys().filter { it.isNotBlank() }
        if (keys.isEmpty()) return@withContext null
        val usableKeys = keys.filterNot(sessionBlacklistedKeys::contains)
        if (usableKeys.isEmpty()) {
            Log.w(TAG, "All ElevenLabs keys are blacklisted for this session, skipping remote TTS")
            return@withContext null
        }

        val cacheFile = File(context.cacheDir, "tts_${text.hashCode()}.mp3")
        if (cacheFile.exists()) {
            if (cacheFile.length() >= MIN_AUDIO_FILE_SIZE_BYTES) {
                return@withContext cacheFile
            }
            runCatching { cacheFile.delete() }
        }

        var attempts = 0
        while (attempts < usableKeys.size) {
            val keyIndex = (currentKeyIndex + attempts) % usableKeys.size
            val key = usableKeys[keyIndex]
            Log.d(TAG, "Trying ElevenLabs with key index: $keyIndex")

            if (tryWithKey(text, key, cacheFile)) {
                currentKeyIndex = keyIndex
                return@withContext cacheFile
            }
            attempts++
        }

        Log.e(TAG, "All ElevenLabs keys exhausted or failed.")
        null
    }

    private fun tryWithKey(text: String, apiKey: String, outputFile: File): Boolean {
        val url = "https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID?output_format=mp3_44100_128"

        val json = JSONObject().apply {
            put("text", text)
            put("model_id", MODEL_ID)
            put(
                "voice_settings",
                JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                }
            )
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("xi-api-key", apiKey)
                .addHeader("Accept", "audio/mpeg")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            elevenLabsClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.take(250)
                    Log.w(TAG, "Key failed with code: ${response.code} body=$errorBody")
                    if (response.code == 401 || response.code == 402) {
                        sessionBlacklistedKeys += apiKey
                    }
                    return false
                }

                val body = response.body ?: return false
                body.byteStream().use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val isValidAudio = outputFile.exists() && outputFile.length() >= MIN_AUDIO_FILE_SIZE_BYTES
                if (!isValidAudio) {
                    runCatching { outputFile.delete() }
                    Log.w(TAG, "ElevenLabs returned an invalid audio file")
                }
                isValidAudio
            }
        } catch (e: Exception) {
            runCatching { outputFile.delete() }
            Log.e(TAG, "Request failed", e)
            false
        }
    }
}
