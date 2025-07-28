package com.app.saveme.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.saveme.ui.components.CaptureButton
import com.app.saveme.ui.ProcessingPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    capturedImage: Bitmap?,
    llmResponse: String,
    userPrompt: String,
    isProcessing: Boolean,
    transcriptionStatus: String = "",
    isSpeaking: Boolean = false,
    isTranscribing: Boolean = false,
    isGeneratingResponse: Boolean = false,
    processingPhase: ProcessingPhase = ProcessingPhase.IDLE,
    onNewCaptureClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    onStopSpeaking: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    LaunchedEffect(llmResponse) {
        if (llmResponse.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    // Debug logging for streaming
    LaunchedEffect(llmResponse) {
        println("ChatScreen: llmResponse changed to length: ${llmResponse.length}")
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Analysis") },
                actions = {
                    when {
                        isTranscribing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Transcribing...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        isGeneratingResponse -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Generating...", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = onCancelClicked) {
                                    Text("Cancel")
                                }
                            }
                        }
                        isSpeaking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.VolumeUp,
                                    contentDescription = "Speaking",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("Speaking...", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = onStopSpeaking) {
                                    Icon(
                                        Icons.Rounded.Stop,
                                        contentDescription = "Stop Speaking",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CaptureButton(
                    onClick = onNewCaptureClicked,
                    icon = Icons.Rounded.PhotoCamera,
                    contentDescription = "New Capture",
                    enabled = !isProcessing && !isSpeaking && !isTranscribing && !isGeneratingResponse
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            capturedImage?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Captured Image",
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            
            // Transcription Status - only show when we have transcription
            if (transcriptionStatus.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "Transcription",
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                "Speech Recognition", 
                                style = MaterialTheme.typography.labelSmall, 
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = transcriptionStatus, 
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            
            // AI Response - show processing state or actual response
            when {
                isTranscribing -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Mic,
                                    contentDescription = "Transcribing",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("AI Assistant", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Transcribing your speech...", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                }
                isGeneratingResponse && llmResponse.isEmpty() -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Psychology,
                                    contentDescription = "Thinking",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("AI Assistant", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Analyzing image and generating response...", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                }
                llmResponse.isNotEmpty() -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("AI Assistant", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                if (isSpeaking) {
                                    Icon(
                                        Icons.Rounded.VolumeUp,
                                        contentDescription = "Speaking",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                // Add streaming indicator
                                if (llmResponse.isNotEmpty()) {
                                    Text(
                                        "(${llmResponse.length} chars)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(llmResponse, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
} 