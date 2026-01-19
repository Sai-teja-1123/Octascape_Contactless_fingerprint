package com.contactless.fingerprint.liveness

import android.graphics.Bitmap
import android.util.Log
import com.contactless.fingerprint.core.ImageProcessor
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

/**
 * Detects liveness and spoof resistance
 * Track D: Motion-based, texture-based, multi-frame consistency checks
 */
class LivenessDetector {
    /**
     * Step 5.1: Liveness Check During Capture
     * Combines all detection methods (motion, texture, consistency) to determine liveness.
     * 
     * Algorithm:
     * 1. Run motion detection (if multiple frames available)
     * 2. Run texture detection (on first/last frame)
     * 3. Run consistency check (if multiple frames available)
     * 4. Combine scores with weighted average
     * 5. Make final decision based on thresholds
     * 
     * Returns LivenessResult with individual scores and final decision.
     */
    fun detectLiveness(frames: List<Bitmap>): LivenessResult {
        return try {
            if (frames.isEmpty()) {
                Log.w("LivenessDetector", "No frames provided for liveness detection")
                return LivenessResult(
                    isLive = false,
                    confidence = 0f,
                    motionScore = 0f,
                    textureScore = 0f,
                    consistencyScore = 0f
                )
            }
            
            // Step 1: Motion detection (requires multiple frames)
            val motionScore = if (frames.size >= 2) {
                checkMotion(frames)
            } else {
                0.5f // Neutral score if only one frame
            }
            
            // Step 2: Texture detection (use the last frame, most representative)
            val textureScore = checkTexture(frames.last())
            
            // Step 2.5: Additional photo detection methods (NEW)
            // Check for compression artifacts (JPEG compression in gallery photos)
            val compressionArtifactScore = if (frames.isNotEmpty()) {
                detectCompressionArtifacts(frames.last())
            } else {
                0.5f
            }
            
            // Check for screen/moiré patterns (photos on screens)
            val screenPatternScore = if (frames.isNotEmpty()) {
                detectScreenPatterns(frames.last())
            } else {
                0.5f
            }
            
            // Check multi-frame texture variation (even with low motion)
            val textureVariationScore = if (frames.size >= 2) {
                checkTextureVariation(frames)
            } else {
                0.5f
            }
            
            // Step 3: Consistency check (requires multiple frames)
            val consistencyScore = if (frames.size >= 2) {
                checkConsistency(frames)
            } else {
                0.5f // Neutral score if only one frame
            }
            
            // Step 4: Combine scores with weighted average
            // BALANCED: Screen detection is important, but texture is primary for real fingers
            // Motion can be low for still fingers, so reduce its weight
            val weights = if (frames.size >= 2) {
                // Multi-frame: texture is primary, screen detection is important for spoofs
                mapOf(
                    "motion" to 0.15f,  // Reduced (still fingers have low motion)
                    "texture" to 0.30f,  // Primary indicator (most reliable)
                    "consistency" to 0.15f,  // Moderate weight
                    "compression" to 0.15f,  // Moderate weight
                    "screen" to 0.15f,  // Increased: Screen detection is important
                    "textureVariation" to 0.10f  // Multi-frame texture variation
                )
            } else {
                // Single frame: texture is primary, screen detection important
                mapOf(
                    "motion" to 0.0f,
                    "texture" to 0.40f,  // Primary indicator
                    "consistency" to 0.20f,
                    "compression" to 0.20f,  // Moderate weight
                    "screen" to 0.15f,  // Increased: Screen detection is important
                    "textureVariation" to 0.05f  // Less reliable with single frame
                )
            }
            
            var combinedConfidence = (
                weights["motion"]!! * motionScore +
                weights["texture"]!! * textureScore +
                weights["consistency"]!! * consistencyScore +
                weights["compression"]!! * compressionArtifactScore +
                weights["screen"]!! * screenPatternScore +
                weights["textureVariation"]!! * textureVariationScore
            ).coerceIn(0f, 1f)
            
            Log.d("LivenessDetector", "Photo detection scores: compression=$compressionArtifactScore, screen=$screenPatternScore, textureVariation=$textureVariationScore")
            
            // BALANCED: More lenient for real fingers, strict only for clear spoofs
            // Priority 1: Only penalize if VERY strong spoof indicators
            // Priority 2: Boost real fingers generously
            // Priority 3: Use adaptive threshold but more lenient
            
            // BALANCED: Stricter for screen patterns (screens are common spoof), lenient for compression
            // Screen photos are a major threat, so detect them more aggressively
            if (screenPatternScore < 0.20f) {
                // Strong screen patterns detected = likely screen photo
                combinedConfidence = (combinedConfidence * 0.6f).coerceIn(0f, 1f)
                Log.d("LivenessDetector", "Strong screen patterns detected: screen=$screenPatternScore")
            } else if (screenPatternScore < 0.35f) {
                // Moderate screen patterns = possibly screen
                combinedConfidence = (combinedConfidence * 0.75f).coerceIn(0f, 1f)
                Log.d("LivenessDetector", "Moderate screen patterns: screen=$screenPatternScore")
            }
            
            // Compression artifacts: Only penalize if very strong (more lenient)
            if (compressionArtifactScore < 0.10f) {
                // EXTREMELY strong compression artifacts = likely photo
                combinedConfidence = (combinedConfidence * 0.8f).coerceIn(0f, 1f)
                Log.d("LivenessDetector", "Extremely strong compression artifacts: compression=$compressionArtifactScore")
            } else if (compressionArtifactScore < 0.15f) {
                // Very strong compression (less penalty)
                combinedConfidence = (combinedConfidence * 0.9f).coerceIn(0f, 1f)
            }
            
            // Texture variation: Only penalize if EXTREMELY identical (photos are nearly perfect)
            if (textureVariationScore < 0.10f && frames.size >= 2) {
                // Texture is extremely identical = likely photo
                combinedConfidence = (combinedConfidence * 0.8f).coerceIn(0f, 1f)
                Log.d("LivenessDetector", "Extremely identical texture: score=$textureVariationScore")
            }
            
            // Boost real fingers GENEROUSLY - if texture is decent
            if (textureScore > 0.35f) {
                // Good texture = likely real (even without motion)
                combinedConfidence = (combinedConfidence * 1.2f).coerceIn(0f, 1f)
                Log.d("LivenessDetector", "Applied texture boost: texture=$textureScore")
            }
            
            // Boost if texture + motion/consistency are good
            if (textureScore > 0.35f && (motionScore > 0.25f || consistencyScore > 0.35f)) {
                // Good texture + motion/consistency = definitely real
                combinedConfidence = (combinedConfidence * 1.25f).coerceIn(0f, 1f)
                Log.d("LivenessDetector", "Applied real-finger boost: texture=$textureScore, motion=$motionScore, consistency=$consistencyScore")
            }
            
            // Special handling for still finger (low motion but good texture)
            // IMPORTANT: Only boost if NO screen patterns detected (screens can have good texture)
            if (motionScore < 0.3f && textureScore > 0.35f && compressionArtifactScore > 0.25f && screenPatternScore > 0.40f) {
                // Still finger with good texture and no screen patterns = likely real
                combinedConfidence = (combinedConfidence * 1.3f).coerceIn(0f, 1f)
                Log.d("LivenessDetector", "Applied still-finger boost: motion=$motionScore, texture=$textureScore, screen=$screenPatternScore")
            }
            
            // Additional boost if multiple good indicators
            val goodIndicators = listOf(
                textureScore > 0.35f,
                consistencyScore > 0.35f,
                compressionArtifactScore > 0.3f,
                screenPatternScore > 0.3f,
                if (frames.size >= 2) textureVariationScore > 0.25f else true
            ).count { it }
            
            if (goodIndicators >= 2) {
                // Multiple good indicators = likely real (boost even if confidence is already high)
                combinedConfidence = (combinedConfidence * 1.15f).coerceIn(0f, 1f)
                Log.d("LivenessDetector", "Applied multi-indicator boost: $goodIndicators good indicators")
            }
            
            // Step 5: Make final decision with balanced threshold
            // Stricter for screen patterns, lenient for real fingers without screen patterns
            val spoofThreshold = when {
                // Screen patterns detected: Stricter threshold
                screenPatternScore < 0.20f -> 0.50f  // Strong screen patterns = higher threshold
                screenPatternScore < 0.35f -> 0.45f  // Moderate screen patterns = moderate threshold
                // Other spoof indicators
                compressionArtifactScore < 0.10f || (frames.size >= 2 && textureVariationScore < 0.10f) -> 0.40f
                // No strong spoof indicators: Lenient for real fingers
                else -> 0.25f  // Low threshold for real fingers (lenient)
            }
            
            val isLive = combinedConfidence >= spoofThreshold
            
            Log.d("LivenessDetector", "Liveness result: isLive=$isLive, confidence=$combinedConfidence, threshold=$spoofThreshold")
            Log.d("LivenessDetector", "  - Motion: $motionScore")
            Log.d("LivenessDetector", "  - Texture: $textureScore")
            Log.d("LivenessDetector", "  - Consistency: $consistencyScore")
            Log.d("LivenessDetector", "  - Compression artifacts: $compressionArtifactScore (photo indicator)")
            Log.d("LivenessDetector", "  - Screen patterns: $screenPatternScore (screen photo indicator)")
            Log.d("LivenessDetector", "  - Texture variation: $textureVariationScore (multi-frame variation)")
            
            LivenessResult(
                isLive = isLive,
                confidence = combinedConfidence,
                motionScore = motionScore,
                textureScore = textureScore,
                consistencyScore = consistencyScore
            )
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in liveness detection: ${e.message}", e)
            LivenessResult(
                isLive = false,
                confidence = 0f,
                motionScore = 0f,
                textureScore = 0f,
                consistencyScore = 0f
            )
        }
    }
    
    /**
     * Step 2.1: Frame Difference Analysis
     * Computes frame-to-frame differences to detect micro-movements.
     * 
     * Algorithm:
     * 1. Convert frames to grayscale
     * 2. Compute absolute difference between consecutive frames
     * 3. Sum differences (motion magnitude)
     * 4. Normalize by frame size
     * 
     * Real finger: moderate motion (0.3-0.7)
     * Photo/print: very low motion (<0.1) or no motion (0.0)
     */
    fun checkMotion(frames: List<Bitmap>): Float {
        return try {
            // Need at least 2 frames to compute differences
            if (frames.size < 2) {
                Log.w("LivenessDetector", "Not enough frames for motion detection: ${frames.size}")
                return 0f
            }
            
            // Convert all frames to grayscale Mat
            val grayMats = mutableListOf<Mat>()
            try {
                for (bitmap in frames) {
                    val mat = ImageProcessor.bitmapToMat(bitmap)
                    val gray = Mat()
                    if (mat.channels() == 1) {
                        mat.copyTo(gray)
                    } else {
                        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
                    }
                    grayMats.add(gray)
                    mat.release() // Release original mat
                }
                
                // Compute frame-to-frame differences
                val motionValues = mutableListOf<Double>()
                
                for (i in 0 until grayMats.size - 1) {
                    val frame1 = grayMats[i]
                    val frame2 = grayMats[i + 1]
                    
                    // Ensure frames are same size (resize if needed)
                    val frame1Resized: Mat
                    val frame2Resized: Mat
                    
                    if (frame1.rows() != frame2.rows() || frame1.cols() != frame2.cols()) {
                        // Resize to match (use smaller dimensions)
                        val targetRows = minOf(frame1.rows(), frame2.rows())
                        val targetCols = minOf(frame1.cols(), frame2.cols())
                        frame1Resized = Mat()
                        frame2Resized = Mat()
                        Imgproc.resize(frame1, frame1Resized, org.opencv.core.Size(targetCols.toDouble(), targetRows.toDouble()))
                        Imgproc.resize(frame2, frame2Resized, org.opencv.core.Size(targetCols.toDouble(), targetRows.toDouble()))
                    } else {
                        frame1Resized = frame1
                        frame2Resized = frame2
                    }
                    
                    // Compute absolute difference
                    val diff = Mat()
                    Core.absdiff(frame1Resized, frame2Resized, diff)
                    
                    // Sum all pixel differences (motion magnitude)
                    val sumScalar = Core.sumElems(diff)
                    val motionMagnitude = sumScalar.`val`[0] // Sum of all pixel differences
                    
                    // Normalize by frame size (pixels)
                    val frameSize = frame1Resized.rows() * frame1Resized.cols()
                    val normalizedMotion = motionMagnitude / frameSize
                    
                    motionValues.add(normalizedMotion)
                    
                    // Clean up temporary mats
                    if (frame1Resized != frame1) {
                        frame1Resized.release()
                    }
                    if (frame2Resized != frame2) {
                        frame2Resized.release()
                    }
                    diff.release()
                }
                
                // Step 2.2: Calculate average motion and check consistency
                val avgMotion = if (motionValues.isNotEmpty()) {
                    motionValues.average()
                } else {
                    0.0
                }
                
                // Additional check: Real fingers should have consistent moderate motion
                // Photos/prints will have very consistent (near-zero) motion
                val motionVariance = if (motionValues.size > 1) {
                    val mean = avgMotion
                    val variance = motionValues.map { (it - mean) * (it - mean) }.average()
                    kotlin.math.sqrt(variance) // Standard deviation
                } else {
                    0.0
                }
                
                // Step 2.2: Refined Motion Score Calculation
                // Map raw motion values to 0-1 score with better thresholds
                var motionScore = calculateMotionScore(avgMotion)
                
                // Penalize if motion is too consistent (likely static photo/print)
                // Real fingers have some variance in motion, photos have near-zero variance
                if (avgMotion < 10.0 && motionVariance < 2.0) {
                    // Very low motion with low variance = likely static spoof
                    motionScore = motionScore * 0.5f // Reduce score further
                }
                
                Log.d("LivenessDetector", "Motion detection: avgMotion=$avgMotion, variance=$motionVariance, score=$motionScore, pairs=${motionValues.size}")
                
                motionScore
            } finally {
                // Clean up all grayscale mats
                grayMats.forEach { it.release() }
            }
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in motion detection: ${e.message}", e)
            0f
        }
    }
    
    /**
     * Step 2.2: Motion Score Calculation
     * Normalizes raw motion values to 0-1 score with thresholds.
     * 
     * Expected ranges:
     * - Photo/print: very low motion (<0.1) or no motion (0.0)
     * - Real finger: moderate motion (0.3-0.7)
     * 
     * Uses non-linear mapping to better separate static vs live:
     * - Very low motion (< 20): maps to 0.0-0.1 (likely spoof)
     * - Low motion (20-40): maps to 0.1-0.3 (uncertain)
     * - Moderate motion (40-100): maps to 0.3-0.7 (likely live)
     * - High motion (> 100): maps to 0.7-1.0 (very live, but may be too much movement)
     */
    private fun calculateMotionScore(avgMotion: Double): Float {
        return when {
            // Zero or near-zero motion: likely photo/print (score 0.0-0.2)
            avgMotion < 2.0 -> {
                // Very strict: only truly static gets very low score
                (avgMotion / 2.0 * 0.2).toFloat().coerceIn(0f, 0.2f)
            }
            // Very low motion: could be real finger held very still (score 0.2-0.5)
            avgMotion < 10.0 -> {
                // More lenient: any small motion gets decent score
                val normalized = (avgMotion - 2.0) / 8.0 // 0-1 in this range
                0.2f + (normalized.toFloat() * 0.3f) // Map to 0.2-0.5
            }
            // Low motion: likely real finger (score 0.5-0.7)
            avgMotion < 30.0 -> {
                val normalized = (avgMotion - 10.0) / 20.0 // 0-1 in this range
                0.5f + (normalized.toFloat() * 0.2f) // Map to 0.5-0.7
            }
            // Moderate motion: definitely live (score 0.7-0.9)
            avgMotion < 60.0 -> {
                val normalized = (avgMotion - 30.0) / 30.0 // 0-1 in this range
                0.7f + (normalized.toFloat() * 0.2f) // Map to 0.7-0.9
            }
            // High motion: very live (score 0.9-1.0)
            else -> {
                val normalized = ((avgMotion - 60.0) / 40.0).coerceIn(0.0, 1.0)
                0.9f + (normalized.toFloat() * 0.1f) // Map to 0.9-1.0
            }
        }
    }
    
    /**
     * Step 3.1: Texture Analysis
     * Detects spoof artifacts (print texture, photo artifacts, screen reflections).
     * 
     * Algorithm:
     * 1. Analyze texture patterns using:
     *    - Edge pattern analysis (gradient uniformity)
     *    - Frequency domain analysis (autocorrelation)
     *    - Local texture variance
     * 2. Detect unnatural patterns:
     *    - Print artifacts (regular patterns, paper texture)
     *    - Photo artifacts (compression, screen moiré)
     *    - Screen reflections (unnatural highlights)
     * 
     * Real finger: natural skin texture (high score > 0.6)
     * Print/photo: artificial texture patterns (low score < 0.4)
     */
    fun checkTexture(bitmap: Bitmap): Float {
        return try {
            // Convert to grayscale Mat
            val mat = ImageProcessor.bitmapToMat(bitmap)
            val gray = Mat()
            if (mat.channels() == 1) {
                mat.copyTo(gray)
            } else {
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            }
            mat.release()
            
            try {
                // 1. Edge Pattern Analysis
                // Real skin has natural, varied edge patterns
                // Prints/photos have more uniform or artificial patterns
                val edgeScore = analyzeEdgePatterns(gray)
                
                // 2. Frequency Domain Analysis
                // Real skin has natural frequency distribution
                // Prints/photos may have regular patterns (moiré, print artifacts)
                val frequencyScore = analyzeFrequencyPatterns(gray)
                
                // 3. Local Texture Variance
                // Real skin has natural variance in texture
                // Prints/photos may be too uniform or have artificial patterns
                val varianceScore = analyzeTextureVariance(gray)
                
                // Step 3.2: Refined Texture Score Calculation
                // Combine scores with refined weighting and additional checks
                val combinedScore = calculateTextureScore(edgeScore, frequencyScore, varianceScore)
                
                Log.d("LivenessDetector", "Texture detection: edge=$edgeScore, frequency=$frequencyScore, variance=$varianceScore, combined=$combinedScore")
                
                combinedScore.coerceIn(0f, 1f)
            } finally {
                gray.release()
            }
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in texture detection: ${e.message}", e)
            0f
        }
    }
    
    /**
     * Analyzes edge patterns to detect natural vs artificial textures.
     * Real skin has varied, natural edge patterns.
     * Prints/photos may have uniform or regular edge patterns.
     */
    private fun analyzeEdgePatterns(gray: Mat): Float {
        return try {
            // Compute gradient magnitude
            val gradX = Mat()
            val gradY = Mat()
            val gradientMagnitude = Mat()
            
            Imgproc.Sobel(gray, gradX, CvType.CV_32F, 1, 0, 3)
            Imgproc.Sobel(gray, gradY, CvType.CV_32F, 0, 1, 3)
            Core.magnitude(gradX, gradY, gradientMagnitude)
            
            // Analyze gradient distribution
            // Real skin: varied gradient magnitudes (natural texture)
            // Prints/photos: may have very uniform or very regular patterns
            
            // Compute statistics
            val meanMat = MatOfDouble()
            val stdMat = MatOfDouble()
            Core.meanStdDev(gradientMagnitude, meanMat, stdMat)
            
            val mean = meanMat.get(0, 0)[0]
            val std = stdMat.get(0, 0)[0]
            
            // Coefficient of variation (std/mean) indicates texture variety
            // Higher CV = more varied texture (natural) = higher score
            // Lower CV = more uniform texture (artificial) = lower score
            val coefficientOfVariation = if (mean > 0) {
                std / mean
            } else {
                0.0
            }
            
            // LOOSER: More lenient thresholds for real fingers
            // Real skin typically has CV 0.2-0.8 (expanded range)
            // Only flag as spoof if extremely uniform (CV < 0.15)
            val edgeScore = when {
                coefficientOfVariation < 0.15 -> {
                    // Extremely uniform (likely print/photo)
                    (coefficientOfVariation / 0.15 * 0.4).toFloat()
                }
                coefficientOfVariation < 0.8 -> {
                    // Natural range (likely real skin) - expanded range
                    val normalized = (coefficientOfVariation - 0.15) / 0.65 // 0-1 in this range
                    0.4f + (normalized.toFloat() * 0.5f) // Map to 0.4-0.9 (higher scores)
                }
                else -> {
                    // Very chaotic (may be noise, but still likely real)
                    val normalized = ((coefficientOfVariation - 0.8) / 0.2).coerceIn(0.0, 1.0)
                    0.9f - (normalized.toFloat() * 0.15f) // Map to 0.9-0.75 (still good)
                }
            }
            
            // Clean up
            gradX.release()
            gradY.release()
            gradientMagnitude.release()
            meanMat.release()
            stdMat.release()
            
            edgeScore.coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in edge pattern analysis: ${e.message}", e)
            0.5f // Neutral score on error
        }
    }
    
    /**
     * Analyzes frequency patterns using autocorrelation.
     * Real skin has natural frequency distribution.
     * Prints/photos may have regular patterns (moiré, print artifacts).
     */
    private fun analyzeFrequencyPatterns(gray: Mat): Float {
        return try {
            // Divide image into patches and analyze frequency in each
            val gridRows = 4
            val gridCols = 4
            val patchHeight = gray.rows() / gridRows
            val patchWidth = gray.cols() / gridCols
            
            val frequencyScores = mutableListOf<Float>()
            
            for (row in 0 until gridRows) {
                for (col in 0 until gridCols) {
                    val startX = col * patchWidth
                    val startY = row * patchHeight
                    val endX = if (col == gridCols - 1) gray.cols() else (col + 1) * patchWidth
                    val endY = if (row == gridRows - 1) gray.rows() else (row + 1) * patchHeight
                    
                    val patch = Mat(gray, Rect(startX, startY, endX - startX, endY - startY))
                    
                    // Analyze frequency using autocorrelation
                    // Count ridge/valley transitions (frequency)
                    val frequency = analyzePatchFrequency(patch)
                    frequencyScores.add(frequency)
                    
                    patch.release()
                }
            }
            
            // Real skin: varied frequencies across patches (natural)
            // Prints/photos: very uniform or very regular frequencies (artificial)
            val frequencyVariance = if (frequencyScores.size > 1) {
                val mean = frequencyScores.average()
                val variance = frequencyScores.map { (it - mean) * (it - mean) }.average()
                kotlin.math.sqrt(variance)
            } else {
                0.0
            }
            
            // LOOSER: More lenient - only flag if extremely uniform
            // Higher variance = more natural = higher score
            val frequencyScore = when {
                frequencyVariance < 0.03 -> {
                    // Extremely uniform (likely print/photo)
                    (frequencyVariance / 0.03 * 0.4).toFloat()
                }
                frequencyVariance < 0.12 -> {
                    // Natural range (expanded)
                    val normalized = (frequencyVariance - 0.03) / 0.09
                    0.4f + (normalized.toFloat() * 0.4f) // Map to 0.4-0.8
                }
                else -> {
                    // Very varied (natural) - give high scores
                    val normalized = ((frequencyVariance - 0.12) / 0.08).coerceIn(0.0, 1.0)
                    0.8f + (normalized.toFloat() * 0.2f) // Map to 0.8-1.0
                }
            }
            
            frequencyScore.coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in frequency pattern analysis: ${e.message}", e)
            0.5f // Neutral score on error
        }
    }
    
    /**
     * Analyzes frequency in a patch by counting transitions.
     */
    private fun analyzePatchFrequency(patch: Mat): Float {
        if (patch.rows() < 2 || patch.cols() < 2) {
            return 0f
        }
        
        // Sample a row in the middle
        val sampleRow = patch.rows() / 2
        var transitions = 0
        val threshold = 128.0
        
        var prevValue = patch.get(sampleRow, 0)[0]
        for (x in 1 until patch.cols()) {
            val currValue = patch.get(sampleRow, x)[0]
            // Count transitions across threshold
            if ((prevValue < threshold && currValue >= threshold) ||
                (prevValue >= threshold && currValue < threshold)) {
                transitions++
            }
            prevValue = currValue
        }
        
        // Normalize by patch width
        return if (patch.cols() > 0) {
            (transitions.toFloat() / patch.cols()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    
    /**
     * Analyzes local texture variance.
     * Real skin has natural variance.
     * Prints/photos may be too uniform or have artificial patterns.
     */
    private fun analyzeTextureVariance(gray: Mat): Float {
        return try {
            // Divide into small patches and compute variance in each
            val patchSize = 16
            val patches = mutableListOf<Double>()
            
            for (y in 0 until gray.rows() step patchSize) {
                for (x in 0 until gray.cols() step patchSize) {
                    val endX = (x + patchSize).coerceAtMost(gray.cols())
                    val endY = (y + patchSize).coerceAtMost(gray.rows())
                    
                    val patch = Mat(gray, Rect(x, y, endX - x, endY - y))
                    
                    val meanMat = MatOfDouble()
                    val stdMat = MatOfDouble()
                    Core.meanStdDev(patch, meanMat, stdMat)
                    
                    val std = stdMat.get(0, 0)[0]
                    patches.add(std)
                    
                    patch.release()
                    meanMat.release()
                    stdMat.release()
                }
            }
            
            if (patches.isEmpty()) {
                return 0.5f
            }
            
            // LOOSER: More lenient thresholds for variance
            // Real skin: moderate variance across patches
            val avgVariance = patches.average()
            
            // Score based on average variance - expanded natural range
            val varianceScore = when {
                avgVariance < 10.0 -> {
                    // Extremely uniform (likely print/photo)
                    (avgVariance / 10.0 * 0.4).toFloat()
                }
                avgVariance < 60.0 -> {
                    // Natural range (expanded from 15-50 to 10-60)
                    val normalized = (avgVariance - 10.0) / 50.0
                    0.4f + (normalized.toFloat() * 0.5f) // Map to 0.4-0.9
                }
                else -> {
                    // Very high variance (may be artifacts, but still likely real)
                    val normalized = ((avgVariance - 60.0) / 40.0).coerceIn(0.0, 1.0)
                    0.9f - (normalized.toFloat() * 0.2f) // Map to 0.9-0.7 (still decent)
                }
            }
            
            varianceScore.coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in texture variance analysis: ${e.message}", e)
            0.5f // Neutral score on error
        }
    }
    
    /**
     * Step 3.2: Texture Score Calculation
     * Refines the combined texture score to better distinguish real vs fake.
     * 
     * Expected ranges:
     * - Real finger: natural skin texture (high score > 0.6)
     * - Print/photo: artificial texture patterns (low score < 0.4)
     * 
     * Additional checks:
     * - Penalize very uniform textures (likely print/photo)
     * - Penalize very regular patterns (likely moiré/artifacts)
     * - Boost natural texture variety
     */
    private fun calculateTextureScore(
        edgeScore: Float,
        frequencyScore: Float,
        varianceScore: Float
    ): Float {
        // Base weighted average
        var combinedScore = (0.5f * edgeScore) + (0.3f * frequencyScore) + (0.2f * varianceScore)
        
        // LOOSER: More lenient heuristics - only penalize clear spoofs
        
        // 1. Check for very uniform texture (likely print/photo) - stricter threshold
        // Only penalize if EXTREMELY uniform
        if (edgeScore < 0.2f && frequencyScore < 0.2f && varianceScore < 0.2f) {
            combinedScore *= 0.8f // Less aggressive reduction
        }
        
        // 2. Check for very regular patterns - stricter threshold
        // Only penalize if frequency is extremely low
        if (frequencyScore < 0.15f && edgeScore > 0.5f) {
            combinedScore *= 0.85f // Less penalty
        }
        
        // 3. Boost natural texture variety (more generous thresholds)
        // Boost if any score is decent
        if (edgeScore > 0.35f || frequencyScore > 0.3f || varianceScore > 0.3f) {
            combinedScore = (combinedScore * 1.2f).coerceIn(0f, 1f) // Stronger boost
        }
        
        // 4. Additional boost if multiple scores are good
        val goodScores = listOf(edgeScore > 0.4f, frequencyScore > 0.35f, varianceScore > 0.35f).count { it }
        if (goodScores >= 2) {
            combinedScore = (combinedScore * 1.15f).coerceIn(0f, 1f) // Extra boost
        }
        
        // 5. Final threshold-based mapping (much more lenient for real fingers)
        // Real fingers should get higher scores
        return when {
            // Very low: likely spoof (but less strict)
            combinedScore < 0.2f -> {
                combinedScore * 0.95f // Minimal reduction
            }
            // Low: possibly spoof, but give benefit of doubt
            combinedScore < 0.4f -> {
                // Boost low scores slightly
                combinedScore * 1.1f
            }
            // Moderate: likely real (boost more)
            combinedScore < 0.6f -> {
                // Map 0.4-0.6 to 0.55-0.85 (boost moderate scores significantly)
                val normalized = (combinedScore - 0.4f) / 0.2f // 0-1 in this range
                0.55f + (normalized * 0.3f) // Map to 0.55-0.85
            }
            // High: definitely real
            else -> {
                // Map 0.6-1.0 to 0.85-1.0 (high scores stay high)
                val normalized = ((combinedScore - 0.6f) / 0.4f).coerceIn(0.0f, 1.0f) // 0-1 in this range
                0.85f + (normalized * 0.15f) // Map to 0.85-1.0
            }
        }
    }
    
    /**
     * Step 4.1: Consistency Check
     * Detects if frames are identical (photo) or too consistent (print).
     * 
     * Algorithm:
     * 1. Compare consecutive frames
     * 2. Compute similarity between frames (correlation or difference)
     * 3. Real finger: some variation between frames (moderate similarity)
     * 4. Photo/print: frames are too similar (identical or near-identical)
     * 
     * High consistency (very similar frames) = low score (likely spoof)
     * Moderate variation = high score (likely live)
     */
    fun checkConsistency(frames: List<Bitmap>): Float {
        return try {
            // Need at least 2 frames to compute consistency
            if (frames.size < 2) {
                Log.w("LivenessDetector", "Not enough frames for consistency check: ${frames.size}")
                return 0f
            }
            
            // Convert all frames to grayscale Mat
            val grayMats = mutableListOf<Mat>()
            try {
                for (bitmap in frames) {
                    val mat = ImageProcessor.bitmapToMat(bitmap)
                    val gray = Mat()
                    if (mat.channels() == 1) {
                        mat.copyTo(gray)
                    } else {
                        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
                    }
                    grayMats.add(gray)
                    mat.release()
                }
                
                // Compute similarity between consecutive frames
                val similarityValues = mutableListOf<Double>()
                
                for (i in 0 until grayMats.size - 1) {
                    val frame1 = grayMats[i]
                    val frame2 = grayMats[i + 1]
                    
                    // Ensure frames are same size (resize if needed)
                    val frame1Resized: Mat
                    val frame2Resized: Mat
                    
                    if (frame1.rows() != frame2.rows() || frame1.cols() != frame2.cols()) {
                        val targetRows = minOf(frame1.rows(), frame2.rows())
                        val targetCols = minOf(frame1.cols(), frame2.cols())
                        frame1Resized = Mat()
                        frame2Resized = Mat()
                        Imgproc.resize(frame1, frame1Resized, org.opencv.core.Size(targetCols.toDouble(), targetRows.toDouble()))
                        Imgproc.resize(frame2, frame2Resized, org.opencv.core.Size(targetCols.toDouble(), targetRows.toDouble()))
                    } else {
                        frame1Resized = frame1
                        frame2Resized = frame2
                    }
                    
                    // Compute similarity using normalized correlation
                    val similarity = computeFrameSimilarity(frame1Resized, frame2Resized)
                    similarityValues.add(similarity)
                    
                    // Clean up temporary mats
                    if (frame1Resized != frame1) {
                        frame1Resized.release()
                    }
                    if (frame2Resized != frame2) {
                        frame2Resized.release()
                    }
                }
                
                // Calculate average similarity
                val avgSimilarity = if (similarityValues.isNotEmpty()) {
                    similarityValues.average()
                } else {
                    0.0
                }
                
                // Calculate variance in similarity
                // Real fingers: some variation in similarity (frames change slightly)
                // Photos/prints: very consistent similarity (frames nearly identical)
                val similarityVariance = if (similarityValues.size > 1) {
                    val mean = avgSimilarity
                    val variance = similarityValues.map { (it - mean) * (it - mean) }.average()
                    kotlin.math.sqrt(variance) // Standard deviation
                } else {
                    0.0
                }
                
                // Step 4.1: Map similarity to consistency score
                // High similarity (> 0.95) = very consistent = low score (likely spoof)
                // Moderate similarity (0.85-0.95) = some variation = high score (likely live)
                // Low similarity (< 0.85) = too much variation = moderate score
                val consistencyScore = calculateConsistencyScore(avgSimilarity, similarityVariance)
                
                Log.d("LivenessDetector", "Consistency check: avgSimilarity=$avgSimilarity, variance=$similarityVariance, score=$consistencyScore, pairs=${similarityValues.size}")
                
                consistencyScore
            } finally {
                // Clean up all grayscale mats
                grayMats.forEach { it.release() }
            }
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in consistency check: ${e.message}", e)
            0f
        }
    }
    
    /**
     * Computes similarity between two frames using normalized correlation.
     * Returns value between 0.0 (completely different) and 1.0 (identical).
     */
    private fun computeFrameSimilarity(frame1: Mat, frame2: Mat): Double {
        return try {
            // Method 1: Normalized correlation (more robust)
            // Convert to float for better precision
            val frame1Float = Mat()
            val frame2Float = Mat()
            frame1.convertTo(frame1Float, CvType.CV_32F)
            frame2.convertTo(frame2Float, CvType.CV_32F)
            
            // Compute correlation coefficient
            val result = Mat()
            Imgproc.matchTemplate(frame1Float, frame2Float, result, Imgproc.TM_CCOEFF_NORMED)
            
            // Get correlation value (should be close to 1.0 for similar frames)
            val correlation = result.get(0, 0)[0]
            
            // Clean up
            frame1Float.release()
            frame2Float.release()
            result.release()
            
            correlation.coerceIn(0.0, 1.0)
        } catch (e: Exception) {
            // Fallback: Use normalized difference
            try {
                val diff = Mat()
                Core.absdiff(frame1, frame2, diff)
                
                val sumScalar = Core.sumElems(diff)
                val totalDiff = sumScalar.`val`[0]
                
                val frameSize = frame1.rows() * frame1.cols()
                val maxPossibleDiff = frameSize * 255.0
                
                val normalizedDiff = totalDiff / maxPossibleDiff
                val similarity = 1.0 - normalizedDiff
                
                diff.release()
                
                similarity.coerceIn(0.0, 1.0)
            } catch (e2: Exception) {
                Log.e("LivenessDetector", "Error computing frame similarity: ${e2.message}", e2)
                0.5 // Neutral similarity on error
            }
        }
    }
    
    /**
     * Step 4.2: Consistency Score Calculation
     * 
     * What Consistency Measures:
     * - Compares how similar consecutive frames are (0.0 = different, 1.0 = identical)
     * - High similarity (> 0.99) = frames nearly identical = likely photo = LOW score
     * - Moderate similarity (0.90-0.99) = frames vary slightly = likely real = HIGH score
     * - Low similarity (< 0.90) = frames vary a lot = likely real but moving = MODERATE score
     * 
     * Problem: Still fingers have high similarity, which was being penalized.
     * Solution: Only penalize EXTREMELY high similarity (> 0.99), be lenient for 0.90-0.99
     */
    private fun calculateConsistencyScore(avgSimilarity: Double, similarityVariance: Double): Float {
        // Simplified and more stable scoring
        // Only penalize extremely high similarity (near-perfect match = photo)
        // Be lenient for moderate-high similarity (still finger = OK)
        return when {
            // Extremely high similarity (> 0.99): likely identical frames (photo)
            // This is the only range we're confident is a spoof
            avgSimilarity > 0.99 -> {
                // Very consistent = likely spoof
                val normalized = ((avgSimilarity - 0.99) / 0.01).coerceIn(0.0, 1.0)
                0.1f + ((1.0 - normalized).toFloat() * 0.2f) // Map to 0.1-0.3 (inverted)
            }
            // High similarity (0.95-0.99): could be still finger OR photo
            // Be lenient - don't penalize too much
            avgSimilarity > 0.95 -> {
                val normalized = (avgSimilarity - 0.95) / 0.04 // 0-1 in this range
                // Map to 0.4-0.7 (moderate score, not too low)
                0.4f + (normalized.toFloat() * 0.3f)
            }
            // Moderate-high similarity (0.90-0.95): likely real finger (still or slight movement)
            // This is the ideal range for real fingers
            avgSimilarity > 0.90 -> {
                val normalized = (avgSimilarity - 0.90) / 0.05 // 0-1 in this range
                // Invert: lower similarity = more variation = higher score
                0.8f - (normalized.toFloat() * 0.2f) // Map to 0.8-0.6 (inverted)
            }
            // Moderate similarity (0.85-0.90): good variation (likely real)
            avgSimilarity > 0.85 -> {
                val normalized = (avgSimilarity - 0.85) / 0.05 // 0-1 in this range
                // Invert: lower similarity = more variation = higher score
                0.75f - (normalized.toFloat() * 0.15f) // Map to 0.75-0.6 (inverted)
            }
            // Lower similarity (< 0.85): lots of variation (likely real, moving)
            else -> {
                // Good variation, but not as ideal as moderate
                val normalized = (avgSimilarity / 0.85).coerceIn(0.0, 1.0)
                0.6f + (normalized.toFloat() * 0.15f) // Map to 0.6-0.75
            }
        }.let { baseScore: Float ->
            // Apply variance adjustment only for extreme cases
            // Very high similarity with very low variance = suspicious (photo)
            when {
                avgSimilarity > 0.99 && similarityVariance < 0.001 -> {
                    // Extremely consistent = likely photo
                    (baseScore * 0.6f).coerceIn(0f, 1f)
                }
                avgSimilarity > 0.95 && avgSimilarity < 0.99 && similarityVariance > 0.01 -> {
                    // High similarity but some variance = likely still finger (not photo)
                    (baseScore * 1.1f).coerceIn(0f, 1f)
                }
                else -> {
                    baseScore
                }
            }
        }
    }
    
    /**
     * IMPROVED: Detects JPEG compression artifacts using multiple methods.
     * Based on research: JPEG creates 8x8 block patterns and color quantization.
     * 
     * Methods:
     * 1. Block variance uniformity (existing)
     * 2. Color quantization detection (NEW - more reliable)
     * 3. Edge sharpness analysis (NEW - compression creates unnatural edges)
     * 
     * Real fingers: no compression artifacts (high score > 0.6)
     * Gallery photos: compression artifacts present (low score < 0.3)
     */
    private fun detectCompressionArtifacts(bitmap: Bitmap): Float {
        return try {
            val mat = ImageProcessor.bitmapToMat(bitmap)
            val gray = Mat()
            if (mat.channels() == 1) {
                mat.copyTo(gray)
            } else {
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            }
            mat.release()
            
            try {
                // Method 1: Color quantization detection (JPEG creates color banding)
                val colorQuantizationScore = detectColorQuantization(bitmap)
                
                // Method 2: Block variance uniformity (8x8 JPEG blocks)
                val blockUniformityScore = detectBlockUniformity(gray)
                
                // Method 3: Edge sharpness (compression creates unnatural sharp edges)
                val edgeSharpnessScore = detectUnnaturalEdges(gray)
                
                // Combine: Be more lenient - only flag as compression if VERY strong indicators
                // Real fingers might have some compression-like patterns, so be careful
                val compressionScore = when {
                    // VERY strong compression indicators from multiple methods
                    colorQuantizationScore < 0.2f && blockUniformityScore < 0.2f -> {
                        // Very likely compressed (photo)
                        0.15f
                    }
                    colorQuantizationScore < 0.25f || blockUniformityScore < 0.25f -> {
                        // Strong compression indicators
                        minOf(colorQuantizationScore, blockUniformityScore) * 0.9f
                    }
                    edgeSharpnessScore < 0.25f && (colorQuantizationScore < 0.4f || blockUniformityScore < 0.4f) -> {
                        // Unnatural edges + compression signs
                        minOf(colorQuantizationScore, blockUniformityScore, edgeSharpnessScore) * 0.95f
                    }
                    else -> {
                        // Weighted average (favor quantization, but be lenient)
                        (0.4f * colorQuantizationScore + 0.3f * blockUniformityScore + 0.3f * edgeSharpnessScore)
                    }
                }
                
                Log.d("LivenessDetector", "Compression detection: quantization=$colorQuantizationScore, blocks=$blockUniformityScore, edges=$edgeSharpnessScore, combined=$compressionScore")
                
                compressionScore.coerceIn(0f, 1f)
            } finally {
                gray.release()
            }
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in compression artifact detection: ${e.message}", e)
            0.5f
        }
    }
    
    /**
     * IMPROVED: Detects screen patterns and moiré using multiple methods.
     * Based on research: Screens create pixel grid, moiré interference, and refresh artifacts.
     * 
     * Methods:
     * 1. Regular pattern detection (pixel grid) - existing
     * 2. Enhanced moiré detection using frequency analysis - improved
     * 3. Screen refresh artifact detection (NEW)
     * 
     * Real fingers: no screen patterns (high score > 0.7)
     * Screen photos: moiré patterns, pixel grid (low score < 0.3)
     */
    private fun detectScreenPatterns(bitmap: Bitmap): Float {
        return try {
            val mat = ImageProcessor.bitmapToMat(bitmap)
            val gray = Mat()
            if (mat.channels() == 1) {
                mat.copyTo(gray)
            } else {
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            }
            mat.release()
            
            try {
                // Detect regular patterns using frequency analysis
                // Screens create regular patterns (pixel grid, moiré)
                
                // Method 1: Check for regular edge patterns (pixel grid)
                val gradX = Mat()
                val gradY = Mat()
                val gradientMagnitude = Mat()
                
                Imgproc.Sobel(gray, gradX, CvType.CV_32F, 1, 0, 3)
                Imgproc.Sobel(gray, gradY, CvType.CV_32F, 0, 1, 3)
                Core.magnitude(gradX, gradY, gradientMagnitude)
                
                // Analyze gradient in horizontal and vertical directions
                // Screens have regular patterns in both directions
                val horizontalPattern = detectRegularPattern(gradientMagnitude, true)
                val verticalPattern = detectRegularPattern(gradientMagnitude, false)
                
                // Method 2: Check for moiré (interference patterns)
                // Moiré creates wave-like patterns
                val moireScore = detectMoirePattern(gray)
                
                // Method 3: Enhanced frequency-based moiré detection
                val frequencyMoireScore = detectFrequencyMoire(gray)
                
                // Combine: More sensitive to screen patterns
                // Screens create detectable regular patterns and moiré
                val screenScore = when {
                    // VERY strong indicators from multiple methods = definitely screen
                    (horizontalPattern > 0.7 || verticalPattern > 0.7) && (moireScore > 0.6 || frequencyMoireScore > 0.7) -> {
                        // Very strong screen patterns = likely screen
                        0.10f
                    }
                    // Strong regular patterns = likely screen
                    horizontalPattern > 0.7 || verticalPattern > 0.7 -> {
                        0.15f
                    }
                    // Strong frequency moiré = likely screen
                    frequencyMoireScore > 0.75 -> {
                        0.20f
                    }
                    // Moderate-strong patterns = possibly screen
                    horizontalPattern > 0.6 || verticalPattern > 0.6 || moireScore > 0.65 -> {
                        0.25f
                    }
                    // Moderate patterns = possibly screen
                    horizontalPattern > 0.5 || verticalPattern > 0.5 || moireScore > 0.55 || frequencyMoireScore > 0.6 -> {
                        0.35f
                    }
                    // Weak patterns = uncertain
                    horizontalPattern > 0.4 || verticalPattern > 0.4 || moireScore > 0.45 || frequencyMoireScore > 0.5 -> {
                        0.50f
                    }
                    else -> {
                        // No patterns = likely real
                        0.80f
                    }
                }
                
                gradX.release()
                gradY.release()
                gradientMagnitude.release()
                
                Log.d("LivenessDetector", "Screen detection: horizontal=$horizontalPattern, vertical=$verticalPattern, moire=$moireScore, freqMoire=$frequencyMoireScore, score=$screenScore")
                
                screenScore.coerceIn(0f, 1f)
            } finally {
                gray.release()
            }
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in screen pattern detection: ${e.message}", e)
            0.5f
        }
    }
    
    /**
     * Detects regular patterns in gradient (pixel grid from screens).
     */
    private fun detectRegularPattern(gradient: Mat, horizontal: Boolean): Float {
        return try {
            // Sample a line (horizontal or vertical) and check for regularity
            val sampleLine = if (horizontal) {
                gradient.rows() / 2
            } else {
                gradient.cols() / 2
            }
            
            if (sampleLine < 0 || (horizontal && sampleLine >= gradient.rows()) || 
                (!horizontal && sampleLine >= gradient.cols())) {
                return 0.5f
            }
            
            val values = mutableListOf<Float>()
            if (horizontal) {
                for (x in 0 until gradient.cols()) {
                    values.add(gradient.get(sampleLine, x)[0].toFloat())
                }
            } else {
                for (y in 0 until gradient.rows()) {
                    values.add(gradient.get(y, sampleLine)[0].toFloat())
                }
            }
            
            if (values.size < 10) {
                return 0.5f
            }
            
            // Check for periodicity (regular patterns)
            // Compute autocorrelation to find repeating patterns
            var maxCorrelation = 0.0
            val maxPeriod = minOf(values.size / 4, 50) // Check up to 1/4 of length
            
            for (period in 2 until maxPeriod) {
                var correlation = 0.0
                var count = 0
                for (i in 0 until values.size - period) {
                    correlation += kotlin.math.abs(values[i] - values[i + period])
                    count++
                }
                if (count > 0) {
                    val avgDiff = correlation / count
                    // Low difference = high correlation = regular pattern
                    val normalizedCorrelation = 1.0 - (avgDiff / 255.0).coerceIn(0.0, 1.0)
                    if (normalizedCorrelation > maxCorrelation) {
                        maxCorrelation = normalizedCorrelation
                    }
                }
            }
            
                maxCorrelation.toFloat()
        } catch (_: Exception) {
            0.5f
        }
    }
    
    /**
     * Detects moiré patterns (wave-like interference from screens).
     */
    private fun detectMoirePattern(gray: Mat): Float {
        return try {
            // Moiré creates wave-like patterns
            // Check for sinusoidal patterns in frequency domain
            val gradX = Mat()
            val gradY = Mat()
            Imgproc.Sobel(gray, gradX, CvType.CV_32F, 1, 0, 3)
            Imgproc.Sobel(gray, gradY, CvType.CV_32F, 0, 1, 3)
            
            // Analyze gradient for wave patterns
            // Moiré typically creates regular wave-like variations
            val meanMat = MatOfDouble()
            val stdMat = MatOfDouble()
            Core.meanStdDev(gradX, meanMat, stdMat)
            val stdX = stdMat.get(0, 0)[0]
            
            Core.meanStdDev(gradY, meanMat, stdMat)
            val stdY = stdMat.get(0, 0)[0]
            
            // High std in both directions with regular patterns = moiré
            val moireIndicator = if (stdX > 30.0 && stdY > 30.0) {
                // Check for regularity in gradient distribution
                0.6f
            } else {
                0.3f
            }
            
            gradX.release()
            gradY.release()
            meanMat.release()
            stdMat.release()
            
            moireIndicator
        } catch (_: Exception) {
            0.5f
        }
    }
    
    /**
     * Checks texture variation across multiple frames (even with low motion).
     * Real fingers: texture varies slightly even when still (lighting/shadows)
     * Photos: texture is identical across frames
     */
    private fun checkTextureVariation(frames: List<Bitmap>): Float {
        return try {
            if (frames.size < 2) {
                return 0.5f
            }
            
            // Extract texture features from each frame
            val textureFeatures = mutableListOf<FloatArray>()
            for (frame in frames) {
                val features = extractSimpleTextureFeatures(frame)
                textureFeatures.add(features)
            }
            
            // Compare texture features across frames
            val similarities = mutableListOf<Float>()
            for (i in 0 until textureFeatures.size - 1) {
                val similarity = compareTextureFeatures(textureFeatures[i], textureFeatures[i + 1])
                similarities.add(similarity)
            }
            
            val avgSimilarity = similarities.average().toFloat()
            val similarityVariance = if (similarities.size > 1) {
                val mean = avgSimilarity
                kotlin.math.sqrt(similarities.map { (it - mean) * (it - mean) }.average()).toFloat()
            } else {
                0f
            }
            
            // BALANCED: More lenient - real fingers held still might have high similarity
            // Only flag as photo if EXTREMELY identical and consistent
            val variationScore = when {
                // Extremely identical AND very consistent = likely photo
                avgSimilarity > 0.99f && similarityVariance < 0.005f -> {
                    0.2f
                }
                avgSimilarity > 0.99f -> {
                    // Extremely identical = possibly photo
                    0.35f
                }
                // Very similar AND very consistent = possibly photo
                avgSimilarity > 0.97f && similarityVariance < 0.01f -> {
                    0.4f
                }
                avgSimilarity > 0.97f -> {
                    // Very similar = likely real (still finger)
                    0.55f
                }
                avgSimilarity > 0.94f -> {
                    // High similarity = likely real
                    0.65f
                }
                avgSimilarity > 0.90f -> {
                    // Moderate-high similarity = real
                    0.75f
                }
                avgSimilarity > 0.85f -> {
                    // Moderate variation = real
                    0.8f
                }
                else -> {
                    // Good variation = definitely real
                    0.85f
                }
            }
            
            Log.d("LivenessDetector", "Texture variation: avgSimilarity=$avgSimilarity, variance=$similarityVariance, score=$variationScore")
            
            variationScore.coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in texture variation check: ${e.message}", e)
            0.5f
        }
    }
    
    /**
     * Extracts simple texture features for comparison.
     */
    private fun extractSimpleTextureFeatures(bitmap: Bitmap): FloatArray {
        return try {
            val mat = ImageProcessor.bitmapToMat(bitmap)
            val gray = Mat()
            if (mat.channels() == 1) {
                mat.copyTo(gray)
            } else {
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            }
            mat.release()
            
            try {
                // Extract features: mean, std, gradient stats in 4x4 grid
                val gridSize = 4
                val features = mutableListOf<Float>()
                
                val patchHeight = gray.rows() / gridSize
                val patchWidth = gray.cols() / gridSize
                
                for (row in 0 until gridSize) {
                    for (col in 0 until gridSize) {
                        val startX = col * patchWidth
                        val startY = row * patchHeight
                        val endX = if (col == gridSize - 1) gray.cols() else (col + 1) * patchWidth
                        val endY = if (row == gridSize - 1) gray.rows() else (row + 1) * patchHeight
                        
                        val patch = Mat(gray, Rect(startX, startY, endX - startX, endY - startY))
                        
                        val meanMat = MatOfDouble()
                        val stdMat = MatOfDouble()
                        Core.meanStdDev(patch, meanMat, stdMat)
                        
                        features.add(meanMat.get(0, 0)[0].toFloat())
                        features.add(stdMat.get(0, 0)[0].toFloat())
                        
                        patch.release()
                        meanMat.release()
                        stdMat.release()
                    }
                }
                
                features.toFloatArray()
            } finally {
                gray.release()
            }
        } catch (_: Exception) {
            FloatArray(32) { 0f } // 4x4 grid * 2 features = 32
        }
    }
    
    /**
     * NEW: Detects color quantization (JPEG compression creates color banding).
     * Real images: smooth color gradients
     * JPEG photos: quantized colors, banding artifacts
     */
    private fun detectColorQuantization(bitmap: Bitmap): Float {
        return try {
            val mat = ImageProcessor.bitmapToMat(bitmap)
            val hsv = Mat()
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
            mat.release()
            
            try {
                // Extract value (brightness) channel
                val channels = mutableListOf<Mat>()
                Core.split(hsv, channels)
                val valueChannel = channels[2] // V channel
                
                // Analyze color distribution
                // JPEG quantization creates clusters in color space
                // Count unique color values - compressed images have fewer unique values
                val uniqueValues = mutableSetOf<Int>()
                val valueCounts = mutableMapOf<Int, Int>()
                
                for (y in 0 until valueChannel.rows()) {
                    for (x in 0 until valueChannel.cols()) {
                        val value = valueChannel.get(y, x)[0].toInt()
                        uniqueValues.add(value)
                        valueCounts[value] = valueCounts.getOrDefault(value, 0) + 1
                    }
                }
                
                val totalPixels = valueChannel.rows() * valueChannel.cols()
                val uniqueRatio = uniqueValues.size.toFloat() / totalPixels
                
                // JPEG creates fewer unique colors (quantization)
                // Real images have more color variation
                // Be more lenient - real fingers might have some quantization-like patterns
                val quantizationScore = when {
                    uniqueRatio < 0.2f -> {
                        // Extremely few unique colors = heavy quantization (JPEG)
                        0.15f
                    }
                    uniqueRatio < 0.35f -> {
                        // Very few unique colors = likely quantization
                        0.3f
                    }
                    uniqueRatio < 0.5f -> {
                        // Moderate quantization
                        0.45f
                    }
                    uniqueRatio < 0.65f -> {
                        // Some quantization
                        0.6f
                    }
                    else -> {
                        // Natural color variation (real image)
                        0.75f
                    }
                }
                
                // Also check for color banding (sudden jumps in color)
                var bandingCount = 0
                val bandingThreshold = 20 // Large jumps indicate quantization
                for (y in 1 until valueChannel.rows()) {
                    for (x in 0 until valueChannel.cols()) {
                        val diff = kotlin.math.abs(valueChannel.get(y, x)[0] - valueChannel.get(y - 1, x)[0])
                        if (diff > bandingThreshold) {
                            bandingCount++
                        }
                    }
                }
                val bandingRatio = bandingCount.toFloat() / (valueChannel.rows() * valueChannel.cols())
                
                // High banding = quantization artifacts
                // Be more lenient - only penalize if very high banding
                val finalScore = if (bandingRatio > 0.15f) {
                    // Very significant banding detected
                    quantizationScore * 0.8f
                } else if (bandingRatio > 0.1f) {
                    // Moderate banding
                    quantizationScore * 0.9f
                } else {
                    quantizationScore
                }
                
                channels.forEach { it.release() }
                hsv.release()
                
                finalScore.coerceIn(0f, 1f)
            } finally {
                hsv.release()
            }
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in color quantization detection: ${e.message}", e)
            0.5f
        }
    }
    
    /**
     * NEW: Improved block uniformity detection for JPEG 8x8 blocks.
     */
    private fun detectBlockUniformity(gray: Mat): Float {
        return try {
            val blockSize = 8
            val blockVariances = mutableListOf<Double>()
            
            for (y in 0 until gray.rows() step blockSize) {
                for (x in 0 until gray.cols() step blockSize) {
                    val endX = (x + blockSize).coerceAtMost(gray.cols())
                    val endY = (y + blockSize).coerceAtMost(gray.rows())
                    
                    val block = Mat(gray, Rect(x, y, endX - x, endY - y))
                    
                    val meanMat = MatOfDouble()
                    val stdMat = MatOfDouble()
                    Core.meanStdDev(block, meanMat, stdMat)
                    
                    val std = stdMat.get(0, 0)[0]
                    blockVariances.add(std)
                    
                    block.release()
                    meanMat.release()
                    stdMat.release()
                }
            }
            
            if (blockVariances.isEmpty()) {
                return 0.5f
            }
            
            // JPEG creates very uniform variance across 8x8 blocks
            val varianceOfVariances = if (blockVariances.size > 1) {
                val mean = blockVariances.average()
                blockVariances.map { (it - mean) * (it - mean) }.average()
            } else {
                0.0
            }
            
            // More lenient thresholds - real fingers might have some uniformity
            when {
                varianceOfVariances < 20.0 -> {
                    // Extremely uniform = likely JPEG
                    0.2f
                }
                varianceOfVariances < 60.0 -> {
                    // Very uniform = possibly JPEG
                    (varianceOfVariances / 60.0 * 0.3).toFloat()
                }
                varianceOfVariances < 150.0 -> {
                    // Moderate variation
                    val normalized = (varianceOfVariances - 60.0) / 90.0
                    0.3f + (normalized.toFloat() * 0.35f)
                }
                varianceOfVariances < 300.0 -> {
                    // Good variation
                    val normalized = (varianceOfVariances - 150.0) / 150.0
                    0.65f + (normalized.toFloat() * 0.15f)
                }
                else -> {
                    // High variation = natural (real)
                    0.8f
                }
            }
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in block uniformity detection: ${e.message}", e)
            0.5f
        }
    }
    
    /**
     * NEW: Detects unnatural edge sharpness from compression.
     * JPEG compression can create unnaturally sharp edges at block boundaries.
     */
    private fun detectUnnaturalEdges(gray: Mat): Float {
        return try {
            // Detect edges
            val edges = Mat()
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            
            // Check for regular edge patterns (8x8 grid from JPEG)
            val blockSize = 8
            var edgeBlockCount = 0
            var totalBlocks = 0
            
            for (y in 0 until gray.rows() step blockSize) {
                for (x in 0 until gray.cols() step blockSize) {
                    val endX = (x + blockSize).coerceAtMost(gray.cols())
                    val endY = (y + blockSize).coerceAtMost(gray.rows())
                    
                    val edgeBlock = Mat(edges, Rect(x, y, endX - x, endY - y))
                    val edgePixels = Core.countNonZero(edgeBlock)
                    
                    // JPEG blocks often have edges at boundaries
                    // Check if edges are concentrated at block boundaries
                    if (edgePixels > (edgeBlock.rows() * edgeBlock.cols() * 0.1)) {
                        edgeBlockCount++
                    }
                    totalBlocks++
                    
                    edgeBlock.release()
                }
            }
            
            edges.release()
            
            if (totalBlocks == 0) {
                return 0.5f
            }
            
            val edgeRatio = edgeBlockCount.toFloat() / totalBlocks
            
            // High edge ratio in regular blocks = compression artifacts
            when {
                edgeRatio > 0.8f -> {
                    // Very regular edge pattern = likely compression
                    0.2f
                }
                edgeRatio > 0.6f -> {
                    // Regular edges = possibly compression
                    0.4f
                }
                else -> {
                    // Natural edge distribution
                    0.7f
                }
            }
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in unnatural edge detection: ${e.message}", e)
            0.5f
        }
    }
    
    /**
     * NEW: Enhanced frequency-based moiré detection.
     * Uses autocorrelation to detect periodic interference patterns.
     */
    private fun detectFrequencyMoire(gray: Mat): Float {
        return try {
            // Sample patches and check for periodic patterns
            val patchSize = 64
            val moireScores = mutableListOf<Float>()
            
            for (y in 0 until gray.rows() step patchSize) {
                for (x in 0 until gray.cols() step patchSize) {
                    val endX = (x + patchSize).coerceAtMost(gray.cols())
                    val endY = (y + patchSize).coerceAtMost(gray.rows())
                    
                    if (endX - x < 32 || endY - y < 32) continue
                    
                    val patch = Mat(gray, Rect(x, y, endX - x, endY - y))
                    
                    // Check for periodic patterns using autocorrelation
                    val periodicity = detectPeriodicity(patch)
                    moireScores.add(periodicity)
                    
                    patch.release()
                }
            }
            
            if (moireScores.isEmpty()) {
                return 0.5f
            }
            
            // High periodicity = moiré patterns
            val avgPeriodicity = moireScores.average().toFloat()
            
            when {
                avgPeriodicity > 0.7f -> {
                    // Strong periodic patterns = moiré
                    0.1f
                }
                avgPeriodicity > 0.5f -> {
                    // Moderate periodicity
                    0.3f
                }
                else -> {
                    // Natural patterns
                    0.8f
                }
            }
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in frequency moiré detection: ${e.message}", e)
            0.5f
        }
    }
    
    /**
     * Detects periodicity in a patch using autocorrelation.
     */
    private fun detectPeriodicity(patch: Mat): Float {
        return try {
            // Sample a row and check for repeating patterns
            val sampleRow = patch.rows() / 2
            if (sampleRow < 0 || sampleRow >= patch.rows()) {
                return 0.5f
            }
            
            val values = mutableListOf<Float>()
            for (x in 0 until patch.cols()) {
                values.add(patch.get(sampleRow, x)[0].toFloat())
            }
            
            if (values.size < 20) {
                return 0.5f
            }
            
            // Compute autocorrelation for different periods
            var maxCorrelation = 0.0
            val maxPeriod = minOf(values.size / 3, 30)
            
            for (period in 2 until maxPeriod) {
                var correlation = 0.0
                var count = 0
                for (i in 0 until values.size - period) {
                    val diff = kotlin.math.abs(values[i] - values[i + period])
                    correlation += (1.0 - diff / 255.0) // Normalize
                    count++
                }
                if (count > 0) {
                    val avgCorrelation = correlation / count
                    if (avgCorrelation > maxCorrelation) {
                        maxCorrelation = avgCorrelation
                    }
                }
            }
            
            maxCorrelation.toFloat()
        } catch (_: Exception) {
            0.5f
        }
    }
    
    /**
     * Compares two texture feature vectors.
     */
    private fun compareTextureFeatures(features1: FloatArray, features2: FloatArray): Float {
        if (features1.size != features2.size || features1.isEmpty()) {
            return 0.5f
        }
        
        // Compute normalized correlation
        var sum1 = 0.0
        var sum2 = 0.0
        var sum12 = 0.0
        var sum1Sq = 0.0
        var sum2Sq = 0.0
        
        for (i in features1.indices) {
            val f1 = features1[i].toDouble()
            val f2 = features2[i].toDouble()
            sum1 += f1
            sum2 += f2
            sum12 += f1 * f2
            sum1Sq += f1 * f1
            sum2Sq += f2 * f2
        }
        
        val n = features1.size.toDouble()
        val numerator = (n * sum12 - sum1 * sum2)
        val denominator = kotlin.math.sqrt((n * sum1Sq - sum1 * sum1) * (n * sum2Sq - sum2 * sum2))
        
        return if (denominator > 0.0001) {
            (numerator / denominator).coerceIn(-1.0, 1.0).toFloat()
        } else {
            1.0f // Identical if denominator is zero
        }
    }
}

data class LivenessResult(
    val isLive: Boolean = false,
    val confidence: Float = 0f,
    val motionScore: Float = 0f,
    val textureScore: Float = 0f,
    val consistencyScore: Float = 0f
)
