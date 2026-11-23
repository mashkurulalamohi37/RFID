package com.rfid.rolltrack

import com.rfid.rolltrack.data.ScanRepository
import com.rfid.rolltrack.data.remote.ApiException
import com.rfid.rolltrack.data.remote.ApiLog
import com.rfid.rolltrack.data.remote.HttpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Knows whether the ERP server (not just Wi-Fi) is reachable.
 * Pings a harmless URL: GET snym_erp/api/load answers 405 (handler exists, GET not allowed) and runs no SQL.
 * When the server comes back, queued batches are resent automatically.
 */
class ServerHealth(
    private val http: HttpTransport,
    private val network: NetworkMonitor,
    private val baseUrl: () -> String,
    private val repo: () -> ScanRepository
) {
    sealed class State(val label: String) {
        object Unknown : State("Checking…")
        object NoInternet : State("No internet")
        data class Up(val ms: Long) : State("Server OK")
        data class Down(val reason: String) : State("Server down")
    }

    private val _state = MutableStateFlow<State>(State.Unknown)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _lastChecked = MutableStateFlow(0L)
    val lastChecked: StateFlow<Long> = _lastChecked.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    fun pingUrl(): String = "${baseUrl().trimEnd('/')}/snym_erp/api/load"

    fun start() {
        scope.launch {
            network.online.collect { check() }
        }
        scope.launch {
            while (true) {
                delay(30_000)
                check()
            }
        }
    }

    /** Runs a check now and returns the new state. */
    suspend fun check(): State = lock.withLock {
        val before = _state.value
        val now = if (!network.online.value) {
            State.NoInternet
        } else {
            try {
                State.Up(http.ping(pingUrl()))
            } catch (e: Exception) {
                // Never crash the app from the background health check.
                State.Down(e.message ?: e.javaClass.simpleName)
            }
        }
        _state.value = now
        _lastChecked.value = System.currentTimeMillis()
        if (now::class != before::class) {
            ApiLog.info("server health: ${before.label} -> ${now.label}" + if (now is State.Down) " (${now.reason})" else "")
        }
        if (now is State.Up && before !is State.Up) {
            try {
                if (repo().observePendingCount().first() > 0) {
                    ApiLog.info("auto-retry pending: " + repo().retryPending())
                }
            } catch (e: Exception) {
                ApiLog.warn("auto-retry failed: ${e.message}")
            }
        }
        now
    }
}
