package com.eduspecial.core.ads

import com.eduspecial.BuildConfig

data class AdConfig(
    val appId: String,
    val nativeAdUnitId: String,
    val rewardedAdUnitId: String,
    val nativeFrequency: Int,
    val nativeEnabled: Boolean,
    val rewardedEnabled: Boolean
)

object AdConfigs {
    val current = AdConfig(
        appId = BuildConfig.ADMOB_APP_ID,
        nativeAdUnitId = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID,
        rewardedAdUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
        nativeFrequency = BuildConfig.ADMOB_NATIVE_FREQUENCY.coerceAtLeast(5),
        nativeEnabled = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID.isNotBlank(),
        rewardedEnabled = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID.isNotBlank()
    )
}
