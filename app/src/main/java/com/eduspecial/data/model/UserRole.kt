package com.eduspecial.data.model

/**
 * User roles in the EduSpecial system with hierarchical permissions.
 */
enum class UserRole(val displayName: String, val level: Int) {
    USER("مستخدم", 1),
    MODERATOR("مراجع", 2),
    ADMIN("مدير", 3);
    
    /**
     * Check if this role has permission to perform an action that requires a specific role.
     */
    fun hasPermission(requiredRole: UserRole): Boolean {
        return this.level >= requiredRole.level
    }
    
    companion object {
        fun fromString(role: String?): UserRole {
            return when (role?.uppercase()) {
                "ADMIN" -> ADMIN
                "MODERATOR" -> MODERATOR
                "USER" -> USER
                else -> USER // Default role
            }
        }
    }
}

/**
 * Specific permissions that can be granted to users.
 */
enum class Permission(val requiredRole: UserRole, val description: String) {
    // User permissions
    CREATE_FLASHCARD(UserRole.USER, "إنشاء بطاقات تعليمية"),
    CREATE_QUESTION(UserRole.USER, "طرح أسئلة"),
    CREATE_ANSWER(UserRole.USER, "الإجابة على الأسئلة"),
    REPORT_CONTENT(UserRole.USER, "الإبلاغ عن المحتوى"),
    
    // Moderator permissions
    REVIEW_CONTENT(UserRole.MODERATOR, "مراجعة المحتوى"),
    APPROVE_CONTENT(UserRole.MODERATOR, "الموافقة على المحتوى"),
    REJECT_CONTENT(UserRole.MODERATOR, "رفض المحتوى"),
    VIEW_REPORTS(UserRole.MODERATOR, "عرض التقارير"),
    
    // Admin permissions
    MANAGE_USERS(UserRole.ADMIN, "إدارة المستخدمين"),
    ASSIGN_ROLES(UserRole.ADMIN, "تعيين الأدوار"),
    DELETE_ANY_CONTENT(UserRole.ADMIN, "حذف أي محتوى"),
    VIEW_ANALYTICS(UserRole.ADMIN, "عرض الإحصائيات"),
    MANAGE_SYSTEM(UserRole.ADMIN, "إدارة النظام");
    
    /**
     * Check if a user role has this permission.
     */
    fun isGrantedTo(userRole: UserRole): Boolean {
        return userRole.hasPermission(this.requiredRole)
    }
}

/**
 * User account status.
 */
enum class AccountStatus(val displayName: String) {
    ACTIVE("نشط"),
    SUSPENDED("معلق"),
    BANNED("محظور"),
    PENDING_VERIFICATION("في انتظار التحقق");
    
    fun isActive(): Boolean = this == ACTIVE
    
    companion object {
        fun fromString(status: String?): AccountStatus {
            return when (status?.uppercase()) {
                "ACTIVE" -> ACTIVE
                "SUSPENDED" -> SUSPENDED
                "BANNED" -> BANNED
                "PENDING_VERIFICATION" -> PENDING_VERIFICATION
                else -> PENDING_VERIFICATION
            }
        }
    }
}

/**
 * Enhanced user profile with role and security information.
 */
data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: UserRole = UserRole.USER,
    val accountStatus: AccountStatus = AccountStatus.PENDING_VERIFICATION,
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val twoFactorEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val profileImageUrl: String? = null,
    val bio: String? = null,
    val points: Int = 0,
    val contributionCount: Int = 0,
    val moderationScore: Float = 0.5f, // 0.0 to 1.0, higher is more trusted
    val preferences: UserPreferences = UserPreferences()
)

/**
 * User preferences and settings.
 */
data class UserPreferences(
    val language: String = "ar",
    val theme: String = "system", // light, dark, system
    val themePalette: String = "qusasa",
    val notificationsEnabled: Boolean = true,
    val studyRemindersEnabled: Boolean = true,
    val emailNotificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val autoPlayTTS: Boolean = false,
    val dailyGoal: Int = 20, // flashcards per day
    val reminderTime: String = "19:00" // 24-hour format
)

/**
 * Security event types for audit logging.
 */
enum class SecurityEvent(val description: String) {
    LOGIN("تسجيل دخول"),
    LOGOUT("تسجيل خروج"),
    PASSWORD_CHANGE("تغيير كلمة المرور"),
    EMAIL_CHANGE("تغيير البريد الإلكتروني"),
    ROLE_CHANGE("تغيير الدور"),
    ACCOUNT_SUSPENSION("تعليق الحساب"),
    ACCOUNT_REACTIVATION("إعادة تفعيل الحساب"),
    FAILED_LOGIN("محاولة دخول فاشلة"),
    PASSWORD_RESET_REQUEST("طلب إعادة تعيين كلمة المرور"),
    EMAIL_VERIFICATION("التحقق من البريد الإلكتروني"),
    TWO_FACTOR_ENABLED("تفعيل المصادقة الثنائية"),
    TWO_FACTOR_DISABLED("إلغاء المصادقة الثنائية")
}

/**
 * Security audit log entry.
 */
data class SecurityAuditLog(
    val id: String = "",
    val userId: String,
    val event: SecurityEvent,
    val timestamp: Long = System.currentTimeMillis(),
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val details: Map<String, Any> = emptyMap(),
    val success: Boolean = true
)
