package com.eduspecial.utils

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.eduspecial.data.remote.api.AudioBackendClient
import com.eduspecial.data.remote.api.AudioRequestKind
import com.eduspecial.data.remote.api.CloudinaryService
import com.eduspecial.data.repository.FlashcardRepository
import com.eduspecial.domain.model.Flashcard
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class TtsManager constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val flashcardRepository: FlashcardRepository,
    private val cloudinaryService: CloudinaryService,
    private val audioBackendClient: AudioBackendClient
) {
    companion object {
        private const val TAG = "TtsManager"
    }

    enum class TtsState { INITIALIZING, READY, SPEAKING, ERROR, UNAVAILABLE }

    private val _state = MutableStateFlow(TtsState.INITIALIZING)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var systemTts: TextToSpeech? = null
    private var isSystemTtsReady = false
    private var mediaPlayer: MediaPlayer? = null

    init {
        setupSystemTts()
    }

    private fun setupSystemTts() {
        systemTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                systemTts?.setLanguage(Locale.US)
                systemTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = TtsState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = TtsState.READY
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = TtsState.ERROR
                    }
                })
                isSystemTtsReady = true
                _state.value = TtsState.READY
            } else {
                _state.value = TtsState.UNAVAILABLE
            }
        }
    }

    fun speakTerm(term: String) {
        val normalizedTerm = term.trim()
        if (normalizedTerm.isBlank()) return

        stop()
        scope.launch {
            if (containsArabicCharacters(normalizedTerm)) {
                val usedSystem = speakViaSystem(normalizedTerm, isEnglish = false)
                if (!usedSystem) {
                    _state.value = TtsState.UNAVAILABLE
                }
                return@launch
            }

            val publicId = DefinitionAudioFingerprint.buildTermPublicId(normalizedTerm)
            val cacheFile = File(
                context.filesDir,
                "tts_cache/term_${DefinitionAudioFingerprint.buildTermCacheKey(normalizedTerm)}.mp3"
            )

            if (cacheFile.exists() && cacheFile.length() > 0L && playLocalFile(cacheFile.absolutePath)) {
                return@launch
            }

            val resolved = if (networkMonitor.isCurrentlyOnline()) {
                audioBackendClient.resolveAudio(normalizedTerm, AudioRequestKind.TERM)
            } else {
                null
            }
            if (resolved != null) {
                if (audioBackendClient.downloadAudio(resolved.audioUrl, cacheFile) &&
                    playLocalFile(cacheFile.absolutePath)
                ) {
                    return@launch
                }
                if (playRemoteAudio(resolved.audioUrl)) return@launch
            }

            val reusableUrl = if (networkMonitor.isCurrentlyOnline()) {
                cloudinaryService.findAudioUrl(publicId)
            } else {
                null
            }
            if (!reusableUrl.isNullOrBlank()) {
                Log.d(TAG, "Playing Cloudinary term audio for term=$normalizedTerm")
                if (audioBackendClient.downloadAudio(reusableUrl, cacheFile) &&
                    playLocalFile(cacheFile.absolutePath)
                ) {
                    return@launch
                }
                if (playRemoteAudio(reusableUrl)) return@launch
            }

            val usedLocal = playLocalFile(cacheFile.absolutePath)
            val usedSystem = if (!usedLocal) speakViaSystem(normalizedTerm, isEnglish = true) else true
            if (!usedLocal && !usedSystem) {
                _state.value = TtsState.UNAVAILABLE
            }
        }
    }

    fun speakDefinition(definition: String) {
        speakText(definition, isEnglish = false)
    }

    fun speakFlashcardDefinition(flashcard: Flashcard) {
        val definition = flashcard.definition.trim()
        if (definition.isBlank()) return
        val containsArabic = containsArabicCharacters(definition)

        stop()
        scope.launch {
            if (containsArabic) {
                val usedSystem = speakViaSystem(definition, isEnglish = false)
                if (!usedSystem) {
                    _state.value = TtsState.UNAVAILABLE
                }
                return@launch
            }

            if (playLocalAudio(flashcard.localAudioPath)) return@launch

            if (!flashcard.audioUrl.isNullOrBlank() && networkMonitor.isCurrentlyOnline()) {
                val targetFile = definitionCacheFile(definition)
                if (audioBackendClient.downloadAudio(flashcard.audioUrl, targetFile)) {
                    flashcardRepository.updateAudioMetadata(
                        id = flashcard.id,
                        audioUrl = flashcard.audioUrl,
                        localAudioPath = targetFile.absolutePath,
                        isAudioReady = true
                    )
                    if (playLocalFile(targetFile.absolutePath)) return@launch
                }
                if (playRemoteAudio(flashcard.audioUrl)) return@launch
            }

            if (networkMonitor.isCurrentlyOnline()) {
                val resolved = audioBackendClient.resolveAudio(definition, AudioRequestKind.DEFINITION)
                if (resolved != null) {
                    val targetFile = definitionCacheFile(definition)
                    val localPath = if (audioBackendClient.downloadAudio(resolved.audioUrl, targetFile)) {
                        targetFile.absolutePath
                    } else {
                        flashcard.localAudioPath
                    }
                    flashcardRepository.updateAudioMetadata(
                        id = flashcard.id,
                        audioUrl = resolved.audioUrl,
                        localAudioPath = localPath,
                        isAudioReady = true
                    )
                    if (!localPath.isNullOrBlank() && playLocalFile(localPath)) return@launch
                    if (playRemoteAudio(resolved.audioUrl)) return@launch
                }

                val publicId = DefinitionAudioFingerprint.buildPublicId(definition)
                val reusableUrl = cloudinaryService.findAudioUrl(publicId)
                if (!reusableUrl.isNullOrBlank()) {
                    val targetFile = definitionCacheFile(definition)
                    val localPath = if (audioBackendClient.downloadAudio(reusableUrl, targetFile)) {
                        targetFile.absolutePath
                    } else {
                        flashcard.localAudioPath
                    }
                    flashcardRepository.updateAudioMetadata(
                        id = flashcard.id,
                        audioUrl = reusableUrl,
                        localAudioPath = localPath,
                        isAudioReady = true
                    )
                    if (!localPath.isNullOrBlank() && playLocalFile(localPath)) return@launch
                    if (playRemoteAudio(reusableUrl)) return@launch
                }
            }

            val reusableAudio = flashcardRepository.findReusableDefinitionAudio(definition)
                ?.takeIf { it.id != flashcard.id }
            if (reusableAudio != null) {
                flashcardRepository.updateAudioMetadata(
                    id = flashcard.id,
                    audioUrl = reusableAudio.audioUrl,
                    localAudioPath = reusableAudio.localAudioPath,
                    isAudioReady = reusableAudio.isAudioReady
                )
                if (!reusableAudio.audioUrl.isNullOrBlank() &&
                    networkMonitor.isCurrentlyOnline() &&
                    playRemoteAudio(reusableAudio.audioUrl)
                ) {
                    return@launch
                }
                if (playLocalAudio(reusableAudio.localAudioPath)) return@launch
            }

            val usedSystem = speakViaSystem(definition, isEnglish = true)
            if (!usedSystem) {
                _state.value = TtsState.UNAVAILABLE
            }
        }
    }

    fun scheduleFlashcardDefinitionGeneration(flashcardId: String) {
        FlashcardTtsWorker.enqueue(context, flashcardId)
    }

    private fun definitionCacheFile(definition: String): File {
        return File(
            context.filesDir,
            "tts_cache/definition_${DefinitionAudioFingerprint.buildCacheKey(definition)}.mp3"
        )
    }

    private fun speakText(text: String, isEnglish: Boolean) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return

        stop()
        scope.launch {
            val usedSystem = speakViaSystem(normalizedText, isEnglish)
            if (!usedSystem) {
                _state.value = TtsState.UNAVAILABLE
            }
        }
    }

    private fun playLocalAudio(localAudioPath: String?): Boolean {
        val path = localAudioPath?.takeIf { it.isNotBlank() } ?: return false
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return false
        return playLocalFile(file.absolutePath)
    }

    private suspend fun playRemoteAudio(audioUrl: String?): Boolean {
        val url = audioUrl?.takeIf { it.isNotBlank() } ?: return false
        if (!networkMonitor.isCurrentlyOnline()) return false
        return playAudioSource(url)
    }

    private fun playLocalFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return false
            releasePlayer()
            _state.value = TtsState.SPEAKING
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    releasePlayer()
                    _state.value = TtsState.READY
                }
                setOnErrorListener { _, _, _ ->
                    releasePlayer()
                    _state.value = TtsState.ERROR
                    true
                }
                start()
            }
            true
        } catch (error: Exception) {
            Log.w(TAG, "Local audio playback failed for $path", error)
            releasePlayer()
            false
        }
    }

    private suspend fun playAudioSource(source: String): Boolean {
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(7000) {
                suspendCancellableCoroutine { continuation ->
                    val settled = AtomicBoolean(false)

                    fun finish(value: Boolean) {
                        if (!settled.compareAndSet(false, true)) return
                        continuation.resume(value)
                    }

                    try {
                        releasePlayer()
                        _state.value = TtsState.SPEAKING
                        mediaPlayer = MediaPlayer().apply {
                            Log.d(TAG, "Preparing remote audio source=$source")
                            setDataSource(source)
                            setOnPreparedListener {
                                it.start()
                                finish(true)
                            }
                            setOnCompletionListener {
                                releasePlayer()
                                _state.value = TtsState.READY
                            }
                            setOnErrorListener { _, _, _ ->
                                releasePlayer()
                                _state.value = TtsState.ERROR
                                finish(false)
                                true
                            }
                            prepareAsync()
                        }
                    } catch (_: Exception) {
                        releasePlayer()
                        finish(false)
                    }

                    continuation.invokeOnCancellation {
                        if (settled.compareAndSet(false, true)) {
                            releasePlayer()
                        }
                    }
                }
            } ?: false
        }
    }

    private fun speakViaSystem(text: String, isEnglish: Boolean): Boolean {
        if (!isSystemTtsReady) return false
        val locale = if (isEnglish) Locale.US else Locale("ar")
        val tts = systemTts ?: return false
        val languageResult = tts.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            return false
        }
        tts.apply {
            setSpeechRate(0.85f)
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "edu_tts_${System.currentTimeMillis()}")
        }
        return true
    }

    private fun containsArabicCharacters(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' }
    }

    private fun releasePlayer() {
        mediaPlayer?.run {
            runCatching { stop() }
            release()
        }
        mediaPlayer = null
    }

    fun stop() {
        releasePlayer()
        systemTts?.stop()
        if (isSystemTtsReady) {
            _state.value = TtsState.READY
        }
    }

    fun shutdown() {
        stop()
        systemTts?.shutdown()
    }
}
