package dev.downlevel.firedns.dns.resolver

import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Tries the resolvers in order and returns the first answer (network DNS for fallback). */
class ChainResolver(private val resolvers: List<DnsResolver>) : DnsResolver {

    override suspend fun resolve(query: ByteArray): ByteArray {
        var lastError: Exception? = null
        for (resolver in resolvers) {
            try {
                return resolver.resolve(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("No network DNS available")
    }
}
