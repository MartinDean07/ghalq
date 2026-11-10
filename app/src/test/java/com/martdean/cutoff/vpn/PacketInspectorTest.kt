package com.martdean.cutoff.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketInspectorTest {

    private fun ipv4(protocol: Int, transport: ByteArray, fragOffset: Int = 0): ByteArray {
        val h = ByteArray(20)
        h[0] = 0x45
        val total = 20 + transport.size
        h[2] = (total shr 8).toByte(); h[3] = total.toByte()
        h[6] = ((fragOffset shr 8) and 0x1F).toByte(); h[7] = fragOffset.toByte()
        h[8] = 64; h[9] = protocol.toByte()
        byteArrayOf(10, 111, 222.toByte(), 1).copyInto(h, 12)
        byteArrayOf(142.toByte(), 250.toByte(), 74, 46).copyInto(h, 16)
        return h + transport
    }

    private fun tcp(sport: Int, dport: Int, flags: Int): ByteArray {
        val t = ByteArray(20)
        t[0] = (sport shr 8).toByte(); t[1] = sport.toByte()
        t[2] = (dport shr 8).toByte(); t[3] = dport.toByte()
        t[12] = 0x50; t[13] = flags.toByte()
        return t
    }

    private fun udp(sport: Int, dport: Int): ByteArray {
        val u = ByteArray(8)
        u[0] = (sport shr 8).toByte(); u[1] = sport.toByte()
        u[2] = (dport shr 8).toByte(); u[3] = dport.toByte()
        return u
    }

    @Test
    fun parsesTcpSyn() {
        val p = ipv4(6, tcp(40000, 443, 0x02))
        val flow = PacketInspector.parse(p, p.size)
        assertNotNull(flow)
        assertEquals(6, flow!!.protocol)
        assertEquals(40000, flow.sourcePort)
        assertEquals(443, flow.destinationPort)
        assertTrue(flow.isTcpSyn)
        assertEquals("6|10.111.222.1:40000>142.250.74.46:443", flow.key)
    }

    @Test
    fun synAckAndPlainAckAreNotNewAttempts() {
        val synAck = ipv4(6, tcp(40000, 443, 0x12))
        val ack = ipv4(6, tcp(40000, 443, 0x10))
        assertFalse(PacketInspector.parse(synAck, synAck.size)!!.isTcpSyn)
        assertFalse(PacketInspector.parse(ack, ack.size)!!.isTcpSyn)
    }

    @Test
    fun parsesUdp() {
        val p = ipv4(17, udp(5353, 53))
        val flow = PacketInspector.parse(p, p.size)!!
        assertEquals(17, flow.protocol)
        assertEquals(53, flow.destinationPort)
        assertFalse(flow.isTcpSyn)
    }

    @Test
    fun parsesIpv6Tcp() {
        val h = ByteArray(40)
        h[0] = 0x60
        h[6] = 6
        h[8] = 0xfd.toByte(); h[39] = 1
        val p = h + tcp(50000, 80, 0x02)
        val flow = PacketInspector.parse(p, p.size)!!
        assertEquals(80, flow.destinationPort)
        assertTrue(flow.isTcpSyn)
    }

    @Test
    fun rejectsTruncatedAndUnknownPackets() {
        assertNull(PacketInspector.parse(ByteArray(0), 0))
        val p = ipv4(6, tcp(1, 2, 0x02))
        assertNull(PacketInspector.parse(p, 30))           // TCP header cut off
        val icmp = ipv4(1, ByteArray(8))
        assertNull(PacketInspector.parse(icmp, icmp.size)) // not TCP/UDP
        val bogus = byteArrayOf(0x20, 0, 0, 0)
        assertNull(PacketInspector.parse(bogus, bogus.size))
        assertNull(PacketInspector.parse(p, p.size + 10))  // length beyond buffer
    }

    @Test
    fun ignoresNonFirstFragments() {
        val p = ipv4(17, udp(1, 2), fragOffset = 185)
        assertNull(PacketInspector.parse(p, p.size))
    }
}
