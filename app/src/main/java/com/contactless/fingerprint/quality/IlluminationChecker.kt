package com.contactless.fingerprint.quality

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Checks illumination/brightness quality
 */
object IlluminationChecker {
    fun checkIllumination(bitmap: Bitmap): Float {
        // Sample pixels for faster processing
        val sampleSize = 50
        // Avoid step size 0 for small images
        val stepX = maxOf(1, bitmap.width / sampleSize)
        val stepY = maxOf(1, bitmap.height / sampleSize)
        
        var totalBrightness = 0.0
        var count = 0
        
        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                // Calculate brightness using luminance formula
                val brightness = (
                    Color.red(pixel) * 0.299 +
                    Color.green(pixel) * 0.587 +
                    Color.blue(pixel) * 0.114
                ) / 255.0
                
                totalBrightness += brightness
                count++
            }
        }
        
        if (count == 0) return 0f
        
        val averageBrightness = totalBrightness / count
        
        // Ideal brightness is around 0.4-0.6 (not too dark, not too bright)
        // Score is highest at 0.5, decreases as it moves away
        val score = when {
            averageBrightness < 0.2 -> (averageBrightness / 0.2).toFloat() // Too dark
            averageBrightness > 0.8 -> ((1.0 - averageBrightness) / 0.2).toFloat() // Too bright
            else -> {
                // Optimal range: 0.2-0.8, best at 0.5
                val distanceFromOptimal = kotlin.math.abs(averageBrightness - 0.5)
                (1.0 - (distanceFromOptimal / 0.3).coerceIn(0.0, 1.0)).toFloat()
            }
        }
        
        return score.coerceIn(0f, 1f)
    }
}
