package com.contactless.fingerprint.utils

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat

object PermissionHandler {
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun rememberCameraPermissionLauncher(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        onPermissionGranted()
    } else {
        onPermissionDenied()
    }
}
