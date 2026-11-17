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
                OutlinedTextField(value = settings.provider.name, onValueChange = {}, readOnly = true, label = { Text("Fournisseur") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    LlmProvider.entries.forEach { provider ->
                        DropdownMenuItem(text = { Text(if (provider == LlmProvider.ANTHROPIC) "Anthropic" else "OpenAI / compatible") }, onClick = {
                            settings = settings.copy(provider = provider,
                                model = if (provider == LlmProvider.ANTHROPIC) "claude-3-5-haiku-latest" else "gpt-4o-mini",
                                baseUrl = if (provider == LlmProvider.ANTHROPIC) "https://api.anthropic.com" else "https://api.openai.com/v1")
                            expanded = false
                        })
                    }
                }
            }
            OutlinedTextField(settings.apiKey, { settings = settings.copy(apiKey = it) }, label = { Text("Clé API") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.model, { settings = settings.copy(model = it) }, label = { Text("Modèle") }, modifier = Modifier.fillMaxWidth())
            if (settings.provider == LlmProvider.OPENAI_COMPATIBLE) {
                OutlinedTextField(settings.baseUrl, { settings = settings.copy(baseUrl = it) }, label = { Text("URL de base compatible OpenAI") }, modifier = Modifier.fillMaxWidth())
            }
            Text("La clé est conservée localement dans des préférences chiffrées avec Android Keystore. Elle n'est jamais envoyée à Modrinth.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
