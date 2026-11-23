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

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(palette.background)
          .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
      // Main Landscape Dashboard Layout
      Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // TOP PART: 4 online cameras in a row
        Row(
          modifier = Modifier.fillMaxWidth().weight(0.54f),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          cameras.forEach { slot ->
            CameraTile(
              slot = slot,
              isFullscreen = false,
              onClick = { viewModel.openCameraFullscreen(slot.slotIndex) },
              modifier = Modifier.weight(1f).fillMaxHeight(),
            )
          }
        }

        // BOTTOM PART: Left (Date/Time + Menu/Battery) + Center (3-day Weather) + Right (Radio)
        Row(
          modifier = Modifier.fillMaxWidth().weight(0.46f),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Bottom-left control column: Date/Time above menu button (and optional battery badge)
          ClockAndMenuColumn(
            timeText = clockTime,
            dateText = clockDate,
            appearanceSettings = appearanceSettings,
            batteryStatus = batteryStatus,
            onMenuClick = { viewModel.openSettings() },
            modifier = Modifier.width(144.dp).fillMaxHeight(),
          )

          // Bottom-center: Passive, non-interactive 3-day Weather Forecast
          AladinWeatherPanel(
            weatherState = weatherState,
            toggles = weatherToggles,
            locationName = weatherLocation.name,
            activeProvider = weatherProvider,
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )

          // Bottom-right: Online Radio (Presets + saved favorites)
          OnlineRadioPanel(
            radioState = radioState,
            availableStations = availableRadioStations,
            onSelectStation = { viewModel.selectActiveRadioStation(it) },
            onTogglePlayStop = { viewModel.toggleRadioPlayStop() },
            onVolumeUp = { viewModel.increaseRadioVolume() },
            onVolumeDown = { viewModel.decreaseRadioVolume() },
            modifier = Modifier.width(172.dp).fillMaxHeight(),
          )
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

  Column(
    modifier =
      modifier
        .clip(shape)
        .background(palette.surface)
        .border(1.dp, palette.surfaceBorder, shape)
        .padding(horizontal = 8.dp, vertical = 8.dp),
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
              Modifier.padding(bottom = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surfaceElevated)
                .border(1.dp, palette.accentBright.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp)
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
        val timeFontSize =
          when (clockStyle) {
            ClockStylePreset.MODERN_LARGE ->
              if (appearanceSettings.showClockSeconds) 26.sp else 32.sp
            ClockStylePreset.PILL_FRAMED ->
              if (appearanceSettings.showClockSeconds) 22.sp else 27.sp
            else ->
              if (appearanceSettings.showClockSeconds) 24.sp else 29.sp
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
          lineHeight = (timeFontSize.value + 4f).sp,
          color = timeColor,
          textAlign = TextAlign.Center,
          maxLines = 1,
          modifier = Modifier.testTag("dashboard_clock"),
        )

        if (clockStyle == ClockStylePreset.ACCENT_TINT) {
          Spacer(Modifier.height(4.dp))
          Box(
            modifier =
              Modifier.width(42.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(palette.accentBright)
          )
          Spacer(Modifier.height(4.dp))
        } else {
          Spacer(Modifier.height(4.dp))
        }

        Text(
          text = dateText,
          style = MaterialTheme.typography.titleMedium,
          fontSize = if (clockStyle == ClockStylePreset.PILL_FRAMED) 17.sp else 19.sp,
          lineHeight = 22.sp,
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
          modifier = Modifier.width(56.dp).height(48.dp),
        )
        Spacer(Modifier.width(6.dp))
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
          modifier = Modifier.width(76.dp).height(48.dp),
        )
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

