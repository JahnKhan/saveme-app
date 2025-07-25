package com.app.saveme.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.saveme.audio.AudioRecorder
import com.app.saveme.data.DUMMY_TRANSCRIPTION
import com.app.saveme.storage.FileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "CameraViewModel"

data class CameraUiState(
    val isCapturing: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val recordingDuration: Float = 0f,
    val audioAmplitude: Int = 0,
    val lastCapturedImage: Bitmap? = null,
    val statusMessage: String = "",
    val showPermissionDialog: Boolean = false,
    val hasAllPermissions: Boolean = false
)

class CameraViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    
    private val audioRecorder = AudioRecorder()
    private val fileManager = FileManager()
    
    fun updatePermissionStatus(hasPermissions: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasAllPermissions = hasPermissions,
            showPermissionDialog = !hasPermissions
        )
    }
    
    fun dismissPermissionDialog() {
        _uiState.value = _uiState.value.copy(showPermissionDialog = false)
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
                    
                    // Process data (first iteration: just show dummy transcription)
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
        // First iteration: Just show success with dummy transcription
        // Second iteration: This is where we'll integrate MediaPipe LLM
        
        Log.d(TAG, "Processing captured data...")
        Log.d(TAG, "Dummy transcription: $DUMMY_TRANSCRIPTION")
        
        _uiState.value = _uiState.value.copy(
            isCapturing = false,
            isRecordingAudio = false,
            recordingDuration = 0f,
            audioAmplitude = 0,
            statusMessage = "Capture completed! Transcription: \"$DUMMY_TRANSCRIPTION\""
        )
        
        // Clear status message after a delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            if (_uiState.value.statusMessage.contains("Capture completed")) {
                _uiState.value = _uiState.value.copy(statusMessage = "")
            }
        }
    }
    
    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = "")
    }
} 