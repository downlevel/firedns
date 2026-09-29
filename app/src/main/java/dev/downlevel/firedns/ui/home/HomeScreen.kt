package dev.downlevel.firedns.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.downlevel.firedns.R
import dev.downlevel.firedns.data.DnsProfile
import dev.downlevel.firedns.ui.components.AddProfileCard
import dev.downlevel.firedns.ui.components.Banner
import dev.downlevel.firedns.ui.components.BannerKind
import dev.downlevel.firedns.ui.components.ConfirmDialog
import dev.downlevel.firedns.ui.components.FireFocus
import dev.downlevel.firedns.ui.components.PowerToggle
import dev.downlevel.firedns.ui.components.ProfileCard
import dev.downlevel.firedns.ui.components.ProfileMenuDialog
import dev.downlevel.firedns.ui.components.StatusHeader
import dev.downlevel.firedns.ui.components.screenPadding
import dev.downlevel.firedns.vpn.VpnError
import dev.downlevel.firedns.vpn.VpnState

@Composable
fun HomeScreen(
    state: HomeUiState,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (DnsProfile) -> Unit,
    onAdd: () -> Unit,
    onEdit: (DnsProfile) -> Unit,
    onDelete: (DnsProfile) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuFor by remember { mutableStateOf<DnsProfile?>(null) }
    var deleteFor by remember { mutableStateOf<DnsProfile?>(null) }
    val toggleFocus = remember { FocusRequester() }

    Column(modifier.fillMaxSize().screenPadding()) {
        TopBar(onOpenSettings)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PowerToggle(state.vpnState, onToggle, Modifier.focusRequester(toggleFocus))
            Spacer(Modifier.width(40.dp))
            StatusHeader(state.vpnState, state.activeProfile)
        }
        StatusBanner(state.vpnState, onRetry, Modifier.padding(top = 16.dp))
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.home_profiles), style = MaterialTheme.typography.titleLarge)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            // Room for the focus scale, otherwise the card gets clipped.
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp)
        ) {
            items(state.profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    active = profile.id == state.activeProfile.id,
                    onClick = { onSelect(profile) },
                    onMenu = if (profile.isPreset) null else ({ menuFor = profile })
                )
            }
            item(key = "add") {
                AddProfileCard(onClick = onAdd, showHint = !state.hasCustomProfiles)
            }
        }
    }

    LaunchedEffect(Unit) { toggleFocus.requestFocus() }

    menuFor?.let { profile ->
        ProfileMenuDialog(
            profile = profile,
            onEdit = {
                menuFor = null
                onEdit(profile)
            },
            onDelete = {
                menuFor = null
                deleteFor = profile
            },
            onDismiss = { menuFor = null }
        )
    }
    deleteFor?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.delete_title, profile.name),
            message = stringResource(R.string.delete_message),
            confirmLabel = stringResource(R.string.menu_delete),
            onConfirm = {
                deleteFor = null
                onDelete(profile)
            },
            onDismiss = { deleteFor = null }
        )
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(
            painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = onOpenSettings,
            modifier = Modifier.size(48.dp),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = FireFocus.colors(container = Color.Transparent),
            border = FireFocus.border(CircleShape),
            scale = FireFocus.scale()
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_title),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun StatusBanner(state: VpnState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        VpnState.Fallback -> Banner(stringResource(R.string.banner_fallback), BannerKind.Warning, modifier)
        is VpnState.Error -> Banner(
            text = stringResource(
                when (state.reason) {
                    VpnError.PERMISSION_DENIED -> R.string.banner_permission_denied
                    VpnError.REVOKED -> R.string.banner_revoked
                }
            ),
            kind = BannerKind.Error,
            modifier = modifier,
            actionLabel = stringResource(R.string.action_retry),
            onAction = onRetry
        )
        else -> Unit
    }
}
