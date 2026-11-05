package com.iastudio.vm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(vm.themeMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root(vm) }
            }
        }
    }
}

@Composable
fun Root(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // L'écran reste allumé pendant qu'une IA travaille ou qu'un modèle se télécharge.
    val keepOn = vm.busy || vm.progress.isNotEmpty()
    val view = LocalView.current
    SideEffect { view.keepScreenOn = keepOn }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Chat, null) }, label = { Text("Chat") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Code, null) }, label = { Text("Projet") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Image, null) }, label = { Text("Images") })
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Storage, null) }, label = { Text("Modèles") })
                NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Réglages") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).padding(horizontal = 16.dp, vertical = 12.dp).fillMaxSize()) {
            when (tab) {
                0 -> ChatScreen(vm) { tab = 3 }
                1 -> ProjectScreen(vm) { tab = 3 }
                2 -> ImageScreen(vm) { tab = 3 }
                3 -> ModelsScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
