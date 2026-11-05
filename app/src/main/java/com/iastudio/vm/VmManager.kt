package com.iastudio.vm

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val THINK_REGEX = Regex("(?s)<think>.*?(</think>|$)")

/** Retire les blocs <think>…</think> (réflexion des modèles « raisonnement »). */
fun stripThink(s: String): String = s.replace(THINK_REGEX, "").trim()

/**
 * Une « machine virtuelle » = un conteneur isolé : son propre dossier de modèles, de sorties,
 * de projets et de conversations. Les moteurs (llama-server, sd) sont des exécutables natifs ARM64
 * fournis dans l'APK.
 */
class VmManager(private val ctx: Context) {
    private val base = File(ctx.filesDir, "vms").apply { mkdirs() }
    val nativeDir: String = ctx.applicationInfo.nativeLibraryDir
    private var chatProc: Process? = null
    private val port = 8080

    fun list(): List<String> =
        base.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun create(name: String) {
        File(base, "$name/models").mkdirs()
        File(base, "$name/out").mkdirs()
        File(base, "$name/rootfs").mkdirs()
    }

    fun delete(name: String) { stopChat(); File(base, name).deleteRecursively() }
    fun modelsDir(vm: String) = File(base, "$vm/models").apply { mkdirs() }
    fun outDir(vm: String) = File(base, "$vm/out").apply { mkdirs() }
    fun freeSpace(): Long = ctx.filesDir.usableSpace

    fun installed(vm: String): Set<String> =
        modelsDir(vm).list()?.filter { !it.endsWith(".part") }?.toSet() ?: emptySet()

    /** Images générées (les fichiers commençant par « _ » sont des fichiers de travail). */
    fun gallery(vm: String): List<File> =
        outDir(vm).listFiles { f -> f.extension == "png" && !f.name.startsWith("_") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun engineStatus(): String {
        fun has(n: String) = File(nativeDir, n).exists()
        return "Chat : ${if (has("libllama_server.so")) "OK" else "absent"} · " +
            "Images : ${if (has("libsd.so")) "OK" else "absent"}"
    }

    // ---------- Vérification des fichiers et des moteurs ----------

    /** Vérifie qu'un fichier de modèle est complet et lisible. */
    fun verifyModel(f: File): Pair<Boolean, String> {
        if (!f.exists()) return false to "fichier absent"
        if (f.length() < 1_000_000L) return false to "fichier trop petit (${f.length()} octets), téléchargement raté"
        if (f.name.endsWith(".gguf", true)) {
            val head = ByteArray(8)
            val n = f.inputStream().use { it.read(head) }
            if (n < 8 || String(head, 0, 4, Charsets.US_ASCII) != "GGUF") {
                return false to "en-tête GGUF invalide : fichier corrompu ou ce n'est pas un modèle GGUF"
            }
            val ver = (head[4].toInt() and 0xFF) or ((head[5].toInt() and 0xFF) shl 8)
            if (ver < 2 || ver > 3) return false to "version GGUF inattendue ($ver)"
            return true to "GGUF v$ver valide"
        }
        return true to "format .${f.extension} : non vérifiable, supposé correct"
    }

    /** Lance un moteur une seconde pour voir s'il démarre sur ce téléphone. */
    fun probe(exe: File, arg: String): String {
        if (!exe.exists()) return "✗ absent de l'APK"
        return try {
            val pb = ProcessBuilder(exe.path, arg).redirectErrorStream(true)
            pb.environment()["TMPDIR"] = ctx.cacheDir.path
            val p = pb.start()
            val out = StringBuilder()
            val t = Thread {
                try {
                    p.inputStream.bufferedReader().forEachLine { if (out.length < 300) out.append(it.take(100)).append(' ') }
                } catch (e: Exception) {}
            }
            t.start()
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                p.destroy()
                "✓ démarre"
            } else {
                t.join(500)
                val code = p.exitValue()
                if (code == 0 || code == 1) "✓ démarre ${out.toString().trim().take(70)}" else "✗ ${explainExit(code)}"
            }
        } catch (e: Exception) {
            "✗ impossible à lancer : ${e.message}"
        }
    }

    fun explainExit(code: Int): String = when (code) {
        137 -> "tué par Android : mémoire insuffisante (code 137)"
        132 -> "ton processeur ne gère pas une instruction utilisée par le moteur (code 132)"
        139 -> "le moteur a planté (code 139)"
        134 -> "le moteur s'est interrompu sur une erreur (code 134)"
        126, 127 -> "Android refuse de lancer le moteur (code $code)"
        else -> "le moteur s'est arrêté (code $code)"
    }

    private fun builder(cmd: List<String>): ProcessBuilder =
        ProcessBuilder(cmd).redirectErrorStream(true).apply {
            environment()["TMPDIR"] = ctx.cacheDir.path
        }

    // ---------- Chat (llama.cpp) ----------

    suspend fun startChat(model: File, threads: Int, ctxSize: Int) = withContext(Dispatchers.IO) {
        val old = chatProc
        stopChat()
        // Attend la fin de l'ancien moteur : sinon il peut encore occuper le port.
        old?.let { if (!it.waitFor(5, TimeUnit.SECONDS)) it.destroyForcibly().waitFor() }
        val exe = File(nativeDir, "libllama_server.so")
        if (!exe.exists()) throw RuntimeException("Moteur de chat absent de l'APK (voir le workflow GitHub)")
        chatProc = builder(
            listOf(
                exe.path, "-m", model.path, "--host", "127.0.0.1", "--port", "$port",
                "-c", "$ctxSize", "-t", "$threads", "--parallel", "1",
            ),
        ).start()
        val p = chatProc!!
        val tail = ConcurrentLinkedDeque<String>()
        Thread {
            try {
                p.inputStream.bufferedReader().forEachLine { l ->
                    tail.add(l.takeLast(160))
                    while (tail.size > 12) tail.pollFirst()
                }
            } catch (e: Exception) {}
        }.start()
        repeat(240) {
            if (!p.isAlive) {
                delay(300)
                val code = try { p.exitValue() } catch (e: Exception) { -1 }
                throw RuntimeException("Le moteur de chat a échoué : " + explainExit(code) + "\n" + tail.toList().takeLast(6).joinToString("\n"))
            }
            try {
                val c = URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection
                c.connectTimeout = 500
                c.readTimeout = 500
                if (c.responseCode == 200) return@withContext
            } catch (e: Exception) {}
            delay(500)
        }
        throw RuntimeException("Le moteur de chat ne répond pas")
    }

    fun chatRunning() = chatProc?.isAlive == true

    fun stopChat() {
        chatProc?.destroy()
        chatProc = null
    }

    /**
     * Discussion en direct (streaming). onText reçoit le texte complet à chaque nouveau morceau
     * (la réflexion éventuelle est placée dans un bloc <think>…</think>).
     */
    suspend fun chat(
        history: List<Pair<String, String>>, system: String, maxTokens: Int, temperature: Double,
        onText: ((String) -> Unit)? = null,
    ): String = withContext(Dispatchers.IO) {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        history.forEach { (r, c) ->
            msgs.put(JSONObject().put("role", r).put("content", if (r == "assistant") stripThink(c) else c))
        }
        val body = JSONObject().put("messages", msgs).put("temperature", temperature)
            .put("max_tokens", maxTokens).put("stream", true)
        val c = URL("http://127.0.0.1:$port/v1/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 5000
        c.readTimeout = 600000
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode !in 200..299) {
            val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
            throw RuntimeException("Erreur moteur : ${err.take(300)}")
        }
        val think = StringBuilder()
        val ans = StringBuilder()
        fun combined(): String = when {
            think.isEmpty() -> ans.toString()
            ans.isEmpty() -> "<think>$think"
            else -> "<think>$think</think>\n$ans"
        }
        var last = 0L
        c.inputStream.bufferedReader().use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                if (data.isEmpty()) continue
                val choices = try { JSONObject(data).getJSONArray("choices") } catch (e: Exception) { continue }
                if (choices.length() == 0) continue
                val d = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                if (!d.isNull("reasoning_content")) think.append(d.getString("reasoning_content"))
                if (!d.isNull("content")) ans.append(d.getString("content"))
                val now = System.currentTimeMillis()
                if (onText != null && now - last > 60) {
                    last = now
                    onText(combined())
                }
            }
        }
        val result = combined().trim()
        if (result.isBlank()) throw RuntimeException("Réponse vide : relance, ou essaie un autre modèle")
        onText?.invoke(result)
        result
    }

    // ---------- Conversations sauvegardées ----------

    fun saveChat(vm: String, key: String, msgs: List<Pair<String, String>>) {
        try {
            val arr = JSONArray()
            msgs.forEach { arr.put(JSONObject().put("r", it.first).put("c", it.second)) }
            File(base, "$vm/chat_$key.json").writeText(arr.toString())
        } catch (e: Exception) {}
    }

    fun loadChat(vm: String, key: String): List<Pair<String, String>> {
        val f = File(base, "$vm/chat_$key.json")
        if (!f.exists()) return emptyList()
        return try {
            val a = JSONArray(f.readText())
            (0 until a.length()).map { val o = a.getJSONObject(it); o.getString("r") to o.getString("c") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- Images (stable-diffusion.cpp) : création et modification ----------

    suspend fun generateImage(
        vm: String, model: ModelEntry, prompt: String, negative: String, size: Int, threads: Int,
        baseImage: File?, strength: Float, onLog: (String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val exe = File(nativeDir, "libsd.so")
        if (!exe.exists()) throw RuntimeException("Moteur d'images absent de l'APK (voir le workflow GitHub)")
        val out = File(outDir(vm), "img_${System.currentTimeMillis()}.png")
        val cmd = mutableListOf(
            exe.path, "-m", File(modelsDir(vm), model.file).path, "-p", prompt, "-o", out.path,
            "--steps", "${model.steps}", "--cfg-scale", "${model.cfg}",
            "-W", "$size", "-H", "$size", "-t", "$threads", "--seed", "-1",
        )
        if (negative.isNotBlank() && model.cfg > 1.0) { cmd += listOf("-n", negative) }
        if (baseImage != null) {
            // Modification d'image : l'image de départ est transformée selon la description.
            cmd += listOf("-i", baseImage.path, "--strength", "$strength")
        }
        fun runSd(args: List<String>): Int {
            val p = builder(args).start()
            p.inputStream.bufferedReader().forEachLine { onLog(it.takeLast(120)) }
            return p.waitFor()
        }
        var code = -1
        if (baseImage != null) {
            // Les anciennes versions de stable-diffusion.cpp exigent -M img2img, les récentes le refusent éventuellement.
            code = runSd(cmd + listOf("-M", "img2img"))
            if (code != 0 || !out.exists()) {
                onLog("Nouvel essai sans l'option de mode…")
                code = runSd(cmd)
            }
        } else {
            code = runSd(cmd)
        }
        if (code != 0 || !out.exists()) throw RuntimeException("Échec de la génération (${explainExit(code)}). Essaie une taille plus petite.")
        out
    }

    /** Copie l'image dans la galerie Photos du téléphone (Android 10+). */
    fun exportToPhotos(f: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, f.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Studio")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
        return true
    }

    // ---------- Projets de code (logiciels générés) ----------

    fun projectDir(vm: String, slug: String) = File(base, "$vm/projects/$slug")

    fun projects(vm: String): List<String> =
        File(base, "$vm/projects").listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun projectFiles(dir: File): List<String> =
        dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path }.sorted().toList()

    fun writeProjectFile(dir: File, path: String, content: String) {
        val clean = path.trim().trimStart('/', '\\').replace("\\", "/")
        if (clean.isEmpty() || clean.split("/").any { it == ".." }) throw RuntimeException("Chemin refusé : $path")
        val f = File(dir, clean)
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    fun readProjectFile(dir: File, path: String): String? =
        File(dir, path).takeIf { it.isFile }?.readText()

    fun zipProject(dir: File): File {
        val zip = File(ctx.cacheDir, "${dir.name}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { z ->
            projectFiles(dir).forEach { rel ->
                z.putNextEntry(ZipEntry(rel))
                File(dir, rel).inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return zip
    }

    /** Enregistre le .zip dans Téléchargements (Android 10+) et renvoie où le trouver. */
    fun exportZip(dir: File): String {
        val zip = zipProject(dir)
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, zip.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Studio")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                ?: throw RuntimeException("Export impossible")
            ctx.contentResolver.openOutputStream(uri)?.use { o -> zip.inputStream().use { it.copyTo(o) } }
            return "Téléchargements › Studio › ${zip.name}"
        }
        val d = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val dest = File(d, zip.name)
        zip.copyTo(dest, true)
        return dest.path
    }
}
