package com.app.saveme.whisper

import android.content.Context
import android.util.Log
import com.whispertflite.engine.WhisperEngineJava as JavaWhisperEngine
import java.io.IOException

class WhisperEngineJava(private val context: Context) : WhisperEngine {
    private val TAG = "WhisperEngineJava"
    private val javaEngine = JavaWhisperEngine(context)
    private var isInitialized = false

    override fun isInitialized(): Boolean {
        return isInitialized && javaEngine.isInitialized()
    }

    override fun initialize(modelPath: String, vocabPath: String, multilingual: Boolean): Boolean {
        return try {
            isInitialized = javaEngine.initialize(modelPath, vocabPath, multilingual)
            Log.d(TAG, "Whisper engine initialized: $isInitialized")
            isInitialized
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize whisper engine", e)
            isInitialized = false
            false
        }
    }

    override fun deinitialize() {
        javaEngine.deinitialize()
        isInitialized = false
        Log.d(TAG, "Whisper engine deinitialized")
    }

    override fun transcribeFile(wavePath: String): String {
        return if (isInitialized()) {
            try {
                val result = javaEngine.transcribeFile(wavePath)
                Log.d(TAG, "Transcription result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to transcribe file", e)
                "Error transcribing audio: ${e.message}"
            }
        } else {
            Log.e(TAG, "Whisper engine not initialized")
            "Error: Whisper engine not initialized"
        }
    }

    override fun transcribeBuffer(samples: FloatArray): String {
        return if (isInitialized()) {
            try {
                val result = javaEngine.transcribeBuffer(samples)
                Log.d(TAG, "Buffer transcription result: $result")
                result ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "Failed to transcribe buffer", e)
                "Error transcribing audio buffer: ${e.message}"
            }
        } else {
            Log.e(TAG, "Whisper engine not initialized")
            "Error: Whisper engine not initialized"
        }
    }
} 