package com.example.data

import android.content.Context
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.model.ActiveRadioStation
import com.example.model.RadioStation
import com.example.model.RadioUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Streams Czech online radio stations via Media3 ExoPlayer with standard USAGE_MEDIA audio routing
 * (automatically plays over connected Bluetooth audio devices or falls back to the built-in
 * speaker) and controls system STREAM_MUSIC volume.
 */
@OptIn(UnstableApi::class)
class RadioPlayerController(context: Context) {

  private val appContext = context.applicationContext
  private val audioManager =
    appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private val _state = MutableStateFlow(RadioUiState())
  val state: StateFlow<RadioUiState> = _state.asStateFlow()

  private var exoPlayer: ExoPlayer? = null
  private var triedFallback = false

  private val audioDeviceCallback =
    object : AudioDeviceCallback() {
      override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
        refreshAudioOutputInfo()
        refreshVolumePercent()
      }

      override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
        refreshAudioOutputInfo()
        refreshVolumePercent()
      }
    }

  private val volumeObserver =
    object : ContentObserver(Handler(Looper.getMainLooper())) {
      override fun onChange(selfChange: Boolean) {
        refreshVolumePercent()
      }
    }

  init {
    refreshAudioOutputInfo()
    refreshVolumePercent()
    audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    runCatching {
      appContext.contentResolver.registerContentObserver(
        Settings.System.CONTENT_URI,
        true,
        volumeObserver,
      )
    }
  }

  fun setSelectedStation(station: RadioStation) {
    setSelectedActiveStation(ActiveRadioStation.fromPreset(station), preset = station)
  }

  fun setSelectedActiveStation(
    station: ActiveRadioStation,
    preset: RadioStation = RadioStation.fromId(station.id),
  ) {
    val wasPlaying = _state.value.isPlaying
    val changed = _state.value.activeStation.id != station.id ||
      _state.value.activeStation.primaryStreamUrl != station.primaryStreamUrl
    _state.update {
      it.copy(
        selectedStation = preset,
        activeStation = station,
        errorMessage = null,
      )
    }
    if (wasPlaying && changed) {
      startPlayback(station)
    }
  }

  fun togglePlayStop() {
    if (_state.value.isPlaying || _state.value.isBuffering) {
      stopPlayback()
    } else {
      startPlayback(_state.value.activeStation)
    }
  }

  fun increaseVolume() {
    audioManager.adjustStreamVolume(
      AudioManager.STREAM_MUSIC,
      AudioManager.ADJUST_RAISE,
      0,
    )
    refreshVolumePercent()
  }

  fun decreaseVolume() {
    audioManager.adjustStreamVolume(
      AudioManager.STREAM_MUSIC,
      AudioManager.ADJUST_LOWER,
      0,
    )
    refreshVolumePercent()
  }

  private fun startPlayback(station: ActiveRadioStation) {
    triedFallback = false
    val player = ensurePlayer()
    _state.update { it.copy(isPlaying = true, isBuffering = true, errorMessage = null) }
    player.setMediaItem(MediaItem.fromUri(station.primaryStreamUrl))
    player.prepare()
    player.playWhenReady = true
  }

  fun stopPlayback() {
    exoPlayer?.let {
      it.stop()
      it.clearMediaItems()
    }
    _state.update { it.copy(isPlaying = false, isBuffering = false, errorMessage = null) }
  }

  private fun ensurePlayer(): ExoPlayer {
    exoPlayer?.let { return it }

    val httpFactory =
      DefaultHttpDataSource.Factory()
        .setUserAgent("CamPanel-Android/1.0")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(12_000)

    val audioAttributes =
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    val created =
      ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    created.addListener(
      object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          when (playbackState) {
            Player.STATE_BUFFERING ->
              _state.update { it.copy(isBuffering = true, errorMessage = null) }
            Player.STATE_READY ->
              _state.update {
                it.copy(isPlaying = true, isBuffering = false, errorMessage = null)
              }
            Player.STATE_IDLE,
            Player.STATE_ENDED ->
              if (!_state.value.isBuffering) {
                _state.update { it.copy(isPlaying = false, isBuffering = false) }
              }
          }
        }

        override fun onPlayerError(error: PlaybackException) {
          val station = _state.value.activeStation
          if (!triedFallback && station.fallbackStreamUrl.isNotBlank()) {
            triedFallback = true
            created.setMediaItem(MediaItem.fromUri(station.fallbackStreamUrl))
            created.prepare()
            created.playWhenReady = true
          } else {
            _state.update {
              it.copy(
                isPlaying = false,
                isBuffering = false,
                errorMessage = "Stream nedostupný",
              )
            }
          }
        }
      }
    )

    exoPlayer = created
    return created
  }

  private fun refreshVolumePercent() {
    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val percent = ((currentVol.toFloat() / maxVol.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    _state.update { it.copy(volumePercent = percent) }
  }

  private fun refreshAudioOutputInfo() {
    val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val btDevice =
      outputs.firstOrNull { device ->
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
          device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
          (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
              device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
      }

    if (btDevice != null) {
      val name = btDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
      _state.update { it.copy(isBluetoothConnected = true, outputDeviceName = name) }
    } else {
      _state.update { it.copy(isBluetoothConnected = false, outputDeviceName = "Reproduktor") }
    }
  }

  fun release() {
    runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
    runCatching { appContext.contentResolver.unregisterContentObserver(volumeObserver) }
    exoPlayer?.release()
    exoPlayer = null
  }
}
