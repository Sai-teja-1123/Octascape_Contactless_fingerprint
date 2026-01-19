package com.contactless.fingerprint.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.contactless.fingerprint.ui.components.InteractiveCard
import com.contactless.fingerprint.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCaptureClick: () -> Unit,
    onMatchingClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Contactless Fingerprint",
                        style = MaterialTheme.typography.displayMedium
                    ) 
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
                .padding(Spacing.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.LargeGap)
        ) {
            Spacer(modifier = Modifier.height(Spacing.ElementMargin))

            Text(
                text = "Contactless Fingerprint",
                style = MaterialTheme.typography.displayLarge,
                color = PrimaryText
            )

            Text(
                text = "Capture, enhance, assess quality, and match",
                style = MaterialTheme.typography.bodyLarge,
                color = SecondaryText
            )

            Spacer(modifier = Modifier.height(Spacing.LargeGap))

            FeatureCard(
                title = "Start Capture",
                description = "Capture → Auto-enhance → Quality check → Auto-match",
                onClick = onCaptureClick,
                modifier = Modifier.fillMaxWidth()
            )

            FeatureCard(
                title = "Fingerprint Matching",
                description = "Enroll contact-based prints and match with contactless captures",
                onClick = onMatchingClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    InteractiveCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = PrimaryText
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText
        )
    }
}
