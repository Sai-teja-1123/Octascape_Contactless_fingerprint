package com.contactless.fingerprint.matching

import android.util.Log

/**
 * Simple in-memory repository for managing fingerprint enrollments.
 * 
 * This is a basic implementation for Track-C. In a production system,
 * you would persist enrollments to a database or secure storage.
 * 
 * Thread-safe operations using synchronized access.
 */
object EnrollmentRepository {
    private val enrollments = mutableListOf<FingerprintEnrollment>()
    private val lock = Any()

    /**
     * Adds a new enrollment to the repository.
     * 
     * @param enrollment The enrollment to add
     * @return true if added successfully, false if ID already exists
     */
    fun addEnrollment(enrollment: FingerprintEnrollment): Boolean {
        synchronized(lock) {
            if (!enrollment.isValid()) {
                Log.w("EnrollmentRepository", "Attempted to add invalid enrollment: ${enrollment.id}")
                return false
            }
            
            if (enrollments.any { it.id == enrollment.id }) {
                Log.w("EnrollmentRepository", "Enrollment with ID ${enrollment.id} already exists")
                return false
            }
            
            enrollments.add(enrollment)
            Log.d("EnrollmentRepository", "Added enrollment: ${enrollment.id} with ${enrollment.imageUris.size} images")
            return true
        }
    }

    /**
     * Removes an enrollment by ID.
     * 
     * @param id The ID of the enrollment to remove
     * @return true if removed, false if not found
     */
    fun removeEnrollment(id: String): Boolean {
        synchronized(lock) {
            val removed = enrollments.removeAll { it.id == id }
            if (removed) {
                Log.d("EnrollmentRepository", "Removed enrollment: $id")
            } else {
                Log.w("EnrollmentRepository", "Enrollment not found: $id")
            }
            return removed
        }
    }

    /**
     * Gets an enrollment by ID.
     * 
     * @param id The ID of the enrollment to retrieve
     * @return The enrollment if found, null otherwise
     */
    fun getEnrollment(id: String): FingerprintEnrollment? {
        synchronized(lock) {
            return enrollments.find { it.id == id }
        }
    }

    /**
     * Gets all enrollments.
     * 
     * @return A list of all enrollments (copy to prevent external modification)
     */
    fun getAllEnrollments(): List<FingerprintEnrollment> {
        synchronized(lock) {
            return enrollments.toList()
        }
    }

    /**
     * Gets all enrollment IDs.
     * 
     * @return A list of all enrollment IDs
     */
    fun getAllIds(): List<String> {
        synchronized(lock) {
            return enrollments.map { it.id }
        }
    }

    /**
     * Checks if an enrollment with the given ID exists.
     * 
     * @param id The ID to check
     * @return true if exists, false otherwise
     */
    fun hasEnrollment(id: String): Boolean {
        synchronized(lock) {
            return enrollments.any { it.id == id }
        }
    }

    /**
     * Clears all enrollments.
     * Useful for testing or reset.
     */
    fun clearAll() {
        synchronized(lock) {
            enrollments.clear()
            Log.d("EnrollmentRepository", "Cleared all enrollments")
        }
    }

    /**
     * Gets the count of enrollments.
     * 
     * @return The number of enrollments
     */
    fun getCount(): Int {
        synchronized(lock) {
            return enrollments.size
        }
    }
}
