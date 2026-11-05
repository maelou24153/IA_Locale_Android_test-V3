package com.iastudio.vm

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(vm: AppViewModel, goModels: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val chats = vm.allModels.filter { it.type == "chat" && it.file in vm.installed }
    var sel by remember { mutableStateOf<ModelEntry?>(null) }
    val model = sel?.takeIf { it in chats } ?: chats.firstOrNull()
    var text by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    val lastLen = vm.messages.lastOrNull()?.second?.length ?: 0
    LaunchedEffect(vm.messages.size, lastLen, vm.busy) { scroll.scrollTo(scroll.maxValue) }
    val attach = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val t = try {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }?.take(30000) ?: ""
            } catch (e: Exception) {
                ""
            }
            if (t.isNotBlank()) text += "\n```\n$t\n```\n"
        }
    }

    if (model == null) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StudioLogo(56.dp)
            Text("Studio", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Installe d'abord une IA pour discuter ou programmer.",
                color = grey, modifier = Modifier.padding(vertical = 12.dp),
            )
            Button({ goModels() }) { Text("Choisir une IA") }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StudioLogo(24.dp)
            Spacer(Modifier.width(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChatMode.values().forEach { m -> FilterChip(vm.mode == m, { vm.changeMode(m) }, { Text(m.label) }) }
            }
        }
        ModelPicker(chats, model) { sel = it }

        if (vm.messages.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StudioLogo(40.dp)
                Text(vm.mode.hello, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))
                val ideas = when (vm.mode) {
                    ChatMode.ASSISTANT -> listOf("Explique-moi comment fonctionne internet", "Donne-moi 5 idées de repas rapides", "Aide-moi à écrire un mail poli")
                    ChatMode.CODE -> listOf("Écris un script Python qui renomme des fichiers", "Fais une page web avec une calculatrice", "Explique ce que fait une boucle for")
                    ChatMode.PROMPT_IMAGE -> listOf("un chat astronaute sur la lune", "un village de montagne sous la neige")
                }
                ideas.forEach { s ->
                    SuggestionChip(onClick = { text = s }, label = { Text(s) }, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                vm.messages.toList().forEachIndexed { i, (r, c) ->
                    if (r == "user") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
                                Text(c.take(1500), Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                            }
                        }
                    } else {
                        val streaming = vm.busy && i == vm.messages.lastIndex
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Text("✻ ", color = MaterialTheme.colorScheme.primary)
                                Box(Modifier.weight(1f)) {
                                    if (c.isBlank()) Text("…", color = grey)
                                    else SelectionContainer { RichText(c) }
                                }
                            }
                            if (c.isNotBlank() && !streaming) {
                                Row {
                                    TextButton({ clip.setText(AnnotatedString(stripThink(c))) }) { Text("Copier") }
                                    if (i == vm.messages.lastIndex) TextButton({ vm.regenerate(model) }) { Text("Régénérer") }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (vm.status.isNotEmpty()) Text(vm.status, style = MaterialTheme.typography.bodyMedium, color = grey)
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f), label = { Text("Écris ton message") },
                shape = RoundedCornerShape(20.dp), maxLines = 5,
            )
            Spacer(Modifier.width(8.dp))
            if (vm.busy) Button({ vm.stopWork() }) { Text("Arrêter") }
            else Button({ vm.send(text, model); text = "" }) { Text("Envoyer") }
        }
        Row {
            TextButton({ attach.launch("*/*") }) { Text("Joindre un fichier") }
            if (vm.messages.isNotEmpty() && !vm.busy) TextButton({ vm.clearChat() }) { Text("Nouvelle conversation") }
        }
    }
}
