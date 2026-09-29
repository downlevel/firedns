package dev.downlevel.firedns.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.downlevel.firedns.ui.theme.FireDnsColors

enum class FireButtonStyle { Primary, Secondary, Danger }

@Composable
fun FireButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FireButtonStyle = FireButtonStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(50)
    val colors = when (style) {
        FireButtonStyle.Primary -> FireFocus.colors(
            container = FireDnsColors.Fire,
            content = FireDnsColors.OnFire,
            focusedContainer = FireDnsColors.FireSoft
        )
        FireButtonStyle.Secondary -> FireFocus.colors()
        FireButtonStyle.Danger -> FireFocus.colors(
            content = FireDnsColors.Error,
            focusedContainer = FireDnsColors.Error,
            focusedContent = FireDnsColors.Graphite
        )
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = colors,
        border = FireFocus.border(
            shape,
            color = if (style == FireButtonStyle.Secondary) FireDnsColors.Fire else FireDnsColors.TextPrimary
        ),
        scale = FireFocus.scale()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
