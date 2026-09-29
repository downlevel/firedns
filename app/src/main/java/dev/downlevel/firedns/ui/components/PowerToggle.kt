package dev.downlevel.firedns.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import dev.downlevel.firedns.R
import dev.downlevel.firedns.ui.theme.FireDnsColors
import dev.downlevel.firedns.vpn.VpnState
import dev.downlevel.firedns.vpn.isOn

/** Big ON/OFF button of the Home screen. */
@Composable
fun PowerToggle(state: VpnState, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val shape = CircleShape
    val lit = state is VpnState.Active || state is VpnState.Fallback
    val idleBorderColor = when {
        state is VpnState.Error -> FireDnsColors.Error
        lit -> FireDnsColors.FireSoft
        else -> FireDnsColors.TextDisabled
    }
    val description = stringResource(if (state.isOn) R.string.a11y_power_off else R.string.a11y_power_on)
    Surface(
        onClick = onToggle,
        modifier = modifier
            .size(160.dp)
            .semantics { contentDescription = description },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = if (lit) {
            FireFocus.colors(
                container = FireDnsColors.Fire,
                content = FireDnsColors.OnFire,
                focusedContainer = FireDnsColors.FireSoft
            )
        } else {
            FireFocus.colors(
                content = if (state is VpnState.Error) FireDnsColors.Error else FireDnsColors.TextSecondary,
                focusedContent = FireDnsColors.TextPrimary
            )
        },
        border = FireFocus.border(
            shape,
            color = if (lit) FireDnsColors.TextPrimary else FireDnsColors.Fire,
            idle = Border(BorderStroke(3.dp, idleBorderColor), shape = shape)
        ),
        scale = FireFocus.scale()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state is VpnState.Starting) {
                Spinner(color = FireDnsColors.Fire, modifier = Modifier.size(72.dp))
            } else {
                Icon(painterResource(R.drawable.ic_power), contentDescription = null, modifier = Modifier.size(64.dp))
            }
        }
    }
}

@Composable
fun Spinner(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "angle"
    )
    Canvas(modifier) {
        val stroke = 6.dp.toPx()
        inset(stroke / 2) {
            rotate(angle) {
                drawArc(
                    color,
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
    }
}
