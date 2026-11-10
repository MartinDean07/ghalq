package com.martdean.cutoff.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.martdean.cutoff.data.BlockStore

/** Restores blocking after a reboot or an app update if it was on before. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val store = BlockStore.get(context)
        if (store.enabled.value && store.blocked.value.isNotEmpty() && !VpnController.needsConsent(context)) {
            VpnController.start(context)
        }
    }
}
