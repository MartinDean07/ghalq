package com.martdean.cutoff.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelPlanTest {

    private val installed = setOf("com.zhiliaoapp.musically", "com.android.chrome", "com.martdean.cutoff")

    @Test
    fun emptySelectionProducesEmptyPlan() {
        // An empty plan must never become a tunnel: the service stops instead.
        assertTrue(TunnelPlan.packagesToRoute(emptySet(), "com.martdean.cutoff") { it in installed }.isEmpty())
    }

    @Test
    fun neverRoutesItself() {
        val plan = TunnelPlan.packagesToRoute(setOf("com.martdean.cutoff"), "com.martdean.cutoff") { it in installed }
        assertTrue(plan.isEmpty())
    }

    @Test
    fun dropsUninstalledAndBlankPackages() {
        val plan = TunnelPlan.packagesToRoute(
            setOf("com.android.chrome", "gone.app", " "), "com.martdean.cutoff"
        ) { it in installed }
        assertEquals(listOf("com.android.chrome"), plan)
    }

    @Test
    fun planIsSortedAndStable() {
        val plan = TunnelPlan.packagesToRoute(
            setOf("com.zhiliaoapp.musically", "com.android.chrome"), "com.martdean.cutoff"
        ) { it in installed }
        assertEquals(listOf("com.android.chrome", "com.zhiliaoapp.musically"), plan)
    }
}
