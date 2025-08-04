package com.app.saveme.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class TTSManager(private val context: Context) {
    private val TAG = "TTSManager"
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var onSpeakComplete: (() -> Unit)? = null
    
    // Streaming TTS variables
    private val speechBuffer = StringBuilder()
    private var isStreaming = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentUtteranceId = ""
    
    // Sentence endings for natural speech breaks
    private val sentenceEndings = setOf(".", "!", "?", "\n")
    
    fun initialize(onInit: (Boolean) -> Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            isInitialized = status == TextToSpeech.SUCCESS
            if (isInitialized) {
                setupTTS()
            }
            Log.d(TAG, "TTS initialized: $isInitialized")
            onInit(isInitialized)
        }
    }
    
    private fun setupTTS() {
        textToSpeech?.let { tts ->
            // Set language to English
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported")
            }
            
            // Set speech rate and pitch
            tts.setSpeechRate(0.9f) // Slightly slower for better clarity
            tts.setPitch(1.0f)
            
            // Set utterance progress listener
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started: $utteranceId")
                }
                
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS completed: $utteranceId")
                    if (utteranceId == currentUtteranceId) {
                        onSpeakComplete?.invoke()
                    }
                }
                
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error: $utteranceId")
                    if (utteranceId == currentUtteranceId) {
                        onSpeakComplete?.invoke()
                    }
                }
            })
        }
    }
    
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.e(TAG, "TTS not initialized")
            onComplete?.invoke()
            return
        }
        
        onSpeakComplete = onComplete
        
        textToSpeech?.let { tts ->
            val utteranceId = "saveme_utterance_${System.currentTimeMillis()}"
            currentUtteranceId = utteranceId
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "Failed to speak text")
                onComplete?.invoke()
            }
        }
    }
    
    // Start streaming TTS mode
    fun startStreaming() {
        if (!isInitialized) {
            Log.e(TAG, "TTS not initialized")
            return
        }
        
        isStreaming = true
        speechBuffer.clear()
        Log.d(TAG, "Started streaming TTS mode")
    }
    
    // Add token to streaming buffer and speak if sentence is complete
    fun streamToken(token: String) {
        if (!isStreaming || !isInitialized) return
        
        speechBuffer.append(token)
        val currentText = speechBuffer.toString()
        
        // Check if we have a complete sentence
        if (hasCompleteSentence(currentText)) {
            speakStreamingBuffer()
        }
    }
    
    // Force speak remaining buffer content
    fun flushStreamingBuffer() {
        if (!isStreaming) return
        
        val remainingText = speechBuffer.toString().trim()
        if (remainingText.isNotEmpty()) {
            speakText(remainingText)
        }
        
        isStreaming = false
        speechBuffer.clear()
        Log.d(TAG, "Flushed streaming TTS buffer")
    }
    
    private fun hasCompleteSentence(text: String): Boolean {
        return sentenceEndings.any { ending -> text.endsWith(ending) }
    }
    
    private fun speakStreamingBuffer() {
        val textToSpeak = speechBuffer.toString().trim()
        if (textToSpeak.isNotEmpty()) {
            speakText(textToSpeak)
            speechBuffer.clear()
        }
    }
    
    private fun speakText(text: String) {
        textToSpeech?.let { tts ->
            val utteranceId = "stream_${System.currentTimeMillis()}"
            val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "Failed to speak streaming text: $text")
            }
        }
    }
    
    fun stop() {
        textToSpeech?.stop()
        isStreaming = false
        speechBuffer.clear()
    }
    
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking == true
    }
    
    fun isStreamingMode(): Boolean {
        return isStreaming
    }
    
    fun cleanup() {
        try {
            stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
            Log.d(TAG, "TTS cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
} 