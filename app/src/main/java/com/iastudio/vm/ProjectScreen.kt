package com.iastudio.vm

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProjectScreen(vm: AppViewModel, goModels: () -> Unit) {
    val chats = vm.allModels.filter { it.type == "chat" && it.file in vm.installed }
    var sel by remember { mutableStateOf<ModelEntry?>(null) }
    val model = sel?.takeIf { it in chats } ?: chats.firstOrNull { it.file.contains("coder", true) } ?: chats.firstOrNull()
    var name by remember { mutableStateOf("") }
    var request by remember { mutableStateOf("") }
    var change by remember { mutableStateOf("") }
    val clip = LocalClipboardManager.current
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    if (model == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TitleBar("Projet")
            Text("Installe d'abord une IA. Pour créer des logiciels, prends « Qwen2.5-Coder ».", color = grey)
            Button({ goModels() }) { Text("Choisir une IA") }
        }
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitleBar("Créer un logiciel")
        Text(
            "Décris ton logiciel. L'IA fait un plan, écrit chaque fichier sous tes yeux, puis tu télécharges tout dans un .zip.",
            color = grey,
        )
        ModelPicker(chats, model) { sel = it }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nom du projet (facultatif)") }, singleLine = true)
        OutlinedTextField(
            request, { request = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = 3,
            label = { Text("Que doit faire le logiciel ?") },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(vm.autoReview, { vm.updateReview(it) })
            Spacer(Modifier.width(8.dp))
            Text("Relecture automatique (plus fiable, plus long)", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.buildProject(name, request, model) }, enabled = !vm.busy) { Text("Générer le logiciel") }
            if (vm.busy) OutlinedButton({ vm.stopWork() }) { Text("Arrêter") }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (vm.status.isNotEmpty()) Text(vm.status, color = grey)
        if (vm.projLog.isNotEmpty()) Text(vm.projLog)

        // Vue en direct : on voit le texte apparaître pendant que l'IA écrit.
        if (vm.busy && vm.liveFile.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "✍ ${vm.liveFile}  ·  ${vm.liveText.length} caractères",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                    val live = rememberScrollState()
                    LaunchedEffect(vm.liveText.length) { live.scrollTo(live.maxValue) }
                    Box(Modifier.fillMaxWidth().height(260.dp).verticalScroll(live).padding(top = 6.dp)) {
                        Text(
                            if (vm.liveText.isEmpty()) "L'IA réfléchit…" else vm.liveText.takeLast(6000),
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp,
                        )
                    }
                }
            }
        }

        if (vm.projects.isNotEmpty()) {
            Text("Mes projets", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.projects.forEach { p -> FilterChip(vm.projectName == p, { if (!vm.busy) vm.openProject(p) }, { Text(p) }) }
            }
        }

        if (vm.projFiles.isNotEmpty()) {
            Text("Fichiers de « ${vm.projectName} »", style = MaterialTheme.typography.titleMedium)
            vm.projFiles.toList().forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ vm.preview(f.path) }, Modifier.weight(1f)) {
                        Text(f.path, Modifier.fillMaxWidth(), maxLines = 1)
                    }
                    Text(f.status, color = grey)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.exportProject() }, enabled = !vm.busy) { Text("Télécharger le .zip") }
                TextButton({ vm.deleteProject(vm.projectName) }, enabled = !vm.busy) { Text("Supprimer") }
            }

            vm.previewPath?.let { path ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(path, style = MaterialTheme.typography.labelMedium, color = grey)
                            TextButton({ clip.setText(AnnotatedString(vm.previewText)) }) { Text("Copier") }
                        }
                        Text(
                            vm.previewText.take(6000), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp,
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                        )
                    }
                }
            }

            Text("Améliorer ce projet", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                change, { change = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = 2,
                label = { Text("Ex. : ajoute un mode sombre, corrige le bouton Valider…") },
            )
            Button({ vm.modifyProject(change, model); change = "" }, enabled = !vm.busy) { Text("Appliquer les modifications") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
