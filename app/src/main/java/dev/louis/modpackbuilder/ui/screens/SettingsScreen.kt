package dev.louis.modpackbuilder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.louis.modpackbuilder.data.local.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: SettingsStore,
    logs: List<String>,
    onClearLogs: () -> Unit,
    onBack: () -> Unit
) {
    var settings by remember { mutableStateOf(store.read()) }
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val logScrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) logScrollState.animateScrollTo(logScrollState.maxValue)
    }

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
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Journal de diagnostic", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(logs.joinToString("\n\n"))) },
                        enabled = logs.isNotEmpty()
                    ) { Text("Tout copier") }
                    TextButton(onClick = onClearLogs, enabled = logs.isNotEmpty()) { Text("Effacer") }
                }
            }
            Text(
                "Journal détaillé des requêtes réseau : méthode, URL, code HTTP, durée et corps des réponses en erreur. Les clés API et les en-têtes d'authentification ne sont pas journalisés. Les derniers événements restent disponibles en mémoire.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("${logs.size} événement(s) — les plus récents sont en bas", style = MaterialTheme.typography.labelSmall)
            if (logs.isEmpty()) {
                Text("Aucun événement pour le moment.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 340.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(logScrollState).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        logs.forEach { entry ->
                            Text(
                                entry,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.contains("ERREUR") || entry.contains("HTTP ← ERREUR") || entry.contains("HTTP ✕"))
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}
