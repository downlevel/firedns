package dev.downlevel.firedns.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pihole = DnsProfile.custom("Pi-hole", DnsAddress.Udp("192.168.1.2"), id = "c1")
    private val nextdns = DnsProfile.custom("NextDNS", DnsAddress.Doh("https://dns.nextdns.io/x"), id = "c2")

    private fun TestScope.newStore(): DataStore<Settings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        scope = backgroundScope,
        produceFile = { File(tmp.root, "settings.json") }
    )

    @Test
    fun `initially only built-in profiles and the default is active`() = runTest(UnconfinedTestDispatcher()) {
        val repo = ProfileRepository(newStore())
        assertEquals(Presets.all, repo.profiles.first())
        assertEquals(Presets.default, repo.activeProfile.first())
    }

    @Test
    fun `saves, replaces in place and activates a custom profile`() = runTest(UnconfinedTestDispatcher()) {
        val repo = ProfileRepository(newStore())
        repo.saveCustom(pihole)
        repo.saveCustom(nextdns)
        val renamed = pihole.copy(name = "Pi-hole home")
        repo.saveCustom(renamed)
        assertEquals(Presets.all + listOf(renamed, nextdns), repo.profiles.first())

        repo.setActive(nextdns.id)
        assertEquals(nextdns, repo.activeProfile.first())
    }

    @Test
    fun `unknown id is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val repo = ProfileRepository(newStore())
        repo.setActive(Presets.adguard.id)
        repo.setActive("missing")
        assertEquals(Presets.adguard, repo.activeProfile.first())
    }

    @Test
    fun `deleting the active profile falls back to the default`() = runTest(UnconfinedTestDispatcher()) {
        val repo = ProfileRepository(newStore())
        repo.saveCustom(pihole)
        repo.setActive(pihole.id)
        repo.deleteCustom(pihole.id)
        assertEquals(Presets.all, repo.profiles.first())
        assertEquals(Presets.default, repo.activeProfile.first())
    }

    @Test
    fun `built-in profiles cannot be saved`() = runTest(UnconfinedTestDispatcher()) {
        val repo = ProfileRepository(newStore())
        val result = runCatching { repo.saveCustom(Presets.google.copy(name = "Other")) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `persisted to file`() = runTest(UnconfinedTestDispatcher()) {
        val file = File(tmp.root, "settings.json")
        ProfileRepository(newStore()).saveCustom(pihole)
        assertTrue(file.readText().contains("192.168.1.2"))
    }
}
