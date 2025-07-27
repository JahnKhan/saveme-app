package com.app.saveme.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    capturedImage: Bitmap?,
    llmResponse: String,
    userPrompt: String,
    isProcessing: Boolean,
    onNewCaptureClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    LaunchedEffect(llmResponse) {
        if (llmResponse.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Analysis") },
                actions = {
                    if (isProcessing) {
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
                    enabled = !isProcessing
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
            
            // User Prompt
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("You", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = userPrompt.ifEmpty { "What do you see?" }, 
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // AI Response
            if (isProcessing || llmResponse.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("AI Assistant", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        if (isProcessing && llmResponse.isEmpty()) {
                             Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Thinking...", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                            }
                        } else {
                            Text(llmResponse, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
} 