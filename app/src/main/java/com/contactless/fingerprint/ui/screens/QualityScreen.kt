package com.contactless.fingerprint.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.contactless.fingerprint.ui.theme.*
import com.contactless.fingerprint.enhancement.ImageEnhancer
import com.contactless.fingerprint.liveness.LivenessResult
import com.contactless.fingerprint.quality.QualityResult
import com.contactless.fingerprint.matching.EnrollmentRepository
import com.contactless.fingerprint.matching.FeatureExtractor
import com.contactless.fingerprint.matching.Matcher
import com.contactless.fingerprint.matching.MatchResult
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
    selectedEnrollmentId: String? = null,
    onBackClick: () -> Unit,
    onNewCaptureClick: () -> Unit = {}, // Start new capture
    onHomeClick: () -> Unit = {}, // Go to home
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageEnhancer = remember { ImageEnhancer() }
    val featureExtractor = remember { FeatureExtractor() }
    val matcher = remember { Matcher() }
    val scrollState = rememberScrollState()
    
    // Enhancement #3: Toggle between raw and enhanced view
    var showRawImage by remember { mutableStateOf(false) }
    
    // Matching state
    var isMatching by remember { mutableStateOf(false) }
    var matchResult by remember { mutableStateOf<MatchResult?>(null) }
    var bestMatchBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showResults by remember { mutableStateOf(false) }
    
    // Function to perform matching
    fun performMatching() {
        if (capturedBitmap == null || selectedEnrollmentId == null) {
            Toast.makeText(
                context,
                "Missing required data for matching",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        val enrollment = EnrollmentRepository.getEnrollment(selectedEnrollmentId)
        if (enrollment == null || enrollment.imageUris.isEmpty()) {
            Toast.makeText(
                context,
                "Enrollment has no images",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        isMatching = true
        matchResult = null
        bestMatchBitmap = null
        showResults = false
        
        val bitmapToMatch = capturedBitmap ?: return
        coroutineScope.launch(Dispatchers.Default) {
            val totalStart = System.currentTimeMillis()
            try {
                val contactlessStart = System.currentTimeMillis()
                val contactlessFeatures = featureExtractor.extractFeatures(bitmapToMatch, isFromGallery = false)
                val contactlessTime = System.currentTimeMillis() - contactlessStart
                Log.d("QualityScreen", "Contactless feature extraction took ${contactlessTime}ms")

                var bestSimilarity = 0f
                var bestMatch: MatchResult? = null
                var bestUri: Uri? = null
                var loadedCount = 0
                var failedCount = 0

                for (uri in enrollment.imageUris) {
                    val contactBitmap = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                BitmapFactory.decodeStream(inputStream)
                            }
                        } catch (e: Exception) {
                            Log.e("QualityScreen", "Failed to load image from URI: $uri", e)
                            null
                        }
                    }

                    if (contactBitmap != null) {
                        loadedCount++
                        try {
                            val contactStart = System.currentTimeMillis()
                            val contactFeatures = featureExtractor.extractFeatures(contactBitmap, isFromGallery = true)
                            val contactTime = System.currentTimeMillis() - contactStart
                            Log.d("QualityScreen", "Contact feature extraction took ${contactTime}ms")

                            val similarityStart = System.currentTimeMillis()
                            val similarity = matcher.computeSimilarity(contactlessFeatures, contactFeatures)
                            val similarityTime = System.currentTimeMillis() - similarityStart
                            Log.d("QualityScreen", "Similarity computation took ${similarityTime}ms")

                            if (similarity > bestSimilarity) {
                                bestSimilarity = similarity
                                bestMatch = matcher.match(contactlessFeatures, contactFeatures)
                                bestUri = uri
                            }

                            contactBitmap.recycle()
                        } catch (e: Exception) {
                            Log.e("QualityScreen", "Error processing image: $uri", e)
                            contactBitmap.recycle()
                            failedCount++
                        }
                    } else {
                        failedCount++
                    }
                }

                val bestMatchImage = bestUri?.let { uri ->
                    withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                BitmapFactory.decodeStream(inputStream)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                val totalTime = System.currentTimeMillis() - totalStart
                Log.d("QualityScreen", "Total matching process took ${totalTime}ms for ${loadedCount} image(s)")
                
                withContext(Dispatchers.Main) {
                    matchResult = bestMatch
                    bestMatchBitmap = bestMatchImage
                    isMatching = false
                    showResults = true

                    if (bestMatch != null) {
                        Toast.makeText(
                            context,
                            "Match result: ${if (bestMatch.isMatch) "MATCH" else "NO MATCH"} (${(bestMatch.similarityScore * 100).toInt()}%)",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Matching failed: Could not process images",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isMatching = false
                    Toast.makeText(
                        context,
                        "Matching error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
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
            // Hero Image Preview Section (larger, more prominent)
            if (capturedBitmap != null) {
                HeroImagePreview(
                    rawBitmap = rawBitmap,
                    enhancedBitmap = capturedBitmap,
                    showRawImage = showRawImage,
                    onToggleRaw = { showRawImage = it }
                )
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
                // Overall Status Card (prominent, at top)
                OverallQualityCard(qualityResult)
                
                Spacer(modifier = Modifier.height(Spacing.MediumGap))
                
                // Image Quality Section
                SectionHeader(title = "Image Quality")
                
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

                // Liveness Section
                if (livenessResult != null) {
                    Spacer(modifier = Modifier.height(Spacing.MediumGap))
                    SectionHeader(title = "Liveness Detection")
                    LivenessResultCard(livenessResult)
                }

                Spacer(modifier = Modifier.height(Spacing.LargeGap))
                
                // Action Buttons Section
                if (capturedBitmap != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                    ) {
                        // ISO Export button
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

                        // Match button - triggers matching
                        Button(
                            onClick = { performMatching() },
                            enabled = !isMatching && capturedBitmap != null && selectedEnrollmentId != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = BorderRadius.Medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                contentColor = BackgroundWhite,
                                disabledContainerColor = TertiaryText,
                                disabledContentColor = BackgroundWhite.copy(alpha = 0.3f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = Elevation.ButtonPrimary
                            )
                        ) {
                            if (isMatching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = BackgroundWhite
                                )
                                Spacer(modifier = Modifier.width(Spacing.SmallGap))
                                Text(
                                    "Matching...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                Text(
                                    "Match",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        
                        // New Capture and Home buttons (shown when results are displayed)
                        if (showResults && matchResult != null) {
                            Spacer(modifier = Modifier.height(Spacing.SmallGap))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                            ) {
                                Button(
                                    onClick = onNewCaptureClick,
                                    modifier = Modifier.weight(1f),
                                    shape = BorderRadius.Medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LightGray,
                                        contentColor = PrimaryText
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                                ) {
                                    Text(
                                        "New Capture",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                
                                Button(
                                    onClick = onHomeClick,
                                    modifier = Modifier.weight(1f),
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
                                        "Home",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }

                // Split View: Quality Assessment and Matching Results
                if (showResults && matchResult != null) {
                    Spacer(modifier = Modifier.height(Spacing.LargeGap))
                    HorizontalDivider(color = BorderGray, thickness = 2.dp)
                    Spacer(modifier = Modifier.height(Spacing.LargeGap))
                    
                    Text(
                        text = "Matching Results",
                        style = MaterialTheme.typography.displaySmall,
                        color = PrimaryText,
                        fontWeight = FontWeight.Bold
                    )
                    
                    MatchingResultsCard(
                        matchResult = matchResult!!,
                        contactlessBitmap = capturedBitmap,
                        bestMatchBitmap = bestMatchBitmap,
                        selectedEnrollmentId = selectedEnrollmentId
                    )
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
            containerColor = statusColor.copy(alpha = 0.12f)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, statusColor.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.Card + 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.CardPadding + 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
        ) {
            // Status badge (text-based, no icon)
            Surface(
                shape = BorderRadius.Medium,
                color = statusColor,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.headlineLarge,
                    color = BackgroundWhite,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = "Overall Quality: ${String.format("%.1f%%", qualityResult.overallScore * 100)}",
                style = MaterialTheme.typography.displaySmall,
                color = PrimaryText,
                fontWeight = FontWeight.SemiBold
            )
            
            LinearProgressIndicator(
                progress = { qualityResult.overallScore },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
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
            containerColor = LightGray.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.Card
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.TightGap)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = PrimaryText,
                    fontWeight = FontWeight.Medium
                )
                LinearProgressIndicator(
                    progress = { score },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.2f)
                )
            }
            
            // Score badge
            Surface(
                shape = BorderRadius.Small,
                color = statusColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "${String.format("%.0f%%", score * 100)}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.headlineLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Hero Image Preview Component
@Composable
fun HeroImagePreview(
    rawBitmap: Bitmap?,
    enhancedBitmap: Bitmap,
    showRawImage: Boolean,
    onToggleRaw: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
    ) {
        // Toggle chips
        if (rawBitmap != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !showRawImage,
                    onClick = { onToggleRaw(false) },
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
                    onClick = { onToggleRaw(true) },
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
        
        // Larger hero image card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            shape = BorderRadius.Large,
            colors = CardDefaults.cardColors(
                containerColor = LightGray.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = Elevation.Card + 1.dp
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = if (showRawImage && rawBitmap != null) {
                        rawBitmap.asImageBitmap()
                    } else {
                        enhancedBitmap.asImageBitmap()
                    },
                    contentDescription = if (showRawImage) "Raw finger image" else "Enhanced finger image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        
        // Label
        Text(
            text = if (showRawImage) "Raw Image" else "Enhanced Image",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

// Section Header Component
@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.SmallGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = PrimaryText,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        // Subtle divider line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(BorderGray.copy(alpha = 0.5f))
        )
    }
}

// Matching Results Card for Split View
@Composable
fun MatchingResultsCard(
    matchResult: MatchResult,
    contactlessBitmap: Bitmap?,
    bestMatchBitmap: Bitmap?,
    selectedEnrollmentId: String?
) {
    val resultColor = if (matchResult.isMatch) SuccessGreen else DangerRed
    val enrollment = remember(selectedEnrollmentId) {
        selectedEnrollmentId?.let { EnrollmentRepository.getEnrollment(it) }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BorderRadius.Large,
        colors = CardDefaults.cardColors(
            containerColor = resultColor.copy(alpha = 0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, resultColor.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.Card + 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.CardPadding + 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
        ) {
            // Match/No Match status
            Surface(
                shape = BorderRadius.Medium,
                color = resultColor,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = if (matchResult.isMatch) "MATCH" else "NO MATCH",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.displaySmall,
                    color = BackgroundWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            // Similarity score
            Text(
                text = "Similarity: ${String.format("%.1f%%", matchResult.similarityScore * 100)}",
                style = MaterialTheme.typography.displaySmall,
                color = PrimaryText,
                fontWeight = FontWeight.SemiBold
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { matchResult.similarityScore },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = resultColor,
                trackColor = resultColor.copy(alpha = 0.2f)
            )

            // Confidence
            Text(
                text = "Confidence: ${String.format("%.1f%%", matchResult.confidence * 100)}",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText
            )

            // Comparison Images
            Spacer(modifier = Modifier.height(Spacing.SmallGap))
            HorizontalDivider(color = BorderGray)
            Spacer(modifier = Modifier.height(Spacing.SmallGap))

            Text(
                text = "Comparison Images",
                style = MaterialTheme.typography.displaySmall,
                color = PrimaryText,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
            ) {
                // Contactless image
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.TightGap)
                ) {
                    Text(
                        text = "Contactless",
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryText
                    )
                    if (contactlessBitmap != null) {
                        Card(
                            modifier = Modifier
                                .height(140.dp)
                                .fillMaxWidth(),
                            shape = BorderRadius.Medium,
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = Elevation.Card
                            )
                        ) {
                            Image(
                                bitmap = contactlessBitmap.asImageBitmap(),
                                contentDescription = "Contactless fingerprint",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Best match contact-based image
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.TightGap)
                ) {
                    Text(
                        text = "Best Match",
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryText
                    )
                    if (bestMatchBitmap != null) {
                        Card(
                            modifier = Modifier
                                .height(140.dp)
                                .fillMaxWidth(),
                            shape = BorderRadius.Medium,
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = Elevation.Card
                            )
                        ) {
                            Image(
                                bitmap = bestMatchBitmap!!.asImageBitmap(),
                                contentDescription = "Best matching contact-based fingerprint",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .height(140.dp)
                                .fillMaxWidth(),
                            shape = BorderRadius.Medium,
                            colors = CardDefaults.cardColors(
                                containerColor = LightGray
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No image",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TertiaryText
                                )
                            }
                        }
                    }
                }
            }

            // Enrollment info
            if (enrollment != null) {
                Spacer(modifier = Modifier.height(Spacing.SmallGap))
                HorizontalDivider(color = BorderGray)
                Spacer(modifier = Modifier.height(Spacing.SmallGap))
                Text(
                    text = "Matched against: ${enrollment.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
                Text(
                    text = "Name: ${enrollment.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
            }
        }
    }
}
