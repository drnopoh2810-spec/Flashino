package com.eduspecial.data.manager

import android.util.Log
import com.eduspecial.data.model.AccountStatus
import com.eduspecial.data.model.Permission
import com.eduspecial.data.model.SecurityAuditLog
import com.eduspecial.data.model.SecurityEvent
import com.eduspecial.data.model.UserPreferences
import com.eduspecial.data.model.UserProfile
import com.eduspecial.data.model.UserRole
import com.eduspecial.utils.UserPreferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

@Singleton
class RoleManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val prefs: UserPreferencesDataStore
) {

    companion object {
        private const val TAG = "RoleManager"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_SECURITY_LOGS = "security_logs"
        private const val COLLECTION_ROLE_ASSIGNMENTS = "role_assignments"

        private val ADMIN_EMAILS: Set<String> = setOf(
            "mahmoudnabihsaleh@gmail.com"
        )
    }

    suspend fun getCurrentUserRole(): UserRole {
        val currentUser = auth.currentUser ?: return UserRole.USER
        if (currentUser.email?.lowercase() in ADMIN_EMAILS) return UserRole.ADMIN
        return getUserRole(currentUser.uid)
    }

    suspend fun getUserRole(userId: String): UserRole {
        return try {
            val userDoc = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .get()
                .await()

            if (userDoc.exists()) {
                val role = UserRole.fromString(userDoc.getString("role"))
                val email = userDoc.getString("email")?.lowercase()
                if (email in ADMIN_EMAILS) UserRole.ADMIN else role
            } else {
                UserRole.USER
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user role: ${e.message}")
            UserRole.USER
        }
    }

    suspend fun getCurrentUserProfile(): UserProfile? {
        val currentUser = auth.currentUser ?: return null
        return getUserProfile(currentUser.uid)
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        val currentUser = auth.currentUser
        if (currentUser?.uid == userId) {
            val localFallback = buildLocalProfile(currentUser)
            try {
                val userDoc = firestore.collection(COLLECTION_USERS)
                    .document(userId)
                    .get()
                    .await()

                if (!userDoc.exists()) return localFallback

                val data = userDoc.data ?: return localFallback
                val remoteProfile = UserProfile(
                    uid = userId,
                    email = data["email"] as? String ?: localFallback.email,
                    displayName = data["displayName"] as? String ?: localFallback.displayName,
                    role = UserRole.fromString(data["role"] as? String),
                    accountStatus = AccountStatus.fromString(data["accountStatus"] as? String),
                    emailVerified = data["emailVerified"] as? Boolean ?: currentUser.isEmailVerified,
                    phoneVerified = data["phoneVerified"] as? Boolean ?: false,
                    twoFactorEnabled = data["twoFactorEnabled"] as? Boolean ?: false,
                    createdAt = data["createdAt"] as? Long ?: localFallback.createdAt,
                    lastLoginAt = data["lastLoginAt"] as? Long ?: localFallback.lastLoginAt,
                    profileImageUrl = (data["profileImageUrl"] as? String)
                        ?: (data["avatarUrl"] as? String)
                        ?: localFallback.profileImageUrl,
                    bio = data["bio"] as? String,
                    points = (data["points"] as? Long)?.toInt() ?: 0,
                    contributionCount = (data["contributionCount"] as? Long)?.toInt() ?: 0,
                    moderationScore = (data["moderationScore"] as? Double)?.toFloat() ?: 0.5f,
                    preferences = parseUserPreferences(data["preferences"] as? Map<String, Any>)
                )
                return remoteProfile.copy(
                    preferences = localFallback.preferences,
                    displayName = prefs.displayName.first() ?: remoteProfile.displayName,
                    profileImageUrl = prefs.avatarUrl.first() ?: remoteProfile.profileImageUrl
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get user profile: ${e.message}")
                return localFallback
            }
        }

        return try {
            val userDoc = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .get()
                .await()

            if (!userDoc.exists()) return null

            val data = userDoc.data ?: return null
            UserProfile(
                uid = userId,
                email = data["email"] as? String ?: "",
                displayName = data["displayName"] as? String ?: "",
                role = UserRole.fromString(data["role"] as? String),
                accountStatus = AccountStatus.fromString(data["accountStatus"] as? String),
                emailVerified = data["emailVerified"] as? Boolean ?: false,
                phoneVerified = data["phoneVerified"] as? Boolean ?: false,
                twoFactorEnabled = data["twoFactorEnabled"] as? Boolean ?: false,
                createdAt = data["createdAt"] as? Long ?: System.currentTimeMillis(),
                lastLoginAt = data["lastLoginAt"] as? Long ?: System.currentTimeMillis(),
                profileImageUrl = (data["profileImageUrl"] as? String) ?: (data["avatarUrl"] as? String),
                bio = data["bio"] as? String,
                points = (data["points"] as? Long)?.toInt() ?: 0,
                contributionCount = (data["contributionCount"] as? Long)?.toInt() ?: 0,
                moderationScore = (data["moderationScore"] as? Double)?.toFloat() ?: 0.5f,
                preferences = parseUserPreferences(data["preferences"] as? Map<String, Any>)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user profile: ${e.message}")
            null
        }
    }

    suspend fun hasPermission(permission: Permission): Boolean {
        return permission.isGrantedTo(getCurrentUserRole())
    }

    suspend fun userHasPermission(userId: String, permission: Permission): Boolean {
        return permission.isGrantedTo(getUserRole(userId))
    }

    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Boolean {
        return try {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId != userId && !hasPermission(Permission.MANAGE_USERS)) {
                Log.w(TAG, "User does not have permission to update this profile")
                return false
            }

            val sanitizedUpdates = updates.toMutableMap()
            sanitizedUpdates["updatedAt"] = System.currentTimeMillis()

            if (currentUserId == userId) {
                sanitizedUpdates.remove("role")
                sanitizedUpdates.remove("accountStatus")
                val profileUpdates = mutableListOf<suspend () -> Unit>()
                (sanitizedUpdates["displayName"] as? String)?.let { displayName ->
                    profileUpdates += suspend {
                        auth.currentUser?.updateProfile(
                            com.google.firebase.auth.userProfileChangeRequest {
                                this.displayName = displayName
                            }
                        )?.await()
                        prefs.setDisplayName(displayName)
                    }
                }
                (sanitizedUpdates["profileImageUrl"] as? String ?: sanitizedUpdates["avatarUrl"] as? String)?.let { avatarUrl ->
                    profileUpdates += suspend {
                        auth.currentUser?.updateProfile(
                            com.google.firebase.auth.userProfileChangeRequest {
                                photoUri = android.net.Uri.parse(avatarUrl)
                            }
                        )?.await()
                        prefs.setAvatarUrl(avatarUrl)
                    }
                }
                profileUpdates.forEach { it() }
            }

            runCatching {
                firestore.collection(COLLECTION_USERS)
                    .document(userId)
                    .update(sanitizedUpdates)
                    .await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update profile: ${e.message}")
            false
        }
    }

    suspend fun logSecurityEvent(
        userId: String,
        event: SecurityEvent,
        details: Map<String, Any> = emptyMap(),
        success: Boolean = true
    ) {
        try {
            firestore.collection(COLLECTION_SECURITY_LOGS)
                .add(
                    SecurityAuditLog(
                        userId = userId,
                        event = event,
                        details = details,
                        success = success
                    )
                )
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log security event: ${e.message}")
        }
    }

    suspend fun getSecurityAuditLogs(userId: String, limit: Int = 20): List<SecurityAuditLog> {
        return try {
            firestore.collection(COLLECTION_SECURITY_LOGS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(SecurityAuditLog::class.java) }
                .sortedByDescending { it.timestamp }
                .take(limit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load security logs: ${e.message}")
            emptyList()
        }
    }

    suspend fun initializeUserProfile(
        userId: String,
        email: String,
        displayName: String
    ): Boolean {
        return try {
            val initialRole = if (email.lowercase() in ADMIN_EMAILS) UserRole.ADMIN else UserRole.USER
            val initialStatus = if (initialRole == UserRole.ADMIN) AccountStatus.ACTIVE else AccountStatus.PENDING_VERIFICATION
            val userProfile = mapOf(
                "email" to email,
                "displayName" to displayName,
                "role" to initialRole.name,
                "accountStatus" to initialStatus.name,
                "emailVerified" to false,
                "phoneVerified" to false,
                "twoFactorEnabled" to false,
                "createdAt" to System.currentTimeMillis(),
                "lastLoginAt" to System.currentTimeMillis(),
                "points" to 0,
                "contributionCount" to 0,
                "moderationScore" to 0.5f,
                "preferences" to mapOf(
                    "language" to "ar",
                    "theme" to "system",
                    "themePalette" to "qusasa",
                    "notificationsEnabled" to true,
                    "studyRemindersEnabled" to true,
                    "emailNotificationsEnabled" to true,
                    "soundEnabled" to true,
                    "vibrationEnabled" to true,
                    "autoPlayTTS" to false,
                    "dailyGoal" to 20,
                    "reminderTime" to "19:00"
                )
            )

            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .set(userProfile)
                .await()

            logSecurityEvent(userId, SecurityEvent.LOGIN)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize user profile: ${e.message}")
            false
        }
    }

    private fun parseUserPreferences(prefsMap: Map<String, Any>?): UserPreferences {
        if (prefsMap == null) return UserPreferences()

        return UserPreferences(
            language = prefsMap["language"] as? String ?: "ar",
            theme = prefsMap["theme"] as? String ?: "system",
            themePalette = prefsMap["themePalette"] as? String ?: "qusasa",
            notificationsEnabled = prefsMap["notificationsEnabled"] as? Boolean ?: true,
            studyRemindersEnabled = prefsMap["studyRemindersEnabled"] as? Boolean ?: true,
            emailNotificationsEnabled = prefsMap["emailNotificationsEnabled"] as? Boolean ?: true,
            soundEnabled = prefsMap["soundEnabled"] as? Boolean ?: true,
            vibrationEnabled = prefsMap["vibrationEnabled"] as? Boolean ?: true,
            autoPlayTTS = prefsMap["autoPlayTTS"] as? Boolean ?: false,
            dailyGoal = (prefsMap["dailyGoal"] as? Long)?.toInt() ?: 20,
            reminderTime = prefsMap["reminderTime"] as? String ?: "19:00"
        )
    }

    private suspend fun buildLocalProfile(currentUser: com.google.firebase.auth.FirebaseUser): UserProfile {
        return UserProfile(
            uid = currentUser.uid,
            email = currentUser.email ?: prefs.userEmail.first().orEmpty(),
            displayName = currentUser.displayName ?: prefs.displayName.first() ?: "مستخدم",
            role = if (currentUser.email?.lowercase() in ADMIN_EMAILS) UserRole.ADMIN else UserRole.USER,
            accountStatus = if (currentUser.isEmailVerified) AccountStatus.ACTIVE else AccountStatus.PENDING_VERIFICATION,
            emailVerified = currentUser.isEmailVerified,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis(),
            profileImageUrl = currentUser.photoUrl?.toString() ?: prefs.avatarUrl.first(),
            preferences = UserPreferences(
                language = prefs.language.first(),
                theme = prefs.themeMode.first(),
                themePalette = prefs.themePalette.first(),
                notificationsEnabled = prefs.notificationsEnabled.first(),
                studyRemindersEnabled = prefs.studyNotificationsEnabled.first(),
                emailNotificationsEnabled = prefs.emailNotificationsEnabled.first(),
                soundEnabled = prefs.soundEnabled.first(),
                vibrationEnabled = prefs.vibrationEnabled.first(),
                autoPlayTTS = prefs.autoPlayTts.first(),
                dailyGoal = prefs.dailyGoal.first(),
                reminderTime = millisToReminderTime(prefs.reminderTimeMillis.first())
            )
        )
    }

    private fun millisToReminderTime(value: Long): String {
        val totalMinutes = (value / 60_000L).toInt()
        val hour = (totalMinutes / 60).coerceIn(0, 23)
        val minute = (totalMinutes % 60).coerceIn(0, 59)
        return "%02d:%02d".format(hour, minute)
    }
}
