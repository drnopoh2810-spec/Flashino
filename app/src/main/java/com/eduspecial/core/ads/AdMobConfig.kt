package com.eduspecial.core.ads

import com.eduspecial.BuildConfig

object AdMobConfig {
    private const val NATIVE_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val REWARDED_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    val appId: String = BuildConfig.ADMOB_APP_ID.trim()
    val nativeAdUnitId: String = if (BuildConfig.DEBUG) {
        NATIVE_TEST_AD_UNIT_ID
    } else {
        BuildConfig.ADMOB_NATIVE_AD_UNIT_ID.trim()
    }
    val rewardedAdUnitId: String = if (BuildConfig.DEBUG) {
        REWARDED_TEST_AD_UNIT_ID
    } else {
        BuildConfig.ADMOB_REWARDED_AD_UNIT_ID.trim()
    }
    val nativeFrequency: Int = BuildConfig.ADMOB_NATIVE_FREQUENCY.coerceAtLeast(0)

    val isNativeEnabled: Boolean
        get() = appId.isNotBlank() && nativeAdUnitId.isNotBlank() && nativeFrequency > 0

    val isRewardedEnabled: Boolean
        get() = appId.isNotBlank() && rewardedAdUnitId.isNotBlank()
}
