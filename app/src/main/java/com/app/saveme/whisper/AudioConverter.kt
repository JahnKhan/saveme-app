package com.app.saveme.whisper

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioConverter {
    companion object {
        private const val TAG = "AudioConverter"
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1 // Mono
        private const val BITS_PER_SAMPLE = 16
    }
    
    fun pcmToWav(pcmData: ByteArray, outputFile: File): Boolean {
        return try {
            val outputStream = FileOutputStream(outputFile)
            
            // Write WAV header
            writeWavHeader(outputStream, pcmData.size)
            
            // Write PCM data
            outputStream.write(pcmData)
            outputStream.close()
            
            Log.d(TAG, "Successfully converted PCM to WAV: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert PCM to WAV", e)
            false
        }
    }
    
    private fun writeWavHeader(outputStream: FileOutputStream, dataSize: Int) {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF header
        buffer.put("RIFF".toByteArray(), 0, 4)
        buffer.putInt(36 + dataSize) // File size - 8
        buffer.put("WAVE".toByteArray(), 0, 4)
        
        // fmt chunk
        buffer.put("fmt ".toByteArray(), 0, 4)
        buffer.putInt(16) // fmt chunk size
        buffer.putShort(1) // Audio format (PCM)
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8) // Byte rate
        buffer.putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort()) // Block align
        buffer.putShort(BITS_PER_SAMPLE.toShort())
        
        // data chunk
        buffer.put("data".toByteArray(), 0, 4)
        buffer.putInt(dataSize)
        
        outputStream.write(header)
    }
    
    fun pcmToFloatArray(pcmData: ByteArray): FloatArray {
        val shortArray = ShortArray(pcmData.size / 2)
        val buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        
        for (i in shortArray.indices) {
            shortArray[i] = buffer.short
        }
        
        return FloatArray(shortArray.size) { shortArray[it].toFloat() / 32768.0f }
    }
} 