package dev.downlevel.firedns.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.downlevel.firedns.R
import dev.downlevel.firedns.data.Settings
import dev.downlevel.firedns.ui.components.SettingsRow
import dev.downlevel.firedns.ui.components.SwitchSettingsRow
import dev.downlevel.firedns.ui.components.screenPadding

@Composable
fun SettingsScreen(
    settings: Settings,
    onAutostartChange: (Boolean) -> Unit,
    onFallbackChange: (Boolean) -> Unit,
    onOpenInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocus = remember { FocusRequester() }
    Column(modifier.fillMaxSize().screenPadding()) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(28.dp))
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchSettingsRow(
                title = stringResource(R.string.settings_autostart),
                description = stringResource(R.string.settings_autostart_desc),
                checked = settings.autostartOnBoot,
                onCheckedChange = onAutostartChange,
                modifier = Modifier.focusRequester(firstFocus)
            )
            SwitchSettingsRow(
                title = stringResource(R.string.settings_fallback),
                description = stringResource(R.string.settings_fallback_desc),
                checked = settings.fallbackEnabled,
                onCheckedChange = onFallbackChange
            )
            SettingsRow(
                title = stringResource(R.string.settings_info),
                description = stringResource(R.string.settings_info_desc),
                onClick = onOpenInfo
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
}
