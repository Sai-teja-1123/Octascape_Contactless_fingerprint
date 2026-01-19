package com.contactless.fingerprint

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.contactless.fingerprint.matching.EnrollmentRepository
import com.contactless.fingerprint.ui.navigation.AppNavigation
import com.contactless.fingerprint.ui.theme.ContactlessFingerprintTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize persistent enrollment repository
        EnrollmentRepository.initialize(this)
        
        // Initialize OpenCV
        if (OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "OpenCV loaded successfully")
        } else {
            Log.e("OpenCV", "Failed to load OpenCV")
        }
        
        setContent {
            ContactlessFingerprintTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
