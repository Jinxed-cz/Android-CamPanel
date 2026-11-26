package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import com.example.model.CameraSlot
import com.example.model.RecordedSegmentInfo
import com.example.model.RecordingQuality
import com.example.model.RecordingRetentionPolicy
import com.example.model.RecordingStorageLocation
import com.example.model.RecordingStorageSettings
import com.example.model.RecordingStorageStats
import java.io.File
import java.io.FileInputStream

/**
 * Manages MP4 recording directories across app-internal storage, external storage, SD card,
 * and zero-permission Storage Access Framework (SAF) user-selected folders, plus ring-buffer
 * quota & retention pruning.
 */
class CameraRecordingStorageManager(private val context: Context) {

  companion object {
    private const val RECORDINGS_FOLDER_NAME = "CamPanelRecordings"
    private const val STAGING_FOLDER_NAME = "CamPanelStaging"
  }

  fun hasSdCard(): Boolean {
    val dirs = context.getExternalFilesDirs(Environment.DIRECTORY_MOVIES)
    return dirs.size > 1 && dirs[1] != null
  }

  fun resolveLocalDirectory(location: RecordingStorageLocation): File {
    val externalDirs = context.getExternalFilesDirs(Environment.DIRECTORY_MOVIES)
    val baseDir =
      when (location) {
        RecordingStorageLocation.APP_INTERNAL -> File(context.filesDir, RECORDINGS_FOLDER_NAME)
        RecordingStorageLocation.APP_EXTERNAL_PRIMARY ->
          externalDirs.firstOrNull()?.let { File(it, RECORDINGS_FOLDER_NAME) }
            ?: File(context.filesDir, RECORDINGS_FOLDER_NAME)
        RecordingStorageLocation.SD_CARD_APP ->
          externalDirs.getOrNull(1)?.let { File(it, RECORDINGS_FOLDER_NAME) }
            ?: externalDirs.firstOrNull()?.let { File(it, RECORDINGS_FOLDER_NAME) }
            ?: File(context.filesDir, RECORDINGS_FOLDER_NAME)
        RecordingStorageLocation.CUSTOM_SAF_TREE,
        RecordingStorageLocation.UNSET ->
          File(context.cacheDir, STAGING_FOLDER_NAME)
      }
    if (!baseDir.exists()) {
      baseDir.mkdirs()
    }
    return baseDir
  }

  fun stagingDirectory(): File {
    val dir = File(context.cacheDir, STAGING_FOLDER_NAME)
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun prepareSegmentTargetFile(
    storageSettings: RecordingStorageSettings,
    slotIndex: Int,
    timestampMs: Long,
    quality: RecordingQuality,
    isMotionEvent: Boolean = false,
  ): File {
    val fileName =
      RecordingRetentionPolicy.buildSegmentFileName(
        slotIndex = slotIndex,
        timestampMs = timestampMs,
        quality = quality,
        isMotionEvent = isMotionEvent,
      )
    val dir =
      if (storageSettings.isFolderConfigured) {
        stagingDirectory()
      } else {
        resolveLocalDirectory(storageSettings.locationType)
      }
    return File(dir, fileName)
  }

  fun persistSafPermission(treeUri: Uri) {
    runCatching {
      val flags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      context.contentResolver.takePersistableUriPermission(treeUri, flags)
    }
  }

  fun resolveDisplayPathLabel(storageSettings: RecordingStorageSettings): String {
    if (!storageSettings.isFolderConfigured) return ""
    return storageSettings.customFolderLabel.ifBlank {
      storageSettings.customTreeUri
    }
  }

  /**
   * Moves a completed local staging MP4 file into the selected SAF tree folder when
   * [RecordingStorageLocation.CUSTOM_SAF_TREE] is active.
   */
  fun finalizeSegmentFile(
    localFile: File,
    storageSettings: RecordingStorageSettings,
  ): Boolean {
    if (!localFile.exists() || localFile.length() < 1024L) {
      runCatching { localFile.delete() }
      return false
    }
    if (!storageSettings.isFolderConfigured) {
      runCatching { localFile.delete() }
      return false
    }

    val treeUri = runCatching { Uri.parse(storageSettings.customTreeUri) }.getOrNull() ?: return false
    return runCatching {
      val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
      val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
      val createdUri =
        DocumentsContract.createDocument(
          context.contentResolver,
          parentDocUri,
          "video/mp4",
          localFile.name,
        ) ?: return false

      context.contentResolver.openOutputStream(createdUri)?.use { out ->
        FileInputStream(localFile).use { input ->
          val buffer = ByteArray(64 * 1024)
          while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
          }
          out.flush()
        }
      } ?: return false

      localFile.delete()
      true
    }.getOrElse {
      // Fallback: move into internal recordings so recording is never silently lost
      val fallbackDir = resolveLocalDirectory(RecordingStorageLocation.APP_INTERNAL)
      val fallbackFile = File(fallbackDir, localFile.name)
      runCatching { localFile.copyTo(fallbackFile, overwrite = true) }
      runCatching { localFile.delete() }
      fallbackFile.exists()
    }
  }

  fun listSegments(
    storageSettings: RecordingStorageSettings,
    activeFileNames: Set<String> = emptySet(),
  ): List<RecordedSegmentInfo> {
    if (!storageSettings.isFolderConfigured) {
      return emptyList()
    }
    val result = mutableListOf<RecordedSegmentInfo>()

    // 1. Show currently recording staging files
    stagingDirectory()
      .listFiles { f -> f.isFile && f.name.endsWith(".mp4", ignoreCase = true) && f.name in activeFileNames }
      ?.forEach { file ->
        val slotIdx = RecordingRetentionPolicy.parseSlotIndexFromFileName(file.name) ?: 0
        val quality = RecordingRetentionPolicy.parseQualityFromFileName(file.name)
        result +=
          RecordedSegmentInfo(
            id = "staging_${file.absolutePath}",
            slotIndex = slotIdx,
            fileName = file.name,
            uriString = Uri.fromFile(file).toString(),
            filePath = file.absolutePath,
            sizeBytes = file.length(),
            createdAtMs = file.lastModified(),
            qualityId = quality.id,
            isCustomSaf = false,
            isCurrentlyRecording = true,
          )
      }

    // 2. Scan selected SAF directory children
    val treeUri = runCatching { Uri.parse(storageSettings.customTreeUri) }.getOrNull()
    if (treeUri != null) {
        runCatching {
          val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
              treeUri,
              DocumentsContract.getTreeDocumentId(treeUri),
            )
          val projection =
            arrayOf(
              DocumentsContract.Document.COLUMN_DOCUMENT_ID,
              DocumentsContract.Document.COLUMN_DISPLAY_NAME,
              DocumentsContract.Document.COLUMN_SIZE,
              DocumentsContract.Document.COLUMN_LAST_MODIFIED,
              DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
          context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
              val docId = if (idCol >= 0) cursor.getString(idCol) else continue
              val name = if (nameCol >= 0) cursor.getString(nameCol) else continue
              if (!name.endsWith(".mp4", ignoreCase = true)) continue
              val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
              val modified = if (modCol >= 0) cursor.getLong(modCol) else System.currentTimeMillis()
              val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
              val slotIdx = RecordingRetentionPolicy.parseSlotIndexFromFileName(name) ?: 0
              val quality = RecordingRetentionPolicy.parseQualityFromFileName(name)
              result +=
                RecordedSegmentInfo(
                  id = "saf_$docId",
                  slotIndex = slotIdx,
                  fileName = name,
                  uriString = docUri.toString(),
                  filePath = null,
                  sizeBytes = size,
                  createdAtMs = modified,
                  qualityId = quality.id,
                  isCustomSaf = true,
                  isCurrentlyRecording = name in activeFileNames,
                )
            }
        }
      }
    }

    return result.sortedByDescending { it.createdAtMs }
  }

  fun deleteSegment(segment: RecordedSegmentInfo): Boolean {
    return if (segment.isCustomSaf) {
      runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(segment.uriString))
      }.getOrDefault(false)
    } else {
      val path = segment.filePath ?: return false
      runCatching { File(path).delete() }.getOrDefault(false)
    }
  }

  fun enforceRingBufferForCamera(
    slot: CameraSlot,
    storageSettings: RecordingStorageSettings,
    activeFileNames: Set<String>,
    reserveNextSegmentBytes: Long = 0L,
  ) {
    val allSegments = listSegments(storageSettings, activeFileNames)
    val cameraSegments = allSegments.filter { it.slotIndex == slot.slotIndex }
    val stats = queryStorageStats(storageSettings, allSegments)
    val maxBytes = slot.recordingMaxSizeMb.toLong() * 1024L * 1024L
    val toDelete =
      RecordingRetentionPolicy.planFilesToDeleteForRingBuffer(
        segments = cameraSegments,
        maxStorageBytes = maxBytes,
        retentionDays = slot.recordingRetentionDays,
        nowMs = System.currentTimeMillis(),
        reserveForNextSegmentBytes = reserveNextSegmentBytes,
        availableFreeBytes = stats.availableFreeBytes,
      )
    toDelete.forEach { deleteSegment(it) }
  }

  fun queryStorageStats(
    storageSettings: RecordingStorageSettings,
    cachedSegments: List<RecordedSegmentInfo>? = null,
  ): RecordingStorageStats {
    val segments = cachedSegments ?: listSegments(storageSettings)
    val usedBytes = segments.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    val statDir =
      when (storageSettings.locationType) {
        RecordingStorageLocation.SD_CARD_APP -> resolveLocalDirectory(RecordingStorageLocation.SD_CARD_APP)
        RecordingStorageLocation.APP_EXTERNAL_PRIMARY -> resolveLocalDirectory(RecordingStorageLocation.APP_EXTERNAL_PRIMARY)
        else -> resolveLocalDirectory(RecordingStorageLocation.APP_EXTERNAL_PRIMARY)
      }
    var freeBytes = 0L
    var totalBytes = 0L
    runCatching {
      val stat = StatFs(statDir.absolutePath)
      freeBytes = stat.availableBytes
      totalBytes = stat.totalBytes
    }
    return RecordingStorageStats(
      usedByAppBytes = usedBytes,
      availableFreeBytes = freeBytes,
      totalDeviceBytes = totalBytes,
      resolvedPathLabel = resolveDisplayPathLabel(storageSettings),
      hasSdCard = hasSdCard(),
    )
  }
}
