package dev.bitplugg.matesound.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import dev.bitplugg.matesound.data.model.ThemeMode

private val Orange = Color(0xFFFF5500)
private val OrangeSoft = Color(0xFFFF8A4C)

val MatesoundDarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1500),
    onPrimaryContainer = Color(0xFFFFD9C7),
    secondary = OrangeSoft,
    onSecondary = Color(0xFF1A0B00),
    tertiary = Color(0xFFFFB59B),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFEDEDEF),
    surface = Color(0xFF0B0B0D),
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF1B1B20),
    onSurfaceVariant = Color(0xFFA9A9B2),
    surfaceContainer = Color(0xFF141418),
    surfaceContainerHigh = Color(0xFF1A1A1F),
    surfaceContainerHighest = Color(0xFF202026),
    outline = Color(0xFF3A3A42),
    outlineVariant = Color(0xFF24242B),
    error = Color(0xFFFF6B6B),
)

val MatesoundLightColors = lightColorScheme(
    primary = Color(0xFFD94800),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A1000),
    secondary = Color(0xFFB34800),
    background = Color(0xFFFDFDFD),
    onBackground = Color(0xFF1A1A1C),
    surface = Color(0xFFFDFDFD),
    onSurface = Color(0xFF1A1A1C),
    surfaceVariant = Color(0xFFF0F0F3),
    onSurfaceVariant = Color(0xFF5A5A63),
    surfaceContainer = Color(0xFFF4F4F7),
    surfaceContainerHigh = Color(0xFFEDEDF1),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    outline = Color(0xFFC4C4CC),
)

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
        content = content,
    )
}
