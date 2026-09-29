package dev.downlevel.firedns.data

/** Built-in profiles, all DoH. Not editable by the user. */
object Presets {
    const val ID_PREFIX = "preset:"

    val cloudflare = DnsProfile(
        id = "${ID_PREFIX}cloudflare",
        name = "Cloudflare",
        protocol = DnsProtocol.DOH,
        dohUrl = "https://cloudflare-dns.com/dns-query",
        bootstrapIps = listOf("1.1.1.1", "1.0.0.1")
    )
    val google = DnsProfile(
        id = "${ID_PREFIX}google",
        name = "Google",
        protocol = DnsProtocol.DOH,
        dohUrl = "https://dns.google/dns-query",
        bootstrapIps = listOf("8.8.8.8", "8.8.4.4")
    )
    val adguard = DnsProfile(
        id = "${ID_PREFIX}adguard",
        name = "AdGuard DNS",
        protocol = DnsProtocol.DOH,
        dohUrl = "https://dns.adguard-dns.com/dns-query",
        bootstrapIps = listOf("94.140.14.14", "94.140.15.15")
    )
    val quad9 = DnsProfile(
        id = "${ID_PREFIX}quad9",
        name = "Quad9",
        protocol = DnsProtocol.DOH,
        dohUrl = "https://dns.quad9.net/dns-query",
        bootstrapIps = listOf("9.9.9.9", "149.112.112.112")
    )

    /** Display order on the Home screen. */
    val all: List<DnsProfile> = listOf(cloudflare, google, adguard, quad9)

    val default: DnsProfile = cloudflare

    fun byId(id: String): DnsProfile? = all.firstOrNull { it.id == id }
}
