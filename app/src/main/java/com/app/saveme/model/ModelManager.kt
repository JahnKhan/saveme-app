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

private const val TAG = "ModelManager"
private const val MODEL_DOWNLOAD_WORK = "model_download_work"

data class LlmModelInstance(val engine: LlmInference)

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
        observeDownloadProgress()
    }
    
    private fun checkModelStatus() {
        // Check if we have any model file in the models directory
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        
        if (modelsDir.exists()) {
            val existingModels = modelsDir.listFiles { file ->
                file.isFile && file.name.endsWith(ModelConfig.MODEL_FILE_EXTENSION)
            }
            
            if (!existingModels.isNullOrEmpty()) {
                // Found existing model(s), use the first one
                val existingModel = existingModels.first()
                val modelName = existingModel.nameWithoutExtension
                
                Log.d(TAG, "Detected model name: '$modelName'")
                
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
                Log.d(TAG, "Found existing model: ${existingModel.absolutePath}")
                Log.d(TAG, "Model name: '$modelName'")
                Log.d(TAG, "Model vision support: ${ModelConfig.SUPPORTS_VISION}")
                return
            }
        }
        
        // No existing model found
        _modelState.value = ModelState.NOT_DOWNLOADED
        Log.d(TAG, "No existing model found, needs to be downloaded or imported")
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
    
    fun downloadModel(modelUrl: String? = null, modelName: String? = null) {
        val url = modelUrl ?: ModelConfig.CURRENT_MODEL_URL
        val name = modelName ?: ModelConfig.CURRENT_MODEL_NAME
        
        Log.d(TAG, "Starting model download: $name from $url")
        
        // Update configuration if custom URL provided
        if (modelUrl != null && modelName != null) {
            ModelConfig.setCustomModel(name, url)
        }
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val inputData = workDataOf(
            ModelDownloadWorker.KEY_MODEL_URL to url,
            ModelDownloadWorker.KEY_MODEL_NAME to name
        )
        
        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()
        
        workManager.enqueueUniqueWork(
            MODEL_DOWNLOAD_WORK,
            ExistingWorkPolicy.REPLACE,
            downloadRequest
        )
        
        _modelState.value = ModelState.DOWNLOADING
        _downloadStatus.value = ModelDownloadStatus(isDownloading = true)
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
                            
                            Log.d(TAG, "Download progress: ${progressPercent.toInt()}%")
                        }
                        
                        WorkInfo.State.SUCCEEDED -> {
                            _downloadStatus.value = ModelDownloadStatus(
                                isDownloading = false,
                                progress = 1f,
                                isCompleted = true
                            )
                            _modelState.value = ModelState.DOWNLOADED
                            Log.d(TAG, "Model download completed successfully")
                        }
                        
                        WorkInfo.State.FAILED -> {
                            val errorMessage = info.outputData.getString(ModelDownloadWorker.KEY_ERROR_MESSAGE)
                            _downloadStatus.value = ModelDownloadStatus(
                                isDownloading = false,
                                errorMessage = errorMessage ?: "Download failed"
                            )
                            _modelState.value = ModelState.ERROR
                            Log.e(TAG, "Model download failed: $errorMessage")
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
        
        val modelFile = getModelFile()
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
            _modelState.value = ModelState.ERROR
            return false
        }
        
        return try {
            _modelState.value = ModelState.LOADING
            Log.d(TAG, "Loading model from: ${modelFile.absolutePath}")
            Log.d(TAG, "Vision support: ${ModelConfig.SUPPORTS_VISION}")
            
            // Create LlmInference engine
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setMaxTopK(40)
                .setMaxNumImages(if (ModelConfig.SUPPORTS_VISION) 1 else 0) // Set max images based on vision support
                .build()
            
            val llmInference = LlmInference.createFromOptions(context, options)
            
            modelInstance = LlmModelInstance(engine = llmInference)
            _modelState.value = ModelState.LOADED
            Log.d(TAG, "Model loaded successfully with vision support: ${ModelConfig.SUPPORTS_VISION}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            _modelState.value = ModelState.ERROR
            false
        }
    }
    
    suspend fun generateResponse(prompt: String, imageBitmap: Bitmap? = null): String? {
        if (_modelState.value != ModelState.LOADED || modelInstance == null) {
            Log.e(TAG, "Model not loaded")
            return null
        }
        
        return try {
            Log.d(TAG, "Generating response for prompt: $prompt")
            Log.d(TAG, "Vision support: ${ModelConfig.SUPPORTS_VISION}, Image provided: ${imageBitmap != null}")
            
            val engine = modelInstance!!.engine
            
            // Create a fresh session for each request to avoid timestamp issues
            val session = LlmInferenceSession.createFromOptions(
                engine,
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
            
            // Add text query
            if (prompt.trim().isNotEmpty()) {
                session.addQueryChunk(prompt)
            }
            
            // Add image only if both model supports vision and image is provided
            if (ModelConfig.SUPPORTS_VISION && imageBitmap != null) {
                val image = BitmapImageBuilder(imageBitmap).build()
                session.addImage(image)
                Log.d(TAG, "Added image to session")
            } else if (imageBitmap != null && !ModelConfig.SUPPORTS_VISION) {
                Log.w(TAG, "Image provided but model doesn't support vision - skipping image")
            }
            
            // Generate response asynchronously
            val result = suspendCancellableCoroutine<String> { continuation ->
                val callback = { partialResult: String, done: Boolean ->
                    if (done && continuation.isActive) {
                        continuation.resume(partialResult)
                    }
                }
                
                session.generateResponseAsync(callback)
            }
            
            // Close the session after use
            session.close()
            
            Log.d(TAG, "Generated response: $result")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate response", e)
            null
        }
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
    
    fun setVisionSupport(enabled: Boolean) {
        Log.d(TAG, "Manually setting vision support to: $enabled")
        ModelConfig.setVisionSupport(enabled)
    }
    
    fun cleanup() {
        modelInstance?.let { instance ->
            try {
                // The session is closed by the generateResponseAsync callback
                // instance.session.close()
                instance.engine.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
        modelInstance = null
    }
} 