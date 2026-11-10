package com.martdean.ghalq.vpn

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.martdean.ghalq.MainActivity
import com.martdean.ghalq.R
import com.martdean.ghalq.data.BlockStore

/** Quick Settings tile: one tap to switch blocking on or off. */
class GhalqTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val store = BlockStore.get(this)
        when {
            TunnelState.running.value -> VpnController.stop(this)
            store.blocked.value.isEmpty() || VpnController.needsConsent(this) -> openApp()
            else -> {
                store.setEnabled(true)
                if (!VpnController.start(this)) openApp()
            }
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val running = TunnelState.running.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) {
                resources.getQuantityString(
                    R.plurals.tile_on, TunnelState.routed.value.size, TunnelState.routed.value.size.toString()
                )
            } else getString(R.string.status_off)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
