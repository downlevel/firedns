package dev.downlevel.firedns.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.downlevel.firedns.R
import dev.downlevel.firedns.data.DnsProfile
import dev.downlevel.firedns.ui.theme.FireDnsColors

@Composable
fun FireDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = FireDnsColors.Surface,
                contentColor = FireDnsColors.TextPrimary
            )
        ) {
            Column(Modifier.widthIn(min = 360.dp, max = 520.dp).padding(28.dp), content = content)
        }
    }
}

/** Confirmation of a destructive action: initial focus goes to "Cancel". */
@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    FireDialog(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = FireDnsColors.TextSecondary)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FireButton(
                stringResource(R.string.action_cancel),
                onDismiss,
                style = FireButtonStyle.Secondary,
                modifier = Modifier.focusRequester(cancelFocus)
            )
            FireButton(confirmLabel, onConfirm, style = FireButtonStyle.Danger)
        }
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    }
}

/** Context menu of a custom profile (Menu key or long press). */
@Composable
fun ProfileMenuDialog(profile: DnsProfile, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    FireDialog(onDismiss) {
        Text(profile.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        MenuItem(Icons.Filled.Edit, stringResource(R.string.menu_edit), onEdit, Modifier.focusRequester(firstFocus))
        Spacer(Modifier.height(8.dp))
        MenuItem(Icons.Filled.Delete, stringResource(R.string.menu_delete), onDelete)
        LaunchedEffect(Unit) { firstFocus.requestFocus() }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = FireFocus.colors(container = FireDnsColors.Surface),
        border = FireFocus.border(shape),
        scale = FireFocus.scale(focused = 1.02f)
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
