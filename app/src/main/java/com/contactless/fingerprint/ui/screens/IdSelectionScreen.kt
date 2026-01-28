package com.contactless.fingerprint.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contactless.fingerprint.matching.EnrollmentRepository
import com.contactless.fingerprint.matching.FingerprintEnrollment
import com.contactless.fingerprint.ui.theme.*
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdSelectionScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    onIdSelected: (String) -> Unit // Callback when ID is selected
) {
    val context = LocalContext.current
    
    // Track enrollment count to trigger recomposition when enrollments change
    var enrollmentCount by remember { mutableStateOf(EnrollmentRepository.getCount()) }
    val allEnrollments = remember(enrollmentCount) { EnrollmentRepository.getAllEnrollments() }
    
    // Delete confirmation dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var enrollmentToDelete by remember { mutableStateOf<FingerprintEnrollment?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Select Enrollment ID",
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

            Text(
                text = "Select an Enrollment ID",
                style = MaterialTheme.typography.displaySmall,
                color = PrimaryText,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Choose an ID to match against",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.MediumGap))

            if (allEnrollments.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BorderRadius.Large,
                    colors = CardDefaults.cardColors(
                        containerColor = LightGray.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.CardPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
                    ) {
                        Text(
                            text = "No enrollments found",
                            style = MaterialTheme.typography.headlineLarge,
                            color = PrimaryText,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Please create enrollment IDs first using the 'Fingerprint Matching' option from the home screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // List of enrolled IDs
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SmallGap)
                ) {
                    allEnrollments.forEach { enrollment ->
                        Card(
                            onClick = {
                                onIdSelected(enrollment.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = BorderRadius.Large,
                            colors = CardDefaults.cardColors(
                                containerColor = BackgroundWhite
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
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
                                        text = enrollment.id,
                                        style = MaterialTheme.typography.displaySmall,
                                        color = PrimaryText,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = enrollment.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SecondaryText
                                    )
                                    Text(
                                        text = "${enrollment.imageUris.size} image(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TertiaryText
                                    )
                                }
                                
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
