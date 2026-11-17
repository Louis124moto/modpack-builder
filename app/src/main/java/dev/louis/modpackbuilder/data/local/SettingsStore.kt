package dev.louis.modpackbuilder.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class LlmProvider { OPENAI_COMPATIBLE, ANTHROPIC, GEMINI }

data class AppSettings(
    val apiKey: String = "",
    val provider: LlmProvider = LlmProvider.OPENAI_COMPATIBLE,
    val model: String = "gpt-4o-mini",
    val baseUrl: String = "https://api.openai.com/v1"
)

class SettingsStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(
        context, "secure_settings", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun read() = AppSettings(
        apiKey = prefs.getString("api_key", "").orEmpty(),
        provider = runCatching { LlmProvider.valueOf(prefs.getString("provider", LlmProvider.OPENAI_COMPATIBLE.name)!!) }
            .getOrDefault(LlmProvider.OPENAI_COMPATIBLE),
        model = prefs.getString("model", "gpt-4o-mini").orEmpty(),
        baseUrl = prefs.getString("base_url", "https://api.openai.com/v1").orEmpty()
    )

    fun save(settings: AppSettings) {
        prefs.edit().putString("api_key", settings.apiKey.trim())
            .putString("provider", settings.provider.name)
            .putString("model", settings.model.trim())
            .putString("base_url", settings.baseUrl.trim().trimEnd('/')).apply()
    }
}
