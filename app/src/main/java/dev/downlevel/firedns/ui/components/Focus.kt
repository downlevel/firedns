package dev.downlevel.firedns.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import dev.downlevel.firedns.ui.theme.FireDnsColors

/** Uniform focus style (DESIGN.md): 1.05 scale, 3 dp orange border, lighter surface. */
object FireFocus {
    const val SCALE = 1.05f
    val BorderWidth = 3.dp

    @Composable
    fun border(shape: Shape, color: Color = FireDnsColors.Fire, idle: Border = Border.None): ClickableSurfaceBorder {
        val focused = Border(BorderStroke(BorderWidth, color), shape = shape)
        return ClickableSurfaceDefaults.border(border = idle, focusedBorder = focused, pressedBorder = focused)
    }

    @Composable
    fun scale(focused: Float = SCALE): ClickableSurfaceScale = ClickableSurfaceDefaults.scale(focusedScale = focused)

    @Composable
    fun colors(
        container: Color = FireDnsColors.Surface,
        content: Color = FireDnsColors.TextPrimary,
        focusedContainer: Color = FireDnsColors.SurfaceFocused,
        focusedContent: Color = content
    ): ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
        containerColor = container,
        contentColor = content,
        focusedContainerColor = focusedContainer,
        focusedContentColor = focusedContent,
        pressedContainerColor = focusedContainer,
        pressedContentColor = focusedContent,
        disabledContainerColor = container,
        disabledContentColor = FireDnsColors.TextDisabled
    )
}

/** TV overscan safe margins. */
fun Modifier.screenPadding(): Modifier = padding(horizontal = 48.dp, vertical = 27.dp)

/** Remote control Menu key (☰). */
fun Modifier.onMenuKey(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key != Key.Menu) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyUp) action()
    true
}
