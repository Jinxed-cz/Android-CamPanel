package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.example.model.CameraRecordingRuntimeStatus
import com.example.model.CameraSlot
import com.example.model.MotionDetectionMath
import com.example.model.RecordedSegmentInfo
import com.example.model.RecordingMode
import com.example.model.RecordingQuality
import com.example.model.RecordingRetentionPolicy
import com.example.model.RecordingStorageSettings
import com.example.model.RecordingStorageStats
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Process-wide coordinator for continuous 15-minute and motion-triggered MP4 camera recording
 * with per-camera quality (SD/HD), size limit ring-buffer overwrite, and day-based retention pruning.
 */
class CameraRecordingCoordinator private constructor(private val appContext: Context) {

  companion object {
    private const val LUMA_GRID_W = 64
    private const val LUMA_GRID_H = 36
    private const val MOTION_SAMPLE_INTERVAL_MS = 450L

    @Volatile private var instance: CameraRecordingCoordinator? = null

    fun getInstance(context: Context): CameraRecordingCoordinator {
      return instance ?: synchronized(this) {
        instance ?: CameraRecordingCoordinator(context.applicationContext).also { instance = it }
      }
    }
  }

  val storageManager = CameraRecordingStorageManager(appContext)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .build()

  private val _statuses = MutableStateFlow<Map<Int, CameraRecordingRuntimeStatus>>(emptyMap())
  val statuses: StateFlow<Map<Int, CameraRecordingRuntimeStatus>> = _statuses.asStateFlow()

  private val _segments = MutableStateFlow<List<RecordedSegmentInfo>>(emptyList())
  val segments: StateFlow<List<RecordedSegmentInfo>> = _segments.asStateFlow()

  private val _storageStats = MutableStateFlow(RecordingStorageStats())
  val storageStats: StateFlow<RecordingStorageStats> = _storageStats.asStateFlow()

  private val slotJobs = mutableMapOf<Int, Job>()
  private val activeSignatures = mutableMapOf<Int, String>()
  private val activeFileNames = mutableMapOf<Int, String>()
  private var latestStorageSettings = RecordingStorageSettings()
  private var latestSlots: List<CameraSlot> = emptyList()

  @Synchronized
  fun syncConfiguration(
    slots: List<CameraSlot>,
    storageSettings: RecordingStorageSettings,
  ) {
    val storageChanged = storageSettings != latestStorageSettings
    latestSlots = slots
    latestStorageSettings = storageSettings

    val activeSlots =
      if (storageSettings.isFolderConfigured) {
        slots.filter { it.isConfigured && it.recordingEnabled }
      } else {
        emptyList()
      }
    val activeSlotIndices = activeSlots.map { it.slotIndex }.toSet()

    // Stop workers for disabled, cleared, or unconfigured-storage slots
    slotJobs.keys.toList().forEach { idx ->
      if (idx !in activeSlotIndices) {
        slotJobs.remove(idx)?.cancel()
        activeSignatures.remove(idx)
        activeFileNames.remove(idx)
        _statuses.update { map -> map - idx }
      }
    }

    if (!storageSettings.isFolderConfigured) {
      slots
        .filter { it.isConfigured && it.recordingEnabled }
        .forEach { slot ->
          _statuses.update { map ->
            map + (
              slot.slotIndex to
                CameraRecordingRuntimeStatus(
                  slotIndex = slot.slotIndex,
                  isRecording = false,
                  lastError = "Vyberte složku pro ukládání záznamů",
                )
            )
          }
        }
    }

    // Start or restart workers whose config or storage location changed
    activeSlots.forEach { slot ->
      val signature =
        "${slot.effectivePlaybackUrl()}|${slot.recordingQualityId}|${slot.recordingMaxSizeMb}|" +
          "${slot.recordingRetentionDays}|${slot.recordingModeId}|${slot.motionSensitivity}|" +
          "${slot.motionEventDurationMinutes}|${storageSettings.locationTypeId}|${storageSettings.customTreeUri}"
      if (storageChanged || activeSignatures[slot.slotIndex] != signature || slotJobs[slot.slotIndex]?.isActive != true) {
        slotJobs.remove(slot.slotIndex)?.cancel()
        activeSignatures[slot.slotIndex] = signature
        slotJobs[slot.slotIndex] = scope.launch { runCameraRecordingLoop(slot) }
      }
    }

    syncForegroundService(activeSlots.size)
    scope.launch { refreshSegmentsAndStats() }
  }

  fun refreshNow() {
    scope.launch { refreshSegmentsAndStats() }
  }

  fun deleteSegment(segment: RecordedSegmentInfo) {
    scope.launch {
      storageManager.deleteSegment(segment)
      refreshSegmentsAndStats()
    }
  }

  fun clearAllSegmentsForCamera(slotIndex: Int) {
    scope.launch {
      val currentActive = activeFileNames.values.toSet()
      storageManager
        .listSegments(latestStorageSettings, currentActive)
        .filter { it.slotIndex == slotIndex && !it.isCurrentlyRecording }
        .forEach { storageManager.deleteSegment(it) }
      refreshSegmentsAndStats()
    }
  }

  private fun syncForegroundService(activeCount: Int) {
    val intent =
      Intent(appContext, CameraRecordingService::class.java).apply {
        putExtra(CameraRecordingService.EXTRA_ACTIVE_CAMERA_COUNT, activeCount)
      }
    runCatching {
      if (activeCount > 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          appContext.startForegroundService(intent)
        } else {
          appContext.startService(intent)
        }
      } else {
        appContext.stopService(intent)
      }
    }
  }

  private suspend fun runCameraRecordingLoop(slot: CameraSlot) {
    val quality = slot.recordingQuality
    val streamUrl = RecordingRetentionPolicy.resolveStreamUrlForQuality(slot, quality)
    if (streamUrl.isBlank()) return

    when (slot.recordingMode) {
      RecordingMode.CONTINUOUS -> runContinuousRecordingLoop(slot, streamUrl, quality)
      RecordingMode.MOTION_EVENTS -> runMotionEventRecordingLoop(slot, streamUrl, quality)
    }
  }

  private suspend fun runContinuousRecordingLoop(
    slot: CameraSlot,
    streamUrl: String,
    quality: RecordingQuality,
  ) {
    while (scope.isActive) {
      val nowMs = System.currentTimeMillis()
      val storage = latestStorageSettings
      val targetFile =
        storageManager.prepareSegmentTargetFile(
          storageSettings = storage,
          slotIndex = slot.slotIndex,
          timestampMs = nowMs,
          quality = quality,
          isMotionEvent = false,
        )

      synchronized(this) {
        activeFileNames[slot.slotIndex] = targetFile.name
      }

      // Enforce ring-buffer & day retention BEFORE starting the next 15-minute segment
      val activeNamesSnapshot = synchronized(this) { activeFileNames.values.toSet() }
      val reserveBytes = quality.estimatedMbPer15Min.toLong() * 1024L * 1024L
      storageManager.enforceRingBufferForCamera(
        slot = slot,
        storageSettings = storage,
        activeFileNames = activeNamesSnapshot,
        reserveNextSegmentBytes = reserveBytes,
      )

      _statuses.update { map ->
        val prev = map[slot.slotIndex] ?: CameraRecordingRuntimeStatus(slotIndex = slot.slotIndex)
        map +
          (slot.slotIndex to
            prev.copy(
              isRecording = true,
              isMotionArmed = false,
              isMotionTriggered = false,
              activeFileName = targetFile.name,
              activeSegmentStartedMs = nowMs,
              lastError = null,
            ))
      }
      refreshSegmentsAndStats()

      var segmentError: String? = null
      try {
        recordSingleSegment(
          slot = slot,
          streamUrl = streamUrl,
          quality = quality,
          targetFile = targetFile,
          storageSettings = storage,
          durationMs = RecordingRetentionPolicy.SEGMENT_DURATION_MS,
        )
      } catch (cancel: CancellationException) {
        storageManager.finalizeSegmentFile(targetFile, storage)
        throw cancel
      } catch (t: Throwable) {
        segmentError = t.localizedMessage ?: "Recording error"
      }

      storageManager.finalizeSegmentFile(targetFile, storage)
      synchronized(this) {
        activeFileNames.remove(slot.slotIndex)
      }

      // Enforce retention again after finalizing segment so size stays strictly within cap
      val activeAfter = synchronized(this) { activeFileNames.values.toSet() }
      storageManager.enforceRingBufferForCamera(
        slot = slot,
        storageSettings = storage,
        activeFileNames = activeAfter,
      )

      _statuses.update { map ->
        val prev = map[slot.slotIndex] ?: CameraRecordingRuntimeStatus(slotIndex = slot.slotIndex)
        map +
          (slot.slotIndex to
            prev.copy(
              isRecording = false,
              isMotionArmed = false,
              isMotionTriggered = false,
              activeFileName = null,
              lastError = segmentError,
            ))
      }
      refreshSegmentsAndStats()

      if (segmentError != null) {
        delay(4_000L)
      } else {
        delay(400L)
      }
    }
  }

  private suspend fun runMotionEventRecordingLoop(
    slot: CameraSlot,
    recordStreamUrl: String,
    quality: RecordingQuality,
  ) {
    val isHttp =
      recordStreamUrl.startsWith("http://", ignoreCase = true) ||
        recordStreamUrl.startsWith("https://", ignoreCase = true)
    val isHttpVideo = CameraSlot.isHttpVideoOrMjpegUrl(recordStreamUrl)
    val isMjpeg =
      isHttpVideo &&
        (recordStreamUrl.contains("/video", ignoreCase = true) ||
          recordStreamUrl.contains("mjpeg", ignoreCase = true) ||
          recordStreamUrl.contains(".mjpg", ignoreCase = true) ||
          recordStreamUrl.contains("videostream", ignoreCase = true) ||
          recordStreamUrl.contains("action=stream", ignoreCase = true))

    when {
      isHttp && !isHttpVideo ->
        runHttpSnapshotMotionLoop(slot, recordStreamUrl, quality)
      isMjpeg ->
        runHttpMjpegMotionLoop(slot, recordStreamUrl, quality)
      else ->
        runRtspMotionLoop(slot, recordStreamUrl, quality)
    }
  }

  private suspend fun runHttpSnapshotMotionLoop(
    slot: CameraSlot,
    url: String,
    quality: RecordingQuality,
  ) {
    val authHeader =
      if (slot.username.isNotBlank()) Credentials.basic(slot.username, slot.password) else null
    val sensitivity = RecordingRetentionPolicy.normalizeMotionSensitivity(slot.motionSensitivity)
    val eventDurationMs =
      RecordingRetentionPolicy.normalizeMotionDurationMinutes(slot.motionEventDurationMinutes) * 60_000L

    var previousLuma: ByteArray? = null
    var recorder: BitmapToMp4Recorder? = null
    var activeFile: File? = null
    var chunkStartedMs = 0L
    var recordingUntilMs = 0L
    var lastMaintenanceMs = System.currentTimeMillis()
    var consecutiveErrors = 0

    updateMotionStatus(slot.slotIndex, armed = true, recording = false, fileName = null, error = null)

    try {
      while (scope.isActive) {
        val sep = if (url.contains("?")) "&" else "?"
        val reqUrl = "$url${sep}_mot=${System.currentTimeMillis()}"
        val request =
          Request.Builder()
            .url(reqUrl)
            .apply {
              if (authHeader != null) header("Authorization", authHeader)
              header("Cache-Control", "no-cache")
            }
            .build()

        val bitmap =
          runCatching {
            httpClient.newCall(request).execute().use { resp ->
              if (!resp.isSuccessful) return@use null
              val bytes = resp.body?.bytes() ?: return@use null
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
          }.getOrNull()

        val now = System.currentTimeMillis()
        if (bitmap == null) {
          consecutiveErrors++
          if (consecutiveErrors >= 12) {
            updateMotionStatus(
              slotIndex = slot.slotIndex,
              armed = true,
              recording = recorder != null,
              fileName = activeFile?.name,
              error = "HTTP snapshot unreachable",
            )
          }
          delay(750L)
          continue
        }

        consecutiveErrors = 0
        try {
          val curLuma = bitmapToLumaGrid(bitmap)
          val prev = previousLuma
          previousLuma = curLuma
          val hasMotion = prev != null && MotionDetectionMath.isMotionDetected(prev, curLuma, sensitivity)
          if (hasMotion) {
            recordingUntilMs = now + eventDurationMs
          }

          val shouldRollChunk =
            recorder != null && (now - chunkStartedMs >= RecordingRetentionPolicy.SEGMENT_DURATION_MS)
          if (recorder != null && (now >= recordingUntilMs || shouldRollChunk)) {
            finalizeActiveMotionChunk(slot, recorder, activeFile)
            recorder = null
            activeFile = null
            if (now >= recordingUntilMs) {
              updateMotionStatus(slot.slotIndex, armed = true, recording = false, fileName = null, error = null)
            }
          }

          if (now < recordingUntilMs) {
            if (recorder == null) {
              val prepared = beginMotionChunk(slot, quality)
              activeFile = prepared
              chunkStartedMs = now
              recorder =
                BitmapToMp4Recorder(
                  outputFile = prepared,
                  width = quality.targetWidth,
                  height = quality.targetHeight,
                  bitrateBps = quality.targetBitrateBps,
                  fps = 2,
                )
              updateMotionStatus(
                slotIndex = slot.slotIndex,
                armed = true,
                recording = true,
                fileName = prepared.name,
                startedMs = now,
                error = null,
              )
            }
            recorder?.encodeFrame(bitmap)
          }
        } finally {
          bitmap.recycle()
        }

        if (now - lastMaintenanceMs >= 60_000L && activeFile != null) {
          lastMaintenanceMs = now
          val activeNames = synchronized(this) { activeFileNames.values.toSet() }
          storageManager.enforceRingBufferForCamera(slot, latestStorageSettings, activeNames)
          refreshSegmentsAndStats()
        }

        delay(MOTION_SAMPLE_INTERVAL_MS)
      }
    } finally {
      finalizeActiveMotionChunk(slot, recorder, activeFile)
      updateMotionStatus(slot.slotIndex, armed = false, recording = false, fileName = null, error = null)
    }
  }

  private suspend fun runHttpMjpegMotionLoop(
    slot: CameraSlot,
    url: String,
    quality: RecordingQuality,
  ) {
    val authHeader =
      if (slot.username.isNotBlank()) Credentials.basic(slot.username, slot.password) else null
    val sensitivity = RecordingRetentionPolicy.normalizeMotionSensitivity(slot.motionSensitivity)
    val eventDurationMs =
      RecordingRetentionPolicy.normalizeMotionDurationMinutes(slot.motionEventDurationMinutes) * 60_000L
    val recordFrameIntervalMs = (1000L / quality.targetFps.coerceAtLeast(1)).coerceAtLeast(50L)

    while (scope.isActive) {
      updateMotionStatus(slot.slotIndex, armed = true, recording = false, fileName = null, error = null)
      var previousLuma: ByteArray? = null
      var recorder: BitmapToMp4Recorder? = null
      var activeFile: File? = null
      var chunkStartedMs = 0L
      var recordingUntilMs = 0L
      var lastSampleMs = 0L
      var lastEncodedMs = 0L
      var lastMaintenanceMs = System.currentTimeMillis()

      val loopError =
        try {
          val req =
            Request.Builder()
              .url(url)
              .apply {
                if (authHeader != null) header("Authorization", authHeader)
              }
              .build()

          httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
              throw IllegalStateException("HTTP ${resp.code}")
            }
            val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty MJPEG stream")
            val readBuf = ByteArray(16 * 1024)
            val frameOut = ByteArrayOutputStream(64 * 1024)
            var inFrame = false
            var prevByte = -1

            while (scope.isActive) {
              val read = stream.read(readBuf)
              if (read <= 0) break

              for (i in 0 until read) {
                val cur = readBuf[i].toInt() and 0xFF
                if (!inFrame) {
                  if (prevByte == 0xFF && cur == 0xD8) {
                    frameOut.reset()
                    frameOut.write(0xFF)
                    frameOut.write(0xD8)
                    inFrame = true
                  }
                } else {
                  frameOut.write(cur)
                  if (prevByte == 0xFF && cur == 0xD9) {
                    inFrame = false
                    val now = System.currentTimeMillis()
                    val currentlyRecording = recorder != null && now < recordingUntilMs
                    val needMotionCheck = now - lastSampleMs >= MOTION_SAMPLE_INTERVAL_MS
                    val needEncodeFrame =
                      currentlyRecording && (now - lastEncodedMs >= recordFrameIntervalMs)

                    if (needMotionCheck || needEncodeFrame) {
                      val jpegBytes = frameOut.toByteArray()
                      val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                      if (bmp != null) {
                        try {
                          if (needMotionCheck) {
                            lastSampleMs = now
                            val curLuma = bitmapToLumaGrid(bmp)
                            val prev = previousLuma
                            previousLuma = curLuma
                            if (prev != null &&
                              MotionDetectionMath.isMotionDetected(prev, curLuma, sensitivity)
                            ) {
                              recordingUntilMs = now + eventDurationMs
                            }
                          }

                          val shouldRollChunk =
                            recorder != null && (now - chunkStartedMs >= RecordingRetentionPolicy.SEGMENT_DURATION_MS)
                          if (recorder != null && (now >= recordingUntilMs || shouldRollChunk)) {
                            finalizeActiveMotionChunk(slot, recorder, activeFile)
                            recorder = null
                            activeFile = null
                            if (now >= recordingUntilMs) {
                              updateMotionStatus(slot.slotIndex, armed = true, recording = false, fileName = null, error = null)
                            }
                          }

                          if (now < recordingUntilMs) {
                            if (recorder == null) {
                              val prepared = beginMotionChunk(slot, quality)
                              activeFile = prepared
                              chunkStartedMs = now
                              recorder =
                                BitmapToMp4Recorder(
                                  outputFile = prepared,
                                  width = quality.targetWidth,
                                  height = quality.targetHeight,
                                  bitrateBps = quality.targetBitrateBps,
                                  fps = quality.targetFps,
                                )
                              updateMotionStatus(
                                slotIndex = slot.slotIndex,
                                armed = true,
                                recording = true,
                                fileName = prepared.name,
                                startedMs = now,
                                error = null,
                              )
                            }
                            if (now - lastEncodedMs >= recordFrameIntervalMs) {
                              lastEncodedMs = now
                              recorder?.encodeFrame(bmp)
                            }
                          }
                        } finally {
                          bmp.recycle()
                        }
                      }
                    }

                    if (now - lastMaintenanceMs >= 60_000L && activeFile != null) {
                      lastMaintenanceMs = now
                      val activeNames = synchronized(this) { activeFileNames.values.toSet() }
                      storageManager.enforceRingBufferForCamera(slot, latestStorageSettings, activeNames)
                      refreshSegmentsAndStats()
                    }
                  }
                }
                prevByte = cur
              }
            }
          }
          null
        } catch (cancel: CancellationException) {
          finalizeActiveMotionChunk(slot, recorder, activeFile)
          throw cancel
        } catch (t: Throwable) {
          t.localizedMessage ?: "MJPEG error"
        } finally {
          finalizeActiveMotionChunk(slot, recorder, activeFile)
        }

      updateMotionStatus(
        slotIndex = slot.slotIndex,
        armed = true,
        recording = false,
        fileName = null,
        error = loopError,
      )
      delay(3_000L)
    }
  }

  private suspend fun runRtspMotionLoop(
    slot: CameraSlot,
    recordStreamUrl: String,
    quality: RecordingQuality,
  ) {
    // Analyze lightweight SD sub-stream during idle watching, record using configured SD/HD URL
    val watchUrl = RecordingRetentionPolicy.resolveStreamUrlForQuality(slot, RecordingQuality.SD)
      .ifBlank { recordStreamUrl }
    val sensitivity = RecordingRetentionPolicy.normalizeMotionSensitivity(slot.motionSensitivity)
    val eventDurationMs =
      RecordingRetentionPolicy.normalizeMotionDurationMinutes(slot.motionEventDurationMinutes) * 60_000L

    while (scope.isActive) {
      updateMotionStatus(slot.slotIndex, armed = true, recording = false, fileName = null, error = null)

      val triggered =
        try {
          waitForRtspMotion(watchUrl = watchUrl, sensitivity = sensitivity)
        } catch (cancel: CancellationException) {
          throw cancel
        } catch (t: Throwable) {
          updateMotionStatus(
            slotIndex = slot.slotIndex,
            armed = true,
            recording = false,
            fileName = null,
            error = t.localizedMessage ?: "Stream error",
          )
          delay(4_000L)
          false
        }

      if (!triggered || !scope.isActive) continue

      val nowMs = System.currentTimeMillis()
      val storage = latestStorageSettings
      val targetFile =
        storageManager.prepareSegmentTargetFile(
          storageSettings = storage,
          slotIndex = slot.slotIndex,
          timestampMs = nowMs,
          quality = quality,
          isMotionEvent = true,
        )

      synchronized(this) {
        activeFileNames[slot.slotIndex] = targetFile.name
      }

      val activeNamesSnapshot = synchronized(this) { activeFileNames.values.toSet() }
      val estMb =
        ((quality.estimatedMbPer15Min * (eventDurationMs / 60_000.0)) / 15.0)
          .toLong()
          .coerceAtLeast(8L)
      storageManager.enforceRingBufferForCamera(
        slot = slot,
        storageSettings = storage,
        activeFileNames = activeNamesSnapshot,
        reserveNextSegmentBytes = estMb * 1024L * 1024L,
      )

      updateMotionStatus(
        slotIndex = slot.slotIndex,
        armed = true,
        recording = true,
        fileName = targetFile.name,
        startedMs = nowMs,
        error = null,
      )
      refreshSegmentsAndStats()

      var clipError: String? = null
      try {
        recordLibVlcSegment(
          slot = slot,
          streamUrl = recordStreamUrl,
          targetFile = targetFile,
          storageSettings = storage,
          durationMs = eventDurationMs,
        )
      } catch (cancel: CancellationException) {
        storageManager.finalizeSegmentFile(targetFile, storage)
        throw cancel
      } catch (t: Throwable) {
        clipError = t.localizedMessage ?: "Motion clip error"
      }

      storageManager.finalizeSegmentFile(targetFile, storage)
      synchronized(this) {
        activeFileNames.remove(slot.slotIndex)
      }

      val activeAfter = synchronized(this) { activeFileNames.values.toSet() }
      storageManager.enforceRingBufferForCamera(slot, storage, activeAfter)
      updateMotionStatus(
        slotIndex = slot.slotIndex,
        armed = true,
        recording = false,
        fileName = null,
        error = clipError,
      )
      refreshSegmentsAndStats()
      delay(300L)
    }
  }

  private suspend fun waitForRtspMotion(watchUrl: String, sensitivity: Int): Boolean {
    val isRtsp = watchUrl.startsWith("rtsp", ignoreCase = true)
    var useTcp = isRtsp
    var libVlc: LibVLC? = null
    var mediaPlayer: MediaPlayer? = null
    var imageReader: ImageReader? = null
    var handlerThread: HandlerThread? = null
    val detected = AtomicBoolean(false)
    val streamEnded = AtomicBoolean(false)

    try {
      handlerThread = HandlerThread("CamMotionWatch_${System.nanoTime()}").apply { start() }
      val handler = Handler(handlerThread.looper)

      var previousLuma: ByteArray? = null
      var lastSampleTimeMs = 0L

      imageReader =
        ImageReader.newInstance(160, 90, PixelFormat.RGBA_8888, 2).apply {
          setOnImageAvailableListener(
            { reader ->
              val img = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
              try {
                if (detected.get()) return@setOnImageAvailableListener
                val now = System.currentTimeMillis()
                if (now - lastSampleTimeMs < MOTION_SAMPLE_INTERVAL_MS) return@setOnImageAvailableListener
                lastSampleTimeMs = now

                val curLuma = rgbaImageToLumaGrid(img)
                val prev = previousLuma
                previousLuma = curLuma
                if (prev != null && MotionDetectionMath.isMotionDetected(prev, curLuma, sensitivity)) {
                  detected.set(true)
                }
              } finally {
                runCatching { img.close() }
              }
            },
            handler,
          )
        }

      val args =
        arrayListOf(
          "--network-caching=450",
          "--live-caching=450",
          "--avcodec-hw=none",
          "--no-audio",
        ).apply {
          if (useTcp) add("--rtsp-tcp")
        }

      libVlc = LibVLC(appContext, args)
      mediaPlayer =
        MediaPlayer(libVlc).apply {
          volume = 0
          vlcVout.setVideoSurface(imageReader.surface, null)
          vlcVout.setWindowSize(160, 90)
          vlcVout.attachViews()
          setEventListener { event ->
            if (event.type == MediaPlayer.Event.EncounteredError ||
              event.type == MediaPlayer.Event.EndReached
            ) {
              streamEnded.set(true)
            }
          }
        }

      val media =
        Media(libVlc, Uri.parse(watchUrl)).apply {
          setHWDecoderEnabled(false, false)
          addOption(":no-audio")
          addOption(":network-caching=450")
          if (useTcp) addOption(":rtsp-tcp")
        }
      mediaPlayer.media = media
      media.release()
      mediaPlayer.play()

      val startedAt = System.currentTimeMillis()
      while (scope.isActive && !detected.get()) {
        if (streamEnded.get()) {
          if (isRtsp && useTcp && (System.currentTimeMillis() - startedAt < 10_000L)) {
            useTcp = false
          }
          throw IllegalStateException("Motion watcher disconnected")
        }
        delay(250L)
      }
      return detected.get()
    } finally {
      runCatching {
        mediaPlayer?.setEventListener(null)
        mediaPlayer?.stop()
        mediaPlayer?.vlcVout?.detachViews()
        mediaPlayer?.release()
        libVlc?.release()
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        handlerThread?.quitSafely()
      }
    }
  }

  private fun beginMotionChunk(slot: CameraSlot, quality: RecordingQuality): File {
    val storage = latestStorageSettings
    val now = System.currentTimeMillis()
    val file =
      storageManager.prepareSegmentTargetFile(
        storageSettings = storage,
        slotIndex = slot.slotIndex,
        timestampMs = now,
        quality = quality,
        isMotionEvent = true,
      )
    synchronized(this) {
      activeFileNames[slot.slotIndex] = file.name
    }
    val activeNames = synchronized(this) { activeFileNames.values.toSet() }
    val reserveMb =
      ((quality.estimatedMbPer15Min * slot.motionEventDurationMinutes) / 15.0)
        .toLong()
        .coerceAtLeast(8L)
    storageManager.enforceRingBufferForCamera(
      slot = slot,
      storageSettings = storage,
      activeFileNames = activeNames,
      reserveNextSegmentBytes = reserveMb * 1024L * 1024L,
    )
    return file
  }

  private fun finalizeActiveMotionChunk(
    slot: CameraSlot,
    recorder: BitmapToMp4Recorder?,
    activeFile: File?,
  ) {
    recorder?.closeSafely()
    if (activeFile != null) {
      val storage = latestStorageSettings
      storageManager.finalizeSegmentFile(activeFile, storage)
      synchronized(this) {
        activeFileNames.remove(slot.slotIndex)
      }
      val activeAfter = synchronized(this) { activeFileNames.values.toSet() }
      storageManager.enforceRingBufferForCamera(slot, storage, activeAfter)
      refreshSegmentsAndStats()
    }
  }

  private fun updateMotionStatus(
    slotIndex: Int,
    armed: Boolean,
    recording: Boolean,
    fileName: String?,
    startedMs: Long = 0L,
    error: String? = null,
  ) {
    _statuses.update { map ->
      val prev = map[slotIndex] ?: CameraRecordingRuntimeStatus(slotIndex = slotIndex)
      map +
        (slotIndex to
          prev.copy(
            isRecording = recording,
            isMotionArmed = armed,
            isMotionTriggered = recording,
            activeFileName = fileName,
            activeSegmentStartedMs = if (recording && startedMs > 0L) startedMs else prev.activeSegmentStartedMs,
            lastError = error,
          ))
    }
  }

  private fun bitmapToLumaGrid(bitmap: Bitmap): ByteArray {
    val w = bitmap.width.coerceAtLeast(1)
    val h = bitmap.height.coerceAtLeast(1)
    val out = ByteArray(LUMA_GRID_W * LUMA_GRID_H)
    var idx = 0
    for (gy in 0 until LUMA_GRID_H) {
      val py = ((gy + 0.5f) * h / LUMA_GRID_H).toInt().coerceIn(0, h - 1)
      for (gx in 0 until LUMA_GRID_W) {
        val px = ((gx + 0.5f) * w / LUMA_GRID_W).toInt().coerceIn(0, w - 1)
        val pixel = bitmap.getPixel(px, py)
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val y = ((77 * r + 150 * g + 29 * b) shr 8) and 0xFF
        out[idx++] = y.toByte()
      }
    }
    return out
  }

  private fun rgbaImageToLumaGrid(image: android.media.Image): ByteArray {
    val plane = image.planes.firstOrNull() ?: return ByteArray(0)
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val w = image.width.coerceAtLeast(1)
    val h = image.height.coerceAtLeast(1)
    val out = ByteArray(LUMA_GRID_W * LUMA_GRID_H)
    var idx = 0
    for (gy in 0 until LUMA_GRID_H) {
      val py = ((gy + 0.5f) * h / LUMA_GRID_H).toInt().coerceIn(0, h - 1)
      for (gx in 0 until LUMA_GRID_W) {
        val px = ((gx + 0.5f) * w / LUMA_GRID_W).toInt().coerceIn(0, w - 1)
        val offset = py * rowStride + px * pixelStride
        if (offset + 2 < buffer.limit()) {
          val r = buffer.get(offset).toInt() and 0xFF
          val g = buffer.get(offset + 1).toInt() and 0xFF
          val b = buffer.get(offset + 2).toInt() and 0xFF
          val y = ((77 * r + 150 * g + 29 * b) shr 8) and 0xFF
          out[idx] = y.toByte()
        }
        idx++
      }
    }
    return out
  }

  private suspend fun recordSingleSegment(
    slot: CameraSlot,
    streamUrl: String,
    quality: RecordingQuality,
    targetFile: File,
    storageSettings: RecordingStorageSettings,
    durationMs: Long,
  ) {
    val isHttp =
      streamUrl.startsWith("http://", ignoreCase = true) ||
        streamUrl.startsWith("https://", ignoreCase = true)
    val isHttpVideo = CameraSlot.isHttpVideoOrMjpegUrl(streamUrl)
    val isMjpeg =
      isHttpVideo &&
        (streamUrl.contains("/video", ignoreCase = true) ||
          streamUrl.contains("mjpeg", ignoreCase = true) ||
          streamUrl.contains(".mjpg", ignoreCase = true) ||
          streamUrl.contains("videostream", ignoreCase = true) ||
          streamUrl.contains("action=stream", ignoreCase = true))

    when {
      isHttp && !isHttpVideo ->
        recordHttpSnapshotsSegment(slot, streamUrl, quality, targetFile, storageSettings, durationMs)
      isMjpeg ->
        recordHttpMjpegSegment(slot, streamUrl, quality, targetFile, storageSettings, durationMs)
      else ->
        recordLibVlcSegment(slot, streamUrl, targetFile, storageSettings, durationMs)
    }
  }

  private suspend fun recordLibVlcSegment(
    slot: CameraSlot,
    streamUrl: String,
    targetFile: File,
    storageSettings: RecordingStorageSettings,
    durationMs: Long = RecordingRetentionPolicy.SEGMENT_DURATION_MS,
  ) {
    val isRtsp = streamUrl.startsWith("rtsp", ignoreCase = true)
    var useTcp = isRtsp
    var libVlc: LibVLC? = null
    var mediaPlayer: MediaPlayer? = null
    val segmentDeadlineMs = System.currentTimeMillis() + durationMs

    try {
      targetFile.parentFile?.mkdirs()
      val escapedPath = targetFile.absolutePath.replace("'", "\\'")
      val args =
        arrayListOf(
          "--network-caching=500",
          "--live-caching=500",
          "--avcodec-hw=none",
          "--sout-keep",
        ).apply {
          if (useTcp) add("--rtsp-tcp")
        }

      val encounteredError = AtomicBoolean(false)
      libVlc = LibVLC(appContext, args)
      mediaPlayer =
        MediaPlayer(libVlc).apply {
          volume = 0
          setEventListener { event ->
            if (event.type == MediaPlayer.Event.EncounteredError ||
              event.type == MediaPlayer.Event.EndReached
            ) {
              encounteredError.set(true)
            }
          }
        }

      val media =
        Media(libVlc, Uri.parse(streamUrl)).apply {
          setHWDecoderEnabled(false, false)
          addOption(":no-audio")
          addOption(":sout=#std{access=file,mux=mp4,dst='$escapedPath'}")
          addOption(":network-caching=500")
          if (useTcp) addOption(":rtsp-tcp")
        }
      mediaPlayer.media = media
      media.release()
      mediaPlayer.play()

      var lastMaintenanceMs = System.currentTimeMillis()
      var lastSizeCheckMs = System.currentTimeMillis()
      var lastObservedSize = 0L

      while (scope.isActive && System.currentTimeMillis() < segmentDeadlineMs) {
        delay(2_000L)
        if (encounteredError.get()) {
          throw IllegalStateException("Stream disconnected during recording")
        }
        val now = System.currentTimeMillis()
        if (now - lastSizeCheckMs >= 20_000L) {
          val curSize = targetFile.length()
          if (curSize == lastObservedSize && curSize == 0L) {
            // Try fallback without TCP on next segment cycle if zero bytes arrived in 20s
            useTcp = !useTcp
            throw IllegalStateException("No video data received")
          }
          lastObservedSize = curSize
          lastSizeCheckMs = now
        }
        if (now - lastMaintenanceMs >= 60_000L) {
          lastMaintenanceMs = now
          val activeNames = synchronized(this) { activeFileNames.values.toSet() }
          storageManager.enforceRingBufferForCamera(slot, storageSettings, activeNames)
          refreshSegmentsAndStats()
        }
      }
    } finally {
      runCatching {
        mediaPlayer?.setEventListener(null)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        libVlc?.release()
      }
    }
  }

  private suspend fun recordHttpSnapshotsSegment(
    slot: CameraSlot,
    url: String,
    quality: RecordingQuality,
    targetFile: File,
    storageSettings: RecordingStorageSettings,
    durationMs: Long,
  ) {
    val recorder =
      BitmapToMp4Recorder(
        outputFile = targetFile,
        width = quality.targetWidth,
        height = quality.targetHeight,
        bitrateBps = quality.targetBitrateBps,
        fps = 2,
      )
    val authHeader =
      if (slot.username.isNotBlank()) Credentials.basic(slot.username, slot.password) else null
    val deadlineMs = System.currentTimeMillis() + durationMs
    var lastMaintenanceMs = System.currentTimeMillis()
    var consecutiveErrors = 0

    try {
      while (scope.isActive && System.currentTimeMillis() < deadlineMs) {
        val sep = if (url.contains("?")) "&" else "?"
        val reqUrl = "$url${sep}_rec=${System.currentTimeMillis()}"
        val request =
          Request.Builder()
            .url(reqUrl)
            .apply {
              if (authHeader != null) header("Authorization", authHeader)
              header("Cache-Control", "no-cache")
            }
            .build()

        val encodedOk =
          runCatching {
            httpClient.newCall(request).execute().use { resp ->
              if (!resp.isSuccessful) return@use false
              val bytes = resp.body?.bytes() ?: return@use false
              val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use false
              try {
                recorder.encodeFrame(bmp)
              } finally {
                bmp.recycle()
              }
              true
            }
          }.getOrDefault(false)

        if (encodedOk) {
          consecutiveErrors = 0
        } else {
          consecutiveErrors++
          if (consecutiveErrors >= 15) {
            throw IllegalStateException("HTTP snapshot unreachable")
          }
        }

        val now = System.currentTimeMillis()
        if (now - lastMaintenanceMs >= 60_000L) {
          lastMaintenanceMs = now
          val activeNames = synchronized(this) { activeFileNames.values.toSet() }
          storageManager.enforceRingBufferForCamera(slot, storageSettings, activeNames)
          refreshSegmentsAndStats()
        }
        delay(500L)
      }
    } finally {
      recorder.closeSafely()
    }
  }

  private suspend fun recordHttpMjpegSegment(
    slot: CameraSlot,
    url: String,
    quality: RecordingQuality,
    targetFile: File,
    storageSettings: RecordingStorageSettings,
    durationMs: Long,
  ) {
    val recorder =
      BitmapToMp4Recorder(
        outputFile = targetFile,
        width = quality.targetWidth,
        height = quality.targetHeight,
        bitrateBps = quality.targetBitrateBps,
        fps = quality.targetFps,
      )
    val authHeader =
      if (slot.username.isNotBlank()) Credentials.basic(slot.username, slot.password) else null
    val deadlineMs = System.currentTimeMillis() + durationMs
    val minFrameIntervalMs = (1000L / quality.targetFps.coerceAtLeast(1)).coerceAtLeast(50L)

    try {
      val req =
        Request.Builder()
          .url(url)
          .apply {
            if (authHeader != null) header("Authorization", authHeader)
          }
          .build()

      httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) {
          throw IllegalStateException("HTTP ${resp.code}")
        }
        val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty MJPEG stream")
        readMjpegFramesUntil(
          stream = stream,
          deadlineMs = deadlineMs,
          minFrameIntervalMs = minFrameIntervalMs,
          onJpegFrame = { jpegBytes ->
            val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            if (bmp != null) {
              try {
                recorder.encodeFrame(bmp)
              } finally {
                bmp.recycle()
              }
            }
          },
          onMinuteMaintenance = {
            val activeNames = synchronized(this) { activeFileNames.values.toSet() }
            storageManager.enforceRingBufferForCamera(slot, storageSettings, activeNames)
            refreshSegmentsAndStats()
          },
        )
      }
    } finally {
      recorder.closeSafely()
    }
  }

  private fun readMjpegFramesUntil(
    stream: InputStream,
    deadlineMs: Long,
    minFrameIntervalMs: Long,
    onJpegFrame: (ByteArray) -> Unit,
    onMinuteMaintenance: () -> Unit,
  ) {
    val readBuf = ByteArray(16 * 1024)
    val frameOut = ByteArrayOutputStream(64 * 1024)
    var inFrame = false
    var prevByte = -1
    var lastEncodedMs = 0L
    var lastMaintenanceMs = System.currentTimeMillis()

    while (scope.isActive && System.currentTimeMillis() < deadlineMs) {
      val read = stream.read(readBuf)
      if (read <= 0) break

      for (i in 0 until read) {
        val cur = readBuf[i].toInt() and 0xFF
        if (!inFrame) {
          if (prevByte == 0xFF && cur == 0xD8) {
            frameOut.reset()
            frameOut.write(0xFF)
            frameOut.write(0xD8)
            inFrame = true
          }
        } else {
          frameOut.write(cur)
          if (prevByte == 0xFF && cur == 0xD9) {
            inFrame = false
            val now = System.currentTimeMillis()
            if (now - lastEncodedMs >= minFrameIntervalMs) {
              lastEncodedMs = now
              onJpegFrame(frameOut.toByteArray())
            }
            if (now - lastMaintenanceMs >= 60_000L) {
              lastMaintenanceMs = now
              onMinuteMaintenance()
            }
            if (now >= deadlineMs) return
          }
        }
        prevByte = cur
      }
    }
  }

  private fun refreshSegmentsAndStats() {
    val activeNames = synchronized(this) { activeFileNames.values.toSet() }
    val list = storageManager.listSegments(latestStorageSettings, activeNames)
    val stats = storageManager.queryStorageStats(latestStorageSettings, list)
    _segments.value = list
    _storageStats.value = stats

    // Update per-camera usedBytes & segmentCount inside runtime status map
    _statuses.update { existing ->
      val updated = existing.toMutableMap()
      (0..3).forEach { idx ->
        val camSegs = list.filter { it.slotIndex == idx }
        val prev = updated[idx]
        if (prev != null || camSegs.isNotEmpty()) {
          updated[idx] =
            (prev ?: CameraRecordingRuntimeStatus(slotIndex = idx)).copy(
              usedBytes = camSegs.sumOf { it.sizeBytes.coerceAtLeast(0L) },
              segmentCount = camSegs.size,
            )
        }
      }
      updated
    }
  }
}
