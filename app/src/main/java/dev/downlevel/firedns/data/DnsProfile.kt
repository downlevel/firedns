package dev.downlevel.firedns.data

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class DnsProtocol { DOH, UDP }

/**
 * A selectable DNS. Built-in profiles live in [Presets]; custom ones are
 * stored in [Settings.customProfiles].
 */
@Serializable
data class DnsProfile(
    val id: String,
    val name: String,
    val protocol: DnsProtocol,
    /** RFC 8484 URL, only for [DnsProtocol.DOH]. */
    val dohUrl: String? = null,
    /** Server IP addresses, only for [DnsProtocol.UDP]. */
    val servers: List<String> = emptyList(),
    /** IPs used to resolve the DoH host without any DNS lookup (built-in profiles only). */
    val bootstrapIps: List<String> = emptyList()
) {
    val isPreset: Boolean get() = id.startsWith(Presets.ID_PREFIX)

    /** Secondary text for the cards: DoH URL or IP. */
    val address: String get() = dohUrl ?: servers.joinToString()

    companion object {
        fun custom(name: String, address: DnsAddress, id: String = UUID.randomUUID().toString()): DnsProfile =
            when (address) {
                is DnsAddress.Udp -> DnsProfile(id, name, DnsProtocol.UDP, servers = listOf(address.ip))
                is DnsAddress.Doh -> DnsProfile(id, name, DnsProtocol.DOH, dohUrl = address.url)
            }
    }
}
