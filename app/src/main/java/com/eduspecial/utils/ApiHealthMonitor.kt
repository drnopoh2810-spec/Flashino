package com.eduspecial.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors API health and exposes it as a StateFlow for the UI.
 *
 * The UI can observe [status] to show a banner when the backend is degraded,
 * so users understand why content might be loading from cache.
 *
 * This is important at scale: when millions of users hit the app simultaneously,
 * the backend may be temporarily overloaded. Instead of showing cryptic errors,
 * we show a friendly "working from cache" message.
 */
@Singleton
class ApiHealthMonitor @Inject constructor(
    private val circuitBreaker: CircuitBreaker,
    private val networkMonitor: NetworkMonitor
) {
    enum class ApiStatus {
        HEALTHY,      // All good
        DEGRADED,     // Some failures but still working
        OFFLINE,      // No network
        UNAVAILABLE   // Circuit open — backend down
    }

    private val _status = MutableStateFlow(ApiStatus.HEALTHY)
    val status: StateFlow<ApiStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            // Poll circuit breaker state every 10 seconds
            while (true) {
                val isOnline = networkMonitor.isCurrentlyOnline()
                _status.value = when {
                    !isOnline -> ApiStatus.OFFLINE
                    circuitBreaker.getState() == CircuitBreaker.State.OPEN -> ApiStatus.UNAVAILABLE
                    circuitBreaker.getFailureCount() > 0 -> ApiStatus.DEGRADED
                    else -> ApiStatus.HEALTHY
                }
                delay(10_000)
            }
        }
    }

    /** Call this when a network request succeeds to update status immediately */
    fun reportSuccess() {
        if (_status.value != ApiStatus.OFFLINE) {
            _status.value = ApiStatus.HEALTHY
        }
    }

    /** Call this when a network request fails */
    fun reportFailure() {
        if (_status.value == ApiStatus.HEALTHY) {
            _status.value = ApiStatus.DEGRADED
        }
    }
}
