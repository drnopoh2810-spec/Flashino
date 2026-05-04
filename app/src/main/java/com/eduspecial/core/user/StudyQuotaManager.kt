package com.eduspecial.core.user

import com.eduspecial.utils.UserPreferencesDataStore
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class DailyGoalQuotaState(
    val selectedGoal: Int,
    val unlockedCap: Int,
    val unlocksUsedToday: Int,
    val canUnlockMore: Boolean
)

data class DailyCreationQuotaState(
    val createdToday: Int,
    val unlockedCap: Int,
    val unlocksUsedToday: Int
) {
    val remaining: Int get() = (unlockedCap - createdToday).coerceAtLeast(0)
    val requiresUnlock: Boolean get() = createdToday >= unlockedCap
}

@Singleton
class StudyQuotaManager @Inject constructor(
    private val prefs: UserPreferencesDataStore
) {
    companion object {
        const val BASE_DAILY_GOAL = 20
        const val DAILY_GOAL_STEP = 10
        const val BASE_DAILY_CREATIONS = 20
        const val DAILY_CREATION_STEP = 15
    }

    suspend fun getDailyGoalQuotaState(): DailyGoalQuotaState {
        syncDailyResets()
        val (_, unlocks) = prefs.getDailyGoalUnlockMeta()
        val cap = BASE_DAILY_GOAL + unlocks * DAILY_GOAL_STEP
        val storedGoal = prefs.dailyGoal.first()
        val selected = storedGoal.coerceIn(BASE_DAILY_GOAL, cap)
        if (selected != storedGoal) {
            prefs.setDailyGoal(selected)
        }
        return DailyGoalQuotaState(
            selectedGoal = selected,
            unlockedCap = cap,
            unlocksUsedToday = unlocks,
            canUnlockMore = true
        )
    }

    suspend fun setSelectedDailyGoal(goal: Int): DailyGoalQuotaState {
        val state = getDailyGoalQuotaState()
        prefs.setDailyGoal(goal.coerceIn(BASE_DAILY_GOAL, state.unlockedCap))
        return getDailyGoalQuotaState()
    }

    suspend fun unlockDailyGoalStep(): DailyGoalQuotaState {
        syncDailyResets()
        val today = LocalDate.now().toString()
        val (_, unlocks) = prefs.getDailyGoalUnlockMeta()
        val nextUnlockCount = unlocks + 1
        val nextCap = BASE_DAILY_GOAL + nextUnlockCount * DAILY_GOAL_STEP
        prefs.setDailyGoalUnlockMeta(today, nextUnlockCount)
        prefs.setDailyGoal(nextCap)
        return getDailyGoalQuotaState()
    }

    suspend fun getDailyCreationQuotaState(createdToday: Int): DailyCreationQuotaState {
        syncDailyResets()
        val (_, unlocks) = prefs.getDailyCreateUnlockMeta()
        val cap = BASE_DAILY_CREATIONS + unlocks * DAILY_CREATION_STEP
        return DailyCreationQuotaState(
            createdToday = createdToday,
            unlockedCap = cap,
            unlocksUsedToday = unlocks
        )
    }

    suspend fun unlockDailyCreationStep(createdToday: Int): DailyCreationQuotaState {
        syncDailyResets()
        val today = LocalDate.now().toString()
        val (_, unlocks) = prefs.getDailyCreateUnlockMeta()
        prefs.setDailyCreateUnlockMeta(today, unlocks + 1)
        return getDailyCreationQuotaState(createdToday)
    }

    private suspend fun syncDailyResets() {
        val today = LocalDate.now().toString()

        val (goalDate, _) = prefs.getDailyGoalUnlockMeta()
        if (goalDate != today) {
            prefs.setDailyGoalUnlockMeta(today, 0)
            prefs.setDailyGoal(BASE_DAILY_GOAL)
        }

        val (createDate, _) = prefs.getDailyCreateUnlockMeta()
        if (createDate != today) {
            prefs.setDailyCreateUnlockMeta(today, 0)
        }
    }
}
