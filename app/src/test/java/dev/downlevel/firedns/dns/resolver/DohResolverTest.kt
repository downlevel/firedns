package dev.downlevel.firedns.dns.resolver

import dev.downlevel.firedns.dns.DnsFixtures
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DohResolverTest {

    private val server = MockWebServer().apply { start() }
    private val resolver = DohResolver(server.url("/dns-query"), OkHttpClient())

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `post rfc 8484`() = runTest {
        val query = DnsFixtures.query(0, "example.com")
        val answer = DnsFixtures.response(query, ttl = 60)
        server.enqueue(
            MockResponse().setHeader("Content-Type", DohResolver.MEDIA_TYPE).setBody(Buffer().write(answer))
        )

        assertArrayEquals(answer, resolver.resolve(query))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(DohResolver.MEDIA_TYPE, request.getHeader("Content-Type"))
        assertEquals(DohResolver.MEDIA_TYPE, request.getHeader("Accept"))
        assertArrayEquals(query, request.body.readByteArray())
    }

    @Test
    fun `http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))
        val result = runCatching { resolver.resolve(DnsFixtures.query(1, "example.com")) }
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `response too short`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(5))))
        val result = runCatching { resolver.resolve(DnsFixtures.query(1, "example.com")) }
        assertTrue(result.exceptionOrNull() is IOException)
    }
}
