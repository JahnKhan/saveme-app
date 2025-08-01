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

// Transcription constants
const val TRANSCRIPTION_PROMPT_PREFIX = ""

// Unified system prompt for all scenarios
const val SYSTEM_PROMPT = "You are a precise AI assistant. Provide short, direct answers (1-5 sentences maximum). If you cannot answer the user's question based on the available context and image, simply say 'I cannot answer that based on the available information' and stop. Do not elaborate or provide general information. Focus only on what you can definitively determine from the context and image. Be concise and to the point."

// Dummy AI response for testing (fallback)
const val DUMMY_AI_RESPONSE = "I can see a camera view with various objects in the scene. This is a test response from the AI model to demonstrate that the image analysis is working properly." 