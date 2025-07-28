package com.app.saveme.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.saveme.audio.AudioRecorder
import com.app.saveme.data.TRANSCRIPTION_PROMPT_PREFIX
import com.app.saveme.data.DUMMY_AI_RESPONSE
import com.app.saveme.data.ModelDownloadStatus
import com.app.saveme.data.ModelImportStatus
import com.app.saveme.data.ModelState
import com.app.saveme.model.ModelManager
import com.app.saveme.storage.FileManager
import com.app.saveme.whisper.TranscriptionService
import com.app.saveme.whisper.AudioConverter
import com.app.saveme.tts.TTSManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "CameraViewModel"

enum class AppScreen {
    CAMERA,
    CHAT
}

data class CameraUiState(
    val currentScreen: AppScreen = AppScreen.CAMERA,
    val hasAllPermissions: Boolean = false,
    val isCapturing: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val recordingDuration: Float = 0f,
    val audioAmplitude: Int = 0,
    val lastCapturedImage: Bitmap? = null,
    val statusMessage: String = "",
    val modelState: ModelState = ModelState.NOT_DOWNLOADED,
    val downloadStatus: ModelDownloadStatus = ModelDownloadStatus(),
    val importStatus: ModelImportStatus = ModelImportStatus(),
    val llmResponse: String = "",
    val isStreamingResponse: Boolean = false,
    val isProcessingImage: Boolean = false, // Add this to prevent rapid captures
    val isLoadingDigitalTwin: Boolean = false,
    val userPrompt: String = "",
    val transcriptionStatus: String = "",
    val isSpeaking: Boolean = false,
    // New processing states
    val isTranscribing: Boolean = false,
    val isGeneratingResponse: Boolean = false,
    val processingPhase: ProcessingPhase = ProcessingPhase.IDLE
)

enum class ProcessingPhase {
    IDLE,
    TRANSCRIBING,
    GENERATING,
    SPEAKING
}

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val audioRecorder = AudioRecorder()
    private val fileManager = FileManager()
    private var modelManager: ModelManager? = null
    private var transcriptionService: TranscriptionService? = null
    private var ttsManager: TTSManager? = null
    private val audioConverter = AudioConverter()
    
    private val digitalTwinApiService by lazy {
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://save-me.app/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
        retrofit.create(com.app.saveme.data.DigitalTwinApiService::class.java)
    }
    
    // Track ongoing inference job to properly cancel it
    private var inferenceJob: Job? = null
    
    fun initializeModelManager(context: Context) {
        if (modelManager == null) {
            Log.d(TAG, "Initializing ModelManager...")
            modelManager = ModelManager(context)
            observeModelState()
            Log.d(TAG, "ModelManager initialized")
        }
        
        // Initialize transcription service
        if (transcriptionService == null) {
            Log.d(TAG, "Initializing TranscriptionService...")
            transcriptionService = TranscriptionService(context)
            initializeTranscriptionService()
        }
        
        // Initialize TTS manager
        if (ttsManager == null) {
            Log.d(TAG, "Initializing TTSManager...")
            ttsManager = TTSManager(context)
            initializeTTSManager()
        }
    }
    
    private fun initializeTranscriptionService() {
        viewModelScope.launch(Dispatchers.Default) {
            val success = transcriptionService?.initialize() ?: false
            withContext(Dispatchers.Main) {
                if (success) {
                    Log.d(TAG, "TranscriptionService initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize TranscriptionService")
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Failed to initialize speech-to-text service"
                    )
                }
            }
        }
    }
    
    private fun initializeTTSManager() {
        ttsManager?.initialize { success ->
            if (success) {
                Log.d(TAG, "TTSManager initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize TTSManager")
            }
        }
    }
    
    private fun observeModelState() {
        viewModelScope.launch {
            modelManager?.modelState?.collect { state ->
                _uiState.value = _uiState.value.copy(modelState = state)
                
                // Auto-load model when downloaded
                if (state == ModelState.DOWNLOADED && !modelManager!!.isModelLoaded()) {
                    loadModel()
                }
            }
        }
        
        viewModelScope.launch {
            modelManager?.downloadStatus?.collect { status ->
                _uiState.value = _uiState.value.copy(downloadStatus = status)
            }
        }
        
        viewModelScope.launch {
            modelManager?.importStatus?.collect { status ->
                _uiState.value = _uiState.value.copy(importStatus = status)
            }
        }
    }
    
    fun loadDigitalTwin(token: String, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDigitalTwin = true, statusMessage = "Loading Digital Twin...")
            try {
                val response = digitalTwinApiService.getDigitalTwin(token)
                fileManager.saveContext(context, response.text)
                _uiState.value = _uiState.value.copy(
                    isLoadingDigitalTwin = false,
                    statusMessage = "Digital Twin loaded successfully!"
                )
                // Clear message after delay
                kotlinx.coroutines.delay(3000)
                if (_uiState.value.statusMessage.contains("Digital Twin loaded successfully")) {
                    _uiState.value = _uiState.value.copy(statusMessage = "")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingDigitalTwin = false,
                    statusMessage = "Failed to load Digital Twin: ${e.message}"
                )
            }
        }
    }

    fun startModelDownload() {
        viewModelScope.launch {
            try {
                modelManager?.downloadModel()
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to start download: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Failed to start download: ${e.message}"
                )
            }
        }
    }
    
    fun retryModelDownload() {
        startModelDownload()
    }
    
    fun validateAndCleanupModels() {
        modelManager?.validateAndCleanupModels()
    }
    
    fun debugModelDirectory(): String {
        return modelManager?.debugModelDirectory() ?: "ModelManager not initialized"
    }
    
    fun refreshDownloadStatus() {
        modelManager?.refreshDownloadStatus()
    }
    
    fun importModel(uri: android.net.Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Importing model..."
            )
            
            val success = modelManager?.importModel(uri, fileName) ?: false
            if (success) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Model imported successfully!"
                )
                // Clear message after delay
                kotlinx.coroutines.delay(2000)
                if (_uiState.value.statusMessage.contains("imported successfully")) {
                    _uiState.value = _uiState.value.copy(statusMessage = "")
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Failed to import model"
                )
            }
        }
    }
    
    private fun loadModel() {
        viewModelScope.launch(Dispatchers.Default) { // Move to background thread
            _uiState.value = _uiState.value.copy(
                statusMessage = "Loading AI model..."
            )

            val success = modelManager?.loadModel() ?: false
            
            // Update UI on main thread
            withContext(Dispatchers.Main) {
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "AI model loaded successfully!"
                    )
                    // Clear message after delay
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000)
                        if (_uiState.value.statusMessage.contains("loaded successfully")) {
                            _uiState.value = _uiState.value.copy(statusMessage = "")
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Failed to load AI model"
                    )
                }
            }
        }
    }
    
    fun updatePermissionStatus(hasPermissions: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasAllPermissions = hasPermissions
        )
    }
    
    fun dismissPermissionDialog() {
        // This function can be removed as we no longer use a permission dialog in the state
    }
    
    fun onPhotoCaptured(context: Context, bitmap: Bitmap) {
        Log.d(TAG, "Photo captured, starting processing...")
        
        _uiState.value = _uiState.value.copy(
            isCapturing = true,
            lastCapturedImage = bitmap,
            statusMessage = "Saving image..."
        )
        
        viewModelScope.launch {
            // Save image
            val imageFile = fileManager.saveImage(context, bitmap)
            if (imageFile != null) {
                Log.d(TAG, "Image saved successfully, starting audio recording...")
                startAudioRecording(context)
            } else {
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    statusMessage = "Failed to save image"
                )
            }
        }
    }
    
    private suspend fun startAudioRecording(context: Context) {
        _uiState.value = _uiState.value.copy(
            isRecordingAudio = true,
            statusMessage = "Recording audio...",
            recordingDuration = 0f,
            audioAmplitude = 0
        )
        
        try {
            audioRecorder.startRecording(
                onAmplitudeChanged = { amplitude ->
                    _uiState.value = _uiState.value.copy(audioAmplitude = amplitude)
                },
                onDurationChanged = { duration ->
                    _uiState.value = _uiState.value.copy(recordingDuration = duration)
                },
                onMaxDurationReached = {
                    finishAudioRecording(context)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            _uiState.value = _uiState.value.copy(
                isCapturing = false,
                isRecordingAudio = false,
                statusMessage = "Failed to start audio recording"
            )
        }
    }
    
    fun stopAudioRecording(context: Context) {
        if (audioRecorder.isRecording()) {
            finishAudioRecording(context)
        }
    }
    
    private fun finishAudioRecording(context: Context) {
        viewModelScope.launch {
            try {
                val audioData = audioRecorder.stopRecording()
                
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Saving audio..."
                )
                
                // Save audio
                val audioFile = fileManager.saveAudioData(context, audioData)
                
                if (audioFile != null) {
                    Log.d(TAG, "Audio saved successfully")
                    
                    // Process data with AI model and real transcription
                    processDataWithTranscription(context, audioData)
                    
                    // Cleanup old files
                    fileManager.cleanupOldFiles(context)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        isRecordingAudio = false,
                        statusMessage = "Failed to save audio"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to finish audio recording", e)
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    isRecordingAudio = false,
                    statusMessage = "Error processing audio"
                )
            }
        }
    }
    
    private fun processDataWithTranscription(context: Context, audioData: ByteArray) {
        if (_uiState.value.isProcessingImage) return

        _uiState.value = _uiState.value.copy(
            isProcessingImage = true,
            processingPhase = ProcessingPhase.TRANSCRIBING,
            statusMessage = "Converting audio format..."
        )
        switchToChatScreen()

        inferenceJob = viewModelScope.launch(Dispatchers.Default) {
            val capturedImage = _uiState.value.lastCapturedImage
            if (capturedImage == null) {
                Log.e(TAG, "processDataWithTranscription failed: image is null")
                _uiState.value = _uiState.value.copy(
                    isProcessingImage = false,
                    processingPhase = ProcessingPhase.IDLE
                )
                return@launch
            }

            // Convert PCM to WAV for transcription
            val wavFile = File(context.getExternalFilesDir(null), "temp_audio_${System.currentTimeMillis()}.wav")
            val conversionSuccess = audioConverter.pcmToWav(audioData, wavFile)
            
            if (!conversionSuccess) {
                Log.e(TAG, "Failed to convert PCM to WAV")
                _uiState.value = _uiState.value.copy(
                    isProcessingImage = false,
                    processingPhase = ProcessingPhase.IDLE,
                    statusMessage = "Failed to convert audio format"
                )
                return@launch
            }

            // Start transcription phase
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isTranscribing = true,
                    processingPhase = ProcessingPhase.TRANSCRIBING,
                    statusMessage = "Transcribing audio..."
                )
            }

            // Transcribe audio
            val transcription = transcriptionService?.transcribeAudioFile(wavFile.absolutePath) ?: "Error: Transcription service not available"
            
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isTranscribing = false,
                    transcriptionStatus = transcription
                )
            }
            
            Log.d(TAG, "Transcription result: $transcription")
            
            // Clean up temporary WAV file
            wavFile.delete()

            val manager = modelManager
            if (manager == null) {
                Log.e(TAG, "ModelManager is not initialized, aborting.")
                _uiState.value = _uiState.value.copy(
                    isProcessingImage = false,
                    processingPhase = ProcessingPhase.IDLE,
                    llmResponse = "Error: Model not initialized."
                )
                return@launch
            }

            if (!manager.isModelLoaded()) {
                val modelReady = manager.waitForModelReady()
                if (!modelReady) {
                    Log.e(TAG, "Model not ready, aborting.")
                    _uiState.value = _uiState.value.copy(
                        isProcessingImage = false,
                        processingPhase = ProcessingPhase.IDLE,
                        llmResponse = "Error: Model not ready."
                    )
                    return@launch
                }
            }
            
            // Create prompt with transcription
            val prompt = if (transcription.startsWith("Error:")) {
                // Fallback to dummy prompt if transcription failed
                "help me i dont know how to shutdown the system here !"
            } else {
                if (TRANSCRIPTION_PROMPT_PREFIX.isNotEmpty()) {
                    "$TRANSCRIPTION_PROMPT_PREFIX $transcription"
                } else {
                    transcription
                }
            }
            
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    userPrompt = transcription, // Only show the transcription, not the full prompt
                    isGeneratingResponse = true,
                    processingPhase = ProcessingPhase.GENERATING,
                    statusMessage = "Generating response...",
                    llmResponse = "" // Clear previous response for streaming
                )
            }
            
            // Start streaming TTS
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isSpeaking = true,
                    processingPhase = ProcessingPhase.SPEAKING
                )
                ttsManager?.startStreaming()
            }
            
            manager.generateResponseStreaming(
                prompt = prompt,
                imageBitmap = capturedImage
            ) { token, isComplete ->
                viewModelScope.launch(Dispatchers.Main) {
                    // Remove the inferenceJob check that was blocking updates
                    val currentResponse = _uiState.value.llmResponse
                    val newResponse = currentResponse + token
                    
                    Log.d(TAG, "Streaming token: '$token', current length: ${currentResponse.length}, new length: ${newResponse.length}")
                    
                    _uiState.value = _uiState.value.copy(
                        llmResponse = newResponse,
                        isStreamingResponse = true
                    )
                    
                    Log.d(TAG, "Updated UI state with new response, length: ${_uiState.value.llmResponse.length}")
                    
                    // Stream token to TTS
                    ttsManager?.streamToken(token)
                    
                    if (isComplete) {
                        Log.d(TAG, "Streaming complete, final response length: ${newResponse.length}")
                        _uiState.value = _uiState.value.copy(
                            isProcessingImage = false,
                            isGeneratingResponse = false,
                            isStreamingResponse = false,
                            processingPhase = ProcessingPhase.SPEAKING
                        )
                        // Flush any remaining text in the TTS buffer
                        ttsManager?.flushStreamingBuffer()
                    }
                }
            }
        }
    }
    
    private fun speakResponse(response: String) {
        if (response.isNotEmpty() && !response.startsWith("Error:")) {
            _uiState.value = _uiState.value.copy(isSpeaking = true)
            ttsManager?.speak(response) {
                _uiState.value = _uiState.value.copy(isSpeaking = false)
            }
        }
    }
    
    fun stopSpeaking() {
        ttsManager?.stop()
        ttsManager?.flushStreamingBuffer()
        _uiState.value = _uiState.value.copy(isSpeaking = false)
    }
    
    private fun processDummyData() {
        Log.d(TAG, "Processing captured data with dummy transcription...")
        
        _uiState.value = _uiState.value.copy(
            isCapturing = false,
            isRecordingAudio = false,
            recordingDuration = 0f,
            audioAmplitude = 0,
            llmResponse = DUMMY_AI_RESPONSE,
            statusMessage = ""  // Clear status message since we show the response
        )
        
        Log.d(TAG, "UI updated with dummy response: $DUMMY_AI_RESPONSE")
    }
    
    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = "")
    }
    
    fun clearLlmResponse() {
        _uiState.value = _uiState.value.copy(
            llmResponse = "",
            isStreamingResponse = false
        )
    }
    
    fun cancelInference() {
        if (!_uiState.value.isProcessingImage) return
        Log.d(TAG, "User requested to cancel inference.")

        viewModelScope.launch(Dispatchers.Default) {
            modelManager?.resetSession()
        }
    }
    
    fun switchToCameraScreen() {
        if (_uiState.value.isProcessingImage) {
            Log.d(TAG, "Cannot switch to camera screen while processing.")
            return
        }

        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.CAMERA,
            llmResponse = "",
            isStreamingResponse = false,
            userPrompt = "",
            transcriptionStatus = "",
            isSpeaking = false,
            isTranscribing = false,
            isGeneratingResponse = false,
            processingPhase = ProcessingPhase.IDLE
        )
        
        // Stop any ongoing speech
        stopSpeaking()
    }

    fun forceSwitchToCameraScreen() {
        Log.w(TAG, "Force switching to camera screen.")
        cancelInference()
        stopSpeaking()
        switchToCameraScreen()
    }
    
    fun switchToChatScreen() {
        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.CHAT,
            isCapturing = false,
            isRecordingAudio = false,
            recordingDuration = 0f,
            audioAmplitude = 0
        )
    }
    
    fun enableVisionSupport() {
        Log.d(TAG, "Enabling vision support manually")
        modelManager?.setVisionSupport(true)
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Cancel any ongoing inference
        inferenceJob?.cancel()
        
        // Stop inference and clean up model
        modelManager?.cleanup()
        
        // Clean up transcription service
        transcriptionService?.cleanup()
        
        // Clean up TTS
        ttsManager?.cleanup()
    }
} 