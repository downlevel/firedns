package dev.downlevel.firedns.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsAddressTest {

    @Test
    fun `ipv4 becomes udp`() {
        assertEquals(DnsAddress.Udp("192.168.1.2"), DnsAddress.parse(" 192.168.1.2 "))
        assertEquals(DnsAddress.Udp("9.9.9.9"), DnsAddress.parse("9.9.9.9"))
    }

    @Test
    fun `ipv6 becomes udp`() {
        listOf(
            "2606:4700:4700::1111",
            "::1",
            "::",
            "fd00::",
            "2001:db8:0:0:0:0:0:1",
            "::ffff:192.168.1.2"
        ).forEach { assertEquals(it, DnsAddress.Udp(it), DnsAddress.parse(it)) }
    }

    @Test
    fun `https url becomes doh`() {
        val url = "https://dns.nextdns.io/abc123"
        assertEquals(DnsAddress.Doh(url), DnsAddress.parse(url))
        assertEquals(DnsAddress.Doh("HTTPS://dns.google/dns-query"), DnsAddress.parse("HTTPS://dns.google/dns-query"))
    }

    @Test
    fun `invalid input`() {
        listOf(
            "",
            "   ",
            "256.1.1.1",
            "1.2.3",
            "1.2.3.4:53",
            "01.2.3.4",
            "dns.google",
            "http://dns.google/dns-query",
            "https://",
            "https:// space.com",
            "1::2::3",
            "2001:db8:0:0:0:0:0:0:1",
            "12345::1",
            "fe80::1%wlan0",
            "1.2.3.4::"
        ).forEach { assertNull(it, DnsAddress.parse(it)) }
    }

    @Test
    fun `custom profile from the detected protocol`() {
        val udp = DnsProfile.custom("Pi-hole", DnsAddress.Udp("192.168.1.2"), id = "a")
        assertEquals(DnsProtocol.UDP, udp.protocol)
        assertEquals(listOf("192.168.1.2"), udp.servers)
        assertEquals("192.168.1.2", udp.address)

        val doh = DnsProfile.custom("NextDNS", DnsAddress.Doh("https://dns.nextdns.io/x"), id = "b")
        assertEquals(DnsProtocol.DOH, doh.protocol)
        assertEquals("https://dns.nextdns.io/x", doh.address)
        assertEquals(false, doh.isPreset)
    }
}
