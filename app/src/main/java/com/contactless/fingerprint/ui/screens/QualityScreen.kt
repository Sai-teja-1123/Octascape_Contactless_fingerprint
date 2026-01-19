package com.contactless.fingerprint.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contactless.fingerprint.ui.theme.*
import com.contactless.fingerprint.enhancement.ImageEnhancer
import com.contactless.fingerprint.liveness.LivenessResult
import com.contactless.fingerprint.quality.QualityResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityScreen(
    rawBitmap: Bitmap? = null,
    capturedBitmap: Bitmap?,
    qualityResult: QualityResult?,
    livenessResult: LivenessResult? = null,
    onBackClick: () -> Unit,
    onMatchingClick: () -> Unit = {}, // Navigate to matching screen
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageEnhancer = remember { ImageEnhancer() }
    val scrollState = rememberScrollState()
    
    // Enhancement #3: Toggle between raw and enhanced view
    var showRawImage by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Quality Assessment",
                        style = MaterialTheme.typography.displayMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.ScreenPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Spacing.LargeGap)
        ) {
            // Enhancement #3: Show raw or enhanced image preview with toggle
            if (capturedBitmap != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Toggle button to switch between raw and enhanced
                    if (rawBitmap != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = !showRawImage,
                                onClick = { showRawImage = false },
                                label = { 
                                    Text(
                                        "Enhanced",
                                        style = MaterialTheme.typography.bodyMedium
                                    ) 
                                },
                                shape = BorderRadius.Small
                            )
                            Spacer(modifier = Modifier.width(Spacing.SmallGap))
                            FilterChip(
                                selected = showRawImage,
                                onClick = { showRawImage = true },
                                label = { 
                                    Text(
                                        "Raw",
                                        style = MaterialTheme.typography.bodyMedium
                                    ) 
                                },
                                shape = BorderRadius.Small
                            )
                        }
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = BorderRadius.Large,
                        colors = CardDefaults.cardColors(
                            containerColor = BackgroundWhite
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = Elevation.Card
                        )
                    ) {
                        Image(
                            bitmap = if (showRawImage && rawBitmap != null) {
                                rawBitmap.asImageBitmap()
                            } else {
                                capturedBitmap.asImageBitmap()
                            },
                            contentDescription = if (showRawImage) "Raw finger image" else "Enhanced finger image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    
                    // Label showing current view
                    Text(
                        text = if (showRawImage) "Raw Image" else "Enhanced Image",
                        style = MaterialTheme.typography.bodySmall,
                        color = TertiaryText,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            if (qualityResult == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BorderRadius.Large,
                    colors = CardDefaults.cardColors(
                        containerColor = LightGray
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = Elevation.Card
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.CardPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                    ) {
                        Text(
                            "No quality data available",
                            style = MaterialTheme.typography.displaySmall,
                            color = PrimaryText
                        )
                        Text(
                            "Capture a finger image first",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    }
                }
            } else {
                OverallQualityCard(qualityResult)

                QualityMetricCard(
                    title = "Blur Score",
                    score = qualityResult.blurScore
                )

                QualityMetricCard(
                    title = "Illumination",
                    score = qualityResult.illuminationScore
                )

                QualityMetricCard(
                    title = "Coverage",
                    score = qualityResult.coverageScore
                )

                QualityMetricCard(
                    title = "Orientation",
                    score = qualityResult.orientationScore
                )

                // Step 5.2: Display Liveness Detection Results (Track-D)
                if (livenessResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LivenessResultCard(livenessResult)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ISO Export button (Track B optional feature)
                if (capturedBitmap != null) {
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.Default) {
                                val exportPath = imageEnhancer.exportToIsoFormat(capturedBitmap, context)
                                withContext(Dispatchers.Main) {
                                    if (exportPath != null) {
                                        Toast.makeText(
                                            context,
                                            "ISO format exported: ${exportPath.substringAfterLast("/")}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Export failed. Check logs for details.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = BorderRadius.Medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue,
                            contentColor = BackgroundWhite
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = Elevation.ButtonPrimary
                        )
                    ) {
                        Text(
                            "Export to ISO Format (500 ppi)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    // Step 5.1: Navigate to Matching button
                    Spacer(modifier = Modifier.height(Spacing.SmallGap))
                    Button(
                        onClick = onMatchingClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = BorderRadius.Medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue,
                            contentColor = BackgroundWhite
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = Elevation.ButtonPrimary
                        )
                    ) {
                        Text(
                            "Go to Matching (Track C)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Auto-matching section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BorderRadius.Large,
                    colors = CardDefaults.cardColors(
                        containerColor = LightGray
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = Elevation.Card
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                    ) {
                        Text(
                            text = "Matching Results",
                            style = MaterialTheme.typography.displaySmall,
                            color = PrimaryText
                        )
                        Text(
                            text = "Auto-matched against available fingerprints",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText
                        )
                        // TODO: Show matching results when implemented
                        Text(
                            text = "No contact-based fingerprints available",
                            style = MaterialTheme.typography.bodySmall,
                            color = TertiaryText
                        )
                    }
                }
            }
        }
    }
}

/**
 * Step 5.2: UI Integration - Liveness Result Card
 * Displays liveness detection results with color-coded status.
 */
@Composable
fun LivenessResultCard(livenessResult: LivenessResult) {
    val isLive = livenessResult.isLive
    val confidence = livenessResult.confidence
    val statusColor = if (isLive) SuccessGreen else DangerRed
    val statusText = if (isLive) "LIVE" else "SPOOF DETECTED"
    val statusIcon = if (isLive) "✓" else "⚠"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BorderRadius.Large,
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.Card
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
        ) {
            Text(
                text = "Liveness Detection (Track-D)",
                style = MaterialTheme.typography.displaySmall,
                color = PrimaryText
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
            ) {
                Text(
                    text = statusIcon,
                    style = MaterialTheme.typography.displayMedium,
                    color = statusColor
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.displaySmall,
                    color = statusColor
                )
            }
            
            Text(
                text = "Confidence: ${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.headlineLarge,
                color = PrimaryText
            )
            
            LinearProgressIndicator(
                progress = { confidence },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.2f)
            )
            
            Divider(color = BorderGray)
            
            // Individual scores
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Individual Scores:",
                    style = MaterialTheme.typography.headlineLarge,
                    color = PrimaryText
                )
                LivenessMetricRow("Motion", livenessResult.motionScore)
                LivenessMetricRow("Texture", livenessResult.textureScore)
                LivenessMetricRow("Consistency", livenessResult.consistencyScore)
            }
            
            if (!isLive) {
                Text(
                    text = "Warning: Spoof detected. This may be a photo or print.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DangerRed
                )
            }
        }
    }
}

@Composable
fun LivenessMetricRow(label: String, score: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PrimaryText
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { score },
                modifier = Modifier.width(100.dp),
                color = if (score > 0.5f) SuccessGreen else DangerRed,
                trackColor = BorderGray.copy(alpha = 0.2f)
            )
            Text(
                text = "${(score * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

@Composable
fun OverallQualityCard(qualityResult: QualityResult) {
    val isPass = qualityResult.isPass
    val statusColor = if (isPass) SuccessGreen else DangerRed
    val statusText = if (isPass) "PASS" else "FAIL"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BorderRadius.Large,
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.Card
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.displaySmall,
                color = statusColor
            )
            
            Text(
                text = "Overall: ${String.format("%.1f%%", qualityResult.overallScore * 100)}",
                style = MaterialTheme.typography.displaySmall,
                color = PrimaryText
            )
            
            LinearProgressIndicator(
                progress = { qualityResult.overallScore },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.2f)
            )
            
            // Display failure reasons if quality check failed
            if (!isPass && qualityResult.failureReasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.SmallGap))
                Divider(color = BorderGray)
                Spacer(modifier = Modifier.height(Spacing.SmallGap))
                Text(
                    text = "Issues found:",
                    style = MaterialTheme.typography.headlineLarge,
                    color = statusColor
                )
                qualityResult.failureReasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "• ",
                            color = statusColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QualityMetricCard(
    title: String,
    score: Float
) {
    val isPass = score >= 0.5f
    val statusColor = if (isPass) SuccessGreen else WarningOrange

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BorderRadius.Large,
        colors = CardDefaults.cardColors(
            containerColor = BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.Card
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = PrimaryText
                )
                Text(
                    text = "${String.format("%.1f%%", score * 100)}",
                    style = MaterialTheme.typography.displaySmall,
                    color = statusColor
                )
            }

            LinearProgressIndicator(
                progress = { score },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.2f)
            )
        }
    }
}
