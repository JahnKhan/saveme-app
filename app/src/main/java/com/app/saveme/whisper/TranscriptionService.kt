package com.app.saveme.whisper

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class TranscriptionService(private val context: Context) {
    private val TAG = "TranscriptionService"
    private val whisperEngine: WhisperEngine = WhisperEngineJava(context)
    private var isInitialized = false
    
    companion object {
        private const val MODEL_FILE = "whisper-tiny.en.tflite"
        private const val VOCAB_FILE = "filters_vocab_en.bin"
        private const val IS_MULTILINGUAL = false
    }
    
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.d(TAG, "TranscriptionService already initialized")
            return true
        }
        
        return try {
            // Copy model files from assets to external files directory
            val modelFile = copyAssetToExternalFiles(MODEL_FILE)
            val vocabFile = copyAssetToExternalFiles(VOCAB_FILE)
            
            if (modelFile != null && vocabFile != null) {
                isInitialized = whisperEngine.initialize(
                    modelFile.absolutePath,
                    vocabFile.absolutePath,
                    IS_MULTILINGUAL
                )
                Log.d(TAG, "TranscriptionService initialized: $isInitialized")
                isInitialized
            } else {
                Log.e(TAG, "Failed to copy model files from assets")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TranscriptionService", e)
            false
        }
    }
    
    fun transcribeAudioFile(audioFilePath: String): String {
        if (!isInitialized) {
            Log.e(TAG, "TranscriptionService not initialized")
            return "Error: Transcription service not initialized"
        }
        
        return try {
            val result = whisperEngine.transcribeFile(audioFilePath)
            Log.d(TAG, "Transcription completed: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transcribe audio file", e)
            "Error transcribing audio: ${e.message}"
        }
    }
    
    fun transcribeAudioBuffer(samples: FloatArray): String {
        if (!isInitialized) {
            Log.e(TAG, "TranscriptionService not initialized")
            return "Error: Transcription service not initialized"
        }
        
        return try {
            val result = whisperEngine.transcribeBuffer(samples)
            Log.d(TAG, "Buffer transcription completed: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transcribe audio buffer", e)
            "Error transcribing audio buffer: ${e.message}"
        }
    }
    
    fun isReady(): Boolean {
        return isInitialized && whisperEngine.isInitialized()
    }
    
    fun cleanup() {
        try {
            whisperEngine.deinitialize()
            isInitialized = false
            Log.d(TAG, "TranscriptionService cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    private fun copyAssetToExternalFiles(assetFileName: String): File? {
        return try {
            val inputStream = context.assets.open(assetFileName)
            val outputFile = File(context.getExternalFilesDir(null), assetFileName)
            
            if (!outputFile.exists()) {
                val outputStream = FileOutputStream(outputFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                Log.d(TAG, "Copied $assetFileName to ${outputFile.absolutePath}")
            } else {
                Log.d(TAG, "$assetFileName already exists at ${outputFile.absolutePath}")
            }
            
            outputFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset $assetFileName", e)
            null
        }
    }
} 