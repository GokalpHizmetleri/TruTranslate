package com.trutranslate.app

object LlamaBridge {
    init { System.loadLibrary("trutranslate") }
    external fun loadModel(path: String, nCtx: Int, nThreads: Int): Boolean
    external fun translate(text: String, targetLangName: String): String
    external fun isLoaded(): Boolean
    external fun getLastError(): String
    external fun freeModel()
}
