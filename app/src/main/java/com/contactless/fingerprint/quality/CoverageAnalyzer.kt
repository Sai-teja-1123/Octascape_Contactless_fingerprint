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
        // If image is already cropped (smaller dimensions), analyze the entire image
        // Otherwise, analyze center region (where finger box is)
        
        // Heuristic: if image is smaller than typical full capture, it's likely cropped
        val isCropped = bitmap.width < 1000 || bitmap.height < 1000
        
        if (isCropped) {
            // For cropped images, analyze the entire image (it's already the finger region)
            return analyzeFullImage(bitmap)
        } else {
            // For full images, analyze center region (where finger box is)
            return analyzeCenterRegion(bitmap)
        }
    }
    
    /**
     * Analyzes the entire image for edge density (used for cropped images)
     */
    private fun analyzeFullImage(bitmap: Bitmap): Float {
        var edgePixels = 0
        var totalPixels = 0
        val edgeThreshold = 0.08f // Lower threshold for enhanced images
        
        // Sample every 2nd pixel for performance
        for (x in 0 until bitmap.width step 2) {
            for (y in 0 until bitmap.height step 2) {
                if (x > 0 && x < bitmap.width - 1 && y > 0 && y < bitmap.height - 1) {
                    val center = bitmap.getPixel(x, y)
                    val centerBrightness = getBrightness(center)
                    
                    // Check 8 neighbors for edge detection
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
        // For cropped images (already finger region), expect higher edge density
        // Normalize: 15% edge density = 0.5 score, 30%+ = 1.0 score
        return (edgeDensity / 0.3f).coerceIn(0f, 1f)
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
        // Enhanced images have clearer ridges, but we need more lenient scoring
        // Normalize: 10% edge density = 0.5 score, 20%+ = 1.0 score (more lenient)
        // This accounts for the fact that even good fingerprints don't have edges everywhere
        return (edgeDensity / 0.2f).coerceIn(0f, 1f)
    }
    
    /**
     * Analyzes center region for finger presence using edge density
     * Works well on enhanced grayscale images where ridges are clearly visible
     */
    private fun analyzeCenterRegion(bitmap: Bitmap): Float {
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        // Use larger region (50% of image) to better capture finger coverage
        val regionSize = kotlin.math.min(bitmap.width, bitmap.height) / 2
        
        var edgePixels = 0
        var totalPixels = 0
        val edgeThreshold = 0.08f // Lower threshold for enhanced images (ridges are clearer)
        
        for (x in (centerX - regionSize / 2) until (centerX + regionSize / 2) step 2) {
            for (y in (centerY - regionSize / 2) until (centerY + regionSize / 2) step 2) {
                if (x >= 0 && x < bitmap.width - 1 && y >= 0 && y < bitmap.height - 1) {
                    val center = bitmap.getPixel(x, y)
                    val centerBrightness = getBrightness(center)
                    
                    // Check 8 neighbors for better edge detection (fingerprint ridges)
                    val neighbors = listOf(
                        if (x > 0 && y > 0) bitmap.getPixel(x - 1, y - 1) else center,
                        if (y > 0) bitmap.getPixel(x, y - 1) else center,
                        if (x < bitmap.width - 1 && y > 0) bitmap.getPixel(x + 1, y - 1) else center,
                        if (x > 0) bitmap.getPixel(x - 1, y) else center,
                        if (x < bitmap.width - 1) bitmap.getPixel(x + 1, y) else center,
                        if (x > 0 && y < bitmap.height - 1) bitmap.getPixel(x - 1, y + 1) else center,
                        if (y < bitmap.height - 1) bitmap.getPixel(x, y + 1) else center,
                        if (x < bitmap.width - 1 && y < bitmap.height - 1) bitmap.getPixel(x + 1, y + 1) else center
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
        // Enhanced images have clearer ridges, but we need more lenient scoring
        // Normalize: 10% edge density = 0.5 score, 20%+ = 1.0 score (more lenient)
        // This accounts for the fact that even good fingerprints don't have edges everywhere
        return (edgeDensity / 0.2f).coerceIn(0f, 1f)
    }
    
    private fun getBrightness(pixel: Int): Float {
        val r = Color.red(pixel) / 255f
        val g = Color.green(pixel) / 255f
        val b = Color.blue(pixel) / 255f
        return (r * 0.299f + g * 0.587f + b * 0.114f)
    }
}
