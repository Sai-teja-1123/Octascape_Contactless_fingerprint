package com.contactless.fingerprint.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contactless.fingerprint.quality.QualityResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityScreen(
    qualityResult: QualityResult?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

                Spacer(modifier = Modifier.height(8.dp))

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
