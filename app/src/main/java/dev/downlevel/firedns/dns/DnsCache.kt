package dev.downlevel.firedns.dns

/**
 * LRU cache of responses with TTL. On read it rewrites the query ID and decreases
 * the TTLs by the elapsed time. Errors and truncated responses are not stored.
 */
class DnsCache(
    private val maxEntries: Int = 1000,
    private val maxTtlSeconds: Long = 3600,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private class Entry(
        val response: ByteArray,
        val ttlOffsets: IntArray,
        val ttls: LongArray,
        val storedAt: Long,
        val expiresAt: Long
    )

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(query: ByteArray): ByteArray? {
        val key = DnsMessage.questionKey(query) ?: return null
        val entry = entries[key] ?: return null
        val now = nowMillis()
        if (now >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        val elapsedSeconds = (now - entry.storedAt) / 1000
        return DnsMessage.withId(entry.response, DnsMessage.id(query)).also { out ->
            entry.ttlOffsets.forEachIndexed { i, offset ->
                out.put32(offset, (entry.ttls[i] - elapsedSeconds).coerceAtLeast(0))
            }
        }
    }

    @Synchronized
    fun put(query: ByteArray, response: ByteArray) {
        val key = DnsMessage.questionKey(query) ?: return
        val rcode = DnsMessage.rcode(response)
        if (rcode != DnsMessage.RCODE_NOERROR && rcode != DnsMessage.RCODE_NXDOMAIN) return
        if (DnsMessage.isTruncated(response)) return
        val offsets = DnsMessage.ttlOffsets(response) ?: return
        if (offsets.isEmpty()) return
        val ttls = LongArray(offsets.size) { response.u32(offsets[it]) }
        val ttl = minOf(ttls.min(), maxTtlSeconds)
        if (ttl <= 0) return
        val now = nowMillis()
        entries[key] = Entry(response.copyOf(), offsets, ttls, now, now + ttl * 1000)
    }

    @Synchronized
    fun clear() = entries.clear()

    @get:Synchronized
    val size: Int get() = entries.size
}
