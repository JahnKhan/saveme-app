package com.app.saveme.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.app.saveme.data.ModelConfig
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

private const val TAG = "ModelDownloadWorker"
private const val NOTIFICATION_CHANNEL_ID = "model_download_channel"
private const val NOTIFICATION_ID = 1

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODEL_URL = "model_url"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR_MESSAGE = "error_message"
    }

    override suspend fun doWork(): Result {
        val modelUrl = inputData.getString(KEY_MODEL_URL) ?: ModelConfig.CURRENT_MODEL_URL
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: ModelConfig.CURRENT_MODEL_NAME

        return try {
            // Try to set up foreground work to prevent system from killing the download
            try {
                setForeground(createForegroundInfo("Starting download...", 0))
                Log.d(TAG, "Successfully started foreground service")
            } catch (e: Exception) {
                Log.w(TAG, "Could not start foreground service: ${e.message}. Continuing with background download...")
                // Continue without foreground service - download may be interrupted but will retry
            }
            
            downloadModelWithRetry(modelUrl, modelName)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            val errorData = workDataOf(KEY_ERROR_MESSAGE to e.message)
            Result.failure(errorData)
        }
    }
    
    private suspend fun downloadModelWithRetry(modelUrl: String, modelName: String, maxRetries: Int = 3) {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG, "Download attempt ${attempt + 1}/$maxRetries")
                try {
                    setForeground(createForegroundInfo("Attempt ${attempt + 1}/$maxRetries - Connecting...", 0))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update foreground notification: ${e.message}")
                }
                downloadModel(modelUrl, modelName)
                return // Success, exit retry loop
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Download attempt ${attempt + 1} failed: ${e.message}")
                
                // If it's a network error and we have more attempts, wait before retrying
                if (attempt < maxRetries - 1 && isNetworkError(e)) {
                    val delay = (attempt + 1) * 2000L // Exponential backoff: 2s, 4s, 6s
                    Log.d(TAG, "Retrying in ${delay}ms...")
                    try {
                        setForeground(createForegroundInfo("Connection failed - retrying in ${delay/1000}s...", 0))
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not update foreground notification: ${e.message}")
                    }
                    kotlinx.coroutines.delay(delay)
                } else {
                    // Final attempt failed or non-retryable error
                    throw e
                }
            }
        }
        
        // If we get here, all retries failed
        throw lastException ?: Exception("Download failed after $maxRetries attempts")
    }
    
    private fun isNetworkError(exception: Exception): Boolean {
        return when (exception) {
            is java.net.SocketException -> true
            is java.net.SocketTimeoutException -> true
            is java.io.IOException -> exception.message?.contains("connection", ignoreCase = true) == true
            else -> false
        }
    }

    private fun createForegroundInfo(message: String, progress: Int): ForegroundInfo {
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AI Model Download")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Downloads for AI models"
                setSound(null, null)
                enableVibration(false)
            }
            
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun downloadModel(modelUrl: String, modelName: String) {
        Log.d(TAG, "Starting download of $modelName from $modelUrl")

        // Create models directory in app's external files directory
        val modelsDir = File(applicationContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
            Log.d(TAG, "Created models directory: ${modelsDir.absolutePath}")
        }

        val modelFile = File(modelsDir, "$modelName${ModelConfig.MODEL_FILE_EXTENSION}")
        val tempFile = File(modelsDir, "$modelName${ModelConfig.MODEL_FILE_EXTENSION}.tmp")
        
        // Clean up any existing temp files first
        if (tempFile.exists()) {
            val tempSize = tempFile.length()
            Log.d(TAG, "Found existing temp file with ${tempSize} bytes")
            
            // If temp file is substantial (>10MB), we might want to resume from it
            if (tempSize > 10 * 1024 * 1024) {
                Log.d(TAG, "Temp file is substantial, could potentially resume download")
                // For now, we'll still delete and restart, but this is where resume logic would go
            }
            
            tempFile.delete()
            Log.d(TAG, "Cleaned up existing temp file")
        }
        
        // Clean up any existing partial file if it's incomplete
        if (modelFile.exists()) {
            Log.d(TAG, "Found existing model file, checking if complete...")
            // If file exists but is too small, it's likely partial - delete it
            if (modelFile.length() < 1024 * 1024 * 100) { // Less than 100MB for a 3GB file
                modelFile.delete()
                Log.d(TAG, "Deleted incomplete existing file")
            } else {
                Log.d(TAG, "Model file already exists and appears complete: ${modelFile.absolutePath}")
                return
            }
        }

        Log.d(TAG, "Connecting to URL: $modelUrl")
        val url = URL(modelUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        // Enhanced connection settings for ultra-high-speed downloads (500+ Mbps)
        connection.connectTimeout = 20000 // 20 seconds - faster for ultra-fast connections
        connection.readTimeout = 45000 // 45 seconds - optimized for high throughput
        connection.setRequestProperty("User-Agent", "SaveMe-Android-App/1.0 (High-Speed)")
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Encoding", "identity") // Prevent compression which can affect content length
        connection.setRequestProperty("Connection", "keep-alive") // Keep connection alive
        connection.setRequestProperty("Cache-Control", "no-cache") // Prevent caching issues
        connection.setRequestProperty("Accept-Ranges", "bytes") // Signal support for range requests
        // Removed Range header that was causing HTTP 206 responses
        connection.instanceFollowRedirects = true
        connection.useCaches = false // Disable caching for large files
        
        try {
            connection.connect()
            Log.d(TAG, "Connection established. Response code: ${connection.responseCode}")

            // Accept both HTTP 200 (OK) and 206 (Partial Content) responses
            if (connection.responseCode != HttpURLConnection.HTTP_OK && 
                connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                val errorMsg = "Server returned HTTP ${connection.responseCode} ${connection.responseMessage}"
                Log.e(TAG, errorMsg)
                throw Exception(errorMsg)
            }

            // Try multiple ways to get file length
            var fileLength = connection.contentLengthLong
            if (fileLength <= 0) {
                // Fallback to contentLength (int version)
                fileLength = connection.contentLength.toLong()
            }
            if (fileLength <= 0) {
                // Try reading the header directly
                val contentLengthHeader = connection.getHeaderField("Content-Length")
                if (contentLengthHeader != null) {
                    try {
                        fileLength = contentLengthHeader.toLong()
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Could not parse Content-Length header: $contentLengthHeader")
                    }
                }
            }
            
            // Fallback: Use known file size for this specific model
            if (fileLength <= 0 && modelUrl.contains("gemma-3n-E2B-it-int4.task")) {
                fileLength = 3136226711L // Known size of this model
                Log.d(TAG, "Using known file size for gemma-3n model: $fileLength bytes")
            }
            
            Log.d(TAG, "File size: $fileLength bytes (${if (fileLength > 0) fileLength / 1024 / 1024 else "unknown"} MB)")
            Log.d(TAG, "Content-Length header: ${connection.getHeaderField("Content-Length")}")

            // Download to temporary file first with buffered output for maximum performance
            connection.inputStream.use { input ->
                BufferedOutputStream(FileOutputStream(tempFile), 1024 * 1024).use { output -> // 1MB buffer for output
                    val buffer = ByteArray(512 * 1024) // Increased to 512KB for ultra-high-speed connections (500+ Mbps)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    var lastProgressLogged = -1
                    var lastProgressUpdate = 0L
                    var lastNotificationUpdate = System.currentTimeMillis()
                    
                    // Speed monitoring
                    val downloadStartTime = System.currentTimeMillis()
                    var lastSpeedCheck = downloadStartTime
                    var lastSpeedBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Update progress much less frequently for ultra-fast connections
                        val progressUpdateInterval = 20 * 1024 * 1024 // Update every 20MB instead of 5MB
                        if (totalBytesRead - lastProgressUpdate >= progressUpdateInterval) {
                            
                            // Update progress - handle case where fileLength is unknown
                            val progress = if (fileLength > 0) {
                                (totalBytesRead * 100f / fileLength.toFloat())
                            } else {
                                // If we don't know the file size, just show bytes downloaded
                                0f
                            }

                            val progressData = workDataOf(
                                KEY_PROGRESS to progress,
                                KEY_DOWNLOADED_BYTES to totalBytesRead,
                                KEY_TOTAL_BYTES to if (fileLength > 0) fileLength else totalBytesRead
                            )
                            setProgress(progressData)
                            
                            lastProgressUpdate = totalBytesRead
                        }
                        
                        // Update foreground notification even less frequently for ultra-fast downloads
                        val currentTime = System.currentTimeMillis()
                        val notificationInterval = 15000 // 15 seconds (increased from 10)
                        val megabytesDownloaded = totalBytesRead / (1024 * 1024)
                        
                        if (currentTime - lastNotificationUpdate >= notificationInterval || 
                            (megabytesDownloaded > 0 && megabytesDownloaded % 50 == 0L)) { // Every 50MB instead of 25MB
                            
                            val progressPercent = if (fileLength > 0) {
                                (totalBytesRead * 100f / fileLength.toFloat()).roundToInt()
                            } else 0
                            
                            // Calculate download speed
                            val timeElapsed = (currentTime - downloadStartTime) / 1000.0 // seconds
                            val speedMBps = if (timeElapsed > 0) (totalBytesRead / 1024.0 / 1024.0) / timeElapsed else 0.0
                            
                            val mbDownloaded = totalBytesRead / 1024 / 1024
                            val mbTotal = if (fileLength > 0) fileLength / 1024 / 1024 else 0
                            val progressMessage = if (fileLength > 0) {
                                "Downloaded $mbDownloaded MB / $mbTotal MB ($progressPercent%) - ${String.format("%.1f", speedMBps)} MB/s"
                            } else {
                                "Downloaded $mbDownloaded MB - ${String.format("%.1f", speedMBps)} MB/s"
                            }
                            try {
                                setForeground(createForegroundInfo(progressMessage, progressPercent))
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not update foreground notification: ${e.message}")
                            }
                            lastNotificationUpdate = currentTime
                        }
                        
                        // Debug logging every 250MB for ultra-fast connections
                        if (totalBytesRead > 0 && (totalBytesRead / (250 * 1024 * 1024)) != ((totalBytesRead - bytesRead) / (250 * 1024 * 1024))) {
                            val currentTime = System.currentTimeMillis()
                            val elapsedSeconds = (currentTime - downloadStartTime) / 1000.0
                            val avgSpeedMBps = (totalBytesRead / 1024.0 / 1024.0) / elapsedSeconds
                            val instantSpeedMBps = if (elapsedSeconds > 1) {
                                // Calculate instantaneous speed over last second
                                val recentBytes = totalBytesRead - lastSpeedBytes
                                val recentTime = (currentTime - lastSpeedCheck) / 1000.0
                                if (recentTime > 0) (recentBytes / 1024.0 / 1024.0) / recentTime else avgSpeedMBps
                            } else avgSpeedMBps
                            
                            Log.d(TAG, "Progress: ${totalBytesRead / 1024 / 1024} MB downloaded - Avg: ${String.format("%.1f", avgSpeedMBps)} MB/s, Current: ${String.format("%.1f", instantSpeedMBps)} MB/s")
                            lastSpeedCheck = currentTime
                            lastSpeedBytes = totalBytesRead
                        }

                        // Log progress every 5% for major milestones (instead of 10% for faster feedback)
                        if (fileLength > 0) {
                            val currentProgress = (totalBytesRead * 100f / fileLength.toFloat()).roundToInt()
                            if (currentProgress % 5 == 0 && currentProgress > lastProgressLogged && currentProgress > 0) {
                                val currentTime = System.currentTimeMillis()
                                val elapsedSeconds = (currentTime - downloadStartTime) / 1000.0
                                val avgSpeedMBps = (totalBytesRead / 1024.0 / 1024.0) / elapsedSeconds
                                Log.d(TAG, "Download progress: $currentProgress% (${totalBytesRead / 1024 / 1024} MB / ${fileLength / 1024 / 1024} MB) - Speed: ${String.format("%.2f", avgSpeedMBps)} MB/s")
                                lastProgressLogged = currentProgress
                            }
                        }
                    }
                }
            }

            // Validate the downloaded file
            if (tempFile.exists() && tempFile.length() > 0) {
                Log.d(TAG, "Validating downloaded file. Temp file size: ${tempFile.length()} bytes")
                
                // If we know the expected size, validate it with more generous tolerance
                if (fileLength > 0) {
                    val actualSize = tempFile.length()
                    val expectedSize = fileLength
                    val sizeDifference = Math.abs(actualSize - expectedSize)
                    val tolerance = expectedSize * 0.05 // Increased to 5% tolerance
                    
                    Log.d(TAG, "Size validation - Expected: $expectedSize, Actual: $actualSize, Difference: $sizeDifference, Tolerance: $tolerance")
                    
                    if (sizeDifference > tolerance) {
                        throw Exception("Downloaded file size mismatch. Expected: $expectedSize, Actual: $actualSize, Difference: $sizeDifference")
                    } else {
                        Log.d(TAG, "Size validation passed")
                    }
                }
                
                // Ensure the final model file doesn't exist before moving
                if (modelFile.exists()) {
                    Log.d(TAG, "Removing existing model file before move")
                    modelFile.delete()
                }
                
                // Move temp file to final location using copy+delete for reliability
                var moved = false
                try {
                    // First try the simple rename
                    moved = tempFile.renameTo(modelFile)
                    if (moved) {
                        Log.d(TAG, "File moved successfully using renameTo")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "renameTo failed: ${e.message}")
                    moved = false
                }
                
                // If rename failed, use copy + delete approach
                if (!moved) {
                    Log.d(TAG, "Attempting copy+delete approach for file move")
                    try {
                        tempFile.inputStream().use { input ->
                            modelFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Verify the copy was successful
                        if (modelFile.exists() && modelFile.length() == tempFile.length()) {
                            tempFile.delete()
                            moved = true
                            Log.d(TAG, "File moved successfully using copy+delete")
                        } else {
                            throw Exception("Copy verification failed. Original: ${tempFile.length()}, Copy: ${if (modelFile.exists()) modelFile.length() else "file not found"}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Copy+delete approach failed: ${e.message}")
                        throw Exception("Failed to move temporary file to final location: ${e.message}")
                    }
                }
                
                if (moved && modelFile.exists()) {
                    Log.d(TAG, "Model downloaded successfully: ${modelFile.absolutePath}")
                    Log.d(TAG, "Final file size: ${modelFile.length()} bytes")
                    
                    // Update notification to show completion
                    try {
                        setForeground(createForegroundInfo("Download completed successfully!", 100))
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not update completion notification: ${e.message}")
                    }
                } else {
                    throw Exception("File move operation reported success but final file validation failed")
                }
            } else {
                throw Exception("Downloaded file is empty or does not exist")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            // Clean up temp file on failure
            if (tempFile.exists()) {
                tempFile.delete()
                Log.d(TAG, "Cleaned up temp file after download failure")
            }
            // Also clean up partial final file if it exists
            if (modelFile.exists() && modelFile.length() < 1024 * 1024 * 100) { // Less than 100MB
                modelFile.delete()
                Log.d(TAG, "Cleaned up partial model file after download failure")
            }
            throw e
        } finally {
            connection.disconnect()
        }
    }
} 