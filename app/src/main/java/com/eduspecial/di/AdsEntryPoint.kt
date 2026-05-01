package com.eduspecial.di

import com.eduspecial.core.ads.AdManager
import com.eduspecial.core.ads.RewardedAdManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AdsEntryPoint {
    fun adManager(): AdManager
    fun rewardedAdManager(): RewardedAdManager
}
