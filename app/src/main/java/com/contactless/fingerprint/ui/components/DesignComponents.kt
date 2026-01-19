package com.contactless.fingerprint.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.contactless.fingerprint.ui.theme.*

/**
 * iOS-inspired Design Components
 * Following iOS Human Interface Guidelines principles:
 * - Clean, minimal design
 * - Touch-optimized interactions
 * - High contrast
 * - Smooth animations
 * - Visual feedback
 * - Professional aesthetic
 * - Consistent spacing
 */

/**
 * Primary Button with iOS-inspired press animation
 * - Scales down to 0.98 on press (iOS standard)
 * - Smooth 200ms animation
 * - High contrast colors
 * - Professional shadow
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) InteractionStates.ButtonPressedScale else 1f,
        animationSpec = tween(
            durationMillis = AnimationDuration.Fast,
            easing = AnimationEasing.EaseOut
        ),
        label = "button_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) InteractionStates.ButtonPressedOpacity else 1f,
        animationSpec = tween(
            durationMillis = AnimationDuration.Fast,
            easing = AnimationEasing.EaseOut
        ),
        label = "button_alpha"
    )
    
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .scale(scale)
            .graphicsLayer { this.alpha = alpha },
        shape = BorderRadius.Medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryBlue,
            contentColor = BackgroundWhite,
            disabledContainerColor = TertiaryText,
            disabledContentColor = BackgroundWhite.copy(alpha = 0.3f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.ButtonPrimary,
            pressedElevation = 2.dp,
            disabledElevation = 0.dp
        ),
        interactionSource = interactionSource
    ) {
        content()
    }
}

/**
 * Secondary Button (Outlined) with iOS-inspired design
 */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) InteractionStates.ButtonPressedScale else 1f,
        animationSpec = tween(
            durationMillis = AnimationDuration.Fast,
            easing = AnimationEasing.EaseOut
        ),
        label = "button_scale"
    )
    
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.scale(scale),
        shape = BorderRadius.Medium,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PrimaryText,
            disabledContentColor = TertiaryText
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (enabled) BorderGray else TertiaryText
        ),
        interactionSource = interactionSource
    ) {
        content()
    }
}

/**
 * Interactive Card with iOS-inspired press feedback
 * - Subtle scale on press
 * - Smooth transition
 * - High contrast borders
 */
@Composable
fun InteractiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) InteractionStates.CardPressedScale else 1f,
        animationSpec = tween(
            durationMillis = AnimationDuration.Fast,
            easing = AnimationEasing.EaseOut
        ),
        label = "card_scale"
    )
    
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.scale(scale),
        shape = BorderRadius.Large,
        colors = CardDefaults.cardColors(
            containerColor = BackgroundWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.Card,
            pressedElevation = 1.dp
        ),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier.padding(Spacing.CardPadding),
            content = content
        )
    }
}

/**
 * Status Indicator with glow effect (iOS-inspired)
 * - Smooth glow animation for active states
 * - High contrast colors
 */
@Composable
fun StatusIndicator(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = SuccessGreen,
    inactiveColor: Color = TertiaryText
) {
    val animatedColor by animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveColor,
        animationSpec = tween<Color>(
            durationMillis = AnimationDuration.Standard,
            easing = AnimationEasing.StandardEasing
        ),
        label = "status_color"
    )
    
    Box(
        modifier = modifier
            .size(ComponentSize.StatusDot)
            .then(
                if (isActive) {
                    Modifier.shadow(
                        elevation = Elevation.Glow,
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .background(
                color = animatedColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Empty content - the background provides the visual indicator
    }
}

/**
 * Animated Progress Indicator with smooth transitions
 */
@Composable
fun AnimatedProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = PrimaryBlue,
    trackColor: Color = BorderGray.copy(alpha = 0.2f)
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = AnimationDuration.Standard,
            easing = AnimationEasing.EaseOut
        ),
        label = "progress"
    )
    
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier,
        color = color,
        trackColor = trackColor
    )
}
