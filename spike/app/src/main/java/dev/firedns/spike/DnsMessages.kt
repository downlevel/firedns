package dev.firedns.spike

/** Minimal DNS message reading, for logging only. */
object DnsMessages {

    /** "name.domain A" from the question section, or "?" if unreadable. */
    fun describeQuestion(msg: ByteArray): String {
        if (msg.size < 12) return "?"
        return try {
            var i = 12
            val labels = mutableListOf<String>()
            while (true) {
                val len = msg[i].toInt() and 0xFF
                if (len == 0) {
                    i++
                    break
                }
                if ((len and 0xC0) != 0) return "?" // no pointer expected in the question
                labels += String(msg, i + 1, len, Charsets.US_ASCII)
                i += 1 + len
            }
            "${labels.joinToString(".")} ${typeName(Packets.u16(msg, i))}"
        } catch (e: IndexOutOfBoundsException) {
            "?"
        }
    }

    fun rcode(msg: ByteArray): String {
        if (msg.size < 4) return "?"
        return when (val code = msg[3].toInt() and 0x0F) {
            0 -> "NOERROR"
            2 -> "SERVFAIL"
            3 -> "NXDOMAIN"
            5 -> "REFUSED"
            else -> "RCODE$code"
        }
    }

    private fun typeName(type: Int): String = when (type) {
        1 -> "A"
        5 -> "CNAME"
        12 -> "PTR"
        16 -> "TXT"
        28 -> "AAAA"
        33 -> "SRV"
        65 -> "HTTPS"
        else -> "TYPE$type"
    }
}
