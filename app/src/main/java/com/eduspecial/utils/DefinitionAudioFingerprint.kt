package com.eduspecial.utils

import java.security.MessageDigest

object DefinitionAudioFingerprint {
    private const val TTS_FOLDER = "eduspecial/tts"
    private const val DEFINITION_VOICE_VERSION = "iflytek-en-definition-v1"
    private const val TERM_VOICE_VERSION = "iflytek-en-term-v1"

    fun normalize(text: String): String {
        return text
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }

    private fun hash(namespacedText: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(namespacedText.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(40)
    }

    fun buildCacheKey(text: String): String =
        hash("${DEFINITION_VOICE_VERSION}:${normalize(text)}")

    fun buildPublicId(text: String): String = "$TTS_FOLDER/${buildCacheKey(text)}"

    fun buildTermCacheKey(text: String): String =
        hash("${TERM_VOICE_VERSION}:${normalize(text)}")

    fun buildTermPublicId(text: String): String = "$TTS_FOLDER/term-${buildTermCacheKey(text)}"
}
