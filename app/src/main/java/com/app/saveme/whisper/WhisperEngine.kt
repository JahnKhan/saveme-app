package com.app.saveme.whisper

import java.io.IOException

interface WhisperEngine {
    fun isInitialized(): Boolean
    fun initialize(modelPath: String, vocabPath: String, multilingual: Boolean): Boolean
    fun deinitialize()
    fun transcribeFile(wavePath: String): String
    fun transcribeBuffer(samples: FloatArray): String
} 