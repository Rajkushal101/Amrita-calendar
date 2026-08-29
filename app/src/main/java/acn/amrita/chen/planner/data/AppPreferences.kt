package acn.amrita.chen.planner.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ThemeConfig(
    val primaryColorHex: String = "#C62828", // AcnRed
    val isDarkMode: Boolean = true
)

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _themeConfig = MutableStateFlow(
        ThemeConfig(
            primaryColorHex = prefs.getString("primary_color_hex", "#C62828") ?: "#C62828",
            isDarkMode = prefs.getBoolean("is_dark_mode", true)
        )
    )
    val themeConfig: StateFlow<ThemeConfig> = _themeConfig.asStateFlow()

    fun updateTheme(colorHex: String?, isDark: Boolean?) {
        val current = _themeConfig.value
        val newColor = colorHex ?: current.primaryColorHex
        val newIsDark = isDark ?: current.isDarkMode

        prefs.edit().apply {
            putString("primary_color_hex", newColor)
            putBoolean("is_dark_mode", newIsDark)
            apply()
        }

        _themeConfig.value = ThemeConfig(newColor, newIsDark)
    }
}
