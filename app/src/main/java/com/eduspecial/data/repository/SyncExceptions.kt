package com.eduspecial.data.repository

class NonRetryableSyncException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
