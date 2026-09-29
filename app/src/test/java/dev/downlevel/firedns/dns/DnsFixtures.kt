package dev.downlevel.firedns.dns

/** Hand-built test DNS messages. */
object DnsFixtures {

    fun query(id: Int, name: String, type: Int = 1): ByteArray {
        val header = byteArrayOf(0, 0, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0).also { it.put16(0, id) }
        val qname = name.split('.').flatMap { listOf(it.length.toByte()) + it.toByteArray().toList() } + 0.toByte()
        return header + qname.toByteArray() + byteArrayOf(0, type.toByte(), 0, 1)
    }

    /** Response with one A record (compressed name) and, if [withOpt], an EDNS OPT record. */
    fun response(query: ByteArray, ttl: Long, rcode: Int = 0, withOpt: Boolean = false): ByteArray {
        val out = query.copyOf()
        out[2] = (0x80 or (query.u8(2) and 0x79)).toByte()
        out[3] = (0x80 or rcode).toByte()
        out.put16(6, 1)
        out.put16(10, if (withOpt) 1 else 0)
        val answer = ByteArray(16).also {
            it.put16(0, 0xC00C) // pointer to the question name
            it.put16(2, 1) // A
            it.put16(4, 1) // IN
            it.put32(6, ttl)
            it.put16(10, 4)
            byteArrayOf(93, 184.toByte(), 216.toByte(), 34).copyInto(it, 12)
        }
        val opt = if (withOpt) byteArrayOf(0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0) else ByteArray(0)
        return out + answer + opt
    }
}
