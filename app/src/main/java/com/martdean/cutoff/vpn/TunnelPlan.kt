package com.martdean.cutoff.vpn

/**
 * Decides which packages go into the blackhole tunnel.
 *
 * Safety rule: VpnService routes EVERY app through the tunnel when no app is added with
 * addAllowedApplication(). An empty plan must therefore never be turned into a tunnel,
 * or the whole phone would go offline.
 */
object TunnelPlan {

    fun packagesToRoute(
        selected: Set<String>,
        selfPackage: String,
        isInstalled: (String) -> Boolean,
    ): List<String> = selected
        .asSequence()
        .filter { it.isNotBlank() && it != selfPackage }
        .filter(isInstalled)
        .sorted()
        .toList()
}
