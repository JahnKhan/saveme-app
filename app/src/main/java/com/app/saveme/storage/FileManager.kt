package com.app.saveme.storage

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.app.saveme.data.AUDIO_FILE_PREFIX
import com.app.saveme.data.FILE_DATE_FORMAT
import com.app.saveme.data.IMAGE_CAPTURE_QUALITY
import com.app.saveme.data.IMAGE_FILE_PREFIX
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "FileManager"
private const val CONTEXT_FILE_NAME = "context.txt"

class FileManager {

    fun saveContext(context: Context, text: String) {
        try {
            val file = File(context.filesDir, CONTEXT_FILE_NAME)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(text.toByteArray())
            }
            Log.d(TAG, "Context saved: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save context", e)
        }
    }

    fun readContext(context: Context): String? {
        return try {
            val file = File(context.filesDir, CONTEXT_FILE_NAME)
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read context", e)
            null
        }
    }

    fun saveImage(context: Context, bitmap: Bitmap): File? {
        return try {
            val timestamp = SimpleDateFormat(FILE_DATE_FORMAT, Locale.getDefault()).format(Date())
            val filename = "${IMAGE_FILE_PREFIX}${timestamp}.jpg"
            val file = File(context.getExternalFilesDir(null), filename)
            
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_CAPTURE_QUALITY, outputStream)
            }
            
            Log.d(TAG, "Image saved: ${file.absolutePath}")
            file
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save image", e)
            null
        }
    }
    
    fun saveAudioData(context: Context, audioData: ByteArray): File? {
        return try {
            val timestamp = SimpleDateFormat(FILE_DATE_FORMAT, Locale.getDefault()).format(Date())
            val filename = "${AUDIO_FILE_PREFIX}${timestamp}.pcm"
            val file = File(context.getExternalFilesDir(null), filename)
            
            FileOutputStream(file).use { outputStream ->
                outputStream.write(audioData)
            }
            
            Log.d(TAG, "Audio saved: ${file.absolutePath}")
            file
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save audio", e)
            null
        }
    }
    
    fun cleanupOldFiles(context: Context, maxFiles: Int = 10) {
        try {
            val externalDir = context.getExternalFilesDir(null) ?: return
            val files = externalDir.listFiles { file ->
                file.name.startsWith(IMAGE_FILE_PREFIX) || file.name.startsWith(AUDIO_FILE_PREFIX)
            }?.sortedByDescending { it.lastModified() }
            
            if (files != null && files.size > maxFiles) {
                files.drop(maxFiles).forEach { file ->
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old files", e)
        }
    }
} 