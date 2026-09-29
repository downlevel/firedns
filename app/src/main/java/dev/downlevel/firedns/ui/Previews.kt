package dev.downlevel.firedns.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.MaterialTheme
import dev.downlevel.firedns.data.DnsAddress
import dev.downlevel.firedns.data.DnsProfile
import dev.downlevel.firedns.data.Presets
import dev.downlevel.firedns.data.Settings
import dev.downlevel.firedns.ui.editor.EditorForm
import dev.downlevel.firedns.ui.editor.EditorScreen
import dev.downlevel.firedns.ui.home.HomeScreen
import dev.downlevel.firedns.ui.home.HomeUiState
import dev.downlevel.firedns.ui.onboarding.OnboardingScreen
import dev.downlevel.firedns.ui.settings.InfoScreen
import dev.downlevel.firedns.ui.settings.SettingsScreen
import dev.downlevel.firedns.ui.theme.FireDnsTheme
import dev.downlevel.firedns.vpn.VpnError
import dev.downlevel.firedns.vpn.VpnState

// Previews with fake data for every UI state (Android Studio → Split/Design).

private val customProfiles = listOf(
    DnsProfile.custom("NextDNS home", DnsAddress.Doh("https://dns.nextdns.io/abc123"), id = "c1"),
    DnsProfile.custom("Pi-hole", DnsAddress.Udp("192.168.1.2"), id = "c2")
)

@Composable
private fun Frame(content: @Composable () -> Unit) {
    FireDnsTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

@Composable
private fun HomePreview(vpnState: VpnState, withCustom: Boolean = true) = Frame {
    HomeScreen(
        state = HomeUiState(
            vpnState = vpnState,
            profiles = Presets.all + if (withCustom) customProfiles else emptyList(),
            activeProfile = Presets.adguard
        ),
        onToggle = {},
        onRetry = {},
        onSelect = {},
        onAdd = {},
        onEdit = {},
        onDelete = {},
        onOpenSettings = {}
    )
}

@Preview(name = "Home · active", device = Devices.TV_1080p)
@Composable
private fun HomeActivePreview() = HomePreview(VpnState.Active)

@Preview(name = "Home · off, no custom profiles", device = Devices.TV_1080p)
@Composable
private fun HomeOffEmptyPreview() = HomePreview(VpnState.Off, withCustom = false)

@Preview(name = "Home · starting", device = Devices.TV_1080p)
@Composable
private fun HomeStartingPreview() = HomePreview(VpnState.Starting)

@Preview(name = "Home · fallback", device = Devices.TV_1080p)
@Composable
private fun HomeFallbackPreview() = HomePreview(VpnState.Fallback)

@Preview(name = "Home · VPN revoked", device = Devices.TV_1080p)
@Composable
private fun HomeRevokedPreview() = HomePreview(VpnState.Error(VpnError.REVOKED))

@Preview(name = "Editor · invalid address", device = Devices.TV_1080p)
@Composable
private fun EditorErrorPreview() = Frame {
    EditorScreen(true, EditorForm("Pi-hole", "192.168.1"), {}, {}, {}, {})
}

@Preview(name = "Editor · DoH", device = Devices.TV_1080p)
@Composable
private fun EditorDohPreview() = Frame {
    EditorScreen(false, EditorForm("NextDNS home", "https://dns.nextdns.io/abc123"), {}, {}, {}, {})
}

@Preview(name = "Onboarding", device = Devices.TV_1080p)
@Composable
private fun OnboardingPreview() = Frame { OnboardingScreen(onContinue = {}) }

@Preview(name = "Settings", device = Devices.TV_1080p)
@Composable
private fun SettingsPreview() = Frame { SettingsScreen(Settings(), {}, {}, {}) }

@Preview(name = "Info", device = Devices.TV_1080p)
@Composable
private fun InfoPreview() = Frame { InfoScreen(versionName = "0.1.0-dev") }
