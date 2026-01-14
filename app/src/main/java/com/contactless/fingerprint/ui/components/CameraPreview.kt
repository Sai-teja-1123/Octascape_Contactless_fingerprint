package com.contactless.fingerprint.ui.components

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.contactless.fingerprint.camera.CameraManager

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onCameraInitialized: (Boolean, CameraManager?) -> Unit = { _, _ -> },
    onPreviewViewReady: (PreviewView?) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var initialized by remember { mutableStateOf(false) }

    // Create camera manager once
    DisposableEffect(Unit) {
        cameraManager = CameraManager(context)
        onDispose {
            cameraManager?.releaseCamera()
        }
    }

    // Initialize camera when preview view is ready
    LaunchedEffect(previewViewRef) {
        if (previewViewRef != null && cameraManager != null && !initialized) {
            previewViewRef?.let { view ->
                cameraManager?.initializeCamera(
                    previewView = view,
                    lifecycleOwner = lifecycleOwner,
                    executor = mainExecutor
                ) { success ->
                    initialized = success
                    onCameraInitialized(success, cameraManager)
                    onPreviewViewReady(view)
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                previewViewRef = this
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
