package com.contactless.fingerprint.matching

import android.net.Uri

/**
 * Represents an enrolled fingerprint with contact-based images.
 * 
 * Used in Track-C to store contact-based fingerprint images
 * that can be matched against contactless captures.
 * 
 * @param id Unique identifier for this enrollment (e.g., "P001", "Sai_RightIndex")
 * @param name Optional human-readable name (e.g., "Sai - Right Index Finger")
 * @param imageUris List of URIs pointing to contact-based fingerprint images
 * @param enrolledDate Timestamp when this enrollment was created
 */
data class FingerprintEnrollment(
    val id: String,
    val name: String,
    val imageUris: List<Uri>,
    val enrolledDate: Long = System.currentTimeMillis()
) {
    /**
     * Validates that the enrollment has required data.
     */
    fun isValid(): Boolean {
        return id.isNotBlank() && imageUris.isNotEmpty()
    }
}
