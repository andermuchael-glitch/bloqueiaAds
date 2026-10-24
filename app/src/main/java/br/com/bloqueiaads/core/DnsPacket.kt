package br.com.bloqueiaads.core

/** Helpers for the IPv4/UDP DNS subset handled by this MVP. */
object DnsPacket {
    data class Query(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dns: ByteArray,
        val hostname: String
    )

    fun parseIpv4Udp(packet: ByteArray, length: Int): Query? {
        if (length < 28 || ((packet[0].toInt() ushr 4) and 0xF) != 4) return null
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (ihl < 20 || length < ihl + 8 || (packet[9].toInt() and 0xFF) != 17) return null
        val totalLen = u16(packet, 2).coerceAtMost(length)
        if (totalLen < ihl + 8) return null
        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        if (dstPort != 53) return null
        val udpLen = u16(packet, ihl + 4)
        if (udpLen < 8 || ihl + udpLen > totalLen) return null
        val dns = packet.copyOfRange(ihl + 8, ihl + udpLen)
        val host = readQuestionName(dns) ?: return null
        return Query(packet.copyOfRange(12, 16), packet.copyOfRange(16, 20), srcPort, dns, host)
    }

    fun readQuestionName(dns: ByteArray): String? {
        if (dns.size < 17) return null
        var p = 12
        val labels = mutableListOf<String>()
        var steps = 0
        while (p < dns.size && steps++ < 128) {
            val size = dns[p++].toInt() and 0xFF
            if (size == 0) break
            if ((size and 0xC0) != 0 || size > 63 || p + size > dns.size) return null
            labels += String(dns, p, size, Charsets.US_ASCII)
            p += size
        }
        return labels.takeIf { it.isNotEmpty() }?.joinToString(".")?.lowercase()
    }

    fun nxdomain(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        val out = query.copyOf()
        out[2] = ((out[2].toInt() and 0xFF) or 0x80).toByte()
        out[3] = (((out[3].toInt() and 0xFF) and 0xF0) or 0x03).toByte()
        for (i in 6..11) out[i] = 0
        return out
    }

    fun buildIpv4Udp(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val b = ByteArray(total)
        b[0] = 0x45
        put16(b, 2, total)
        put16(b, 6, 0x4000)
        b[8] = 64
        b[9] = 17
        System.arraycopy(srcIp, 0, b, 12, 4)
        System.arraycopy(dstIp, 0, b, 16, 4)
        put16(b, 10, checksum(b, 0, 20))
        put16(b, 20, srcPort)
        put16(b, 22, dstPort)
        put16(b, 24, 8 + payload.size)
        System.arraycopy(payload, 0, b, 28, payload.size)
        val csum = udpChecksum(srcIp, dstIp, b, 20, 8 + payload.size)
        put16(b, 26, if (csum == 0) 0xFFFF else csum)
        return b
    }

    private fun u16(b: ByteArray, p: Int) = ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
    private fun put16(b: ByteArray, p: Int, v: Int) { b[p] = (v ushr 8).toByte(); b[p + 1] = v.toByte() }

    private fun checksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i < off + len) {
            sum += (((b[i].toInt() and 0xFF) shl 8) or (if (i + 1 < off + len) b[i + 1].toInt() and 0xFF else 0)).toLong()
            i += 2
        }
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    private fun udpChecksum(src: ByteArray, dst: ByteArray, udp: ByteArray, off: Int, len: Int): Int {
        val pseudo = ByteArray(12 + len)
        System.arraycopy(src, 0, pseudo, 0, 4)
        System.arraycopy(dst, 0, pseudo, 4, 4)
        pseudo[9] = 17
        put16(pseudo, 10, len)
        System.arraycopy(udp, off, pseudo, 12, len)
        return checksum(pseudo, 0, pseudo.size)
    }
}
