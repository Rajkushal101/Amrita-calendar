package acn.amrita.chen.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import acn.amrita.chen.planner.data.ThemeConfig

val LightColorScheme = lightColorScheme(
    primary = AcnLightPrimary,
    onPrimary = AcnLightOnPrimary,
    primaryContainer = AcnLightSurfaceVariant,
    onPrimaryContainer = AcnBrandCrimsonDark,
    secondary = Color(0xFF91435D),
    onSecondary = Color.White,
    secondaryContainer = AcnLightSurfaceVariant,
    onSecondaryContainer = Color(0xFF3B071B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF31101D),
    background = AcnLightBackground,
    onBackground = AcnLightText,
    surface = AcnLightSurface,
    onSurface = AcnLightText,
    surfaceVariant = AcnLightSurfaceVariant,
    onSurfaceVariant = AcnLightTextSecondary,
    outline = AcnLightBorder,
    outlineVariant = Color(0xFFF0E5EB),
    error = AcnErrorLight,
    onError = Color.White,
    errorContainer = AcnErrorContainerLight,
    onErrorContainer = Color(0xFF410002)
)

val DarkColorScheme = darkColorScheme(
    primary = AcnDarkPrimary,
    onPrimary = AcnDarkOnPrimary,
    primaryContainer = AcnDarkPrimary,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFE5B9C8),
    onSecondary = Color(0xFF422934),
    secondaryContainer = Color(0xFF4E313D),
    onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = Color(0xFFEFBDCC),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B49),
    onTertiaryContainer = Color(0xFFFFD9E2),
    background = AcnDarkBackground,
    onBackground = AcnDarkText,
    surface = AcnDarkSurface,
    onSurface = AcnDarkText,
    surfaceVariant = AcnDarkSurfaceRaised,
    onSurfaceVariant = AcnDarkTextSecondary,
    outline = AcnDarkBorder,
    outlineVariant = Color(0xFF362C32),
    error = AcnErrorDark,
    onError = Color(0xFF690005),
    errorContainer = AcnErrorContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun AcnPlannerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun AmritaCalendar2627Theme(
    themeConfig: ThemeConfig? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = themeConfig?.isDarkMode ?: false
    AcnPlannerTheme(darkTheme = darkTheme, content = content)
}

@Composable
fun brandAccentText(): Color {
    val isDark = MaterialTheme.colorScheme.background == AcnDarkBackground
    return if (isDark) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson
}
