package dev.downlevel.firedns.dns.resolver

import dev.downlevel.firedns.dns.DnsFixtures
import dev.downlevel.firedns.dns.DnsMessage
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpResolverTest {

    private val loopback = InetAddress.getLoopbackAddress()
    private val server = DatagramSocket(0, loopback)

    @After
    fun tearDown() = server.close()

    /** Server answering first with a wrong ID and then with the right one. */
    private fun serveOnce() = thread {
        val buf = ByteArray(512)
        val packet = DatagramPacket(buf, buf.size)
        server.receive(packet)
        val query = buf.copyOf(packet.length)
        val wrong = DnsMessage.withId(DnsFixtures.response(query, ttl = 60), DnsMessage.id(query) + 1)
        val right = DnsFixtures.response(query, ttl = 60)
        for (reply in listOf(wrong, right)) server.send(DatagramPacket(reply, reply.size, packet.socketAddress))
    }

    @Test
    fun `drops other ids and returns the right answer`() = runTest {
        serveOnce()
        var protected = false
        val resolver = UdpResolver(loopback, server.localPort, protect = {
            protected = true
            true
        })
        val response = resolver.resolve(DnsFixtures.query(0x0102, "example.com"))
        assertEquals(0x0102, DnsMessage.id(response))
        assertTrue(protected)
    }

    @Test
    fun `timeout without an answer`() = runTest {
        val resolver = UdpResolver(loopback, server.localPort, timeoutMs = 200)
        val result = runCatching { resolver.resolve(DnsFixtures.query(1, "example.com")) }
        assertTrue(result.exceptionOrNull() is SocketTimeoutException)
    }
}
