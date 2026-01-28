package com.contactless.fingerprint.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * Matching Screen - Enrollment Management Only
 * 
 * This screen is used only for managing enrollments:
 * - Create new enrollment IDs
 * - View all enrolled IDs
 * - Delete enrollment IDs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchingScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Local state: selected contact-based fingerprint images (URIs)
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Local state: enrollment dialog visibility and fields
    var showEnrollDialog by remember { mutableStateOf(false) }
    var enrollmentId by remember { mutableStateOf("") }
    var enrollmentName by remember { mutableStateOf("") }

    // Track enrollment count to trigger recomposition when enrollments change
    var enrollmentCount by remember { mutableStateOf(EnrollmentRepository.getCount()) }
    
    // Delete confirmation dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var enrollmentToDelete by remember { mutableStateOf<FingerprintEnrollment?>(null) }

    // Get all enrollments from repository (reactive to enrollmentCount)
    val allEnrollments = remember(enrollmentCount) { EnrollmentRepository.getAllEnrollments() }

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
            Spacer(modifier = Modifier.height(Spacing.MediumGap))

            // Enrollment Management Content (no tabs)
            EnrollTabContent(
                selectedImages = selectedImages,
                pickImagesLauncher = pickImagesLauncher,
                showEnrollDialog = showEnrollDialog,
                enrollmentId = enrollmentId,
                enrollmentName = enrollmentName,
                onEnrollmentIdChange = { enrollmentId = it },
                onEnrollmentNameChange = { enrollmentName = it },
                onShowEnrollDialogChange = { showEnrollDialog = it },
                onEnrollClick = {
                    val trimmedId = enrollmentId.trim()
                    if (trimmedId.isEmpty()) {
                        Toast.makeText(
                            context,
                            "Please enter an Enrollment ID",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@EnrollTabContent
                    }

                    if (selectedImages.isEmpty()) {
                        Toast.makeText(
                            context,
                            "No images selected for enrollment",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@EnrollTabContent
                    }

                    val enrollment = FingerprintEnrollment(
                        id = trimmedId,
                        name = enrollmentName.ifBlank { trimmedId },
                        imageUris = selectedImages
                    )

                    coroutineScope.launch(Dispatchers.Main) {
                        val added = EnrollmentRepository.addEnrollment(enrollment)
                        if (added) {
                            Toast.makeText(
                                context,
                                "Enrolled ID $trimmedId with ${selectedImages.size} image(s)",
                                Toast.LENGTH_SHORT
                            ).show()
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
                allEnrollments = allEnrollments,
                selectedEnrollmentId = null,
                onSelectedEnrollmentIdChange = { },
                enrollmentToDelete = enrollmentToDelete,
                showDeleteDialog = showDeleteDialog,
                onShowDeleteDialogChange = { showDeleteDialog = it },
                onEnrollmentToDeleteChange = { enrollmentToDelete = it },
                onDeleteConfirm = {
                    val idToDelete = enrollmentToDelete!!.id
                    val deleted = EnrollmentRepository.removeEnrollment(idToDelete)
                    
                    if (deleted) {
                        Toast.makeText(
                            context,
                            "Deleted enrollment: $idToDelete",
                            Toast.LENGTH_SHORT
                        ).show()
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
                }
            )
        }
        
        // Enrollment Dialog
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

                            coroutineScope.launch(Dispatchers.Main) {
                                val added = EnrollmentRepository.addEnrollment(enrollment)
                                if (added) {
                                    Toast.makeText(
                                        context,
                                        "Enrolled ID $trimmedId with ${selectedImages.size} image(s)",
                                        Toast.LENGTH_SHORT
                                    ).show()
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
        
        // Delete Dialog
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

// Enroll Tab Content
@Composable
fun EnrollTabContent(
    selectedImages: List<Uri>,
    pickImagesLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
    showEnrollDialog: Boolean,
    enrollmentId: String,
    enrollmentName: String,
    onEnrollmentIdChange: (String) -> Unit,
    onEnrollmentNameChange: (String) -> Unit,
    onShowEnrollDialogChange: (Boolean) -> Unit,
    onEnrollClick: () -> Unit,
    allEnrollments: List<FingerprintEnrollment>,
    selectedEnrollmentId: String?,
    onSelectedEnrollmentIdChange: (String?) -> Unit,
    enrollmentToDelete: FingerprintEnrollment?,
    showDeleteDialog: Boolean,
    onShowDeleteDialogChange: (Boolean) -> Unit,
    onEnrollmentToDeleteChange: (FingerprintEnrollment?) -> Unit,
    onDeleteConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.LargeGap)
    ) {
        // Gallery picker button
        Button(
            onClick = {
                pickImagesLauncher.launch(
                    PickVisualMediaRequest(
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

        // Enrollment Images Status Card
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
                        color = SecondaryText,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Selected ${selectedImages.size} image(s).\n" +
                                "Next step: link them to an ID (enrollment).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Spacing.SmallGap))

                    Button(
                        onClick = {
                            onEnrollmentIdChange("")
                            onEnrollmentNameChange("")
                            onShowEnrollDialogChange(true)
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

        // Enrolled Fingerprints Section
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
                        color = SecondaryText,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Select an enrolled ID to match against:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                    ) {
                        allEnrollments.forEach { enrollment ->
                            val isSelected = selectedEnrollmentId == enrollment.id
                            Card(
                                onClick = {
                                    onSelectedEnrollmentIdChange(if (isSelected) null else enrollment.id)
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
                                        IconButton(
                                            onClick = {
                                                onEnrollmentToDeleteChange(enrollment)
                                                onShowDeleteDialogChange(true)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete enrollment",
                                                tint = DangerRed
                                            )
                                        }
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
                }
            }
        }
    }
}
