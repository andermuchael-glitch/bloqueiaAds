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
 * DNS-only local VPN for the MVP. It does not tunnel general traffic to a remote VPN.
 * Current scope: IPv4/UDP DNS queries only.
 */
class LocalDnsVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val upstream by lazy { InetAddress.getByName("1.1.1.1") }

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
                    if (running.get()) { /* Ignore transient packet errors; loop remains active. */ }
                }
            }
            try { input.close() } catch (_: Exception) {}
            try { output.close() } catch (_: Exception) {}
        }
    }

    private fun forwardDns(query: ByteArray): ByteArray? {
        val socket = DatagramSocket()
        return try {
            if (!protect(socket)) return null
            socket.soTimeout = 3500
            socket.send(DatagramPacket(query, query.size, upstream, 53))
            val data = ByteArray(4096)
            val packet = DatagramPacket(data, data.size)
            socket.receive(packet)
            packet.data.copyOf(packet.length)
        } catch (_: Exception) {
            null
        } finally {
            socket.close()
        }
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
            .setContentText("Filtragem DNS local em execução")
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
