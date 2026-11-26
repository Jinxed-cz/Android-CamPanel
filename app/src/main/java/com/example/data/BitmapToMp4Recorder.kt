package com.example.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Lightweight H.264 / MP4 segment writer backed by Android's hardware MediaCodec + MediaMuxer.
 * Used for HTTP snapshot & MJPEG IP cameras to produce space-efficient standard `.mp4` files.
 */
class BitmapToMp4Recorder(
  private val outputFile: File,
  width: Int,
  height: Int,
  private val bitrateBps: Int,
  private val fps: Int,
) {
  private val alignedWidth = (width / 2) * 2
  private val alignedHeight = (height / 2) * 2
  private val colorFormat: Int
  private val codec: MediaCodec
  private val muxer: MediaMuxer
  private val bufferInfo = MediaCodec.BufferInfo()
  private var trackIndex = -1
  private var muxerStarted = false
  private var frameCount = 0L
  private var isClosed = false
  private val startTimeNs = System.nanoTime()

  private val scaledBitmap = Bitmap.createBitmap(alignedWidth, alignedHeight, Bitmap.Config.ARGB_8888)
  private val drawCanvas = Canvas(scaledBitmap)
  private val argbPixels = IntArray(alignedWidth * alignedHeight)
  private val yuvBytes = ByteArray(alignedWidth * alignedHeight * 3 / 2)

  init {
    outputFile.parentFile?.mkdirs()
    codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    colorFormat = selectSupportedColorFormat(codec)

    val format =
      MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, alignedWidth, alignedHeight).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
        setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps.coerceAtLeast(1))
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
      }
    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    codec.start()
    muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
  }

  fun encodeFrame(source: Bitmap) {
    if (isClosed) return

    // Letterbox source bitmap cleanly onto aligned encoder canvas
    drawCanvas.drawColor(Color.BLACK)
    val scale = minOf(alignedWidth.toFloat() / source.width, alignedHeight.toFloat() / source.height)
    val dstW = (source.width * scale).toInt().coerceAtLeast(2)
    val dstH = (source.height * scale).toInt().coerceAtLeast(2)
    val left = (alignedWidth - dstW) / 2
    val top = (alignedHeight - dstH) / 2
    val dstRect = android.graphics.Rect(left, top, left + dstW, top + dstH)
    drawCanvas.drawBitmap(source, null, dstRect, null)

    scaledBitmap.getPixels(argbPixels, 0, alignedWidth, 0, 0, alignedWidth, alignedHeight)
    val isSemiPlanar =
      colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    argbToYuv420(argbPixels, alignedWidth, alignedHeight, yuvBytes, semiPlanar = isSemiPlanar)

    val inputIdx = codec.dequeueInputBuffer(10_000L)
    if (inputIdx >= 0) {
      val inputBuf: ByteBuffer? = codec.getInputBuffer(inputIdx)
      inputBuf?.clear()
      inputBuf?.put(yuvBytes, 0, yuvBytes.size.coerceAtMost(inputBuf.capacity()))
      val ptsUs = (System.nanoTime() - startTimeNs) / 1_000L
      codec.queueInputBuffer(inputIdx, 0, yuvBytes.size, ptsUs, 0)
      frameCount++
    }
    drainEncoder(endOfStream = false)
  }

  fun closeSafely() {
    if (isClosed) return
    isClosed = true
    runCatching {
      val inputIdx = codec.dequeueInputBuffer(10_000L)
      if (inputIdx >= 0) {
        val ptsUs = (System.nanoTime() - startTimeNs) / 1_000L
        codec.queueInputBuffer(inputIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
      }
      drainEncoder(endOfStream = true)
    }
    runCatching {
      codec.stop()
      codec.release()
    }
    runCatching {
      if (muxerStarted) {
        muxer.stop()
      }
      muxer.release()
    }
    runCatching { scaledBitmap.recycle() }
  }

  private fun drainEncoder(endOfStream: Boolean) {
    val timeoutUs = if (endOfStream) 10_000L else 0L
    while (true) {
      val outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
      when {
        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
          if (!endOfStream) break
        }
        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          if (!muxerStarted) {
            trackIndex = muxer.addTrack(codec.outputFormat)
            muxer.start()
            muxerStarted = true
          }
        }
        outIdx >= 0 -> {
          val encoded = codec.getOutputBuffer(outIdx)
          if (encoded != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
            encoded.position(bufferInfo.offset)
            encoded.limit(bufferInfo.offset + bufferInfo.size)
            muxer.writeSampleData(trackIndex, encoded, bufferInfo)
          }
          codec.releaseOutputBuffer(outIdx, false)
          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
        }
      }
    }
  }

  private fun selectSupportedColorFormat(codec: MediaCodec): Int {
    val caps =
      runCatching { codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC) }.getOrNull()
    val formats = caps?.colorFormats ?: intArrayOf()
    return when {
      MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar in formats ->
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
      MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar in formats ->
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
      else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
    }
  }

  private fun argbToYuv420(
    argb: IntArray,
    width: Int,
    height: Int,
    out: ByteArray,
    semiPlanar: Boolean,
  ) {
    val frameSize = width * height
    var yIndex = 0
    var uvIndex = frameSize
    val uPlaneOffset = frameSize
    val vPlaneOffset = frameSize + frameSize / 4
    var planarPos = 0

    for (j in 0 until height) {
      for (i in 0 until width) {
        val px = argb[j * width + i]
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF

        val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
        out[yIndex++] = y.coerceIn(0, 255).toByte()

        if ((j and 1) == 0 && (i and 1) == 0) {
          val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
          val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
          if (semiPlanar) {
            out[uvIndex++] = u.coerceIn(0, 255).toByte()
            out[uvIndex++] = v.coerceIn(0, 255).toByte()
          } else {
            out[uPlaneOffset + planarPos] = u.coerceIn(0, 255).toByte()
            out[vPlaneOffset + planarPos] = v.coerceIn(0, 255).toByte()
            planarPos++
          }
        }
      }
    }
  }
}
