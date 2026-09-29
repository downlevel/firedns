package dev.downlevel.firedns.dns.resolver

/** Forwards a DNS query (binary RFC 1035 message) and returns the answer. Throws on failure. */
fun interface DnsResolver {
    suspend fun resolve(query: ByteArray): ByteArray
}
