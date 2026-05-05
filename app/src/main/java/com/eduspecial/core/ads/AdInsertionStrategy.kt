package com.eduspecial.core.ads

object AdInsertionStrategy {
    fun <T> injectNativeAds(
        items: List<T>,
        frequency: Int = AdMobConfig.nativeFrequency,
        slotPrefix: String
    ): List<AdFeedItem<T>> {
        return items.map { AdFeedItem.Content(it) }
    }
}
