package com.eduspecial.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── Request DTOs ─────────────────────────────────────────────────────────────
data class CreateFlashcardRequest(
    @SerializedName("id") val id: String? = null,
    @SerializedName("term") val term: String,
    @SerializedName("definition") val definition: String,
    @SerializedName("category") val category: String,
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("media_type") val mediaType: String = "NONE",
    @SerializedName("contributor_id") val contributorId: String
)

data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("displayName") val displayName: String
)

data class CreateQuestionRequest(
    @SerializedName("id") val id: String? = null,
    @SerializedName("question") val question: String,
    @SerializedName("category") val category: String,
    @SerializedName("contributor_id") val contributorId: String,
    @SerializedName("tags") val tags: List<String> = emptyList()
)

data class CreateAnswerRequest(
    @SerializedName("id") val id: String? = null,
    @SerializedName("question_id") val questionId: String,
    @SerializedName("content") val content: String,
    @SerializedName("contributor_id") val contributorId: String
)

data class DuplicateCheckRequest(
    @SerializedName("term") val term: String
)

data class UpdateFlashcardRequest(
    @SerializedName("term") val term: String,
    @SerializedName("definition") val definition: String,
    @SerializedName("category") val category: String,
    @SerializedName("media_url") val mediaUrl: String?,
    @SerializedName("media_type") val mediaType: String
)

data class UpdateQuestionRequest(
    @SerializedName("question") val question: String,
    @SerializedName("category") val category: String
)

data class UpdateAnswerRequest(
    @SerializedName("content") val content: String
)

data class UpdateProfileRequest(
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

// ─── Response DTOs ────────────────────────────────────────────────────────────
data class FlashcardDto(
    @SerializedName("id") val id: String,
    @SerializedName("term") val term: String,
    @SerializedName("definition") val definition: String,
    @SerializedName("category") val category: String,
    @SerializedName("media_url") val mediaUrl: String?,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("contributor") val contributor: String,
    @SerializedName("created_at") val createdAt: Long
)

data class QAQuestionDto(
    @SerializedName("id") val id: String,
    @SerializedName("question") val question: String,
    @SerializedName("answers") val answers: List<QAAnswerDto> = emptyList(),
    @SerializedName("category") val category: String,
    @SerializedName("contributor") val contributor: String,
    @SerializedName("upvotes") val upvotes: Int,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("is_answered") val isAnswered: Boolean,
    @SerializedName("tags") val tags: List<String>
)

data class QAAnswerDto(
    @SerializedName("id") val id: String,
    @SerializedName("question_id") val questionId: String,
    @SerializedName("content") val content: String,
    @SerializedName("contributor") val contributor: String,
    @SerializedName("upvotes") val upvotes: Int,
    @SerializedName("is_accepted") val isAccepted: Boolean,
    @SerializedName("created_at") val createdAt: Long
)

data class DuplicateCheckResponse(
    @SerializedName("is_duplicate") val isDuplicate: Boolean,
    @SerializedName("similar_terms") val similarTerms: List<String>
)

data class PaginatedResponse<T>(
    @SerializedName("data") val data: List<T>,
    @SerializedName("page") val page: Int,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("has_more") val hasMore: Boolean
)

data class CloudinaryUploadResponse(
    @SerializedName("secure_url") val secureUrl: String,
    @SerializedName("public_id") val publicId: String,
    @SerializedName("resource_type") val resourceType: String,
    @SerializedName("duration") val duration: Float?
)

data class UserProfileDto(
    @SerializedName("uid") val uid: String,
    @SerializedName("email") val email: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("contributionCount") val contributionCount: Int,
    @SerializedName("joinedAt") val joinedAt: Long
)

data class RegisterResponse(
    @SerializedName("token") val token: String?,
    @SerializedName("user") val user: UserProfileDto
)
