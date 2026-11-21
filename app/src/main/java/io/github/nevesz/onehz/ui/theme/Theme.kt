package io.github.nevesz.onehz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.nevesz.onehz.core.ThemeMode

private val LightColors = lightColorScheme(
    primary = OneHzGreen,
    onPrimary = Color.White,
    primaryContainer = OneHzMint,
    onPrimaryContainer = OneHzGreenDark,
    secondary = OneHzTeal,
    onSecondary = Color.White,
    secondaryContainer = MetricTealBg,
    onSecondaryContainer = Color(0xFF00443D),
    tertiary = OneHzLime,
    onTertiary = Color.White,
    tertiaryContainer = OneHzLimeContainer,
    onTertiaryContainer = Color(0xFF1E3700),
    background = OneHzBg,
    onBackground = OneHzOnSurface,
    surface = OneHzSurface,
    onSurface = OneHzOnSurface,
    surfaceVariant = OneHzSurfaceVariant,
    onSurfaceVariant = OneHzOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FCFA),
    surfaceContainer = Color(0xFFF3F8F4),
    surfaceContainerHigh = Color(0xFFEDF4EF),
    surfaceContainerHighest = Color(0xFFE7F0EA),
    surfaceDim = Color(0xFFDDE8E0),
    surfaceBright = Color(0xFFFFFFFF),
    outline = OneHzOutline,
    outlineVariant = OneHzOutlineVariant,
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    onPrimary = OneHzGreenDark,
    primaryContainer = Color(0xFF1B4D24),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00332E),
    secondaryContainer = Color(0xFF1E4B46),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFC5E1A5),
    onTertiary = Color(0xFF1E3700),
    tertiaryContainer = Color(0xFF35521A),
    onTertiaryContainer = Color(0xFFDCEDC8),
    background = OneHzDarkBg,
    onBackground = OneHzDarkOnSurface,
    surface = OneHzDarkSurface,
    onSurface = OneHzDarkOnSurface,
    surfaceVariant = OneHzDarkSurfaceVariant,
    onSurfaceVariant = OneHzDarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF080D0A),
    surfaceContainerLow = Color(0xFF101711),
    surfaceContainer = Color(0xFF151C17),
    surfaceContainerHigh = Color(0xFF1B241D),
    surfaceContainerHighest = Color(0xFF223129),
    surfaceDim = OneHzDarkBg,
    surfaceBright = Color(0xFF2A362E),
    outline = OneHzDarkOutline,
    outlineVariant = OneHzDarkOutlineVariant,
    error = Color(0xFFFFB4AB)
)

/** True black surfaces for OLED panels. Elevated containers stay almost black on purpose. */
private val AmoledColors = darkColorScheme(
    primary = Color(0xFF8FE39A),
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF0E2E16),
    onPrimaryContainer = Color(0xFFB6F5BC),
    secondary = Color(0xFF7FD6CC),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF0B332F),
    onSecondaryContainer = Color(0xFFA8E8E0),
    tertiary = Color(0xFFC9E8A0),
    onTertiary = Color(0xFF1E3700),
    tertiaryContainer = Color(0xFF243A10),
    onTertiaryContainer = Color(0xFFDFF3C4),
    background = OneHzAmoledBlack,
    onBackground = OneHzAmoledOnSurface,
    surface = OneHzAmoledBlack,
    onSurface = OneHzAmoledOnSurface,
    surfaceVariant = OneHzAmoledSurfaceVariant,
    onSurfaceVariant = OneHzAmoledOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF020202),
    surfaceContainer = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF0A0A0A),
    surfaceContainerHighest = Color(0xFF121212),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF1A1A1A),
    outline = OneHzAmoledOutline,
    outlineVariant = OneHzAmoledOutlineVariant,
    error = Color(0xFFFFB4AB)
)

@Composable
fun OneHZTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = mode.isDark(isSystemInDarkTheme())
    val colors = when {
        mode == ThemeMode.AMOLED -> AmoledColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = OneHzTypography,
        shapes = OneHzShapes,
        content = content
    )
}
