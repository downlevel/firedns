package dev.downlevel.firedns.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/** DESIGN.md palette (dark theme only). */
object FireDnsColors {
    val Graphite = Color(0xFF121315)
    val Surface = Color(0xFF1E2024)
    val SurfaceFocused = Color(0xFF2A2D32)
    val Fire = Color(0xFFFF7A1A)
    val OnFire = Color(0xFF1A0E05)
    val FireSoft = Color(0xFFFFB347)
    val TextPrimary = Color(0xFFF2F2F3)
    val TextSecondary = Color(0xFFA9ADB4)
    val TextDisabled = Color(0xFF6B6F76)
    val Warning = Color(0xFFF5B400)
    val Error = Color(0xFFFF5A5F)
    val Success = Color(0xFF4CD08A)
}

private val colorScheme = darkColorScheme(
    primary = FireDnsColors.Fire,
    onPrimary = FireDnsColors.OnFire,
    secondary = FireDnsColors.FireSoft,
    onSecondary = FireDnsColors.OnFire,
    background = FireDnsColors.Graphite,
    onBackground = FireDnsColors.TextPrimary,
    surface = FireDnsColors.Surface,
    onSurface = FireDnsColors.TextPrimary,
    surfaceVariant = FireDnsColors.SurfaceFocused,
    onSurfaceVariant = FireDnsColors.TextSecondary,
    error = FireDnsColors.Error,
    onError = FireDnsColors.Graphite,
    border = FireDnsColors.Fire,
    inverseSurface = FireDnsColors.TextPrimary,
    inverseOnSurface = FireDnsColors.Graphite
)

/** TV type scale (DESIGN.md): never below 14 sp. System font (Roboto on Fire OS). */
private val typography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun FireDnsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        // tv-material's default content color is black: text outside a Surface would vanish on graphite.
        CompositionLocalProvider(LocalContentColor provides FireDnsColors.TextPrimary, content = content)
    }
}
