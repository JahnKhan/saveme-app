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
const val DUMMY_TRANSCRIPTION = "what is visible?" 