package com.iastudio.vm

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val clip = LocalClipboardManager.current
    var newName by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TitleBar("Réglages")

        // ---- Apparence
        Text("Apparence", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.values().forEach { m -> FilterChip(vm.themeMode == m, { vm.updateTheme(m) }, { Text(m.label) }) }
        }

        // ---- Machines
        HorizontalDivider()
        Text("Machines virtuelles", style = MaterialTheme.typography.titleMedium)
        Text("Une machine est un espace isolé : ses IA, projets, images et conversations restent séparés.", color = grey, fontSize = 13.sp)
        vm.machines.forEach { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(vm.current == n, { vm.select(n) })
                Text(n, Modifier.weight(1f))
                TextButton({ vm.deleteMachine(n) }) { Text("Supprimer") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newName, { newName = it }, Modifier.weight(1f), label = { Text("Nouvelle machine") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button({ vm.createMachine(newName); newName = "" }) { Text("Créer") }
        }

        // ---- IA
        HorizontalDivider()
        Text("Réglages de l'IA", style = MaterialTheme.typography.titleMedium)
        Text("Créativité : ${"%.1f".format(vm.temperature)}  (bas = précis, idéal pour le code)", color = grey)
        Slider(vm.temperature, { vm.updateTemperature(it) }, valueRange = 0f..1.2f)
        Text("Mémoire de conversation : ${vm.ctxSize}  (plus grand = plus de RAM)", color = grey)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2048, 4096, 8192, 16384).forEach { c -> FilterChip(vm.ctxSize == c, { vm.updateCtx(c) }, { Text("$c") }) }
        }
        Text("Longueur maximale des réponses : ${vm.maxTokens}", color = grey)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(512, 1024, 2048, 4096).forEach { c -> FilterChip(vm.maxTokens == c, { vm.updateMaxTokens(c) }, { Text("$c") }) }
        }
        Text("Threads CPU : ${vm.threads}  (nombre de cœurs moins 1 conseillé)", color = grey)
        Slider(vm.threads.toFloat(), { vm.updateThreads(it.toInt()) }, valueRange = 1f..8f, steps = 6)
        OutlinedTextField(
            vm.customPrompt, { vm.updateCustomPrompt(it) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = 2,
            label = { Text("Instructions personnalisées (ex. : réponds toujours brièvement)") },
        )
        OutlinedTextField(
            vm.hfToken, { vm.updateToken(it) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Jeton Hugging Face (modèles réservés, facultatif)") },
            visualTransformation = PasswordVisualTransformation(),
        )

        // ---- Diagnostic
        HorizontalDivider()
        Text("Analyse complète", style = MaterialTheme.typography.titleMedium)
        Text(
            "Vérifie le téléphone, les moteurs, tous les modèles installés (fichiers complets) et la connexion à Hugging Face.",
            color = grey, fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.runDiagnostics() }, enabled = !vm.diagBusy) { Text("Analyser mon Studio") }
            if (vm.report.isNotEmpty()) {
                OutlinedButton({ clip.setText(AnnotatedString(vm.report.joinToString("\n"))) }) { Text("Copier le rapport") }
            }
        }
        if (vm.diagBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (vm.report.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    vm.report.toList().forEach { line ->
                        Text(
                            line, fontSize = 13.sp,
                            color = if (line.startsWith("✗")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // ---- Notice
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notice d'utilisation", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton({ showHelp = !showHelp }) { Text(if (showHelp) "Masquer" else "Afficher") }
        }
        if (showHelp) HelpContent()

        HorizontalDivider()
        Text("Studio $APP_VERSION · ${vm.engines}", color = grey, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun HelpContent() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lines = remember { ctx.assets.open("NOTICE.md").bufferedReader().readLines() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { l ->
            when {
                l.startsWith("# ") -> Text(l.drop(2), style = MaterialTheme.typography.headlineMedium)
                l.startsWith("## ") -> Text(l.drop(3), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleLarge)
                l.startsWith("- ") -> Text("•  " + l.drop(2).replace("**", ""))
                l.isBlank() -> Spacer(Modifier.height(2.dp))
                else -> Text(l.replace("**", ""))
            }
        }
    }
}
