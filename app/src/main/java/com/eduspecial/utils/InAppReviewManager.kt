package com.eduspecial.utils

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Manages the Google Play In-App Review flow.
 *
 * Uses a simple two-step callback chain instead of coroutines to avoid
 * Kotlin nullable/platform-type mismatches with the Play Core Java API.
 *
 * Silently no-ops if the review flow is unavailable or fails at any step.
 */
class InAppReviewManager(private val activity: Activity) {

    private val manager: ReviewManager = ReviewManagerFactory.create(activity)
    private var hasRequestedThisSession = false

    /**
     * Requests and launches the in-app review dialog.
     * Safe to call multiple times — only triggers once per app session.
     * Google Play internally throttles how often the dialog actually appears.
     */
    fun requestReview() {
        if (hasRequestedThisSession) return
        hasRequestedThisSession = true

        // Step 1: request ReviewInfo
        manager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo: ReviewInfo ->
                // Step 2: launch the review flow with the non-null ReviewInfo
                launchReview(reviewInfo)
            }
            .addOnFailureListener { e ->
                Log.w("InAppReview", "requestReviewFlow failed silently", e)
            }
    }

    private fun launchReview(reviewInfo: ReviewInfo) {
        manager.launchReviewFlow(activity, reviewInfo)
            .addOnCompleteListener {
                // Flow complete — dialog was shown (or throttled by Play)
                Log.d("InAppReview", "Review flow completed")
            }
            .addOnFailureListener { e ->
                Log.w("InAppReview", "launchReviewFlow failed silently", e)
            }
    }
}
