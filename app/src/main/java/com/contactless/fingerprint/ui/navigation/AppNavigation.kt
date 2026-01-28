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
import com.contactless.fingerprint.liveness.LivenessResult
import com.contactless.fingerprint.quality.QualityResult
import com.contactless.fingerprint.ui.screens.CameraScreen
import com.contactless.fingerprint.ui.screens.HomeScreen
import com.contactless.fingerprint.ui.screens.IdSelectionScreen
import com.contactless.fingerprint.ui.screens.MatchingScreen
import com.contactless.fingerprint.ui.screens.QualityScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object IdSelection : Screen("id_selection")
    object Camera : Screen("camera")
    object Quality : Screen("quality")
    object Matching : Screen("matching")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    // Enhancement #3: Store both raw and enhanced bitmaps
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) } // Enhanced bitmap
    var qualityResult by remember { mutableStateOf<QualityResult?>(null) }
    var livenessResult by remember { mutableStateOf<LivenessResult?>(null) }
    var selectedEnrollmentId by remember { mutableStateOf<String?>(null) } // Store selected enrollment ID

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onCaptureClick = {
                    // Navigate to ID selection screen
                    navController.navigate(Screen.IdSelection.route)
                },
                onMatchingClick = {
                    navController.navigate(Screen.Matching.route)
                }
            )
        }

        composable(Screen.IdSelection.route) {
            IdSelectionScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onIdSelected = { enrollmentId ->
                    selectedEnrollmentId = enrollmentId
                    navController.navigate(Screen.Camera.route)
                }
            )
        }

        composable(Screen.Camera.route) {
            CameraScreen(
                onCaptureClick = { raw, enhanced, result, liveness ->
                    rawBitmap = raw
                    capturedBitmap = enhanced
                    qualityResult = result
                    livenessResult = liveness
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
                rawBitmap = rawBitmap,
                capturedBitmap = capturedBitmap,
                qualityResult = qualityResult,
                livenessResult = livenessResult,
                selectedEnrollmentId = selectedEnrollmentId,
                onBackClick = {
                    navController.popBackStack()
                },
                onNewCaptureClick = {
                    // Clear data and go to ID selection
                    rawBitmap = null
                    capturedBitmap = null
                    qualityResult = null
                    livenessResult = null
                    selectedEnrollmentId = null
                    navController.navigate(Screen.IdSelection.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onHomeClick = {
                    // Clear all data and go to home
                    rawBitmap = null
                    capturedBitmap = null
                    qualityResult = null
                    livenessResult = null
                    selectedEnrollmentId = null
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Matching.route) {
            MatchingScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
