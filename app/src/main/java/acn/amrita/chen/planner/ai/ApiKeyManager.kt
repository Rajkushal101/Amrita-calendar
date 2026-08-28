package acn.amrita.chen.planner.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages the Gemini API key using EncryptedSharedPreferences.
 * Key never leaves the device — BYOK (Bring Your Own Key) architecture.
 */
object ApiKeyManager {
    private const val PREFS_NAME = "acn_secure_prefs"
    private const val KEY_GEMINI = "gemini_api_key"

    private var prefs: android.content.SharedPreferences? = null

    private fun getPrefs(context: Context): android.content.SharedPreferences {
        if (prefs == null) {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                prefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // Fallback for devices where Keystore is corrupted or unsupported
                android.util.Log.e("ApiKeyManager", "EncryptedSharedPreferences failed", e)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                prefs = context.getSharedPreferences("acn_fallback_prefs", Context.MODE_PRIVATE)
            }
        }
        return prefs!!
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_GEMINI, apiKey).apply()
    }

    fun getApiKey(context: Context): String? {
        return getPrefs(context).getString(KEY_GEMINI, null)
    }

    fun hasApiKey(context: Context): Boolean {
        return !getApiKey(context).isNullOrBlank()
    }

    fun clearApiKey(context: Context) {
        getPrefs(context).edit().remove(KEY_GEMINI).apply()
    }
}
