package acn.amrita.chen.planner.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Personal key, encrypted on device; supplied only to the authenticated relay per request. */
object ApiKeyManager {
    @Synchronized private fun prefs(context: Context): android.content.SharedPreferences {
        // Remove the insecure fallback left by earlier builds; never migrate plaintext secrets.
        context.getSharedPreferences("acn_fallback_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(context, "acn_secure_prefs", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    fun saveApiKey(context: Context, apiKey: String) {
        require(apiKey.isNotBlank()) { "Enter an API key" }
        check(prefs(context).edit().putString("gemini_api_key", apiKey.trim()).commit())
    }
    fun getApiKey(context: Context): String? = prefs(context).getString("gemini_api_key", null)
    fun hasApiKey(context: Context): Boolean = runCatching { !getApiKey(context).isNullOrBlank() }.getOrDefault(false)
    fun clearApiKey(context: Context) { prefs(context).edit().clear().commit() }
}
