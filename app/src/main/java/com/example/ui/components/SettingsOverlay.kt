package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.Go2RtcClient
import com.example.model.AppLanguage
import com.example.model.AppearanceSettings
import com.example.model.BatteryRuntimeStatus
import com.example.model.CameraSlot
import com.example.model.DiscoveredNetworkCamera
import com.example.model.DisplayRuntimeStatus
import com.example.model.DisplaySettings
import com.example.model.FavoriteRadioStation
import com.example.model.NvrProtocolPreset
import com.example.model.RadioDirectoryStation
import com.example.model.RtspPathPreset
import com.example.model.WeatherLocation
import com.example.model.WeatherMetricToggle
import com.example.model.WeatherProvider
import com.example.ui.theme.DashSurface
import com.example.ui.theme.DashSurfaceBorder
import com.example.ui.theme.DashSurfaceElevated
import com.example.ui.theme.DashTextMuted
import com.example.ui.theme.DashTextPrimary
import com.example.ui.theme.DashTextSecondary
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalDashPalette
import com.example.ui.theme.LocalWeatherGraphPalette
import com.example.ui.theme.MenuBlue
import com.example.ui.theme.MenuBlueBright
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.tr

@Composable
fun SettingsOverlay(
  appLanguage: AppLanguage = AppLanguage.CS,
  onSelectLanguage: (AppLanguage) -> Unit = {},
  weatherProvider: WeatherProvider = WeatherProvider.ALADIN,
  onSelectWeatherProvider: (WeatherProvider) -> Unit = {},
  inactivityRemainingSec: Int,
  inactivityTotalSec: Int,
  weatherLocation: WeatherLocation,
  weatherToggles: WeatherMetricToggle,
  savedWeatherLocations: List<WeatherLocation> = emptyList(),
  locationSearchResults: List<WeatherLocation>,
  isSearchingLocations: Boolean,
  locationSearchError: String?,
  cameras: List<CameraSlot>,
  discoveredCameras: List<DiscoveredNetworkCamera>,
  isScanningOnvif: Boolean,
  onvifStatusMessage: String?,
  onCameraTabChanged: (Boolean) -> Unit,
  onUserActivity: () -> Unit,
  onClose: () -> Unit,
  onSearchLocations: (String) -> Unit,
  onSelectLocation: (WeatherLocation) -> Unit,
  onSaveWeatherLocation: (WeatherLocation) -> Unit = {},
  onRemoveSavedWeatherLocation: (WeatherLocation) -> Unit = {},
  onUpdateWeatherToggles: ((WeatherMetricToggle) -> WeatherMetricToggle) -> Unit,
  onScanOnvif: () -> Unit,
  onAssignDiscoveredCamera: (DiscoveredNetworkCamera, Int, String, String, Int) -> Unit,
  onAssignAllFourNvrChannels: (DiscoveredNetworkCamera, String, String, Int) -> Unit,
  onConnectManualNvr: (String, NvrProtocolPreset, Int, String, String, List<Int>, (Boolean, String) -> Unit) -> Unit,
  onImportFromGo2Rtc: (String, Int, Int, (Boolean, String) -> Unit) -> Unit,
  onRegisterEseeCloudInGo2Rtc: (String, Int, Int, String, Int, String, String, List<Int>, Int, (Boolean, String) -> Unit) -> Unit,
  onSaveCameraSlot: (CameraSlot) -> Unit,
  onClearCameraSlot: (Int) -> Unit,
  onMoveCameraSlot: (Int, Int) -> Unit,
  radioSearchResults: List<RadioDirectoryStation> = emptyList(),
  isSearchingRadios: Boolean = false,
  radioSearchError: String? = null,
  favoriteRadios: List<FavoriteRadioStation> = emptyList(),
  onSearchRadios: (String) -> Unit = {},
  onLoadCzechRadios: () -> Unit = {},
  onToggleFavoriteRadio: (RadioDirectoryStation) -> Unit = {},
  onRemoveFavoriteRadio: (String) -> Unit = {},
  displaySettings: DisplaySettings = DisplaySettings(),
  displayRuntimeStatus: DisplayRuntimeStatus = DisplayRuntimeStatus(),
  onUpdateDisplaySettings: ((DisplaySettings) -> DisplaySettings) -> Unit = {},
  onSetNightPreview: (Boolean) -> Unit = {},
  appearanceSettings: AppearanceSettings = AppearanceSettings(),
  batteryStatus: BatteryRuntimeStatus = BatteryRuntimeStatus(),
  onUpdateAppearanceSettings: ((AppearanceSettings) -> AppearanceSettings) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onClose)

  val palette = LocalDashPalette.current
  val lang = appLanguage
  // 0 = Weather, 1 = Cameras, 2 = Radio, 3 = Display, 4 = Appearance
  var selectedTab by remember { mutableIntStateOf(0) }
  var editingSlot by remember { mutableStateOf<CameraSlot?>(null) }
  var showDiscoveryDialog by remember { mutableStateOf(false) }
  var showNvrDialog by remember { mutableStateOf(false) }
  var showGo2RtcDialog by remember { mutableStateOf(false) }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(palette.background.copy(alpha = 0.96f))
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              awaitPointerEvent(PointerEventPass.Initial)
              onUserActivity()
            }
          }
        }
        .padding(12.dp)
        .testTag("settings_overlay")
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .clip(RoundedCornerShape(14.dp))
          .background(palette.surface)
          .border(1.dp, palette.surfaceBorder, RoundedCornerShape(14.dp))
          .padding(10.dp)
    ) {
      // Top Settings Header Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          TabSelectorButton(
            selected = selectedTab == 0,
            icon = Icons.Rounded.WbSunny,
            label = tr(lang, "Počasí", "Weather"),
            testTag = "tab_weather",
            onClick = {
              selectedTab = 0
              onSetNightPreview(false)
              onCameraTabChanged(false)
            },
          )
          TabSelectorButton(
            selected = selectedTab == 1,
            icon = Icons.Rounded.Videocam,
            label = tr(lang, "Kamery", "Cameras"),
            testTag = "tab_cameras",
            onClick = {
              selectedTab = 1
              onSetNightPreview(false)
              onCameraTabChanged(true)
            },
          )
          TabSelectorButton(
            selected = selectedTab == 2,
            icon = Icons.Rounded.Radio,
            label = tr(lang, "Rádio", "Radio"),
            testTag = "tab_radio",
            onClick = {
              selectedTab = 2
              onSetNightPreview(false)
              onCameraTabChanged(true)
            },
          )
          TabSelectorButton(
            selected = selectedTab == 3,
            icon = Icons.Rounded.Brightness6,
            label = tr(lang, "Displej", "Display"),
            testTag = "tab_display",
            onClick = {
              selectedTab = 3
              onCameraTabChanged(true)
            },
          )
          TabSelectorButton(
            selected = selectedTab == 4,
            icon = Icons.Rounded.Palette,
            label = tr(lang, "Vzhled", "Appearance"),
            testTag = "tab_appearance",
            onClick = {
              selectedTab = 4
              onSetNightPreview(false)
              onCameraTabChanged(true)
            },
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Instant CZ / ENG Language Switcher in Opening Menu Header
          LanguageSwitcherPill(
            selectedLanguage = lang,
            onSelectLanguage = { targetLang ->
              onUserActivity()
              onSelectLanguage(targetLang)
            },
          )

          // Compact Inactivity Countdown Pill
          Column(
            modifier =
              Modifier.width(54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceElevated)
                .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 5.dp, vertical = 4.dp)
                .testTag("settings_inactivity_pill"),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = tr(lang, "Automatický návrat", "Auto return"),
                tint = palette.accentBright,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = "${inactivityRemainingSec}s",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DashTextPrimary,
                maxLines = 1,
                softWrap = false,
              )
            }
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator(
              progress = {
                val denom = inactivityTotalSec.coerceAtLeast(1).toFloat()
                (inactivityRemainingSec.toFloat() / denom).coerceIn(0f, 1f)
              },
              modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
              color = palette.accentBright,
              trackColor = palette.surfaceBorder,
            )
          }

          Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(48.dp).testTag("settings_close_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = tr(lang, "Zavřít nastavení", "Close settings"),
              modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(tr(lang, "Zpět", "Back"), maxLines = 1, softWrap = false)
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      when (selectedTab) {
        0 -> {
          AladinSettingsTab(
            weatherProvider = weatherProvider,
            onSelectWeatherProvider = onSelectWeatherProvider,
            currentLocation = weatherLocation,
            savedLocations = savedWeatherLocations,
            toggles = weatherToggles,
            searchResults = locationSearchResults,
            isSearching = isSearchingLocations,
            searchError = locationSearchError,
            onUserActivity = onUserActivity,
            onSearch = onSearchLocations,
            onSelectLocation = onSelectLocation,
            onSaveLocation = onSaveWeatherLocation,
            onRemoveSavedLocation = onRemoveSavedWeatherLocation,
            onUpdateToggles = onUpdateWeatherToggles,
            modifier = Modifier.fillMaxSize(),
          )
        }
        1 -> {
          CameraSettingsTab(
            cameras = cameras,
            discoveredCount = discoveredCameras.size,
            isScanning = isScanningOnvif,
            statusMessage = onvifStatusMessage,
            onOpenDiscoveryDialog = {
              onUserActivity()
              showDiscoveryDialog = true
              if (discoveredCameras.isEmpty() && !isScanningOnvif) {
                onScanOnvif()
              }
            },
            onOpenNvrDialog = {
              onUserActivity()
              showNvrDialog = true
            },
            onOpenGo2RtcDialog = {
              onUserActivity()
              showGo2RtcDialog = true
            },
            onEditSlot = { slot ->
              onUserActivity()
              editingSlot = slot
            },
            onClearSlot = onClearCameraSlot,
            onMoveSlot = onMoveCameraSlot,
            modifier = Modifier.fillMaxSize(),
          )
        }
        2 -> {
          RadioSettingsTab(
            searchResults = radioSearchResults,
            isSearching = isSearchingRadios,
            searchError = radioSearchError,
            favoriteRadios = favoriteRadios,
            onUserActivity = onUserActivity,
            onSearchRadios = onSearchRadios,
            onLoadCzechRadios = onLoadCzechRadios,
            onToggleFavorite = onToggleFavoriteRadio,
            onRemoveFavorite = onRemoveFavoriteRadio,
            modifier = Modifier.fillMaxSize(),
          )
        }
        3 -> {
          DisplaySettingsTab(
            settings = displaySettings,
            runtimeStatus = displayRuntimeStatus,
            onUserActivity = onUserActivity,
            onUpdateSettings = onUpdateDisplaySettings,
            onSetNightPreview = onSetNightPreview,
            modifier = Modifier.fillMaxSize(),
          )
        }
        else -> {
          AppearanceSettingsTab(
            settings = appearanceSettings,
            batteryStatus = batteryStatus,
            onUserActivity = onUserActivity,
            onUpdateSettings = onUpdateAppearanceSettings,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }

    if (showDiscoveryDialog) {
      CameraDiscoveryFullscreenDialog(
        discoveredCameras = discoveredCameras,
        isScanning = isScanningOnvif,
        statusMessage = onvifStatusMessage,
        initialUsername = cameras.firstOrNull { it.username.isNotBlank() }?.username ?: "admin",
        initialPassword = cameras.firstOrNull { it.password.isNotBlank() }?.password ?: "",
        onUserActivity = onUserActivity,
        onScanOnvif = onScanOnvif,
        onAssignDiscovered = onAssignDiscoveredCamera,
        onAssignAllFourNvr = onAssignAllFourNvrChannels,
        onDismiss = {
          onUserActivity()
          showDiscoveryDialog = false
        },
      )
    }

    editingSlot?.let { slot ->
      CameraEditDialog(
        slot = slot,
        onUserActivity = onUserActivity,
        onDismiss = {
          onUserActivity()
          editingSlot = null
        },
        onSave = { updated ->
          onSaveCameraSlot(updated)
          editingSlot = null
        },
      )
    }

    if (showNvrDialog) {
      val suggestedIp =
        discoveredCameras.firstOrNull { it.isNvr }?.ipAddress
          ?: cameras.firstOrNull { it.onvifDeviceAddress.isNotBlank() }?.onvifDeviceAddress
          ?: ""
      NvrSetupDialog(
        initialIp = suggestedIp,
        onUserActivity = onUserActivity,
        onDismiss = {
          onUserActivity()
          showNvrDialog = false
        },
        onConnect = onConnectManualNvr,
      )
    }

    if (showGo2RtcDialog) {
      val suggestedIp =
        discoveredCameras.firstOrNull { it.isNvr }?.ipAddress
          ?: cameras.firstOrNull { it.onvifDeviceAddress.isNotBlank() && it.onvifDeviceAddress != "127.0.0.1" }?.onvifDeviceAddress
          ?: ""
      Go2RtcBridgeDialog(
        initialNvrIp = suggestedIp,
        onUserActivity = onUserActivity,
        onDismiss = {
          onUserActivity()
          showGo2RtcDialog = false
        },
        onImportStreams = onImportFromGo2Rtc,
        onRegisterEseeCloud = onRegisterEseeCloudInGo2Rtc,
      )
    }
  }
}

@Composable
private fun LanguageSwitcherPill(
  selectedLanguage: AppLanguage,
  onSelectLanguage: (AppLanguage) -> Unit,
) {
  val palette = LocalDashPalette.current
  Row(
    modifier =
      Modifier.height(48.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(palette.surfaceElevated)
        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
        .padding(3.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    AppLanguage.entries.forEach { option ->
      val isSelected = selectedLanguage == option
      Box(
        modifier =
          Modifier.fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .background(if (isSelected) palette.accent else Color.Transparent)
            .clickable { onSelectLanguage(option) }
            .padding(horizontal = 9.dp)
            .testTag("lang_switch_${option.id}"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = option.shortLabel,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = if (isSelected) Color.White else DashTextSecondary,
          maxLines = 1,
          softWrap = false,
        )
      }
    }
  }
}

@Composable
private fun TabSelectorButton(
  selected: Boolean,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  testTag: String,
  onClick: () -> Unit,
) {
  val palette = LocalDashPalette.current
  val bg = if (selected) palette.accent else palette.surfaceElevated
  val border = if (selected) palette.accentBright else palette.surfaceBorder
  Row(
    modifier =
      Modifier.height(48.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(bg)
        .border(1.dp, border, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = Color.White,
      maxLines = 1,
      softWrap = false,
    )
  }
}

@Composable
private fun AladinSettingsTab(
  weatherProvider: WeatherProvider,
  onSelectWeatherProvider: (WeatherProvider) -> Unit,
  currentLocation: WeatherLocation,
  savedLocations: List<WeatherLocation>,
  toggles: WeatherMetricToggle,
  searchResults: List<WeatherLocation>,
  isSearching: Boolean,
  searchError: String?,
  onUserActivity: () -> Unit,
  onSearch: (String) -> Unit,
  onSelectLocation: (WeatherLocation) -> Unit,
  onSaveLocation: (WeatherLocation) -> Unit,
  onRemoveSavedLocation: (WeatherLocation) -> Unit,
  onUpdateToggles: ((WeatherMetricToggle) -> WeatherMetricToggle) -> Unit,
  modifier: Modifier = Modifier,
) {
  val lang = LocalAppLanguage.current
  val palette = LocalDashPalette.current
  val weatherPalette = LocalWeatherGraphPalette.current
  var searchQuery by remember { mutableStateOf("") }

  fun isLocationSaved(loc: WeatherLocation): Boolean =
    savedLocations.any {
      it.name == loc.name && it.latitude == loc.latitude && it.longitude == loc.longitude
    }

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Left column: Provider switcher, worldwide search, saved cities & presets
    Column(
      modifier =
        Modifier.weight(1.2f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(10.dp))
          .background(palette.surfaceElevated.copy(alpha = 0.6f))
          .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
          .padding(10.dp)
    ) {
      // Provider Selector Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(Icons.Rounded.Public, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(16.dp))
        Text(
          text = tr(lang, "Poskytovatel dat:", "Data provider:"),
          style = MaterialTheme.typography.labelMedium,
          color = DashTextPrimary,
          fontWeight = FontWeight.SemiBold,
        )
        Row(
          modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          WeatherProvider.entries.forEach { provider ->
            val selected = weatherProvider == provider
            FilterChip(
              selected = selected,
              onClick = {
                onUserActivity()
                onSelectWeatherProvider(provider)
              },
              label = {
                Text(
                  text = provider.labelFor(lang),
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                )
              },
              leadingIcon =
                if (selected) {
                  { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                } else null,
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = palette.accent,
                  selectedLabelColor = Color.White,
                  containerColor = palette.surface,
                  labelColor = DashTextSecondary,
                ),
              modifier = Modifier.testTag("weather_provider_${provider.id}"),
            )
          }
        }
      }

      Spacer(Modifier.height(4.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(18.dp))
        Text(tr(lang, "Místo předpovědi", "Forecast location"), style = MaterialTheme.typography.titleMedium, color = DashTextPrimary)
        Spacer(Modifier.weight(1f))
        Text(
          text = "${tr(lang, "Aktuální", "Current")}: ${currentLocation.name}",
          style = MaterialTheme.typography.labelMedium,
          color = LiveEmerald,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!isLocationSaved(currentLocation)) {
          IconButton(
            onClick = { onSaveLocation(currentLocation) },
            modifier = Modifier.size(40.dp).testTag("save_current_city_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.BookmarkAdd,
              contentDescription = tr(lang, "Uložit aktuální město", "Save current city"),
              tint = palette.accentBright,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      Spacer(Modifier.height(6.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = {
            onUserActivity()
            searchQuery = it
          },
          placeholder = {
            Text(
              tr(
                lang,
                "Hledat město kdekoliv na světě (např. London, Tokyo, Praha)",
                "Search any city worldwide (e.g. London, Tokyo, Prague)",
              ),
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          singleLine = true,
          colors = textFieldColors(),
          modifier = Modifier.weight(1f).testTag("location_search_input"),
        )
        Button(
          onClick = { onSearch(searchQuery) },
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
          modifier = Modifier.height(52.dp).testTag("location_search_button"),
        ) {
          if (isSearching) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
          } else {
            Icon(Icons.Rounded.Search, contentDescription = tr(lang, "Hledat", "Search"))
            Spacer(Modifier.width(4.dp))
            Text(tr(lang, "Hledat", "Search"))
          }
        }
      }

      Spacer(Modifier.height(6.dp))

      // Saved user cities section (can select or remove)
      if (savedLocations.isNotEmpty()) {
        Text(
          text = "${tr(lang, "Uložená města", "Saved cities")} (${savedLocations.size})",
          style = MaterialTheme.typography.labelSmall,
          color = DashTextSecondary,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          savedLocations.forEach { saved ->
            val isSelected =
              saved.name == currentLocation.name &&
                saved.latitude == currentLocation.latitude &&
                saved.longitude == currentLocation.longitude
            Row(
              modifier =
                Modifier.height(38.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isSelected) palette.accent else palette.surface)
                  .border(
                    1.dp,
                    if (isSelected) palette.accentBright else palette.surfaceBorder,
                    RoundedCornerShape(8.dp),
                  )
                  .clickable { onSelectLocation(saved) }
                  .padding(start = 10.dp, end = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = saved.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) Color.White else DashTextPrimary,
                maxLines = 1,
              )
              IconButton(
                onClick = { onRemoveSavedLocation(saved) },
                modifier = Modifier.size(32.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = tr(lang, "Odebrat město ${saved.name}", "Remove city ${saved.name}"),
                  tint = if (isSelected) Color.White else OfflineRed,
                  modifier = Modifier.size(15.dp),
                )
              }
            }
          }
        }
        Spacer(Modifier.height(6.dp))
      }

      // Quick Worldwide + Czech Presets
      val combinedPresets =
        remember(weatherProvider) {
          if (weatherProvider == WeatherProvider.ALADIN) {
            WeatherLocation.CzechPresets + WeatherLocation.GlobalPresets
          } else {
            WeatherLocation.GlobalPresets + WeatherLocation.CzechPresets
          }
        }
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        combinedPresets.forEach { preset ->
          val isSelected = preset.name == currentLocation.name
          FilterChip(
            selected = isSelected,
            onClick = { onSelectLocation(preset) },
            label = { Text(preset.name, style = MaterialTheme.typography.bodyMedium) },
            leadingIcon =
              if (isSelected) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
              } else null,
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = palette.accent,
                selectedLabelColor = Color.White,
                containerColor = palette.surface,
                labelColor = DashTextSecondary,
              ),
          )
        }
      }

      Spacer(Modifier.height(6.dp))

      if (searchError != null) {
        Text(
          text = searchError,
          style = MaterialTheme.typography.bodyMedium,
          color = OfflineRed,
          modifier = Modifier.padding(vertical = 4.dp),
        )
      }

      LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(searchResults) { loc ->
          val isCurrent = loc.name == currentLocation.name && loc.latitude == currentLocation.latitude
          val isSaved = isLocationSaved(loc)
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCurrent) palette.accent.copy(alpha = 0.25f) else palette.surface)
                .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
                .clickable { onSelectLocation(loc) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(loc.name, style = MaterialTheme.typography.bodyLarge, color = DashTextPrimary, fontWeight = FontWeight.SemiBold)
              Text(loc.region, style = MaterialTheme.typography.bodyMedium, color = DashTextSecondary)
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              OutlinedButton(
                onClick = {
                  if (isSaved) onRemoveSavedLocation(loc) else onSaveLocation(loc)
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(38.dp),
              ) {
                Icon(
                  imageVector = if (isSaved) Icons.Rounded.Check else Icons.Rounded.BookmarkAdd,
                  contentDescription =
                    if (isSaved) tr(lang, "Uloženo", "Saved") else tr(lang, "Uložit město", "Save city"),
                  tint = if (isSaved) LiveEmerald else palette.accentBright,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                  text = if (isSaved) tr(lang, "Uloženo", "Saved") else tr(lang, "Uložit", "Save"),
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isSaved) LiveEmerald else palette.accentBright,
                )
              }
              Text(
                text = if (isCurrent) tr(lang, "Vybráno", "Selected") else tr(lang, "Vybrat", "Select"),
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) LiveEmerald else palette.accentBright,
              )
            }
          }
        }
      }
    }

    // Right column: Metric display toggles
    Column(
      modifier =
        Modifier.weight(0.8f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(10.dp))
          .background(palette.surfaceElevated.copy(alpha = 0.6f))
          .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
          .padding(10.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(tr(lang, "Zobrazované hodnoty", "Displayed metrics"), style = MaterialTheme.typography.titleMedium, color = DashTextPrimary)

      MetricToggleRow(
        label = tr(lang, "Teplota (°C)", "Temperature (°C)"),
        subtitle = tr(lang, "Hodinová křivka a denní min/max", "Hourly curve & daily min/max"),
        accentColor = weatherPalette.tempWarm,
        checked = toggles.showTemperature,
        testTag = "toggle_temperature",
        onCheckedChange = { checked -> onUpdateToggles { it.copy(showTemperature = checked) } },
      )
      MetricToggleRow(
        label = tr(lang, "Srážky – déšť (mm)", "Rain precipitation (mm)"),
        subtitle = tr(lang, "Sloupcový graf dešťových srážek", "Hourly rainfall bar chart"),
        accentColor = weatherPalette.rainBar,
        checked = toggles.showRain,
        testTag = "toggle_rain",
        onCheckedChange = { checked -> onUpdateToggles { it.copy(showRain = checked) } },
      )
      MetricToggleRow(
        label = tr(lang, "Sněžení (cm)", "Snowfall (cm)"),
        subtitle = tr(lang, "Hodinové úhrny sněhových srážek", "Hourly snowfall amounts"),
        accentColor = weatherPalette.snowBar,
        checked = toggles.showSnow,
        testTag = "toggle_snow",
        onCheckedChange = { checked -> onUpdateToggles { it.copy(showSnow = checked) } },
      )
      MetricToggleRow(
        label = tr(lang, "Tlak (hPa)", "Pressure (hPa)"),
        subtitle = tr(lang, "Přerušovaná křivka atmosférického tlaku", "Dashed atmospheric pressure curve"),
        accentColor = weatherPalette.pressureLine,
        checked = toggles.showPressure,
        testTag = "toggle_pressure",
        onCheckedChange = { checked -> onUpdateToggles { it.copy(showPressure = checked) } },
      )
    }
  }
}

@Composable
private fun MetricToggleRow(
  label: String,
  subtitle: String,
  accentColor: Color,
  checked: Boolean,
  testTag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  val palette = LocalDashPalette.current
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(palette.surface)
        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
        .clickable { onCheckedChange(!checked) }
        .padding(horizontal = 10.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(accentColor))
      Column {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = DashTextPrimary, fontWeight = FontWeight.Medium)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = DashTextSecondary)
      }
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = Color.White,
          checkedTrackColor = palette.accent,
        ),
    )
  }
}

@Composable
private fun CameraSettingsTab(
  cameras: List<CameraSlot>,
  discoveredCount: Int,
  isScanning: Boolean,
  statusMessage: String?,
  onOpenDiscoveryDialog: () -> Unit,
  onOpenNvrDialog: () -> Unit,
  onOpenGo2RtcDialog: () -> Unit,
  onEditSlot: (CameraSlot) -> Unit,
  onClearSlot: (Int) -> Unit,
  onMoveSlot: (Int, Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val lang = LocalAppLanguage.current
  val palette = LocalDashPalette.current
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    // Left column: Fullscreen Network Discovery launch + NVR/go2rtc actions
    Column(
      modifier =
        Modifier.weight(0.95f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(10.dp))
          .background(palette.surfaceElevated.copy(alpha = 0.6f))
          .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
          .padding(10.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(tr(lang, "Vyhledávání kamer v síti", "Network Camera Discovery"), style = MaterialTheme.typography.titleMedium, color = DashTextPrimary)
      Text(
        text =
          tr(
            lang,
            "Otevře samostatné okno přes celou obrazovku pro přehledný výběr nalezených kamer a NVR kanálů (ONVIF, EseeCloud, XMEye, Dahua, RTSP, HTTP).",
            "Opens a dedicated fullscreen browser to inspect discovered LAN cameras and NVR channels (ONVIF, EseeCloud, XMEye, Dahua, RTSP, HTTP).",
          ),
        style = MaterialTheme.typography.bodyMedium,
        color = DashTextSecondary,
      )

      Button(
        onClick = onOpenDiscoveryDialog,
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("open_camera_discovery_button"),
      ) {
        if (isScanning) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
          Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
          text =
            when {
              isScanning -> tr(lang, "Hledám kamery... (Otevřít přehled)", "Scanning cameras... (Open browser)")
              discoveredCount > 0 -> tr(lang, "Vyhledat kamery v síti ($discoveredCount nalezeno)", "Discover LAN cameras ($discoveredCount found)")
              else -> tr(lang, "Vyhledat kamery v síti", "Discover LAN cameras")
            },
          style = MaterialTheme.typography.titleMedium,
          color = Color.White,
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = onOpenNvrDialog,
          modifier = Modifier.weight(1f).height(48.dp).testTag("open_nvr_setup_button"),
        ) {
          Icon(Icons.Rounded.Dns, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(tr(lang, "Přidat NVR (kanály)", "Add NVR (channels)"), style = MaterialTheme.typography.labelMedium, color = DashTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
          onClick = onOpenGo2RtcDialog,
          modifier = Modifier.weight(1f).height(48.dp).testTag("open_go2rtc_button"),
        ) {
          Icon(Icons.Rounded.SettingsEthernet, contentDescription = null, tint = LiveEmerald, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(tr(lang, "go2rtc (Telefon / Termux)", "go2rtc (Phone / Termux)"), style = MaterialTheme.typography.labelMedium, color = DashTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      if (!statusMessage.isNullOrBlank()) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(palette.surface)
              .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
              .padding(10.dp)
        ) {
          Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.accentBright,
          )
        }
      }
    }

    // Right column: Camera Slot Order & Manual Configuration
    Column(
      modifier =
        Modifier.weight(1.05f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(10.dp))
          .background(palette.surfaceElevated.copy(alpha = 0.6f))
          .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
          .padding(10.dp)
    ) {
      Text(tr(lang, "Uspořádání kamer (1–4)", "Camera slots (1–4)"), style = MaterialTheme.typography.titleMedium, color = DashTextPrimary)
      Spacer(Modifier.height(6.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(cameras, key = { it.slotIndex }) { slot ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surface)
                .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
                .clickable { onEditSlot(slot) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier =
                Modifier.size(28.dp)
                  .clip(RoundedCornerShape(6.dp))
                  .background(if (slot.isConfigured) palette.accent else palette.surfaceElevated),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "${slot.slotIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = slot.name,
                style = MaterialTheme.typography.bodyLarge,
                color = DashTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = slot.maskedUrlDisplay(lang),
                style = MaterialTheme.typography.labelSmall,
                color = if (slot.isConfigured) LiveEmerald else DashTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            IconButton(
              onClick = { onMoveSlot(slot.slotIndex, slot.slotIndex - 1) },
              enabled = slot.slotIndex > 0,
            ) {
              Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = tr(lang, "Posunout doleva / nahoru", "Move up / left"),
                tint = if (slot.slotIndex > 0) DashTextPrimary else DashTextMuted,
              )
            }
            IconButton(
              onClick = { onMoveSlot(slot.slotIndex, slot.slotIndex + 1) },
              enabled = slot.slotIndex < 3,
            ) {
              Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = tr(lang, "Posunout doprava / dolů", "Move down / right"),
                tint = if (slot.slotIndex < 3) DashTextPrimary else DashTextMuted,
              )
            }
            IconButton(
              onClick = { onEditSlot(slot) },
              modifier = Modifier.testTag("edit_camera_${slot.slotIndex}"),
            ) {
              Icon(Icons.Rounded.Edit, contentDescription = tr(lang, "Upravit kameru", "Edit camera"), tint = palette.accentBright)
            }
            if (slot.isConfigured) {
              IconButton(onClick = { onClearSlot(slot.slotIndex) }) {
                Icon(Icons.Rounded.Delete, contentDescription = tr(lang, "Vymazat kameru", "Clear camera"), tint = OfflineRed)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CameraEditDialog(
  slot: CameraSlot,
  onUserActivity: () -> Unit,
  onDismiss: () -> Unit,
  onSave: (CameraSlot) -> Unit,
) {
  val lang = LocalAppLanguage.current
  var name by remember { mutableStateOf(slot.name) }
  var ipAddress by remember {
    mutableStateOf(CameraSlot.extractHost(slot.streamUrl, slot.onvifDeviceAddress))
  }
  var portText by remember {
    mutableStateOf(CameraSlot.extractPort(slot.streamUrl, CameraSlot.DEFAULT_MANUAL_RTSP_PORT.toString()))
  }
  var selectedChannel by remember {
    mutableIntStateOf(CameraSlot.extractChannelZeroBased(slot.streamUrl, slot.slotIndex))
  }
  var selectedQuality by remember {
    mutableIntStateOf(CameraSlot.extractStreamQualityIndex(slot.streamUrl, 0))
  }
  var url by remember { mutableStateOf(slot.streamUrl) }
  var username by remember {
    mutableStateOf(
      if (!slot.isConfigured && slot.username.isBlank()) CameraSlot.DEFAULT_MANUAL_USERNAME
      else slot.username
    )
  }
  var password by remember { mutableStateOf(slot.password) }

  fun defaultChannelPath(): String = "/ch${selectedChannel}_${selectedQuality}.264"

  fun rebuildUrlFromIpAndPort(
    newIp: String = ipAddress,
    newPort: String = portText,
    overridePath: String? = null,
    isHttp: Boolean = url.trim().startsWith("http", ignoreCase = true) && overridePath == null,
  ) {
    val activePath =
      overridePath ?: CameraSlot.extractPath(url, defaultChannelPath())
    url =
      CameraSlot.buildManualStreamUrl(
        ipAddress = newIp,
        portText = newPort,
        path = activePath,
        isHttp = isHttp,
      )
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.86f)
          .fillMaxHeight(0.84f)
          .imePadding()
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                onUserActivity()
              }
            }
          },
      shape = RoundedCornerShape(16.dp),
      color = DashSurface,
      border = androidx.compose.foundation.BorderStroke(1.dp, DashSurfaceBorder),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Text(
              tr(lang, "Upravit kameru ${slot.slotIndex + 1}", "Edit camera ${slot.slotIndex + 1}"),
              style = MaterialTheme.typography.titleMedium,
              color = DashTextPrimary,
            )
            Text(
              tr(
                lang,
                "Ruční RTSP: stačí vyplnit IP adresu a kanál (výchozí port 80, jméno admin, bez hesla)",
                "Manual RTSP: just enter IP address and channel (default port 80, user admin, empty password)",
              ),
              style = MaterialTheme.typography.labelSmall,
              color = DashTextSecondary,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = tr(lang, "Zavřít", "Close"), tint = DashTextSecondary)
          }
        }

        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // Left column
          Column(
            modifier = Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = ipAddress,
                onValueChange = {
                  onUserActivity()
                  ipAddress = it
                  rebuildUrlFromIpAndPort(newIp = it)
                },
                label = { Text(tr(lang, "IP adresa", "IP address")) },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.weight(1.5f).testTag("camera_manual_ip_input"),
              )
              OutlinedTextField(
                value = portText,
                onValueChange = {
                  onUserActivity()
                  val digits = it.filter { ch -> ch.isDigit() }.take(5)
                  portText = digits
                  if (ipAddress.isNotBlank()) {
                    rebuildUrlFromIpAndPort(newPort = digits)
                  }
                },
                label = { Text("Port") },
                placeholder = { Text("80") },
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.weight(0.6f).testTag("camera_manual_port_input"),
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = username,
                onValueChange = {
                  onUserActivity()
                  username = it
                },
                label = { Text(tr(lang, "Jméno", "Username")) },
                placeholder = { Text("admin") },
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.weight(1f).testTag("camera_manual_user_input"),
              )
              OutlinedTextField(
                value = password,
                onValueChange = {
                  onUserActivity()
                  password = it
                },
                label = { Text(tr(lang, "Heslo (volitelné)", "Password (optional)")) },
                placeholder = { Text(tr(lang, "Bez hesla", "No password")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = textFieldColors(),
                modifier = Modifier.weight(1f).testTag("camera_manual_pass_input"),
              )
            }

            OutlinedTextField(
              value = name,
              onValueChange = {
                onUserActivity()
                name = it
              },
              label = { Text(tr(lang, "Název kamery", "Camera name")) },
              singleLine = true,
              colors = textFieldColors(),
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = url,
              onValueChange = {
                onUserActivity()
                url = it
                val parsedHost = CameraSlot.extractHost(it)
                if (parsedHost.isNotBlank()) ipAddress = parsedHost
                val parsedPort = CameraSlot.extractPort(it, "")
                if (parsedPort.isNotBlank()) portText = parsedPort
                selectedChannel = CameraSlot.extractChannelZeroBased(it, selectedChannel)
                selectedQuality = CameraSlot.extractStreamQualityIndex(it, selectedQuality)
              },
              label = { Text(tr(lang, "RTSP nebo HTTP URL streamu", "RTSP or HTTP stream URL")) },
              placeholder = { Text("rtsp://192.168.1.50:80/ch0_0.264") },
              singleLine = true,
              colors = textFieldColors(),
              modifier = Modifier.fillMaxWidth().testTag("camera_manual_url_input"),
            )
          }

          // Right column: Manual RTSP preset + Channel K1-K8 + Stream quality + Templates
          Column(
            modifier =
              Modifier.weight(0.9f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(DashSurfaceElevated.copy(alpha = 0.6f))
                .border(1.dp, DashSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = {
                onUserActivity()
                portText = CameraSlot.DEFAULT_MANUAL_RTSP_PORT.toString()
                username = CameraSlot.DEFAULT_MANUAL_USERNAME
                password = ""
                if (ipAddress.isNotBlank()) {
                  rebuildUrlFromIpAndPort(
                    newIp = ipAddress,
                    newPort = CameraSlot.DEFAULT_MANUAL_RTSP_PORT.toString(),
                    overridePath = defaultChannelPath(),
                    isHttp = false,
                  )
                }
              },
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("camera_manual_rtsp_preset_button"),
            ) {
              Text(
                tr(lang, "Výchozí RTSP (port 80, admin, bez hesla)", "Default RTSP (port 80, admin, no pass)"),
                style = MaterialTheme.typography.labelSmall,
                color = MenuBlueBright,
              )
            }

            Text(tr(lang, "Kanál NVR (K1–K8)", "NVR Channel (K1–K8)"), style = MaterialTheme.typography.labelMedium, color = DashTextPrimary)
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              (0..7).forEach { ch ->
                FilterChip(
                  selected = selectedChannel == ch,
                  onClick = {
                    onUserActivity()
                    selectedChannel = ch
                    url =
                      CameraSlot.applyChannelAndQuality(
                        streamUrl = url,
                        fallbackIp = ipAddress,
                        fallbackPort = portText,
                        channelZeroBased = ch,
                        qualityIndex = selectedQuality,
                      )
                    if (ipAddress.isBlank()) ipAddress = CameraSlot.extractHost(url)
                  },
                  label = { Text("K${ch + 1}", style = MaterialTheme.typography.labelSmall) },
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text("Stream:", style = MaterialTheme.typography.labelSmall, color = DashTextSecondary)
              FilterChip(
                selected = selectedQuality == 0,
                onClick = {
                  onUserActivity()
                  selectedQuality = 0
                  url =
                    CameraSlot.applyChannelAndQuality(
                      streamUrl = url,
                      fallbackIp = ipAddress,
                      fallbackPort = portText,
                      channelZeroBased = selectedChannel,
                      qualityIndex = 0,
                    )
                  if (ipAddress.isBlank()) ipAddress = CameraSlot.extractHost(url)
                },
                label = { Text(tr(lang, "Hlavní (_0)", "Main (_0)"), style = MaterialTheme.typography.labelSmall) },
              )
              FilterChip(
                selected = selectedQuality == 1,
                onClick = {
                  onUserActivity()
                  selectedQuality = 1
                  url =
                    CameraSlot.applyChannelAndQuality(
                      streamUrl = url,
                      fallbackIp = ipAddress,
                      fallbackPort = portText,
                      channelZeroBased = selectedChannel,
                      qualityIndex = 1,
                    )
                  if (ipAddress.isBlank()) ipAddress = CameraSlot.extractHost(url)
                },
                label = { Text(tr(lang, "Vedlejší (_1)", "Sub (_1)"), style = MaterialTheme.typography.labelSmall) },
              )
            }

            Text(tr(lang, "Rychlé šablony cesty", "Quick path presets"), style = MaterialTheme.typography.labelMedium, color = DashTextPrimary)
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              val go2RtcPath = "/kam${selectedChannel + 1}"
              FilterChip(
                selected = url.endsWith(go2RtcPath),
                onClick = {
                  onUserActivity()
                  val targetHost = ipAddress.ifBlank { "127.0.0.1" }
                  val targetPort = if (ipAddress.isBlank() && portText == "80") "8554" else portText
                  ipAddress = targetHost
                  portText = targetPort
                  url = CameraSlot.buildManualStreamUrl(targetHost, targetPort, go2RtcPath, isHttp = false)
                },
                label = { Text("go2rtc ($go2RtcPath)", style = MaterialTheme.typography.labelSmall) },
              )
              RtspPathPreset.CommonPresets.forEach { preset ->
                val adjustedPresetPath =
                  CameraSlot.extractPath(
                    CameraSlot.applyChannelAndQuality(
                      streamUrl = "rtsp://host:80${preset.path}",
                      fallbackIp = "host",
                      fallbackPort = "80",
                      channelZeroBased = selectedChannel,
                      qualityIndex = selectedQuality,
                    ),
                    preset.path,
                  ).let { transformed ->
                    if (preset.path == "/stream1" || preset.path == "/unicast" || preset.path == "/capture" || preset.path == "/h264Preview_01_sub") {
                      preset.path
                    } else {
                      transformed
                    }
                  }
                FilterChip(
                  selected = url.endsWith(adjustedPresetPath),
                  onClick = {
                    onUserActivity()
                    val targetHost = ipAddress.ifBlank { "192.168.1.50" }
                    val targetPort =
                      if (preset.isHttp && preset.path == "/capture" && portText == "80") "81"
                      else portText
                    if (ipAddress.isBlank()) ipAddress = targetHost
                    url = CameraSlot.buildManualStreamUrl(targetHost, targetPort, adjustedPresetPath, isHttp = preset.isHttp)
                  },
                  label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                )
              }
            }
          }
        }

        HorizontalDivider(color = DashSurfaceBorder)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
            Text(tr(lang, "Zrušit", "Cancel"), color = DashTextSecondary)
          }
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = {
              val resolvedUrl =
                url.trim().ifBlank {
                  CameraSlot.buildManualStreamUrl(
                    ipAddress = ipAddress,
                    portText = portText,
                    path = defaultChannelPath(),
                  )
                }
              val resolvedHost = CameraSlot.extractHost(resolvedUrl, ipAddress)
              onSave(
                slot.copy(
                  name = name.trim().ifBlank { "${tr(lang, "Kamera", "Camera")} ${slot.slotIndex + 1}" },
                  streamUrl = resolvedUrl,
                  username = username.trim(),
                  password = password,
                  onvifDeviceAddress = resolvedHost,
                )
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = MenuBlue),
            modifier = Modifier.height(48.dp).testTag("camera_save_button"),
          ) {
            Text(tr(lang, "Uložit kameru", "Save camera"))
          }
        }
      }
    }
  }
}

@Composable
private fun NvrSetupDialog(
  initialIp: String,
  onUserActivity: () -> Unit,
  onDismiss: () -> Unit,
  onConnect: (String, NvrProtocolPreset, Int, String, String, List<Int>, (Boolean, String) -> Unit) -> Unit,
) {
  val lang = LocalAppLanguage.current
  val palette = LocalDashPalette.current
  var selectedPreset by remember { mutableStateOf(NvrProtocolPreset.ESEECLOUD_JOOAN) }
  var ipAddress by remember { mutableStateOf(initialIp) }
  var rtspPortText by remember { mutableStateOf("554") }
  var username by remember { mutableStateOf("admin") }
  var password by remember { mutableStateOf("") }
  var selectedChannels by remember { mutableStateOf(listOf(0, 1, 2, 3)) }
  var isConnecting by remember { mutableStateOf(false) }
  var feedbackMsg by remember { mutableStateOf<String?>(null) }
  var feedbackSuccess by remember { mutableStateOf(false) }

  fun toggleSpecificChannel(ch: Int) {
    onUserActivity()
    selectedChannels =
      if (ch in selectedChannels) {
        if (selectedChannels.size > 1) selectedChannels - ch else selectedChannels
      } else {
        if (selectedChannels.size < 4) {
          (selectedChannels + ch).sorted()
        } else {
          (selectedChannels.drop(1) + ch).sorted()
        }
      }
  }

  Dialog(
    onDismissRequest = { if (!isConnecting) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.86f)
          .fillMaxHeight(0.84f)
          .imePadding()
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                onUserActivity()
              }
            }
          },
      shape = RoundedCornerShape(16.dp),
      color = DashSurface,
      border = androidx.compose.foundation.BorderStroke(1.dp, DashSurfaceBorder),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Text(
              tr(lang, "Přidat NVR (sada nebo vybrané kanály)", "Add NVR (batch or specific channels)"),
              style = MaterialTheme.typography.titleMedium,
              color = DashTextPrimary,
            )
            Text(
              tr(
                lang,
                "EseeCloud / XMEye / Dahua · Zvolte K1–4, K5–8 nebo libovolných 1–4 konkrétních kanálů",
                "EseeCloud / XMEye / Dahua · Pick K1–4, K5–8, or any 1–4 specific channels",
              ),
              style = MaterialTheme.typography.labelSmall,
              color = DashTextSecondary,
            )
          }
          IconButton(onClick = { if (!isConnecting) onDismiss() }) {
            Icon(Icons.Rounded.Close, contentDescription = tr(lang, "Zavřít", "Close"), tint = DashTextSecondary)
          }
        }

        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // Left column: Network address & Credentials
          Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = ipAddress,
                onValueChange = {
                  onUserActivity()
                  ipAddress = it
                },
                label = { Text(tr(lang, "IP adresa NVR", "NVR IP address")) },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.weight(1.6f).testTag("nvr_ip_input"),
              )
              OutlinedTextField(
                value = rtspPortText,
                onValueChange = {
                  onUserActivity()
                  rtspPortText = it.filter { ch -> ch.isDigit() }
                },
                label = { Text(tr(lang, "RTSP port", "RTSP port")) },
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.weight(0.8f),
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = username,
                onValueChange = {
                  onUserActivity()
                  username = it
                },
                label = { Text(tr(lang, "Jméno NVR", "NVR username")) },
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = password,
                onValueChange = {
                  onUserActivity()
                  password = it
                },
                label = { Text(tr(lang, "Heslo NVR", "NVR password")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = textFieldColors(),
                modifier = Modifier.weight(1f),
              )
            }

            Text(
              tr(
                lang,
                "Pozn.: Cloud ID v aplikaci EseeCloud slouží pouze pro cloudový přenos – místní IP adresu NVR zjistíte v nastavení sítě NVR nebo v aplikaci EseeCloud.",
                "Note: EseeCloud Cloud ID is only for cloud relay – find the local LAN IP of your NVR in NVR network settings or in the EseeCloud app.",
              ),
              style = MaterialTheme.typography.labelSmall,
              color = DashTextMuted,
            )
          }

          // Right column: Protocol Preset & Batch / Specific Channels
          Column(
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(DashSurfaceElevated.copy(alpha = 0.6f))
                .border(1.dp, DashSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(tr(lang, "Protokol NVR", "NVR Protocol"), style = MaterialTheme.typography.labelMedium, color = DashTextPrimary)
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              NvrProtocolPreset.entries.forEach { preset ->
                FilterChip(
                  selected = selectedPreset == preset,
                  onClick = {
                    onUserActivity()
                    selectedPreset = preset
                  },
                  label = { Text(preset.labelFor(lang), style = MaterialTheme.typography.labelSmall) },
                )
              }
            }

            Text(tr(lang, "Rychlá sada kanálů", "Quick channel batch"), style = MaterialTheme.typography.labelMedium, color = DashTextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = selectedChannels == listOf(0, 1, 2, 3),
                onClick = {
                  onUserActivity()
                  selectedChannels = listOf(0, 1, 2, 3)
                },
                label = { Text(tr(lang, "Kanály 1–4", "Channels 1–4"), style = MaterialTheme.typography.labelSmall) },
              )
              FilterChip(
                selected = selectedChannels == listOf(4, 5, 6, 7),
                onClick = {
                  onUserActivity()
                  selectedChannels = listOf(4, 5, 6, 7)
                },
                label = { Text(tr(lang, "Kanály 5–8", "Channels 5–8"), style = MaterialTheme.typography.labelSmall) },
              )
            }

            Text(
              tr(lang, "Nebo vyberte konkrétní kanály (1–4):", "Or select specific channels (1–4):"),
              style = MaterialTheme.typography.labelMedium,
              color = DashTextPrimary,
            )
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              (0..7).forEach { ch ->
                val isSelected = ch in selectedChannels
                FilterChip(
                  selected = isSelected,
                  onClick = { toggleSpecificChannel(ch) },
                  label = { Text("K${ch + 1}", style = MaterialTheme.typography.labelSmall) },
                  leadingIcon =
                    if (isSelected) {
                      { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                    } else null,
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = palette.accent,
                      selectedLabelColor = Color.White,
                    ),
                  modifier = Modifier.testTag("nvr_channel_${ch}"),
                )
              }
            }

            val mappingSummary =
              selectedChannels.mapIndexed { idx, ch ->
                val camPrefix = tr(lang, "Kam", "Cam")
                "K${ch + 1} → $camPrefix ${idx + 1}"
              }.joinToString(" · ")
            Text(
              text = "${tr(lang, "Mapování", "Mapping")}: $mappingSummary",
              style = MaterialTheme.typography.labelSmall,
              color = palette.accentBright,
            )

            if (!feedbackMsg.isNullOrBlank()) {
              Text(
                text = feedbackMsg.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (feedbackSuccess) LiveEmerald else OfflineRed,
              )
            }
          }
        }

        HorizontalDivider(color = DashSurfaceBorder)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss, enabled = !isConnecting, modifier = Modifier.height(48.dp)) {
            Text(tr(lang, "Zrušit", "Cancel"), color = DashTextSecondary)
          }
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = {
              onUserActivity()
              isConnecting = true
              feedbackMsg = null
              val port = rtspPortText.toIntOrNull() ?: 554
              onConnect(
                ipAddress.trim(),
                selectedPreset,
                port,
                username.trim(),
                password,
                selectedChannels,
              ) { ok, msg ->
                isConnecting = false
                feedbackSuccess = ok
                feedbackMsg = msg
                if (ok) onDismiss()
              }
            },
            enabled = !isConnecting && ipAddress.isNotBlank() && selectedChannels.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MenuBlue),
            modifier = Modifier.height(48.dp).testTag("nvr_connect_button"),
          ) {
            if (isConnecting) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
              Spacer(Modifier.width(6.dp))
              Text(tr(lang, "Připojuji...", "Connecting..."))
            } else {
              Text(
                tr(
                  lang,
                  "Připojit (${selectedChannels.size})",
                  "Connect (${selectedChannels.size})",
                )
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Go2RtcBridgeDialog(
  initialNvrIp: String,
  onUserActivity: () -> Unit,
  onDismiss: () -> Unit,
  onImportStreams: (String, Int, Int, (Boolean, String) -> Unit) -> Unit,
  onRegisterEseeCloud: (String, Int, Int, String, Int, String, String, List<Int>, Int, (Boolean, String) -> Unit) -> Unit,
) {
  val lang = LocalAppLanguage.current
  val palette = LocalDashPalette.current
  val context = LocalContext.current
  var goHost by remember { mutableStateOf("127.0.0.1") }
  var apiPortText by remember { mutableStateOf("1984") }
  var rtspPortText by remember { mutableStateOf("8554") }

  var nvrIp by remember { mutableStateOf(initialNvrIp) }
  var nvrBubblePort by remember { mutableIntStateOf(80) }
  var username by remember { mutableStateOf("admin") }
  var password by remember { mutableStateOf("") }
  var selectedChannels by remember { mutableStateOf(listOf(0, 1, 2, 3)) }
  var streamQuality by remember { mutableIntStateOf(1) } // 1 = Sub-stream, 0 = Main HD

  var isWorking by remember { mutableStateOf(false) }
  var feedbackMsg by remember { mutableStateOf<String?>(null) }
  var feedbackSuccess by remember { mutableStateOf(false) }

  val apiPort = apiPortText.toIntOrNull() ?: 1984
  val rtspPort = rtspPortText.toIntOrNull() ?: 8554

  fun toggleChannel(ch: Int) {
    onUserActivity()
    selectedChannels =
      if (ch in selectedChannels) {
        if (selectedChannels.size > 1) selectedChannels - ch else selectedChannels
      } else {
        if (selectedChannels.size < 4) {
          (selectedChannels + ch).sorted()
        } else {
          (selectedChannels.drop(1) + ch).sorted()
        }
      }
  }

  fun copyToClipboard(label: String, text: String, successNote: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    feedbackSuccess = true
    feedbackMsg = successNote
  }

  Dialog(
    onDismissRequest = { if (!isWorking) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.88f)
          .fillMaxHeight(0.86f)
          .imePadding()
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                onUserActivity()
              }
            }
          },
      shape = RoundedCornerShape(16.dp),
      color = DashSurface,
      border = androidx.compose.foundation.BorderStroke(1.dp, DashSurfaceBorder),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Header Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              tr(lang, "go2rtc most (Telefon / Termux / Home Assistant)", "go2rtc bridge (Phone / Termux / Home Assistant)"),
              style = MaterialTheme.typography.titleMedium,
              color = DashTextPrimary,
            )
            Text(
              tr(
                lang,
                "Převádí nativní protokol EseeCloud bubble:// do RTSP pro CamPanel",
                "Bridges native EseeCloud bubble:// protocol to RTSP for CamPanel",
              ),
              style = MaterialTheme.typography.labelSmall,
              color = DashTextSecondary,
            )
          }
          IconButton(onClick = { if (!isWorking) onDismiss() }) {
            Icon(Icons.Rounded.Close, contentDescription = tr(lang, "Zavřít", "Close"), tint = DashTextSecondary)
          }
        }

        // Main 2-column body
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // LEFT PANE: go2rtc server address, stream import & Termux/YAML copy
          Column(
            modifier =
              Modifier.weight(0.95f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(DashSurfaceElevated.copy(alpha = 0.6f))
                .border(1.dp, DashSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              tr(lang, "1. Adresa běžícího go2rtc", "1. Running go2rtc address"),
              style = MaterialTheme.typography.labelMedium,
              color = DashTextPrimary,
              fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedTextField(
                value = goHost,
                onValueChange = {
                  onUserActivity()
                  goHost = it
                },
                label = { Text("go2rtc IP") },
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.weight(1.4f).testTag("go2rtc_host_input"),
              )
              OutlinedTextField(
                value = apiPortText,
                onValueChange = {
                  onUserActivity()
                  apiPortText = it.filter { ch -> ch.isDigit() }
                },
                label = { Text("API") },
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.weight(0.75f),
              )
              OutlinedTextField(
                value = rtspPortText,
                onValueChange = {
                  onUserActivity()
                  rtspPortText = it.filter { ch -> ch.isDigit() }
                },
                label = { Text("RTSP") },
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.weight(0.75f),
              )
            }

            OutlinedButton(
              onClick = {
                onUserActivity()
                isWorking = true
                feedbackMsg = null
                onImportStreams(goHost, apiPort, rtspPort) { ok, msg ->
                  isWorking = false
                  feedbackSuccess = ok
                  feedbackMsg = msg
                  if (ok) onDismiss()
                }
              },
              enabled = !isWorking,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("go2rtc_import_button"),
            ) {
              Text(tr(lang, "Načíst běžící streamy z go2rtc", "Import running streams from go2rtc"), color = LiveEmerald, style = MaterialTheme.typography.labelMedium)
            }

            Text(
              tr(lang, "Spuštění v Termuxu na tomto telefonu:", "Run in Termux on this phone:"),
              style = MaterialTheme.typography.labelSmall,
              color = DashTextSecondary,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = {
                  onUserActivity()
                  val yaml =
                    Go2RtcClient.buildEseeCloudGo2RtcYaml(
                      nvrIp = nvrIp,
                      nvrPort = nvrBubblePort,
                      username = username,
                      password = password,
                      channelsZeroBased = selectedChannels,
                      streamQualityIndex = streamQuality,
                      apiPort = apiPort,
                      rtspPort = rtspPort,
                    )
                  copyToClipboard(
                    "termux_cmd",
                    Go2RtcClient.buildTermuxInstallCommand(yaml),
                    tr(
                      lang,
                      "Příkaz pro Termux zkopírován do schránky. Vložte jej do Termuxu a spusťte.",
                      "Termux command copied to clipboard. Paste and run it in Termux.",
                    ),
                  )
                },
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(tr(lang, "Příkaz Termux", "Termux cmd"), style = MaterialTheme.typography.labelSmall)
              }
              OutlinedButton(
                onClick = {
                  onUserActivity()
                  val yaml =
                    Go2RtcClient.buildEseeCloudGo2RtcYaml(
                      nvrIp = nvrIp,
                      nvrPort = nvrBubblePort,
                      username = username,
                      password = password,
                      channelsZeroBased = selectedChannels,
                      streamQualityIndex = streamQuality,
                      apiPort = apiPort,
                      rtspPort = rtspPort,
                    )
                  copyToClipboard(
                    "go2rtc_yaml",
                    yaml,
                    tr(
                      lang,
                      "Konfigurace go2rtc.yaml zkopírována do schránky.",
                      "go2rtc.yaml configuration copied to clipboard.",
                    ),
                  )
                },
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("go2rtc.yaml", style = MaterialTheme.typography.labelSmall)
              }
            }
          }

          // RIGHT PANE: EseeCloud NVR parameters & batch/individual channel selection
          Column(
            modifier =
              Modifier.weight(1.05f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(DashSurfaceElevated.copy(alpha = 0.6f))
                .border(1.dp, DashSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              tr(lang, "2. Parametry NVR EseeCloud (bubble://)", "2. EseeCloud NVR settings (bubble://)"),
              style = MaterialTheme.typography.labelMedium,
              color = DashTextPrimary,
              fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
              value = nvrIp,
              onValueChange = {
                onUserActivity()
                nvrIp = it
              },
              label = { Text(tr(lang, "Místní IP adresa NVR", "Local NVR IP address")) },
              placeholder = { Text("192.168.1.50") },
              singleLine = true,
              colors = textFieldColors(),
              modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = username,
                onValueChange = {
                  onUserActivity()
                  username = it
                },
                label = { Text(tr(lang, "Jméno NVR", "NVR username")) },
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = password,
                onValueChange = {
                  onUserActivity()
                  password = it
                },
                label = { Text(tr(lang, "Heslo NVR", "NVR password")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = textFieldColors(),
                modifier = Modifier.weight(1f),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text("Port:", style = MaterialTheme.typography.labelSmall, color = DashTextSecondary)
              listOf(80, 10000, 8080).forEach { p ->
                FilterChip(
                  selected = nvrBubblePort == p,
                  onClick = {
                    onUserActivity()
                    nvrBubblePort = p
                  },
                  label = { Text("$p", style = MaterialTheme.typography.labelSmall) },
                )
              }
              FilterChip(
                selected = streamQuality == 1,
                onClick = {
                  onUserActivity()
                  streamQuality = if (streamQuality == 1) 0 else 1
                },
                label = {
                  Text(
                    if (streamQuality == 1) tr(lang, "Sub (rychlý)", "Sub (fast)") else "Main HD",
                    style = MaterialTheme.typography.labelSmall,
                  )
                },
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              FilterChip(
                selected = selectedChannels == listOf(0, 1, 2, 3),
                onClick = {
                  onUserActivity()
                  selectedChannels = listOf(0, 1, 2, 3)
                },
                label = { Text("K1–4", style = MaterialTheme.typography.labelSmall) },
              )
              FilterChip(
                selected = selectedChannels == listOf(4, 5, 6, 7),
                onClick = {
                  onUserActivity()
                  selectedChannels = listOf(4, 5, 6, 7)
                },
                label = { Text("K5–8", style = MaterialTheme.typography.labelSmall) },
              )
              (0..7).forEach { ch ->
                val isSelected = ch in selectedChannels
                FilterChip(
                  selected = isSelected,
                  onClick = { toggleChannel(ch) },
                  label = { Text("K${ch + 1}", style = MaterialTheme.typography.labelSmall) },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = palette.accent,
                      selectedLabelColor = Color.White,
                    ),
                )
              }
            }
          }
        }

        HorizontalDivider(color = DashSurfaceBorder)

        // Footer Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text =
              feedbackMsg
                ?: tr(
                  lang,
                  "Výchozí pro go2rtc přímo v telefonu (Termux) je 127.0.0.1.",
                  "Default host for go2rtc running on this phone (Termux) is 127.0.0.1.",
                ),
            style = MaterialTheme.typography.bodyMedium,
            color =
              when {
                feedbackMsg == null -> DashTextMuted
                feedbackSuccess -> LiveEmerald
                else -> OfflineRed
              },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(onClick = onDismiss, enabled = !isWorking, modifier = Modifier.height(48.dp)) {
              Text(tr(lang, "Zavřít", "Close"), color = DashTextSecondary)
            }
            Button(
              onClick = {
                onUserActivity()
                isWorking = true
                feedbackMsg = null
                onRegisterEseeCloud(
                  goHost,
                  apiPort,
                  rtspPort,
                  nvrIp,
                  nvrBubblePort,
                  username,
                  password,
                  selectedChannels,
                  streamQuality,
                ) { ok, msg ->
                  isWorking = false
                  feedbackSuccess = ok
                  feedbackMsg = msg
                  if (ok) onDismiss()
                }
              },
              enabled = !isWorking && nvrIp.isNotBlank() && selectedChannels.isNotEmpty(),
              colors = ButtonDefaults.buttonColors(containerColor = MenuBlue),
              modifier = Modifier.height(48.dp).testTag("go2rtc_register_button"),
            ) {
              if (isWorking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(tr(lang, "Odesílám...", "Sending..."))
              } else {
                Text(tr(lang, "Odeslat do go2rtc a připojit", "Push to go2rtc & connect"))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun textFieldColors(): androidx.compose.material3.TextFieldColors {
  val palette = LocalDashPalette.current
  return OutlinedTextFieldDefaults.colors(
    focusedBorderColor = palette.accentBright,
    unfocusedBorderColor = palette.surfaceBorder,
    focusedTextColor = DashTextPrimary,
    unfocusedTextColor = DashTextPrimary,
    focusedLabelColor = palette.accentBright,
    unfocusedLabelColor = DashTextSecondary,
    cursorColor = palette.accentBright,
  )
}
