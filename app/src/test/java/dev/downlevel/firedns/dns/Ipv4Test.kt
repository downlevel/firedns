package dev.downlevel.firedns.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4Test {

    private val client = Ipv4.address("10.111.222.2")
    private val dns = Ipv4.address("10.111.222.1")

    /** One's complement sum: 0xFFFF over a range with a correct checksum. */
    private fun onesSum(b: ByteArray, offset: Int, length: Int, initial: Long = 0): Int {
        var s = initial
        var i = offset
        while (i + 1 < offset + length) {
            s += b.u16(i)
            i += 2
        }
        if (i < offset + length) s += b.u8(i) shl 8
        while ((s shr 16) != 0L) s = (s and 0xFFFF) + (s shr 16)
        return s.toInt()
    }

    private fun assertChecksums(p: ByteArray, protocol: Int) {
        assertEquals(0xFFFF, onesSum(p, 0, 20))
        val l4 = p.size - 20
        assertEquals(0xFFFF, onesSum(p, 20, l4, onesSum(p, 12, 8).toLong() + protocol + l4))
    }

    @Test
    fun `addresses`() {
        assertEquals("10.111.222.1", Ipv4.format(dns))
        assertEquals("255.0.1.2", Ipv4.format(Ipv4.address("255.0.1.2")))
    }

    @Test
    fun `udp built and parsed back, even and odd payload`() {
        for (size in listOf(29, 30, 512)) {
            val payload = ByteArray(size) { (it * 7).toByte() }
            val bytes = Ipv4.udp(dns, client, 53, 41234, payload)
            assertChecksums(bytes, Ipv4.PROTO_UDP)
            val parsed = Ipv4.parse(bytes) as Ipv4Packet.Udp
            assertEquals(dns, parsed.src)
            assertEquals(client, parsed.dst)
            assertEquals(53, parsed.srcPort)
            assertEquals(41234, parsed.dstPort)
            assertArrayEquals(payload, parsed.payload)
        }
    }

    @Test
    fun `buffer length is honored`() {
        val bytes = Ipv4.udp(client, dns, 5000, 53, ByteArray(40))
        val buf = bytes.copyOf(2000)
        assertEquals(40, (Ipv4.parse(buf, bytes.size) as Ipv4Packet.Udp).payload.size)
        assertNull(Ipv4.parse(bytes, bytes.size - 1))
    }

    private fun tcp(flags: Int, seq: Long, ack: Long, payload: Int = 0): Ipv4Packet.Tcp =
        Ipv4Packet.Tcp(client, dns, 45000, 853, seq, ack, flags, payload)

    @Test
    fun `rst to a syn`() {
        val rst = Ipv4.tcpReset(tcp(Ipv4.TCP_SYN, seq = 0xFFFFFFFFL, ack = 0))!!
        assertChecksums(rst, Ipv4.PROTO_TCP)
        val parsed = Ipv4.parse(rst) as Ipv4Packet.Tcp
        assertEquals(dns, parsed.src)
        assertEquals(client, parsed.dst)
        assertEquals(853, parsed.srcPort)
        assertEquals(45000, parsed.dstPort)
        assertEquals(Ipv4.TCP_RST or Ipv4.TCP_ACK, parsed.flags)
        assertEquals(0L, parsed.seq)
        assertEquals(0L, parsed.ack) // seq+1 with 32-bit wrap-around
    }

    @Test
    fun `rst to a segment with ack`() {
        val parsed = Ipv4.parse(
            Ipv4.tcpReset(tcp(Ipv4.TCP_ACK, seq = 100, ack = 5000, payload = 10))!!
        ) as Ipv4Packet.Tcp
        assertEquals(Ipv4.TCP_RST, parsed.flags)
        assertEquals(5000L, parsed.seq)
    }

    @Test
    fun `no rst to a rst`() {
        assertNull(Ipv4.tcpReset(tcp(Ipv4.TCP_RST, 1, 1)))
    }

    @Test
    fun `not ipv4 or malformed`() {
        assertNull(Ipv4.parse(ByteArray(40).also { it[0] = 0x60 }))
        assertNull(Ipv4.parse(ByteArray(10)))
        val icmp = Ipv4.udp(client, dns, 1, 2, ByteArray(4)).also { it[9] = 1 }
        assertTrue(Ipv4.parse(icmp) is Ipv4Packet.Other)
    }
}
