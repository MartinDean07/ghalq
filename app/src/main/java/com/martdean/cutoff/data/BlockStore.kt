package com.martdean.cutoff.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's choices: which packages are cut off and whether blocking is switched on.
 * Backed by SharedPreferences so the VPN service, the tile and the UI share one source.
 */
class BlockStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _blocked = MutableStateFlow(prefs.getStringSet(KEY_BLOCKED, emptySet())!!.toSet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    /** What the user asked for. Whether the tunnel is actually up lives in [com.martdean.cutoff.vpn.TunnelState]. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Synchronized
    fun setBlocked(packageName: String, block: Boolean) {
        val next = if (block) _blocked.value + packageName else _blocked.value - packageName
        prefs.edit().putStringSet(KEY_BLOCKED, next).apply()
        _blocked.value = next
    }

    @Synchronized
    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
    }

    companion object {
        private const val PREFS = "cutoff"
        private const val KEY_BLOCKED = "blocked_packages"
        private const val KEY_ENABLED = "enabled"

        @Volatile private var instance: BlockStore? = null

        fun get(context: Context): BlockStore =
            instance ?: synchronized(this) {
                instance ?: BlockStore(context.applicationContext).also { instance = it }
            }
    }
}
