package com.contactless.fingerprint.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraManager(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var fingerDetectionCallback: ((Boolean, Float) -> Unit)? = null
    private var lastDetectionTime = 0L
    private val detectionInterval = 500L // Analyze every 500ms to avoid performance issues
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    fun setFingerDetectionCallback(callback: (Boolean, Float) -> Unit) {
        fingerDetectionCallback = callback
    }

    fun initializeCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        executor: Executor,
        onInitialized: (Boolean) -> Unit
    ) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()

                    preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    // ImageAnalysis for real-time finger detection
                    imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(analysisExecutor) { imageProxy ->
                                analyzeFrameForFinger(imageProxy)
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    cameraProvider?.unbindAll()
                    camera = cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )
                    
                    // Auto-focus on center (where finger box will be)
                    focusAtCenter(previewView, executor)
                    
                    Log.d("CameraManager", "Camera initialized successfully")
                    onInitialized(true)
                } catch (e: Exception) {
                    Log.e("CameraManager", "Camera initialization failed: ${e.message}", e)
                    onInitialized(false)
                }
            }, executor)
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to get camera provider: ${e.message}", e)
            onInitialized(false)
        }
    }

    private fun analyzeFrameForFinger(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        // Only analyze every N milliseconds to avoid performance issues
        if (currentTime - lastDetectionTime < detectionInterval) {
            imageProxy.close()
            return
        }
        lastDetectionTime = currentTime

        try {
            // Convert ImageProxy to Bitmap for finger detection
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                val fingerDetector = FingerDetector()
                val result = fingerDetector.detectFinger(bitmap)
                
                Log.d("CameraManager", "Finger detection: detected=${result.isFingerDetected}, confidence=${result.confidence}")
                
                // Notify callback on main thread
                fingerDetectionCallback?.let { callback ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        callback(result.isFingerDetected, result.confidence)
                    }
                } ?: Log.w("CameraManager", "Finger detection callback is null")
            } else {
                Log.w("CameraManager", "Failed to convert ImageProxy to Bitmap")
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Error analyzing frame: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val width = imageProxy.width
            val height = imageProxy.height
            
            // YUV_420_888 format: Y plane (luminance), U plane (chrominance), V plane (chrominance)
            val yPlane = imageProxy.planes[0]
            val uPlane = imageProxy.planes[1]
            val vPlane = imageProxy.planes[2]
            
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uRowStride = uPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vRowStride = vPlane.rowStride
            val vPixelStride = vPlane.pixelStride
            
            // Create RGB bitmap from YUV
            val pixels = IntArray(width * height)
            yBuffer.rewind()
            uBuffer.rewind()
            vBuffer.rewind()
            
            var offset = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    // Get Y value
                    val yIndex = y * yRowStride + x * yPixelStride
                    val yValue = (yBuffer.get(yIndex).toInt() and 0xFF) - 16
                    
                    // Get U and V values (subsampled - shared between 2x2 pixels)
                    val uvX = x / 2
                    val uvY = y / 2
                    val uIndex = uvY * uRowStride + uvX * uPixelStride
                    val vIndex = uvY * vRowStride + uvX * vPixelStride
                    
                    val uValue = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
                    val vValue = (vBuffer.get(vIndex).toInt() and 0xFF) - 128
                    
                    // Convert YUV to RGB (ITU-R BT.601)
                    val r = (1.164f * yValue + 1.596f * vValue).toInt().coerceIn(0, 255)
                    val g = (1.164f * yValue - 0.392f * uValue - 0.813f * vValue).toInt().coerceIn(0, 255)
                    val b = (1.164f * yValue + 2.017f * uValue).toInt().coerceIn(0, 255)
                    
                    // Create ARGB pixel
                    pixels[offset++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            
            val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            
            // Handle rotation if needed (based on imageProxy.imageInfo.rotationDegrees)
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = android.graphics.Matrix().apply {
                    postRotate(rotationDegrees.toFloat())
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                    bitmap.recycle()
                }
            } else {
                bitmap
            }
            
            // Scale down for faster processing
            val smallBitmap = if (rotatedBitmap.width > 400 || rotatedBitmap.height > 400) {
                val scale = 400f / rotatedBitmap.width.coerceAtLeast(rotatedBitmap.height)
                val newWidth = (rotatedBitmap.width * scale).toInt()
                val newHeight = (rotatedBitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(rotatedBitmap, newWidth, newHeight, true).also {
                    rotatedBitmap.recycle()
                }
            } else {
                rotatedBitmap
            }
            
            smallBitmap
        } catch (e: Exception) {
            Log.e("CameraManager", "Error converting ImageProxy to Bitmap: ${e.message}", e)
            null
        }
    }

    private fun focusAtCenter(previewView: PreviewView, executor: Executor, onFocusComplete: (() -> Unit)? = null) {
        try {
            val cameraControl = camera?.cameraControl ?: run {
                onFocusComplete?.invoke()
                return
            }
            
            // Check if preview view has valid dimensions
            if (previewView.width <= 0 || previewView.height <= 0) {
                Log.w("CameraManager", "PreviewView has invalid dimensions, skipping focus")
                onFocusComplete?.invoke()
                return
            }
            
            // Create metering point at center (where finger box will be)
            val factory = previewView.meteringPointFactory
            val centerX = previewView.width / 2f
            val centerY = previewView.height / 2f
            
            try {
                val point = factory.createPoint(centerX, centerY)
                
                // Start focus and metering at center with longer duration
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(5, TimeUnit.SECONDS)
                    .build()
                
                val future = cameraControl.startFocusAndMetering(action)
                
                // Wait for focus to complete (but don't block if it fails)
                future.addListener({
                    try {
                        val result = future.get()
                        if (result.isFocusSuccessful) {
                            Log.d("CameraManager", "Focus successful")
                        } else {
                            Log.d("CameraManager", "Focus may not be optimal")
                        }
                    } catch (e: Exception) {
                        // Some cameras don't support metering points - this is OK, just log it
                        Log.d("CameraManager", "Focus metering not supported or failed: ${e.message}")
                    } finally {
                        onFocusComplete?.invoke()
                    }
                }, executor)
                
                Log.d("CameraManager", "Auto-focus started at center")
            } catch (e: IllegalArgumentException) {
                // Camera doesn't support this metering point - this is OK, just continue
                Log.d("CameraManager", "Focus metering not supported on this camera, continuing without focus")
                onFocusComplete?.invoke()
            }
        } catch (e: Exception) {
            Log.d("CameraManager", "Focus not available: ${e.message}")
            onFocusComplete?.invoke()
        }
    }

    fun refocusAtCenter(previewView: PreviewView, executor: Executor) {
        focusAtCenter(previewView, executor)
    }

    fun captureImage(
        previewView: PreviewView,
        executor: Executor,
        onImageCaptured: (Bitmap?) -> Unit
    ) {
        val imageCapture = this.imageCapture ?: run {
            Log.e("CameraManager", "ImageCapture not initialized")
            onImageCaptured(null)
            return
        }

        try {
            // Trigger focus once, then capture (reduced delays to avoid motion blur)
            focusAtCenter(previewView, executor) {
                // Focus completed, wait for stabilization, then capture
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val outputDir = context.cacheDir
                            val photoFile = java.io.File.createTempFile("finger_capture_", ".jpg", outputDir)

                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            imageCapture.takePicture(
                                outputOptions,
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        try {
                                            val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                                            if (bitmap != null) {
                                                onImageCaptured(bitmap)
                                            } else {
                                                Log.e("CameraManager", "Decoded bitmap is null")
                                                onImageCaptured(null)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("CameraManager", "Error decoding saved image: ${e.message}", e)
                                            onImageCaptured(null)
                                        } finally {
                                            photoFile.delete()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("CameraManager", "Image capture failed: ${exception.message}", exception)
                                        onImageCaptured(null)
                                        photoFile.delete()
                                    }
                                }
                            )
                        }, 600) // Wait 600ms after focus for stabilization (reduced to avoid motion)
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Error setting up image capture: ${e.message}", e)
            onImageCaptured(null)
        }
    }

    fun releaseCamera() {
        try {
            imageAnalysis?.clearAnalyzer()
            analysisExecutor.shutdown()
            cameraProvider?.unbindAll()
            preview = null
            imageCapture = null
            imageAnalysis = null
            camera = null
            fingerDetectionCallback = null
            Log.d("CameraManager", "Camera released")
        } catch (e: Exception) {
            Log.e("CameraManager", "Error releasing camera: ${e.message}", e)
        }
    }
}
