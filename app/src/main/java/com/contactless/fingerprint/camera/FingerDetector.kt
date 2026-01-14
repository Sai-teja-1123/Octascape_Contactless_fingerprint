package com.contactless.fingerprint.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Detects and segments finger in the camera frame
 * Track A: Finger detection/segmentation (rule-based)
 * 
 * Uses multiple heuristics:
 * - Skin color detection (HSV color space)
 * - Edge density analysis
 * - Shape analysis (aspect ratio, area)
 * - Center region focus (where finger box is)
 */
class FingerDetector {
    
    /**
     * Detects finger in the image, focusing on center region (where finger box is)
     */
    fun detectFinger(bitmap: Bitmap): FingerDetectionResult {
        // Focus on center region (where finger box is located)
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val regionWidth = (bitmap.width * 0.4f).toInt() // 40% of width
        val regionHeight = (bitmap.height * 0.4f).toInt() // 40% of height
        
        val startX = max(0, centerX - regionWidth / 2)
        val endX = min(bitmap.width, centerX + regionWidth / 2)
        val startY = max(0, centerY - regionHeight / 2)
        val endY = min(bitmap.height, centerY + regionHeight / 2)
        
        // Analyze the center region
        val skinScore = detectSkinColor(bitmap, startX, endX, startY, endY)
        val edgeScore = detectEdgeDensity(bitmap, startX, endX, startY, endY)
        val shapeScore = analyzeShape(bitmap, startX, endX, startY, endY)
        
        // Combined confidence (weighted average)
        val confidence = (skinScore * 0.4f + edgeScore * 0.4f + shapeScore * 0.2f).coerceIn(0f, 1f)
        
        // Finger is detected if confidence is above threshold (lowered to 0.35 for better sensitivity)
        val isDetected = confidence >= 0.35f
        
        // Calculate bounding box (expanded from center region if finger detected)
        val boundingBox = if (isDetected) {
            Rect(
                startX - (regionWidth * 0.1f).toInt(),
                startY - (regionHeight * 0.1f).toInt(),
                endX + (regionWidth * 0.1f).toInt(),
                endY + (regionHeight * 0.1f).toInt()
            ).apply {
                // Clamp to image bounds
                left = max(0, left)
                top = max(0, top)
                right = min(bitmap.width, right)
                bottom = min(bitmap.height, bottom)
            }
        } else {
            null
        }
        
        return FingerDetectionResult(
            isFingerDetected = isDetected,
            boundingBox = boundingBox,
            confidence = confidence
        )
    }
    
    /**
     * Detects skin color in HSV color space
     * Skin typically has: H: 0-50 or 150-180, S: 0.2-0.7, V: 0.3-1.0
     */
    private fun detectSkinColor(bitmap: Bitmap, startX: Int, endX: Int, startY: Int, endY: Int): Float {
        var skinPixels = 0
        var totalPixels = 0
        val step = 3 // Sample every 3rd pixel for performance
        
        for (x in startX until endX step step) {
            for (y in startY until endY step step) {
                if (x < bitmap.width && y < bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel) / 255f
                    val g = Color.green(pixel) / 255f
                    val b = Color.blue(pixel) / 255f
                    
                    // Convert RGB to HSV
                    val max = max(max(r, g), b)
                    val min = min(min(r, g), b)
                    val delta = max - min
                    
                    val v = max // Value
                    val s = if (max > 0) delta / max else 0f // Saturation
                    
                    // Calculate Hue
                    val h = when {
                        delta == 0f -> 0f
                        max == r -> ((g - b) / delta + (if (g < b) 6 else 0)) / 6f * 360f
                        max == g -> ((b - r) / delta + 2) / 6f * 360f
                        else -> ((r - g) / delta + 4) / 6f * 360f
                    }
                    
                    // Check if pixel matches skin color range
                    val isSkin = when {
                        // Light skin tones (H: 0-50, S: 0.2-0.7, V: 0.3-1.0)
                        h in 0f..50f && s in 0.2f..0.7f && v in 0.3f..1.0f -> true
                        // Darker skin tones (H: 150-180, S: 0.2-0.6, V: 0.2-0.8)
                        h in 150f..180f && s in 0.2f..0.6f && v in 0.2f..0.8f -> true
                        // Medium skin tones (H: 10-40, S: 0.3-0.6, V: 0.4-0.9)
                        h in 10f..40f && s in 0.3f..0.6f && v in 0.4f..0.9f -> true
                        else -> false
                    }
                    
                    if (isSkin) skinPixels++
                    totalPixels++
                }
            }
        }
        
        if (totalPixels == 0) return 0f
        
        val skinRatio = skinPixels.toFloat() / totalPixels
        // Normalize: good detection has >30% skin pixels
        return (skinRatio / 0.5f).coerceIn(0f, 1f)
    }
    
    /**
     * Detects edge density (fingerprints have many ridges = many edges)
     */
    private fun detectEdgeDensity(bitmap: Bitmap, startX: Int, endX: Int, startY: Int, endY: Int): Float {
        var edgePixels = 0
        var totalPixels = 0
        val edgeThreshold = 0.15f // Threshold for edge detection
        
        for (x in (startX + 1) until (endX - 1)) {
            for (y in (startY + 1) until (endY - 1)) {
                if (x < bitmap.width - 1 && y < bitmap.height - 1) {
                    val center = bitmap.getPixel(x, y)
                    val centerBrightness = getBrightness(center)
                    
                    // Check 4 neighbors
                    val neighbors = listOf(
                        bitmap.getPixel(x - 1, y),
                        bitmap.getPixel(x + 1, y),
                        bitmap.getPixel(x, y - 1),
                        bitmap.getPixel(x, y + 1)
                    )
                    
                    var maxGradient = 0f
                    for (neighbor in neighbors) {
                        val neighborBrightness = getBrightness(neighbor)
                        val gradient = abs(centerBrightness - neighborBrightness)
                        maxGradient = max(maxGradient, gradient)
                    }
                    
                    if (maxGradient > edgeThreshold) {
                        edgePixels++
                    }
                    totalPixels++
                }
            }
        }
        
        if (totalPixels == 0) return 0f
        
        val edgeDensity = edgePixels.toFloat() / totalPixels
        // Normalize: good finger images have >25% edge density
        return (edgeDensity / 0.4f).coerceIn(0f, 1f)
    }
    
    /**
     * Analyzes shape characteristics (finger should be roughly vertical/rectangular)
     */
    private fun analyzeShape(bitmap: Bitmap, startX: Int, endX: Int, startY: Int, endY: Int): Float {
        // For now, we assume if there's good skin/edge detection in center region,
        // the shape is likely correct (finger is in the box)
        // This is a simplified check - could be enhanced with contour detection
        
        val regionWidth = endX - startX
        val regionHeight = endY - startY
        
        if (regionWidth == 0 || regionHeight == 0) return 0f
        
        // Check aspect ratio (finger in box should be roughly 1.2:1 to 2:1)
        val aspectRatio = regionHeight.toFloat() / regionWidth.toFloat()
        val aspectScore = when {
            aspectRatio in 1.0f..2.5f -> 1.0f // Good aspect ratio
            aspectRatio in 0.8f..3.0f -> 0.7f // Acceptable
            else -> 0.3f // Poor aspect ratio
        }
        
        // Check if region has sufficient area (finger should occupy reasonable portion)
        val regionArea = regionWidth * regionHeight
        val imageArea = bitmap.width * bitmap.height
        val areaRatio = regionArea.toFloat() / imageArea
        
        val areaScore = when {
            areaRatio in 0.1f..0.4f -> 1.0f // Good coverage
            areaRatio in 0.05f..0.5f -> 0.7f // Acceptable
            else -> 0.4f // Too small or too large
        }
        
        return (aspectScore * 0.6f + areaScore * 0.4f).coerceIn(0f, 1f)
    }
    
    /**
     * Calculates brightness of a pixel
     */
    private fun getBrightness(pixel: Int): Float {
        val r = Color.red(pixel) / 255f
        val g = Color.green(pixel) / 255f
        val b = Color.blue(pixel) / 255f
        return (r * 0.299f + g * 0.587f + b * 0.114f)
    }
}

data class FingerDetectionResult(
    val isFingerDetected: Boolean = false,
    val boundingBox: Rect? = null,
    val confidence: Float = 0f,
    val orientation: Float = 0f // Orientation angle in degrees (0 = vertical, positive = clockwise)
)
