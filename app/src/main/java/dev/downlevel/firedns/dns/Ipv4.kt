package dev.downlevel.firedns.dns

/** IPv4 packet read from the TUN. Addresses are big-endian Ints. */
sealed interface Ipv4Packet {
    val src: Int
    val dst: Int

    class Udp(
        override val src: Int,
        override val dst: Int,
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray
    ) : Ipv4Packet

    class Tcp(
        override val src: Int,
        override val dst: Int,
        val srcPort: Int,
        val dstPort: Int,
        val seq: Long,
        val ack: Long,
        val flags: Int,
        val payloadLength: Int
    ) : Ipv4Packet

    class Other(override val src: Int, override val dst: Int, val protocol: Int) : Ipv4Packet
}

/** Minimal parsing and building of IPv4 + UDP/TCP packets (only what the DNS tunnel needs). */
object Ipv4 {
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_ACK = 0x10

    private const val IP_HEADER = 20
    private const val UDP_HEADER = 8
    private const val TCP_HEADER = 20

    /** `null` if not IPv4 or malformed. [length] = valid bytes in [buf]. */
    fun parse(buf: ByteArray, length: Int = buf.size): Ipv4Packet? {
        if (length < IP_HEADER || (buf.u8(0) shr 4) != 4) return null
        val ihl = (buf.u8(0) and 0x0F) * 4
        val total = buf.u16(2)
        if (ihl < IP_HEADER || total < ihl || total > length) return null
        val protocol = buf.u8(9)
        val src = buf.u32(12).toInt()
        val dst = buf.u32(16).toInt()
        return when (protocol) {
            PROTO_UDP -> {
                if (total < ihl + UDP_HEADER) return null
                val udpLength = buf.u16(ihl + 4)
                if (udpLength < UDP_HEADER || ihl + udpLength > total) return null
                Ipv4Packet.Udp(
                    src = src,
                    dst = dst,
                    srcPort = buf.u16(ihl),
                    dstPort = buf.u16(ihl + 2),
                    payload = buf.copyOfRange(ihl + UDP_HEADER, ihl + udpLength)
                )
            }
            PROTO_TCP -> {
                if (total < ihl + TCP_HEADER) return null
                val dataOffset = (buf.u8(ihl + 12) shr 4) * 4
                if (dataOffset < TCP_HEADER || ihl + dataOffset > total) return null
                Ipv4Packet.Tcp(
                    src = src,
                    dst = dst,
                    srcPort = buf.u16(ihl),
                    dstPort = buf.u16(ihl + 2),
                    seq = buf.u32(ihl + 4),
                    ack = buf.u32(ihl + 8),
                    flags = buf.u8(ihl + 13),
                    payloadLength = total - ihl - dataOffset
                )
            }
            else -> Ipv4Packet.Other(src, dst, protocol)
        }
    }

    fun udp(src: Int, dst: Int, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val udpLength = UDP_HEADER + payload.size
        val p = ByteArray(IP_HEADER + udpLength)
        writeIpHeader(p, PROTO_UDP, src, dst)
        p.put16(20, srcPort)
        p.put16(22, dstPort)
        p.put16(24, udpLength)
        payload.copyInto(p, IP_HEADER + UDP_HEADER)
        val sum = checksum(p, IP_HEADER, udpLength, pseudoHeaderSum(p, PROTO_UDP, udpLength))
        p.put16(26, if (sum == 0) 0xFFFF else sum)
        return p
    }

    /**
     * RST in reply to [to] (RFC 793): immediately closes a TCP connection to the virtual
     * DNS, so the system does not wait for a timeout. `null` if [to] is already a RST.
     */
    fun tcpReset(to: Ipv4Packet.Tcp): ByteArray? {
        if (to.flags and TCP_RST != 0) return null
        val p = ByteArray(IP_HEADER + TCP_HEADER)
        writeIpHeader(p, PROTO_TCP, src = to.dst, dst = to.src)
        p.put16(20, to.dstPort)
        p.put16(22, to.srcPort)
        if (to.flags and TCP_ACK != 0) {
            p.put32(24, to.ack)
            p[33] = TCP_RST.toByte()
        } else {
            val consumed = to.payloadLength +
                (if (to.flags and TCP_SYN != 0) 1 else 0) +
                (if (to.flags and TCP_FIN != 0) 1 else 0)
            p.put32(28, (to.seq + consumed) and 0xFFFFFFFFL)
            p[33] = (TCP_RST or TCP_ACK).toByte()
        }
        p[32] = (5 shl 4).toByte() // data offset: 20 bytes
        p.put16(36, checksum(p, IP_HEADER, TCP_HEADER, pseudoHeaderSum(p, PROTO_TCP, TCP_HEADER)))
        return p
    }

    fun address(dotted: String): Int {
        val parts = dotted.split('.').map { it.toInt() }
        require(parts.size == 4 && parts.all { it in 0..255 }) { "Invalid IPv4: $dotted" }
        return parts.fold(0) { acc, b -> (acc shl 8) or b }
    }

    fun format(address: Int): String = (3 downTo 0).joinToString(".") { ((address shr (it * 8)) and 0xFF).toString() }

    private fun writeIpHeader(p: ByteArray, protocol: Int, src: Int, dst: Int) {
        p[0] = 0x45 // IPv4, IHL 5
        p.put16(2, p.size)
        p.put16(6, 0x4000) // Don't Fragment
        p[8] = 64 // TTL
        p[9] = protocol.toByte()
        p.put32(12, src.toLong() and 0xFFFFFFFFL)
        p.put32(16, dst.toLong() and 0xFFFFFFFFL)
        p.put16(10, checksum(p, 0, IP_HEADER, 0))
    }

    private fun pseudoHeaderSum(p: ByteArray, protocol: Int, length: Int): Long = sum(p, 12, 8, 0) + protocol + length

    private fun sum(b: ByteArray, offset: Int, length: Int, initial: Long): Long {
        var s = initial
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            s += b.u16(i)
            i += 2
        }
        if (i < end) s += b.u8(i) shl 8
        return s
    }

    private fun checksum(b: ByteArray, offset: Int, length: Int, initial: Long): Int {
        var s = sum(b, offset, length, initial)
        while ((s shr 16) != 0L) s = (s and 0xFFFFL) + (s shr 16)
        return (s.inv() and 0xFFFFL).toInt()
    }
}
