package com.contactless.fingerprint.matching

/**
 * Matches contactless and contact-based fingerprints
 * Track C: Similarity score and match/no-match decision
 */
class Matcher {
    fun match(
        contactlessFeatures: FingerprintFeatures,
        contactFeatures: FingerprintFeatures
    ): MatchResult {
        // Matching algorithm
        return MatchResult()
    }
}

data class MatchResult(
    val similarityScore: Float = 0f,
    val isMatch: Boolean = false,
    val confidence: Float = 0f
)
