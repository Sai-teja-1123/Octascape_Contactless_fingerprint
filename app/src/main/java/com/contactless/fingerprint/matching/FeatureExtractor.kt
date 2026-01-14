package com.contactless.fingerprint.matching

import android.graphics.Bitmap

/**
 * Extracts features from fingerprint images
 * Track C: Feature extraction (minutiae or surrogate features)
 */
class FeatureExtractor {
    fun extractFeatures(bitmap: Bitmap): FingerprintFeatures {
        // Feature extraction logic
        return FingerprintFeatures()
    }
}

data class FingerprintFeatures(
    val minutiae: List<Minutia> = emptyList(),
    val orientation: Float = 0f,
    val core: android.graphics.Point? = null,
    val delta: android.graphics.Point? = null
)

data class Minutia(
    val x: Int,
    val y: Int,
    val type: MinutiaType,
    val angle: Float
)

enum class MinutiaType {
    RIDGE_ENDING,
    BIFURCATION
}
