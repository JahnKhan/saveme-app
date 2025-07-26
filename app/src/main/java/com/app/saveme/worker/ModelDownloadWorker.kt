package com.app.saveme.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.app.saveme.data.ModelConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

private const val TAG = "ModelDownloadWorker"

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
            downloadModel(modelUrl, modelName)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            val errorData = workDataOf(KEY_ERROR_MESSAGE to e.message)
            Result.failure(errorData)
        }
    }

    private suspend fun downloadModel(modelUrl: String, modelName: String) {
        Log.d(TAG, "Starting download of $modelName from $modelUrl")

        // Create models directory in app's external files directory
        val modelsDir = File(applicationContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val modelFile = File(modelsDir, "$modelName${ModelConfig.MODEL_FILE_EXTENSION}")
        
        // Check if file already exists
        if (modelFile.exists()) {
            Log.d(TAG, "Model file already exists: ${modelFile.absolutePath}")
            return
        }

        val url = URL(modelUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
        }

        val fileLength = connection.contentLength
        Log.d(TAG, "File size: $fileLength bytes")

        connection.inputStream.use { input ->
            FileOutputStream(modelFile).use { output ->
                val buffer = ByteArray(8192)
                var totalBytesRead = 0L
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Update progress
                    val progress = if (fileLength > 0) {
                        (totalBytesRead * 100f / fileLength)
                    } else {
                        0f
                    }

                    val progressData = workDataOf(
                        KEY_PROGRESS to progress,
                        KEY_DOWNLOADED_BYTES to totalBytesRead,
                        KEY_TOTAL_BYTES to fileLength.toLong()
                    )
                    setProgress(progressData)

                    // Log progress every 10%
                    if (progress.roundToInt() % 10 == 0 && progress > 0) {
                        Log.d(TAG, "Download progress: ${progress.roundToInt()}%")
                    }
                }
            }
        }

        Log.d(TAG, "Model downloaded successfully: ${modelFile.absolutePath}")
    }
} 