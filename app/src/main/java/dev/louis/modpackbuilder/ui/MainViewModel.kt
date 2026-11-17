package dev.louis.modpackbuilder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.louis.modpackbuilder.data.api.LlmClient
import dev.louis.modpackbuilder.data.local.SettingsStore
import dev.louis.modpackbuilder.data.repository.ModpackExporter
import dev.louis.modpackbuilder.data.repository.ModpackRepository
import dev.louis.modpackbuilder.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false,
    val status: String = "",
    val mods: List<ResolvedMod> = emptyList(),
    val pack: GeneratedModpack? = null,
    val error: String? = null,
    val outputFormat: OutputFormat = OutputFormat.DIRECT_LINKS
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val llm = LlmClient()
    private val repository = ModpackRepository()
    val exporter = ModpackExporter()
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    fun generate(description: String, loader: ModLoader, automatic: Boolean, version: String, format: OutputFormat) {
        if (description.isBlank()) { _state.value = _state.value.copy(error = "Décris le modpack souhaité."); return }
        if (!automatic && version.isBlank()) { _state.value = _state.value.copy(error = "Saisis une version Minecraft."); return }
        viewModelScope.launch {
            _state.value = UiState(loading = true, status = "Analyse de la demande…", outputFormat = format)
            try {
                val settings = store.read()
                val names = llm.identifyMods(description, settings)
                _state.value = _state.value.copy(status = "Recherche des mods et dépendances sur Modrinth…")
                val pack = repository.resolve(names, ModpackRequest(description, loader, automatic, if (automatic) null else version, format))
                _state.value = _state.value.copy(loading = false, status = "Modpack prêt : ${pack.mods.size} mods.", mods = pack.mods, pack = pack)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message ?: "Erreur inconnue.")
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun settingsStore() = store
}
