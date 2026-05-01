package com.eduspecial.core.config

/**
 * App-level constants that are NOT secrets.
 * Runtime service keys are supplied through BuildConfig-backed configuration.
 */
object AppConfig {
    /** App name - safe constant, not a secret. */
    const val APP_NAME = "\u0642\u0635\u0627\u0635\u0629"

    /** Default page size for pagination - safe constant. */
    const val DEFAULT_PAGE_SIZE = 20

    /** Max retry count for network operations - safe constant. */
    const val MAX_NETWORK_RETRIES = 2
}
