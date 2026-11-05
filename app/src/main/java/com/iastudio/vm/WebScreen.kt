package com.iastudio.vm

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val HOME_URL = "https://huggingface.co/models?library=gguf&sort=downloads"

private fun isModelUrl(u: String): Boolean = u.substringBefore('?').lowercase().endsWith(".gguf")

/**
 * Navigateur intégré : on parcourt Hugging Face (ou n'importe quel site) et, dès qu'un lien de
 * téléchargement .gguf est touché, Studio propose de l'installer directement dans la machine.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(vm: AppViewModel, modifier: Modifier) {
    var address by remember { mutableStateOf(vm.webUrl.ifEmpty { HOME_URL }) }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    val webRef = remember { arrayOfNulls<WebView>(1) }

    BackHandler(enabled = canBack) { webRef[0]?.goBack() }
    DisposableEffect(Unit) {
        onDispose {
            webRef[0]?.destroy()
            webRef[0] = null
        }
    }

    fun go(text: String) {
        val t = text.trim()
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "https://huggingface.co/models?library=gguf&sort=downloads&search=" + Uri.encode(t)
        }
        webRef[0]?.loadUrl(url)
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton({ webRef[0]?.goBack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") }
            IconButton({ webRef[0]?.goForward() }) { Icon(Icons.Default.ArrowForward, contentDescription = "Avancer") }
            IconButton({ webRef[0]?.reload() }) { Icon(Icons.Default.Refresh, contentDescription = "Actualiser") }
            IconButton({ webRef[0]?.loadUrl(HOME_URL) }) { Icon(Icons.Default.Home, contentDescription = "Accueil") }
        }
        OutlinedTextField(
            address, { address = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Adresse ou recherche") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go(address) }),
        )
        if (progress in 1..99) LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth().padding(vertical = 2.dp))
        Text(
            "Touche un fichier .gguf (bouton « Download » d'un modèle) : Studio proposera de l'installer.",
            modifier = Modifier.padding(vertical = 4.dp),
        )
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val u = request?.url?.toString() ?: return false
                            if (isModelUrl(u)) {
                                pending = u to fileNameFrom(u, null)
                                return true
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            canBack = view?.canGoBack() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            canBack = view?.canGoBack() == true
                            if (url != null) {
                                address = url
                                vm.webUrl = url
                            }
                        }
                    }
                    setDownloadListener { url, _, contentDisposition, _, _ ->
                        pending = url to fileNameFrom(url, contentDisposition)
                    }
                    webRef[0] = this
                    loadUrl(vm.webUrl.ifEmpty { HOME_URL })
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }

    pending?.let { (u, n) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Installer ce modèle ?") },
            text = { Text("$n\n\nChoisis le type de modèle : IA de chat / code, ou IA d'images.") },
            confirmButton = {
                Row {
                    TextButton({ vm.installFromUrl(u, n, false); pending = null }) { Text("Chat / code") }
                    TextButton({ vm.installFromUrl(u, n, true); pending = null }) { Text("Images") }
                }
            },
            dismissButton = { TextButton({ pending = null }) { Text("Annuler") } },
        )
    }
}
