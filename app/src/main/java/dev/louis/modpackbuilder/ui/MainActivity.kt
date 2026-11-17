package dev.louis.modpackbuilder.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.louis.modpackbuilder.ui.screens.MainScreen
import dev.louis.modpackbuilder.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            var showSettings by remember { mutableStateOf(false) }
            MaterialTheme {
                Surface {
                    if (showSettings) SettingsScreen(vm.settingsStore()) { showSettings = false }
                    else MainScreen(vm) { showSettings = true }
                }
            }
        }
    }
}
