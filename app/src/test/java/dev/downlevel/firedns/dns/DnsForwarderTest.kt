package dev.downlevel.firedns.dns

import dev.downlevel.firedns.dns.resolver.DnsResolver
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DnsForwarderTest {

    private val client = Ipv4.address("10.111.222.2")
    private val virtualDns = Ipv4.address("10.111.222.1")
    private val publicDns = Ipv4.address("8.8.8.8")
    private val output = mutableListOf<ByteArray>()
    private var calls = 0

    private var now = 0L
    private val policy = FallbackPolicy(threshold = 3, retryIntervalMs = 60_000, nowMillis = { now })

    private fun TestScope.forwarder(resolver: DnsResolver, fallback: DnsResolver? = null) = DnsForwarder(
        scope = backgroundScope,
        interceptedAddresses = setOf(virtualDns, publicDns),
        cache = DnsCache(nowMillis = { now }),
        primary = { resolver },
        output = { output += it },
        fallback = { fallback },
        policy = policy
    )

    private val echo = DnsResolver { query ->
        calls++
        // Upstream answering with a different ID: the forwarder must restore the query's.
        DnsMessage.withId(DnsFixtures.response(query, ttl = 60), 0)
    }

    private fun DnsForwarder.send(dst: Int, query: ByteArray, port: Int = 53) {
        val packet = Ipv4.udp(client, dst, 40000, port, query)
        handle(packet, packet.size)
    }

    @Test
    fun `answer comes from the queried DNS with the query id`() = runTest(UnconfinedTestDispatcher()) {
        val f = forwarder(echo)
        f.send(publicDns, DnsFixtures.query(0x4242, "example.com"))
        val reply = Ipv4.parse(output.single()) as Ipv4Packet.Udp
        assertEquals(publicDns, reply.src)
        assertEquals(client, reply.dst)
        assertEquals(53, reply.srcPort)
        assertEquals(40000, reply.dstPort)
        assertEquals(0x4242, DnsMessage.id(reply.payload))
        assertEquals(DnsMessage.RCODE_NOERROR, DnsMessage.rcode(reply.payload))
    }

    @Test
    fun `identical second query is served from cache`() = runTest(UnconfinedTestDispatcher()) {
        val f = forwarder(echo)
        f.send(virtualDns, DnsFixtures.query(1, "example.com"))
        f.send(virtualDns, DnsFixtures.query(2, "example.com"))
        assertEquals(1, calls)
        assertEquals(1L, f.stats.cacheHits.get())
        assertEquals(2, DnsMessage.id((Ipv4.parse(output.last()) as Ipv4Packet.Udp).payload))
    }

    @Test
    fun `failing upstream produces servfail`() = runTest(UnconfinedTestDispatcher()) {
        val f = forwarder(resolver = { throw IOException("network down") })
        f.send(virtualDns, DnsFixtures.query(7, "example.com"))
        val reply = Ipv4.parse(output.single()) as Ipv4Packet.Udp
        assertEquals(DnsMessage.RCODE_SERVFAIL, DnsMessage.rcode(reply.payload))
        assertEquals(1L, f.stats.failures.get())
    }

    /** TCP segment client→[dst] (the parser does not need checksums). */
    private fun tcpSegment(dst: Int, flags: Int, seq: Long): ByteArray = ByteArray(40).also {
        it[0] = 0x45
        it.put16(2, 40)
        it[9] = Ipv4.PROTO_TCP.toByte()
        it.put32(12, client.toLong() and 0xFFFFFFFFL)
        it.put32(16, dst.toLong() and 0xFFFFFFFFL)
        it.put16(20, 45000)
        it.put16(22, 853)
        it.put32(24, seq)
        it[32] = 0x50
        it[33] = flags.toByte()
    }

    @Test
    fun `tcp to the dns gets a rst`() = runTest(UnconfinedTestDispatcher()) {
        val f = forwarder(echo)
        val syn = tcpSegment(virtualDns, Ipv4.TCP_SYN, seq = 1000)
        f.handle(syn, syn.size)
        val rst = Ipv4.parse(output.single()) as Ipv4Packet.Tcp
        assertEquals(virtualDns, rst.src)
        assertEquals(client, rst.dst)
        assertEquals(Ipv4.TCP_RST or Ipv4.TCP_ACK, rst.flags)
        assertEquals(1001L, rst.ack)
        assertEquals(0, calls)
    }

    @Test
    fun `tcp to other addresses is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val f = forwarder(echo)
        val syn = tcpSegment(Ipv4.address("1.2.3.4"), Ipv4.TCP_SYN, seq = 1)
        f.handle(syn, syn.size)
        assertTrue(output.isEmpty())
    }

    private fun rcodeOf(packet: ByteArray) = DnsMessage.rcode((Ipv4.parse(packet) as Ipv4Packet.Udp).payload)

    @Test
    fun `fallback after three failures, probe and return to the chosen dns`() = runTest(UnconfinedTestDispatcher()) {
        var primaryUp = false
        var fallbackCalls = 0
        val primary = DnsResolver { query ->
            calls++
            if (!primaryUp) throw IOException("provider down")
            DnsFixtures.response(query, ttl = 60)
        }
        val network = DnsResolver { query ->
            fallbackCalls++
            DnsFixtures.response(query, ttl = 60)
        }
        val f = forwarder(primary, network)

        // Below the threshold: SERVFAIL, no query to the network DNS.
        f.send(virtualDns, DnsFixtures.query(1, "a.com"))
        f.send(virtualDns, DnsFixtures.query(2, "b.com"))
        assertEquals(listOf(2, 2), output.map(::rcodeOf))
        assertEquals(0, fallbackCalls)

        // Third failure: fallback starts and the same query is served by the network DNS.
        f.send(virtualDns, DnsFixtures.query(3, "c.com"))
        assertTrue(policy.isActive)
        assertEquals(0, rcodeOf(output.last()))
        assertEquals(1, fallbackCalls)

        // In fallback the chosen DNS is not queried until the probe.
        f.send(virtualDns, DnsFixtures.query(4, "d.com"))
        assertEquals(3, calls)
        assertEquals(2, fallbackCalls)

        // After the interval the probe succeeds: back to the chosen DNS.
        now = 60_000
        primaryUp = true
        f.send(virtualDns, DnsFixtures.query(5, "e.com"))
        assertEquals(4, calls)
        assertTrue(!policy.isActive)
        assertEquals(2L, f.stats.fallbackAnswers.get())

        // Network DNS answers were not cached: d.com asks the chosen DNS again.
        f.send(virtualDns, DnsFixtures.query(6, "d.com"))
        assertEquals(5, calls)
    }

    @Test
    fun `without fallback only servfail`() = runTest(UnconfinedTestDispatcher()) {
        val f = forwarder({ throw IOException("down") }, fallback = null)
        repeat(5) { f.send(virtualDns, DnsFixtures.query(it, "x$it.com")) }
        assertEquals(List(5) { 2 }, output.map(::rcodeOf))
        assertTrue(!policy.isActive)
    }

    @Test
    fun `non dns traffic is dropped`() = runTest(UnconfinedTestDispatcher()) {
        val f = forwarder(echo)
        f.send(Ipv4.address("1.2.3.4"), DnsFixtures.query(1, "example.com"))
        f.send(virtualDns, DnsFixtures.query(1, "example.com"), port = 5353)
        assertTrue(output.isEmpty())
        assertEquals(2L, f.stats.dropped.get())
    }
}
