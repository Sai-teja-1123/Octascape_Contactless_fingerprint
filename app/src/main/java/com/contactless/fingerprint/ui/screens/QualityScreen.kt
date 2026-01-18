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
                title = { Text("Quality Assessment") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilterChip(
                                selected = !showRawImage,
                                onClick = { showRawImage = false },
                                label = { Text("Enhanced") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = showRawImage,
                                onClick = { showRawImage = true },
                                label = { Text("Raw") }
                            )
                        }
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            if (qualityResult == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No quality data available")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Capture a finger image first")
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export to ISO Format (500 ppi)")
                    }

                    // Step 5.1: Navigate to Matching button
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onMatchingClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Go to Matching (Track C)")
                    }
                }

                // Auto-matching section
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Matching Results",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Auto-matched against available fingerprints",
                            style = MaterialTheme.typography.bodySmall
                        )
                        // TODO: Show matching results when implemented
                        Text(
                            text = "No contact-based fingerprints available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
    val statusColor = if (isLive) Color(0xFF4CAF50) else Color(0xFFF44336)
    val statusText = if (isLive) "LIVE" else "SPOOF DETECTED"
    val statusIcon = if (isLive) "✓" else "⚠"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Liveness Detection (Track-D)",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = statusIcon,
                    fontSize = 24.sp,
                    color = statusColor
                )
                Text(
                    text = statusText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = statusColor
                )
            }
            
            Text(
                text = "Confidence: ${(confidence * 100).toInt()}%",
                fontSize = 16.sp
            )
            
            LinearProgressIndicator(
                progress = confidence,
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.2f)
            )
            
            Divider()
            
            // Individual scores
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Individual Scores:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                LivenessMetricRow("Motion", livenessResult.motionScore)
                LivenessMetricRow("Texture", livenessResult.textureScore)
                LivenessMetricRow("Consistency", livenessResult.consistencyScore)
            }
            
            if (!isLive) {
                Text(
                    text = "Warning: Spoof detected. This may be a photo or print.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF44336)
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
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = score,
                modifier = Modifier.width(100.dp),
                color = if (score > 0.5f) Color(0xFF4CAF50) else Color(0xFFF44336),
                trackColor = Color.Gray.copy(alpha = 0.2f)
            )
            Text(
                text = "${(score * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

@Composable
fun OverallQualityCard(qualityResult: QualityResult) {
    val isPass = qualityResult.isPass
    val statusColor = if (isPass) Color(0xFF4CAF50) else Color(0xFFF44336)
    val statusText = if (isPass) "PASS" else "FAIL"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = statusText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            
            Text(
                text = "Overall: ${String.format("%.1f%%", qualityResult.overallScore * 100)}",
                fontSize = 18.sp
            )
            
            LinearProgressIndicator(
                progress = { qualityResult.overallScore },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor
            )
            
            // Display failure reasons if quality check failed
            if (!isPass && qualityResult.failureReasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Issues found:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
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
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
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
    val statusColor = if (isPass) Color(0xFF4CAF50) else Color(0xFFFF9800)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${String.format("%.1f%%", score * 100)}",
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            LinearProgressIndicator(
                progress = { score },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor
            )
        }
    }
}
