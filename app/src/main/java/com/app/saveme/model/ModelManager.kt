package com.app.saveme.model

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.app.saveme.data.ModelConfig
import com.app.saveme.data.ModelDownloadStatus
import com.app.saveme.data.ModelImportStatus
import com.app.saveme.data.ModelState
import com.app.saveme.worker.ModelDownloadWorker
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.work.OutOfQuotaPolicy

private const val TAG = "ModelManager"
private const val MODEL_DOWNLOAD_WORK = "model_download_work"

data class LlmModelInstance(val engine: LlmInference, var session: LlmInferenceSession)

class ModelManager(private val context: Context) {
    
    private val workManager = WorkManager.getInstance(context)
    private var modelInstance: LlmModelInstance? = null
    
    private val _modelState = MutableStateFlow(ModelState.NOT_DOWNLOADED)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()
    
    private val _downloadStatus = MutableStateFlow(ModelDownloadStatus())
    val downloadStatus: StateFlow<ModelDownloadStatus> = _downloadStatus.asStateFlow()
    
    private val _importStatus = MutableStateFlow(ModelImportStatus())
    val importStatus: StateFlow<ModelImportStatus> = _importStatus.asStateFlow()
    
    init {
        checkModelStatus()
        checkOngoingDownloads()
        observeDownloadProgress()
    }
    
    private fun checkOngoingDownloads() {
        try {
            // Check if there are any ongoing download work requests
            val workInfos = workManager.getWorkInfosForUniqueWork(MODEL_DOWNLOAD_WORK).get()
            
            val ongoingWork = workInfos.filter { workInfo ->
                workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
            }
            
            if (ongoingWork.isNotEmpty()) {
                Log.d(TAG, "Found ${ongoingWork.size} ongoing download(s)")
                _modelState.value = ModelState.DOWNLOADING
                _downloadStatus.value = ModelDownloadStatus(isDownloading = true)
                return
            }
            
            // Also check for substantial temp files which might indicate an ongoing download
            val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            if (modelsDir.exists()) {
                val tempFiles = modelsDir.listFiles { file ->
                    file.name.endsWith(".tmp") && file.length() > 10 * 1024 * 1024 // > 10MB
                }
                
                if (!tempFiles.isNullOrEmpty()) {
                    val tempFile = tempFiles.first()
                    Log.d(TAG, "Found substantial temp file: ${tempFile.name} (${tempFile.length()} bytes)")
                    Log.d(TAG, "This might indicate an interrupted download - setting state to NOT_DOWNLOADED for retry")
                    // Don't set to DOWNLOADING state since WorkManager isn't running
                    // Let the user retry the download instead
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking ongoing downloads: ${e.message}", e)
        }
    }
    
    private fun checkModelStatus() {
        // If we're already downloading, don't change the state
        if (_modelState.value == ModelState.DOWNLOADING) {
            Log.d(TAG, "Download already in progress, skipping model file check")
            return
        }
        
        // Check if we have any model file in the models directory
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        
        if (modelsDir.exists()) {
            val existingModels = modelsDir.listFiles { file ->
                file.isFile && file.name.endsWith(ModelConfig.MODEL_FILE_EXTENSION)
            }
            
            if (!existingModels.isNullOrEmpty()) {
                // Found existing model(s), validate the first one
                val existingModel = existingModels.first()
                val modelName = existingModel.nameWithoutExtension
                
                Log.d(TAG, "Found model file: ${existingModel.absolutePath}")
                Log.d(TAG, "File size: ${existingModel.length()} bytes")
                
                // Validate the model file
                if (isValidModelFile(existingModel)) {
                    Log.d(TAG, "Model file validation passed")
                    
                    // Update configuration to use the existing model
                    ModelConfig.CURRENT_MODEL_NAME = modelName
                    
                    // Enhanced vision support detection
                    val supportsVision = modelName.contains("gemma-3n", ignoreCase = true) || 
                                       modelName.contains("vision", ignoreCase = true) ||
                                       modelName.contains("multimodal", ignoreCase = true) ||
                                       modelName.contains("E2B", ignoreCase = true) ||
                                       modelName.contains("E4B", ignoreCase = true)
                    
                    ModelConfig.SUPPORTS_VISION = supportsVision
                    
                    _modelState.value = ModelState.DOWNLOADED
                    Log.d(TAG, "Model ready: $modelName (vision: ${ModelConfig.SUPPORTS_VISION})")
                    return
                } else {
                    Log.w(TAG, "Model file validation failed, cleaning up corrupt file")
                    cleanupCorruptModel(existingModel)
                }
            }
        }
        
        // No existing valid model found
        _modelState.value = ModelState.NOT_DOWNLOADED
        Log.d(TAG, "No valid model found, needs to be downloaded or imported")
    }
    
    private fun isValidModelFile(modelFile: File): Boolean {
        try {
            Log.d(TAG, "Validating model file: ${modelFile.absolutePath}")
            
            // Basic file validation
            if (!modelFile.exists()) {
                Log.w(TAG, "Model file does not exist")
                return false
            }
            
            val fileSize = modelFile.length()
            Log.d(TAG, "Model file size: $fileSize bytes")
            
            if (fileSize < 1024 * 1024) { // Less than 1MB is definitely too small
                Log.w(TAG, "Model file too small: $fileSize bytes")
                return false
            }
            
            // Check if file is readable
            if (!modelFile.canRead()) {
                Log.w(TAG, "Model file is not readable")
                return false
            }
            
            // For gemma-3n model, expect around 3GB
            if (modelFile.name.contains("gemma-3n")) {
                val expectedSize = 3136226711L // Known size
                val sizeDifference = Math.abs(fileSize - expectedSize)
                val tolerance = expectedSize * 0.05 // 5% tolerance (same as download)
                
                Log.d(TAG, "Gemma-3n size validation - Expected: $expectedSize, Actual: $fileSize, Difference: $sizeDifference, Tolerance: $tolerance")
                
                if (sizeDifference > tolerance) {
                    Log.w(TAG, "Model file size mismatch. Expected: ~$expectedSize, Actual: $fileSize")
                    return false
                } else {
                    Log.d(TAG, "Gemma-3n size validation passed")
                }
            }
            
            // Try to read the first few bytes to ensure file is not corrupt
            try {
                modelFile.inputStream().use { stream ->
                    val buffer = ByteArray(1024)
                    val bytesRead = stream.read(buffer)
                    if (bytesRead <= 0) {
                        Log.w(TAG, "Cannot read from model file")
                        return false
                    }
                    Log.d(TAG, "Successfully read $bytesRead bytes from model file")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading model file: ${e.message}")
                return false
            }
            
            Log.d(TAG, "Model file validation passed completely")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating model file: ${e.message}", e)
            return false
        }
    }
    
    private fun cleanupCorruptModel(modelFile: File) {
        try {
            if (modelFile.exists()) {
                val deleted = modelFile.delete()
                Log.d(TAG, "Corrupt model file deletion: ${if (deleted) "successful" else "failed"}")
            }
            
            // Also clean up any partial downloads or temp files
            val modelsDir = modelFile.parentFile
            modelsDir?.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp") || file.name.endsWith(".partial")) {
                    file.delete()
                    Log.d(TAG, "Cleaned up temp file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up corrupt model: ${e.message}", e)
        }
    }
    
    private fun getModelFile(): File {
        // Use app's private external files directory
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        
        // Always return a file in the models directory using the current model name
        return File(modelsDir, "${ModelConfig.CURRENT_MODEL_NAME}${ModelConfig.MODEL_FILE_EXTENSION}")
    }
    
    suspend fun downloadModel(modelUrl: String? = null, modelName: String? = null) {
        val url = modelUrl ?: ModelConfig.CURRENT_MODEL_URL
        val name = modelName ?: ModelConfig.CURRENT_MODEL_NAME
        
        Log.d(TAG, "Starting model download: $name from $url")
        
        // Clean up any corrupt or partial files before starting
        cleanupPartialDownloads()
        
        // Update configuration if custom URL provided
        if (modelUrl != null && modelName != null) {
            ModelConfig.setCustomModel(name, url)
        }
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)  // Don't require charging
            .setRequiresDeviceIdle(false)  // Don't require device to be idle
            .build()
        
        val inputData = workDataOf(
            ModelDownloadWorker.KEY_MODEL_URL to url,
            ModelDownloadWorker.KEY_MODEL_NAME to name
        )
        
        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)  // Try expedited, fallback to normal
            .build()
        
        workManager.enqueueUniqueWork(
            MODEL_DOWNLOAD_WORK,
            ExistingWorkPolicy.REPLACE, // Replace any existing work
            downloadRequest
        )
        
        _modelState.value = ModelState.DOWNLOADING
    }
    
    private fun cleanupPartialDownloads() {
        try {
            val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            if (modelsDir.exists()) {
                modelsDir.listFiles()?.forEach { file ->
                    when {
                        file.name.endsWith(".tmp") -> {
                            file.delete()
                            Log.d(TAG, "Cleaned up temp file: ${file.name}")
                        }
                        file.name.endsWith(".partial") -> {
                            file.delete()
                            Log.d(TAG, "Cleaned up partial file: ${file.name}")
                        }
                        file.name.endsWith(ModelConfig.MODEL_FILE_EXTENSION) && file.length() < 1024 * 1024 * 50 -> {
                            // Delete any .task files smaller than 50MB (likely incomplete)
                            file.delete()
                            Log.d(TAG, "Cleaned up incomplete model file: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up partial downloads: ${e.message}", e)
        }
    }
    
    suspend fun importModel(uri: Uri, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _modelState.value = ModelState.IMPORTING
                _importStatus.value = ModelImportStatus(isImporting = true)
                
                Log.d(TAG, "Starting model import from: $uri")
                
                // Generate model name from filename
                val modelName = fileName.removeSuffix(ModelConfig.MODEL_FILE_EXTENSION)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val finalModelName = "${modelName}_imported_$timestamp"
                
                // Update configuration for imported model
                ModelConfig.setImportedModel(finalModelName, uri.toString())
                
                val destinationFile = getModelFile()
                
                // Copy file from URI to app storage
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var totalBytes = 0L
                        var bytesRead: Int
                        
                        // Get file size for progress tracking
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        val fileSize = cursor?.use {
                            if (it.moveToFirst()) {
                                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (sizeIndex != -1) it.getLong(sizeIndex) else -1L
                            } else -1L
                        } ?: -1L
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            
                            // Update progress
                            if (fileSize > 0) {
                                val progress = totalBytes.toFloat() / fileSize.toFloat()
                                _importStatus.value = ModelImportStatus(
                                    isImporting = true,
                                    progress = progress
                                )
                            }
                        }
                    }
                }
                
                _importStatus.value = ModelImportStatus(
                    isImporting = false,
                    progress = 1f,
                    isCompleted = true
                )
                _modelState.value = ModelState.DOWNLOADED
                
                Log.d(TAG, "Model imported successfully: ${destinationFile.absolutePath}")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import model", e)
                _importStatus.value = ModelImportStatus(
                    isImporting = false,
                    errorMessage = e.message ?: "Import failed"
                )
                _modelState.value = ModelState.ERROR
                false
            }
        }
    }
    
    private fun observeDownloadProgress() {
        workManager.getWorkInfosForUniqueWorkLiveData(MODEL_DOWNLOAD_WORK)
            .observeForever { workInfos ->
                val workInfo = workInfos?.firstOrNull()
                workInfo?.let { info ->
                    when (info.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = info.progress
                            val progressPercent = progress.getFloat(ModelDownloadWorker.KEY_PROGRESS, 0f)
                            val downloadedBytes = progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
                            val totalBytes = progress.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0L)
                            
                            _downloadStatus.value = ModelDownloadStatus(
                                isDownloading = true,
                                progress = progressPercent / 100f,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )
                            
                            Log.d(TAG, "Download progress: ${progressPercent.toInt()}% (${downloadedBytes / 1024 / 1024}MB / ${totalBytes / 1024 / 1024}MB)")
                        }
                        
                        WorkInfo.State.SUCCEEDED -> {
                            Log.d(TAG, "WorkManager reports download SUCCEEDED")
                            
                            _downloadStatus.value = ModelDownloadStatus(
                                isDownloading = false,
                                progress = 1f,
                                isCompleted = true
                            )
                            _modelState.value = ModelState.DOWNLOADED
                            
                            // Re-validate the model after successful download
                            checkModelStatus()
                            
                            Log.d(TAG, "Model download completed successfully, validation triggered")
                        }
                        
                        WorkInfo.State.FAILED -> {
                            val errorMessage = info.outputData.getString(ModelDownloadWorker.KEY_ERROR_MESSAGE)
                            
                            // Clean up any partial files after failure
                            cleanupPartialDownloads()
                            
                            _downloadStatus.value = ModelDownloadStatus(
                                isDownloading = false,
                                errorMessage = errorMessage ?: "Download failed"
                            )
                            _modelState.value = ModelState.ERROR
                            Log.e(TAG, "Model download failed: $errorMessage")
                            Log.d(TAG, "Cleaned up partial files after download failure")
                        }
                        
                        WorkInfo.State.CANCELLED -> {
                            // Handle cancelled downloads
                            cleanupPartialDownloads()
                            _downloadStatus.value = ModelDownloadStatus(
                                isDownloading = false,
                                errorMessage = "Download was cancelled"
                            )
                            _modelState.value = ModelState.NOT_DOWNLOADED
                            Log.d(TAG, "Model download was cancelled, cleaned up partial files")
                        }
                        
                        else -> {
                            // Handle other states if needed
                        }
                    }
                }
            }
    }
    
    suspend fun loadModel(): Boolean {
        if (_modelState.value == ModelState.LOADED) {
            Log.d(TAG, "Model already loaded")
            return true
        }
        
        if (_modelState.value == ModelState.INITIALIZING) {
            Log.d(TAG, "Model is already being initialized")
            return false
        }

        val modelFile = getModelFile()
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
            _modelState.value = ModelState.ERROR
            return false
        }

        return try {
            _modelState.value = ModelState.INITIALIZING  // Set initializing state first
            Log.d(TAG, "Initializing model from: ${modelFile.absolutePath}")
            Log.d(TAG, "Vision support: ${ModelConfig.SUPPORTS_VISION}")

            // Create LlmInference engine
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setMaxTopK(40)
                .setMaxNumImages(if (ModelConfig.SUPPORTS_VISION) 1 else 0) // Set max images based on vision support
                .build()

            val llmInference = LlmInference.createFromOptions(context, options)

            // Create session for handling text and images (like gallery project)
            val session = LlmInferenceSession.createFromOptions(
                llmInference,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTopP(0.9f)
                    .setTemperature(0.8f)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(ModelConfig.SUPPORTS_VISION) // Enable vision based on model support
                            .build()
                    )
                    .build()
            )

            modelInstance = LlmModelInstance(engine = llmInference, session = session)
            _modelState.value = ModelState.LOADED
            Log.d(TAG, "Model loaded successfully with vision support: ${ModelConfig.SUPPORTS_VISION}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            _modelState.value = ModelState.ERROR
            false
        }
    }
    
    suspend fun generateResponseStreaming(
        prompt: String,
        imageBitmap: Bitmap? = null,
        onTokenReceived: (token: String, isComplete: Boolean) -> Unit
    ): String? {
        if (_modelState.value != ModelState.LOADED || modelInstance == null) {
            Log.e(TAG, "Model not loaded")
            onTokenReceived("", true)
            return null
        }

        return try {
            // Always reset the session before a new inference to ensure a clean context.
            // This prevents the "Input is too long for the model to process" error.
            resetSession()

            val instance = modelInstance!!
            val currentSession = instance.session

            val fileManager = com.app.saveme.storage.FileManager()
            val contextText = fileManager.readContext(context)
            val fullPrompt = if (contextText != null) {
                "$contextText\n\n$prompt"
            } else {
                prompt
            }

            if (fullPrompt.trim().isNotEmpty()) {
                currentSession.addQueryChunk(fullPrompt)
            }

            if (ModelConfig.SUPPORTS_VISION && imageBitmap != null) {
                val image = BitmapImageBuilder(imageBitmap).build()
                currentSession.addImage(image)
            }

            suspendCancellableCoroutine<String> { continuation ->
                var fullResponse = ""
                val callback = { partialResult: String, done: Boolean ->
                    if (continuation.isActive) {
                        fullResponse += partialResult
                        onTokenReceived(partialResult, done)
                        if (done) {
                            continuation.resume(fullResponse)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    Log.d(TAG, "invokeOnCancellation: coroutine was cancelled.")
                }

                currentSession.generateResponseAsync(callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateResponseStreaming failed", e)
            onTokenReceived("Error: ${e.localizedMessage}", true) // Ensure UI is unlocked on error
            null
        }
    }

    fun stopInference() {
        Log.d(TAG, "Stopping ongoing inference...")

        // Prevent concurrent cancellation calls
        // This function is no longer needed as inference is blocking
        // The UI will handle screen switching and inference cancellation.
        // This function is kept for now, but its logic needs to be re-evaluated
        // if the UI is truly blocking inference.
    }

    fun isModelDownloaded(): Boolean {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)

        if (modelsDir.exists()) {
            val existingModels = modelsDir.listFiles { file ->
                file.isFile && file.name.endsWith(ModelConfig.MODEL_FILE_EXTENSION)
            }
            return !existingModels.isNullOrEmpty()
        }

        return false
    }

    fun isModelLoaded(): Boolean {
        return _modelState.value == ModelState.LOADED
    }
    
    fun resetSession() {
        if (modelInstance == null) {
            Log.d(TAG, "Model not initialized, skipping session reset.")
            return
        }
        Log.d(TAG, "Resetting session...")

        try {
            val instance = modelInstance!!
            // Close the old session. This will interrupt any ongoing generateResponseAsync call.
            instance.session.close()

            // Create a new session with the same engine and options
            val newSession = LlmInferenceSession.createFromOptions(
                instance.engine,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTopP(0.9f)
                    .setTemperature(0.8f)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(ModelConfig.SUPPORTS_VISION)
                            .build()
                    )
                    .build()
            )
            instance.session = newSession
            Log.d(TAG, "Session reset successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset session", e)
            _modelState.value = ModelState.ERROR
        }
    }
    
    suspend fun waitForModelReady(): Boolean {
        Log.d(TAG, "Waiting for model to be ready...")
        
        // Wait for model to be initialized (like gallery project)
        var attempts = 0
        val maxAttempts = 100 // 10 seconds max wait
        
        while (attempts < maxAttempts) {
            when (_modelState.value) {
                ModelState.LOADED -> {
                    Log.d(TAG, "Model is ready!")
                    return true
                }
                ModelState.ERROR -> {
                    Log.e(TAG, "Model is in error state")
                    return false
                }
                ModelState.NOT_DOWNLOADED -> {
                    Log.e(TAG, "Model is not downloaded")
                    return false
                }
                else -> {
                    // Still loading/initializing, wait
                    kotlinx.coroutines.delay(100)
                    attempts++
                }
            }
        }
        
        Log.e(TAG, "Timeout waiting for model to be ready")
        return false
    }
    
    fun setVisionSupport(enabled: Boolean) {
        Log.d(TAG, "Manually setting vision support to: $enabled")
        ModelConfig.setVisionSupport(enabled)
    }
    
    fun cleanup() {
        modelInstance?.let { instance ->
            try {
                instance.session.close()
                instance.engine.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
        modelInstance = null
    }

    fun validateAndCleanupModels() {
        try {
            Log.d(TAG, "Manual model validation and cleanup requested")
            
            // First clean up any obvious partial downloads
            cleanupPartialDownloads()
            
            // Then recheck model status which will validate existing models
            checkModelStatus()
            
            Log.d(TAG, "Model validation and cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during manual validation and cleanup: ${e.message}", e)
        }
    }
    
    fun debugModelDirectory(): String {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        val debug = StringBuilder()
        
        debug.append("=== Model Directory Debug ===\n")
        debug.append("Directory: ${modelsDir.absolutePath}\n")
        debug.append("Exists: ${modelsDir.exists()}\n")
        
        if (modelsDir.exists()) {
            val files = modelsDir.listFiles()
            debug.append("File count: ${files?.size ?: 0}\n")
            
            files?.forEach { file ->
                debug.append("File: ${file.name}\n")
                debug.append("  Size: ${file.length()} bytes (${file.length() / 1024 / 1024} MB)\n")
                debug.append("  Readable: ${file.canRead()}\n")
                debug.append("  Is Task File: ${file.name.endsWith(ModelConfig.MODEL_FILE_EXTENSION)}\n")
                
                if (file.name.endsWith(ModelConfig.MODEL_FILE_EXTENSION)) {
                    debug.append("  Valid: ${isValidModelFile(file)}\n")
                }
                debug.append("\n")
            }
        }
        
        debug.append("Current Model State: ${_modelState.value}\n")
        debug.append("Download Status: isDownloading=${_downloadStatus.value.isDownloading}, progress=${_downloadStatus.value.progress}\n")
        
        // Check WorkManager status
        try {
            val workInfos = workManager.getWorkInfosForUniqueWork(MODEL_DOWNLOAD_WORK).get()
            val activeWork = workInfos.filter { workInfo ->
                workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
            }
            debug.append("Active WorkManager jobs: ${activeWork.size}\n")
            activeWork.forEach { workInfo ->
                debug.append("  Job ${workInfo.id}: ${workInfo.state}\n")
            }
        } catch (e: Exception) {
            debug.append("WorkManager query error: ${e.message}\n")
        }
        
        debug.append("========================\n")
        
        val result = debug.toString()
        Log.d(TAG, result)
        return result
    }
    
    fun refreshDownloadStatus() {
        Log.d(TAG, "Refreshing download status...")
        checkOngoingDownloads()
        if (_modelState.value != ModelState.DOWNLOADING) {
            checkModelStatus()
        }
    }
    
    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_DOWNLOAD_WORK = "model_download_work"
    }
} 