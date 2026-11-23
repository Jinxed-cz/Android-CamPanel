package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.model.CameraSlot
import com.example.ui.theme.DashTextMuted
import com.example.ui.theme.DashTextPrimary
import com.example.ui.theme.DashTextSecondary
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalDashPalette
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun CameraTile(
  slot: CameraSlot,
  isFullscreen: Boolean,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  val shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(10.dp)
  val borderColor =
    if (slot.isConfigured) palette.surfaceBorder else palette.surfaceBorder.copy(alpha = 0.65f)

  Box(
    modifier =
      modifier
        .clip(shape)
        .background(palette.surface)
        .then(if (!isFullscreen) Modifier.border(1.dp, borderColor, shape) else Modifier)
        .then(
          if (onClick != null) {
            Modifier.clickable(onClick = onClick)
          } else {
            Modifier
          }
        )
        .testTag(if (isFullscreen) "fullscreen_camera_view" else "camera_tile_${slot.slotIndex}")
  ) {
    if (slot.isConfigured) {
      val playbackUrl = remember(slot) { slot.effectivePlaybackUrl() }
      val isHttpUrl =
        playbackUrl.startsWith("http://", ignoreCase = true) ||
          playbackUrl.startsWith("https://", ignoreCase = true)
      val isHttpVideoStream =
        isHttpUrl &&
          (playbackUrl.contains("/livestream/", ignoreCase = true) ||
            playbackUrl.contains(".m3u8", ignoreCase = true) ||
            playbackUrl.contains(".ts", ignoreCase = true) ||
            playbackUrl.contains(".mp4", ignoreCase = true))

      if (isHttpUrl && !isHttpVideoStream) {
        HttpSnapshotFeed(
          url = playbackUrl,
          username = slot.username,
          password = slot.password,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        CameraVideoFeed(
          streamUrl = playbackUrl,
          username = slot.username,
          password = slot.password,
          isFullscreen = isFullscreen,
          modifier = Modifier.fillMaxSize(),
        )
      }
    } else {
      EmptyCameraPlaceholder(slot = slot, isFullscreen = isFullscreen)
    }

    // Subtle top gradient scrim + camera badge
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.verticalGradient(
              colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
            )
          )
          .padding(horizontal = 8.dp, vertical = 6.dp)
          .align(Alignment.TopCenter)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier =
            Modifier.size(7.dp)
              .clip(CircleShape)
              .background(if (slot.isConfigured) LiveEmerald else DashTextMuted)
        )
        Text(
          text = "K${slot.slotIndex + 1}",
          style = MaterialTheme.typography.labelSmall,
          color = palette.accentBright,
          fontWeight = FontWeight.Bold,
        )
        val displaySlotName =
          if (slot.name.startsWith("Kamera ") && LocalAppLanguage.current == com.example.model.AppLanguage.EN) {
            slot.name.replaceFirst("Kamera ", "Camera ")
          } else {
            slot.name
          }
        Text(
          text = displaySlotName,
          style = MaterialTheme.typography.bodyMedium,
          color = DashTextPrimary,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun CameraVideoFeed(
  streamUrl: String,
  username: String,
  password: String,
  isFullscreen: Boolean,
  modifier: Modifier = Modifier,
) {
  val isRtsp =
    streamUrl.startsWith("rtsp://", ignoreCase = true) ||
      streamUrl.startsWith("rtspt://", ignoreCase = true)
  val preferLibVlcDirectly =
    remember(streamUrl) {
      isRtsp &&
        (streamUrl.contains(".264", ignoreCase = true) ||
          streamUrl.contains(":80/", ignoreCase = true) ||
          streamUrl.contains("/live/ch", ignoreCase = true) ||
          streamUrl.contains("user=", ignoreCase = true))
    }
  var useLibVlc by remember(streamUrl) { mutableStateOf(preferLibVlcDirectly) }

  if (useLibVlc && isRtsp) {
    LibVlcVideoFeed(
      rtspUrl = streamUrl,
      isFullscreen = isFullscreen,
      modifier = modifier,
    )
  } else {
    RtspExoPlayerFeed(
      rtspUrl = streamUrl,
      username = username,
      password = password,
      isFullscreen = isFullscreen,
      onRtspPlaybackFailure = {
        if (isRtsp) useLibVlc = true
      },
      modifier = modifier,
    )
  }
}

@OptIn(UnstableApi::class)
@Composable
private fun RtspExoPlayerFeed(
  rtspUrl: String,
  username: String,
  password: String,
  isFullscreen: Boolean,
  onRtspPlaybackFailure: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var isBuffering by remember(rtspUrl) { mutableStateOf(true) }
  var errorMsg by remember(rtspUrl) { mutableStateOf<String?>(null) }

  val exoPlayer =
    remember(rtspUrl) {
      ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ false)
        .build()
        .apply {
          volume = if (isFullscreen) 1f else 0f
          playWhenReady = true
        }
    }

  LaunchedEffect(isFullscreen) { exoPlayer.volume = if (isFullscreen) 1f else 0f }

  DisposableEffect(rtspUrl) {
    val isRtsp =
      rtspUrl.startsWith("rtsp://", ignoreCase = true) ||
        rtspUrl.startsWith("rtspt://", ignoreCase = true)
    val listener =
      object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          when (playbackState) {
            Player.STATE_BUFFERING -> isBuffering = true
            Player.STATE_READY -> {
              isBuffering = false
              errorMsg = null
            }
            Player.STATE_ENDED -> isBuffering = false
            Player.STATE_IDLE -> Unit
          }
        }

        override fun onPlayerError(error: PlaybackException) {
          isBuffering = false
          if (isRtsp) {
            onRtspPlaybackFailure()
          } else {
            errorMsg = "Stream nedostupný (${error.errorCodeName})"
          }
        }
      }
    exoPlayer.addListener(listener)

    runCatching {
      if (rtspUrl.startsWith("http://", ignoreCase = true) ||
        rtspUrl.startsWith("https://", ignoreCase = true)
      ) {
        if (username.isNotBlank()) {
          val authHeader = okhttp3.Credentials.basic(username, password)
          val httpFactory =
            DefaultHttpDataSource.Factory()
              .setDefaultRequestProperties(mapOf("Authorization" to authHeader))
              .setConnectTimeoutMs(5000)
              .setReadTimeoutMs(5000)
              .setAllowCrossProtocolRedirects(true)
          val mediaSource =
            DefaultMediaSourceFactory(httpFactory)
              .createMediaSource(MediaItem.fromUri(Uri.parse(rtspUrl)))
          exoPlayer.setMediaSource(mediaSource)
        } else {
          exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(rtspUrl)))
        }
      } else {
        val mediaSource =
          RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .setTimeoutMs(5000L)
            .createMediaSource(MediaItem.fromUri(Uri.parse(rtspUrl)))
        exoPlayer.setMediaSource(mediaSource)
      }
      exoPlayer.prepare()
    }.onFailure { e ->
      isBuffering = false
      if (isRtsp) {
        onRtspPlaybackFailure()
      } else {
        errorMsg = e.localizedMessage ?: "Neplatná adresa streamu"
      }
    }

    onDispose {
      exoPlayer.removeListener(listener)
      exoPlayer.release()
    }
  }

  Box(modifier = modifier.background(Color.Black)) {
    AndroidView(
      factory = { ctx ->
        PlayerView(ctx).apply {
          useController = false
          isClickable = false
          isFocusable = false
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
          resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
          player = exoPlayer
        }
      },
      update = { view ->
        view.player = exoPlayer
        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      },
      modifier = Modifier.fillMaxSize(),
    )

    if (isBuffering && errorMsg == null) {
      val palette = LocalDashPalette.current
      CircularProgressIndicator(
        modifier = Modifier.size(24.dp).align(Alignment.Center),
        color = palette.accentBright,
        strokeWidth = 2.5.dp,
      )
    }

    if (errorMsg != null) {
      Column(
        modifier = Modifier.align(Alignment.Center).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Rounded.VideocamOff,
          contentDescription = "Kamera offline",
          tint = OfflineRed,
          modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = errorMsg.orEmpty(),
          style = MaterialTheme.typography.labelSmall,
          color = DashTextSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun LibVlcVideoFeed(
  rtspUrl: String,
  isFullscreen: Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var isBuffering by remember(rtspUrl) { mutableStateOf(true) }
  var errorMsg by remember(rtspUrl) { mutableStateOf<String?>(null) }
  var forceRtspTcp by remember(rtspUrl) { mutableStateOf(false) }

  val vlcLayout =
    remember(rtspUrl) {
      VLCVideoLayout(context).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
      }
    }

  val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
  var activePlayer by remember(rtspUrl) { mutableStateOf<MediaPlayer?>(null) }

  LaunchedEffect(isFullscreen, activePlayer) {
    activePlayer?.let { player ->
      runCatching {
        player.volume = if (isFullscreen) 100 else 0
        player.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
      }
    }
  }

  DisposableEffect(rtspUrl, forceRtspTcp) {
    var libVlc: LibVLC? = null
    var mediaPlayer: MediaPlayer? = null

    runCatching {
      val args =
        arrayListOf(
          "--network-caching=350",
          "--live-caching=350",
          "--clock-jitter=0",
          "--clock-synchro=0",
        ).apply {
          if (forceRtspTcp) add("--rtsp-tcp")
        }
      libVlc = LibVLC(context, args)
      mediaPlayer =
        MediaPlayer(libVlc).apply {
          attachViews(vlcLayout, null, false, true)
          volume = if (isFullscreen) 100 else 0
          videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
          setEventListener { event ->
            when (event.type) {
              MediaPlayer.Event.Playing,
              MediaPlayer.Event.Vout -> {
                mainHandler.post {
                  isBuffering = false
                  errorMsg = null
                }
              }
              MediaPlayer.Event.Buffering -> {
                if (event.buffering >= 100f) {
                  mainHandler.post { isBuffering = false }
                }
              }
              MediaPlayer.Event.EncounteredError -> {
                mainHandler.post {
                  isBuffering = false
                  if (!forceRtspTcp) {
                    forceRtspTcp = true
                  } else {
                    errorMsg = "RTSP stream nedostupný"
                  }
                }
              }
            }
          }
        }
      activePlayer = mediaPlayer

      val media =
        Media(libVlc, Uri.parse(rtspUrl)).apply {
          setHWDecoderEnabled(true, false)
          addOption(":network-caching=350")
          if (forceRtspTcp) addOption(":rtsp-tcp")
        }
      mediaPlayer?.media = media
      media.release()
      mediaPlayer?.play()
    }.onFailure { err ->
      isBuffering = false
      errorMsg = err.localizedMessage ?: "Chyba VLC přehrávače"
    }

    onDispose {
      activePlayer = null
      runCatching {
        mediaPlayer?.setEventListener(null)
        mediaPlayer?.stop()
        mediaPlayer?.detachViews()
        mediaPlayer?.release()
        libVlc?.release()
      }
    }
  }

  Box(modifier = modifier.background(Color.Black)) {
    AndroidView(
      factory = { vlcLayout },
      update = {
        activePlayer?.let { player ->
          runCatching { player.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT }
        }
      },
      modifier = Modifier.fillMaxSize(),
    )

    if (isBuffering && errorMsg == null) {
      val palette = LocalDashPalette.current
      CircularProgressIndicator(
        modifier = Modifier.size(24.dp).align(Alignment.Center),
        color = palette.accentBright,
        strokeWidth = 2.5.dp,
      )
    }

    if (errorMsg != null) {
      Column(
        modifier = Modifier.align(Alignment.Center).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Rounded.VideocamOff,
          contentDescription = "Kamera offline",
          tint = OfflineRed,
          modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = errorMsg.orEmpty(),
          style = MaterialTheme.typography.labelSmall,
          color = DashTextSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun HttpSnapshotFeed(
  url: String,
  username: String,
  password: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lang = LocalAppLanguage.current
  var refreshTick by remember(url) { mutableLongStateOf(0L) }
  var hasError by remember(url) { mutableStateOf(false) }

  LaunchedEffect(url) {
    while (isActive) {
      delay(1000L)
      refreshTick++
    }
  }

  val sep = if (url.contains("?")) "&" else "?"
  val requestUrl = "$url${sep}_t=$refreshTick"
  val authHeader =
    remember(username, password) {
      if (username.isNotBlank()) okhttp3.Credentials.basic(username, password) else null
    }

  Box(modifier = modifier.background(Color.Black)) {
    AsyncImage(
      model =
        ImageRequest.Builder(context)
          .data(requestUrl)
          .apply {
            if (authHeader != null) addHeader("Authorization", authHeader)
          }
          .memoryCachePolicy(CachePolicy.DISABLED)
          .diskCachePolicy(CachePolicy.DISABLED)
          .crossfade(false)
          .listener(
            onSuccess = { _, _ -> hasError = false },
            onError = { _, _ -> hasError = true },
          )
          .build(),
      contentDescription = tr(lang, "Živý snímek kamery", "Live camera snapshot"),
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxSize(),
    )

    if (hasError) {
      Column(
        modifier = Modifier.align(Alignment.Center).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Rounded.VideocamOff,
          contentDescription = tr(lang, "Kamera nedostupná", "Camera unavailable"),
          tint = OfflineRed,
          modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = tr(lang, "HTTP kamera nedostupná", "HTTP camera unavailable"),
          style = MaterialTheme.typography.labelSmall,
          color = DashTextSecondary,
        )
      }
    }
  }
}

@Composable
private fun EmptyCameraPlaceholder(slot: CameraSlot, isFullscreen: Boolean) {
  val palette = LocalDashPalette.current
  val lang = LocalAppLanguage.current
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(palette.surfaceElevated, palette.background),
          )
        )
  ) {
    // Subtle precision surveillance reticle grid
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val lineColor = palette.surfaceBorder.copy(alpha = 0.7f)
      drawLine(lineColor, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1f)
      drawLine(lineColor, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)
    }

    Column(
      modifier = Modifier.align(Alignment.Center).padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = Icons.Rounded.Videocam,
        contentDescription =
          tr(
            lang,
            "Kamera ${slot.slotIndex + 1} nenastavena",
            "Camera ${slot.slotIndex + 1} not configured",
          ),
        tint = DashTextMuted,
        modifier = Modifier.size(if (isFullscreen) 44.dp else 26.dp),
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text =
          tr(
            lang,
            "Kamera ${slot.slotIndex + 1} nenastavena",
            "Camera ${slot.slotIndex + 1} not configured",
          ),
        style =
          if (isFullscreen) MaterialTheme.typography.titleMedium
          else MaterialTheme.typography.labelSmall,
        color = DashTextSecondary,
      )
      if (isFullscreen) {
        Spacer(Modifier.height(4.dp))
        Text(
          text =
            tr(
              lang,
              "Přidejte kameru v Menu → Kamery",
              "Add camera in Menu → Cameras",
            ),
          style = MaterialTheme.typography.bodyMedium,
          color = DashTextMuted,
          fontSize = 12.sp,
        )
      }
    }
  }
}
