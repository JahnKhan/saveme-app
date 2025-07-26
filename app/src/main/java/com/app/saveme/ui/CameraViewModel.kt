package com.app.saveme.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.saveme.audio.AudioRecorder
import com.app.saveme.data.DUMMY_TRANSCRIPTION
import com.app.saveme.data.DUMMY_AI_RESPONSE
import com.app.saveme.data.ModelDownloadStatus
import com.app.saveme.data.ModelImportStatus
import com.app.saveme.data.ModelState
import com.app.saveme.model.ModelManager
import com.app.saveme.storage.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val isProcessingImage: Boolean = false // Add this to prevent rapid captures
)

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val audioRecorder = AudioRecorder()
    private val fileManager = FileManager()
    private var modelManager: ModelManager? = null
    
    // Track ongoing inference job to properly cancel it
    private var inferenceJob: Job? = null
    
    fun initializeModelManager(context: Context) {
        if (modelManager == null) {
            Log.d(TAG, "Initializing ModelManager...")
            modelManager = ModelManager(context)
            observeModelState()
            Log.d(TAG, "ModelManager initialized")
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
    
    fun startModelDownload() {
        viewModelScope.launch {
            modelManager?.downloadModel()
        }
    }
    
    fun retryModelDownload() {
        startModelDownload()
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
                    
                    // Process data with AI model or dummy transcription
                    processData(context)
                    
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
    
    private fun processData(context: Context) {
        // Prevent multiple rapid captures
        if (_uiState.value.isProcessingImage) {
            Log.d(TAG, "Already processing an image, ignoring new request")
            return
        }
        
        // Cancel any previous inference job
        inferenceJob?.cancel()
        
        inferenceJob = viewModelScope.launch(Dispatchers.Default) {
            val capturedImage = _uiState.value.lastCapturedImage
            
            Log.d(TAG, "processData called - modelState: ${_uiState.value.modelState}, hasImage: ${capturedImage != null}")
            
            if (capturedImage != null && (_uiState.value.modelState == ModelState.LOADED || 
                _uiState.value.modelState == ModelState.INITIALIZING)) {
                
                // Update UI on main thread first
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Preparing AI analysis...",
                        isCapturing = true,
                        isRecordingAudio = false,
                        isProcessingImage = true // Set processing flag
                    )
                    
                    // Switch to chat screen immediately
                    switchToChatScreen()
                }
                
                // Wait for model to be ready if it's still initializing
                Log.d(TAG, "Waiting for model to be ready...")
                val modelReady = modelManager?.waitForModelReady() ?: false
                
                if (!modelReady) {
                    Log.e(TAG, "Model not ready, falling back to dummy data")
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isProcessingImage = false)
                        processDummyData()
                    }
                    return@launch
                }
                
                // Model is ready, proceed with AI analysis
                Log.d(TAG, "Model is ready, starting AI analysis...")
                
                try {
                    val startTime = System.currentTimeMillis()
                    val prompt = DUMMY_TRANSCRIPTION
                    
                    Log.d(TAG, "Starting streaming inference with prompt: '$prompt'")
                    
                    // Start streaming - update UI state to show we're streaming
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            llmResponse = "",
                            isStreamingResponse = true,
                            statusMessage = "Analyzing with AI..."
                        )
                    }
                    
                    val result = modelManager?.generateResponseStreaming(
                        prompt = prompt,
                        imageBitmap = capturedImage,
                        onTokenReceived = { token, isComplete ->
                            // Only update UI if we're still on chat screen and job is active
                            if (inferenceJob?.isActive == true && _uiState.value.currentScreen == AppScreen.CHAT) {
                                viewModelScope.launch(Dispatchers.Main) {
                                    val currentResponse = _uiState.value.llmResponse
                                    _uiState.value = _uiState.value.copy(
                                        llmResponse = currentResponse + token,
                                        isStreamingResponse = !isComplete
                                    )
                                    
                                    if (isComplete) {
                                        Log.d(TAG, "Streaming completed")
                                        _uiState.value = _uiState.value.copy(
                                            isCapturing = false,
                                            isRecordingAudio = false,
                                            recordingDuration = 0f,
                                            audioAmplitude = 0,
                                            statusMessage = "",
                                            isProcessingImage = false // Clear processing flag
                                        )
                                    }
                                }
                            }
                        }
                    )
                    
                    val inferenceEndTime = System.currentTimeMillis()
                    Log.d(TAG, "Total streaming time: ${inferenceEndTime - startTime}ms")
                    Log.d(TAG, "Final result: '$result'")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "AI inference failed", e)
                    // Update UI on main thread
                    if (inferenceJob?.isActive == true) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                isCapturing = false,
                                isRecordingAudio = false,
                                recordingDuration = 0f,
                                audioAmplitude = 0,
                                llmResponse = DUMMY_AI_RESPONSE,
                                isStreamingResponse = false,
                                statusMessage = "",
                                isProcessingImage = false // Clear processing flag
                            )
                        }
                    }
                }
            } else {
                Log.d(TAG, "Using dummy data - modelState: ${_uiState.value.modelState}, hasImage: ${capturedImage != null}")
                // Fallback to dummy transcription if model not ready
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isProcessingImage = false)
                    processDummyData()
                }
            }
        }
    }
    
    private fun processDummyData() {
        Log.d(TAG, "Processing captured data with dummy transcription...")
        Log.d(TAG, "Dummy transcription: $DUMMY_TRANSCRIPTION")
        
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
    
    fun switchToCameraScreen() {
        Log.d(TAG, "Switching to camera screen...")
        
        // Cancel any ongoing AI inference job immediately
        inferenceJob?.cancel()
        inferenceJob = null
        
        // Stop the inference at MediaPipe level immediately
        modelManager?.stopInference()
        
        // Update UI state immediately to prevent further operations
        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.CAMERA,
            llmResponse = "",
            isStreamingResponse = false,
            isCapturing = false,
            isRecordingAudio = false,
            recordingDuration = 0f,
            audioAmplitude = 0,
            statusMessage = "",
            isProcessingImage = false // Clear processing flag when switching back
        )
        
        // Reset session in background with proper error handling
        viewModelScope.launch(Dispatchers.Default) {
            try {
                Log.d(TAG, "Resetting session in background...")
                modelManager?.resetSession()
                Log.d(TAG, "Session reset completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting session in background", e)
                // Try one more time if first attempt fails
                try {
                    kotlinx.coroutines.delay(500)
                    modelManager?.resetSession()
                    Log.d(TAG, "Session reset completed on retry")
                } catch (e2: Exception) {
                    Log.e(TAG, "Session reset failed even on retry", e2)
                }
            }
        }
        
        Log.d(TAG, "Switched to camera screen and interrupted any ongoing operations")
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
        modelManager?.stopInference()
        modelManager?.cleanup()
    }
} 