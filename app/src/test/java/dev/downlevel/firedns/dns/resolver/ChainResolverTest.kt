package dev.downlevel.firedns.dns.resolver

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainResolverTest {

    @Test
    fun `first valid answer in order`() = runTest {
        val tried = mutableListOf<Int>()
        val chain = ChainResolver(
            listOf(
                DnsResolver {
                    tried += 1
                    throw IOException("router down")
                },
                DnsResolver {
                    tried += 2
                    byteArrayOf(2)
                },
                DnsResolver {
                    tried += 3
                    byteArrayOf(3)
                }
            )
        )
        assertArrayEquals(byteArrayOf(2), chain.resolve(ByteArray(12)))
        assertEquals(listOf(1, 2), tried)
    }

    @Test
    fun `all failing`() = runTest {
        val chain =
            ChainResolver(listOf(DnsResolver { throw IOException("a") }, DnsResolver { throw IOException("b") }))
        val error = runCatching { chain.resolve(ByteArray(12)) }.exceptionOrNull()
        assertEquals("b", error?.message)
        assertTrue(runCatching { ChainResolver(emptyList()).resolve(ByteArray(12)) }.exceptionOrNull() is IOException)
    }
}
