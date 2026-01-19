package com.contactless.fingerprint.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Design System Constants
 * Based on the provided UI Style Guide
 */

// Spacing System
object Spacing {
    // Padding
    val CardPadding = 20.dp
    val ScreenPadding = 32.dp
    val ButtonVerticalPadding = 14.dp
    val SmallPadding = 12.dp
    
    // Gaps
    val TightGap = 2.dp
    val SmallGap = 12.dp
    val MediumGap = 14.dp
    val LargeGap = 16.dp
    val ExtraLargeGap = 32.dp
    
    // Margins
    val SectionMargin = 40.dp
    val ElementMargin = 32.dp
    val SmallMargin = 16.dp
}

// Border Radius
object BorderRadius {
    val Small: RoundedCornerShape = RoundedCornerShape(13.dp) // Small buttons, toggles
    val Medium: RoundedCornerShape = RoundedCornerShape(18.dp) // Buttons
    val Large: RoundedCornerShape = RoundedCornerShape(22.dp) // Cards
    val ExtraLarge: RoundedCornerShape = RoundedCornerShape(28.dp) // Large cards
    val ExtraLarge2: RoundedCornerShape = RoundedCornerShape(30.dp)
    val ExtraLarge3: RoundedCornerShape = RoundedCornerShape(36.dp)
    val ExtraLarge4: RoundedCornerShape = RoundedCornerShape(40.dp) // Modals
    val Full: RoundedCornerShape = RoundedCornerShape(50) // Badges, icons, toggles
}

// Shadows (using elevation in Material 3)
object Elevation {
    val Card = 2.dp // Equivalent to shadow: 0 4px 24px rgba(0, 0, 0, 0.04)
    val ButtonPrimary = 4.dp
    val ButtonDanger = 3.dp
    val Glow = 8.dp // For status indicators
}

// Component Sizes
object ComponentSize {
    val ToggleWidth = 46.dp
    val ToggleHeight = 28.dp
    val ToggleThumb = 24.dp
    val StatusDot = 8.dp
    val BadgePaddingHorizontal = 10.dp // px-2.5
    val BadgePaddingVertical = 2.dp // py-0.5
}

// Animation Durations (iOS-inspired)
object AnimationDuration {
    val Standard = 300 // ms - Standard transitions
    val Slow = 700 // ms - Slow transitions
    val VerySlow = 800 // ms - Very slow transitions
    val Fast = 200 // ms - Fast interactions
    val Instant = 0 // ms - Immediate feedback
}

// Animation Easing (iOS-inspired)
object AnimationEasing {
    // Standard ease-in-out for most transitions
    val StandardEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    
    // Ease-out for entrances (iOS default)
    val EaseOut: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    
    // Ease-in for exits
    val EaseIn: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
    
    // Sharp curve for quick interactions
    val Sharp: Easing = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f)
}

// Interaction States (iOS-inspired)
object InteractionStates {
    // Button press scale (iOS uses ~0.98)
    val ButtonPressedScale = 0.98f
    
    // Button pressed opacity
    val ButtonPressedOpacity = 0.9f
    
    // Card press scale
    val CardPressedScale = 0.99f
    
    // Ripple alpha (Material default is good, but we can customize)
    val RippleAlpha = 0.12f
}
