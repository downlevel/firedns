package dev.downlevel.firedns.dns.resolver

import dev.downlevel.firedns.data.DnsProfile
import dev.downlevel.firedns.data.DnsProtocol
import java.net.DatagramSocket
import java.net.InetAddress
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** Creates the resolver for a profile. IPs are literals: InetAddress performs no lookup. */
class ResolverFactory(
    private val httpClient: OkHttpClient,
    private val protect: (DatagramSocket) -> Boolean
) {
    fun create(profile: DnsProfile): DnsResolver = when (profile.protocol) {
        DnsProtocol.DOH -> {
            val url = requireNotNull(profile.dohUrl) { "DoH profile without URL: ${profile.id}" }.toHttpUrl()
            val client = if (profile.bootstrapIps.isEmpty()) {
                httpClient
            } else {
                httpClient.newBuilder().dns(BootstrapDns(url.host, profile.bootstrapIps)).build()
            }
            DohResolver(url, client)
        }
        DnsProtocol.UDP -> UdpResolver(InetAddress.getByName(profile.servers.first()), protect = protect)
    }
}

/** Resolves the DoH host to known IPs without any DNS lookup; other hosts use the system DNS. */
class BootstrapDns(private val host: String, private val ips: List<String>) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        if (hostname.equals(host, ignoreCase = true)) ips.map(InetAddress::getByName) else Dns.SYSTEM.lookup(hostname)
}
