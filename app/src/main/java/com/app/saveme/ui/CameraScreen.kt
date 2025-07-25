package com.app.saveme.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.saveme.ui.components.AudioRecordingIndicator
import com.app.saveme.ui.components.CameraPreview
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
        if (uiState.hasAllPermissions) {
            // Camera preview (show only when not recording audio)
            if (!uiState.isRecordingAudio) {
                CameraPreview(
                    onImageCaptured = { bitmap ->
                        viewModel.onPhotoCaptured(context, bitmap)
                    },
                    onCaptureReady = { captureFunc ->
                        captureFunction = captureFunc
                    }
                )
            }
            
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
            FloatingActionButton(
                onClick = {
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .size(80.dp)
                    .border(
                        4.dp,
                        Color.White,
                        CircleShape
                    ),
                containerColor = if (uiState.isCapturing) Color.Red else MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = if (uiState.isRecordingAudio) Icons.Rounded.Stop else Icons.Rounded.PhotoCamera,
                    contentDescription = if (uiState.isRecordingAudio) "Stop Recording" else "Capture",
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            // Show permission required message
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Camera and microphone permissions are required to use this app.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        
        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { data ->
            Snackbar(
                snackbarData = data,
                modifier = Modifier.padding(bottom = 120.dp)
            )
        }
    }
    
    // Permission dialog
    if (uiState.showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionDialog() },
            title = { Text("Permissions Required") },
            text = { 
                Text("This app needs camera and microphone permissions to function properly. Please grant the required permissions.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissPermissionDialog()
                        permissionLauncher.launch(PermissionUtils.REQUIRED_PERMISSIONS)
                    }
                ) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissPermissionDialog() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
} 