package dev.firedns.spike

/** Minimal reading/building of IPv4 + UDP packets. */
object Packets {
    private const val IPV4_HEADER = 20
    private const val UDP_HEADER = 8
    private const val PROTO_UDP = 17

    fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v shr 8).toByte()
        b[off + 1] = v.toByte()
    }

    /** IPv4/UDP packet from [src]:53 to [dst]:[dstPort] with [payload]. */
    fun buildUdpResponse(src: ByteArray, dst: ByteArray, dstPort: Int, payload: ByteArray): ByteArray {
        val udpLen = UDP_HEADER + payload.size
        val total = IPV4_HEADER + udpLen
        val p = ByteArray(total)

        p[0] = 0x45 // IPv4, IHL = 5
        put16(p, 2, total)
        put16(p, 6, 0x4000) // Don't Fragment
        p[8] = 64 // TTL
        p[9] = PROTO_UDP.toByte()
        src.copyInto(p, 12)
        dst.copyInto(p, 16)
        put16(p, 10, checksum(p, 0, IPV4_HEADER, 0))

        put16(p, 20, 53)
        put16(p, 22, dstPort)
        put16(p, 24, udpLen)
        payload.copyInto(p, IPV4_HEADER + UDP_HEADER)
        // Pseudo-header: source + destination IP, protocol, UDP length.
        val pseudo = sum(p, 12, 8, 0) + PROTO_UDP + udpLen
        val udpSum = checksum(p, IPV4_HEADER, udpLen, pseudo)
        put16(p, 26, if (udpSum == 0) 0xFFFF else udpSum)
        return p
    }

    private fun sum(b: ByteArray, off: Int, len: Int, initial: Long): Long {
        var s = initial
        var i = off
        val end = off + len
        while (i + 1 < end) {
            s += u16(b, i)
            i += 2
        }
        if (i < end) s += (b[i].toInt() and 0xFF) shl 8
        return s
    }

    private fun checksum(b: ByteArray, off: Int, len: Int, initial: Long): Int {
        var s = sum(b, off, len, initial)
        while ((s shr 16) != 0L) s = (s and 0xFFFFL) + (s shr 16)
        return (s.inv() and 0xFFFFL).toInt()
    }
}
