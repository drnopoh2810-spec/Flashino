package com.eduspecial.core.user

object VerificationRules {
    const val CONTRIBUTION_POINTS_THRESHOLD = 5000
    private const val OWNER_EMAIL = "mahmoudnabihsaleh@gmail.com"
    private const val OWNER_USER_ID = "7ukTBlMRYaeTcIilttkf7IBoYiu1"

    fun isOwnerAccount(userId: String?, email: String?): Boolean {
        return userId == OWNER_USER_ID || email.equals(OWNER_EMAIL, ignoreCase = true)
    }

    fun isVerified(userId: String?, email: String?, points: Int): Boolean {
        return isOwnerAccount(userId, email) || points >= CONTRIBUTION_POINTS_THRESHOLD
    }
}
