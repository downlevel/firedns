package dev.firedns.spike

import org.junit.Assert.assertEquals
import org.junit.Test

class PacketsTest {

    private val dns = byteArrayOf(10, 111, 222.toByte(), 1)
    private val client = byteArrayOf(10, 111, 222.toByte(), 2)

    /** One's complement sum: 0xFFFF over a header with a correct checksum. */
    private fun onesSum(b: ByteArray, off: Int, len: Int, initial: Long = 0): Int {
        var s = initial
        var i = off
        while (i + 1 < off + len) {
            s += Packets.u16(b, i)
            i += 2
        }
        if (i < off + len) s += (b[i].toInt() and 0xFF) shl 8
        while ((s shr 16) != 0L) s = (s and 0xFFFF) + (s shr 16)
        return s.toInt()
    }

    @Test
    fun `valid ip and udp checksums, also with odd payload`() {
        for (size in listOf(29, 30, 512)) {
            val payload = ByteArray(size) { (it * 7).toByte() }
            val p = Packets.buildUdpResponse(dns, client, 41234, payload)

            assertEquals(28 + size, p.size)
            assertEquals(0xFFFF, onesSum(p, 0, 20))
            val udpLen = Packets.u16(p, 24)
            val pseudo = onesSum(p, 12, 8).toLong() + 17 + udpLen
            assertEquals(0xFFFF, onesSum(p, 20, udpLen, pseudo))
            assertEquals(53, Packets.u16(p, 20))
            assertEquals(41234, Packets.u16(p, 22))
        }
    }

    @Test
    fun `describes the dns question`() {
        // A query for example.com, ID 0x1234.
        val q = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) +
            byteArrayOf(7) + "example".toByteArray() + byteArrayOf(3) + "com".toByteArray() +
            byteArrayOf(0, 0, 1, 0, 1)
        assertEquals("example.com A", DnsMessages.describeQuestion(q))
        assertEquals("?", DnsMessages.describeQuestion(q.copyOf(15)))
    }
}
