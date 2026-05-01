package com.eduspecial.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Circuit Breaker pattern — protects the backend from cascading failures
 * when millions of users hit the API simultaneously.
 *
 * States:
 *  CLOSED   → normal operation, requests pass through
 *  OPEN     → backend is failing, requests are blocked immediately
 *  HALF_OPEN → testing if backend recovered, one probe request allowed
 *
 * Thresholds (tunable for scale):
 *  - Opens after [failureThreshold] consecutive failures
 *  - Stays open for [openDurationMs] before trying HALF_OPEN
 *  - Resets on first success in HALF_OPEN
 */
@Singleton
class CircuitBreaker @Inject constructor() {

    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val failureThreshold = 5
    private val openDurationMs = 30_000L   // 30 seconds

    private val failureCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0L)
    private val mutex = Mutex()

    @Volatile private var state = State.CLOSED

    /**
     * Execute [block] through the circuit breaker.
     * Throws [CircuitOpenException] if the circuit is OPEN.
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        checkState()
        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private suspend fun checkState() {
        mutex.withLock {
            when (state) {
                State.OPEN -> {
                    val elapsed = System.currentTimeMillis() - lastFailureTime.get()
                    if (elapsed >= openDurationMs) {
                        state = State.HALF_OPEN
                    } else {
                        throw CircuitOpenException(
                            "Circuit is OPEN. Backend unavailable. Retry in ${(openDurationMs - elapsed) / 1000}s"
                        )
                    }
                }
                State.HALF_OPEN, State.CLOSED -> { /* allow through */ }
            }
        }
    }

    private suspend fun onSuccess() {
        mutex.withLock {
            failureCount.set(0)
            state = State.CLOSED
        }
    }

    private suspend fun onFailure() {
        mutex.withLock {
            lastFailureTime.set(System.currentTimeMillis())
            val failures = failureCount.incrementAndGet()
            if (failures >= failureThreshold || state == State.HALF_OPEN) {
                state = State.OPEN
            }
        }
    }

    fun getState(): State = state
    fun getFailureCount(): Int = failureCount.get()
}

class CircuitOpenException(message: String) : Exception(message)
