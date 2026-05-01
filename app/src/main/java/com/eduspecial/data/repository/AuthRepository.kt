package com.eduspecial.data.repository

import com.eduspecial.data.local.EduSpecialDatabase
import com.eduspecial.data.manager.RoleManager
import com.eduspecial.data.model.AccountStatus
import com.eduspecial.data.model.Permission
import com.eduspecial.data.model.SecurityEvent
import com.eduspecial.data.model.UserPreferences
import com.eduspecial.data.model.UserProfile
import com.eduspecial.data.model.UserRole
import com.eduspecial.data.remote.dto.UserProfileDto
import com.eduspecial.utils.UserPreferencesDataStore
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import android.net.Uri
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(DelicateCoroutinesApi::class)
@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val prefs: UserPreferencesDataStore,
    private val database: EduSpecialDatabase,
    private val roleManager: RoleManager
) {
    companion object {
        private const val TAG = "AuthRepository"
        private const val AUTH_PROFILE_TIMEOUT_MS = 5_000L
        private const val FIRESTORE_SIDE_EFFECT_TIMEOUT_MS = 8_000L
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_SECURITY_LOGS = "security_logs"
        private const val COLLECTION_ROLE_ASSIGNMENTS = "role_assignments"
    }

    private val usersCol = firestore.collection(COLLECTION_USERS)

    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid

    fun getCurrentUserEmail(): String? = firebaseAuth.currentUser?.email ?: runBlocking { prefs.userEmail.first() }

    fun getCurrentDisplayName(): String? = firebaseAuth.currentUser?.displayName ?: runBlocking { prefs.displayName.first() }

    fun getCurrentAvatarUrl(): String? = firebaseAuth.currentUser?.photoUrl?.toString()
        ?: runBlocking { prefs.avatarUrl.first() }

    fun isLoggedIn(): Boolean = firebaseAuth.currentUser != null

    fun canParticipateInCommunity(): Boolean {
        val user = firebaseAuth.currentUser ?: return false
        return !user.isAnonymous
    }

    suspend fun login(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: throw Exception("No user returned")
            persistSession(user.uid, user.email, user.displayName, user.photoUrl?.toString())

            GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                    withTimeout(FIRESTORE_SIDE_EFFECT_TIMEOUT_MS) {
                        usersCol.document(user.uid).update("lastLoginAt", System.currentTimeMillis()).await()
                        roleManager.logSecurityEvent(user.uid, SecurityEvent.LOGIN)
                    }
                }
            }

            user.uid
        }.recoverCatching { error ->
            runCatching {
                val userQuery = usersCol.whereEqualTo("email", email.trim()).get().await()
                if (!userQuery.isEmpty) {
                    val userId = userQuery.documents.first().id
                    roleManager.logSecurityEvent(userId, SecurityEvent.FAILED_LOGIN, success = false)
                }
            }
            throw error
        }
    }

    suspend fun register(email: String, password: String, displayName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: throw Exception("No user returned")

            user.updateProfile(userProfileChangeRequest { this.displayName = displayName }).await()
            persistSession(user.uid, user.email, displayName, user.photoUrl?.toString())

            GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                    withTimeout(FIRESTORE_SIDE_EFFECT_TIMEOUT_MS) {
                        roleManager.initializeUserProfile(user.uid, email.trim(), displayName)
                    }
                }
            }

            user.uid
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user ?: throw Exception("Google sign-in returned no user")
            persistSession(user.uid, user.email, user.displayName, user.photoUrl?.toString())

            val isNewUser = result.additionalUserInfo?.isNewUser == true
            if (isNewUser) {
                GlobalScope.launch(Dispatchers.IO) {
                    runCatching {
                        roleManager.initializeUserProfile(
                            userId = user.uid,
                            email = user.email.orEmpty(),
                            displayName = user.displayName ?: user.email?.substringBefore("@") ?: "مستخدم"
                        )
                    }
                }
            } else {
                GlobalScope.launch(Dispatchers.IO) {
                    runCatching {
                        usersCol.document(user.uid)
                            .update("lastLoginAt", System.currentTimeMillis())
                            .await()
                    }
                }
            }

            GlobalScope.launch(Dispatchers.IO) {
                runCatching { roleManager.logSecurityEvent(user.uid, SecurityEvent.LOGIN) }
            }
            user.uid
        }
    }

    suspend fun signInAnonymously(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = firebaseAuth.signInAnonymously().await()
            val user = result.user ?: throw Exception("Anonymous sign-in failed")
            persistSession(user.uid, user.email, "زائر", user.photoUrl?.toString())
            user.uid
        }
    }

    suspend fun getMyProfile(): UserProfileDto? = withContext(Dispatchers.IO) {
        val uid = getCurrentUserId() ?: return@withContext null
        val profile = roleManager.getUserProfile(uid) ?: return@withContext null
        UserProfileDto(
            uid = profile.uid,
            displayName = profile.displayName,
            email = profile.email,
            avatarUrl = profile.profileImageUrl,
            contributionCount = profile.contributionCount,
            joinedAt = profile.createdAt
        )
    }

    suspend fun getEnhancedProfile(): UserProfile? = roleManager.getCurrentUserProfile()

    suspend fun hasPermission(permission: Permission): Boolean = roleManager.hasPermission(permission)

    suspend fun getCurrentUserRole(): UserRole = roleManager.getCurrentUserRole()

    suspend fun isCurrentUserAdmin(): Boolean = hasPermission(Permission.DELETE_ANY_CONTENT)

    suspend fun canManageContent(ownerUserId: String): Boolean {
        val currentUserId = getCurrentUserId() ?: return false
        val currentEmail = getCurrentUserEmail()?.trim()?.lowercase()
        val normalizedOwner = ownerUserId.trim().lowercase()
        return currentUserId == ownerUserId ||
            (!currentEmail.isNullOrBlank() && currentEmail == normalizedOwner) ||
            isCurrentUserAdmin()
    }

    suspend fun updateDisplayName(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = getCurrentUserId() ?: return@withContext Result.failure(Exception("Not logged in"))
        runCatching {
            prefs.setDisplayName(name)
            syncDisplayNameInBackground(uid, name)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun updateAvatarUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = getCurrentUserId() ?: return@withContext Result.failure(Exception("Not logged in"))
        runCatching {
            Log.d(TAG, "Updating avatar url for user=$uid, url=$url")
            prefs.setAvatarUrl(url)
            syncAvatarUrlInBackground(uid, url)
            Log.d(TAG, "Avatar url updated locally and on FirebaseAuth")
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    fun syncDisplayNameInBackground(name: String) {
        val uid = getCurrentUserId() ?: return
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { prefs.setDisplayName(name) }
                .onFailure { Log.w(TAG, "Skipping local display name cache update", it) }
            syncDisplayNameInBackground(uid, name)
        }
    }

    fun syncAvatarUrlInBackground(url: String) {
        val uid = getCurrentUserId() ?: return
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { prefs.setAvatarUrl(url) }
                .onFailure { Log.w(TAG, "Skipping local avatar cache update", it) }
            syncAvatarUrlInBackground(uid, url)
        }
    }

    private fun syncDisplayNameInBackground(uid: String, name: String) {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                withTimeout(AUTH_PROFILE_TIMEOUT_MS) {
                    firebaseAuth.currentUser?.updateProfile(
                        userProfileChangeRequest { displayName = name }
                    )?.await()
                }
            }.onFailure {
                Log.w(TAG, "Skipping FirebaseAuth display name update", it)
            }
            runCatching {
                withTimeout(FIRESTORE_SIDE_EFFECT_TIMEOUT_MS) {
                    usersCol.document(uid).update("displayName", name).await()
                }
            }.onFailure {
                Log.w(TAG, "Skipping Firestore display name update", it)
            }
        }
    }

    private fun syncAvatarUrlInBackground(uid: String, url: String) {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                withTimeout(AUTH_PROFILE_TIMEOUT_MS) {
                    firebaseAuth.currentUser?.updateProfile(
                        userProfileChangeRequest { photoUri = Uri.parse(url) }
                    )?.await()
                }
            }.onFailure {
                Log.w(TAG, "Skipping FirebaseAuth avatar update", it)
            }
            runCatching {
                withTimeout(FIRESTORE_SIDE_EFFECT_TIMEOUT_MS) {
                    usersCol.document(uid).update("avatarUrl", url, "profileImageUrl", url).await()
                }
            }.onFailure {
                Log.w(TAG, "Skipping Firestore avatar update", it)
            }
            Log.d(TAG, "Avatar url background sync finished")
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        val user = firebaseAuth.currentUser
            ?: return@withContext Result.failure(Exception("المستخدم غير مسجل الدخول"))
        val email = user.email
            ?: return@withContext Result.failure(Exception("لا يوجد بريد إلكتروني مرتبط بالحساب"))

        try {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            roleManager.logSecurityEvent(user.uid, SecurityEvent.PASSWORD_CHANGE)
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("كلمة المرور الحالية غير صحيحة"))
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(Exception("كلمة المرور الجديدة ضعيفة جداً"))
        } catch (e: Exception) {
            Result.failure(Exception("فشل تغيير كلمة المرور: ${e.message}"))
        }
    }

    fun signOut() {
        val userId = getCurrentUserId()
        firebaseAuth.signOut()
        runBlocking {
            prefs.clearSession()
        }
        userId?.let { uid ->
            GlobalScope.launch {
                runCatching {
                    roleManager.logSecurityEvent(uid, SecurityEvent.LOGOUT)
                }
            }
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            runCatching {
                val userQuery = usersCol.whereEqualTo("email", email.trim()).get().await()
                if (!userQuery.isEmpty) {
                    roleManager.logSecurityEvent(
                        userQuery.documents.first().id,
                        SecurityEvent.PASSWORD_RESET_REQUEST
                    )
                }
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun sendEmailVerification(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = firebaseAuth.currentUser ?: throw Exception("المستخدم غير مسجل الدخول")
            user.sendEmailVerification().await()
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    fun isEmailVerified(): Boolean = firebaseAuth.currentUser?.isEmailVerified ?: false

    suspend fun reloadUser(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            firebaseAuth.currentUser?.reload()?.await()
            val user = firebaseAuth.currentUser
            if (user != null && user.isEmailVerified) {
                usersCol.document(user.uid).update(
                    mapOf(
                        "emailVerified" to true,
                        "accountStatus" to AccountStatus.ACTIVE.name
                    )
                ).await()
                roleManager.logSecurityEvent(user.uid, SecurityEvent.EMAIL_VERIFICATION)
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun updateUserPreferences(preferences: UserPreferences): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = getCurrentUserId() ?: return@withContext Result.failure(Exception("Not logged in"))
        runCatching {
            val prefsMap = mapOf(
                "preferences" to mapOf(
                    "language" to preferences.language,
                    "theme" to preferences.theme,
                    "themePalette" to preferences.themePalette,
                    "notificationsEnabled" to preferences.notificationsEnabled,
                    "studyRemindersEnabled" to preferences.studyRemindersEnabled,
                    "emailNotificationsEnabled" to preferences.emailNotificationsEnabled,
                    "soundEnabled" to preferences.soundEnabled,
                    "vibrationEnabled" to preferences.vibrationEnabled,
                    "autoPlayTTS" to preferences.autoPlayTTS,
                    "dailyGoal" to preferences.dailyGoal,
                    "reminderTime" to preferences.reminderTime
                )
            )

            prefs.setDailyGoal(preferences.dailyGoal)
            prefs.setNotificationsEnabled(preferences.notificationsEnabled)
            prefs.setStudyNotifications(preferences.studyRemindersEnabled)
            prefs.setEmailNotifications(preferences.emailNotificationsEnabled)
            prefs.setSoundEnabled(preferences.soundEnabled)
            prefs.setVibrationEnabled(preferences.vibrationEnabled)
            prefs.setAutoPlayTts(preferences.autoPlayTTS)
            prefs.setThemeMode(preferences.theme)
            prefs.setThemePalette(preferences.themePalette)
            prefs.setLanguage(preferences.language)
            prefs.setReminderTime(parseReminderTimeToMillis(preferences.reminderTime))
            runCatching {
                roleManager.updateUserProfile(uid, prefsMap)
            }.onFailure {
                Log.w(TAG, "Skipping remote preferences sync", it)
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = firebaseAuth.currentUser ?: return@withContext Result.failure(Exception("المستخدم غير مسجل الدخول"))
        runCatching {
            deleteDocumentsByField(COLLECTION_SECURITY_LOGS, "userId", user.uid)
            deleteDocumentsByField(COLLECTION_ROLE_ASSIGNMENTS, "targetUserId", user.uid)
            deleteDocumentsByField(COLLECTION_ROLE_ASSIGNMENTS, "assignerId", user.uid)
            usersCol.document(user.uid).delete().await()
            user.delete().await()
            database.clearAllTables()
            prefs.clearSession()
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun linkAnonymousAccount(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = firebaseAuth.currentUser ?: throw Exception("No current user")
            if (!user.isAnonymous) throw Exception("User is not anonymous")

            val credential = EmailAuthProvider.getCredential(email.trim(), password)
            user.linkWithCredential(credential).await()
            persistSession(user.uid, email.trim(), user.displayName ?: email.substringBefore("@"), user.photoUrl?.toString())
            roleManager.updateUserProfile(
                user.uid,
                mapOf(
                    "email" to email.trim(),
                    "accountStatus" to AccountStatus.PENDING_VERIFICATION.name
                )
            )
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun getIdToken(): String? {
        return try {
            firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun persistSession(
        userId: String,
        email: String?,
        displayName: String?,
        avatarUrl: String?
    ) {
        prefs.saveUserId(userId)
        email?.let { prefs.setUserEmail(it) }
        displayName?.let { prefs.setDisplayName(it) }
        avatarUrl?.let { prefs.setAvatarUrl(it) }
    }

    private fun parseReminderTimeToMillis(value: String): Long {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 19
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return ((hour * 60L) + minute) * 60_000L
    }

    private suspend fun deleteDocumentsByField(collection: String, field: String, value: String) {
        val snapshot = firestore.collection(collection)
            .whereEqualTo(field, value)
            .get()
            .await()
        snapshot.documents.forEach { it.reference.delete().await() }
    }
}
