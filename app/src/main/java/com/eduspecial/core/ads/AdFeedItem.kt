package com.eduspecial.core.ads

sealed interface AdFeedItem<out T> {
    data class Content<T>(val value: T) : AdFeedItem<T>
    data class Native(val slotKey: String) : AdFeedItem<Nothing>
}
