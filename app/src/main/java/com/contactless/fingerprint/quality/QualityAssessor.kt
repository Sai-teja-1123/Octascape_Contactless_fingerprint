package com.contactless.fingerprint.quality

import android.graphics.Bitmap
import com.contactless.fingerprint.utils.Constants

/**
 * Assesses quality of captured finger images
 * Track A: Quality indicators (blur, illumination, coverage, orientation)
 * 
 * Now includes:
 * - Improved blur detection with calibrated thresholds
 * - Enhanced coverage analysis using finger detection
 * - Orientation analysis for proper finger positioning
 * - Clear failure reasons instead of only PASS/FAIL
 */
class QualityAssessor {
    fun assessQuality(bitmap: Bitmap): QualityResult {
        // Calculate individual quality scores
        val blurScore = BlurDetector.calculateBlurScore(bitmap)
        val illuminationScore = IlluminationChecker.checkIllumination(bitmap)
        val coverageScore = CoverageAnalyzer.analyzeCoverage(bitmap)
        val orientationScore = OrientationAnalyzer.analyzeOrientation(bitmap)
        
        // Calculate overall score (weighted average)
        // Blur is most important (40%), then coverage (25%), illumination (20%), orientation (15%)
        val overallScore = (
            blurScore * 0.40f +
            coverageScore * 0.25f +
            illuminationScore * 0.20f +
            orientationScore * 0.15f
        )
        
        // Determine pass/fail based on thresholds
        // All individual scores must meet minimum thresholds AND overall score must pass
        val isPass = overallScore >= Constants.MIN_OVERALL_QUALITY &&
                blurScore >= Constants.MIN_BLUR_SCORE &&
                illuminationScore >= Constants.MIN_ILLUMINATION_SCORE &&
                coverageScore >= Constants.MIN_COVERAGE_SCORE &&
                orientationScore >= Constants.MIN_ORIENTATION_SCORE
        
        // Collect failure reasons for better user feedback
        val failureReasons = mutableListOf<String>()
        if (!isPass) {
            if (blurScore < Constants.MIN_BLUR_SCORE) {
                failureReasons.add("Image too blurry")
            }
            if (illuminationScore < Constants.MIN_ILLUMINATION_SCORE) {
                failureReasons.add("Low light")
            }
            if (coverageScore < Constants.MIN_COVERAGE_SCORE) {
                failureReasons.add("Partial finger detected")
            }
            if (orientationScore < Constants.MIN_ORIENTATION_SCORE) {
                failureReasons.add("Finger misaligned")
            }
            if (overallScore < Constants.MIN_OVERALL_QUALITY) {
                failureReasons.add("Overall quality too low")
            }
        }
        
        return QualityResult(
            blurScore = blurScore,
            illuminationScore = illuminationScore,
            coverageScore = coverageScore,
            orientationScore = orientationScore,
            overallScore = overallScore,
            isPass = isPass,
            failureReasons = failureReasons
        )
    }
}

data class QualityResult(
    val blurScore: Float = 0f,
    val illuminationScore: Float = 0f,
    val coverageScore: Float = 0f,
    val orientationScore: Float = 0f,
    val overallScore: Float = 0f,
    val isPass: Boolean = false,
    val failureReasons: List<String> = emptyList()
)
