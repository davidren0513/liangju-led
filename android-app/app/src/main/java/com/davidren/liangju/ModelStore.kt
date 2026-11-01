package com.davidren.liangju

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ModelFile(val file: String, val url: String, val bytes: Long, val sha256: String)
class ModelStore(context: Context) {
    private val preferences = context.getSharedPreferences("models", Context.MODE_PRIVATE)
    var aiGroup: String
        get() = preferences.getString("ai_group", "ai")!!.takeIf { it in listOf("ai", "ai_quality") } ?: "ai"
        set(value) { require(value in listOf("ai", "ai_quality")); preferences.edit().putString("ai_group", value).apply() }
    fun aiName() = if (aiGroup == "ai") "轻量 0.5B" else "增强 1.5B"
    fun aiFile() = file(specs(aiGroup).single().file)

    private val root = File(context.filesDir, "models").apply { mkdirs() }
    private val manifest = JSONObject(context.assets.open("models.json").bufferedReader().use { it.readText() })
    fun specs(group: String): List<ModelFile> = manifest.getJSONArray(group).let { a ->
        (0 until a.length()).map { i -> a.getJSONObject(i).let { ModelFile(it.getString("file"), it.getString("url"), it.getLong("bytes"), it.getString("sha256")) } }
    }
    fun file(name: String) = File(root, name)
    fun ready(group: String) = specs(group).all { s ->
        file(s.file).length() == s.bytes && file(s.file + ".verified").takeIf { it.exists() }?.readText() == s.sha256
    }
    fun total(group: String) = specs(group).sumOf { it.bytes }
    suspend fun install(group: String, progress: suspend (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val files = specs(group)
        val total = files.sumOf { it.bytes }
        val required = files.filterNot { file(it.file).length() == it.bytes }.sumOf { (it.bytes - file(it.file + ".part").length()).coerceAtLeast(0) }
        if (StatFs(root.path).availableBytes < required + 100_000_000L) throw IOException("存储空间不足，请至少留出 1 GB 可用空间")
        var finished = 0L
        for (s in files) {
            currentCoroutineContext().ensureActive()
            val target = file(s.file)
            val stamp = file(s.file + ".verified")
            if (target.length() == s.bytes && stamp.exists() && stamp.readText() == s.sha256) {
                finished += s.bytes; progress(finished, total); continue
            }
            val part = file(s.file + ".part")
            if (part.length() > s.bytes) part.delete()
            var offset = part.length()
            if (offset < s.bytes) {
                val conn = connect(s.url, offset)
                try {
                    val status = conn.responseCode
                    if (status == 200) offset = 0
                    else if (status != 206 || conn.getHeaderField("Content-Range")?.startsWith("bytes $offset-") != true) throw IOException("模型服务器未正确响应，请稍后重试（$status）")
                    part.outputStream(offset > 0).use { out ->
                        conn.inputStream.use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var lastUpdate = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                if (offset + n > s.bytes) throw IOException("模型大小不符合预期")
                                out.write(buffer, 0, n); offset += n
                                if (System.currentTimeMillis() - lastUpdate > 200) {
                                    progress(finished + offset, total); lastUpdate = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                } finally { conn.disconnect() }
            }
            if (part.length() != s.bytes) throw IOException("模型下载中断，可点击继续下载")
            progress(finished + s.bytes, total)
            val digest = MessageDigest.getInstance("SHA-256")
            part.inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != s.sha256) { part.delete(); throw IOException("模型校验失败，请重新下载") }
            currentCoroutineContext().ensureActive()
            if (target.exists() && !target.delete()) throw IOException("无法更新模型文件")
            if (!part.renameTo(target)) throw IOException("无法保存模型文件")
            stamp.writeText(s.sha256)
            finished += s.bytes; progress(finished, total)
        }
    }
    suspend fun importFile(name: String, open: () -> java.io.InputStream, progress: suspend (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val spec = listOf("speech", "ai", "ai_quality").flatMap { specs(it) }.firstOrNull { it.file == name }
            ?: throw IOException("不支持这个文件，请选择清单中的模型文件或 tokens.txt")
        if (StatFs(root.path).availableBytes < spec.bytes + 30_000_000L) throw IOException("存储空间不足，无法导入模型")
        val part = file(spec.file + ".importing")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L; var lastUpdate = 0L
            open().use { input -> part.outputStream().use { out ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    count += n
                    if (count > spec.bytes) throw IOException("模型文件大小不符，请使用项目清单中的版本")
                    out.write(buffer,0,n); digest.update(buffer,0,n)
                    if (System.currentTimeMillis() - lastUpdate > 200) { progress(count,spec.bytes);lastUpdate=System.currentTimeMillis() }
                }
            } }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (count != spec.bytes || hash != spec.sha256) throw IOException("模型文件校验失败，请使用项目清单中的版本")
            currentCoroutineContext().ensureActive()
            val target = file(spec.file)
            if (target.exists() && !target.delete()) throw IOException("无法更新模型")
            if (!part.renameTo(target)) throw IOException("无法保存模型")
            file(spec.file + ".verified").writeText(spec.sha256)
            progress(count,spec.bytes)
        } finally { if(part.exists()) part.delete() }
    }
    private fun File.outputStream(append: Boolean) = java.io.FileOutputStream(this, append)
    private suspend fun connect(address: String, offset: Long): HttpURLConnection {
        var url = URL(address)
        repeat(6) {
            currentCoroutineContext().ensureActive()
            require(url.protocol == "https") { "只允许 HTTPS 模型下载" }
            val c = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 15_000; instanceFollowRedirects = false
                setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            val status = try { c.responseCode } catch (e: Exception) { c.disconnect(); throw e }
            if (status !in listOf(301, 302, 303, 307, 308)) return c
            val location = c.getHeaderField("Location"); c.disconnect()
            if (location == null) throw IOException("下载地址无效")
            url = URL(url, location)
        }
        throw IOException("模型下载重定向次数过多")
    }
}
