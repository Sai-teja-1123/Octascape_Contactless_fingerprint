package com.contactless.fingerprint.utils

object Constants {
    // Quality thresholds (calibrated for improved algorithms)
    const val MIN_BLUR_SCORE = 0.45f          // Lowered slightly - improved blur detection is more accurate
    const val MIN_ILLUMINATION_SCORE = 0.5f  // Keep same - illumination check is stable
    const val MIN_COVERAGE_SCORE = 0.08f     // Adjusted for edge-based coverage metric (8% = reasonable minimum)
    const val MIN_ORIENTATION_SCORE = 0.4f  // New - orientation check (finger should be vertical)
    const val MIN_OVERALL_QUALITY = 0.55f    // Slightly lowered - better algorithms = more accurate scores
    
    // Image dimensions
    const val CAPTURE_WIDTH = 1920
    const val CAPTURE_HEIGHT = 1080
    const val ISO_RESOLUTION_WIDTH = 500
    const val ISO_RESOLUTION_HEIGHT = 500
    
    // Matching thresholds
    const val MATCH_THRESHOLD = 0.7f
    
    // Liveness thresholds
    const val MIN_LIVENESS_SCORE = 0.6f
}
