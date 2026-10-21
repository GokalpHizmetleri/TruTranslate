package com.trutranslate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val scope = MainScope()
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var srcSpin: Spinner
    private lateinit var dstSpin: Spinner
    private var translating = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val title = TextView(this).apply { text = "TruTranslate"; textSize = 24f; setPadding(0,0,0,4) }
        val sub = TextView(this).apply { text = "%100 cihazda çeviri - sunucu yok, hesap yok"; textSize = 12f; setPadding(0,0,0,16) }
        val spinRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        srcSpin = Spinner(this); dstSpin = Spinner(this)
        val srcItems = listOf("Otomatik") + langs.map { it.second }
        srcSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, srcItems)
        dstSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs.map { it.second })
        spinRow.addView(srcSpin, LinearLayout.LayoutParams(0, -2, 1f))
        spinRow.addView(dstSpin, LinearLayout.LayoutParams(0, -2, 1f))
        input = EditText(this).apply { hint = "Çevrilecek metni yaz veya mikrofona konuş"; minLines = 3; gravity = Gravity.TOP }
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val micBtn = Button(this).apply { text = "🎤 Konuş" }
        val goBtn = Button(this).apply { text = "Çevir" }
        btnRow.addView(micBtn, LinearLayout.LayoutParams(0, -2, 1f))
        btnRow.addView(goBtn, LinearLayout.LayoutParams(0, -2, 1f))
        output = TextView(this).apply { textSize = 18f; setPadding(0,16,0,16); setTextIsSelectable(true) }
        val speakBtn = Button(this).apply { text = "🔊 Dinle" }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        status = TextView(this).apply { textSize = 12f }
        root.addView(title); root.addView(sub); root.addView(spinRow); root.addView(input)
        root.addView(btnRow); root.addView(output); root.addView(speakBtn); root.addView(progress); root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })

        tts = TextToSpeech(this) { }
        goBtn.setOnClickListener { doTranslate() }
        speakBtn.setOnClickListener {
            val code = langs.getOrNull(dstSpin.selectedItemPosition)?.first ?: "tr"
            tts?.language = Locale.forLanguageTag(code)
            tts?.speak(output.text.toString(), TextToSpeech.QUEUE_FLUSH, null, "tt")
        }
        micBtn.setOnClickListener { startListening() }
        ensureModel()
    }

    private fun ensureModel() {
        if (ModelDownloader.isReady(this)) { loadNative(); return }
        status.text = "Model bir kere iniyor (~1,1 GB)..."
        progress.visibility = View.VISIBLE
        scope.launch {
            val ok = ModelDownloader.download(this@MainActivity) { d, t ->
                runOnUiThread {
                    val pct = if (t > 0) (d * 100 / t).toInt() else 0
                    progress.progress = pct
                    status.text = "Model iniyor: %$pct (${d/1048576} MB / ${t/1048576} MB) - bir kere iner"
                }
            }
            runOnUiThread { progress.visibility = View.GONE }
            if (ok) loadNative() else runOnUiThread { status.text = "Model indirilemedi. İnterneti kontrol edip uygulamayı yeniden aç." }
        }
    }

    private fun loadNative() {
        scope.launch(Dispatchers.Default) {
            runOnUiThread { status.text = "Model yükleniyor..." }
            val ok = LlamaBridge.loadModel(ModelDownloader.modelFile(this@MainActivity).absolutePath)
            runOnUiThread { status.text = if (ok) "Hazır ✓ (çeviri tamamen bu cihazda)" else "Model yüklenemedi" }
        }
    }

    private fun doTranslate() {
        if (translating) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (!LlamaBridge.isLoaded()) { status.text = "Model henüz hazır değil"; return }
        val code = langs.getOrNull(dstSpin.selectedItemPosition)?.first ?: "tr"
        val target = langNamesEn[code] ?: "Turkish"
        translating = true
        status.text = "Çevriliyor..."
        scope.launch(Dispatchers.Default) {
            val res = try { LlamaBridge.translate(text, target) } catch (e: Exception) { "Hata: ${e.message}" }
            runOnUiThread { output.text = res; status.text = "Hazır ✓"; translating = false }
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        val code = langs.getOrNull((srcSpin.selectedItemPosition - 1).coerceAtLeast(0))?.first ?: "tr"
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, code)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!texts.isNullOrEmpty()) { input.setText(texts[0]); doTranslate() }
            }
            override fun onError(error: Int) { status.text = "Ses tanıma hatası: $error" }
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Dinliyorum..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }

    override fun onDestroy() { scope.cancel(); recognizer?.destroy(); tts?.shutdown(); super.onDestroy() }
}
