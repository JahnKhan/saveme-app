package com.app.saveme.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.app.saveme.data.ModelDownloadStatus
import com.app.saveme.data.ModelImportStatus
import com.app.saveme.data.ModelState
import kotlin.math.roundToInt

@Composable
fun ModelDownloadScreen(
    modelState: ModelState,
    downloadStatus: ModelDownloadStatus,
    importStatus: ModelImportStatus,
    onStartDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onImportModel: (Uri, String) -> Unit,
    loadDigitalTwin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // State to track if user is in import mode
    var showImportMode by remember { mutableStateOf(false) }
    var showTokenMode by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Extract filename from URI
            val fileName = selectedUri.lastPathSegment?.substringAfterLast('/')
                ?: "imported_model.task"
            onImportModel(selectedUri, fileName)
        }
        // If uri is null (user cancelled), reset to main options
        showImportMode = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (modelState) {
            ModelState.DOWNLOADING -> {
                // Clean download screen with video
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Digital Twin Loading",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Video player for download process
                        val context = LocalContext.current
                        AndroidView(
                            factory = { context ->
                                PlayerView(context).apply {
                                    // Hide all player controls
                                    useController = false
                                    player = ExoPlayer.Builder(context).build().apply {
                                        // Copy asset to temporary file
                                        val tempFile = java.io.File(context.cacheDir, "digitaltwinloading.mp4")
                                        if (!tempFile.exists()) {
                                            context.assets.open("digitaltwinloading.mp4").use { input ->
                                                tempFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                        }
                                        val mediaItem = MediaItem.fromUri(Uri.fromFile(tempFile))
                                        setMediaItem(mediaItem)
                                        prepare()
                                        playWhenReady = true
                                        repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        CircularProgressIndicator(
                            progress = { downloadStatus.progress },
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LinearProgressIndicator(
                            progress = { downloadStatus.progress },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${(downloadStatus.progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        if (downloadStatus.totalBytes > 0) {
                            val downloadedMB = downloadStatus.downloadedBytes / (1024 * 1024)
                            val totalMB = downloadStatus.totalBytes / (1024 * 1024)

                            Text(
                                text = "$downloadedMB MB / $totalMB MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            else -> {
                // Original card for all other states
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Download Model",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "AI Model Required",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Download the Gemma 3n model (with vision support) or import your own .task model file to enable AI-powered analysis.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        when (modelState) {
                            ModelState.DOWNLOADING -> {
                                // This case is handled in the outer when statement
                                // No content needed here
                            }
                            
                            ModelState.NOT_DOWNLOADED -> {
                                if (showImportMode) {
                                    // Import mode - show instructions and file picker option
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Import Model File",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = "Select a .task model file from your device storage. Make sure the file is a compatible LLM model.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(24.dp))

                                        Button(
                                            onClick = { filePickerLauncher.launch("*/*") },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Rounded.Upload,
                                                contentDescription = "Select File",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Select File from Device")
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        OutlinedButton(
                                            onClick = { showImportMode = false },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Cancel - Back to Download")
                                        }
                                    }
                                } else if (showTokenMode) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        OutlinedTextField(
                                            value = token,
                                            onValueChange = { token = it },
                                            label = { Text("Enter Token") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                if (token.isNotEmpty()) {
                                                    loadDigitalTwin(token)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = token.isNotEmpty()
                                        ) {
                                            Text("Load Digital Twin")
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = { showTokenMode = false },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                } else {
                                    // Main options - download or import
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Button(
                                            onClick = onStartDownload,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Download Model (~3GB)")
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            text = "OR",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        OutlinedButton(
                                            onClick = { showImportMode = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Rounded.Upload,
                                                contentDescription = "Import Model",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Import Model File (.task)")
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = { showTokenMode = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Load Digital Twin")
                                        }
                                    }
                                }
                            }

                            ModelState.IMPORTING -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        progress = { importStatus.progress },
                                        modifier = Modifier.size(48.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    LinearProgressIndicator(
                                        progress = { importStatus.progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Importing Model...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            ModelState.DOWNLOADED -> {
                                Text(
                                    text = "✓ Model Ready",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            ModelState.LOADING -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "Loading Model...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            ModelState.INITIALIZING -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "Initializing Model...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            ModelState.LOADED -> {
                                Text(
                                    text = "✓ Model Ready",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            ModelState.ERROR -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val errorMessage = downloadStatus.errorMessage ?: importStatus.errorMessage

                                    Text(
                                        text = "❌ Error",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )

                                    if (errorMessage != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = errorMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onRetryDownload,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Retry Download")
                                        }

                                        OutlinedButton(
                                            onClick = { filePickerLauncher.launch("*/*") },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Import File")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} 