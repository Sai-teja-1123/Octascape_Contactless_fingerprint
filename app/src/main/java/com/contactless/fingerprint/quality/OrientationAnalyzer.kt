package com.contactless.fingerprint.quality

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Analyzes finger orientation in the image
 * Track A: Finger coverage/orientation quality indicator
 */
object OrientationAnalyzer {
    /**
     * Analyzes if finger is properly oriented (should be roughly vertical)
     * Returns score: 1.0 = perfect vertical, 0.0 = horizontal or poor orientation
     */
    fun analyzeOrientation(bitmap: Bitmap): Float {
        // Focus on center region where finger should be
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val regionSize = kotlin.math.min(bitmap.width, bitmap.height) / 3
        
        val startX = maxOf(0, centerX - regionSize / 2)
        val endX = minOf(bitmap.width, centerX + regionSize / 2)
        val startY = maxOf(0, centerY - regionSize / 2)
        val endY = minOf(bitmap.height, centerY + regionSize / 2)
        
        // Calculate gradient orientation using Sobel operator
        var verticalGradient = 0.0
        var horizontalGradient = 0.0
        var count = 0
        
        for (x in (startX + 1) until (endX - 1) step 2) {
            for (y in (startY + 1) until (endY - 1) step 2) {
                if (x < bitmap.width - 1 && y < bitmap.height - 1) {
                    // Sobel operator for edge detection
                    val gx = (
                        -getBrightness(bitmap.getPixel(x - 1, y - 1)) +
                        getBrightness(bitmap.getPixel(x + 1, y - 1)) +
                        -2 * getBrightness(bitmap.getPixel(x - 1, y)) +
                        2 * getBrightness(bitmap.getPixel(x + 1, y)) +
                        -getBrightness(bitmap.getPixel(x - 1, y + 1)) +
                        getBrightness(bitmap.getPixel(x + 1, y + 1))
                    )
                    
                    val gy = (
                        -getBrightness(bitmap.getPixel(x - 1, y - 1)) +
                        -2 * getBrightness(bitmap.getPixel(x, y - 1)) +
                        -getBrightness(bitmap.getPixel(x + 1, y - 1)) +
                        getBrightness(bitmap.getPixel(x - 1, y + 1)) +
                        2 * getBrightness(bitmap.getPixel(x, y + 1)) +
                        getBrightness(bitmap.getPixel(x + 1, y + 1))
                    )
                    
                    val magnitude = kotlin.math.sqrt(gx * gx + gy * gy)
                    if (magnitude > 0.1) { // Only consider significant edges
                        verticalGradient += abs(gx)
                        horizontalGradient += abs(gy)
                        count++
                    }
                }
            }
        }
        
        if (count == 0) return 0.5f // Neutral score if no edges detected
        
        // Calculate dominant orientation
        val avgVertical = verticalGradient / count
        val avgHorizontal = horizontalGradient / count
        
        // Finger should be vertical (vertical gradient > horizontal)
        // Score is higher when vertical gradient dominates
        val orientationRatio = if (avgHorizontal > 0) {
            avgVertical / avgHorizontal
        } else {
            2.0 // Perfect vertical
        }
        
        // Normalize: ratio > 1.5 = good vertical orientation
        val score = when {
            orientationRatio >= 1.5 -> 1.0f // Excellent vertical
            orientationRatio >= 1.2 -> 0.8f // Good vertical
            orientationRatio >= 1.0 -> 0.6f // Acceptable
            orientationRatio >= 0.8 -> 0.4f // Slightly tilted
            else -> 0.2f // Poor orientation (too horizontal)
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    private fun getBrightness(pixel: Int): Float {
        val r = Color.red(pixel) / 255f
        val g = Color.green(pixel) / 255f
        val b = Color.blue(pixel) / 255f
        return (r * 0.299f + g * 0.587f + b * 0.114f)
    }
}
