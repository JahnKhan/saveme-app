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
const val SYSTEM_PROMPT = "You are an AI assistant with access to context and an image. When the user asks a question, answer them directly and provide helpful guidance based on the context, image, and their question. When no question is provided, analyze the image and tell the user what you see and how it might be relevant to their situation. Always speak directly to the user in a friendly, helpful manner. Provide clear insights and practical advice based on what you observe in the image and the available context."

// Dummy AI response for testing (fallback)
const val DUMMY_AI_RESPONSE = "I can see a camera view with various objects in the scene. This is a test response from the AI model to demonstrate that the image analysis is working properly." 