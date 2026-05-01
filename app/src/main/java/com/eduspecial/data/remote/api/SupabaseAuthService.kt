package com.eduspecial.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface SupabaseAuthService {

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(
        @Body request: SupabasePasswordAuthRequest
    ): Response<SupabaseSessionResponse>

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: SupabaseSignUpRequest
    ): Response<SupabaseSessionResponse>

    @POST("auth/v1/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @GET("auth/v1/user")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): Response<SupabaseAuthUser>

    @PUT("auth/v1/user")
    suspend fun updateCurrentUser(
        @Header("Authorization") authorization: String,
        @Body request: SupabaseUpdateUserRequest
    ): Response<SupabaseAuthUser>

    @POST("auth/v1/recover")
    suspend fun sendPasswordRecovery(
        @Body request: SupabaseRecoverRequest
    ): Response<Unit>

    @POST("auth/v1/resend")
    suspend fun resendSignupVerification(
        @Body request: SupabaseResendRequest
    ): Response<Unit>
}

interface SupabaseAdminAuthService {

    @GET("auth/v1/admin/users")
    suspend fun listUsers(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 1000
    ): Response<SupabaseAdminUsersResponse>

    @POST("auth/v1/admin/users")
    suspend fun createUser(
        @Body request: SupabaseAdminCreateUserRequest
    ): Response<SupabaseAdminUser>
}

interface SupabaseRestService {

    @GET("rest/v1/users")
    suspend fun getUsers(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<SupabaseUserRow>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/users")
    suspend fun upsertUser(
        @Body request: SupabaseUserRow
    ): Response<List<SupabaseUserRow>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/users")
    suspend fun updateUser(
        @Query("id") idFilter: String,
        @Body request: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<SupabaseUserRow>>

    @PATCH("rest/v1/users")
    suspend fun updateUserNoReturn(
        @Query("id") idFilter: String,
        @Body request: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    @PATCH("rest/v1/flashcards")
    suspend fun patchFlashcardsByContributor(
        @Query("user_id") userIdFilter: String,
        @Body request: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    @DELETE("rest/v1/users")
    suspend fun deleteUser(
        @Query("id") idFilter: String
    ): Response<Unit>

    @GET("rest/v1/flashcards")
    suspend fun getFlashcards(
        @QueryMap options: Map<String, String>
    ): Response<List<SupabaseFlashcardRow>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/flashcards")
    suspend fun createFlashcard(
        @Body request: SupabaseFlashcardRow
    ): Response<List<SupabaseFlashcardRow>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/flashcards")
    suspend fun updateFlashcard(
        @Query("id") idFilter: String,
        @Body request: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<SupabaseFlashcardRow>>

    @DELETE("rest/v1/flashcards")
    suspend fun deleteFlashcard(
        @Query("id") idFilter: String
    ): Response<Unit>

    @GET("rest/v1/qa_questions")
    suspend fun getQuestions(
        @QueryMap options: Map<String, String>
    ): Response<List<SupabaseQuestionRow>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/qa_questions")
    suspend fun createQuestion(
        @Body request: SupabaseQuestionRow
    ): Response<List<SupabaseQuestionRow>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/qa_questions")
    suspend fun updateQuestion(
        @Query("id") idFilter: String,
        @Body request: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<SupabaseQuestionRow>>

    @DELETE("rest/v1/qa_questions")
    suspend fun deleteQuestion(
        @Query("id") idFilter: String
    ): Response<Unit>

    @GET("rest/v1/qa_answers")
    suspend fun getAnswers(
        @QueryMap options: Map<String, String>
    ): Response<List<SupabaseAnswerRow>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/qa_answers")
    suspend fun createAnswer(
        @Body request: SupabaseAnswerRow
    ): Response<List<SupabaseAnswerRow>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/qa_answers")
    suspend fun updateAnswer(
        @Query("id") idFilter: String,
        @Body request: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<SupabaseAnswerRow>>

    @DELETE("rest/v1/qa_answers")
    suspend fun deleteAnswer(
        @Query("id") idFilter: String
    ): Response<Unit>
}

data class SupabasePasswordAuthRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class SupabaseSignUpRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("data") val data: SupabaseUserMetadata
)

data class SupabaseUpdateUserRequest(
    @SerializedName("email") val email: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("data") val data: SupabaseUserMetadata? = null
)

data class SupabaseRecoverRequest(
    @SerializedName("email") val email: String
)

data class SupabaseResendRequest(
    @SerializedName("type") val type: String,
    @SerializedName("email") val email: String
)

data class SupabaseAdminCreateUserRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("email_confirm") val emailConfirm: Boolean = true,
    @SerializedName("user_metadata") val userMetadata: SupabaseUserMetadata? = null
)

data class SupabaseSessionResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("user") val user: SupabaseAuthUser? = null
)

data class SupabaseAuthUser(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("email_confirmed_at") val emailConfirmedAt: String? = null,
    @SerializedName("user_metadata") val userMetadata: SupabaseUserMetadata? = null
)

data class SupabaseAdminUsersResponse(
    @SerializedName("users") val users: List<SupabaseAdminUser> = emptyList()
)

data class SupabaseAdminUser(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("user_metadata") val userMetadata: SupabaseUserMetadata? = null
)

data class SupabaseUserMetadata(
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("two_factor_enabled") val twoFactorEnabled: Boolean? = null,
    @SerializedName("last_login_at") val lastLoginAt: Long? = null
)

data class SupabaseUserRow(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class SupabaseFlashcardRow(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("term") val term: String,
    @SerializedName("definition") val definition: String,
    @SerializedName("category") val category: String,
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("media_type") val mediaType: String = "NONE",
    @SerializedName("audio_url") val audioUrl: String? = null,
    @SerializedName("cloud_name") val cloudName: String? = null,
    @SerializedName("public_id") val publicId: String? = null,
    @SerializedName("is_public") val isPublic: Boolean = true,
    @SerializedName("created_at") val createdAt: String? = null
)

data class SupabaseQuestionRow(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("question") val question: String,
    @SerializedName("category") val category: String,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("upvotes") val upvotes: Int = 0,
    @SerializedName("is_answered") val isAnswered: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

data class SupabaseAnswerRow(
    @SerializedName("id") val id: String,
    @SerializedName("question_id") val questionId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("content") val content: String,
    @SerializedName("parent_answer_id") val parentAnswerId: String? = null,
    @SerializedName("upvotes") val upvotes: Int = 0,
    @SerializedName("is_accepted") val isAccepted: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)
