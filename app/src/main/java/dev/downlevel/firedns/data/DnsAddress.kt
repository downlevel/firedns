package dev.downlevel.firedns.data

import java.net.URI
import java.net.URISyntaxException

/** Address typed by the user, with the protocol inferred from its format. */
sealed interface DnsAddress {
    data class Udp(val ip: String) : DnsAddress

    data class Doh(val url: String) : DnsAddress

    companion object {
        private val IPV4 = Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")

        /**
         * IP (v4 or v6) → [Udp]; `https://host/...` → [Doh]; otherwise `null`.
         * Purely syntactic validation: never performs DNS lookups.
         */
        fun parse(input: String): DnsAddress? {
            val s = input.trim()
            return when {
                s.isEmpty() -> null
                IPV4.matches(s) -> Udp(s)
                isIpv6(s) -> Udp(s)
                s.startsWith("https://", ignoreCase = true) -> parseDoh(s)
                else -> null
            }
        }

        private fun parseDoh(s: String): Doh? {
            val uri = try {
                URI(s)
            } catch (_: URISyntaxException) {
                return null
            }
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
            return Doh(s)
        }

        private fun isIpv6(s: String): Boolean {
            if (s.count { it == ':' } < 2) return false
            val parts = s.split("::")
            if (parts.size > 2) return false
            val left = countGroups(parts[0], allowIpv4Tail = parts.size == 1) ?: return false
            if (parts.size == 1) return left == 8
            val right = countGroups(parts[1], allowIpv4Tail = true) ?: return false
            return left + right <= 7
        }

        /** Number of 16-bit groups in [part] (an IPv4 tail counts as 2), `null` if invalid. */
        private fun countGroups(part: String, allowIpv4Tail: Boolean): Int? {
            if (part.isEmpty()) return 0
            val groups = part.split(':')
            var count = 0
            groups.forEachIndexed { i, group ->
                count += when {
                    allowIpv4Tail && i == groups.lastIndex && IPV4.matches(group) -> 2
                    group.length in 1..4 && group.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' } -> 1
                    else -> return null
                }
            }
            return count
        }
    }
}
