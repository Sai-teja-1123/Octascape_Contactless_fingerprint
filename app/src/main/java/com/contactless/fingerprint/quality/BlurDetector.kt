package com.contactless.fingerprint.quality

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.min

/**
 * Detects blur/focus quality in finger images using Laplacian variance
 * Improved normalization for better threshold calibration
 */
object BlurDetector {
    // Calibrated thresholds based on real-world testing (as Float for consistency)
    private const val VERY_SHARP_VARIANCE = 300.0f  // Excellent focus
    private const val SHARP_VARIANCE = 150.0f       // Good focus
    private const val ACCEPTABLE_VARIANCE = 80.0f   // Acceptable
    private const val BLURRY_VARIANCE = 40.0f       // Blurry
    
    fun calculateBlurScore(bitmap: Bitmap): Float {
        // Use a slightly larger sample for better accuracy
        val sampleSize = 300
        val targetWidth = if (sampleSize < bitmap.width) sampleSize else bitmap.width
        val targetHeight = if (sampleSize < bitmap.height) sampleSize else bitmap.height
        val smallBitmap = Bitmap.createScaledBitmap(
            bitmap, 
            targetWidth, 
            targetHeight, 
            true
        )
        
        val grayBitmap = Bitmap.createBitmap(
            smallBitmap.width, 
            smallBitmap.height, 
            Bitmap.Config.RGB_565
        )
        
        // Convert to grayscale
        for (x in 0 until smallBitmap.width) {
            for (y in 0 until smallBitmap.height) {
                val pixel = smallBitmap.getPixel(x, y)
                val gray = (Color.red(pixel) * 0.299 + 
                           Color.green(pixel) * 0.587 + 
                           Color.blue(pixel) * 0.114).toInt()
                grayBitmap.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        
        // Calculate Laplacian variance (blur metric)
        var sum = 0.0
        var sumSquared = 0.0
        var count = 0
        
        for (x in 1 until grayBitmap.width - 1) {
            for (y in 1 until grayBitmap.height - 1) {
                val center = Color.red(grayBitmap.getPixel(x, y))
                
                // Laplacian kernel: detects edges
                val laplacian = (
                    -Color.red(grayBitmap.getPixel(x - 1, y)) +
                    -Color.red(grayBitmap.getPixel(x + 1, y)) +
                    -Color.red(grayBitmap.getPixel(x, y - 1)) +
                    -Color.red(grayBitmap.getPixel(x, y + 1)) +
                    4 * center
                ).toDouble()
                
                sum += laplacian
                sumSquared += laplacian * laplacian
                count++
            }
        }
        
        if (count == 0) return 0f
        
        val mean = sum / count
        val variance = (sumSquared / count) - (mean * mean)
        
        // Convert variance to Float for consistent type handling
        val varianceFloat = variance.toFloat()
        
        // Improved normalization with calibrated thresholds
        val normalizedScore: Float = when {
            varianceFloat >= VERY_SHARP_VARIANCE -> {
                // Excellent focus - map to 0.9-1.0
                val excess = (varianceFloat - VERY_SHARP_VARIANCE) / VERY_SHARP_VARIANCE
                (0.9f + (excess * 0.1f).coerceIn(0f, 0.1f)).coerceIn(0f, 1f)
            }
            varianceFloat >= SHARP_VARIANCE -> {
                // Good focus - map to 0.7-0.9
                val ratio = (varianceFloat - SHARP_VARIANCE) / (VERY_SHARP_VARIANCE - SHARP_VARIANCE)
                0.7f + (ratio * 0.2f).coerceIn(0f, 0.2f)
            }
            varianceFloat >= ACCEPTABLE_VARIANCE -> {
                // Acceptable - map to 0.5-0.7
                val ratio = (varianceFloat - ACCEPTABLE_VARIANCE) / (SHARP_VARIANCE - ACCEPTABLE_VARIANCE)
                0.5f + (ratio * 0.2f).coerceIn(0f, 0.2f)
            }
            varianceFloat >= BLURRY_VARIANCE -> {
                // Blurry - map to 0.3-0.5
                val ratio = (varianceFloat - BLURRY_VARIANCE) / (ACCEPTABLE_VARIANCE - BLURRY_VARIANCE)
                0.3f + (ratio * 0.2f).coerceIn(0f, 0.2f)
            }
            else -> {
                // Very blurry - map to 0.0-0.3
                (varianceFloat / BLURRY_VARIANCE * 0.3f).coerceIn(0f, 0.3f)
            }
        }
        
        return normalizedScore
    }
}
