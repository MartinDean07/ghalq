package com.martdean.cutoff.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.martdean.cutoff.data.BlockStore

object VpnController {

    /** True when the user still has to approve Cutoff as a VPN. */
    fun needsConsent(context: Context): Boolean = VpnService.prepare(context) != null

    /** Starts the tunnel or rebuilds it with the current selection. Call from a visible screen. */
    fun start(context: Context): Boolean = runCatching {
        context.startService(
            Intent(context, CutoffVpnService::class.java).setAction(CutoffVpnService.ACTION_APPLY)
        )
        true
    }.getOrDefault(false)

    fun stop(context: Context) {
        BlockStore.get(context).setEnabled(false)
        if (!TunnelState.running.value) return
        runCatching {
            context.startService(
                Intent(context, CutoffVpnService::class.java).setAction(CutoffVpnService.ACTION_STOP)
            )
        }
    }
}
