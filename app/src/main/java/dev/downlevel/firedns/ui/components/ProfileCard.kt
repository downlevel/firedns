package dev.downlevel.firedns.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.downlevel.firedns.R
import dev.downlevel.firedns.data.DnsProfile
import dev.downlevel.firedns.ui.theme.FireDnsColors

private val CardWidth = 200.dp
private val CardHeight = 116.dp
private val CardShape = RoundedCornerShape(12.dp)

/**
 * Profile card in the Home row. [onMenu] (Menu key or long press) is `null` for
 * built-in profiles, which cannot be edited.
 */
@Composable
fun ProfileCard(
    profile: DnsProfile,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null
) {
    val activeLabel = stringResource(R.string.a11y_active_profile)
    Surface(
        onClick = onClick,
        onLongClick = onMenu,
        modifier = modifier
            .size(CardWidth, CardHeight)
            .then(if (onMenu != null) Modifier.onMenuKey(onMenu) else Modifier)
            .semantics { if (active) stateDescription = activeLabel },
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = FireFocus.colors(),
        border = FireFocus.border(
            CardShape,
            idle = if (active) Border(BorderStroke(2.dp, FireDnsColors.Fire), shape = CardShape) else Border.None
        ),
        scale = FireFocus.scale()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile)
                Spacer(Modifier.weight(1f))
                ProtocolBadge(profile.protocol)
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = FireDnsColors.Success,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    profile.displayAddress(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FireDnsColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Dashed "+ Add DNS" card; [showHint] in the empty state (no custom profiles). */
@Composable
fun AddProfileCard(onClick: () -> Unit, showHint: Boolean, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(CardWidth, CardHeight),
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = FireFocus.colors(container = Color.Transparent),
        border = FireFocus.border(CardShape),
        scale = FireFocus.scale()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    inset(stroke / 2) {
                        drawRoundRect(
                            color = FireDnsColors.TextDisabled,
                            cornerRadius = CornerRadius(12.dp.toPx()),
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
                            )
                        )
                    }
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.add_dns), style = MaterialTheme.typography.titleMedium)
            if (showHint) {
                Text(
                    stringResource(R.string.add_dns_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FireDnsColors.TextSecondary
                )
            }
        }
    }
}

private val AvatarColors = listOf(
    Color(0xFF3B6FD8),
    Color(0xFF2E9E6A),
    Color(0xFF8A5CD1),
    Color(0xFFB8860B),
    Color(0xFF238C8C),
    Color(0xFFC0508A)
)

/** Profile initial on a stable color derived from its id. */
@Composable
fun ProfileAvatar(profile: DnsProfile, modifier: Modifier = Modifier) {
    val color = AvatarColors[(profile.id.hashCode() and Int.MAX_VALUE) % AvatarColors.size]
    Box(modifier.size(32.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            profile.name.take(1).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}
