package dev.downlevel.firedns.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsCacheTest {

    private var now = 0L
    private val cache = DnsCache(maxEntries = 2, nowMillis = { now })

    private fun ttlOf(response: ByteArray) = response.u32(DnsMessage.ttlOffsets(response)!![0])

    @Test
    fun `hit uses the new query id and decreased ttl`() {
        val q1 = DnsFixtures.query(1, "example.com")
        cache.put(q1, DnsFixtures.response(q1, ttl = 300))
        now = 100_500
        val hit = cache.get(DnsFixtures.query(99, "EXAMPLE.com"))!!
        assertEquals(99, DnsMessage.id(hit))
        assertEquals(200L, ttlOf(hit))
    }

    @Test
    fun `expiry`() {
        val q = DnsFixtures.query(1, "example.com")
        cache.put(q, DnsFixtures.response(q, ttl = 10))
        now = 9_999
        assertNotNull(cache.get(q))
        now = 10_000
        assertNull(cache.get(q))
    }

    @Test
    fun `errors, truncated and zero ttl are not stored`() {
        val q = DnsFixtures.query(1, "example.com")
        cache.put(q, DnsFixtures.response(q, ttl = 60, rcode = DnsMessage.RCODE_SERVFAIL))
        cache.put(q, DnsFixtures.response(q, ttl = 60).also { it[2] = (it.u8(2) or 0x02).toByte() })
        cache.put(q, DnsFixtures.response(q, ttl = 0))
        assertEquals(0, cache.size)
        cache.put(q, DnsFixtures.response(q, ttl = 60, rcode = DnsMessage.RCODE_NXDOMAIN))
        assertEquals(1, cache.size)
    }

    @Test
    fun `lru`() {
        val a = DnsFixtures.query(1, "a.com")
        val b = DnsFixtures.query(2, "b.com")
        val c = DnsFixtures.query(3, "c.com")
        listOf(a, b).forEach { cache.put(it, DnsFixtures.response(it, ttl = 60)) }
        cache.get(a) // a becomes the most recent
        cache.put(c, DnsFixtures.response(c, ttl = 60))
        assertNotNull(cache.get(a))
        assertNull(cache.get(b))
        assertNotNull(cache.get(c))
    }
}
