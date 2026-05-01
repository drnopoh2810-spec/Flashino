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
        appId = AdMobConfig.appId,
        nativeAdUnitId = AdMobConfig.nativeAdUnitId,
        rewardedAdUnitId = AdMobConfig.rewardedAdUnitId,
        nativeFrequency = BuildConfig.ADMOB_NATIVE_FREQUENCY.coerceAtLeast(5),
        nativeEnabled = AdMobConfig.isNativeEnabled,
        rewardedEnabled = AdMobConfig.isRewardedEnabled
    )
}
