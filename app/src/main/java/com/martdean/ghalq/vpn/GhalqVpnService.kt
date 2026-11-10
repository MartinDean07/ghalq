package com.martdean.ghalq.vpn

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.martdean.ghalq.MainActivity
import com.martdean.ghalq.R
import com.martdean.ghalq.data.BlockStore
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

/**
 * A local "blackhole" VPN. Only the selected apps are routed into the tunnel, and nothing
 * is ever written back, so those apps lose internet while every other app is untouched.
 * No packet leaves the phone through Ghalq.
 */
class GhalqVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    @Volatile private var generation = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = BlockStore.get(this)
        if (intent?.action == ACTION_STOP) {
            store.setEnabled(false)
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        // ACTION_APPLY from the app, or android.net.VpnService from Always-on VPN.
        store.setEnabled(true)
        apply()
        return START_STICKY
    }

    private fun apply() {
        val packages = TunnelPlan.packagesToRoute(
            selected = BlockStore.get(this).blocked.value,
            selfPackage = packageName,
            isInstalled = ::isInstalled,
        )
        if (packages.isEmpty()) {
            // Never establish without an allow-list: that would cut the whole phone off.
            shutdown()
            stopSelf()
            return
        }

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress("10.111.222.1", 32)
            .addAddress("fd00:6375:746f::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .setBlocking(false)
            .setConfigureIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val routed = packages.filter { pkg ->
            try {
                builder.addAllowedApplication(pkg); true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        if (routed.isEmpty()) {
            shutdown(); stopSelf(); return
        }

        val fresh = try {
            builder.establish()
        } catch (_: Exception) {
            null
        }
        if (fresh == null) {
            // VPN consent was revoked; the user must open Ghalq and approve again.
            BlockStore.get(this).setEnabled(false)
            shutdown(); stopSelf(); return
        }

        // Swap tunnels without a gap, then retire the old one.
        val old = tunnel
        tunnel = fresh
        startSink(fresh)
        runCatching { old?.close() }
        TunnelState.setRunning(this, true, routed)
    }

    /** Reads and discards every packet so the selected apps simply time out. */
    private fun startSink(pfd: ParcelFileDescriptor) {
        val gen = ++generation
        val seenFlows = object : LinkedHashMap<String, Unit>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 512
        }
        thread(name = "ghalq-sink-$gen", isDaemon = true) {
            val fd = pfd.fileDescriptor
            val buffer = ByteArray(32767)
            val pollFd = StructPollfd().apply {
                this.fd = fd
                events = OsConstants.POLLIN.toShort()
            }
            while (generation == gen) {
                try {
                    if (Os.poll(arrayOf(pollFd), 1000) <= 0) continue
                    while (generation == gen) {
                        val length = Os.read(fd, buffer, 0, buffer.size)
                        if (length <= 0) break
                        inspect(buffer, length, seenFlows)
                    }
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue
                    break // tunnel closed
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun inspect(buffer: ByteArray, length: Int, seenFlows: MutableMap<String, Unit>) {
        val flow = PacketInspector.parse(buffer, length) ?: return
        val isNewAttempt = when (flow.protocol) {
            PacketInspector.TCP -> flow.isTcpSyn
            else -> seenFlows.put(flow.key, Unit) == null
        }
        if (!isNewAttempt) return
        TunnelState.recordAttempt(ownerOf(flow))
    }

    private val ownerCache = HashMap<Int, String?>()

    /** Best effort: which blocked app made this attempt (Android 10+ only). */
    private fun ownerOf(flow: PacketInspector.Flow): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            val uid = cm.getConnectionOwnerUid(
                flow.protocol,
                InetSocketAddress(InetAddress.getByAddress(flow.source), flow.sourcePort),
                InetSocketAddress(InetAddress.getByAddress(flow.destination), flow.destinationPort),
            )
            if (uid < 0) return null
            ownerCache.getOrPut(uid) {
                val routed = TunnelState.routed.value
                packageManager.getPackagesForUid(uid)?.firstOrNull { it in routed }
            }
        }.getOrNull()
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getApplicationInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun shutdown() {
        generation++
        runCatching { tunnel?.close() }
        tunnel = null
        ownerCache.clear()
        TunnelState.setRunning(this, false, emptyList())
    }

    override fun onRevoke() {
        // Another VPN took over, or the user disconnected Ghalq in Settings.
        BlockStore.get(this).setEnabled(false)
        shutdown()
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_APPLY = "com.martdean.ghalq.APPLY"
        const val ACTION_STOP = "com.martdean.ghalq.STOP"
    }
}
