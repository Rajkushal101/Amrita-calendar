package acn.amrita.chen.planner.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import acn.amrita.chen.planner.data.ThemeConfig

private val DarkColorScheme = darkColorScheme(
    primary = AcnRed,
    secondary = AcnRedDark,
    tertiary = AcnRedLight,
    background = AcnDark,
    surface = AcnDark2,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = AcnDark,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AcnRed,
    secondary = AcnRedDark,
    tertiary = AcnRedLight,
    background = AcnBg,
    surface = AcnSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = AcnDark,
    onBackground = AcnText,
    onSurface = AcnText
)

@Composable
fun AmritaCalendar2627Theme(
    themeConfig: ThemeConfig? = null,
    // We disable dynamic color to enforce ACN branding
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = themeConfig?.isDarkMode ?: isSystemInDarkTheme()
    val primaryColor = themeConfig?.primaryColorHex?.let {
        try {
            Color(android.graphics.Color.parseColor(it))
        } catch (e: Exception) {
            AcnRed
        }
    } ?: AcnRed

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme.copy(primary = primaryColor)
        else -> LightColorScheme.copy(primary = primaryColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
