package com.contactless.fingerprint.ui.navigation

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.contactless.fingerprint.quality.QualityResult
import com.contactless.fingerprint.ui.screens.CameraScreen
import com.contactless.fingerprint.ui.screens.HomeScreen
import com.contactless.fingerprint.ui.screens.QualityScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Quality : Screen("quality")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qualityResult by remember { mutableStateOf<QualityResult?>(null) }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onCaptureClick = {
                    navController.navigate(Screen.Camera.route)
                }
            )
        }

        composable(Screen.Camera.route) {
            CameraScreen(
                onCaptureClick = { bitmap, result ->
                    capturedBitmap = bitmap
                    qualityResult = result
                    navController.navigate(Screen.Quality.route)
                },
                onQualityCheckClick = {
                    // If we have a captured image, use it; otherwise navigate with null
                    navController.navigate(Screen.Quality.route)
                }
            )
        }

        composable(Screen.Quality.route) {
            QualityScreen(
                capturedBitmap = capturedBitmap,
                qualityResult = qualityResult,
                onBackClick = {
                    // Clear captured data when going back
                    capturedBitmap = null
                    qualityResult = null
                    navController.popBackStack()
                }
            )
        }
    }
}
