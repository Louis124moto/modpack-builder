package dev.louis.modpackbuilder.ui

import android.app.Application
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private fun addLog(message: String) {
        val entry = "${java.time.LocalTime.now().withNano(0)}  $message"
        _logs.value = (_logs.value + entry).takeLast(150)
        Log.i(TAG, message)
    }

    private fun addErrorLog(message: String, error: Throwable) {
        val details = generateSequence(error) { it.cause }
            .take(8)
            .joinToString(" <- ") { cause ->
                "${cause.javaClass.simpleName}: ${cause.message ?: "aucun détail"}"
            }
        val entry = "${java.time.LocalTime.now().withNano(0)}  ERREUR — $message — $details"
        _logs.value = (_logs.value + entry).takeLast(150)
        Log.e(TAG, message, error)
    }

    fun generate(description: String, loader: ModLoader, automatic: Boolean, version: String, format: OutputFormat) {
        if (description.isBlank()) { _state.value = _state.value.copy(error = "Décris le modpack souhaité."); return }
        if (!automatic && version.isBlank()) { _state.value = _state.value.copy(error = "Saisis une version Minecraft."); return }
        viewModelScope.launch {
            _state.value = UiState(loading = true, status = "Analyse de la demande…", outputFormat = format)
            var phase = "initialisation"
            try {
                val settings = store.read()
                phase = "fournisseur IA"
                addLog("Génération démarrée — fournisseur : ${settings.provider}, modèle : ${settings.model}")
                addLog("Étape 1/2 — connexion au fournisseur IA")
                val names = try {
                    llm.identifyMods(description, settings)
                } catch (t: Throwable) {
                    addErrorLog("Échec de la requête au fournisseur IA", t)
                    throw IllegalStateException(
                        "Échec de connexion au fournisseur IA (${settings.provider}, modèle ${settings.model}) : ${rootCauseMessage(t)}",
                        t
                    )
                }
                addLog("Étape 1/2 terminée — ${names.size} mods identifiés")
                phase = "Modrinth"
                _state.value = _state.value.copy(status = "Recherche des mods et dépendances sur Modrinth…")
                addLog("Étape 2/2 — recherche de ${names.size} mods sur Modrinth")
                val pack = try {
                    repository.resolve(names, ModpackRequest(description, loader, automatic, if (automatic) null else version, format))
                } catch (t: Throwable) {
                    addErrorLog("Échec de la résolution Modrinth", t)
                    throw IllegalStateException(
                        "Échec de connexion ou de résolution sur Modrinth : ${rootCauseMessage(t)}",
                        t
                    )
                }
                addLog("Génération terminée — ${pack.mods.size} mods résolus")
                _state.value = _state.value.copy(loading = false, status = "Modpack prêt : ${pack.mods.size} mods.", mods = pack.mods, pack = pack)
            } catch (t: Throwable) {
                addErrorLog("Génération échouée pendant l'étape : $phase", t)
                _state.value = _state.value.copy(loading = false, error = t.message ?: "Erreur inconnue.")
            }
        }
    }

    fun exportZip(pack: GeneratedModpack, onReady: (ByteArray) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, status = "Téléchargement des fichiers .jar…", error = null)
            try {
                val bytes = withContext(Dispatchers.IO) { exporter.zipBytes(pack) }
                _state.value = _state.value.copy(loading = false, status = "Archive prête.")
                onReady(bytes)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message ?: "Échec de création du ZIP.")
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun clearLogs() { _logs.value = emptyList() }
    fun settingsStore() = store

    private fun rootCauseMessage(error: Throwable): String {
        var cause = error
        while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
        return cause.message ?: cause.javaClass.simpleName
    }

    companion object {
        private const val TAG = "ModpackBuilder"
    }
}
