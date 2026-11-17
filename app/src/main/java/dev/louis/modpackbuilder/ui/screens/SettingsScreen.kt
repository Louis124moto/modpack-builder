package dev.louis.modpackbuilder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.louis.modpackbuilder.data.local.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
    var settings by remember { mutableStateOf(store.read()) }
    var expanded by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Configuration") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Retour") }
    }, actions = { TextButton(onClick = { store.save(settings); onBack() }) { Text("Enregistrer") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fournisseur LLM", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = when (settings.provider) {
                        LlmProvider.OPENAI_COMPATIBLE -> "OpenAI / compatible"
                        LlmProvider.ANTHROPIC -> "Anthropic"
                        LlmProvider.GEMINI -> "Google Gemini"
                    },
                    onValueChange = {}, readOnly = true, label = { Text("Fournisseur") },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    LlmProvider.entries.forEach { provider ->
                        DropdownMenuItem(text = {
                            Text(when (provider) {
                                LlmProvider.OPENAI_COMPATIBLE -> "OpenAI / compatible"
                                LlmProvider.ANTHROPIC -> "Anthropic"
                                LlmProvider.GEMINI -> "Google Gemini"
                            })
                        }, onClick = {
                            val defaults = when (provider) {
                                LlmProvider.OPENAI_COMPATIBLE -> Triple("gpt-4o-mini", "https://api.openai.com/v1", settings.apiKey)
                                LlmProvider.ANTHROPIC -> Triple("claude-3-5-haiku-latest", "https://api.anthropic.com", settings.apiKey)
                                LlmProvider.GEMINI -> Triple("gemini-2.5-flash", "https://generativelanguage.googleapis.com", settings.apiKey)
                            }
                            settings = settings.copy(provider = provider, model = defaults.first, baseUrl = defaults.second)
                            expanded = false
                        })
                    }
                }
            }
            OutlinedTextField(
                settings.apiKey, { settings = settings.copy(apiKey = it) },
                label = { Text(when (settings.provider) {
                    LlmProvider.OPENAI_COMPATIBLE -> "Clé API"
                    LlmProvider.ANTHROPIC -> "Clé API Anthropic"
                    LlmProvider.GEMINI -> "Clé API Gemini"
                }) },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(settings.model, { settings = settings.copy(model = it) }, label = { Text("Modèle") }, modifier = Modifier.fillMaxWidth())
            if (settings.provider == LlmProvider.OPENAI_COMPATIBLE) {
                OutlinedTextField(settings.baseUrl, { settings = settings.copy(baseUrl = it) }, label = { Text("URL de base compatible OpenAI") }, modifier = Modifier.fillMaxWidth())
            }
            Text(
                "La clé est conservée localement dans des préférences chiffrées avec Android Keystore. Elle n'est jamais envoyée à Modrinth.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
