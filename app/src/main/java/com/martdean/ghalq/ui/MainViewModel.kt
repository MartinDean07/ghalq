package com.martdean.ghalq.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.martdean.ghalq.data.AppInfo
import com.martdean.ghalq.data.AppRepository
import com.martdean.ghalq.data.BlockStore
import com.martdean.ghalq.vpn.TunnelState
import com.martdean.ghalq.vpn.VpnController
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Filter { All, Blocked }

data class UiState(
    val loading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val blocked: Set<String> = emptySet(),
    val running: Boolean = false,
    val query: String = "",
    val filter: Filter = Filter.All,
    val attempts: Int = 0,
    val attemptsByApp: Map<String, Int> = emptyMap(),
) {
    val visible: List<AppInfo>
        get() = apps.asSequence()
            .filter { filter == Filter.All || it.packageName in blocked }
            .filter {
                query.isBlank() ||
                    it.label.contains(query.trim(), ignoreCase = true) ||
                    it.packageName.contains(query.trim(), ignoreCase = true)
            }
            .toList()
}

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repository = AppRepository(app)
    private val store = BlockStore.get(app)

    private val apps = MutableStateFlow<List<AppInfo>?>(null)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(Filter.All)

    private val counters = combine(TunnelState.totalAttempts, TunnelState.attemptsByApp) { t, m -> t to m }

    val state: StateFlow<UiState> = combine(
        combine(apps, store.blocked, TunnelState.running) { a, b, r -> Triple(a, b, r) },
        query, filter, counters,
    ) { (a, b, r), q, f, (t, m) ->
        UiState(
            loading = a == null, apps = a.orEmpty(), blocked = b, running = r,
            query = q, filter = f, attempts = t, attemptsByApp = m,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        refreshApps()
        // While blocking is on, apply selection changes to the live tunnel (debounced).
        viewModelScope.launch {
            store.blocked.drop(1).debounce(400).collect { selected ->
                if (!store.enabled.value) return@collect
                if (selected.isEmpty()) VpnController.stop(getApplication())
                else VpnController.start(getApplication())
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch { apps.value = repository.loadApps() }
    }

    fun toggle(packageName: String) =
        store.setBlocked(packageName, packageName !in store.blocked.value)

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: Filter) { filter.value = value }

    /** Called after VPN consent is granted (or was already granted). */
    fun turnOn() {
        if (store.blocked.value.isEmpty()) return
        TunnelState.resetCounters()
        store.setEnabled(true)
        VpnController.start(getApplication())
    }

    fun turnOff() = VpnController.stop(getApplication())
}
