package com.eduspecial.core.ads

object AdInsertionStrategy {
    fun <T> injectNativeAds(
        items: List<T>,
        frequency: Int = AdMobConfig.nativeFrequency,
        slotPrefix: String
    ): List<AdFeedItem<T>> {
        if (!AdMobConfig.isNativeEnabled || items.isEmpty() || frequency <= 0) {
            return items.map { AdFeedItem.Content(it) }
        }

        val output = ArrayList<AdFeedItem<T>>(items.size + (items.size / frequency))
        items.forEachIndexed { index, item ->
            output += AdFeedItem.Content(item)
            val itemNumber = index + 1
            if (itemNumber % frequency == 0 && itemNumber < items.size) {
                output += AdFeedItem.Native(slotKey = "$slotPrefix-$itemNumber")
            }
        }
        return output
    }
}
