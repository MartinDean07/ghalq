package com.martdean.ghalq.vpn

/**
 * Minimal IPv4/IPv6 header reader. Ghalq never forwards packets; it only looks at the
 * headers to count how often a blocked app tries to connect.
 */
object PacketInspector {

    const val TCP = 6
    const val UDP = 17

    class Flow(
        val protocol: Int,
        val source: ByteArray,
        val sourcePort: Int,
        val destination: ByteArray,
        val destinationPort: Int,
        /** True for the first packet of a TCP connection (SYN without ACK). */
        val isTcpSyn: Boolean,
    ) {
        /** Stable identity of the socket pair, used to count each UDP flow once. */
        val key: String
            get() = buildString {
                append(protocol).append('|')
                source.joinTo(this, ".") { (it.toInt() and 0xFF).toString() }
                append(':').append(sourcePort).append('>')
                destination.joinTo(this, ".") { (it.toInt() and 0xFF).toString() }
                append(':').append(destinationPort)
            }
    }

    fun parse(packet: ByteArray, length: Int): Flow? {
        if (length < 1 || length > packet.size) return null
        return when ((packet[0].toInt() shr 4) and 0x0F) {
            4 -> parseV4(packet, length)
            6 -> parseV6(packet, length)
            else -> null
        }
    }

    private fun parseV4(p: ByteArray, len: Int): Flow? {
        if (len < 20) return null
        val headerLength = (p[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || len < headerLength) return null
        // Only the first fragment carries the transport header.
        val fragmentOffset = ((p[6].toInt() and 0x1F) shl 8) or (p[7].toInt() and 0xFF)
        if (fragmentOffset != 0) return null
        val protocol = p[9].toInt() and 0xFF
        return transport(p, len, headerLength, protocol, p.copyOfRange(12, 16), p.copyOfRange(16, 20))
    }

    private fun parseV6(p: ByteArray, len: Int): Flow? {
        if (len < 40) return null
        // Extension headers are rare for app traffic; skip those packets rather than guess.
        val nextHeader = p[6].toInt() and 0xFF
        return transport(p, len, 40, nextHeader, p.copyOfRange(8, 24), p.copyOfRange(24, 40))
    }

    private fun transport(
        p: ByteArray, len: Int, offset: Int, protocol: Int, src: ByteArray, dst: ByteArray,
    ): Flow? {
        val needed = when (protocol) {
            TCP -> 14
            UDP -> 8
            else -> return null
        }
        if (len < offset + needed) return null
        val sport = u16(p, offset)
        val dport = u16(p, offset + 2)
        val syn = protocol == TCP && run {
            val flags = p[offset + 13].toInt() and 0xFF
            (flags and 0x02) != 0 && (flags and 0x10) == 0
        }
        return Flow(protocol, src, sport, dst, dport, syn)
    }

    private fun u16(p: ByteArray, i: Int) = ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)
}
