package dev.bitplugg.matesound.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.bitplugg.matesound.data.local.AccentStyle

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()
private val AmoledColorScheme = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainerHigh = Color(0xFF101010),
)

data class MateShapes(
    val cornerRadius: Dp = 16.dp,
)

val LocalMateShapes = staticCompositionLocalOf { MateShapes() }
val LocalAmoled = staticCompositionLocalOf { false }
val LocalGlass = staticCompositionLocalOf { false }

@Composable
fun MateSoundTheme(
    themeMode: dev.bitplugg.matesound.data.model.ThemeMode = dev.bitplugg.matesound.data.model.ThemeMode.DARK,
    dynamicColor: Boolean = true,
    accentStyle: AccentStyle = AccentStyle.DYNAMIC,
    cornerRadius: Int = 16,
    amoledBlack: Boolean = false,
    glassEffect: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        dev.bitplugg.matesound.data.model.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        dev.bitplugg.matesound.data.model.ThemeMode.DARK -> true
        dev.bitplugg.matesound.data.model.ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        darkTheme && amoledBlack -> AmoledColorScheme
        accentStyle == AccentStyle.DYNAMIC && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        accentStyle == AccentStyle.BLUE -> if (darkTheme) darkColorScheme(primary = Color(0xFF8AB4FF), secondary = Color(0xFF8AB4FF)) else lightColorScheme(primary = Color(0xFF1A73E8))
        accentStyle == AccentStyle.PURPLE -> if (darkTheme) darkColorScheme(primary = Color(0xFFD0BCFF), secondary = Color(0xFFD0BCFF)) else lightColorScheme(primary = Color(0xFF6750A4))
        accentStyle == AccentStyle.PINK -> if (darkTheme) darkColorScheme(primary = Color(0xFFF6B8D1), secondary = Color(0xFFF6B8D1)) else lightColorScheme(primary = Color(0xFFB71C5C))
        accentStyle == AccentStyle.ORANGE -> if (darkTheme) darkColorScheme(primary = Color(0xFFFFB77C), secondary = Color(0xFFFFB77C)) else lightColorScheme(primary = Color(0xFFE65100))
        accentStyle == AccentStyle.GREEN -> if (darkTheme) darkColorScheme(primary = Color(0xFF81C995), secondary = Color(0xFF81C995)) else lightColorScheme(primary = Color(0xFF2E7D32))
        accentStyle == AccentStyle.TEAL -> if (darkTheme) darkColorScheme(primary = Color(0xFF4DD0E1), secondary = Color(0xFF4DD0E1)) else lightColorScheme(primary = Color(0xFF00695C))
        accentStyle == AccentStyle.RED -> if (darkTheme) darkColorScheme(primary = Color(0xFFF28B82), secondary = Color(0xFFF28B82)) else lightColorScheme(primary = Color(0xFFD93025))
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalMateShapes provides MateShapes(cornerRadius = cornerRadius.dp),
        LocalAmoled provides (darkTheme && amoledBlack),
        LocalGlass provides glassEffect,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = androidx.compose.material3.Typography(),
            content = content,
        )
    }
}
