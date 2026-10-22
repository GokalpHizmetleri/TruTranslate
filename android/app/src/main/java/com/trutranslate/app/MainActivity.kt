package com.trutranslate.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val scope = MainScope()
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var modelReady = false
    private var translating = false
    private var pendingTranslate: Job? = null

    private lateinit var root: LinearLayout
    private lateinit var srcBtn: TextView
    private lateinit var dstBtn: TextView
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var statusLine: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var micBtn: TextView
    private lateinit var speakBtn: TextView
    private lateinit var historyBox: LinearLayout
    private lateinit var historySection: LinearLayout

    private var srcIdx = 0
    private var dstIdx = 1

    private val langs = listOf(
        "tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "fr" to "Français", "es" to "Español",
        "pt" to "Português", "it" to "Italiano", "nl" to "Nederlands", "pl" to "Polski", "cs" to "Čeština",
        "ru" to "Русский", "uk" to "Українська", "ar" to "العربية", "he" to "עברית", "fa" to "فارسی",
        "ur" to "اردو", "hi" to "हिन्दी", "bn" to "বাংলা", "ta" to "தமிழ்", "te" to "తెలుగు",
        "mr" to "मराठी", "gu" to "ગુજરાતી", "zh" to "中文", "zh-Hant" to "繁體中文", "ja" to "日本語",
        "ko" to "한국어", "th" to "ไทย", "vi" to "Tiếng Việt", "ms" to "Bahasa Melayu", "id" to "Bahasa Indonesia",
        "tl" to "Filipino", "km" to "ខ្មែរ", "my" to "မြန်မာ", "bo" to "བོད་ཡིག", "kk" to "Қазақша",
        "mn" to "Монгол", "ug" to "ئۇيغۇرچە", "yue" to "粵語"
    )
    private val langNamesEn = mapOf(
        "tr" to "Turkish", "en" to "English", "de" to "German", "fr" to "French", "es" to "Spanish",
        "pt" to "Portuguese", "it" to "Italian", "nl" to "Dutch", "pl" to "Polish", "cs" to "Czech",
        "ru" to "Russian", "uk" to "Ukrainian", "ar" to "Arabic", "he" to "Hebrew", "fa" to "Persian",
        "ur" to "Urdu", "hi" to "Hindi", "bn" to "Bengali", "ta" to "Tamil", "te" to "Telugu",
        "mr" to "Marathi", "gu" to "Gujarati", "zh" to "Chinese", "zh-Hant" to "Traditional Chinese",
        "ja" to "Japanese", "ko" to "Korean", "th" to "Thai", "vi" to "Vietnamese", "ms" to "Malay",
        "id" to "Indonesian", "tl" to "Filipino", "km" to "Khmer", "my" to "Burmese", "bo" to "Tibetan",
        "kk" to "Kazakh", "mn" to "Mongolian", "ug" to "Uyghur", "yue" to "Cantonese"
    )
    private val locales = mapOf(
        "tr" to "tr-TR", "en" to "en-US", "de" to "de-DE", "fr" to "fr-FR", "es" to "es-ES",
        "pt" to "pt-PT", "it" to "it-IT", "nl" to "nl-NL", "pl" to "pl-PL", "cs" to "cs-CZ",
        "ru" to "ru-RU", "uk" to "uk-UA", "ar" to "ar-SA", "hi" to "hi-IN", "zh" to "zh-CN",
        "ja" to "ja-JP", "ko" to "ko-KR", "th" to "th-TH", "vi" to "vi-VN", "id" to "id-ID"
    )

    private val accent = Color.parseColor("#1A73E8")
    private fun isNight() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private fun bgColor() = if (isNight()) Color.parseColor("#121316") else Color.parseColor("#F1F3F4")
    private fun cardColor() = if (isNight()) Color.parseColor("#1E1F22") else Color.WHITE
    private fun textColor() = if (isNight()) Color.parseColor("#E8EAED") else Color.parseColor("#202124")
    private fun subTextColor() = if (isNight()) Color.parseColor("#9AA0A6") else Color.parseColor("#5F6368")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun card(): GradientDrawable = GradientDrawable().apply {
        setColor(cardColor()); cornerRadius = dp(16).toFloat()
        if (!isNight()) setStroke(dp(1), Color.parseColor("#E0E0E0"))
    }
    private fun pill(filled: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        if (filled) setColor(accent) else { setColor(Color.TRANSPARENT); setStroke(dp(1), subTextColor()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        tts = TextToSpeech(this) { }
        prepareModel()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor())
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        // Başlık
        root.addView(TextView(this).apply {
            text = "TruTranslate"; textSize = 22f; setTextColor(textColor())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(4), 0, 0, dp(16))
        })

        // Dil çubuğu: [Kaynak] [⇄] [Hedef]
        val langBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = card(); setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fun langButton(): TextView = TextView(this).apply {
            textSize = 15f; setTextColor(accent); gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        }
        srcBtn = langButton().apply { text = langs[srcIdx].second; setOnClickListener { pickLanguage(true) } }
        dstBtn = langButton().apply { text = langs[dstIdx].second; setOnClickListener { pickLanguage(false) } }
        val swap = TextView(this).apply {
            text = "⇄"; textSize = 20f; setTextColor(subTextColor()); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(44))
            setOnClickListener {
                if (!modelReady) return@setOnClickListener
                val t = srcIdx; srcIdx = dstIdx; dstIdx = t
                srcBtn.text = langs[srcIdx].second; dstBtn.text = langs[dstIdx].second
                val it = input.text.toString(); input.setText(output.text.toString()); output.text = it
                scheduleTranslate()
            }
        }
        langBar.addView(srcBtn); langBar.addView(swap); langBar.addView(dstBtn)
        root.addView(langBar)

        // Giriş kartı
        val inCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = card()
            setPadding(dp(16), dp(12), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        input = EditText(this).apply {
            hint = "Çevrilecek metni yazın"; textSize = 18f
            setTextColor(textColor()); setHintTextColor(subTextColor())
            background = null; minLines = 3; gravity = Gravity.TOP
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { scheduleTranslate() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        inCard.addView(input)
        val inRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        micBtn = actionButton("🎤", "Konuş") { startListening() }
        val clearBtn = actionButton("✕", "Temizle") { input.setText(""); output.text = "" }
        inRow.addView(clearBtn); inRow.addView(micBtn)
        inCard.addView(inRow)
        root.addView(inCard)

        // Çıkış kartı
        val outCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = card()
            setPadding(dp(16), dp(12), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        outCard.addView(TextView(this).apply {
            text = langs[dstIdx].second; textSize = 13f; setTextColor(accent)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            tag = "outLabel"
        })
        output = TextView(this).apply {
            textSize = 18f; setTextColor(textColor()); minLines = 3
            setPadding(0, dp(4), 0, dp(4))
            setTextIsSelectable(true)
        }
        outCard.addView(output)
        val outRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val copyBtn = actionButton("⧉", "Kopyala") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("çeviri", output.text.toString()))
            toast("Kopyalandı")
        }
        val shareBtn = actionButton("↗", "Paylaş") {
            val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, output.text.toString()) }
            startActivity(Intent.createChooser(i, "Çeviriyi paylaş"))
        }
        speakBtn = actionButton("🔊", "Dinle") { speakOutput() }
        outRow.addView(copyBtn); outRow.addView(shareBtn); outRow.addView(speakBtn)
        outCard.addView(outRow)
        root.addView(outCard)

        // Durum + ilerleme
        statusLine = TextView(this).apply { textSize = 13f; setTextColor(subTextColor()); setPadding(dp(4), dp(12), 0, 0) }
        root.addView(statusLine)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(6) }
        }
        root.addView(progressBar)

        // Geçmiş
        historySection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(historySection)
        renderHistory()
    }

    private fun actionButton(icon: String, label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = "$icon  $label"; textSize = 13f; setTextColor(accent)
            gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            setOnClickListener { onClick() }
        }

    private fun pickLanguage(isSrc: Boolean) {
        val items = langs.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (isSrc) "Kaynak dil" else "Hedef dil")
            .setItems(items) { _, which ->
                if (isSrc) { srcIdx = which; srcBtn.text = langs[which].second }
                else { dstIdx = which; dstBtn.text = langs[which].second; historySection.findViewWithTag<TextView>("outLabel")?.text = langs[which].second }
                scheduleTranslate()
            }.show()
    }

    // ---- Model yükleme: çökme korumalı ----
    // loadModel native çağrısı çökerse (SIGILL/OOM) uygulama anında kapanır.
    // Yüklemeden ÖNCE bayrak yazılır; başarıda silinir. Açılışta bayrak varsa
    // önceki deneme çöktü demektir → otomatik yükleme yapma, hata ekranı göster.
    private fun loadPrefs() = getSharedPreferences("model_state", Context.MODE_PRIVATE)

    private fun deviceProfile(): Triple<Int, Int, String?> {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalGb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availGb = mi.availMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = when {
            totalGb < 3.5 -> minOf(2, cores)
            totalGb < 6.0 -> minOf(3, cores)
            else -> minOf(4, cores)
        }
        val ctx = when {
            totalGb < 3.5 -> 512
            totalGb < 6.0 -> 768
            else -> 1024
        }
        val warn = if (availGb < 1.2) "Boş RAM çok az (%.1f GB). Diğer uygulamaları kapatıp tekrar deneyin.".format(availGb) else null
        return Triple(ctx, threads, warn)
    }

    private fun prepareModel() {
        val prefs = loadPrefs()
        if (prefs.getString("load_state", null) == "loading") {
            // Önceki yükleme denemesi çöktü → döngüye girme
            showModelError("Model önceki açılışta yüklenirken uygulama kapandı. " +
                "Aşağıdaki seçeneklerden birini deneyin.")
            return
        }
        startModelLoad()
    }

    private fun startModelLoad() {
        val prefs = loadPrefs()
        statusLine.text = "Model hazırlanıyor…"
        progressBar.visibility = View.VISIBLE
        val (nCtx, nThreads, memWarn) = deviceProfile()
        scope.launch(Dispatchers.IO) {
            try {
                if (!ModelManager.isReady(this@MainActivity)) {
                    withContext(Dispatchers.Main) { statusLine.text = "Model ilk kez ayıklanıyor (bir kez, ~1,1 GB)…" }
                    ModelManager.extract(this@MainActivity) { p ->
                        scope.launch(Dispatchers.Main) { progressBar.progress = p }
                    }
                }
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusLine.text = "Model yükleniyor (bellek: %d token bağlam, %d iş parçacığı)…".format(nCtx, nThreads)
                }
                // Çökme bayrağı: native çağrıdan önce yaz
                prefs.edit().putString("load_state", "loading").commit()
                var ok = false
                try {
                    ok = LlamaBridge.loadModel(ModelManager.modelFile(this@MainActivity).absolutePath, nCtx, nThreads)
                } catch (t: Throwable) {
                    prefs.edit().putString("load_state", "error").commit()
                    withContext(Dispatchers.Main) { showModelError("Model yüklenemedi: " + (t.message ?: t.toString())) }
                    return@launch
                }
                if (ok) {
                    prefs.edit().remove("load_state").commit()
                    withContext(Dispatchers.Main) {
                        modelReady = true
                        statusLine.text = (memWarn?.plus("\n") ?: "") + "Hazır — çeviri cihazınızda, çevrimdışı çalışır."
                    }
                } else {
                    prefs.edit().putString("load_state", "error").commit()
                    val err = try { LlamaBridge.getLastError() } catch (t: Throwable) { "" }
                    withContext(Dispatchers.Main) {
                        showModelError("Model yüklenemedi." + (if (err.isNotBlank()) "\n\nDetay:\n" + err.takeLast(800) else ""))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showModelError(e.message ?: "Model hazırlanamadı.")
                }
            }
        }
    }

    private fun showModelError(msg: String) {
        progressBar.visibility = View.GONE
        statusLine.text = ""
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = card()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        box.addView(TextView(this).apply {
            text = "Model açılamadı"; textSize = 17f; setTextColor(Color.parseColor("#D93025"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = msg; textSize = 13f; setTextColor(textColor())
            setPadding(0, dp(8), 0, dp(8)); setTextIsSelectable(true)
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("↻", "Tekrar dene") {
            (root.findViewWithTag<View>("errorBox"))?.let { root.removeView(it) }
            loadPrefs().edit().remove("load_state").commit()
            startModelLoad()
        })
        row.addView(actionButton("🗑", "Modeli sıfırla") {
            loadPrefs().edit().remove("load_state").commit()
            ModelManager.modelFile(this).delete()
            (root.findViewWithTag<View>("errorBox"))?.let { root.removeView(it) }
            startModelLoad()
        })
        box.addView(row)
        box.tag = "errorBox"
        root.addView(box, 2)
    }

    private fun scheduleTranslate() {
        pendingTranslate?.cancel()
        val text = input.text.toString().trim()
        if (text.isEmpty()) { output.text = ""; return }
        if (!modelReady || translating) return
        pendingTranslate = scope.launch {
            delay(600)
            doTranslate(text)
        }
    }

    private suspend fun doTranslate(text: String) {
        translating = true
        withContext(Dispatchers.Main) { statusLine.text = "Çevriliyor…" }
        val result = withContext(Dispatchers.IO) {
            try { LlamaBridge.translate(text, langNamesEn[langs[dstIdx].first] ?: "English") }
            catch (e: Exception) { "Hata: ${e.message}" }
        }
        translating = false
        output.text = result
        statusLine.text = "Hazır — çeviri cihazınızda, çevrimdışı çalışır."
        addHistory(text, result)
        // Bekleyen yeni metin varsa çevir
        val now = input.text.toString().trim()
        if (now.isNotEmpty() && now != text) scheduleTranslate()
    }

    private fun speakOutput() {
        val text = output.text.toString()
        if (text.isEmpty()) return
        val loc = locales[langs[dstIdx].first]?.let { Locale.forLanguageTag(it) } ?: Locale.ENGLISH
        tts?.language = loc
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1")
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 7)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { toast("Bu cihazda konuşma tanıma yok."); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) input.setText(matches[0])
                }
                override fun onError(error: Int) { statusLine.text = "Sesli giriş durdu." }
                override fun onReadyForSpeech(params: Bundle?) { statusLine.text = "Dinleniyor…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { statusLine.text = "İşleniyor…" }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locales[langs[srcIdx].first] ?: "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Konuşun…")
        }
        recognizer?.startListening(intent)
    }

    // ---- Geçmiş ----
    private fun historyStore() = getSharedPreferences("history", Context.MODE_PRIVATE)

    private fun addHistory(src: String, dst: String) {
        if (dst.startsWith("Hata:")) return
        val arr = JSONArray(historyStore().getString("items", "[]"))
        val o = JSONObject().apply {
            put("src", src); put("dst", dst)
            put("sl", langs[srcIdx].second); put("dl", langs[dstIdx].second)
        }
        val na = JSONArray(); na.put(o)
        for (i in 0 until minOf(arr.length(), 49)) na.put(arr.get(i))
        historyStore().edit().putString("items", na.toString()).apply()
        renderHistory()
    }

    private fun renderHistory() {
        historySection.removeAllViews()
        val arr = JSONArray(historyStore().getString("items", "[]"))
        if (arr.length() == 0) return
        historySection.addView(TextView(this).apply {
            text = "Geçmiş"; textSize = 15f; setTextColor(textColor())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(20), 0, dp(8))
        })
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (i in 0 until minOf(arr.length(), 10)) {
            val o = arr.getJSONObject(i)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = card()
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                setOnClickListener {
                    input.setText(o.getString("src")); output.text = o.getString("dst")
                }
            }
            item.addView(TextView(this).apply {
                text = "${o.getString("sl")} → ${o.getString("dl")}"; textSize = 11f; setTextColor(accent)
            })
            item.addView(TextView(this).apply {
                text = o.getString("src").take(90); textSize = 14f; setTextColor(textColor())
            })
            item.addView(TextView(this).apply {
                text = o.getString("dst").take(90); textSize = 14f; setTextColor(subTextColor())
            })
            historyBox.addView(item)
        }
        historySection.addView(historyBox)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel(); recognizer?.destroy(); tts?.shutdown()
    }
}
