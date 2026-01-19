package com.contactless.fingerprint.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.contactless.fingerprint.ui.theme.*
import com.contactless.fingerprint.matching.EnrollmentRepository
import com.contactless.fingerprint.matching.FingerprintEnrollment
import com.contactless.fingerprint.matching.FeatureExtractor
import com.contactless.fingerprint.matching.Matcher
import com.contactless.fingerprint.matching.MatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Matching Screen - Track C
 *
 * Step 2.1: Gallery picker integration.
 * - Allows user to pick one or more contact-based fingerprint images from gallery.
 * - Stores selected URIs in local state (will be used for enrollment in next step).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchingScreen(
    contactlessBitmap: Bitmap? = null, // Contactless captured image (from CameraScreen)
    onBackClick: () -> Unit,
    onCaptureClick: () -> Unit = {}, // Navigate to camera to capture contactless
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val featureExtractor = remember { FeatureExtractor() }
    val matcher = remember { Matcher() }

    // Local state: selected contact-based fingerprint images (URIs)
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Local state: enrollment dialog visibility and fields
    var showEnrollDialog by remember { mutableStateOf(false) }
    var enrollmentId by remember { mutableStateOf("") }
    var enrollmentName by remember { mutableStateOf("") }

    // Step 2.3: Local state for selected enrollment ID
    var selectedEnrollmentId by remember { mutableStateOf<String?>(null) }
    
    // Track enrollment count to trigger recomposition when enrollments change
    var enrollmentCount by remember { mutableStateOf(EnrollmentRepository.getCount()) }
    
    // Delete confirmation dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var enrollmentToDelete by remember { mutableStateOf<FingerprintEnrollment?>(null) }

    // Step 5.1: Matching state
    var isMatching by remember { mutableStateOf(false) }
    var matchResult by remember { mutableStateOf<MatchResult?>(null) }
    var bestMatchImageUri by remember { mutableStateOf<Uri?>(null) }
    var bestMatchBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Get all enrollments from repository (reactive to enrollmentCount)
    val allEnrollments = remember(enrollmentCount) { EnrollmentRepository.getAllEnrollments() }
    val selectedEnrollment = remember(selectedEnrollmentId) {
        selectedEnrollmentId?.let { EnrollmentRepository.getEnrollment(it) }
    }

    // Gallery picker for images (photos & images only)
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNullOrEmpty()) {
            Toast.makeText(context, "No images selected", Toast.LENGTH_SHORT).show()
        } else {
            selectedImages = uris
            Toast.makeText(
                context,
                "Selected ${uris.size} fingerprint image(s)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Fingerprint Matching",
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.LargeGap)
        ) {
            Spacer(modifier = Modifier.height(Spacing.ElementMargin))

            Text(
                text = "Fingerprint Matching",
                style = MaterialTheme.typography.displayLarge,
                color = PrimaryText
            )

            Text(
                text = "Track C: Contactless-to-Contact Matching",
                style = MaterialTheme.typography.bodyLarge,
                color = SecondaryText
            )

            Spacer(modifier = Modifier.height(Spacing.LargeGap))

            // Step 2.1: Gallery picker button
            Button(
                onClick = {
                    // Launch system picker for images (contact-based fingerprints)
                    pickImagesLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
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
                    "Select Contact-based Fingerprints from Gallery",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Simple status card showing how many images are selected
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
                    Text(
                        text = "Enrollment Images",
                        style = MaterialTheme.typography.displaySmall,
                        color = PrimaryText
                    )
                    if (selectedImages.isEmpty()) {
                        Text(
                            text = "No contact-based fingerprint images selected yet.\n" +
                                    "Use the button above to pick images from the gallery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    } else {
                        Text(
                            text = "Selected ${selectedImages.size} image(s).\n" +
                                    "Next step: link them to an ID (enrollment).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )

                        Spacer(modifier = Modifier.height(Spacing.SmallGap))

                        // Step 2.2: Enrollment button
                        Button(
                            onClick = {
                                enrollmentId = ""
                                enrollmentName = ""
                                showEnrollDialog = true
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
                                "Enroll Selected Images with ID",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // Step 2.2: Enrollment dialog
            if (showEnrollDialog) {
                AlertDialog(
                    onDismissRequest = { showEnrollDialog = false },
                    shape = BorderRadius.ExtraLarge4,
                    title = {
                        Text(
                            text = "Enroll Fingerprints",
                            style = MaterialTheme.typography.displaySmall,
                            color = PrimaryText
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                        ) {
                            Text(
                                text = "Assign an ID to these contact-based fingerprint images.\n" +
                                        "You will later match contactless captures against this ID.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )

                            OutlinedTextField(
                                value = enrollmentId,
                                onValueChange = { enrollmentId = it },
                                label = { 
                                    Text(
                                        "Enrollment ID (e.g., P001)",
                                        style = MaterialTheme.typography.bodyMedium
                                    ) 
                                },
                                singleLine = true,
                                shape = BorderRadius.Medium
                            )

                            OutlinedTextField(
                                value = enrollmentName,
                                onValueChange = { enrollmentName = it },
                                label = { 
                                    Text(
                                        "Name / Description (optional)",
                                        style = MaterialTheme.typography.bodyMedium
                                    ) 
                                },
                                singleLine = true,
                                shape = BorderRadius.Medium
                            )

                            Text(
                                text = "Images selected: ${selectedImages.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TertiaryText
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val trimmedId = enrollmentId.trim()
                                if (trimmedId.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "Please enter an Enrollment ID",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@TextButton
                                }

                                if (selectedImages.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "No images selected for enrollment",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@TextButton
                                }

                                val enrollment = FingerprintEnrollment(
                                    id = trimmedId,
                                    name = enrollmentName.ifBlank { trimmedId },
                                    imageUris = selectedImages
                                )

                                // Use coroutine for async enrollment (copies images to storage)
                                coroutineScope.launch(Dispatchers.Main) {
                                    val added = EnrollmentRepository.addEnrollment(enrollment)
                                    if (added) {
                                        Toast.makeText(
                                            context,
                                            "Enrolled ID $trimmedId with ${selectedImages.size} image(s)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        // Clear selected images and refresh enrollment list
                                        selectedImages = emptyList()
                                        enrollmentCount = EnrollmentRepository.getCount()
                                        showEnrollDialog = false
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "ID '$trimmedId' already exists. Each ID must be unique.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = PrimaryBlue
                            )
                        ) {
                            Text(
                                "Enroll",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showEnrollDialog = false },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = SecondaryText
                            )
                        ) {
                            Text(
                                "Cancel",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                )
            }

            // Step 2.3: Display Enrolled IDs section
            Spacer(modifier = Modifier.height(Spacing.SmallGap))

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
                    verticalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
                ) {
                    Text(
                        text = "Enrolled Fingerprints",
                        style = MaterialTheme.typography.displaySmall,
                        color = PrimaryText
                    )

                    if (allEnrollments.isEmpty()) {
                        Text(
                            text = "No enrollments yet.\n" +
                                    "Select images above and enroll them with an ID.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    } else {
                        Text(
                            text = "Select an enrolled ID to match against:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )

                        // List of enrolled IDs (selectable)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                        ) {
                            allEnrollments.forEach { enrollment ->
                                val isSelected = selectedEnrollmentId == enrollment.id
                                Card(
                                    onClick = {
                                        selectedEnrollmentId = if (isSelected) null else enrollment.id
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = BorderRadius.Large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            PrimaryBlueLight
                                        } else {
                                            LightGray
                                        }
                                    ),
                                    border = if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(2.dp, PrimaryBlue)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                                    },
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (isSelected) Elevation.Card else 0.dp
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
                                                text = enrollment.id,
                                                style = MaterialTheme.typography.displaySmall,
                                                color = PrimaryText
                                            )
                                            Text(
                                                text = enrollment.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SecondaryText
                                            )
                                            Text(
                                                text = "${enrollment.imageUris.size} image(s)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TertiaryText
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallGap),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Delete button
                                            IconButton(
                                                onClick = {
                                                    enrollmentToDelete = enrollment
                                                    showDeleteDialog = true
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete enrollment",
                                                    tint = DangerRed
                                                )
                                            }
                                            // Selection indicator
                                            if (isSelected) {
                                                Text(
                                                    text = "✓",
                                                    style = MaterialTheme.typography.displaySmall,
                                                    color = PrimaryBlue
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Show selected enrollment details
                        if (selectedEnrollment != null) {
                            Spacer(modifier = Modifier.height(Spacing.SmallGap))
                            Divider(color = BorderGray)
                            Spacer(modifier = Modifier.height(Spacing.SmallGap))
                            Text(
                                text = "Selected: ${selectedEnrollment.id}",
                                style = MaterialTheme.typography.headlineLarge,
                                color = PrimaryText
                            )
                            Text(
                                text = "Name: ${selectedEnrollment.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText
                            )
                            Text(
                                text = "Images: ${selectedEnrollment.imageUris.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText
                            )
                            Text(
                                text = "Ready for matching with contactless capture.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryBlue
                            )

                            // Step 5.1: Match button and contactless image section
                            Spacer(modifier = Modifier.height(Spacing.MediumGap))
                            Divider(color = BorderGray)
                            Spacer(modifier = Modifier.height(Spacing.MediumGap))

                            // Contactless image section
                            Text(
                                text = "Contactless Fingerprint",
                                style = MaterialTheme.typography.displaySmall,
                                color = PrimaryText
                            )

                            if (contactlessBitmap == null) {
                                Text(
                                    text = "No contactless image captured yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryText
                                )
                                Button(
                                    onClick = onCaptureClick,
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
                                        "Capture Contactless Finger",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            } else {
                                Text(
                                    text = "Contactless image ready for matching.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PrimaryBlue
                                )
                                Button(
                                    onClick = onCaptureClick,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = BorderRadius.Medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LightGray,
                                        contentColor = PrimaryText
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                                ) {
                                    Text(
                                        "Capture New Contactless Finger",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }

                            // Match button
                            Spacer(modifier = Modifier.height(Spacing.SmallGap))
                            Button(
                                onClick = {
                                    if (contactlessBitmap == null) {
                                        Toast.makeText(
                                            context,
                                            "Please capture a contactless finger first",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@Button
                                    }

                                    if (selectedEnrollment == null) {
                                        Toast.makeText(
                                            context,
                                            "Please select an enrolled ID to match against",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@Button
                                    }

                                    // Check if enrollment has images
                                    if (selectedEnrollment.imageUris.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "This enrollment has no images. Please add images first.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@Button
                                    }

                                    // Step 5.1: Perform matching
                                    isMatching = true
                                    matchResult = null
                                    bestMatchImageUri = null

                                    coroutineScope.launch(Dispatchers.Default) {
                                        val totalStart = System.currentTimeMillis()
                                        try {
                                            // Extract features from contactless image
                                            // Contactless image is already enhanced from CameraScreen
                                            val contactlessStart = System.currentTimeMillis()
                                            val contactlessFeatures = featureExtractor.extractFeatures(contactlessBitmap, isFromGallery = false)
                                            val contactlessTime = System.currentTimeMillis() - contactlessStart
                                            Log.d("MatchingScreen", "Contactless feature extraction took ${contactlessTime}ms")

                                            // Extract features from all contact-based images for this enrollment
                                            var bestSimilarity = 0f
                                            var bestMatch: MatchResult? = null
                                            var bestUri: Uri? = null
                                            var loadedCount = 0
                                            var failedCount = 0

                                            for (uri in selectedEnrollment.imageUris) {
                                                // Load bitmap from URI
                                                val contactBitmap = withContext(Dispatchers.IO) {
                                                    try {
                                                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                                            BitmapFactory.decodeStream(inputStream)
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("MatchingScreen", "Failed to load image from URI: $uri", e)
                                                        null
                                                    }
                                                }

                                                if (contactBitmap != null) {
                                                    loadedCount++
                                                    try {
                                                        // Extract features (mark as from gallery)
                                                        val contactStart = System.currentTimeMillis()
                                                        val contactFeatures = featureExtractor.extractFeatures(contactBitmap, isFromGallery = true)
                                                        val contactTime = System.currentTimeMillis() - contactStart
                                                        Log.d("MatchingScreen", "Contact feature extraction took ${contactTime}ms")
                                                        
                                                        Log.d("MatchingScreen", "Extracted features - Contactless: ${contactlessFeatures.minutiae.size} minutiae, Contact: ${contactFeatures.minutiae.size} minutiae")

                                                        // Compute similarity
                                                        val similarityStart = System.currentTimeMillis()
                                                        val similarity = matcher.computeSimilarity(contactlessFeatures, contactFeatures)
                                                        val similarityTime = System.currentTimeMillis() - similarityStart
                                                        Log.d("MatchingScreen", "Similarity computation took ${similarityTime}ms")
                                                        
                                                        Log.d("MatchingScreen", "Similarity score: $similarity (threshold: ${com.contactless.fingerprint.utils.Constants.MATCH_THRESHOLD})")

                                                        // Track best match
                                                        if (similarity > bestSimilarity) {
                                                            bestSimilarity = similarity
                                                            bestMatch = matcher.match(contactlessFeatures, contactFeatures)
                                                            bestUri = uri
                                                            Log.d("MatchingScreen", "New best match: similarity=$similarity, isMatch=${bestMatch?.isMatch}")
                                                        }

                                                        contactBitmap.recycle()
                                                    } catch (e: Exception) {
                                                        Log.e("MatchingScreen", "Error processing image: $uri", e)
                                                        contactBitmap.recycle()
                                                        failedCount++
                                                    }
                                                } else {
                                                    failedCount++
                                                    Log.w("MatchingScreen", "Could not load bitmap from URI: $uri")
                                                }
                                            }

                                            // Load best match image bitmap for display
                                            val bestMatchImage = bestUri?.let { uri ->
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                                            BitmapFactory.decodeStream(inputStream)
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("MatchingScreen", "Failed to reload best match image: $uri", e)
                                                        null
                                                    }
                                                }
                                            }

                                            // Update UI with results
                                            val totalTime = System.currentTimeMillis() - totalStart
                                            Log.d("MatchingScreen", "Total matching process took ${totalTime}ms for ${loadedCount} image(s)")
                                            
                                            withContext(Dispatchers.Main) {
                                                matchResult = bestMatch
                                                bestMatchImageUri = bestUri
                                                bestMatchBitmap = bestMatchImage
                                                isMatching = false

                                                if (bestMatch != null) {
                                                    Toast.makeText(
                                                        context,
                                                        "Match result: ${if (bestMatch.isMatch) "MATCH" else "NO MATCH"} (${(bestMatch.similarityScore * 100).toInt()}%)",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    // Provide more specific error message
                                                    val errorMsg = when {
                                                        loadedCount == 0 && failedCount > 0 -> 
                                                            "Could not load any images from enrollment (${failedCount} failed)"
                                                        loadedCount == 0 -> 
                                                            "No images found in enrollment"
                                                        else -> 
                                                            "Matching completed but no valid matches found (processed $loadedCount image(s))"
                                                    }
                                                    Toast.makeText(
                                                        context,
                                                        errorMsg,
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    Log.w("MatchingScreen", "Matching failed: loaded=$loadedCount, failed=$failedCount")
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
                                },
                                enabled = !isMatching && contactlessBitmap != null && selectedEnrollment != null,
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
                                        "Match Contactless to Selected ID",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }

                            // Step 5.2: Display Results
                            if (matchResult != null) {
                                Spacer(modifier = Modifier.height(Spacing.LargeGap))
                                Divider(color = BorderGray)
                                Spacer(modifier = Modifier.height(Spacing.LargeGap))

                                val resultColor = if (matchResult!!.isMatch) SuccessGreen else DangerRed
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = BorderRadius.Large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = resultColor.copy(alpha = 0.1f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, resultColor.copy(alpha = 0.3f)),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = Elevation.Card
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Spacing.CardPadding),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
                                    ) {
                                        // Match/No Match status
                                        Text(
                                            text = if (matchResult!!.isMatch) "✓ MATCH" else "✗ NO MATCH",
                                            style = MaterialTheme.typography.displaySmall,
                                            color = resultColor
                                        )

                                        // Similarity score
                                        Text(
                                            text = "Similarity: ${String.format("%.1f%%", matchResult!!.similarityScore * 100)}",
                                            style = MaterialTheme.typography.displaySmall,
                                            color = PrimaryText
                                        )

                                        // Progress bar
                                        LinearProgressIndicator(
                                            progress = { matchResult!!.similarityScore },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = resultColor,
                                            trackColor = resultColor.copy(alpha = 0.2f)
                                        )

                                        // Confidence
                                        Text(
                                            text = "Confidence: ${String.format("%.1f%%", matchResult!!.confidence * 100)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SecondaryText
                                        )

                                        // Image thumbnails
                                        Spacer(modifier = Modifier.height(Spacing.SmallGap))
                                        Divider(color = BorderGray)
                                        Spacer(modifier = Modifier.height(Spacing.SmallGap))

                                        Text(
                                            text = "Comparison Images",
                                            style = MaterialTheme.typography.displaySmall,
                                            color = PrimaryText
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
                                        ) {
                                            // Contactless image thumbnail
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
                                                            .height(120.dp)
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

                                            // Best match contact-based image thumbnail
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(Spacing.TightGap)
                                            ) {
                                                Text(
                                                    text = "Best Match (Contact-based)",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = PrimaryText
                                                )
                                                if (bestMatchBitmap != null) {
                                                    Card(
                                                        modifier = Modifier
                                                            .height(120.dp)
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
                                                            .height(120.dp)
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

                                        // Selected enrollment info
                                        if (selectedEnrollment != null) {
                                            Spacer(modifier = Modifier.height(Spacing.SmallGap))
                                            Divider(color = BorderGray)
                                            Spacer(modifier = Modifier.height(Spacing.SmallGap))
                                            Text(
                                                text = "Matched against: ${selectedEnrollment.id}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SecondaryText
                                            )
                                            Text(
                                                text = "Name: ${selectedEnrollment.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SecondaryText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Delete confirmation dialog
            if (showDeleteDialog && enrollmentToDelete != null) {
                AlertDialog(
                    onDismissRequest = { 
                        showDeleteDialog = false
                        enrollmentToDelete = null
                    },
                    shape = BorderRadius.ExtraLarge4,
                    title = {
                        Text(
                            text = "Delete Enrollment",
                            style = MaterialTheme.typography.displaySmall,
                            color = PrimaryText
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                        ) {
                            Text(
                                text = "Are you sure you want to delete enrollment:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                            Text(
                                text = "ID: ${enrollmentToDelete!!.id}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = PrimaryText,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Name: ${enrollmentToDelete!!.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                            Text(
                                text = "Images: ${enrollmentToDelete!!.imageUris.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                            Spacer(modifier = Modifier.height(Spacing.SmallGap))
                            Text(
                                text = "This will permanently delete the enrollment and all associated images from storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerRed
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val idToDelete = enrollmentToDelete!!.id
                                val deleted = EnrollmentRepository.removeEnrollment(idToDelete)
                                
                                if (deleted) {
                                    Toast.makeText(
                                        context,
                                        "Deleted enrollment: $idToDelete",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    
                                    // Clear selection if deleted enrollment was selected
                                    if (selectedEnrollmentId == idToDelete) {
                                        selectedEnrollmentId = null
                                    }
                                    
                                    // Refresh enrollment list
                                    enrollmentCount = EnrollmentRepository.getCount()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Failed to delete enrollment: $idToDelete",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                
                                showDeleteDialog = false
                                enrollmentToDelete = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = DangerRed
                            )
                        ) {
                            Text(
                                "Delete",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { 
                                showDeleteDialog = false
                                enrollmentToDelete = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = SecondaryText
                            )
                        ) {
                            Text(
                                "Cancel",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                )
            }
        }
    }
}

