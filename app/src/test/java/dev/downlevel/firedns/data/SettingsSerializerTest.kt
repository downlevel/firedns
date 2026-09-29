package dev.downlevel.firedns.data

import androidx.datastore.core.CorruptionException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSerializerTest {

    private suspend fun roundTrip(settings: Settings): Settings {
        val out = ByteArrayOutputStream()
        SettingsSerializer.writeTo(settings, out)
        return SettingsSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `full round trip`() = runTest {
        val settings = Settings(
            activeProfileId = "c1",
            vpnDesiredOn = true,
            onboardingDone = true,
            customProfiles = listOf(
                DnsProfile.custom("Pi-hole", DnsAddress.Udp("192.168.1.2"), id = "c1"),
                DnsProfile.custom("NextDNS", DnsAddress.Doh("https://dns.nextdns.io/x"), id = "c2")
            )
        )
        assertEquals(settings, roundTrip(settings))
    }

    @Test
    fun `unknown fields ignored and missing ones defaulted`() = runTest {
        val json = """{"schemaVersion":1,"activeProfileId":"preset:quad9","futureField":42}"""
        val settings = SettingsSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))
        assertEquals("preset:quad9", settings.activeProfileId)
        assertEquals(Settings().autostartOnBoot, settings.autostartOnBoot)
        assertTrue(settings.customProfiles.isEmpty())
    }

    @Test
    fun `corrupted file`() = runTest {
        val result = runCatching { SettingsSerializer.readFrom(ByteArrayInputStream("{not json".toByteArray())) }
        assertTrue(result.exceptionOrNull() is CorruptionException)
    }
}
