package com.iastudio.vm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun Thumb(f: File, thumbSize: Dp, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, f) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })
        }
    }
    Box(
        Modifier.size(thumbSize).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
    ) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
fun ImageScreen(vm: AppViewModel, goModels: () -> Unit) {
    val models = vm.allModels.filter { it.type == "image" && it.file in vm.installed }
    var sel by remember { mutableStateOf<ModelEntry?>(null) }
    val model = sel?.takeIf { it in models } ?: models.firstOrNull()
    var prompt by remember { mutableStateOf("") }
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.setBase(uri) }

    if (model == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TitleBar("Images")
            Text("Installe d'abord une IA d'images (SD-Turbo est la plus rapide).", color = grey)
            Button({ goModels() }) { Text("Choisir une IA") }
        }
        return
    }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitleBar(if (vm.baseThumb == null) "Créer une image" else "Modifier une image")
        ModelPicker(models, model) { sel = it }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ picker.launch("image/*") }) {
                Text(if (vm.baseThumb == null) "Choisir une photo à modifier" else "Changer de photo")
            }
            if (vm.baseThumb != null) TextButton({ vm.clearBase() }) { Text("Retirer") }
        }
        vm.baseThumb?.let { b ->
            Image(b.asImageBitmap(), null, Modifier.height(140.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
            Text("Force de la modification : ${(vm.strength * 100).toInt()} %  (bas = proche de l'original)")
            Slider(vm.strength, { vm.strength = it }, valueRange = 0.2f..0.95f)
        }

        OutlinedTextField(
            prompt, { prompt = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            label = { Text(if (vm.baseThumb == null) "Décris l'image (anglais conseillé)" else "Décris le résultat voulu") },
        )
        OutlinedTextField(
            vm.negative, { vm.negative = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            label = { Text("À éviter (anglais)") }, singleLine = true,
        )
        Text("Taille : ${vm.imageSize}×${vm.imageSize}  (256 = rapide · 512 = qualité)")
        Slider(vm.imageSize / 64f, { vm.imageSize = (it.toInt() * 64).coerceIn(256, 768) }, valueRange = 4f..12f, steps = 7)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.generate(prompt, model) }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                Text(if (vm.baseThumb == null) "Générer" else "Transformer la photo")
            }
            if (vm.busy) OutlinedButton({ vm.stopWork() }) { Text("Arrêter") }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        // Journal en direct du moteur (étapes de génération)
        Text(vm.imageLog, style = MaterialTheme.typography.bodyMedium, color = grey)

        vm.image?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) }
        vm.imageFile?.let { f ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({ vm.saveToPhotos(f) }) { Text("Enregistrer") }
                TextButton({ vm.setBaseFromFile(f) }) { Text("Modifier") }
                TextButton({ vm.deleteImage(f) }) { Text("Supprimer") }
            }
        }
        if (vm.gallery.isNotEmpty()) {
            Text("Galerie", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.gallery.toList()) { f -> Thumb(f, 92.dp) { vm.showImage(f) } }
            }
        }
    }
}
