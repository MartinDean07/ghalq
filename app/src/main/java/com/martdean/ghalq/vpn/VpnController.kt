package com.martdean.ghalq.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.martdean.ghalq.data.BlockStore

object VpnController {

    /** True when the user still has to approve Ghalq as a VPN. */
    fun needsConsent(context: Context): Boolean = VpnService.prepare(context) != null

    /** Starts the tunnel or rebuilds it with the current selection. Call from a visible screen. */
    fun start(context: Context): Boolean = runCatching {
        context.startService(
            Intent(context, GhalqVpnService::class.java).setAction(GhalqVpnService.ACTION_APPLY)
        )
        true
    }.getOrDefault(false)

    fun stop(context: Context) {
        BlockStore.get(context).setEnabled(false)
        if (!TunnelState.running.value) return
        runCatching {
            context.startService(
                Intent(context, GhalqVpnService::class.java).setAction(GhalqVpnService.ACTION_STOP)
            )
        }
    }
}
