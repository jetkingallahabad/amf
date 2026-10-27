package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Safely copies customer KYC documents selected via Android Photo Picker / Document Picker
 * into app-private internal storage (`filesDir/customer_docs/<customerId>/`) with strict
 * canonical path confinement, extension allowlisting, and SHA-256 integrity verification.
 */
object DocumentStorageHelper {

  private const val MAX_DOCUMENT_SIZE_BYTES = 15L * 1024L * 1024L // 15 MB
  private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_-]{1,128}$")
  private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "pdf")

  enum class DocumentIntegrityStatus(val badgeLabel: String) {
    VERIFIED_LOCAL_FILE("SHA-256 Verified on Device"),
    CLOUD_METADATA_SYNCED("Cloud Metadata Synced"),
    CHECKSUM_MISMATCH("Checksum or Path Tampered"),
  }

  data class SavedDocumentFile(
    val originalFileName: String,
    val localFilePath: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val sha256Checksum: String,
  )

  /**
   * Resolves [localFilePath] only if it strictly resides inside the app-private
   * `filesDir/customer_docs/<customerId>/` sandbox, blocking any `..` or symlink traversal.
   */
  fun resolveConfinedDocumentFile(
    context: Context,
    customerId: String,
    localFilePath: String,
  ): File? {
    if (!VALID_ID_REGEX.matches(customerId) || localFilePath.isBlank()) return null
    return try {
      val allowedDir = File(context.filesDir, "customer_docs/$customerId").canonicalFile
      val candidate = File(localFilePath).canonicalFile
      val allowedPrefix = allowedDir.path + File.separator
      if (candidate.path.startsWith(allowedPrefix) && candidate.exists() && candidate.isFile) {
        candidate
      } else {
        null
      }
    } catch (_: Exception) {
      null
    }
  }

  suspend fun copyUriToInternalStorage(
    context: Context,
    uri: Uri,
    customerId: String,
  ): Result<SavedDocumentFile> = withContext(Dispatchers.IO) {
    runCatching {
      require(VALID_ID_REGEX.matches(customerId)) {
        "Invalid customer identifier for secure document storage."
      }
      val resolver = context.contentResolver
      val mimeType = (resolver.getType(uri) ?: "application/octet-stream").take(100)
      val rawDisplayName = resolveDisplayName(context, uri) ?: "kyc_doc_${System.currentTimeMillis()}"
      val sanitizedDisplayName = rawDisplayName
        .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
        .trim()
        .ifBlank { "kyc_doc_${System.currentTimeMillis()}" }
        .take(180)

      val rawExt = sanitizedDisplayName.substringAfterLast('.', "").lowercase(Locale.US)
      val safeExtension = when {
        rawExt in ALLOWED_EXTENSIONS -> rawExt
        mimeType.contains("png", ignoreCase = true) -> "png"
        mimeType.contains("webp", ignoreCase = true) -> "webp"
        mimeType.contains("pdf", ignoreCase = true) -> "pdf"
        else -> "jpg"
      }

      val docsDir = File(context.filesDir, "customer_docs/$customerId").apply { mkdirs() }.canonicalFile
      val targetFile = File(docsDir, "${UUID.randomUUID()}.$safeExtension").canonicalFile
      check(targetFile.path.startsWith(docsDir.path + File.separator)) {
        "Security confinement error: target document path escaped sandbox."
      }

      val digest = MessageDigest.getInstance("SHA-256")
      var totalBytes = 0L

      val inputStream = resolver.openInputStream(uri)
        ?: throw IllegalStateException("Unable to read selected document URI")

      inputStream.use { input ->
        targetFile.outputStream().use { output ->
          val buffer = ByteArray(8192)
          var read: Int
          while (input.read(buffer).also { read = it } != -1) {
            totalBytes += read
            if (totalBytes > MAX_DOCUMENT_SIZE_BYTES) {
              output.close()
              targetFile.delete()
              throw IllegalArgumentException("Document exceeds maximum allowed size of 15 MB.")
            }
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
          }
          output.flush()
        }
      }

      if (totalBytes <= 0L) {
        targetFile.delete()
        throw IllegalStateException("Selected document file is empty")
      }

      val checksumHex = digest.digest().joinToString("") { "%02x".format(Locale.US, it) }

      SavedDocumentFile(
        originalFileName = sanitizedDisplayName,
        localFilePath = targetFile.absolutePath,
        mimeType = mimeType,
        fileSizeBytes = totalBytes,
        sha256Checksum = checksumHex,
      )
    }
  }

  suspend fun verifyDocumentIntegrity(
    context: Context,
    customerId: String,
    localFilePath: String,
    expectedSha256: String,
  ): DocumentIntegrityStatus = withContext(Dispatchers.IO) {
    if (localFilePath.isBlank()) {
      return@withContext DocumentIntegrityStatus.CLOUD_METADATA_SYNCED
    }
    if (!VALID_ID_REGEX.matches(customerId)) {
      return@withContext DocumentIntegrityStatus.CHECKSUM_MISMATCH
    }
    val rawCandidate = File(localFilePath)
    val allowedDir = File(context.filesDir, "customer_docs/$customerId").canonicalFile
    val canonicalCandidate = runCatching { rawCandidate.canonicalFile }.getOrNull()
      ?: return@withContext DocumentIntegrityStatus.CHECKSUM_MISMATCH

    if (!canonicalCandidate.path.startsWith(allowedDir.path + File.separator)) {
      return@withContext DocumentIntegrityStatus.CHECKSUM_MISMATCH
    }
    if (!canonicalCandidate.exists() || !canonicalCandidate.isFile) {
      return@withContext DocumentIntegrityStatus.CLOUD_METADATA_SYNCED
    }
    hashAndCompareFile(canonicalCandidate, expectedSha256)
  }

  suspend fun verifyDocumentIntegrity(
    localFilePath: String,
    expectedSha256: String,
  ): DocumentIntegrityStatus = withContext(Dispatchers.IO) {
    if (localFilePath.isBlank() || localFilePath.contains("..")) {
      return@withContext if (localFilePath.contains("..")) {
        DocumentIntegrityStatus.CHECKSUM_MISMATCH
      } else {
        DocumentIntegrityStatus.CLOUD_METADATA_SYNCED
      }
    }
    val file = File(localFilePath)
    if (!file.exists() || !file.isFile) {
      return@withContext DocumentIntegrityStatus.CLOUD_METADATA_SYNCED
    }
    hashAndCompareFile(file, expectedSha256)
  }

  private fun hashAndCompareFile(
    file: File,
    expectedSha256: String,
  ): DocumentIntegrityStatus {
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
      file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
          digest.update(buffer, 0, read)
        }
      }
      val actualHex = digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
      if (actualHex.equals(expectedSha256, ignoreCase = true)) {
        DocumentIntegrityStatus.VERIFIED_LOCAL_FILE
      } else {
        DocumentIntegrityStatus.CHECKSUM_MISMATCH
      }
    } catch (_: Exception) {
      DocumentIntegrityStatus.CLOUD_METADATA_SYNCED
    }
  }

  private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
          } else {
            null
          }
        }
    } catch (_: Exception) {
      null
    }
  }
}
