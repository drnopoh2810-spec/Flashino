package com.eduspecial.utils

/**
 * Centralized input sanitization and validation utilities.
 *
 * All user-provided text should pass through these functions before being
 * stored or sent to the backend to prevent injection and malformed data.
 */
object InputSanitizer {

    private const val MAX_TERM_LENGTH       = 200
    private const val MAX_DEFINITION_LENGTH = 2000
    private const val MAX_QUESTION_LENGTH   = 1000
    private const val MAX_ANSWER_LENGTH     = 3000
    private const val MAX_NAME_LENGTH       = 50
    private const val MIN_NAME_LENGTH       = 2
    // Keep email validation JVM-safe for local unit tests (no android.util.Patterns dependency).
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // ─── Text Sanitization ────────────────────────────────────────────────────

    /**
     * Trims whitespace and collapses multiple internal spaces into one.
     * Safe to call on any user input before storage.
     */
    fun sanitizeText(input: String): String =
        input.trim().replace(Regex("\\s+"), " ")

    /**
     * Sanitizes and validates a flashcard term.
     * Returns [Result.failure] with a user-facing Arabic message on error.
     */
    fun validateTerm(term: String): Result<String> {
        val clean = sanitizeText(term)
        return when {
            clean.isBlank() -> Result.failure(IllegalArgumentException("المصطلح لا يمكن أن يكون فارغاً"))
            clean.length > MAX_TERM_LENGTH -> Result.failure(
                IllegalArgumentException("المصطلح طويل جداً (الحد الأقصى $MAX_TERM_LENGTH حرف)")
            )
            else -> Result.success(clean)
        }
    }

    /**
     * Sanitizes and validates a flashcard definition.
     */
    fun validateDefinition(definition: String): Result<String> {
        val clean = sanitizeText(definition)
        return when {
            clean.isBlank() -> Result.failure(IllegalArgumentException("التعريف لا يمكن أن يكون فارغاً"))
            clean.length > MAX_DEFINITION_LENGTH -> Result.failure(
                IllegalArgumentException("التعريف طويل جداً (الحد الأقصى $MAX_DEFINITION_LENGTH حرف)")
            )
            else -> Result.success(clean)
        }
    }

    /**
     * Sanitizes and validates a Q&A question.
     */
    fun validateQuestion(question: String): Result<String> {
        val clean = sanitizeText(question)
        return when {
            clean.isBlank() -> Result.failure(IllegalArgumentException("السؤال لا يمكن أن يكون فارغاً"))
            clean.length < 10 -> Result.failure(IllegalArgumentException("السؤال قصير جداً (10 أحرف على الأقل)"))
            clean.length > MAX_QUESTION_LENGTH -> Result.failure(
                IllegalArgumentException("السؤال طويل جداً (الحد الأقصى $MAX_QUESTION_LENGTH حرف)")
            )
            else -> Result.success(clean)
        }
    }

    /**
     * Sanitizes and validates a Q&A answer.
     */
    fun validateAnswer(answer: String): Result<String> {
        val clean = sanitizeText(answer)
        return when {
            clean.isBlank() -> Result.failure(IllegalArgumentException("الإجابة لا يمكن أن تكون فارغة"))
            clean.length < 5 -> Result.failure(IllegalArgumentException("الإجابة قصيرة جداً"))
            clean.length > MAX_ANSWER_LENGTH -> Result.failure(
                IllegalArgumentException("الإجابة طويلة جداً (الحد الأقصى $MAX_ANSWER_LENGTH حرف)")
            )
            else -> Result.success(clean)
        }
    }

    /**
     * Validates a display name.
     */
    fun validateDisplayName(name: String): Result<String> {
        val clean = sanitizeText(name)
        return when {
            clean.length < MIN_NAME_LENGTH -> Result.failure(
                IllegalArgumentException("الاسم قصير جداً (${MIN_NAME_LENGTH} أحرف على الأقل)")
            )
            clean.length > MAX_NAME_LENGTH -> Result.failure(
                IllegalArgumentException("الاسم طويل جداً (الحد الأقصى $MAX_NAME_LENGTH حرف)")
            )
            else -> Result.success(clean)
        }
    }

    /**
     * Validates an email address format.
     */
    fun validateEmail(email: String): Result<String> {
        val clean = email.trim().lowercase()
        return if (EMAIL_REGEX.matches(clean)) {
            Result.success(clean)
        } else {
            Result.failure(IllegalArgumentException("بريد إلكتروني غير صحيح"))
        }
    }

    /**
     * Validates a password (minimum 6 characters, at least one letter and one digit).
     */
    fun validatePassword(password: String): Result<String> {
        return when {
            password.length < 6 -> Result.failure(
                IllegalArgumentException("كلمة المرور 6 أحرف على الأقل")
            )
            else -> Result.success(password)
        }
    }

    /**
     * Strips any HTML/script tags from user input to prevent XSS in WebViews.
     */
    fun stripHtml(input: String): String =
        input.replace(Regex("<[^>]*>"), "").trim()
}
