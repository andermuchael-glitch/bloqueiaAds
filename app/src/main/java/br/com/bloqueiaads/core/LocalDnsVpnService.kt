package br.com.bloqueiaads.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import br.com.bloqueiaads.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Experimental DNS-only local VPN. Not a full traffic tunnel.
 * Fail-safe principle: do not block a domain unless the query was parsed safely.
 */
class LocalDnsVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val upstreams by lazy {
        listOf(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("8.8.8.8")
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        if (running.compareAndSet(false, true)) startDnsLoop()
        return START_STICKY
    }

    private fun startDnsLoop() {
        val builder = Builder()
            .setSession("Bloqueia Ads - DNS local")
            .addAddress("10.111.0.1", 32)
            .addDnsServer("10.111.0.2")
            .addRoute("10.111.0.2", 32)
            .setMtu(1500)

        tun = try { builder.establish() } catch (_: Exception) { null }
        val fd = tun ?: run { running.set(false); stopSelf(); return }

        thread(name = "BloqueiaAdsDns", isDaemon = true) {
            val input = FileInputStream(fd.fileDescriptor)
            val output = FileOutputStream(fd.fileDescriptor)
            val buf = ByteArray(32767)
            while (running.get()) {
                try {
                    val n = input.read(buf)
                    if (n <= 0) continue
                    val query = DnsPacket.parseIpv4Udp(buf, n) ?: continue
                    val response = if (FilterRules.isBlocked(query.hostname)) {
                        DnsPacket.nxdomain(query.dns)
                    } else {
                        forwardDns(query.dns)
                    } ?: continue
                    val packet = DnsPacket.buildIpv4Udp(
                        query.dstIp, query.srcIp, 53, query.srcPort, response
                    )
                    synchronized(output) { output.write(packet) }
                } catch (_: Exception) {
                    // Do not terminate the worker on a single malformed packet.
                }
            }
            try { input.close() } catch (_: Exception) {}
            try { output.close() } catch (_: Exception) {}
        }
    }

    /** Try two public resolvers so one unreachable resolver does not break all DNS. */
    private fun forwardDns(query: ByteArray): ByteArray? {
        for (resolver in upstreams) {
            val socket = DatagramSocket()
            try {
                if (!protect(socket)) continue
                socket.soTimeout = 2500
                socket.send(DatagramPacket(query, query.size, resolver, 53))
                val data = ByteArray(4096)
                val packet = DatagramPacket(data, data.size)
                socket.receive(packet)
                if (packet.length >= 12) return packet.data.copyOf(packet.length)
            } catch (_: Exception) {
                // Try the next resolver.
            } finally {
                socket.close()
            }
        }
        // Fail closed for the query only if both upstream DNS resolvers fail.
        return null
    }

    private fun notification(): Notification {
        val channelId = "bloqueia_ads_protection"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Proteção Bloqueia Ads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bloqueia Ads ativo")
            .setContentText("Filtragem DNS experimental em execução")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running.set(false)
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 3651
    }
}
