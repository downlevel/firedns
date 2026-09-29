package dev.downlevel.firedns.dns

/** Minimal DNS message reading (RFC 1035): just enough for caching and error responses. */
object DnsMessage {
    const val HEADER_SIZE = 12
    const val RCODE_NOERROR = 0
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3
    private const val TYPE_OPT = 41

    fun id(msg: ByteArray): Int = msg.u16(0)

    fun withId(msg: ByteArray, id: Int): ByteArray = msg.copyOf().also { it.put16(0, id) }

    fun rcode(msg: ByteArray): Int = msg.u8(3) and 0x0F

    fun isTruncated(msg: ByteArray): Boolean = msg.u8(2) and 0x02 != 0

    /** Cache key "name|type|class" (lowercase name), `null` unless the query has exactly one question. */
    fun questionKey(msg: ByteArray): String? = try {
        if (msg.size < HEADER_SIZE || msg.u16(4) != 1) {
            null
        } else {
            val end = skipName(msg, HEADER_SIZE)
            val name = msg.copyOfRange(HEADER_SIZE, end).map { it.toInt().toChar().lowercaseChar() }.joinToString("")
            "$name|${msg.u16(end)}|${msg.u16(end + 2)}"
        }
    } catch (_: IndexOutOfBoundsException) {
        null
    }

    /** SERVFAIL response to [query] (same ID and question), `null` if the query is unreadable. */
    fun servfail(query: ByteArray): ByteArray? = try {
        if (query.size < HEADER_SIZE || query.u16(4) != 1) {
            null
        } else {
            val end = skipName(query, HEADER_SIZE) + 4
            query.copyOf(end).also {
                it[2] = (0x80 or (query.u8(2) and 0x79)).toByte() // QR=1, opcode and RD preserved
                it[3] = (0x80 or RCODE_SERVFAIL).toByte() // RA=1
                for (offset in 6 until HEADER_SIZE) it[offset] = 0 // AN/NS/AR = 0
            }
        }
    } catch (_: IndexOutOfBoundsException) {
        null
    }

    /** Offsets of the TTL fields of all records (OPT excluded), `null` if the message is malformed. */
    fun ttlOffsets(msg: ByteArray): IntArray? = try {
        var offset = HEADER_SIZE
        repeat(msg.u16(4)) { offset = skipName(msg, offset) + 4 }
        val records = msg.u16(6) + msg.u16(8) + msg.u16(10)
        val offsets = ArrayList<Int>(records)
        repeat(records) {
            offset = skipName(msg, offset)
            val type = msg.u16(offset)
            if (type != TYPE_OPT) offsets += offset + 4
            offset += 10 + msg.u16(offset + 8)
        }
        if (offset > msg.size) null else offsets.toIntArray()
    } catch (_: IndexOutOfBoundsException) {
        null
    }

    /** Offset right after a name (labels or compression pointer). */
    private fun skipName(msg: ByteArray, start: Int): Int {
        var offset = start
        while (true) {
            val len = msg.u8(offset)
            when {
                len == 0 -> return offset + 1
                len and 0xC0 == 0xC0 -> return offset + 2
                len and 0xC0 != 0 -> throw IndexOutOfBoundsException("invalid label")
                else -> offset += 1 + len
            }
        }
    }
}
