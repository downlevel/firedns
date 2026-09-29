package dev.downlevel.firedns.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.downlevel.firedns.R
import dev.downlevel.firedns.data.DnsProfile
import dev.downlevel.firedns.data.DnsProtocol
import dev.downlevel.firedns.ui.theme.FireDnsColors
import dev.downlevel.firedns.vpn.VpnState
import java.net.URI

/** VPN state + active profile, next to the PowerToggle. */
@Composable
fun StatusHeader(state: VpnState, profile: DnsProfile, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        VpnState.Off -> R.string.status_off to FireDnsColors.TextSecondary
        VpnState.Starting -> R.string.status_starting to FireDnsColors.TextPrimary
        VpnState.Active -> R.string.status_active to FireDnsColors.Fire
        VpnState.Fallback -> R.string.status_fallback to FireDnsColors.Warning
        is VpnState.Error -> R.string.status_error to FireDnsColors.Error
    }
    Column(modifier) {
        Text(stringResource(label), style = MaterialTheme.typography.displaySmall, color = color)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(profile.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            ProtocolBadge(profile.protocol)
        }
        Text(
            profile.displayAddress(),
            style = MaterialTheme.typography.bodyMedium,
            color = FireDnsColors.TextSecondary
        )
    }
}

@Composable
fun ProtocolBadge(protocol: DnsProtocol, modifier: Modifier = Modifier) {
    val (label, background, foreground) = when (protocol) {
        DnsProtocol.DOH -> Triple("DoH", FireDnsColors.Fire.copy(alpha = 0.18f), FireDnsColors.Fire)
        DnsProtocol.UDP -> Triple("UDP", FireDnsColors.TextDisabled.copy(alpha = 0.35f), FireDnsColors.TextSecondary)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

/** Host for DoH (more readable than the full URL), IP for UDP. */
fun DnsProfile.displayAddress(): String =
    dohUrl?.let { runCatching { URI(it).host }.getOrNull() ?: it } ?: servers.joinToString()
