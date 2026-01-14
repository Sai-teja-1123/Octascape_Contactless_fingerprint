package com.contactless.fingerprint.quality

import android.graphics.Bitmap
import android.graphics.Color
import com.contactless.fingerprint.camera.FingerDetector

/**
 * Analyzes finger coverage and orientation
 * Now integrates with FingerDetector for better accuracy
 */
object CoverageAnalyzer {
    fun analyzeCoverage(bitmap: Bitmap): Float {
        // Use FingerDetector to get better finger detection
        val fingerDetector = FingerDetector()
        val detectionResult = fingerDetector.detectFinger(bitmap)
        
        // If finger is detected, use detection confidence as base score
        if (detectionResult.isFingerDetected && detectionResult.confidence > 0.5f) {
            val baseScore = detectionResult.confidence
            
            // Enhance with edge density analysis in detected region
            val boundingBox = detectionResult.boundingBox
            if (boundingBox != null) {
                val edgeScore = analyzeEdgeDensityInRegion(bitmap, boundingBox)
                // Combine detection confidence with edge density
                return (baseScore * 0.7f + edgeScore * 0.3f).coerceIn(0f, 1f)
            }
            
            return baseScore
        }
        
        // Fallback: analyze center region (where finger box is)
        return analyzeCenterRegion(bitmap)
    }
    
    /**
     * Analyzes edge density in a specific region (fingerprint ridges)
     */
    private fun analyzeEdgeDensityInRegion(bitmap: Bitmap, region: android.graphics.Rect): Float {
        val startX = maxOf(0, region.left)
        val endX = minOf(bitmap.width, region.right)
        val startY = maxOf(0, region.top)
        val endY = minOf(bitmap.height, region.bottom)
        
        var edgePixels = 0
        var totalPixels = 0
        val edgeThreshold = 0.12f
        
        for (x in startX until endX step 2) {
            for (y in startY until endY step 2) {
                if (x > 0 && x < bitmap.width - 1 && y > 0 && y < bitmap.height - 1) {
                    val center = bitmap.getPixel(x, y)
                    val centerBrightness = getBrightness(center)
                    
                    // Check 8 neighbors for better edge detection
                    val neighbors = listOf(
                        bitmap.getPixel(x - 1, y - 1),
                        bitmap.getPixel(x, y - 1),
                        bitmap.getPixel(x + 1, y - 1),
                        bitmap.getPixel(x - 1, y),
                        bitmap.getPixel(x + 1, y),
                        bitmap.getPixel(x - 1, y + 1),
                        bitmap.getPixel(x, y + 1),
                        bitmap.getPixel(x + 1, y + 1)
                    )
                    
                    var maxGradient = 0f
                    for (neighbor in neighbors) {
                        val neighborBrightness = getBrightness(neighbor)
                        val gradient = kotlin.math.abs(centerBrightness - neighborBrightness)
                        maxGradient = kotlin.math.max(maxGradient, gradient)
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
        // Good fingerprint coverage has >30% edge density
        return (edgeDensity / 0.5f).coerceIn(0f, 1f)
    }
    
    /**
     * Fallback: analyzes center region for finger presence
     */
    private fun analyzeCenterRegion(bitmap: Bitmap): Float {
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val regionSize = kotlin.math.min(bitmap.width, bitmap.height) / 3
        
        var edgePixels = 0
        var totalPixels = 0
        
        for (x in (centerX - regionSize / 2) until (centerX + regionSize / 2) step 2) {
            for (y in (centerY - regionSize / 2) until (centerY + regionSize / 2) step 2) {
                if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    val brightness = getBrightness(pixel)
                    
                    // Check for edges
                    if (x > 0 && y > 0) {
                        val prevPixel = bitmap.getPixel(x - 1, y - 1)
                        val prevBrightness = getBrightness(prevPixel)
                        
                        val edgeStrength = kotlin.math.abs(brightness - prevBrightness)
                        if (edgeStrength > 0.1) {
                            edgePixels++
                        }
                    }
                    totalPixels++
                }
            }
        }
        
        if (totalPixels == 0) return 0f
        
        val edgeDensity = edgePixels.toFloat() / totalPixels
        return (edgeDensity / 0.4f).coerceIn(0f, 1f)
    }
    
    private fun getBrightness(pixel: Int): Float {
        val r = Color.red(pixel) / 255f
        val g = Color.green(pixel) / 255f
        val b = Color.blue(pixel) / 255f
        return (r * 0.299f + g * 0.587f + b * 0.114f)
    }
}
