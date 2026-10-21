package com.trutranslate.app

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object ModelManager {
    const val MODEL_SIZE = 1133080448L
    private const val ASSET_NAME = "model.gguf"

    fun modelFile(ctx: Context): File = File(ctx.filesDir, "model.gguf")

    fun isReady(ctx: Context): Boolean {
        val f = modelFile(ctx)
        return f.exists() && f.length() == MODEL_SIZE
    }

    // Streams the bundled model out of the APK assets into internal storage.
    // onProgress: 0..100, or -1 for "checking space".
    fun extract(ctx: Context, onProgress: (Int) -> Unit) {
        val dst = modelFile(ctx)
        if (isReady(ctx)) { onProgress(100); return }
        val free = ctx.filesDir.usableSpace
        if (free < MODEL_SIZE + 200L * 1024 * 1024) {
            throw IllegalStateException("Yetersiz depolama: en az 1,4 GB boş alan gerekli.")
        }
        val tmp = File(ctx.filesDir, "model.gguf.tmp")
        ctx.assets.open(ASSET_NAME).use { inp ->
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(1024 * 1024)
                var total = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    onProgress(((total * 100) / MODEL_SIZE).toInt())
                }
            }
        }
        if (tmp.length() != MODEL_SIZE) { tmp.delete(); throw IllegalStateException("Model kopyası eksik çıktı.") }
        if (!tmp.renameTo(dst)) { tmp.delete(); throw IllegalStateException("Model yerine taşınamadı.") }
        onProgress(100)
    }
}
