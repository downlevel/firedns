package dev.downlevel.firedns.dns

import dev.downlevel.firedns.dns.resolver.DnsResolver
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Dispatches the packets read from the TUN:
 * - UDP/53 to an intercepted DNS → answer from the cache, from the chosen DNS ([primary]) or,
 *   when [policy] is in fallback, from the network DNS ([fallback]); SERVFAIL if nobody answers;
 * - TCP to an intercepted DNS → immediate RST;
 * - everything else → dropped.
 * [fallback] returns `null` when fallback is disabled or the network has no DNS.
 */
class DnsForwarder(
    private val scope: CoroutineScope,
    interceptedAddresses: Set<Int>,
    private val cache: DnsCache,
    private val primary: () -> DnsResolver,
    private val output: (ByteArray) -> Unit,
    private val fallback: () -> DnsResolver? = { null },
    private val policy: FallbackPolicy = FallbackPolicy()
) {
    class Stats {
        val queries = AtomicLong()
        val cacheHits = AtomicLong()
        val fallbackAnswers = AtomicLong()
        val failures = AtomicLong()
        val resets = AtomicLong()
        val dropped = AtomicLong()
    }

    val stats = Stats()

    /** Updated when the network DNS servers change (the TUN is re-created with the new routes). */
    @Volatile
    var interceptedAddresses: Set<Int> = interceptedAddresses

    fun handle(buf: ByteArray, length: Int) {
        when (val packet = Ipv4.parse(buf, length)) {
            is Ipv4Packet.Udp ->
                if (packet.dst in interceptedAddresses && packet.dstPort == DNS_PORT) {
                    stats.queries.incrementAndGet()
                    scope.launch { answer(packet) }
                } else {
                    stats.dropped.incrementAndGet()
                }
            is Ipv4Packet.Tcp -> {
                val reset = if (packet.dst in interceptedAddresses) Ipv4.tcpReset(packet) else null
                if (reset != null) {
                    stats.resets.incrementAndGet()
                    output(reset)
                } else {
                    stats.dropped.incrementAndGet()
                }
            }
            else -> stats.dropped.incrementAndGet()
        }
    }

    private suspend fun answer(packet: Ipv4Packet.Udp) {
        val query = packet.payload
        val response = cache.get(query)?.also { stats.cacheHits.incrementAndGet() }
            ?: resolveUpstream(query)
            ?: return
        // The answer comes from the queried address, so the client accepts it.
        output(
            Ipv4.udp(
                src = packet.dst,
                dst = packet.src,
                srcPort = DNS_PORT,
                dstPort = packet.srcPort,
                payload = response
            )
        )
    }

    private suspend fun resolveUpstream(query: ByteArray): ByteArray? {
        val fallbackResolver = fallback()
        if (fallbackResolver == null || policy.shouldTryPrimary()) {
            try {
                val response = DnsMessage.withId(primary().resolve(query), DnsMessage.id(query))
                if (fallbackResolver != null) policy.onPrimarySuccess()
                cache.put(query, response)
                return response
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (fallbackResolver == null) return fail(query)
                policy.onPrimaryFailure()
                // Below the threshold there is no fallback (PRD): the client gets SERVFAIL and retries.
                if (!policy.isActive) return fail(query)
            }
        }
        // fallbackResolver is not null here: with null the block above always returns.
        return try {
            // Network DNS answers are not cached: they must not outlive the return to the chosen DNS.
            DnsMessage.withId(fallbackResolver.resolve(query), DnsMessage.id(query))
                .also { stats.fallbackAnswers.incrementAndGet() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fail(query)
        }
    }

    private fun fail(query: ByteArray): ByteArray? {
        stats.failures.incrementAndGet()
        return DnsMessage.servfail(query)
    }

    private companion object {
        const val DNS_PORT = 53
    }
}
