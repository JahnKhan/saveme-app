package com.app.saveme.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.app.saveme.data.PREFERRED_IMAGE_SIZE
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "CameraPreview"

@Composable
fun CameraPreview(
    onImageCaptured: (Bitmap) -> Unit,
    onCaptureReady: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        val preferredSize = Size(PREFERRED_IMAGE_SIZE, PREFERRED_IMAGE_SIZE)
        val resolutionStrategy = ResolutionStrategy(
            preferredSize,
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
        )
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(resolutionStrategy)
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
            
        ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
    }
    
    LaunchedEffect(Unit) {
        val cameraProvider = getCameraProvider(context)
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            preview.setSurfaceProvider(previewView.surfaceProvider)
            
            // Provide capture function to parent
            onCaptureReady {
                captureImage(imageCapture, executor, onImageCaptured)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera", e)
        }
    }
    
    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}

private suspend fun getCameraProvider(context: Context): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                continuation.resume(future.get())
            }, ContextCompat.getMainExecutor(context))
        }
    }

private fun captureImage(
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onImageCaptured: (Bitmap) -> Unit
) {
    val callback = object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            try {
                var bitmap = image.toBitmap()
                val rotation = image.imageInfo.rotationDegrees
                
                bitmap = if (rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else bitmap
                
                onImageCaptured(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process captured image", e)
            } finally {
                image.close()
            }
        }
        
        override fun onError(exception: androidx.camera.core.ImageCaptureException) {
            Log.e(TAG, "Image capture failed", exception)
        }
    }
    
    imageCapture.takePicture(executor, callback)
} 