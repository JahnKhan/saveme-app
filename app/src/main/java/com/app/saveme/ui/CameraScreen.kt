package com.app.saveme.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.saveme.data.ModelState
import com.app.saveme.ui.components.AudioRecordingIndicator
import com.app.saveme.ui.components.CameraPreview
import com.app.saveme.ui.components.CaptureButton
import com.app.saveme.ui.components.ChatScreen
import com.app.saveme.ui.components.ModelDownloadScreen
import com.app.saveme.utils.PermissionUtils

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var captureFunction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    // Initialize model manager
    LaunchedEffect(Unit) {
        viewModel.initializeModelManager(context)
        // Refresh download status to sync with any ongoing downloads
        viewModel.refreshDownloadStatus()
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasAllPermissions = permissions.values.all { it }
        viewModel.updatePermissionStatus(hasAllPermissions)
    }
    
    // Check permissions on first composition
    LaunchedEffect(Unit) {
        val hasPermissions = PermissionUtils.hasAllPermissions(context)
        if (hasPermissions) {
            viewModel.updatePermissionStatus(true)
        } else {
            permissionLauncher.launch(PermissionUtils.REQUIRED_PERMISSIONS)
        }
    }
    
    // Show status messages
    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(uiState.statusMessage)
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        when {
            // Check if we should show model download screen
            !uiState.hasAllPermissions || 
            uiState.modelState in listOf(ModelState.NOT_DOWNLOADED, ModelState.DOWNLOADING, ModelState.IMPORTING, ModelState.ERROR) -> {
                
                if (!uiState.hasAllPermissions) {
                    // Permission message
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Camera and microphone permissions are required",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Model download screen
                    ModelDownloadScreen(
                        modelState = uiState.modelState,
                        downloadStatus = uiState.downloadStatus,
                        importStatus = uiState.importStatus,
                        onStartDownload = { viewModel.startModelDownload() },
                        onRetryDownload = { viewModel.retryModelDownload() },
                        onImportModel = { uri, fileName ->
                            viewModel.importModel(uri, fileName)
                        }
                    )
                }
            }
            
            // Model is initializing - show loading screen
            uiState.modelState == ModelState.INITIALIZING -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Initializing AI model...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Please wait, this may take a moment",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            else -> {
                // Main app interface based on current screen
                when (uiState.currentScreen) {
                    AppScreen.CAMERA -> {
                        // Camera interface
                        CameraPreview(
                            onImageCaptured = { bitmap ->
                                viewModel.onPhotoCaptured(context, bitmap)
                            },
                            onCaptureReady = { captureFunc ->
                                captureFunction = captureFunc
                            }
                        )
                        
                        // Show captured image as background during audio recording
                        if (uiState.isRecordingAudio) {
                            val capturedImage = uiState.lastCapturedImage
                            if (capturedImage != null) {
                                Image(
                                    bitmap = capturedImage.asImageBitmap(),
                                    contentDescription = "Captured Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Add semi-transparent overlay to make the recording indicators more visible
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f))
                                )
                            }
                        }
                        
                        // Audio recording indicator
                        AudioRecordingIndicator(
                            isRecording = uiState.isRecordingAudio,
                            duration = uiState.recordingDuration,
                            amplitude = uiState.audioAmplitude,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        
                        // Capture button
                        CaptureButton(
                            onClick = {
                                // Don't allow capture if already processing
                                if (uiState.isProcessingImage) {
                                    return@CaptureButton
                                }
                                
                                if (uiState.isCapturing) {
                                    // Stop audio recording if in progress
                                    if (uiState.isRecordingAudio) {
                                        viewModel.stopAudioRecording(context)
                                    }
                                } else {
                                    // Capture photo
                                    captureFunction?.invoke()
                                }
                            },
                            icon = if (uiState.isRecordingAudio) Icons.Rounded.Stop else Icons.Rounded.PhotoCamera,
                            contentDescription = if (uiState.isRecordingAudio) "Stop Recording" else "Capture",
                            isRecording = uiState.isRecordingAudio,
                            enabled = !uiState.isProcessingImage, // Disable when processing
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(32.dp)
                        )
                    }
                    
                    AppScreen.CHAT -> {
                        ChatScreen(
                            capturedImage = uiState.lastCapturedImage,
                            llmResponse = uiState.llmResponse,
                            isProcessing = uiState.isProcessingImage,
                            onNewCaptureClicked = { viewModel.switchToCameraScreen() },
                            onCancelClicked = { viewModel.cancelInference() }
                        )
                    }
                }
            }
        }
        
        // Snackbar for status messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(bottom = 120.dp)
        )
    }
} 