package dev.louis.modpackbuilder.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.louis.modpackbuilder.domain.model.*
import dev.louis.modpackbuilder.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, onSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var loader by remember { mutableStateOf(ModLoader.FABRIC) }
    var loaderExpanded by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(OutputFormat.DIRECT_LINKS) }
    var formatExpanded by remember { mutableStateOf(false) }
    var automatic by remember { mutableStateOf(true) }
    var version by remember { mutableStateOf("1.20.1") }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    val jsonSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) pendingBytes?.let { bytes -> context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
        pendingBytes = null
    }
    val zipSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) pendingBytes?.let { bytes -> context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
        pendingBytes = null
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Modpack Builder") }, actions = {
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Configuration") }
    }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Décris ton modpack") }, placeholder = { Text("Ex. exploration médiévale, ruines, magie légère…") }) }
            item {
                ExposedDropdownMenuBox(expanded = loaderExpanded, onExpandedChange = { loaderExpanded = it }) {
                    OutlinedTextField(loader.name, {}, readOnly = true, label = { Text("Mod loader") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = loaderExpanded, onDismissRequest = { loaderExpanded = false }) {
                        ModLoader.entries.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { loader = item; loaderExpanded = false }) }
                    }
                }
            }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Version Minecraft automatique")
                Switch(checked = automatic, onCheckedChange = { automatic = it })
            } }
            if (!automatic) item { OutlinedTextField(version, { version = it }, label = { Text("Version Minecraft") }, modifier = Modifier.fillMaxWidth()) }
            item {
                ExposedDropdownMenuBox(expanded = formatExpanded, onExpandedChange = { formatExpanded = it }) {
                    OutlinedTextField(when(format) {
                        OutputFormat.DIRECT_LINKS -> "Liens directs"
                        OutputFormat.MODRINTH_INDEX -> "Fichier config (modrinth.index.json)"
                        OutputFormat.ZIP -> "Archive ZIP"
                    }, {}, readOnly = true, label = { Text("Format de sortie") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                        listOf(OutputFormat.DIRECT_LINKS to "Liens directs", OutputFormat.MODRINTH_INDEX to "Fichier config (modrinth.index.json)", OutputFormat.ZIP to "Archive ZIP").forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { format = value; formatExpanded = false })
                        }
                    }
                }
            }
            item { Button(onClick = { vm.generate(description, loader, automatic, version, format) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Générer le Modpack")
            } }
            if (state.status.isNotBlank()) item { Text(state.status, style = MaterialTheme.typography.bodyMedium) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm::clearError) { Text("Fermer") } } }
            if (state.pack != null) {
                item { Text("Minecraft ${state.pack!!.minecraftVersion} • ${state.pack!!.loader.name} ${state.pack!!.loaderVersion}", style = MaterialTheme.typography.titleMedium) }
                if (format == OutputFormat.MODRINTH_INDEX) item {
                    Button(onClick = {
                        pendingBytes = vm.exporter.indexBytes(state.pack!!)
                        jsonSaver.launch("modrinth.index.json")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer modrinth.index.json") }
                }
                if (format == OutputFormat.ZIP) item {
                    Button(onClick = {
                        pendingBytes = runCatching { vm.exporter.zipBytes(state.pack!!) }.getOrElse {
                            null
                        }
                        if (pendingBytes != null) zipSaver.launch("modpack.zip")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Télécharger et enregistrer le ZIP") }
                }
                items(state.mods) { mod ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(mod.projectTitle, style = MaterialTheme.typography.titleSmall)
                            Text(mod.versionNumber, style = MaterialTheme.typography.bodySmall)
                            if (format == OutputFormat.DIRECT_LINKS) TextButton(onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(mod.downloadUrl))
                                context.startActivity(intent)
                            }) { Text("Télécharger ${mod.fileName}") }
                        }
                    }
                }
            }
        }
    }
}
