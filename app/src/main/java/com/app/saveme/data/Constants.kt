package com.app.saveme.data

import android.media.AudioFormat

// Audio recording constants (following gallery project pattern)
const val SAMPLE_RATE = 16000
const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
const val MAX_AUDIO_RECORDING_DURATION_SEC = 10

// Camera constants
const val IMAGE_CAPTURE_QUALITY = 85
const val PREFERRED_IMAGE_SIZE = 512

// File naming
const val IMAGE_FILE_PREFIX = "saveme_image_"
const val AUDIO_FILE_PREFIX = "saveme_audio_"
const val FILE_DATE_FORMAT = "yyyyMMdd_HHmmss"

// Dummy transcription text
const val DUMMY_TRANSCRIPTION = "you are an emergency AI assistant, based on the context provided to you, you answer questions of the user, you incorporate the image aswell: query: help me i dont know how to shutdown the system here !"

// Dummy AI response for testing
const val DUMMY_AI_RESPONSE = "I can see a camera view with various objects in the scene. This is a test response from the AI model to demonstrate that the image analysis is working properly." 