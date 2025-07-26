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
        if (_uiState.value.isProcessingImage) return

        _uiState.value = _uiState.value.copy(isProcessingImage = true)
        switchToChatScreen()

        inferenceJob = viewModelScope.launch(Dispatchers.Default) {
            val capturedImage = _uiState.value.lastCapturedImage
            if (capturedImage == null) {
                Log.e(TAG, "processData failed: image is null")
                _uiState.value = _uiState.value.copy(isProcessingImage = false)
                return@launch
            }

            val manager = modelManager
            if (manager == null) {
                Log.e(TAG, "ModelManager is not initialized, aborting.")
                _uiState.value = _uiState.value.copy(isProcessingImage = false, llmResponse = "Error: Model not initialized.")
                return@launch
            }

            if (!manager.isModelLoaded()) {
                val modelReady = manager.waitForModelReady()
                if (!modelReady) {
                    Log.e(TAG, "Model not ready, aborting.")
                    _uiState.value = _uiState.value.copy(isProcessingImage = false, llmResponse = "Error: Model not ready.")
                    return@launch
                }
            }
            
            manager.generateResponseStreaming(
                prompt = DUMMY_TRANSCRIPTION,
                imageBitmap = capturedImage
            ) { token, isComplete ->
                viewModelScope.launch(Dispatchers.Main) {
                    if (inferenceJob?.isActive == true) {
                        _uiState.value = _uiState.value.copy(
                            llmResponse = _uiState.value.llmResponse + token
                        )
                        if (isComplete) {
                            _uiState.value = _uiState.value.copy(isProcessingImage = false)
                        }
                    }
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
            isStreamingResponse = false
        )
    }

    fun forceSwitchToCameraScreen() {
        Log.w(TAG, "Force switching to camera screen.")
        cancelInference()
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
    }
} 