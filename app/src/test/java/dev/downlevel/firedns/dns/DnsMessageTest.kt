package dev.downlevel.firedns.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsMessageTest {

    @Test
    fun `cache key ignores case and id`() {
        val a = DnsMessage.questionKey(DnsFixtures.query(1, "Example.COM"))
        assertEquals(a, DnsMessage.questionKey(DnsFixtures.query(2, "example.com")))
        assertNotEquals(a, DnsMessage.questionKey(DnsFixtures.query(1, "example.com", type = 28)))
        assertNull(DnsMessage.questionKey(ByteArray(5)))
    }

    @Test
    fun `servfail keeps id and question`() {
        val query = DnsFixtures.query(0x1234, "example.com")
        val fail = DnsMessage.servfail(query)!!
        assertEquals(0x1234, DnsMessage.id(fail))
        assertEquals(DnsMessage.RCODE_SERVFAIL, DnsMessage.rcode(fail))
        assertEquals(0x80, fail.u8(2) and 0x80) // QR
        assertEquals(0x01, fail.u8(2) and 0x01) // RD preserved
        assertArrayEquals(query.copyOfRange(12, query.size), fail.copyOfRange(12, fail.size))
        assertNull(DnsMessage.servfail(ByteArray(8)))
    }

    @Test
    fun `ttl offsets, opt excluded`() {
        val query = DnsFixtures.query(1, "example.com")
        val response = DnsFixtures.response(query, ttl = 300, withOpt = true)
        val offsets = DnsMessage.ttlOffsets(response)!!
        assertEquals(1, offsets.size)
        assertEquals(300L, response.u32(offsets[0]))
        assertNull(DnsMessage.ttlOffsets(response.copyOf(response.size - 6)))
    }
}
