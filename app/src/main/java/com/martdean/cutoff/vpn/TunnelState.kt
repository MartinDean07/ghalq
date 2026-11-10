package com.martdean.cutoff.vpn

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Live, in-memory state of the tunnel, shared by the service, the UI and the tile. */
object TunnelState {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Packages currently inside the tunnel (installed and selected). */
    private val _routed = MutableStateFlow<List<String>>(emptyList())
    val routed: StateFlow<List<String>> = _routed.asStateFlow()

    private val _totalAttempts = MutableStateFlow(0)
    val totalAttempts: StateFlow<Int> = _totalAttempts.asStateFlow()

    private val _attemptsByApp = MutableStateFlow<Map<String, Int>>(emptyMap())
    val attemptsByApp: StateFlow<Map<String, Int>> = _attemptsByApp.asStateFlow()

    fun setRunning(context: Context, running: Boolean, routed: List<String>) {
        _running.value = running
        _routed.value = if (running) routed else emptyList()
        refreshTile(context)
    }

    fun recordAttempt(packageName: String?) {
        _totalAttempts.update { it + 1 }
        if (packageName != null) {
            _attemptsByApp.update { it + (packageName to ((it[packageName] ?: 0) + 1)) }
        }
    }

    fun resetCounters() {
        _totalAttempts.value = 0
        _attemptsByApp.value = emptyMap()
    }

    fun refreshTile(context: Context) {
        runCatching {
            TileService.requestListeningState(
                context, ComponentName(context, CutoffTileService::class.java)
            )
        }
    }
}
