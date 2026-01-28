package com.contactless.fingerprint.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.contactless.fingerprint.ui.components.PrimaryButton
import com.contactless.fingerprint.ui.components.SecondaryButton
import com.contactless.fingerprint.ui.theme.*
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.contactless.fingerprint.camera.CameraManager
import com.contactless.fingerprint.quality.QualityAssessor
import com.contactless.fingerprint.enhancement.ImageEnhancer
import com.contactless.fingerprint.liveness.LivenessDetector
import com.contactless.fingerprint.liveness.LivenessResult
import com.contactless.fingerprint.ui.components.CameraPreview
import com.contactless.fingerprint.utils.PermissionHandler
import com.contactless.fingerprint.utils.rememberCameraPermissionLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onCaptureClick: (Bitmap?, Bitmap?, com.contactless.fingerprint.quality.QualityResult?, LivenessResult?) -> Unit,
    onQualityCheckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val qualityAssessor = remember { QualityAssessor() }
    val imageEnhancer = remember { ImageEnhancer() }
    val livenessDetector = remember { LivenessDetector() }
    val coroutineScope = rememberCoroutineScope()
    
    var hasPermission by remember { mutableStateOf(PermissionHandler.hasCameraPermission(context)) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var isFingerDetected by remember { mutableStateOf(false) }
    var cameraInitialized by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var fingerDetectionConfidence by remember { mutableFloatStateOf(0f) }
    
    // Step 1.2: Frame collection for liveness detection (Track-D)
    var frameCollectionStartTime by remember { mutableStateOf<Long?>(null) }
    var isFrameCollectionReady by remember { mutableStateOf(false) }
    val frameCollectionDuration = 1500L // Collect frames for 1.5 seconds before allowing capture
    
    // Step 5.3: Liveness detection during frame collection
    var preCaptureLivenessResult by remember { mutableStateOf<LivenessResult?>(null) }
    var isCheckingLiveness by remember { mutableStateOf(false) }

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
    
    // Step 1.2 & 5.3: Monitor frame collection duration and run liveness detection
    LaunchedEffect(frameCollectionStartTime) {
        if (frameCollectionStartTime != null && isFingerDetected) {
            delay(frameCollectionDuration)
            if (frameCollectionStartTime != null && isFingerDetected && cameraManager != null) {
                // Step 5.3: Run liveness detection on collected frames
                isCheckingLiveness = true
                preCaptureLivenessResult = null
                
                coroutineScope.launch(Dispatchers.Default) {
                    try {
                        val collectedFrames = cameraManager?.getCollectedFrames() ?: emptyList()
                        if (collectedFrames.isNotEmpty()) {
                            Log.d("CameraScreen", "Running pre-capture liveness detection on ${collectedFrames.size} frames...")
                            val livenessResult = livenessDetector.detectLiveness(collectedFrames)
                            
                            withContext(Dispatchers.Main) {
                                preCaptureLivenessResult = livenessResult
                                isCheckingLiveness = false
                                
                                // Log results
                                Log.d("CameraScreen", "Pre-capture liveness: isLive=${livenessResult.isLive}, confidence=${livenessResult.confidence}")
                                
                                // Show warning if spoof detected
                                if (!livenessResult.isLive) {
                                    Log.w("CameraScreen", "SPOOF DETECTED: confidence=${livenessResult.confidence}")
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                isCheckingLiveness = false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Error in pre-capture liveness detection: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            isCheckingLiveness = false
                        }
                    }
                }
                
                isFrameCollectionReady = true
                Log.d("CameraScreen", "Frame collection ready (${frameCollectionDuration}ms elapsed)")
            }
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
                                        
                                        // Step 1.2: Start collecting frames when finger is first detected
                                        if (detected && frameCollectionStartTime == null) {
                                            frameCollectionStartTime = System.currentTimeMillis()
                                            isFrameCollectionReady = false
                                            manager.startFrameCollection()
                                            Log.d("CameraScreen", "Started frame collection for liveness detection")
                                        } else if (!detected) {
                                            // Stop collection if finger is no longer detected
                                            if (frameCollectionStartTime != null) {
                                                manager.stopFrameCollection()
                                                frameCollectionStartTime = null
                                                isFrameCollectionReady = false
                                                preCaptureLivenessResult = null // Clear liveness result
                                                isCheckingLiveness = false
                                                Log.d("CameraScreen", "Stopped frame collection (finger lost)")
                                            }
                                        }
                                    }
                                } else if (!success) {
                                    cameraError = "Failed to initialize camera. Check Logcat for details."
                                }
                            },
                            onPreviewViewReady = { view ->
                                previewView = view
                            }
                        )
                        
                        // Finger placement box overlay with animations
                        if (cameraInitialized && previewView != null) {
                            FingerPlacementBox(
                                isFingerDetected = isFingerDetected,
                                isFrameCollectionReady = isFrameCollectionReady,
                                frameCollectionProgress = frameCollectionStartTime?.let { 
                                    ((System.currentTimeMillis() - it).toFloat() / frameCollectionDuration.toFloat()).coerceIn(0f, 1f)
                                } ?: 0f,
                                isCheckingLiveness = isCheckingLiveness
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
                        
                        // Animated finger detection indicator
                        if (isFingerDetected && cameraInitialized && !isCapturing) {
                            AnimatedFingerDetectedIndicator(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(1f)
                            )
                        }
                        
                        // Liveness status badge (top-right corner)
                        if (preCaptureLivenessResult != null && cameraInitialized && !isCapturing) {
                            LivenessStatusBadge(
                                livenessResult = preCaptureLivenessResult!!,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .zIndex(2f)
                            )
                        }
                    }
                }
            }

            // Controls with status badge
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Spacing.CardPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
            ) {
                // Status badge/chip
                StatusChip(
                    hasPermission = hasPermission,
                    cameraInitialized = cameraInitialized,
                    cameraError = cameraError,
                    isCapturing = isCapturing,
                    isFingerDetected = isFingerDetected,
                    isFrameCollectionReady = isFrameCollectionReady,
                    isCheckingLiveness = isCheckingLiveness,
                    frameCollectionStartTime = frameCollectionStartTime,
                    frameCollectionDuration = frameCollectionDuration,
                    fingerDetectionConfidence = fingerDetectionConfidence,
                    preCaptureLivenessResult = preCaptureLivenessResult
                )
                
                // Step 5.3: Show spoof warning if detected
                if (preCaptureLivenessResult != null && !preCaptureLivenessResult!!.isLive) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = BorderRadius.Large,
                        colors = CardDefaults.cardColors(
                            containerColor = DangerRed.copy(alpha = 0.1f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.CardPadding),
                            verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                            ) {
                                Text(
                                    text = "⚠",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = DangerRed
                                )
                                Text(
                                    text = "Spoof Detected",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = DangerRed
                                )
                            }
                            Text(
                                text = "Liveness check indicates this may be a photo or print.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PrimaryText
                            )
                            Text(
                                text = "Confidence: ${(preCaptureLivenessResult!!.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText
                            )
                            Text(
                                text = "You can still capture, but results may be unreliable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TertiaryText
                            )
                        }
                    }
                }

                PrimaryButton(
                    onClick = {
                        if (cameraManager != null && previewView != null && !isCapturing) {
                            // Step 5.1: Get collected frames for liveness detection
                            val collectedFrames = cameraManager?.getCollectedFrames() ?: emptyList()
                            
                            // Step 1.2: Stop frame collection before capture
                            cameraManager?.stopFrameCollection()
                            
                            isCapturing = true
                            cameraManager?.captureImage(previewView!!, mainExecutor) { bitmap ->
                                if (bitmap != null) {
                                    Log.d("CameraScreen", "Image captured, running enhancement + quality assessment + liveness detection...")
                                    // Run enhancement + quality assessment + liveness detection on background thread
                                    coroutineScope.launch(Dispatchers.Default) {
                                        // Enhancement #3: Store raw bitmap before enhancement
                                        val rawBitmap = bitmap.copy(bitmap.config, false)
                                        
                                        // Track B: enhance captured image using OpenCV
                                        // Pass preview dimensions for accurate box matching
                                        val previewWidth = previewView?.width
                                        val previewHeight = previewView?.height
                                        val enhancedBitmap = imageEnhancer.enhanceImage(bitmap, previewWidth, previewHeight)

                                        val qualityResult = qualityAssessor.assessQuality(enhancedBitmap)
                                        Log.d("CameraScreen", "Quality assessed: ${qualityResult.overallScore}")
                                        
                                        // Step 5.1: Run liveness detection on collected frames
                                        val livenessResult = if (collectedFrames.isNotEmpty()) {
                                            Log.d("CameraScreen", "Running liveness detection on ${collectedFrames.size} frames...")
                                            livenessDetector.detectLiveness(collectedFrames)
                                        } else {
                                            Log.w("CameraScreen", "No frames collected for liveness detection")
                                            LivenessResult(
                                                isLive = false,
                                                confidence = 0f,
                                                motionScore = 0f,
                                                textureScore = 0f,
                                                consistencyScore = 0f
                                            )
                                        }
                                        
                                        // Switch back to main thread to update UI
                                        withContext(Dispatchers.Main) {
                                            isCapturing = false
                                            // Step 1.2: Reset frame collection state after capture
                                            frameCollectionStartTime = null
                                            isFrameCollectionReady = false
                                            preCaptureLivenessResult = null // Clear pre-capture result
                                            // Clear frame buffer after use
                                            cameraManager?.clearFrameBuffer()
                                            // Enhancement #3: Pass both raw and enhanced bitmaps
                                            // Downstream flows (quality screen, later matching) use enhanced image
                                            onCaptureClick(rawBitmap, enhancedBitmap, qualityResult, livenessResult)
                                        }
                                    }
                                } else {
                                    isCapturing = false
                                    Log.e("CameraScreen", "Failed to capture image")
                                }
                            }
                        }
                    },
                    enabled = hasPermission && cameraInitialized && !isCapturing && previewView != null && isFingerDetected && isFrameCollectionReady && !isCheckingLiveness,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            !isFingerDetected -> "Place finger in box"
                            !isFrameCollectionReady -> "Collecting frames..."
                            else -> "Capture"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                SecondaryButton(
                    onClick = onQualityCheckClick,
                    enabled = hasPermission && cameraInitialized && !isCapturing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Check Quality",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// Animated Finger Placement Box with Progress Indicator
@Composable
fun FingerPlacementBox(
    isFingerDetected: Boolean,
    isFrameCollectionReady: Boolean,
    frameCollectionProgress: Float,
    isCheckingLiveness: Boolean
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isFrameCollectionReady -> SuccessGreen
            isFingerDetected -> PrimaryBlue
            else -> BackgroundWhite.copy(alpha = 0.9f)
        },
        animationSpec = tween(300, easing = AnimationEasing.StandardEasing),
        label = "border_color"
    )
    
    val borderWidth by animateFloatAsState(
        targetValue = if (isFingerDetected) 4f else 3f,
        animationSpec = tween(300, easing = AnimationEasing.StandardEasing),
        label = "border_width"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
        contentAlignment = Alignment.Center
    ) {
        // Main finger placement box
        Box(
            modifier = Modifier
                .size(300.dp, 220.dp)
                .border(
                    width = borderWidth.dp,
                    color = borderColor,
                    shape = BorderRadius.Medium
                )
        )
        
        // Corner indicators with animation
        Box(
            modifier = Modifier.size(300.dp, 220.dp)
        ) {
            val cornerSize = if (isFingerDetected) 24.dp else 20.dp
            val cornerBorderWidth = if (isFingerDetected) 4.dp else 3.dp
            
            // Top-left corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(cornerSize)
                    .border(cornerBorderWidth, borderColor, BorderRadius.Small)
            )
            // Top-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(cornerSize)
                    .border(cornerBorderWidth, borderColor, BorderRadius.Small)
            )
            // Bottom-left corner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(cornerSize)
                    .border(cornerBorderWidth, borderColor, BorderRadius.Small)
            )
            // Bottom-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(cornerSize)
                    .border(cornerBorderWidth, borderColor, BorderRadius.Small)
            )
        }
        
        // Circular progress indicator around box (when collecting frames)
        if (isFingerDetected && !isFrameCollectionReady && !isCheckingLiveness) {
            CircularProgressIndicator(
                progress = { frameCollectionProgress },
                modifier = Modifier
                    .size(340.dp, 260.dp)
                    .alpha(0.8f),
                color = PrimaryBlue,
                trackColor = PrimaryBlue.copy(alpha = 0.2f),
                strokeWidth = 4.dp
            )
        }
        
        // Hint text below box
        Text(
            text = when {
                isCheckingLiveness -> "Checking liveness..."
                isFrameCollectionReady -> "Ready to capture"
                isFingerDetected -> "Hold still..."
                else -> "Place your finger inside the box"
            },
            color = BackgroundWhite.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 240.dp),
            textAlign = TextAlign.Center
        )
    }
}

// Animated Finger Detected Indicator (Pulsing Ring)
@Composable
fun AnimatedFingerDetectedIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "finger_detected")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = AnimationEasing.StandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = AnimationEasing.StandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Pulsing ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .alpha(alpha)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SuccessGreen.copy(alpha = 0.6f),
                            SuccessGreen.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        // Checkmark icon
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Finger detected",
            modifier = Modifier.size(48.dp),
            tint = SuccessGreen
        )
    }
}

// Liveness Status Badge
@Composable
fun LivenessStatusBadge(
    livenessResult: LivenessResult,
    modifier: Modifier = Modifier
) {
    val isLive = livenessResult.isLive
    val badgeColor = if (isLive) SuccessGreen else DangerRed
    val badgeText = if (isLive) "LIVE" else "SPOOF"
    val icon = if (isLive) Icons.Default.CheckCircle else Icons.Default.Warning
    
    Surface(
        modifier = modifier,
        shape = BorderRadius.Medium,
        color = badgeColor.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = badgeColor
            )
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium,
                color = badgeColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Status Chip Component
@Composable
fun StatusChip(
    hasPermission: Boolean,
    cameraInitialized: Boolean,
    cameraError: String?,
    isCapturing: Boolean,
    isFingerDetected: Boolean,
    isFrameCollectionReady: Boolean,
    isCheckingLiveness: Boolean,
    frameCollectionStartTime: Long?,
    frameCollectionDuration: Long,
    fingerDetectionConfidence: Float,
    preCaptureLivenessResult: LivenessResult?
) {
    val statusText = when {
        !hasPermission -> "Camera permission needed"
        !cameraInitialized && cameraError == null -> "Initializing..."
        cameraError != null -> "Error: $cameraError"
        isCapturing -> "Capturing image..."
        isFingerDetected && !isFrameCollectionReady -> {
            val elapsed = frameCollectionStartTime?.let { System.currentTimeMillis() - it } ?: 0L
            val remaining = (frameCollectionDuration - elapsed).coerceAtLeast(0L)
            if (isCheckingLiveness) {
                "Checking liveness..."
            } else {
                "Collecting frames... (${(remaining / 1000f).coerceAtLeast(0f).toInt()}s)"
            }
        }
        isFingerDetected -> {
            when {
                preCaptureLivenessResult != null && !preCaptureLivenessResult!!.isLive -> {
                    "⚠ SPOOF DETECTED"
                }
                preCaptureLivenessResult != null -> {
                    "Ready (Live: ${(preCaptureLivenessResult!!.confidence * 100).toInt()}%)"
                }
                else -> {
                    "Finger detected (${(fingerDetectionConfidence * 100).toInt()}%)"
                }
            }
        }
        else -> "Position finger in the box"
    }
    
    val statusColor = when {
        !hasPermission -> MaterialTheme.colorScheme.error
        cameraError != null -> MaterialTheme.colorScheme.error
        preCaptureLivenessResult != null && !preCaptureLivenessResult!!.isLive -> DangerRed
        isFrameCollectionReady -> SuccessGreen
        isFingerDetected -> PrimaryBlue
        cameraInitialized -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BorderRadius.Medium,
        color = statusColor.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Text(
            text = statusText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
    }
}
