package com.contactless.fingerprint.matching

import android.graphics.Bitmap
import android.graphics.Point
import android.util.Log
import com.contactless.fingerprint.core.ImageProcessor
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/**
 * Simplified minutiae extraction for fingerprint matching.
 * 
 * Steps:
 * 1. Binarize image (threshold)
 * 2. Skeletonize ridges (morphological thinning)
 * 3. Detect minutiae (ridge endings and bifurcations)
 * 4. Extract minutiae features (position, angle, type)
 */
class MinutiaeExtractor {
    
    /**
     * Extracts minutiae from a preprocessed fingerprint image.
     * PERFORMANCE: Downscales image before skeletonization for speed.
     */
    fun extractMinutiae(preprocessedBitmap: Bitmap): List<Minutia> {
        val startTime = System.currentTimeMillis()
        return try {
            Log.d("MinutiaeExtractor", "Starting minutiae extraction, image size: ${preprocessedBitmap.width}x${preprocessedBitmap.height}")
            
            // Convert to Mat
            val grayMat = ImageProcessor.bitmapToMat(preprocessedBitmap)
            val gray = Mat()
            if (grayMat.channels() == 1) {
                grayMat.copyTo(gray)
            } else {
                Imgproc.cvtColor(grayMat, gray, Imgproc.COLOR_RGBA2GRAY)
            }
            
            // PERFORMANCE: Downscale for faster skeletonization (300x300 is sufficient)
            val targetSize = 300
            val originalWidth = gray.width()
            val originalHeight = gray.height()
            val scaleFactor = if (originalWidth > targetSize || originalHeight > targetSize) {
                min(targetSize.toDouble() / originalWidth, targetSize.toDouble() / originalHeight)
            } else {
                1.0
            }
            
            val workingGray = if (scaleFactor < 1.0) {
                val resized = Mat()
                val newWidth = (originalWidth * scaleFactor).toInt()
                val newHeight = (originalHeight * scaleFactor).toInt()
                Imgproc.resize(gray, resized, Size(newWidth.toDouble(), newHeight.toDouble()))
                gray.release()
                Log.d("MinutiaeExtractor", "Downscaled from ${originalWidth}x${originalHeight} to ${newWidth}x${newHeight} for performance")
                resized
            } else {
                gray
            }
            
            val preprocessTime = System.currentTimeMillis() - startTime
            Log.d("MinutiaeExtractor", "Preprocessing took ${preprocessTime}ms")
            
            // Step 1: Binarize (threshold to black/white)
            val binaryStart = System.currentTimeMillis()
            val binary = Mat()
            Imgproc.threshold(workingGray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            
            // Invert if needed (ridges should be black, valleys white)
            val inverted = Mat()
            Core.bitwise_not(binary, inverted)
            val binaryTime = System.currentTimeMillis() - binaryStart
            Log.d("MinutiaeExtractor", "Binarization took ${binaryTime}ms")
            
            // Step 2: Skeletonize using morphological operations
            val skeletonStart = System.currentTimeMillis()
            val skeleton = skeletonize(inverted)
            val skeletonTime = System.currentTimeMillis() - skeletonStart
            Log.d("MinutiaeExtractor", "Skeletonization took ${skeletonTime}ms")
            
            val skeletonNonZero = Core.countNonZero(skeleton)
            Log.d("MinutiaeExtractor", "Skeleton: nonZero=$skeletonNonZero")
            
            // Step 3: Detect minutiae
            val detectStart = System.currentTimeMillis()
            val minutiae = detectMinutiae(skeleton)
            val detectTime = System.currentTimeMillis() - detectStart
            Log.d("MinutiaeExtractor", "Minutiae detection took ${detectTime}ms")
            
            // Scale minutiae coordinates back to original size if we downscaled
            val scaledMinutiae = if (scaleFactor < 1.0) {
                minutiae.map { m ->
                    Minutia(
                        x = (m.x / scaleFactor).toInt(),
                        y = (m.y / scaleFactor).toInt(),
                        type = m.type,
                        angle = m.angle
                    )
                }
            } else {
                minutiae
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.d("MinutiaeExtractor", "Extracted ${scaledMinutiae.size} minutiae in ${totalTime}ms total")
            
            // Clean up
            grayMat.release()
            workingGray.release()
            binary.release()
            inverted.release()
            skeleton.release()
            
            scaledMinutiae
        } catch (e: Exception) {
            Log.e("MinutiaeExtractor", "Error extracting minutiae: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Skeletonization using Zhang-Suen thinning algorithm.
     * OPTIMIZED: Reduced max iterations, early termination, and optimized neighbor access.
     */
    private fun skeletonize(binary: Mat): Mat {
        val skeleton = Mat()
        binary.copyTo(skeleton)
        val toDelete = mutableSetOf<Pair<Int, Int>>()
        
        var changed = true
        var iterations = 0
        val maxIterations = 25 // Reduced further - most images converge in 15-20 iterations
        val rows = skeleton.rows()
        val cols = skeleton.cols()
        val totalPixels = rows * cols
        val minDeletionsForContinue = max(5, totalPixels / 500) // At least 0.2% of pixels must be deleted
        
        while (changed && iterations < maxIterations) {
            changed = false
            toDelete.clear()
            var deletedInSub1 = 0
            
            // Zhang-Suen thinning: two sub-iterations
            // Sub-iteration 1: mark pixels for deletion
            for (y in 1 until rows - 1) {
                for (x in 1 until cols - 1) {
                    // Fast check: skip if pixel is black
                    if (skeleton.get(y, x)[0] <= 128) continue
                    
                    // Get neighbors (optimized: direct array access)
                    val p2 = if (skeleton.get(y - 1, x)[0] > 128) 1 else 0
                    val p3 = if (skeleton.get(y - 1, x + 1)[0] > 128) 1 else 0
                    val p4 = if (skeleton.get(y, x + 1)[0] > 128) 1 else 0
                    val p5 = if (skeleton.get(y + 1, x + 1)[0] > 128) 1 else 0
                    val p6 = if (skeleton.get(y + 1, x)[0] > 128) 1 else 0
                    val p7 = if (skeleton.get(y + 1, x - 1)[0] > 128) 1 else 0
                    val p8 = if (skeleton.get(y, x - 1)[0] > 128) 1 else 0
                    val p9 = if (skeleton.get(y - 1, x - 1)[0] > 128) 1 else 0
                    
                    val b = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
                    
                    // Fast transition count
                    val a = countTransitionsFast(p2, p3, p4, p5, p6, p7, p8, p9)
                    
                    // Conditions for deletion in sub-iteration 1
                    if (b >= 2 && b <= 6 && a == 1 &&
                        p2 * p4 * p6 == 0 && p4 * p6 * p8 == 0) {
                        toDelete.add(Pair(x, y))
                        deletedInSub1++
                    }
                }
            }
            
            // Delete marked pixels from sub-iteration 1
            if (deletedInSub1 > 0) {
                changed = true
                for ((x, y) in toDelete) {
                    skeleton.put(y, x, 0.0)
                }
            }
            
            // Early termination if very few pixels deleted
            if (deletedInSub1 < minDeletionsForContinue) {
                break
            }
            
            toDelete.clear()
            var deletedInSub2 = 0
            
            // Sub-iteration 2: mark pixels for deletion
            for (y in 1 until rows - 1) {
                for (x in 1 until cols - 1) {
                    // Fast check: skip if pixel is black
                    if (skeleton.get(y, x)[0] <= 128) continue
                    
                    // Get neighbors
                    val p2 = if (skeleton.get(y - 1, x)[0] > 128) 1 else 0
                    val p3 = if (skeleton.get(y - 1, x + 1)[0] > 128) 1 else 0
                    val p4 = if (skeleton.get(y, x + 1)[0] > 128) 1 else 0
                    val p5 = if (skeleton.get(y + 1, x + 1)[0] > 128) 1 else 0
                    val p6 = if (skeleton.get(y + 1, x)[0] > 128) 1 else 0
                    val p7 = if (skeleton.get(y + 1, x - 1)[0] > 128) 1 else 0
                    val p8 = if (skeleton.get(y, x - 1)[0] > 128) 1 else 0
                    val p9 = if (skeleton.get(y - 1, x - 1)[0] > 128) 1 else 0
                    
                    val b = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
                    val a = countTransitionsFast(p2, p3, p4, p5, p6, p7, p8, p9)
                    
                    // Conditions for deletion in sub-iteration 2
                    if (b >= 2 && b <= 6 && a == 1 &&
                        p2 * p4 * p8 == 0 && p2 * p6 * p8 == 0) {
                        toDelete.add(Pair(x, y))
                        deletedInSub2++
                    }
                }
            }
            
            // Delete marked pixels from sub-iteration 2
            if (deletedInSub2 > 0) {
                changed = true
                for ((x, y) in toDelete) {
                    skeleton.put(y, x, 0.0)
                }
            }
            
            // Early termination if very few pixels deleted
            if (deletedInSub2 < minDeletionsForContinue) {
                changed = false
            }
            
            iterations++
        }
        
        Log.d("MinutiaeExtractor", "Skeletonization completed in $iterations iterations")
        return skeleton
    }
    
    /**
     * Fast transition counting for Zhang-Suen (inline version).
     */
    private fun countTransitionsFast(p2: Int, p3: Int, p4: Int, p5: Int, p6: Int, p7: Int, p8: Int, p9: Int): Int {
        var transitions = 0
        if (p2 == 0 && p3 == 1) transitions++
        if (p3 == 0 && p4 == 1) transitions++
        if (p4 == 0 && p5 == 1) transitions++
        if (p5 == 0 && p6 == 1) transitions++
        if (p6 == 0 && p7 == 1) transitions++
        if (p7 == 0 && p8 == 1) transitions++
        if (p8 == 0 && p9 == 1) transitions++
        if (p9 == 0 && p2 == 1) transitions++
        return transitions
    }
    
    /**
     * Gets 8-connected neighbors (P2-P9) around a pixel.
     * Returns array: [P2, P3, P4, P5, P6, P7, P8, P9]
     * where P2=top, P3=top-right, P4=right, P5=bottom-right,
     * P6=bottom, P7=bottom-left, P8=left, P9=top-left
     */
    private fun getNeighbors(mat: Mat, x: Int, y: Int): IntArray {
        return intArrayOf(
            if (mat.get(y - 1, x)[0] > 128) 1 else 0,      // P2 (top)
            if (mat.get(y - 1, x + 1)[0] > 128) 1 else 0,  // P3 (top-right)
            if (mat.get(y, x + 1)[0] > 128) 1 else 0,      // P4 (right)
            if (mat.get(y + 1, x + 1)[0] > 128) 1 else 0,  // P5 (bottom-right)
            if (mat.get(y + 1, x)[0] > 128) 1 else 0,      // P6 (bottom)
            if (mat.get(y + 1, x - 1)[0] > 128) 1 else 0,  // P7 (bottom-left)
            if (mat.get(y, x - 1)[0] > 128) 1 else 0,      // P8 (left)
            if (mat.get(y - 1, x - 1)[0] > 128) 1 else 0   // P9 (top-left)
        )
    }
    
    /**
     * Counts transitions from 0 to 1 in the neighbor sequence (P2-P9-P2).
     * A(P1) = number of 0->1 transitions in sequence P2, P3, ..., P9, P2
     */
    private fun countTransitions(neighbors: IntArray): Int {
        var transitions = 0
        for (i in neighbors.indices) {
            val next = neighbors[(i + 1) % neighbors.size]
            if (neighbors[i] == 0 && next == 1) {
                transitions++
            }
        }
        return transitions
    }
    
    /**
     * Detects minutiae by analyzing skeleton neighbors.
     * - 1 neighbor = ridge ending
     * - 3 neighbors = bifurcation
     */
    private fun detectMinutiae(skeleton: Mat): List<Minutia> {
        val minutiae = mutableListOf<Minutia>()
        val rows = skeleton.rows()
        val cols = skeleton.cols()
        
        // Skip border pixels (avoid edge artifacts)
        val border = 5
        
        for (y in border until rows - border) {
            for (x in border until cols - border) {
                val pixelValue = skeleton.get(y, x)[0]
                
                // Only process skeleton pixels (non-zero)
                if (pixelValue > 128) {
                    // Count neighbors (8-connected)
                    val neighbors = countNeighbors(skeleton, x, y)
                    
                    when (neighbors) {
                        1 -> {
                            // Ridge ending
                            val angle = computeAngle(skeleton, x, y)
                            minutiae.add(Minutia(x, y, MinutiaType.RIDGE_ENDING, angle))
                        }
                        3 -> {
                            // Bifurcation
                            val angle = computeAngle(skeleton, x, y)
                            minutiae.add(Minutia(x, y, MinutiaType.BIFURCATION, angle))
                        }
                        // 2 neighbors = normal ridge (not a minutia)
                        // 4+ neighbors = noise/artifact (ignore)
                    }
                }
            }
        }
        
        // Filter minutiae that are too close to each other (remove duplicates)
        return filterMinutiae(minutiae, minDistance = 10)
    }
    
    /**
     * Counts 8-connected neighbors of a pixel.
     */
    private fun countNeighbors(skeleton: Mat, x: Int, y: Int): Int {
        var count = 0
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue // Skip center pixel
                
                val ny = y + dy
                val nx = x + dx
                
                if (ny >= 0 && ny < skeleton.rows() && nx >= 0 && nx < skeleton.cols()) {
                    val value = skeleton.get(ny, nx)[0]
                    if (value > 128) count++
                }
            }
        }
        return count
    }
    
    /**
     * Computes orientation angle of minutia.
     * For endings: direction away from ridge
     * For bifurcations: average direction of branches
     */
    private fun computeAngle(skeleton: Mat, x: Int, y: Int): Float {
        // Simple approach: find direction to nearest neighbor
        var minDist = Float.MAX_VALUE
        var nearestX = x
        var nearestY = y
        
        // Search in 8-connected neighborhood
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                
                val ny = y + dy
                val nx = x + dx
                
                if (ny >= 0 && ny < skeleton.rows() && nx >= 0 && nx < skeleton.cols()) {
                    val value = skeleton.get(ny, nx)[0]
                    if (value > 128) {
                        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        if (dist < minDist) {
                            minDist = dist
                            nearestX = nx
                            nearestY = ny
                        }
                    }
                }
            }
        }
        
        // Compute angle
        val dx = nearestX - x
        val dy = nearestY - y
        val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
        
        // Normalize to 0-2π, then to 0-π (ridge symmetry)
        return (if (angle < 0) angle + 2 * PI else angle).toFloat() % PI.toFloat()
    }
    
    /**
     * Filters minutiae that are too close together (removes duplicates).
     */
    private fun filterMinutiae(minutiae: List<Minutia>, minDistance: Int): List<Minutia> {
        val filtered = mutableListOf<Minutia>()
        
        for (m in minutiae) {
            var tooClose = false
            for (existing in filtered) {
                val dist = sqrt(
                    ((m.x - existing.x) * (m.x - existing.x) + 
                     (m.y - existing.y) * (m.y - existing.y)).toDouble()
                )
                if (dist < minDistance) {
                    tooClose = true
                    break
                }
            }
            if (!tooClose) {
                filtered.add(m)
            }
        }
        
        return filtered
    }
}
