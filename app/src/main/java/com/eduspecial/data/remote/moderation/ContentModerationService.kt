package com.eduspecial.data.remote.moderation

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ContentModerationService @Inject constructor() {
    companion object {
        private const val TAG = "ContentModeration"
    }

    private val blacklistLock = Mutex()
    private var blacklistedTerms: Set<String> = emptySet()
    private var blacklistedPatterns: List<String> = emptyList()

    suspend fun moderateContent(
        content: String,
        contentType: ContentType,
        authorId: String,
        additionalContext: Map<String, Any> = emptyMap()
    ): ModerationResult {
        return try {
            Log.d(TAG, "Moderating ${contentType.name} content from user: $authorId")

            val blacklistResult = checkBlacklist(content)
            if (blacklistResult.isBlocked) {
                return ModerationResult(
                    decision = ModerationDecision.REJECT,
                    confidence = 1.0f,
                    reason = "Blacklisted content: ${blacklistResult.matchedTerm}",
                    flags = listOf(ModerationFlag.BLACKLISTED_CONTENT)
                )
            }

            val analysisResult = analyzeContent(content, contentType)
            val userScore = 0.5f
            val finalDecision = makeModerationDecision(
                analysisResult = analysisResult,
                userScore = userScore,
                contentType = contentType,
                context = additionalContext
            )

            Log.d(TAG, "Moderation complete: ${finalDecision.decision} (${finalDecision.confidence})")
            finalDecision
        } catch (e: Exception) {
            Log.e(TAG, "Moderation failed: ${e.message}")
            ModerationResult(
                decision = ModerationDecision.APPROVE_WITH_REVIEW,
                confidence = 0.0f,
                reason = "Moderation system error: ${e.message}",
                flags = listOf(ModerationFlag.SYSTEM_ERROR)
            )
        }
    }

    suspend fun reportContent(
        contentId: String,
        contentType: ContentType,
        reporterId: String,
        reason: ReportReason,
        additionalInfo: String = ""
    ): Boolean {
        Log.d(
            TAG,
            "Report queued locally: content=$contentId type=${contentType.name} reporter=$reporterId reason=${reason.name}"
        )
        return true
    }

    suspend fun updateBlacklist(
        terms: List<String>,
        patterns: List<String>
    ) {
        blacklistLock.withLock {
            blacklistedTerms = terms.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            blacklistedPatterns = patterns.map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    private suspend fun checkBlacklist(content: String): BlacklistResult {
        val contentLower = content.lowercase()
        val terms = blacklistLock.withLock { blacklistedTerms.toList() }
        for (term in terms) {
            if (contentLower.contains(term)) {
                return BlacklistResult(true, term)
            }
        }

        val patterns = blacklistLock.withLock { blacklistedPatterns.toList() }
        for (pattern in patterns) {
            try {
                if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(content)) {
                    return BlacklistResult(true, "Pattern: $pattern")
                }
            } catch (_: Exception) {
                Log.w(TAG, "Invalid regex pattern: $pattern")
            }
        }

        return BlacklistResult(false, null)
    }

    private suspend fun analyzeContent(
        content: String,
        contentType: ContentType
    ): ContentAnalysisResult {
        val flags = mutableListOf<ModerationFlag>()
        var riskScore = 0.0f

        when (contentType) {
            ContentType.FLASHCARD_TERM -> {
                if (content.length > 200) flags.add(ModerationFlag.EXCESSIVE_LENGTH)
                if (content.length < 2) flags.add(ModerationFlag.TOO_SHORT)
            }
            ContentType.FLASHCARD_DEFINITION -> {
                if (content.length > 1000) flags.add(ModerationFlag.EXCESSIVE_LENGTH)
                if (content.length < 5) flags.add(ModerationFlag.TOO_SHORT)
            }
            ContentType.QUESTION -> {
                if (content.length > 500) flags.add(ModerationFlag.EXCESSIVE_LENGTH)
                if (content.length < 10) flags.add(ModerationFlag.TOO_SHORT)
            }
            ContentType.ANSWER -> {
                if (content.length > 2000) flags.add(ModerationFlag.EXCESSIVE_LENGTH)
                if (content.length < 5) flags.add(ModerationFlag.TOO_SHORT)
            }
            ContentType.USER_PROFILE -> {
                if (content.length > 100) flags.add(ModerationFlag.EXCESSIVE_LENGTH)
                if (content.length < 2) flags.add(ModerationFlag.TOO_SHORT)
            }
        }

        val upperCaseRatio = content.count { it.isUpperCase() }.toFloat() / content.length.coerceAtLeast(1)
        if (upperCaseRatio > 0.7f && !isEducationalAbbreviation(content)) {
            flags.add(ModerationFlag.EXCESSIVE_CAPS)
            riskScore += 0.3f
        }

        if (hasRepetitiveContent(content)) {
            flags.add(ModerationFlag.REPETITIVE_CONTENT)
            riskScore += 0.4f
        }

        if (hasSpamIndicators(content)) {
            flags.add(ModerationFlag.SPAM_INDICATORS)
            riskScore += 0.5f
        }

        if (!isEducationallyRelevant(content, contentType)) {
            flags.add(ModerationFlag.OFF_TOPIC)
            riskScore += 0.3f
        }

        return ContentAnalysisResult(
            riskScore = riskScore.coerceAtMost(1.0f),
            flags = flags,
            confidence = if (flags.isEmpty()) 0.9f else 0.7f
        )
    }

    private fun makeModerationDecision(
        analysisResult: ContentAnalysisResult,
        userScore: Float,
        contentType: ContentType,
        context: Map<String, Any>
    ): ModerationResult {
        val riskScore = analysisResult.riskScore
        val flags = analysisResult.flags
        val adjustedRisk = riskScore * (2.0f - userScore)

        val decision = when {
            adjustedRisk > 0.8f || flags.contains(ModerationFlag.BLACKLISTED_CONTENT) ->
                ModerationDecision.REJECT
            adjustedRisk > 0.5f || flags.any { it.requiresReview() } ->
                ModerationDecision.APPROVE_WITH_REVIEW
            else -> ModerationDecision.APPROVE
        }

        return ModerationResult(
            decision = decision,
            confidence = analysisResult.confidence * userScore,
            reason = generateModerationReason(flags, adjustedRisk, userScore),
            flags = flags,
            riskScore = adjustedRisk,
            userReputationScore = userScore
        )
    }

    private fun hasRepetitiveContent(content: String): Boolean {
        val words = content.split("\\s+".toRegex())
        if (words.size < 5) return false
        val wordCounts = words.groupingBy { it.lowercase() }.eachCount()
        val maxCount = wordCounts.values.maxOrNull() ?: 0
        return maxCount > words.size * 0.3
    }

    private fun hasSpamIndicators(content: String): Boolean {
        if (isEducationalAbbreviation(content)) return false
        val spamPatterns = listOf(
            "اضغط هنا", "رابط", "تحميل", "مجاني", "عرض خاص",
            "click here", "download", "free", "special offer"
        )
        val contentLower = content.lowercase()
        return spamPatterns.any { contentLower.contains(it) }
    }

    private fun isEducationallyRelevant(content: String, contentType: ContentType): Boolean {
        if (contentType == ContentType.FLASHCARD_TERM && isEducationalAbbreviation(content)) {
            return true
        }
        val educationalKeywords = listOf(
            "تعلم", "تعليم", "درس", "شرح", "مفهوم", "تعريف", "مثال",
            "learn", "education", "lesson", "explain", "concept", "definition", "example",
            "علاج", "سلوك", "نطق", "تطوير", "مهارة", "تدريب",
            "therapy", "behavior", "speech", "development", "skill", "training"
        )

        val contentLower = content.lowercase()
        return educationalKeywords.any { contentLower.contains(it) } || content.length > 50
    }

    private fun isEducationalAbbreviation(content: String): Boolean {
        val normalized = content.trim()
        if (normalized.length !in 2..10) return false
        if (!normalized.all { it.isLetterOrDigit() || it == '-' || it == '/' }) return false
        val alphaCount = normalized.count { it.isLetter() }
        if (alphaCount < 2) return false
        val upperCount = normalized.count { it.isUpperCase() }
        return upperCount >= alphaCount.coerceAtMost(2) || normalized.any { it.isDigit() }
    }

    private fun generateModerationReason(
        flags: List<ModerationFlag>,
        riskScore: Float,
        userScore: Float
    ): String {
        return when {
            flags.contains(ModerationFlag.BLACKLISTED_CONTENT) -> "محتوى محظور"
            flags.contains(ModerationFlag.SPAM_INDICATORS) -> "مؤشرات سبام"
            flags.contains(ModerationFlag.OFF_TOPIC) -> "محتوى غير تعليمي"
            flags.contains(ModerationFlag.EXCESSIVE_CAPS) -> "استخدام مفرط للأحرف الكبيرة"
            flags.contains(ModerationFlag.REPETITIVE_CONTENT) -> "محتوى متكرر"
            riskScore > 0.5f -> "محتوى عالي المخاطر (${(riskScore * 100).toInt()}%)"
            userScore < 0.3f -> "مستخدم جديد - مراجعة احترازية"
            else -> "تمت الموافقة"
        }
    }
}

data class ModerationResult(
    val decision: ModerationDecision,
    val confidence: Float,
    val reason: String,
    val flags: List<ModerationFlag> = emptyList(),
    val riskScore: Float = 0.0f,
    val userReputationScore: Float = 0.5f
)

data class ContentAnalysisResult(
    val riskScore: Float,
    val flags: List<ModerationFlag>,
    val confidence: Float
)

data class BlacklistResult(
    val isBlocked: Boolean,
    val matchedTerm: String?
)

enum class ModerationDecision {
    APPROVE,
    APPROVE_WITH_REVIEW,
    REJECT
}

enum class ContentType {
    FLASHCARD_TERM,
    FLASHCARD_DEFINITION,
    QUESTION,
    ANSWER,
    USER_PROFILE
}

enum class ModerationFlag {
    BLACKLISTED_CONTENT,
    SPAM_INDICATORS,
    OFF_TOPIC,
    EXCESSIVE_CAPS,
    REPETITIVE_CONTENT,
    EXCESSIVE_LENGTH,
    TOO_SHORT,
    SYSTEM_ERROR;

    fun requiresReview(): Boolean = when (this) {
        BLACKLISTED_CONTENT, SPAM_INDICATORS, OFF_TOPIC -> true
        else -> false
    }
}

enum class ReportReason {
    INAPPROPRIATE_CONTENT,
    SPAM,
    OFF_TOPIC,
    HARASSMENT,
    COPYRIGHT_VIOLATION,
    MISINFORMATION,
    OTHER
}
