package com.app.saveme.data

// Default Gemma 3B model configuration
object ModelConfig {
    const val DEFAULT_MODEL_NAME = "gemma-3n-E2B-it-int4"
    //const val DEFAULT_MODEL_URL = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task"
    const val DEFAULT_MODEL_URL ="https://gemma3n.s3.eu-central-1.amazonaws.com/gemma3n.task"
    // Configurable model URL - change this to use different models
    var CURRENT_MODEL_URL = DEFAULT_MODEL_URL
    var CURRENT_MODEL_NAME = DEFAULT_MODEL_NAME
    
    const val MODEL_FILE_EXTENSION = ".task"
    const val MODELS_DIR = "models"
    
    // Import functionality
    var IMPORTED_MODEL_NAME: String? = null
    var IMPORTED_MODEL_PATH: String? = null
    
    // Vision support tracking
    var SUPPORTS_VISION = true // Default model supports vision
    
    fun setCustomModel(name: String, url: String) {
        CURRENT_MODEL_NAME = name
        CURRENT_MODEL_URL = url
        IMPORTED_MODEL_NAME = null
        IMPORTED_MODEL_PATH = null
        // Enhanced vision support detection
        SUPPORTS_VISION = name.contains("gemma-3n", ignoreCase = true) ||
                         name.contains("vision", ignoreCase = true) ||
                         name.contains("multimodal", ignoreCase = true) ||
                         name.contains("E2B", ignoreCase = true) ||
                         name.contains("E4B", ignoreCase = true)
    }
    
    fun setImportedModel(name: String, path: String) {
        IMPORTED_MODEL_NAME = name
        IMPORTED_MODEL_PATH = path
        CURRENT_MODEL_NAME = name
        // Enhanced vision support detection for imported models
        SUPPORTS_VISION = name.contains("gemma-3n", ignoreCase = true) || 
                         name.contains("vision", ignoreCase = true) ||
                         name.contains("multimodal", ignoreCase = true) ||
                         name.contains("E2B", ignoreCase = true) ||
                         name.contains("E4B", ignoreCase = true)
    }
    
    fun setVisionSupport(enabled: Boolean) {
        SUPPORTS_VISION = enabled
    }
    
    fun hasImportedModel(): Boolean {
        return IMPORTED_MODEL_NAME != null && IMPORTED_MODEL_PATH != null
    }
}

data class ModelDownloadStatus(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

data class ModelImportStatus(
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

enum class ModelState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    IMPORTING,
    DOWNLOADED,
    INITIALIZING,  // Add this state
    LOADING,
    LOADED,
    ERROR
} 