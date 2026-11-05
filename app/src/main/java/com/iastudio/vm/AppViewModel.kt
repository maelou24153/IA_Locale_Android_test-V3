package com.iastudio.vm

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

enum class ThemeMode(val label: String) {
    SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre"),
}

enum class ChatMode(val label: String, val system: String, val hello: String) {
    ASSISTANT(
        "Assistant",
        "Tu es un assistant utile, chaleureux et honnête. Réponds en français, de façon claire et concise. Si tu ne sais pas, dis-le.",
        "Comment puis-je t'aider ?",
    ),
    CODE(
        "Code",
        "Tu es un expert en programmation. Réponds en français. Donne du code complet, correct et commenté " +
            "dans des blocs ```langage, puis explique brièvement les choix. Si l'utilisateur colle du code, " +
            "corrige-le ou améliore-le en montrant seulement ce qui change quand c'est possible. " +
            "Signale les erreurs et les cas limites.",
        "Que veux-tu construire ou corriger ?",
    ),
    PROMPT_IMAGE(
        "Prompt image",
        "Tu transformes la demande de l'utilisateur en un prompt anglais détaillé pour Stable Diffusion : " +
            "sujet, décor, style, éclairage, qualité, séparés par des virgules. Réponds uniquement avec le prompt.",
        "Décris l'image que tu imagines.",
    ),
}

class ProjFile(val path: String, val role: String, status: String = "en attente") {
    var status by mutableStateOf(status)
}

/** Un fichier de modèle présent sur le téléphone, avec le résultat de sa vérification. */
class InstalledModel(val name: String, val size: Long, val image: Boolean) {
    var ok by mutableStateOf<Boolean?>(null)
    var check by mutableStateOf("")
}

private const val PLAN_SYSTEM =
    "Tu es un architecte logiciel senior. À partir de la demande, liste les fichiers nécessaires à un projet complet " +
        "et fonctionnel : point d'entrée, code, configuration, et un README avec les instructions pour lancer le logiciel. " +
        "Réponds UNIQUEMENT par une liste, un fichier par ligne, au format : chemin/fichier.ext | rôle en une phrase. " +
        "Maximum 12 fichiers. Choisis la technologie la plus simple qui répond à la demande " +
        "(par défaut : une page web HTML/CSS/JS qui fonctionne en ouvrant index.html, ou un script Python)."

private const val FILE_SYSTEM =
    "Tu es un développeur expert. Tu écris un fichier complet, correct et prêt à l'emploi, cohérent avec les autres " +
        "fichiers du projet (mêmes noms de fonctions, d'identifiants, d'imports et de fichiers). Aucun texte avant ou " +
        "après : uniquement le contenu du fichier dans un seul bloc de code. Jamais de « ... » ni de TODO : tout doit être écrit."

private const val REVIEW_SYSTEM =
    "Tu es un relecteur de code strict. Corrige les erreurs de syntaxe, les imports manquants, les noms incohérents " +
        "et les bugs évidents. Renvoie le fichier COMPLET corrigé dans un seul bloc de code, sans commentaire autour."

private const val SELECT_SYSTEM =
    "Tu es un lead développeur. Selon la demande de modification, liste les chemins des fichiers à modifier ou à créer, " +
        "un par ligne, sans aucun autre texte."

private const val MODIFY_SYSTEM =
    "Tu es un développeur expert. Applique la demande au fichier et renvoie le fichier COMPLET modifié dans un seul " +
        "bloc de code, sans autre texte."

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val vmm = VmManager(app)
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    val catalog = Catalog.load(app)

    val ramGb: Double = run {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.totalMem / 1e9
    }

    fun ramAdvice(): String = when {
        ramGb < 4.5 -> "modèles de 0,5 à 1,5 milliard de paramètres"
        ramGb < 7 -> "modèles jusqu'à 3-4 milliards de paramètres"
        ramGb < 10 -> "modèles jusqu'à 4 milliards, 7B possible mais lent"
        else -> "modèles jusqu'à 7-8 milliards de paramètres"
    }

    // ---------- Réglages (sauvegardés) ----------
    var themeMode by mutableStateOf(
        ThemeMode.values().firstOrNull { it.name == prefs.getString("theme", "SYSTEM") } ?: ThemeMode.SYSTEM,
    )
    var temperature by mutableStateOf(prefs.getFloat("temp", 0.6f))
    var ctxSize by mutableStateOf(prefs.getInt("ctx", if (ramGb < 6) 2048 else if (ramGb < 10) 4096 else 8192))
    var threads by mutableStateOf(
        prefs.getInt("threads", (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 8)),
    )
    var maxTokens by mutableStateOf(prefs.getInt("maxtok", 1024))
    var customPrompt by mutableStateOf(prefs.getString("custom", "") ?: "")
    var hfToken by mutableStateOf(prefs.getString("hf_token", "") ?: "")
    var autoReview by mutableStateOf(prefs.getBoolean("review", false))

    fun updateTheme(m: ThemeMode) { themeMode = m; prefs.edit().putString("theme", m.name).apply() }
    fun updateTemperature(v: Float) { temperature = v; prefs.edit().putFloat("temp", v).apply() }
    fun updateCtx(v: Int) { ctxSize = v; prefs.edit().putInt("ctx", v).apply() }
    fun updateThreads(v: Int) { threads = v; prefs.edit().putInt("threads", v).apply() }
    fun updateMaxTokens(v: Int) { maxTokens = v; prefs.edit().putInt("maxtok", v).apply() }
    fun updateCustomPrompt(v: String) { customPrompt = v; prefs.edit().putString("custom", v).apply() }
    fun updateToken(v: String) { hfToken = v; prefs.edit().putString("hf_token", v).apply() }
    fun updateReview(v: Boolean) { autoReview = v; prefs.edit().putBoolean("review", v).apply() }

    // ---------- Machines et modèles ----------
    val machines = mutableStateListOf<String>()
    var current by mutableStateOf<String?>(null)
    var installed by mutableStateOf<Set<String>>(emptySet())
    val models = mutableStateListOf<InstalledModel>()
    val partials = mutableStateListOf<Pair<String, Long>>()
    val progress = mutableStateMapOf<String, Int>()
    val dlNames = mutableStateMapOf<String, String>()
    var status by mutableStateOf("")
    var engines by mutableStateOf(vmm.engineStatus())
    val linkStatus = mutableStateMapOf<String, String>()
    var checkingLinks by mutableStateOf(false)
    private val stopSet = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Catalogue + modèles installés par lien, par Hugging Face ou par le navigateur. */
    val allModels: List<ModelEntry>
        get() = catalog + installed.filter { f -> catalog.none { it.file == f } }.map { f ->
            if (f.startsWith("img__")) {
                val fast = f.contains("turbo", true) || f.contains("lcm", true) || f.contains("sdxs", true)
                ModelEntry("custom-$f", "image", f.removePrefix("img__"), f, "", if (fast) 4 else 20, if (fast) 1.0 else 7.0)
            } else {
                ModelEntry("custom-$f", "chat", f, f, "")
            }
        }

    // ---------- Chat ----------
    val messages = mutableStateListOf<Pair<String, String>>()
    var mode by mutableStateOf(ChatMode.ASSISTANT)
    var chatModel by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    private var chatCtx = 0
    private var job: Job? = null
    private var stopped = false

    // ---------- Hugging Face ----------
    var hfType by mutableStateOf("chat")
    var hfQuery by mutableStateOf("")
    val hfRepos = mutableStateListOf<HfRepo>()
    var hfSelected by mutableStateOf<String?>(null)
    val hfFiles = mutableStateListOf<HfFile>()
    var hfLoading by mutableStateOf(false)
    var hfError by mutableStateOf<String?>(null)
    var webUrl by mutableStateOf("")

    // ---------- Projets de code ----------
    val projects = mutableStateListOf<String>()
    var projectName by mutableStateOf("")
    var projLog by mutableStateOf("")
    val projFiles = mutableStateListOf<ProjFile>()
    var previewPath by mutableStateOf<String?>(null)
    var previewText by mutableStateOf("")
    var liveFile by mutableStateOf("")
    var liveText by mutableStateOf("")

    // ---------- Images ----------
    var imageLog by mutableStateOf("")
    var image by mutableStateOf<Bitmap?>(null)
    var imageFile by mutableStateOf<File?>(null)
    var imageSize by mutableStateOf(512)
    var negative by mutableStateOf("blurry, low quality, deformed, watermark")
    var strength by mutableStateOf(0.6f)
    var baseThumb by mutableStateOf<Bitmap?>(null)
    val gallery = mutableStateListOf<File>()

    // ---------- Diagnostic ----------
    val report = mutableStateListOf<String>()
    var diagBusy by mutableStateOf(false)

    init {
        refresh()
        if (machines.isEmpty()) createMachine("principale")
    }

    private fun srcFile(vm: String) = File(vmm.outDir(vm), "_src.png")

    private fun refresh() {
        machines.clear()
        machines.addAll(vmm.list())
        if (current == null || current !in machines) current = machines.firstOrNull()
        val vm = current
        gallery.clear()
        projects.clear()
        projFiles.clear()
        previewPath = null
        previewText = ""
        baseThumb = null
        if (vm != null) {
            gallery.addAll(vmm.gallery(vm))
            projects.addAll(vmm.projects(vm))
        }
        reloadModels()
        loadChatHistory()
    }

    fun reloadModels() {
        val vm = current
        if (vm == null) {
            installed = emptySet()
            models.clear()
            partials.clear()
            return
        }
        installed = vmm.installed(vm)
        val files = vmm.modelsDir(vm).listFiles()?.filter { it.isFile } ?: emptyList()
        val old = models.associateBy { it.name }
        models.clear()
        files.filter { !it.name.endsWith(".part") }.sortedBy { it.name }.forEach { f ->
            val prev = old[f.name]
            if (prev != null && prev.size == f.length()) models.add(prev)
            else models.add(InstalledModel(f.name, f.length(), isImageName(f.name)))
        }
        partials.clear()
        files.filter { it.name.endsWith(".part") }.forEach { partials.add(it.name to it.length()) }
    }

    private fun isImageName(name: String) =
        name.startsWith("img__") || catalog.any { it.file == name && it.type == "image" }

    fun diskFree(): Long = vmm.freeSpace()

    fun createMachine(name: String) {
        val clean = name.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
        if (clean.isEmpty()) return
        vmm.create(clean)
        current = clean
        refresh()
    }

    fun select(name: String) {
        saveChat()
        vmm.stopChat()
        chatModel = null
        current = name
        refresh()
    }

    fun deleteMachine(name: String) {
        vmm.delete(name)
        chatModel = null
        current = null
        refresh()
        if (machines.isEmpty()) createMachine("principale")
    }

    // ---------- Installation, suppression et vérification des modèles ----------

    fun startDownload(key: String, display: String, url: String, fileName: String) {
        val vm = current ?: return
        if (progress.containsKey(key)) return
        stopSet.remove(key)
        dlNames[key] = display
        progress[key] = 0
        status = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dest = File(vmm.modelsDir(vm), fileName)
                Downloader.download(url, dest, hfToken, { key in stopSet }) { progress[key] = it }
                val (ok, msg) = vmm.verifyModel(dest)
                status = if (ok) "$display installé ✓" else "⚠ $display téléchargé mais : $msg"
            } catch (e: DownloadCancelled) {
                status = "Téléchargement annulé. Relance l'installation pour reprendre là où il s'est arrêté."
            } catch (e: Exception) {
                status = "Erreur : ${e.message}"
            } finally {
                progress.remove(key)
                dlNames.remove(key)
                withContext(Dispatchers.Main) { reloadModels() }
            }
        }
    }

    fun cancelDownload(key: String) { stopSet.add(key) }

    fun install(m: ModelEntry) = startDownload(m.id, m.name, m.url, m.file)

    fun hfInstall(id: String, file: String) {
        val flat = file.substringAfterLast('/')
        val stored = if (hfType == "image") "img__$flat" else flat
        startDownload("hf-$id/$file", flat, HfBrowser.url(id, file), stored)
    }

    fun installFromUrl(url: String, name: String, image: Boolean) {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "modele.gguf" }
        val stored = if (image) "img__$safe" else safe
        startDownload("web-$stored", safe, url, stored)
    }

    private fun stopEngineIfRunning() {
        if (vmm.chatRunning() || chatModel != null) {
            vmm.stopChat()
            chatModel = null
        }
    }

    fun deleteModel(name: String) {
        val vm = current ?: return
        stopEngineIfRunning()
        File(vmm.modelsDir(vm), name).delete()
        reloadModels()
        status = "${name.removePrefix("img__")} supprimé"
    }

    fun deletePartial(name: String) {
        val vm = current ?: return
        File(vmm.modelsDir(vm), name).delete()
        reloadModels()
    }

    fun verifyOne(m: InstalledModel) {
        val vm = current ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = vmm.verifyModel(File(vmm.modelsDir(vm), m.name))
            m.ok = ok
            m.check = msg
        }
    }

    fun verifyAll() { models.toList().forEach { verifyOne(it) } }

    fun checkLinks() {
        if (checkingLinks) return
        checkingLinks = true
        linkStatus.clear()
        viewModelScope.launch(Dispatchers.IO) {
            catalog.forEach { m ->
                linkStatus[m.id] = try {
                    val c = URL(m.url).openConnection() as HttpURLConnection
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.setRequestProperty("Range", "bytes=0-0")
                    c.setRequestProperty("User-Agent", "Studio-Android")
                    val code = c.responseCode
                    c.disconnect()
                    if (code in 200..299) "ok" else "HTTP $code"
                } catch (e: Exception) {
                    "réseau"
                }
            }
            checkingLinks = false
        }
    }

    // ---------- Navigateur Hugging Face ----------

    fun hfSearch(query: String = hfQuery) {
        hfQuery = query
        hfLoading = true
        hfError = null
        hfSelected = null
        hfFiles.clear()
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { HfBrowser.search(query, hfToken) }
                hfRepos.clear()
                hfRepos.addAll(r)
                if (r.isEmpty()) hfError = "Aucun résultat pour « $query »"
            } catch (e: Exception) {
                hfError = "La recherche a échoué : ${e.message}"
            } finally {
                hfLoading = false
            }
        }
    }

    fun hfOpen(id: String) {
        if (hfSelected == id) { hfSelected = null; hfFiles.clear(); return }
        hfSelected = id
        hfFiles.clear()
        hfLoading = true
        hfError = null
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { HfBrowser.files(id, hfToken) }
                hfFiles.addAll(f)
                if (f.isEmpty()) hfError = "Aucun fichier .gguf simple dans ce dépôt"
            } catch (e: Exception) {
                hfError = "Impossible de lire ce dépôt : ${e.message}"
            } finally {
                hfLoading = false
            }
        }
    }

    // ---------- Moteur de chat ----------

    private suspend fun ensureChat(vm: String, model: ModelEntry, minCtx: Int = 0) {
        val want = maxOf(ctxSize, minCtx)
        if (vmm.chatRunning() && chatModel == model.id && chatCtx == want) return
        status = "Démarrage du moteur… (30 s à 2 min la première fois)"
        vmm.startChat(File(vmm.modelsDir(vm), model.file), threads, want)
        chatModel = model.id
        chatCtx = want
        status = ""
    }

    fun stopWork() {
        stopped = true
        job?.cancel()
        vmm.stopChat()
        chatModel = null
        busy = false
        status = "Arrêté"
    }

    private fun systemPrompt(): String {
        val extra = customPrompt.trim()
        return if (extra.isEmpty() || mode == ChatMode.PROMPT_IMAGE) mode.system
        else mode.system + "\n\nInstructions de l'utilisateur : " + extra
    }

    private fun tokensFor(): Int = if (mode == ChatMode.PROMPT_IMAGE) 200 else maxTokens

    fun send(text: String, model: ModelEntry) {
        val vm = current ?: return
        if (text.isBlank() || busy) return
        messages.add("user" to text)
        runChat(vm, model)
    }

    private fun runChat(vm: String, model: ModelEntry) {
        busy = true
        stopped = false
        job = viewModelScope.launch {
            messages.add("assistant" to "")
            val idx = messages.size - 1
            try {
                ensureChat(vm, model)
                val history = messages.toList().dropLast(1)
                val r = vmm.chat(history, systemPrompt(), tokensFor(), temperature.toDouble()) { t ->
                    if (idx < messages.size) messages[idx] = "assistant" to t
                }
                if (idx < messages.size) messages[idx] = "assistant" to r
            } catch (e: Exception) {
                if (idx < messages.size) {
                    if (stopped) {
                        if (messages[idx].second.isBlank()) messages.removeAt(idx)
                    } else {
                        messages[idx] = "assistant" to "⚠ ${e.message}"
                    }
                }
                status = ""
            } finally {
                busy = false
                saveChat()
            }
        }
    }

    fun regenerate(model: ModelEntry) {
        val vm = current ?: return
        if (busy || messages.isEmpty()) return
        if (messages.last().first == "assistant") messages.removeAt(messages.size - 1)
        if (messages.isNotEmpty() && messages.last().first == "user") runChat(vm, model)
    }

    private fun saveChat() {
        val vm = current ?: return
        vmm.saveChat(vm, mode.name, messages.toList())
    }

    private fun loadChatHistory() {
        messages.clear()
        val vm = current ?: return
        messages.addAll(vmm.loadChat(vm, mode.name))
    }

    fun clearChat() {
        messages.clear()
        saveChat()
    }

    fun changeMode(m: ChatMode) {
        if (busy) return
        saveChat()
        mode = m
        loadChatHistory()
    }

    // ---------- Génération de logiciels complets (affichée en direct) ----------

    private fun slug(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').ifEmpty { "projet" }

    private fun extractCode(raw: String): String {
        val s = raw.indexOf("```")
        if (s < 0) return raw.trim()
        val nl = raw.indexOf('\n', s)
        if (nl < 0) return raw.trim()
        val e = raw.lastIndexOf("```")
        return raw.substring(nl + 1, if (e > nl) e else raw.length).trimEnd()
    }

    private fun cleanPath(line: String): String =
        line.trim().trimStart('-', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim('`', ' ', '*')

    private fun parsePlan(txt: String): List<ProjFile> {
        val out = LinkedHashMap<String, ProjFile>()
        txt.lines().forEach { l ->
            val parts = l.split("|", limit = 2)
            val path = cleanPath(parts[0])
            val ok = path.isNotEmpty() && path.length < 80 && !path.contains(' ') && path.contains('.')
            if (ok && path !in out && out.size < 12) out[path] = ProjFile(path, parts.getOrElse(1) { "" }.trim())
        }
        return out.values.toList()
    }

    fun buildProject(name: String, request: String, model: ModelEntry) {
        val vm = current ?: return
        if (request.isBlank() || busy) return
        busy = true
        stopped = false
        projectName = slug(name.ifBlank { request.take(24) })
        job = viewModelScope.launch {
            try {
                ensureChat(vm, model, 4096)
                val dir = vmm.projectDir(vm, projectName)
                dir.deleteRecursively()
                dir.mkdirs()
                projFiles.clear()
                previewPath = null
                projLog = "1/3 · L'IA prépare le plan du projet…"
                liveFile = "Plan du projet"
                liveText = ""
                val planRaw = vmm.chat(listOf("user" to "Demande : $request"), PLAN_SYSTEM, 1500, 0.3) { liveText = it }
                val plan = parsePlan(stripThink(planRaw))
                if (plan.isEmpty()) throw RuntimeException("Plan illisible. Reformule la demande ou choisis un modèle plus grand.")
                projFiles.addAll(plan)
                val summary = plan.joinToString("\n") { "- ${it.path} : ${it.role}" }
                plan.forEachIndexed { i, pf ->
                    projLog = "2/3 · Écriture de ${pf.path} (${i + 1}/${plan.size})"
                    pf.status = "écriture…"
                    liveFile = pf.path
                    liveText = ""
                    val user = "Projet : $request\n\nFichiers du projet :\n$summary\n\n" +
                        "Écris le contenu COMPLET du fichier ${pf.path} (${pf.role}). Un seul bloc de code, rien d'autre."
                    val raw = vmm.chat(listOf("user" to user), FILE_SYSTEM, 4000, 0.3) { liveText = it }
                    var code = extractCode(stripThink(raw))
                    if (autoReview && code.isNotBlank()) {
                        pf.status = "relecture…"
                        projLog = "3/3 · Relecture de ${pf.path}"
                        liveFile = "Relecture de ${pf.path}"
                        liveText = ""
                        val rev = vmm.chat(
                            listOf("user" to "Fichier ${pf.path} du projet « $request » :\n```\n$code\n```\nCorrige-le."),
                            REVIEW_SYSTEM, 4000, 0.2,
                        ) { liveText = it }
                        val r = extractCode(stripThink(rev))
                        if (r.isNotBlank()) code = r
                    }
                    vmm.writeProjectFile(dir, pf.path, code)
                    pf.status = "✓"
                }
                projLog = "Terminé : ${plan.size} fichiers. Appuie sur « Télécharger le .zip »."
                liveFile = ""
                projects.clear()
                projects.addAll(vmm.projects(vm))
            } catch (e: Exception) {
                status = ""
                if (!stopped) projLog = "⚠ ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun modifyProject(request: String, model: ModelEntry) {
        val vm = current ?: return
        if (request.isBlank() || busy || projectName.isEmpty()) return
        busy = true
        stopped = false
        job = viewModelScope.launch {
            try {
                ensureChat(vm, model, 4096)
                val dir = vmm.projectDir(vm, projectName)
                val existing = vmm.projectFiles(dir)
                projLog = "L'IA cherche les fichiers à modifier…"
                liveFile = "Choix des fichiers"
                liveText = ""
                val selRaw = vmm.chat(
                    listOf("user" to "Fichiers du projet :\n${existing.joinToString("\n")}\n\nDemande : $request"),
                    SELECT_SYSTEM, 800, 0.2,
                ) { liveText = it }
                val sel = stripThink(selRaw).lines().map { cleanPath(it.substringBefore('|')) }
                    .filter { it.isNotEmpty() && it.contains('.') && !it.contains(' ') }.distinct().take(8)
                if (sel.isEmpty()) throw RuntimeException("L'IA n'a désigné aucun fichier. Reformule la demande.")
                sel.forEachIndexed { i, path ->
                    projLog = "Modification de $path (${i + 1}/${sel.size})"
                    val pf = projFiles.firstOrNull { it.path == path } ?: ProjFile(path, "").also { projFiles.add(it) }
                    pf.status = "modification…"
                    liveFile = path
                    liveText = ""
                    val old = vmm.readProjectFile(dir, path)?.take(9000) ?: "(nouveau fichier)"
                    val user = "Fichiers du projet :\n${existing.joinToString("\n")}\n\nDemande : $request\n\n" +
                        "Contenu actuel de $path :\n```\n$old\n```\nRenvoie le fichier $path complet et modifié."
                    val raw = vmm.chat(listOf("user" to user), MODIFY_SYSTEM, 4000, 0.3) { liveText = it }
                    val code = extractCode(stripThink(raw))
                    if (code.isNotBlank()) vmm.writeProjectFile(dir, path, code)
                    pf.status = "✓ modifié"
                }
                projLog = "Modifications terminées. Retélécharge le .zip."
                liveFile = ""
            } catch (e: Exception) {
                status = ""
                if (!stopped) projLog = "⚠ ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun openProject(slug: String) {
        val vm = current ?: return
        projectName = slug
        projFiles.clear()
        vmm.projectFiles(vmm.projectDir(vm, slug)).forEach { projFiles.add(ProjFile(it, "", "✓")) }
        previewPath = null
        previewText = ""
        projLog = "Projet « $slug » ouvert"
    }

    fun preview(path: String) {
        val vm = current ?: return
        previewPath = path
        previewText = vmm.readProjectFile(vmm.projectDir(vm, projectName), path) ?: "(fichier absent)"
    }

    fun deleteProject(slug: String) {
        val vm = current ?: return
        vmm.projectDir(vm, slug).deleteRecursively()
        projects.remove(slug)
        if (projectName == slug) {
            projectName = ""
            projFiles.clear()
            previewPath = null
        }
    }

    fun exportProject() {
        val vm = current ?: return
        if (projectName.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            projLog = try {
                "Archive enregistrée : " + vmm.exportZip(vmm.projectDir(vm, projectName))
            } catch (e: Exception) {
                "⚠ ${e.message}"
            }
        }
    }

    // ---------- Images : création et modification ----------

    private fun decodeSampled(open: () -> InputStream?, max: Int): Bitmap? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, b) }
        var s = 1
        while (b.outWidth / s > max * 2 || b.outHeight / s > max * 2) s *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = s }
        return open()?.use { BitmapFactory.decodeStream(it, null, o) }
    }

    private fun saveSource(bmp: Bitmap) {
        val vm = current ?: return
        FileOutputStream(srcFile(vm)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        baseThumb = bmp
    }

    fun setBase(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = decodeSampled({ getApplication<Application>().contentResolver.openInputStream(uri) }, 1024)
            if (bmp == null) { imageLog = "⚠ Impossible de lire cette image"; return@launch }
            saveSource(bmp)
        }
    }

    fun setBaseFromFile(f: File) {
        viewModelScope.launch(Dispatchers.IO) {
            decodeSampled({ f.inputStream() }, 1024)?.let { saveSource(it) }
        }
    }

    fun clearBase() {
        current?.let { srcFile(it).delete() }
        baseThumb = null
    }

    private fun prepareBase(vm: String, size: Int): File? {
        if (baseThumb == null) return null
        val bmp = BitmapFactory.decodeFile(srcFile(vm).path) ?: return null
        val side = minOf(bmp.width, bmp.height)
        val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(sq, size, size, true)
        val dest = File(vmm.outDir(vm), "_base.png")
        FileOutputStream(dest).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return dest
    }

    fun generate(prompt: String, model: ModelEntry) {
        val vm = current ?: return
        if (prompt.isBlank() || busy) return
        busy = true
        stopped = false
        imageLog = "Génération en cours…"
        job = viewModelScope.launch {
            try {
                vmm.stopChat() // libère la RAM pour le moteur d'images
                chatModel = null
                val base = withContext(Dispatchers.IO) { prepareBase(vm, imageSize) }
                val f = vmm.generateImage(vm, model, prompt, negative, imageSize, threads, base, strength) { imageLog = it }
                showImage(f)
                gallery.add(0, f)
                imageLog = "Terminé : ${f.name}"
            } catch (e: Exception) {
                if (!stopped) imageLog = "⚠ ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun showImage(f: File) {
        viewModelScope.launch {
            image = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.path) }
            imageFile = f
        }
    }

    fun saveToPhotos(f: File) {
        viewModelScope.launch(Dispatchers.IO) {
            imageLog = if (vmm.exportToPhotos(f)) "Enregistrée dans Photos › Studio"
            else "Enregistrement impossible (Android 10 ou plus requis)"
        }
    }

    fun deleteImage(f: File) {
        f.delete()
        gallery.remove(f)
        if (imageFile == f) { image = null; imageFile = null }
    }

    // ---------- Diagnostic complet ----------

    fun runDiagnostics() {
        if (diagBusy) return
        diagBusy = true
        report.clear()
        val vm = current
        viewModelScope.launch(Dispatchers.IO) {
            try {
                report.add("Studio $APP_VERSION")
                report.add("Téléphone : ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                val abis = Build.SUPPORTED_ABIS.joinToString()
                report.add((if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) "✓" else "✗") + " Processeur : $abis" +
                    (if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) "" else " (les moteurs ont besoin de arm64-v8a)"))
                val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                report.add("✓ Mémoire : ${"%.1f".format(mi.totalMem / 1e9)} Go au total, ${"%.1f".format(mi.availMem / 1e9)} Go libres maintenant")
                val free = vmm.freeSpace()
                report.add((if (free > 3_000_000_000L) "✓" else "⚠") + " Espace libre : ${"%.1f".format(free / 1e9)} Go")
                report.add("Moteur de chat : " + vmm.probe(File(vmm.nativeDir, "libllama_server.so"), "--version"))
                report.add("Moteur d'images : " + vmm.probe(File(vmm.nativeDir, "libsd.so"), "--help"))
                if (vm != null) {
                    report.add("Machine : $vm")
                    val files = vmm.modelsDir(vm).listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: emptyList()
                    if (files.isEmpty()) report.add("⚠ Aucun modèle installé")
                    files.sortedBy { it.name }.forEach { f ->
                        val (ok, msg) = vmm.verifyModel(f)
                        val gb = f.length() / 1e9
                        val heavy = if (gb > ramGb * 0.6) " · ⚠ trop lourd pour ta RAM" else ""
                        report.add((if (ok) "✓ " else "✗ ") + f.name.removePrefix("img__") + " (${"%.2f".format(gb)} Go) : $msg$heavy")
                    }
                    val parts = vmm.modelsDir(vm).listFiles()?.filter { it.name.endsWith(".part") } ?: emptyList()
                    if (parts.isNotEmpty()) {
                        report.add("⚠ ${parts.size} téléchargement(s) interrompu(s), ${"%.1f".format(parts.sumOf { it.length() } / 1e9)} Go à reprendre ou supprimer")
                    }
                }
                report.add(try {
                    val c = URL("https://huggingface.co/api/models?limit=1").openConnection() as HttpURLConnection
                    c.connectTimeout = 6000
                    c.readTimeout = 6000
                    c.setRequestProperty("User-Agent", "Studio-Android")
                    val code = c.responseCode
                    c.disconnect()
                    if (code in 200..299) "✓ Internet et Hugging Face : accessibles" else "⚠ Hugging Face a répondu HTTP $code"
                } catch (e: Exception) {
                    "✗ Hugging Face inaccessible (${e.message}) : vérifie ta connexion"
                })
                report.add("— Analyse terminée —")
            } finally {
                diagBusy = false
            }
        }
    }

    override fun onCleared() { vmm.stopChat() }
}
