package com.contactless.fingerprint.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.contactless.fingerprint.ui.components.InteractiveCard
import com.contactless.fingerprint.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onCaptureClick: () -> Unit,
    onMatchingClick: () -> Unit = {}
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
                .padding(horizontal = Spacing.ScreenPadding)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Section with animated icon
            Spacer(modifier = Modifier.height(Spacing.ElementMargin))
            
            HeroSection()
            
            Spacer(modifier = Modifier.height(Spacing.ExtraLargeGap))
            
            // Feature Cards with staggered animation
            FeatureCard(
                title = "Start Capture",
                description = "Select an enrollment ID, then capture contactless finger",
                icon = Icons.Default.Add,
                iconColor = PrimaryBlue,
                onClick = onCaptureClick,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(Spacing.MediumGap))
            
            FeatureCard(
                title = "Fingerprint Matching",
                description = "Enroll contact-based prints and match with contactless captures",
                icon = Icons.Default.Search,
                iconColor = SuccessGreen,
                onClick = onMatchingClick,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(Spacing.LargeGap))
        }
    }
}

@Composable
fun HeroSection() {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = AnimationEasing.StandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.MediumGap)
    ) {
        // Animated icon container
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrimaryBlue.copy(alpha = 0.2f),
                            PrimaryBlue.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Fingerprint Icon",
                modifier = Modifier.size(64.dp),
                tint = PrimaryBlue
            )
        }
        
        // Title
        Text(
            text = "Contactless Fingerprint",
            style = MaterialTheme.typography.displayLarge,
            color = PrimaryText,
            textAlign = TextAlign.Center
        )
        
        // Subtitle
        Text(
            text = "Capture, enhance, assess quality, and match fingerprints seamlessly",
            style = MaterialTheme.typography.bodyLarge,
            color = SecondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.LargeGap)
        )
    }
}

@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    InteractiveCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.MediumGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container with gradient background
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(BorderRadius.Medium)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                iconColor.copy(alpha = 0.15f),
                                iconColor.copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = iconColor
                )
            }
            
            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.TightGap)
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
            
            // Arrow indicator
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = TertiaryText
            )
        }
    }
}
