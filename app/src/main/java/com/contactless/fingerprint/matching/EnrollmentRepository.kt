package com.contactless.fingerprint.matching

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Persistent repository for managing fingerprint enrollments.
 * 
 * Stores enrollments using:
 * - SharedPreferences for metadata (IDs, names, dates, image file paths)
 * - Internal storage for image files
 * 
 * No database required - uses simple file-based storage.
 * Data persists across app restarts.
 * Each enrollment has a unique ID.
 */
object EnrollmentRepository {
    private var context: Context? = null
    private val gson = Gson()
    private val enrollments = mutableListOf<FingerprintEnrollment>()
    private val lock = Any()
    
    private val prefs: android.content.SharedPreferences?
        get() = context?.getSharedPreferences("fingerprint_enrollments", Context.MODE_PRIVATE)
    
    private val enrollmentsDir: File?
        get() = context?.filesDir?.let { File(it, "enrollments") }
    
    /**
     * Initialize the repository with application context.
     * Must be called once in MainActivity.onCreate()
     */
    fun initialize(appContext: Context) {
        synchronized(lock) {
            if (context == null) {
                context = appContext.applicationContext
                // Create enrollments directory
                enrollmentsDir?.mkdirs()
                // Load existing enrollments
                loadEnrollments()
                Log.d("EnrollmentRepository", "Initialized with ${enrollments.size} enrollments")
            }
        }
    }
    
    /**
     * Loads all enrollments from persistent storage.
     */
    private fun loadEnrollments() {
        val prefs = this.prefs ?: return
        val enrollmentsDir = this.enrollmentsDir ?: return
        
        try {
            val json = prefs.getString("enrollments", "[]") ?: "[]"
            val type = object : TypeToken<List<EnrollmentData>>() {}.type
            val enrollmentDataList: List<EnrollmentData> = gson.fromJson(json, type)
            
            enrollments.clear()
            enrollmentDataList.forEach { data ->
                // Convert file paths back to file URIs
                val imageUris = data.imageFilePaths.mapNotNull { filePath ->
                    val file = File(enrollmentsDir, filePath)
                    if (file.exists()) {
                        android.net.Uri.fromFile(file)
                    } else {
                        Log.w("EnrollmentRepository", "Image file not found: $filePath")
                        null
                    }
                }
                
                if (imageUris.isNotEmpty()) {
                    enrollments.add(
                        FingerprintEnrollment(
                            id = data.id,
                            name = data.name,
                            imageUris = imageUris,
                            enrolledDate = data.enrolledDate
                        )
                    )
                } else {
                    Log.w("EnrollmentRepository", "Skipping enrollment ${data.id} - no valid images")
                }
            }
            
            Log.d("EnrollmentRepository", "Loaded ${enrollments.size} enrollments from storage")
        } catch (e: Exception) {
            Log.e("EnrollmentRepository", "Error loading enrollments", e)
        }
    }
    
    /**
     * Saves all enrollments to persistent storage.
     */
    private fun saveEnrollments() {
        val prefs = this.prefs ?: return
        
        try {
            val enrollmentDataList = enrollments.map { enrollment ->
                EnrollmentData(
                    id = enrollment.id,
                    name = enrollment.name,
                    imageFilePaths = enrollment.imageUris.map { uri ->
                        // Extract filename from URI path
                        File(uri.path ?: "").name
                    },
                    enrolledDate = enrollment.enrolledDate
                )
            }
            
            val json = gson.toJson(enrollmentDataList)
            prefs.edit().putString("enrollments", json).apply()
            
            Log.d("EnrollmentRepository", "Saved ${enrollments.size} enrollments to storage")
        } catch (e: Exception) {
            Log.e("EnrollmentRepository", "Error saving enrollments", e)
        }
    }
    
    /**
     * Copies an image from URI to internal storage.
     * 
     * @param uri Source URI (from gallery)
     * @param enrollmentId Enrollment ID for file naming
     * @param index Image index for multiple images
     * @return File path relative to enrollments directory, or null if failed
     */
    private suspend fun copyImageToStorage(uri: Uri, enrollmentId: String, index: Int): String? {
        val enrollmentsDir = this.enrollmentsDir ?: return null
        val context = this.context ?: return null
        
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "${enrollmentId}_${index}_${System.currentTimeMillis()}.jpg"
                val destFile = File(enrollmentsDir, fileName)
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                Log.d("EnrollmentRepository", "Copied image to: ${destFile.name}")
                fileName
            } catch (e: Exception) {
                Log.e("EnrollmentRepository", "Error copying image", e)
                null
            }
        }
    }
    
    /**
     * Adds a new enrollment. Copies images to storage and saves metadata.
     * Each enrollment must have a unique ID.
     * 
     * @param enrollment The enrollment to add (with URIs from gallery)
     * @return true if added successfully, false if ID already exists or copy failed
     */
    suspend fun addEnrollment(enrollment: FingerprintEnrollment): Boolean {
        return withContext(Dispatchers.IO) {
            // Step 1: Quick validation and ID check (inside synchronized)
            val isValid = synchronized(lock) {
                if (!enrollment.isValid()) {
                    Log.w("EnrollmentRepository", "Invalid enrollment: ${enrollment.id}")
                    return@withContext false
                }
                
                // Check for unique ID
                if (enrollments.any { it.id == enrollment.id }) {
                    Log.w("EnrollmentRepository", "Enrollment with ID '${enrollment.id}' already exists. Each ID must be unique.")
                    return@withContext false
                }
                true
            }
            
            if (!isValid) return@withContext false
            
            // Step 2: Copy images OUTSIDE synchronized block (this is a suspend operation)
            val copiedFileNames = mutableListOf<String>()
            enrollment.imageUris.forEachIndexed { index, uri ->
                val fileName = copyImageToStorage(uri, enrollment.id, index)
                if (fileName != null) {
                    copiedFileNames.add(fileName)
                }
            }
            
            if (copiedFileNames.isEmpty()) {
                Log.e("EnrollmentRepository", "Failed to copy any images for enrollment: ${enrollment.id}")
                return@withContext false
            }
            
            // Step 3: Add to list and save (inside synchronized again)
            synchronized(lock) {
                // Double-check ID is still unique (in case another coroutine added it)
                if (enrollments.any { it.id == enrollment.id }) {
                    Log.w("EnrollmentRepository", "Enrollment with ID '${enrollment.id}' was added by another process")
                    // Clean up copied files
                    val enrollmentsDir = this@EnrollmentRepository.enrollmentsDir
                    copiedFileNames.forEach { fileName ->
                        try {
                            val file = File(enrollmentsDir, fileName)
                            if (file.exists()) file.delete()
                        } catch (e: Exception) {
                            Log.e("EnrollmentRepository", "Error cleaning up file", e)
                        }
                    }
                    return@withContext false
                }
                
                // Create URIs for copied files
                val enrollmentsDir = this@EnrollmentRepository.enrollmentsDir ?: return@withContext false
                val fileUris = copiedFileNames.map { fileName ->
                    val file = File(enrollmentsDir, fileName)
                    android.net.Uri.fromFile(file)
                }
                
                // Create enrollment with file URIs
                val persistentEnrollment = FingerprintEnrollment(
                    id = enrollment.id,
                    name = enrollment.name,
                    imageUris = fileUris,
                    enrolledDate = enrollment.enrolledDate
                )
                
                enrollments.add(persistentEnrollment)
                saveEnrollments()
                
                Log.d("EnrollmentRepository", "Added enrollment: ${enrollment.id} with ${fileUris.size} images")
                true
            }
        }
    }

    /**
     * Removes an enrollment by ID.
     * Deletes associated image files and updates storage.
     * 
     * @param id The ID of the enrollment to remove
     * @return true if removed, false if not found
     */
    fun removeEnrollment(id: String): Boolean {
        synchronized(lock) {
            val enrollment = enrollments.find { it.id == id } ?: return false
            
            // Delete associated image files
            val enrollmentsDir = this.enrollmentsDir
            enrollment.imageUris.forEach { uri ->
                try {
                    val file = File(uri.path ?: "")
                    if (file.exists() && enrollmentsDir?.let { file.parentFile == it } == true) {
                        if (file.delete()) {
                            Log.d("EnrollmentRepository", "Deleted image file: ${file.name}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EnrollmentRepository", "Error deleting image file", e)
                }
            }
            
            val removed = enrollments.removeAll { it.id == id }
            if (removed) {
                saveEnrollments()
                Log.d("EnrollmentRepository", "Removed enrollment: $id")
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
     * Deletes all image files and clears storage.
     */
    fun clearAll() {
        synchronized(lock) {
            // Delete all image files
            val enrollmentsDir = this.enrollmentsDir
            enrollments.forEach { enrollment ->
                enrollment.imageUris.forEach { uri ->
                    try {
                        val file = File(uri.path ?: "")
                        if (file.exists() && enrollmentsDir?.let { file.parentFile == it } == true) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        Log.e("EnrollmentRepository", "Error deleting file", e)
                    }
                }
            }
            
            enrollments.clear()
            prefs?.edit()?.clear()?.apply()
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
    
    /**
     * Data class for JSON serialization.
     * Stores file paths instead of URIs for persistence.
     */
    private data class EnrollmentData(
        val id: String,
        val name: String,
        val imageFilePaths: List<String>,
        val enrolledDate: Long
    )
}
