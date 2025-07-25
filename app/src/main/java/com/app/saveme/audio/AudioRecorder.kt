package com.app.saveme.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.app.saveme.data.AUDIO_FORMAT
import com.app.saveme.data.CHANNEL_CONFIG
import com.app.saveme.data.MAX_AUDIO_RECORDING_DURATION_SEC
import com.app.saveme.data.SAMPLE_RATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

private const val TAG = "AudioRecorder"

class AudioRecorder {
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioStream = ByteArrayOutputStream()
    
    @SuppressLint("MissingPermission")
    suspend fun startRecording(
        onAmplitudeChanged: (Int) -> Unit,
        onDurationChanged: (Float) -> Unit,
        onMaxDurationReached: () -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting audio recording...")
        
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        
        audioRecord?.release()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )
        
        val buffer = ByteArray(minBufferSize)
        audioRecord?.startRecording()
        isRecording = true
        
        val startTime = System.currentTimeMillis()
        
        while (isRecording) {
            val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (bytesRead > 0) {
                val amplitude = calculatePeakAmplitude(buffer, bytesRead)
                onAmplitudeChanged(amplitude)
                audioStream.write(buffer, 0, bytesRead)
            }
            
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000f
            onDurationChanged(elapsedTime)
            
            if (elapsedTime >= MAX_AUDIO_RECORDING_DURATION_SEC) {
                onMaxDurationReached()
                break
            }
        }
    }
    
    fun stopRecording(): ByteArray {
        Log.d(TAG, "Stopping audio recording...")
        isRecording = false
        
        audioRecord?.let { recorder ->
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
            recorder.release()
        }
        audioRecord = null
        
        val recordedBytes = audioStream.toByteArray()
        audioStream.reset()
        Log.d(TAG, "Recording stopped. Recorded ${recordedBytes.size} bytes")
        
        return recordedBytes
    }
    
    private fun calculatePeakAmplitude(buffer: ByteArray, bytesRead: Int): Int {
        var peak = 0
        for (i in 0 until bytesRead step 2) {
            if (i + 1 < bytesRead) {
                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                val amplitude = abs(sample)
                if (amplitude > peak) {
                    peak = amplitude
                }
            }
        }
        return peak
    }
    
    fun isRecording(): Boolean = isRecording
} 