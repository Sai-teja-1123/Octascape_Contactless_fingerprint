package com.contactless.fingerprint.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.contactless.fingerprint.camera.CameraManager
import com.contactless.fingerprint.quality.QualityAssessor
import com.contactless.fingerprint.ui.components.CameraPreview
import com.contactless.fingerprint.utils.PermissionHandler
import com.contactless.fingerprint.utils.rememberCameraPermissionLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onCaptureClick: (Bitmap?, com.contactless.fingerprint.quality.QualityResult?) -> Unit,
    onQualityCheckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val qualityAssessor = remember { QualityAssessor() }
    val coroutineScope = rememberCoroutineScope()
    
    var hasPermission by remember { mutableStateOf(PermissionHandler.hasCameraPermission(context)) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var isFingerDetected by remember { mutableStateOf(false) }
    var cameraInitialized by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var fingerDetectionConfidence by remember { mutableStateOf(0f) }

    val permissionLauncher = rememberCameraPermissionLauncher(
        onPermissionGranted = {
            hasPermission = true
            showPermissionRationale = false
            Log.d("CameraScreen", "Permission granted")
        },
        onPermissionDenied = {
            hasPermission = false
            showPermissionRationale = true
            Log.d("CameraScreen", "Permission denied")
        }
    )

    // Request permission when screen is first displayed
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            Log.d("CameraScreen", "Requesting camera permission")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Capture Finger") })
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            // Camera Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                when {
                    !hasPermission -> {
                        // Permission request UI
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(2f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Camera Permission Required",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (showPermissionRationale) {
                                        "Camera permission is required to capture finger images. Please grant permission in settings."
                                    } else {
                                        "Requesting camera permission..."
                                    },
                                    color = Color.White.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                                if (showPermissionRationale) {
                                    Button(
                                        onClick = {
                                            permissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    ) {
                                        Text("Grant Permission")
                                    }
                                }
                            }
                        }
                    }
                    cameraError != null -> {
                        // Error UI
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(2f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Camera Error",
                                    color = Color.Red,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = cameraError ?: "Unknown error",
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    else -> {
                        // Always show camera preview when permission is granted
                        CameraPreview(
                            modifier = Modifier.zIndex(0f),
                            onCameraInitialized = { success, manager ->
                                Log.d("CameraScreen", "Camera initialized: $success")
                                cameraInitialized = success
                                cameraManager = manager
                                if (success && manager != null) {
                                    // Set up finger detection callback
                                    manager.setFingerDetectionCallback { detected, confidence ->
                                        isFingerDetected = detected
                                        fingerDetectionConfidence = confidence
                                    }
                                } else if (!success) {
                                    cameraError = "Failed to initialize camera. Check Logcat for details."
                                }
                            },
                            onPreviewViewReady = { view ->
                                previewView = view
                            }
                        )
                        
                        // Finger placement box overlay
                        if (cameraInitialized && previewView != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                // Finger placement box
                                Box(
                                    modifier = Modifier
                                        .size(300.dp, 220.dp)
                                        .border(
                                            width = 3.dp,
                                            color = Color.White.copy(alpha = 0.9f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                )
                                
                                // Corner indicators for better visibility
                                Box(
                                    modifier = Modifier
                                        .size(300.dp, 220.dp)
                                ) {
                                    // Top-left corner
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .size(20.dp)
                                            .border(3.dp, Color.White, RoundedCornerShape(4.dp))
                                    )
                                    // Top-right corner
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                            .border(3.dp, Color.White, RoundedCornerShape(4.dp))
                                    )
                                    // Bottom-left corner
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .size(20.dp)
                                            .border(3.dp, Color.White, RoundedCornerShape(4.dp))
                                    )
                                    // Bottom-right corner
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(20.dp)
                                            .border(3.dp, Color.White, RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                            
                            // Hint text below box
                            Text(
                                text = "Place your finger inside the box",
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 240.dp)
                                    .zIndex(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        // Show loading overlay while initializing
                        if (!cameraInitialized && cameraError == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(2f)
                                    .background(Color.Black.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(color = Color.White)
                                    Text(
                                        text = "Initializing camera...",
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        
                        // Show capturing overlay
                        if (isCapturing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(2f)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(color = Color.White)
                                    Text(
                                        text = "Capturing...",
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        
                        // Finger detection indicator overlay
                        if (isFingerDetected && cameraInitialized && !isCapturing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✓",
                                    color = Color.Green,
                                    style = MaterialTheme.typography.displayLarge
                                )
                            }
                        }
                    }
                }
            }

            // Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = when {
                        !hasPermission -> "Camera permission needed"
                        !cameraInitialized && cameraError == null -> "Initializing..."
                        cameraError != null -> "Error: ${cameraError}"
                        isCapturing -> "Capturing image..."
                        isFingerDetected -> "Finger detected (${(fingerDetectionConfidence * 100).toInt()}%)"
                        else -> "Position finger in the box"
                    },
                    textAlign = TextAlign.Center,
                    color = when {
                        !hasPermission -> MaterialTheme.colorScheme.error
                        cameraError != null -> MaterialTheme.colorScheme.error
                        cameraInitialized -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )

                Button(
                    onClick = {
                        if (cameraManager != null && previewView != null && !isCapturing) {
                            isCapturing = true
                            cameraManager?.captureImage(previewView!!, mainExecutor) { bitmap ->
                                if (bitmap != null) {
                                    Log.d("CameraScreen", "Image captured, assessing quality...")
                                    // Run quality assessment on background thread to avoid blocking UI
                                    coroutineScope.launch(Dispatchers.Default) {
                                        val qualityResult = qualityAssessor.assessQuality(bitmap)
                                        Log.d("CameraScreen", "Quality assessed: ${qualityResult.overallScore}")
                                        // Switch back to main thread to update UI
                                        withContext(Dispatchers.Main) {
                                            isCapturing = false
                                            onCaptureClick(bitmap, qualityResult)
                                        }
                                    }
                                } else {
                                    isCapturing = false
                                    Log.e("CameraScreen", "Failed to capture image")
                                }
                            }
                        }
                    },
                    enabled = hasPermission && cameraInitialized && !isCapturing && previewView != null && isFingerDetected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isFingerDetected) "Capture" else "Place finger in box")
                }

                OutlinedButton(
                    onClick = onQualityCheckClick,
                    enabled = hasPermission && cameraInitialized && !isCapturing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check Quality")
                }
            }
        }
    }
}
