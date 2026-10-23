package dev.bitplugg.matesound.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import dev.bitplugg.matesound.data.model.ThemeMode

private val Orange = Color(0xFFFF5C00)
private val OrangeBright = Color(0xFFFF7A2A)
private val Coral = Color(0xFFFF8F5C)

val MatesoundDarkColors = darkColorScheme(
    primary = OrangeBright,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF4A1900),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Coral,
    onSecondary = Color(0xFF1A0A00),
    tertiary = Color(0xFFFFB99C),
    onTertiary = Color(0xFF2A1100),
    background = Color(0xFF0A0A0C),
    onBackground = Color(0xFFF2F1F4),
    surface = Color(0xFF101014),
    onSurface = Color(0xFFF2F1F4),
    surfaceVariant = Color(0xFF23232A),
    onSurfaceVariant = Color(0xFFB9B9C3),
    surfaceContainer = Color(0xFF151519),
    surfaceContainerHigh = Color(0xFF1B1B22),
    surfaceContainerHighest = Color(0xFF23232B),
    inverseSurface = Color(0xFFF2F1F4),
    inverseOnSurface = Color(0xFF101014),
    outline = Color(0xFF3D3D48),
    outlineVariant = Color(0xFF2A2A33),
    error = Color(0xFFFF7A6C),
)

val MatesoundLightColors = lightColorScheme(
    primary = Color(0xFFCC4C00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0D0),
    onPrimaryContainer = Color(0xFF3A1000),
    secondary = Color(0xFFB33F00),
    onSecondary = Color.White,
    tertiary = Color(0xFF7A2F00),
    onTertiary = Color.White,
    background = Color(0xFFFCFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFCFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF3F1F6),
    onSurfaceVariant = Color(0xFF5C5C66),
    surfaceContainer = Color(0xFFF7F6FA),
    surfaceContainerHigh = Color(0xFFF1EFF4),
    surfaceContainerHighest = Color(0xFFECEAF0),
    outline = Color(0xFFC7C5D0),
)

val MatesoundShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

val MatesoundTypography = Typography()

@Composable
fun MateSoundTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> MatesoundDarkColors
        else -> MatesoundLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MatesoundShapes,
        typography = MatesoundTypography,
        content = content,
    )
}
