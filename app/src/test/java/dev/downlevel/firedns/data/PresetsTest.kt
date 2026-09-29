package dev.downlevel.firedns.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {

    @Test
    fun `built-in profiles are consistent`() {
        assertEquals(Presets.all.size, Presets.all.map { it.id }.toSet().size)
        Presets.all.forEach { p ->
            assertTrue(p.id, p.isPreset)
            assertEquals(p.id, DnsProtocol.DOH, p.protocol)
            assertTrue(p.id, DnsAddress.parse(p.dohUrl!!) is DnsAddress.Doh)
            assertTrue(p.id, p.bootstrapIps.isNotEmpty())
            p.bootstrapIps.forEach { ip -> assertTrue(ip, DnsAddress.parse(ip) is DnsAddress.Udp) }
        }
        assertEquals(Presets.cloudflare, Presets.default)
    }
}
