package com.contactless.fingerprint.matching

import android.util.Log
import com.contactless.fingerprint.utils.Constants
import kotlin.math.*

/**
 * Matches contactless and contact-based fingerprints
 * Track C: Similarity score and match/no-match decision
 * 
 * Uses minutiae-based matching only (removed surrogate features).
 */
class Matcher {
    /**
     * Computes overall similarity between two fingerprint feature sets.
     * 
     * Uses minutiae-based matching only (removed surrogate features).
     * 
     * @param features1 First fingerprint features
     * @param features2 Second fingerprint features
     * @return Overall similarity score (0.0 to 1.0)
     */
    fun computeSimilarity(
        features1: FingerprintFeatures,
        features2: FingerprintFeatures
    ): Float {
        return try {
            // Use only minutiae matching (removed surrogate features)
            if (features1.minutiae.isNotEmpty() && features2.minutiae.isNotEmpty()) {
                val similarity = matchMinutiae(features1.minutiae, features2.minutiae)
                Log.d("Matcher", "Minutiae matching: ${features1.minutiae.size} vs ${features2.minutiae.size} minutiae, similarity=$similarity")
                return similarity
            }
            
            // No fallback - minutiae is required
            Log.w("Matcher", "Minutiae extraction failed or empty: ${features1.minutiae.size} vs ${features2.minutiae.size}")
            0f
        } catch (e: Exception) {
            Log.e("Matcher", "Error computing similarity: ${e.message}", e)
            0f
        }
    }
    
    /**
     * Matches minutiae between two fingerprints.
     * Uses local descriptor matching with tolerance for rotation/translation.
     * STRICT: Requires high-quality matches to reduce false positives.
     */
    private fun matchMinutiae(
        minutiae1: List<Minutia>,
        minutiae2: List<Minutia>
    ): Float {
        val matchStart = System.currentTimeMillis()
        if (minutiae1.isEmpty() || minutiae2.isEmpty()) {
            return 0f
        }
        
        // Require minimum number of minutiae for reliable matching
        if (minutiae1.size < 5 || minutiae2.size < 5) {
            Log.w("Matcher", "Too few minutiae: ${minutiae1.size} and ${minutiae2.size}")
            return 0f
        }
        
        // Check if minutiae counts are very different (likely different fingers)
        val countRatio = min(minutiae1.size, minutiae2.size).toFloat() / max(minutiae1.size, minutiae2.size).toFloat()
        if (countRatio < 0.5f) {
            // Very different counts - likely different fingers
            return 0.3f
        }
        
        // Build local descriptors for each minutia
        val descriptors1 = buildDescriptors(minutiae1)
        val descriptors2 = buildDescriptors(minutiae2)
        
        // Match descriptors with STRICT criteria
        var matches = 0
        var highQualityMatches = 0
        val matched2 = BooleanArray(minutiae2.size) { false }
        
        for (i in descriptors1.indices) {
            var bestMatch = -1
            var bestScore = 0f
            
            for (j in descriptors2.indices) {
                if (matched2[j]) continue
                
                val score = matchDescriptors(descriptors1[i], descriptors2[j])
                // STRICT: Require higher match quality (0.75 instead of 0.6)
                if (score > bestScore && score > 0.75f) {
                    bestScore = score
                    bestMatch = j
                }
            }
            
            if (bestMatch >= 0) {
                matches++
                if (bestScore > 0.85f) {
                    highQualityMatches++
                }
                matched2[bestMatch] = true
            }
        }
        
        // Similarity calculation - use max size to normalize properly
        val maxMinutiae = max(minutiae1.size, minutiae2.size).toFloat()
        val avgMinutiae = (minutiae1.size + minutiae2.size) / 2.0f
        if (maxMinutiae <= 0) return 0f
        
        // Match ratio: what fraction of minutiae matched
        val matchRatio = matches / maxMinutiae
        
        // Base similarity: match ratio scaled to 0-1 range
        // Use matchRatio directly, but apply scaling based on quality
        var baseSimilarity = matchRatio.coerceIn(0f, 1f)
        
        Log.d("Matcher", "Matching stats: matches=$matches, maxMinutiae=$maxMinutiae, avgMinutiae=$avgMinutiae, matchRatio=$matchRatio, baseSimilarity=$baseSimilarity")
        
        if (matchRatio < 0.2f) {
            // Too few matches - very low similarity
            val final = baseSimilarity * 0.3f
            Log.d("Matcher", "Very low match ratio (<0.2), scaled similarity: $final")
            return final
        }
        
        // Boost if many high-quality matches
        val qualityBoost = if (highQualityMatches > matches / 2 && highQualityMatches >= 3) {
            1.15f // Boost for high quality matches
        } else {
            1.0f
        }
        
        // Apply scaling based on match ratio
        val finalSimilarity = when {
            matchRatio > 0.5f -> {
                // High match ratio - likely same finger
                val result = baseSimilarity * qualityBoost
                Log.d("Matcher", "High match ratio (>0.5), final similarity: $result")
                result
            }
            matchRatio > 0.3f -> {
                // Moderate match ratio
                val result = baseSimilarity * 0.85f
                Log.d("Matcher", "Moderate match ratio (0.3-0.5), final similarity: $result")
                result
            }
            else -> {
                // Low match ratio (0.2-0.3)
                val result = baseSimilarity * 0.6f
                Log.d("Matcher", "Low match ratio (0.2-0.3), final similarity: $result")
                result
            }
        }
        
        val final = finalSimilarity.coerceIn(0f, 1f)
        val matchTime = System.currentTimeMillis() - matchStart
        Log.d("Matcher", "Minutiae matching took ${matchTime}ms total")
        return final
    }
    
    /**
     * Builds local descriptor for each minutia.
     * Descriptor includes: type, angle, and relative positions of nearby minutiae.
     * OPTIMIZED: Limits nearby minutiae to closest ones for performance.
     */
    private fun buildDescriptors(minutiae: List<Minutia>): List<MinutiaDescriptor> {
        val descriptors = mutableListOf<MinutiaDescriptor>()
        val maxDistance = 40.0f // Reduced search radius for performance
        val maxNearby = 8 // Limit number of nearby minutiae per descriptor
        
        for (m in minutiae) {
            val nearby = mutableListOf<Triple<Float, Float, Float>>() // (distance, angle_diff, priority)
            
            // Collect all nearby minutiae with distances
            for (other in minutiae) {
                if (other == m) continue
                
                val dx = other.x - m.x
                val dy = other.y - m.y
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                
                if (distance <= maxDistance) {
                    val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                    val angleDiff = ((angle - m.angle + PI.toFloat()) % (2 * PI.toFloat()) - PI.toFloat()).toFloat()
                    // Priority: closer = higher priority
                    nearby.add(Triple(distance, angleDiff, -distance))
                }
            }
            
            // Sort by distance (closest first) and take only top N
            val sortedNearby = nearby.sortedBy { it.third }.take(maxNearby)
            val finalNearby = sortedNearby.map { Pair(it.first, it.second) }
            
            descriptors.add(MinutiaDescriptor(m, finalNearby))
        }
        
        return descriptors
    }
    
    /**
     * Matches two minutiae descriptors.
     */
    private fun matchDescriptors(desc1: MinutiaDescriptor, desc2: MinutiaDescriptor): Float {
        // Type must match
        if (desc1.minutia.type != desc2.minutia.type) {
            return 0f
        }
        
        // STRICT: Angle similarity (less tolerance)
        val angleDiff = abs(desc1.minutia.angle - desc2.minutia.angle)
        // Normalize angle difference to 0-PI range
        val normalizedAngleDiff = min(angleDiff, (2 * PI.toFloat() - angleDiff))
        val angleSimilarity = 1.0f - (normalizedAngleDiff / (PI.toFloat() / 2.0f)).coerceIn(0f, 1f)
        
        // STRICT: Nearby minutiae similarity (more weight)
        val nearbySimilarity = matchNearbyMinutiae(desc1.nearby, desc2.nearby)
        
        // Require BOTH angle and nearby to be good (stricter)
        val combined = (0.3f * angleSimilarity + 0.7f * nearbySimilarity)
        
        // Only return high score if both are good
        return if (angleSimilarity > 0.7f && nearbySimilarity > 0.7f) {
            combined.coerceIn(0f, 1f)
        } else {
            // Penalize if either is poor
            combined * 0.6f
        }
    }
    
    /**
     * Matches nearby minutiae patterns.
     */
    private fun matchNearbyMinutiae(
        nearby1: List<Pair<Float, Float>>,
        nearby2: List<Pair<Float, Float>>
    ): Float {
        if (nearby1.isEmpty() && nearby2.isEmpty()) return 0.5f // Not a strong indicator
        if (nearby1.isEmpty() || nearby2.isEmpty()) return 0.2f // Penalize missing context
        
        var matches = 0
        val matched2 = BooleanArray(nearby2.size) { false }
        
        for (n1 in nearby1) {
            for (j in nearby2.indices) {
                if (matched2[j]) continue
                
                val n2 = nearby2[j]
                val distDiff = abs(n1.first - n2.first)
                val angleDiff = abs(n1.second - n2.second)
                
                // STRICT: Tighter tolerance for matching
                if (distDiff < 8.0f && angleDiff < 0.3f) {
                    matches++
                    matched2[j] = true
                    break
                }
            }
        }
        
        val avgCount = (nearby1.size + nearby2.size) / 2.0f
        return if (avgCount > 0) {
            (matches * 2.0f / avgCount).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    
    /**
     * Descriptor for a minutia including its local context.
     */
    private data class MinutiaDescriptor(
        val minutia: Minutia,
        val nearby: List<Pair<Float, Float>> // (distance, angle_diff) to nearby minutiae
    )
    
    /**
     * Makes match/no-match decision based on similarity score.
     * 
     * @param similarityScore Similarity score (0.0 to 1.0)
     * @return MatchResult with decision
     */
    private fun makeMatchDecision(similarityScore: Float): MatchResult {
        val threshold = Constants.MATCH_THRESHOLD // 0.7
        val isMatch = similarityScore >= threshold
        val confidence = similarityScore // Confidence = similarity score
        
        Log.d("Matcher", "Match decision: similarity=$similarityScore, threshold=$threshold, isMatch=$isMatch")
        
        return MatchResult(
            similarityScore = similarityScore,
            isMatch = isMatch,
            confidence = confidence
        )
    }
    
    /**
     * Main matching function: matches contactless features against contact-based features.
     * 
     * @param contactlessFeatures Features from contactless captured fingerprint
     * @param contactFeatures Features from contact-based enrolled fingerprint
     * @return MatchResult with similarity score and match/no-match decision
     */
    fun match(
        contactlessFeatures: FingerprintFeatures,
        contactFeatures: FingerprintFeatures
    ): MatchResult {
        // Step 4.2: Compute similarity
        val similarityScore = computeSimilarity(contactlessFeatures, contactFeatures)
        
        // Step 4.3: Make match decision
        return makeMatchDecision(similarityScore)
    }
}

data class MatchResult(
    val similarityScore: Float = 0f,
    val isMatch: Boolean = false,
    val confidence: Float = 0f
)
