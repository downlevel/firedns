package dev.downlevel.firedns.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.downlevel.firedns.ui.theme.FireDnsColors

@Composable
fun SettingsRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = FireFocus.colors(),
        border = FireFocus.border(shape),
        scale = FireFocus.scale(focused = 1.02f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = FireDnsColors.TextSecondary)
            }
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchSettingsRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsRow(
        title = title,
        description = description,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        }
    ) {
        FireSwitch(checked)
    }
}

/** Visual-only switch: clicks are handled by the whole row. */
@Composable
fun FireSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val thumbOffset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "thumb")
    Box(
        modifier
            .size(width = 48.dp, height = 28.dp)
            .background(if (checked) FireDnsColors.Fire else FireDnsColors.TextDisabled, RoundedCornerShape(50))
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset, y = 2.dp)
                .size(24.dp)
                .background(if (checked) FireDnsColors.OnFire else FireDnsColors.TextPrimary, CircleShape)
        )
    }
}
