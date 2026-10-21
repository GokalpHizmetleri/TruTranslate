package com.trutranslate.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {
    const val MODEL_URL = "https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF/resolve/main/Hy-MT2-1.8B-Q4_K_M.gguf"
    const val MODEL_SIZE = 1133080448L

    fun modelFile(ctx: Context) = File(ctx.filesDir, "hy-mt2-q4km.gguf")
    fun isReady(ctx: Context) = modelFile(ctx).let { it.exists() && it.length() == MODEL_SIZE }

    suspend fun download(ctx: Context, onProgress: (downloaded: Long, total: Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val out = modelFile(ctx)
        var existing = if (out.exists()) out.length() else 0L
        if (existing == MODEL_SIZE) return@withContext true
        try {
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
            conn.connectTimeout = 15000; conn.readTimeout = 30000
            conn.connect()
            val code = conn.responseCode
            if (code !in intArrayOf(200, 206)) { conn.disconnect(); return@withContext false }
            if (code == 200) { existing = 0; out.delete() }
            val total = if (code == 206) existing + conn.contentLengthLong else conn.contentLengthLong
            conn.inputStream.use { ins ->
                RandomAccessFile(out, "rw").use { raf ->
                    raf.seek(existing)
                    val buf = ByteArray(256 * 1024)
                    var downloaded = existing
                    while (true) {
                        val r = ins.read(buf); if (r < 0) break
                        raf.write(buf, 0, r); downloaded += r
                        onProgress(downloaded, total)
                    }
                }
            }
            conn.disconnect()
            out.length() == MODEL_SIZE
        } catch (e: Exception) { false }
    }
}
