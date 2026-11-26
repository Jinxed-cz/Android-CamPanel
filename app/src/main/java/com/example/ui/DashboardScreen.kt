package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppearanceSettings
import com.example.model.BatteryRuntimeStatus
import com.example.model.ClockStylePreset
import com.example.ui.components.AladinWeatherPanel
import com.example.ui.components.CameraTile
import com.example.ui.components.OnlineRadioPanel
import com.example.ui.components.SettingsOverlay
import com.example.ui.theme.CamPanelTheme
import com.example.ui.theme.DashTextPrimary
import com.example.ui.theme.DashTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalDashPalette
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.TempAmber
import kotlin.math.abs

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
  val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
  val weatherProvider by viewModel.weatherProvider.collectAsStateWithLifecycle()
  val cameras by viewModel.cameras.collectAsStateWithLifecycle()
  val cameraDisplayCount by viewModel.cameraDisplayCount.collectAsStateWithLifecycle()
  val weatherLocation by viewModel.weatherLocation.collectAsStateWithLifecycle()
  val weatherToggles by viewModel.weatherToggles.collectAsStateWithLifecycle()
  val savedWeatherLocations by viewModel.savedWeatherLocations.collectAsStateWithLifecycle()
  val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()

  val radioState by viewModel.radioState.collectAsStateWithLifecycle()
  val favoriteRadios by viewModel.favoriteRadios.collectAsStateWithLifecycle()
  val availableRadioStations by viewModel.availableRadioStations.collectAsStateWithLifecycle()
  val radioSearchResults by viewModel.radioSearchResults.collectAsStateWithLifecycle()
  val isSearchingRadios by viewModel.isSearchingRadios.collectAsStateWithLifecycle()
  val radioSearchError by viewModel.radioSearchError.collectAsStateWithLifecycle()

  val displaySettings by viewModel.displaySettings.collectAsStateWithLifecycle()
  val displayRuntimeStatus by viewModel.displayRuntimeStatus.collectAsStateWithLifecycle()
  val appearanceSettings by viewModel.appearanceSettings.collectAsStateWithLifecycle()
  val batteryStatus by viewModel.batteryStatus.collectAsStateWithLifecycle()

  val clockTime by viewModel.clockTime.collectAsStateWithLifecycle()
  val clockDate by viewModel.clockDate.collectAsStateWithLifecycle()

  val fullscreenSlot by viewModel.fullscreenSlot.collectAsStateWithLifecycle()
  val fullscreenRemainingSec by viewModel.fullscreenRemainingSec.collectAsStateWithLifecycle()

  val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
  val settingsTimeoutSec by viewModel.settingsTimeoutSec.collectAsStateWithLifecycle()
  val settingsRemainingSec by viewModel.settingsRemainingSec.collectAsStateWithLifecycle()

  val locationSearchResults by viewModel.locationSearchResults.collectAsStateWithLifecycle()
  val isSearchingLocations by viewModel.isSearchingLocations.collectAsStateWithLifecycle()
  val locationSearchError by viewModel.locationSearchError.collectAsStateWithLifecycle()

  val discoveredCameras by viewModel.discoveredCameras.collectAsStateWithLifecycle()
  val isScanningOnvif by viewModel.isScanningOnvif.collectAsStateWithLifecycle()
  val onvifStatusMessage by viewModel.onvifStatusMessage.collectAsStateWithLifecycle()

  val recordingStorageSettings by viewModel.recordingStorageSettings.collectAsStateWithLifecycle()
  val recordingStatuses by viewModel.recordingStatuses.collectAsStateWithLifecycle()
  val recordedSegments by viewModel.recordedSegments.collectAsStateWithLifecycle()
  val recordingStorageStats by viewModel.recordingStorageStats.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer =
      LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_START -> viewModel.startLightMonitoring()
          Lifecycle.Event.ON_STOP -> viewModel.stopLightMonitoring()
          else -> Unit
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      viewModel.startLightMonitoring()
    }
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.stopLightMonitoring()
    }
  }

  LaunchedEffect(displaySettings.keepScreenOn, displayRuntimeStatus.effectiveBrightness) {
    val window = context.findActivity()?.window ?: return@LaunchedEffect
    if (displaySettings.keepScreenOn) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    val targetBrightness = displayRuntimeStatus.effectiveBrightness
    val attrs = window.attributes
    if (abs(attrs.screenBrightness - targetBrightness) > 0.005f) {
      attrs.screenBrightness = targetBrightness
      window.attributes = attrs
    }
  }

  CamPanelTheme(appearance = appearanceSettings, language = appLanguage) {
    val palette = LocalDashPalette.current

    BoxWithConstraints(
      modifier =
        Modifier.fillMaxSize()
          .background(palette.background)
          .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
      val availW = maxWidth
      val availH = maxHeight
      val isCompactWidth = availW < 680.dp
      val isCompactHeight = availH < 370.dp
      val isExpandedScreen = availW >= 960.dp

      val outerPad =
        when {
          isCompactWidth || isCompactHeight -> 5.dp
          isExpandedScreen -> 10.dp
          else -> 8.dp
        }
      val gridGap =
        when {
          isCompactWidth || isCompactHeight -> 5.dp
          isExpandedScreen -> 10.dp
          else -> 8.dp
        }
      val topRowWeight = if (isCompactHeight) 0.49f else 0.54f
      val bottomRowWeight = 1f - topRowWeight
      val clockColWidth = (availW * 0.175f).coerceIn(124.dp, 220.dp)
      val radioColWidth = (availW * 0.21f).coerceIn(160.dp, 250.dp)
      val isDashboardStreamsActive = fullscreenSlot == null && !isSettingsOpen

      // Main Landscape Dashboard Layout supporting 1, 2, or 4 visible cameras
      when (cameraDisplayCount) {
        1 -> {
          val rightPaneWidth = (availW - outerPad * 2f - gridGap) * 0.65f
          val splitClockColWidth = (rightPaneWidth * 0.34f).coerceIn(124.dp, 210.dp)
          val weatherRowWeight = if (isCompactHeight) 0.55f else 0.58f
          val bottomControlWeight = 1f - weatherRowWeight

          Row(
            modifier = Modifier.fillMaxSize().padding(outerPad).testTag("layout_1_camera"),
            horizontalArrangement = Arrangement.spacedBy(gridGap),
          ) {
            // Left ~1/3: Single primary camera
            val firstCam = cameras.first()
            CameraTile(
              slot = firstCam,
              isFullscreen = false,
              isStreamActive = isDashboardStreamsActive,
              activeTileCount = 1,
              recordingStatus = recordingStatuses[firstCam.slotIndex],
              onClick = { viewModel.openCameraFullscreen(firstCam.slotIndex) },
              modifier = Modifier.weight(0.35f).fillMaxHeight(),
            )

            // Right ~2/3: Weather on top + [Radio | Clock & Menu] below
            Column(
              modifier = Modifier.weight(0.65f).fillMaxHeight(),
              verticalArrangement = Arrangement.spacedBy(gridGap),
            ) {
              AladinWeatherPanel(
                weatherState = weatherState,
                toggles = weatherToggles,
                locationName = weatherLocation.name,
                activeProvider = weatherProvider,
                modifier = Modifier.fillMaxWidth().weight(weatherRowWeight),
              )

              Row(
                modifier = Modifier.fillMaxWidth().weight(bottomControlWeight),
                horizontalArrangement = Arrangement.spacedBy(gridGap),
              ) {
                OnlineRadioPanel(
                  radioState = radioState,
                  availableStations = availableRadioStations,
                  onSelectStation = { viewModel.selectActiveRadioStation(it) },
                  onTogglePlayStop = { viewModel.toggleRadioPlayStop() },
                  onVolumeUp = { viewModel.increaseRadioVolume() },
                  onVolumeDown = { viewModel.decreaseRadioVolume() },
                  modifier = Modifier.weight(1f).fillMaxHeight(),
                )

                ClockAndMenuColumn(
                  timeText = clockTime,
                  dateText = clockDate,
                  appearanceSettings = appearanceSettings,
                  batteryStatus = batteryStatus,
                  onMenuClick = { viewModel.openSettings() },
                  modifier = Modifier.width(splitClockColWidth).fillMaxHeight(),
                )
              }
            }
          }
        }
        2 -> {
          val rightPaneWidth = (availW - outerPad * 2f - gridGap) * 0.61f
          val splitClockColWidth = (rightPaneWidth * 0.34f).coerceIn(118.dp, 200.dp)
          val weatherRowWeight = if (isCompactHeight) 0.52f else 0.55f
          val bottomControlWeight = 1f - weatherRowWeight

          Row(
            modifier = Modifier.fillMaxSize().padding(outerPad).testTag("layout_2_cameras"),
            horizontalArrangement = Arrangement.spacedBy(gridGap),
          ) {
            // Left: 2 cameras stacked vertically
            Column(
              modifier = Modifier.weight(0.39f).fillMaxHeight(),
              verticalArrangement = Arrangement.spacedBy(gridGap),
            ) {
              cameras.take(2).forEach { slot ->
                CameraTile(
                  slot = slot,
                  isFullscreen = false,
                  isStreamActive = isDashboardStreamsActive,
                  activeTileCount = 2,
                  recordingStatus = recordingStatuses[slot.slotIndex],
                  onClick = { viewModel.openCameraFullscreen(slot.slotIndex) },
                  modifier = Modifier.fillMaxWidth().weight(1f),
                )
              }
            }

            // Right: Compactly scaled Weather on top + [Radio | Clock & Menu] below
            Column(
              modifier = Modifier.weight(0.61f).fillMaxHeight(),
              verticalArrangement = Arrangement.spacedBy(gridGap),
            ) {
              AladinWeatherPanel(
                weatherState = weatherState,
                toggles = weatherToggles,
                locationName = weatherLocation.name,
                activeProvider = weatherProvider,
                modifier = Modifier.fillMaxWidth().weight(weatherRowWeight),
              )

              Row(
                modifier = Modifier.fillMaxWidth().weight(bottomControlWeight),
                horizontalArrangement = Arrangement.spacedBy(gridGap),
              ) {
                OnlineRadioPanel(
                  radioState = radioState,
                  availableStations = availableRadioStations,
                  onSelectStation = { viewModel.selectActiveRadioStation(it) },
                  onTogglePlayStop = { viewModel.toggleRadioPlayStop() },
                  onVolumeUp = { viewModel.increaseRadioVolume() },
                  onVolumeDown = { viewModel.decreaseRadioVolume() },
                  modifier = Modifier.weight(1f).fillMaxHeight(),
                )

                ClockAndMenuColumn(
                  timeText = clockTime,
                  dateText = clockDate,
                  appearanceSettings = appearanceSettings,
                  batteryStatus = batteryStatus,
                  onMenuClick = { viewModel.openSettings() },
                  modifier = Modifier.width(splitClockColWidth).fillMaxHeight(),
                )
              }
            }
          }
        }
        else -> {
          // Original 4-camera top-row + 3-panel bottom-row layout
          Column(
            modifier = Modifier.fillMaxSize().padding(outerPad).testTag("layout_4_cameras"),
            verticalArrangement = Arrangement.spacedBy(gridGap),
          ) {
            // TOP PART: 4 online cameras in a row
            Row(
              modifier = Modifier.fillMaxWidth().weight(topRowWeight),
              horizontalArrangement = Arrangement.spacedBy(gridGap),
            ) {
              cameras.take(4).forEach { slot ->
                CameraTile(
                  slot = slot,
                  isFullscreen = false,
                  isStreamActive = isDashboardStreamsActive,
                  activeTileCount = 4,
                  recordingStatus = recordingStatuses[slot.slotIndex],
                  onClick = { viewModel.openCameraFullscreen(slot.slotIndex) },
                  modifier = Modifier.weight(1f).fillMaxHeight(),
                )
              }
            }

            // BOTTOM PART: Left (Date/Time + Menu/Battery) + Center (3-day Weather) + Right (Radio)
            Row(
              modifier = Modifier.fillMaxWidth().weight(bottomRowWeight),
              horizontalArrangement = Arrangement.spacedBy(gridGap),
            ) {
              ClockAndMenuColumn(
                timeText = clockTime,
                dateText = clockDate,
                appearanceSettings = appearanceSettings,
                batteryStatus = batteryStatus,
                onMenuClick = { viewModel.openSettings() },
                modifier = Modifier.width(clockColWidth).fillMaxHeight(),
              )

              AladinWeatherPanel(
                weatherState = weatherState,
                toggles = weatherToggles,
                locationName = weatherLocation.name,
                activeProvider = weatherProvider,
                modifier = Modifier.weight(1f).fillMaxHeight(),
              )

              OnlineRadioPanel(
                radioState = radioState,
                availableStations = availableRadioStations,
                onSelectStation = { viewModel.selectActiveRadioStation(it) },
                onTogglePlayStop = { viewModel.toggleRadioPlayStop() },
                onVolumeUp = { viewModel.increaseRadioVolume() },
                onVolumeDown = { viewModel.decreaseRadioVolume() },
                modifier = Modifier.width(radioColWidth).fillMaxHeight(),
              )
            }
          }
        }
      }

      // Fullscreen Camera Overlay (10s auto return)
      AnimatedVisibility(
        visible = fullscreenSlot != null,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
      ) {
        val slotIndex = fullscreenSlot ?: 0
        val slot = cameras.getOrNull(slotIndex)
        if (slot != null) {
          BackHandler { viewModel.closeCameraFullscreen() }
          Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            CameraTile(
              slot = slot,
              isFullscreen = true,
              isStreamActive = true,
              activeTileCount = 1,
              recordingStatus = recordingStatuses[slot.slotIndex],
              onClick = { viewModel.closeCameraFullscreen() },
              modifier = Modifier.fillMaxSize(),
            )

            // Top-right 10s countdown & close pill
            Row(
              modifier =
                Modifier.align(Alignment.TopEnd)
                  .padding(12.dp)
                  .clip(RoundedCornerShape(20.dp))
                  .background(Color.Black.copy(alpha = 0.7f))
                  .border(1.dp, palette.surfaceBorder, RoundedCornerShape(20.dp))
                  .clickable { viewModel.closeCameraFullscreen() }
                  .padding(horizontal = 12.dp, vertical = 8.dp)
                  .testTag("fullscreen_countdown_badge"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text =
                  if (appLanguage == com.example.model.AppLanguage.EN)
                    "Return in ${fullscreenRemainingSec} s"
                  else "Návrat za ${fullscreenRemainingSec} s",
                style = MaterialTheme.typography.labelMedium,
                color = DashTextPrimary,
              )
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription =
                  if (appLanguage == com.example.model.AppLanguage.EN) "Close fullscreen"
                  else "Zavřít celou obrazovku",
                tint = DashTextSecondary,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }

      // Settings Overlay (10s / 30s inactivity auto return)
      AnimatedVisibility(
        visible = isSettingsOpen,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        SettingsOverlay(
          appLanguage = appLanguage,
          onSelectLanguage = { viewModel.setAppLanguage(it) },
          weatherProvider = weatherProvider,
          onSelectWeatherProvider = { viewModel.selectWeatherProvider(it) },
          inactivityRemainingSec = settingsRemainingSec,
          inactivityTotalSec = settingsTimeoutSec,
          weatherLocation = weatherLocation,
          weatherToggles = weatherToggles,
          savedWeatherLocations = savedWeatherLocations,
          locationSearchResults = locationSearchResults,
          isSearchingLocations = isSearchingLocations,
          locationSearchError = locationSearchError,
          cameras = cameras,
          cameraDisplayCount = cameraDisplayCount,
          onSelectCameraDisplayCount = { viewModel.setCameraDisplayCount(it) },
          discoveredCameras = discoveredCameras,
          isScanningOnvif = isScanningOnvif,
          onvifStatusMessage = onvifStatusMessage,
          onCameraTabChanged = { isCamTab -> viewModel.setSettingsCameraTabActive(isCamTab) },
          onUserActivity = { viewModel.resetSettingsInactivityTimer() },
          onClose = { viewModel.closeSettings() },
          onSearchLocations = { viewModel.searchLocations(it) },
          onSelectLocation = { viewModel.selectWeatherLocation(it) },
          onSaveWeatherLocation = { viewModel.saveWeatherCity(it) },
          onRemoveSavedWeatherLocation = { viewModel.removeSavedWeatherCity(it) },
          onUpdateWeatherToggles = { viewModel.updateWeatherToggles(it) },
          onScanOnvif = { viewModel.scanOnvifCameras() },
          onAssignDiscoveredCamera = { cam, idx, user, pass, ch ->
            viewModel.assignDiscoveredCameraToSlot(cam, idx, user, pass, ch)
          },
          onAssignAllFourNvrChannels = { cam, user, pass, startCh ->
            viewModel.assignAllFourNvrChannels(cam, user, pass, startCh)
          },
          onConnectManualNvr = { ip, preset, port, user, pass, channels, onDone ->
            viewModel.connectManualNvrChannels(ip, preset, port, user, pass, channels, onDone)
          },
          onImportFromGo2Rtc = { host, apiPort, rtspPort, onDone ->
            viewModel.importStreamsFromGo2Rtc(host, apiPort, rtspPort, onDone)
          },
          onRegisterEseeCloudInGo2Rtc = { goHost, apiPort, rtspPort, nvrIp, nvrPort, user, pass, channels, quality, onDone ->
            viewModel.registerEseeCloudInGo2Rtc(goHost, apiPort, rtspPort, nvrIp, nvrPort, user, pass, channels, quality, onDone)
          },
          onRegisterTuyaInGo2Rtc = { goHost, apiPort, rtspPort, targetSlot, streamName, region, authMode, deviceId, email, pass, uid, clientId, clientSecret, useSub, onDone ->
            viewModel.registerTuyaInGo2Rtc(
              go2RtcHost = goHost,
              apiPort = apiPort,
              rtspPort = rtspPort,
              targetSlotIndex = targetSlot,
              streamName = streamName,
              region = region,
              authMode = authMode,
              deviceId = deviceId,
              email = email,
              password = pass,
              uid = uid,
              clientId = clientId,
              clientSecret = clientSecret,
              useSubStream = useSub,
              onCompleted = onDone,
            )
          },
          onSaveCameraSlot = { viewModel.saveCameraSlot(it) },
          onClearCameraSlot = { viewModel.clearCameraSlot(it) },
          onMoveCameraSlot = { from, to -> viewModel.moveCameraSlot(from, to) },
          radioSearchResults = radioSearchResults,
          isSearchingRadios = isSearchingRadios,
          radioSearchError = radioSearchError,
          favoriteRadios = favoriteRadios,
          onSearchRadios = { viewModel.searchInternetRadios(it) },
          onLoadCzechRadios = { viewModel.loadCzechInternetRadios() },
          onToggleFavoriteRadio = { viewModel.toggleFavoriteRadio(it) },
          onRemoveFavoriteRadio = { viewModel.removeFavoriteRadio(it) },
          displaySettings = displaySettings,
          displayRuntimeStatus = displayRuntimeStatus,
          onUpdateDisplaySettings = { viewModel.updateDisplaySettings(it) },
          onSetNightPreview = { viewModel.setNightBrightnessPreview(it) },
          appearanceSettings = appearanceSettings,
          batteryStatus = batteryStatus,
          onUpdateAppearanceSettings = { viewModel.updateAppearanceSettings(it) },
          recordingStorageSettings = recordingStorageSettings,
          recordingStatuses = recordingStatuses,
          recordedSegments = recordedSegments,
          recordingStorageStats = recordingStorageStats,
          onSelectRecordingStorageLocation = { viewModel.updateRecordingStorageLocation(it) },
          onSelectCustomSafFolder = { uri, label -> viewModel.setCustomRecordingFolder(uri, label) },
          onSelectRecordingFolder = { loc, uri, label -> viewModel.setCustomRecordingFolder(uri, label, loc) },
          onRefreshRecordings = { viewModel.refreshRecordings() },
          onDeleteRecordedSegment = { viewModel.deleteRecordedSegment(it) },
          onClearCameraRecordings = { viewModel.clearCameraRecordings(it) },
        )
      }
    }
  }
}

@Composable
private fun ClockAndMenuColumn(
  timeText: String,
  dateText: String,
  appearanceSettings: AppearanceSettings,
  batteryStatus: BatteryRuntimeStatus,
  onMenuClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  val shape = RoundedCornerShape(12.dp)
  val clockStyle = ClockStylePreset.fromId(appearanceSettings.clockStyleId)
  val displayedTime =
    if (appearanceSettings.showClockSeconds) timeText else timeText.take(5)

  BoxWithConstraints(
    modifier =
      modifier
        .clip(shape)
        .background(palette.surface)
        .border(1.dp, palette.surfaceBorder, shape)
        .padding(horizontal = 6.dp, vertical = 6.dp),
  ) {
    val colScale = minOf(maxWidth.value / 140f, maxHeight.value / 155f).coerceIn(0.68f, 1.4f)
    val baseTimeSp =
      when (clockStyle) {
        ClockStylePreset.MODERN_LARGE ->
          if (appearanceSettings.showClockSeconds) 25f else 31f
        ClockStylePreset.PILL_FRAMED ->
          if (appearanceSettings.showClockSeconds) 21f else 26f
        else ->
          if (appearanceSettings.showClockSeconds) 23f else 28f
      }
    val timeFontSize = (baseTimeSp * colScale).coerceIn(15f, 44f).sp
    val baseDateSp = if (clockStyle == ClockStylePreset.PILL_FRAMED) 16.5f else 18.5f
    val dateFontSize = (baseDateSp * colScale).coerceIn(11.5f, 26f).sp

    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // Current Time & Date above the menu button (styled per Appearance settings)
      Box(
        modifier =
          Modifier.weight(1f)
            .fillMaxWidth()
            .then(
              if (clockStyle == ClockStylePreset.PILL_FRAMED) {
                Modifier.padding(bottom = 4.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(palette.surfaceElevated)
                  .border(1.dp, palette.accentBright.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                  .padding(horizontal = 4.dp, vertical = 3.dp)
              } else {
                Modifier
              }
            ),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          val timeFont =
            when (clockStyle) {
              ClockStylePreset.MODERN_LARGE -> FontFamily.SansSerif
              else -> JetBrainsMonoFamily
            }
          val timeColor =
            when (clockStyle) {
              ClockStylePreset.ACCENT_TINT -> palette.accentBright
              else -> DashTextPrimary
            }

          Text(
            text = displayedTime,
            fontFamily = timeFont,
            fontWeight = FontWeight.Bold,
            fontSize = timeFontSize,
            lineHeight = (timeFontSize.value + 3f).sp,
            color = timeColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag("dashboard_clock"),
          )

          if (clockStyle == ClockStylePreset.ACCENT_TINT) {
            Spacer(Modifier.height(3.dp))
            Box(
              modifier =
                Modifier.width((42f * colScale).dp)
                  .height(2.dp)
                  .clip(RoundedCornerShape(1.dp))
                  .background(palette.accentBright)
            )
            Spacer(Modifier.height(3.dp))
          } else {
            Spacer(Modifier.height(3.dp))
          }

          Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            fontSize = dateFontSize,
            lineHeight = (dateFontSize.value + 3f).sp,
            fontWeight = FontWeight.SemiBold,
            color = DashTextPrimary.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.testTag("dashboard_date"),
          )
        }
      }

      // Bottom Menu Button: aligned LEFT when battery indicator is active beside it; CENTERED otherwise
      if (appearanceSettings.showBatteryStatus) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          MenuTriggerButton(
            accent = palette.accent,
            accentBright = palette.accentBright,
            onClick = onMenuClick,
            modifier = Modifier.width(50.dp).height(48.dp),
          )
          Spacer(Modifier.width(5.dp))
          BatteryStatusBadge(
            batteryStatus = batteryStatus,
            modifier = Modifier.weight(1f).height(48.dp),
          )
        }
      } else {
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          MenuTriggerButton(
            accent = palette.accent,
            accentBright = palette.accentBright,
            onClick = onMenuClick,
            modifier = Modifier.width((76f * colScale).coerceIn(56f, 110f).dp).height(48.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun MenuTriggerButton(
  accent: Color,
  accentBright: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .shadow(4.dp, RoundedCornerShape(8.dp))
        .clip(RoundedCornerShape(8.dp))
        .background(Brush.verticalGradient(listOf(accentBright, accent)))
        .clickable(onClick = onClick)
        .testTag("menu_button"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(5.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      repeat(3) {
        Box(
          modifier =
            Modifier.width(26.dp)
              .height(3.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(Color.White)
        )
      }
    }
  }
}

@Composable
private fun BatteryStatusBadge(
  batteryStatus: BatteryRuntimeStatus,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  val pct = batteryStatus.levelPercent.coerceIn(0, 100)
  val fillColor =
    when {
      batteryStatus.isCharging -> LiveEmerald
      pct <= 15 -> OfflineRed
      pct <= 35 -> TempAmber
      else -> LiveEmerald
    }

  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(palette.surfaceElevated)
        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
        .padding(horizontal = 5.dp, vertical = 4.dp)
        .testTag("battery_status_badge"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Canvas(modifier = Modifier.width(26.dp).height(13.dp)) {
        val bodyWidth = size.width - 3.dp.toPx()
        val bodyHeight = size.height
        val strokePx = 1.4.dp.toPx()
        drawRoundRect(
          color = DashTextPrimary.copy(alpha = 0.85f),
          topLeft = Offset(0f, 0f),
          size = Size(bodyWidth, bodyHeight),
          cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
          style = Stroke(width = strokePx),
        )
        // Battery cap nub
        drawRoundRect(
          color = DashTextPrimary.copy(alpha = 0.85f),
          topLeft = Offset(bodyWidth + 0.8.dp.toPx(), bodyHeight * 0.27f),
          size = Size(2.2.dp.toPx(), bodyHeight * 0.46f),
          cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )
        // Fill bar inside
        val pad = strokePx + 1.dp.toPx()
        val maxFillW = (bodyWidth - pad * 2f).coerceAtLeast(1f)
        val fillW = maxFillW * (pct / 100f)
        if (fillW > 0f) {
          drawRoundRect(
            color = fillColor,
            topLeft = Offset(pad, pad),
            size = Size(fillW, (bodyHeight - pad * 2f).coerceAtLeast(1f)),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
          )
        }
      }
      if (batteryStatus.isCharging) {
        Icon(
          imageVector = Icons.Rounded.Bolt,
          contentDescription = null,
          tint = TempAmber,
          modifier = Modifier.size(14.dp),
        )
      }
    }
    Spacer(Modifier.height(1.dp))
    Text(
      text = "$pct%",
      fontFamily = JetBrainsMonoFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 11.sp,
      lineHeight = 13.sp,
      color = DashTextPrimary,
      maxLines = 1,
      softWrap = false,
    )
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

