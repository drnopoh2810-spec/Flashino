package com.eduspecial.data.repository

import com.eduspecial.data.local.dao.AnalyticsDao
import com.eduspecial.data.local.entities.DailyReviewLogEntity
import com.eduspecial.domain.model.DailyProgress
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepository @Inject constructor(
    private val analyticsDao: AnalyticsDao
) {
    suspend fun recordReview(reviewCount: Int = 1, archivedCount: Int = 0) {
        val today = LocalDate.now().toEpochDay()
        // Try to increment existing row; if no row exists, upsert a new one
        val existing = analyticsDao.getLogsFrom(today).firstOrNull { it.dayEpoch == today }
        if (existing != null) {
            analyticsDao.incrementLog(today, reviewCount, archivedCount)
        } else {
            analyticsDao.upsertLog(
                DailyReviewLogEntity(
                    dayEpoch = today,
                    reviewCount = reviewCount,
                    archivedCount = archivedCount
                )
            )
        }
    }

    suspend fun getStreak(): Int {
        val today = LocalDate.now().toEpochDay()
        val logs = analyticsDao.getLogsFrom(today - 365)
        // Build a set of days with reviews
        val daysWithReviews = logs.filter { it.reviewCount > 0 }.map { it.dayEpoch }.toSet()
        // Count consecutive days ending today
        var streak = 0
        var day = today
        while (daysWithReviews.contains(day)) {
            streak++
            day--
        }
        return streak
    }

    suspend fun getLast7Days(): List<DailyProgress> {
        val today = LocalDate.now().toEpochDay()
        val logs = analyticsDao.getLast7Days()
        val logMap = logs.associateBy { it.dayEpoch }
        // Return 7 days ending today, filling gaps with 0
        return (6 downTo 0).map { daysAgo ->
            val dayEpoch = today - daysAgo
            val log = logMap[dayEpoch]
            DailyProgress(dayEpoch = dayEpoch, reviewCount = log?.reviewCount ?: 0)
        }
    }

    suspend fun getTodayReviewCount(): Int {
        val today = LocalDate.now().toEpochDay()
        val logs = analyticsDao.getLogsFrom(today)
        return logs.firstOrNull { it.dayEpoch == today }?.reviewCount ?: 0
    }
}
