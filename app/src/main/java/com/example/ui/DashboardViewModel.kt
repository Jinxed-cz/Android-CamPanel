package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AmbientLightMonitor
import com.example.data.BatteryMonitor
import com.example.data.Go2RtcClient
import com.example.data.NetworkCameraDiscoveryManager
import com.example.data.RadioDirectoryClient
import com.example.data.RadioPlayerController
import com.example.data.SettingsRepository
import com.example.data.WeatherRepository
import com.example.model.ActiveRadioStation
import com.example.model.AladinForecastData
import com.example.model.AppLanguage
import com.example.model.AppearanceSettings
import com.example.model.BatteryRuntimeStatus
import com.example.model.CameraSlot
import com.example.model.DiscoveredNetworkCamera
import com.example.model.DisplayBrightnessMath
import com.example.model.DisplayRuntimeStatus
import com.example.model.DisplaySettings
import com.example.model.FavoriteRadioStation
import com.example.model.NvrProtocolPreset
import com.example.model.RadioDirectoryStation
import com.example.model.RadioStation
import com.example.model.RadioUiState
import com.example.model.WeatherLocation
import com.example.model.WeatherMetricToggle
import com.example.model.WeatherProvider
import com.example.model.WeatherUiState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

  companion object {
    const val CAMERA_FULLSCREEN_DURATION_SEC = 10
    const val SETTINGS_INACTIVITY_TIMEOUT_SEC = 10
    const val CAMERA_SETTINGS_INACTIVITY_TIMEOUT_SEC = 30
    const val WEATHER_REFRESH_INTERVAL_MS = 10L * 60L * 1000L // 10 minutes
  }

  private val settingsRepo = SettingsRepository(application)
  private val weatherRepo = WeatherRepository()
  private val cameraDiscoveryManager = NetworkCameraDiscoveryManager(application)
  private val go2RtcClient = Go2RtcClient()
  private val radioDirectoryClient = RadioDirectoryClient()
  private val radioController = RadioPlayerController(application)
  private val ambientLightMonitor = AmbientLightMonitor(application)
  private val batteryMonitor = BatteryMonitor(application)

  val radioState: StateFlow<RadioUiState> = radioController.state
  val batteryStatus: StateFlow<BatteryRuntimeStatus> = batteryMonitor.status

  private val czechLocale = Locale.forLanguageTag("cs-CZ")
  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", czechLocale)
  private val czechDateFormatter = DateTimeFormatter.ofPattern("EEE d. M. yyyy", czechLocale)
  private val englishDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH)

  val appLanguage: StateFlow<AppLanguage> =
    settingsRepo.appLanguageFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = AppLanguage.CS,
    )

  val weatherProvider: StateFlow<WeatherProvider> =
    settingsRepo.weatherProviderFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = WeatherProvider.ALADIN,
    )

  val cameras: StateFlow<List<CameraSlot>> =
    settingsRepo.camerasFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = (0..3).map { CameraSlot(slotIndex = it) },
    )

  val weatherLocation: StateFlow<WeatherLocation> =
    settingsRepo.weatherLocationFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = WeatherLocation(),
    )

  val weatherToggles: StateFlow<WeatherMetricToggle> =
    settingsRepo.weatherTogglesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = WeatherMetricToggle(),
    )

  val savedWeatherLocations: StateFlow<List<WeatherLocation>> =
    settingsRepo.savedWeatherLocationsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = emptyList(),
    )

  val favoriteRadios: StateFlow<List<FavoriteRadioStation>> =
    settingsRepo.favoriteRadiosFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = emptyList(),
    )

  val availableRadioStations: StateFlow<List<ActiveRadioStation>> =
    favoriteRadios
      .map { favs ->
        RadioStation.entries.map { ActiveRadioStation.fromPreset(it) } +
          favs.map { ActiveRadioStation.fromFavorite(it) }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RadioStation.entries.map { ActiveRadioStation.fromPreset(it) },
      )

  val appearanceSettings: StateFlow<AppearanceSettings> =
    settingsRepo.appearanceSettingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = AppearanceSettings(),
    )

  val displaySettings: StateFlow<DisplaySettings> =
    settingsRepo.displaySettingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = DisplaySettings(),
    )

  private val _nightPreviewActive = MutableStateFlow(false)
  private val _currentHourMinute =
    MutableStateFlow(LocalDateTime.now().let { it.hour to it.minute })

  val displayRuntimeStatus: StateFlow<DisplayRuntimeStatus> =
    combine(
      displaySettings,
      ambientLightMonitor.ambientLux,
      _currentHourMinute,
      _nightPreviewActive,
    ) { settings, lux, (hour, minute), preview ->
      val (brightness, nightActive) =
        DisplayBrightnessMath.resolveEffectiveScreenBrightness(
          settings = settings,
          currentHour = hour,
          currentMinute = minute,
          ambientLux = lux,
          previewNightBrightness = preview,
        )
      DisplayRuntimeStatus(
        effectiveBrightness = brightness,
        isNightModeActive = nightActive,
        isPreviewActive = preview,
        ambientLux = lux,
        hasLightSensor = ambientLightMonitor.hasLightSensor,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = DisplayRuntimeStatus(hasLightSensor = ambientLightMonitor.hasLightSensor),
    )

  private val _clockTime = MutableStateFlow(LocalDateTime.now().format(timeFormatter))
  val clockTime: StateFlow<String> = _clockTime.asStateFlow()

  private val _clockDate = MutableStateFlow(formatDateNow())
  val clockDate: StateFlow<String> = _clockDate.asStateFlow()

  private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
  val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

  // Fullscreen camera state (10-second auto-return)
  private val _fullscreenSlot = MutableStateFlow<Int?>(null)
  val fullscreenSlot: StateFlow<Int?> = _fullscreenSlot.asStateFlow()

  private val _fullscreenRemainingSec = MutableStateFlow(0)
  val fullscreenRemainingSec: StateFlow<Int> = _fullscreenRemainingSec.asStateFlow()

  // Settings overlay state (10s weather / 30s camera setup inactivity auto-return)
  private val _isSettingsOpen = MutableStateFlow(false)
  val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

  private val _settingsTimeoutSec = MutableStateFlow(SETTINGS_INACTIVITY_TIMEOUT_SEC)
  val settingsTimeoutSec: StateFlow<Int> = _settingsTimeoutSec.asStateFlow()

  private val _settingsRemainingSec = MutableStateFlow(SETTINGS_INACTIVITY_TIMEOUT_SEC)
  val settingsRemainingSec: StateFlow<Int> = _settingsRemainingSec.asStateFlow()

  // Location search state
  private val _locationSearchResults = MutableStateFlow<List<WeatherLocation>>(emptyList())
  val locationSearchResults: StateFlow<List<WeatherLocation>> = _locationSearchResults.asStateFlow()

  private val _isSearchingLocations = MutableStateFlow(false)
  val isSearchingLocations: StateFlow<Boolean> = _isSearchingLocations.asStateFlow()

  private val _locationSearchError = MutableStateFlow<String?>(null)
  val locationSearchError: StateFlow<String?> = _locationSearchError.asStateFlow()

  // ONVIF discovery state
  private val _isScanningOnvif = MutableStateFlow(false)
  val isScanningOnvif: StateFlow<Boolean> = _isScanningOnvif.asStateFlow()

  private val _discoveredCameras = MutableStateFlow<List<DiscoveredNetworkCamera>>(emptyList())
  val discoveredCameras: StateFlow<List<DiscoveredNetworkCamera>> = _discoveredCameras.asStateFlow()

  private val _onvifStatusMessage = MutableStateFlow<String?>(null)
  val onvifStatusMessage: StateFlow<String?> = _onvifStatusMessage.asStateFlow()

  // Internet Radio directory search state
  private val _radioSearchResults = MutableStateFlow<List<RadioDirectoryStation>>(emptyList())
  val radioSearchResults: StateFlow<List<RadioDirectoryStation>> = _radioSearchResults.asStateFlow()

  private val _isSearchingRadios = MutableStateFlow(false)
  val isSearchingRadios: StateFlow<Boolean> = _isSearchingRadios.asStateFlow()

  private val _radioSearchError = MutableStateFlow<String?>(null)
  val radioSearchError: StateFlow<String?> = _radioSearchError.asStateFlow()

  private var fullscreenTimerJob: Job? = null
  private var settingsInactivityJob: Job? = null
  private var cachedForecast: AladinForecastData? = null

  init {
    startClockTicker()
    observeLocationAndAutoRefreshWeather()
    observeSavedRadioStation()
  }

  private fun observeSavedRadioStation() {
    viewModelScope.launch {
      combine(settingsRepo.selectedRadioIdFlow, availableRadioStations) { selectedId, allStations ->
        allStations.firstOrNull { it.id == selectedId }
          ?: ActiveRadioStation.fromPreset(RadioStation.fromId(selectedId))
      }.collect { active ->
        radioController.setSelectedActiveStation(
          station = active,
          preset = RadioStation.fromId(active.id),
        )
      }
    }
  }

  private fun formatDateNow(language: AppLanguage = appLanguage.value): String {
    val locale = if (language == AppLanguage.EN) Locale.ENGLISH else czechLocale
    val fmt = if (language == AppLanguage.EN) englishDateFormatter else czechDateFormatter
    return LocalDateTime.now().format(fmt).replaceFirstChar { it.titlecase(locale) }
  }

  private fun startClockTicker() {
    viewModelScope.launch {
      while (isActive) {
        val now = LocalDateTime.now()
        val lang = appLanguage.value
        val locale = if (lang == AppLanguage.EN) Locale.ENGLISH else czechLocale
        val fmt = if (lang == AppLanguage.EN) englishDateFormatter else czechDateFormatter
        _clockTime.value = now.format(timeFormatter)
        _clockDate.value = now.format(fmt).replaceFirstChar { it.titlecase(locale) }
        val hm = now.hour to now.minute
        if (_currentHourMinute.value != hm) {
          _currentHourMinute.value = hm
        }
        delay(1000L)
      }
    }
  }

  private fun observeLocationAndAutoRefreshWeather() {
    viewModelScope.launch {
      combine(
        settingsRepo.weatherLocationFlow,
        settingsRepo.weatherProviderFlow,
        settingsRepo.appLanguageFlow,
      ) { loc, prov, lang -> Triple(loc, prov, lang) }
        .distinctUntilChanged()
        .collectLatest { (location, provider, language) ->
          fetchWeatherNow(location, provider, language)
        }
    }
    viewModelScope.launch {
      while (isActive) {
        delay(WEATHER_REFRESH_INTERVAL_MS)
        fetchWeatherNow(weatherLocation.value, weatherProvider.value, appLanguage.value)
      }
    }
  }

  private suspend fun fetchWeatherNow(
    location: WeatherLocation,
    provider: WeatherProvider = weatherProvider.value,
    language: AppLanguage = appLanguage.value,
  ) {
    val previous = cachedForecast
    if (previous != null && previous.locationName == location.name && previous.provider == provider) {
      _weatherState.value = WeatherUiState.Ready(previous, isRefreshing = true)
    } else {
      _weatherState.value = WeatherUiState.Loading
    }

    runCatching { weatherRepo.fetchThreeDayForecast(location, provider, language) }
      .onSuccess { data ->
        cachedForecast = data
        _weatherState.value = WeatherUiState.Ready(data, isRefreshing = false)
      }
      .onFailure { err ->
        val fallbackMsg =
          if (language == AppLanguage.EN) "Failed to load weather forecast"
          else "Nepodařilo se načíst předpověď počasí"
        _weatherState.value =
          WeatherUiState.Error(
            message = err.localizedMessage ?: fallbackMsg,
            cachedData = cachedForecast,
          )
      }
  }

  // --- Fullscreen Camera (10s auto return) ---

  fun openCameraFullscreen(slotIndex: Int) {
    _fullscreenSlot.value = slotIndex
    fullscreenTimerJob?.cancel()
    fullscreenTimerJob =
      viewModelScope.launch {
        for (sec in CAMERA_FULLSCREEN_DURATION_SEC downTo 1) {
          _fullscreenRemainingSec.value = sec
          delay(1000L)
        }
        _fullscreenSlot.value = null
        _fullscreenRemainingSec.value = 0
      }
  }

  fun closeCameraFullscreen() {
    fullscreenTimerJob?.cancel()
    _fullscreenSlot.value = null
    _fullscreenRemainingSec.value = 0
  }

  // --- Settings Menu (10s weather / 30s camera setup inactivity auto return) ---

  fun openSettings() {
    _nightPreviewActive.value = false
    _settingsTimeoutSec.value = SETTINGS_INACTIVITY_TIMEOUT_SEC
    _isSettingsOpen.value = true
    resetSettingsInactivityTimer()
  }

  fun closeSettings() {
    settingsInactivityJob?.cancel()
    _nightPreviewActive.value = false
    _isSettingsOpen.value = false
    _settingsTimeoutSec.value = SETTINGS_INACTIVITY_TIMEOUT_SEC
    _settingsRemainingSec.value = SETTINGS_INACTIVITY_TIMEOUT_SEC
  }

  fun setSettingsCameraTabActive(isCameraTab: Boolean) {
    val targetTimeout =
      if (isCameraTab) CAMERA_SETTINGS_INACTIVITY_TIMEOUT_SEC
      else SETTINGS_INACTIVITY_TIMEOUT_SEC
    _settingsTimeoutSec.value = targetTimeout
    resetSettingsInactivityTimer()
  }

  fun resetSettingsInactivityTimer() {
    if (!_isSettingsOpen.value) return
    val timeoutSec = _settingsTimeoutSec.value
    _settingsRemainingSec.value = timeoutSec
    settingsInactivityJob?.cancel()
    settingsInactivityJob =
      viewModelScope.launch {
        for (sec in timeoutSec downTo 1) {
          _settingsRemainingSec.value = sec
          delay(1000L)
        }
        _nightPreviewActive.value = false
        _isSettingsOpen.value = false
      }
  }

  // --- Language & Weather Settings ---

  fun setAppLanguage(language: AppLanguage) {
    resetSettingsInactivityTimer()
    _clockDate.value = formatDateNow(language)
    viewModelScope.launch { settingsRepo.saveAppLanguage(language) }
  }

  fun selectWeatherProvider(provider: WeatherProvider) {
    resetSettingsInactivityTimer()
    if (provider == weatherProvider.value) {
      viewModelScope.launch { fetchWeatherNow(weatherLocation.value, provider, appLanguage.value) }
    } else {
      cachedForecast = null
      _weatherState.value = WeatherUiState.Loading
      viewModelScope.launch { settingsRepo.saveWeatherProvider(provider) }
    }
  }

  fun searchLocations(query: String) {
    resetSettingsInactivityTimer()
    if (query.trim().length < 2) {
      _locationSearchResults.value = emptyList()
      _locationSearchError.value = null
      return
    }
    viewModelScope.launch {
      val lang = appLanguage.value
      _isSearchingLocations.value = true
      _locationSearchError.value = null
      runCatching { weatherRepo.searchLocations(query, lang) }
        .onSuccess { results ->
          _locationSearchResults.value = results
          if (results.isEmpty()) {
            _locationSearchError.value =
              if (lang == AppLanguage.EN) "Location not found" else "Místo nenalezeno"
          }
        }
        .onFailure { e ->
          _locationSearchError.value =
            e.localizedMessage
              ?: if (lang == AppLanguage.EN) "Location search failed" else "Chyba vyhledávání místa"
        }
      _isSearchingLocations.value = false
    }
  }

  fun selectWeatherLocation(location: WeatherLocation) {
    resetSettingsInactivityTimer()
    if (location == weatherLocation.value) {
      viewModelScope.launch { fetchWeatherNow(location, weatherProvider.value, appLanguage.value) }
    } else {
      cachedForecast = null
      _weatherState.value = WeatherUiState.Loading
      viewModelScope.launch {
        settingsRepo.saveWeatherLocation(location)
      }
    }
  }

  fun updateWeatherToggles(transform: (WeatherMetricToggle) -> WeatherMetricToggle) {
    resetSettingsInactivityTimer()
    val updated = transform(weatherToggles.value)
    viewModelScope.launch { settingsRepo.saveWeatherToggles(updated) }
  }

  fun saveWeatherCity(location: WeatherLocation) {
    resetSettingsInactivityTimer()
    val existing = savedWeatherLocations.value
    if (existing.any { it.name == location.name && it.latitude == location.latitude && it.longitude == location.longitude }) {
      return
    }
    viewModelScope.launch { settingsRepo.saveSavedWeatherLocations(existing + location) }
  }

  fun removeSavedWeatherCity(location: WeatherLocation) {
    resetSettingsInactivityTimer()
    val updated =
      savedWeatherLocations.value.filterNot {
        it.name == location.name && it.latitude == location.latitude && it.longitude == location.longitude
      }
    viewModelScope.launch { settingsRepo.saveSavedWeatherLocations(updated) }
  }

  // --- Display, Auto-Brightness, Battery & Night Mode ---

  fun startLightMonitoring() {
    ambientLightMonitor.start()
    batteryMonitor.start()
  }

  fun stopLightMonitoring() {
    ambientLightMonitor.stop()
    batteryMonitor.stop()
  }

  fun updateDisplaySettings(transform: (DisplaySettings) -> DisplaySettings) {
    resetSettingsInactivityTimer()
    val updated = transform(displaySettings.value)
    viewModelScope.launch { settingsRepo.saveDisplaySettings(updated) }
  }

  fun setNightBrightnessPreview(enabled: Boolean) {
    resetSettingsInactivityTimer()
    _nightPreviewActive.value = enabled
  }

  fun updateAppearanceSettings(transform: (AppearanceSettings) -> AppearanceSettings) {
    resetSettingsInactivityTimer()
    val updated = transform(appearanceSettings.value)
    viewModelScope.launch { settingsRepo.saveAppearanceSettings(updated) }
  }

  // --- Camera Settings & Multi-Protocol Network Discovery ---

  fun scanOnvifCameras() {
    resetSettingsInactivityTimer()
    if (_isScanningOnvif.value) return
    val isEn = appLanguage.value == AppLanguage.EN
    viewModelScope.launch {
      _isScanningOnvif.value = true
      _onvifStatusMessage.value =
        if (isEn) "Scanning LAN (ONVIF, EseeCloud, XMEye, Dahua, RTSP, HTTP)..."
        else "Prohledávám síť (ONVIF, EseeCloud, XMEye, Dahua, RTSP, HTTP)..."
      val found = cameraDiscoveryManager.discoverAllCameras()
      _discoveredCameras.value = found
      _onvifStatusMessage.value =
        if (found.isEmpty()) {
          if (isEn) "No network cameras found. Use \"Add NVR\" or manual setup."
          else "Žádné kamery nebyly v síti nalezeny. Pro NVR použijte „Přidat NVR“ nebo ruční úpravu."
        } else {
          if (isEn) "Found ${found.size} device(s). Pick a channel and camera slot 1–4."
          else "Nalezeno zařízení v síti: ${found.size}. Vyberte kanál a pozici 1–4."
        }
      _isScanningOnvif.value = false
      resetSettingsInactivityTimer()
    }
  }

  fun assignDiscoveredCameraToSlot(
    discovered: DiscoveredNetworkCamera,
    targetSlotIndex: Int,
    username: String,
    password: String,
    channelIndex: Int = 0,
  ) {
    resetSettingsInactivityTimer()
    viewModelScope.launch {
      _discoveredCameras.update { list ->
        list.map {
          if (it.id == discovered.id) it.copy(isResolvingUri = true, resolveError = null) else it
        }
      }

      val resolvedResult =
        cameraDiscoveryManager.resolveChannelStreamUri(
          camera = discovered,
          channelIndex = channelIndex,
          username = username.trim(),
          password = password,
        )

      resolvedResult
        .onSuccess { streamUri ->
          _discoveredCameras.update { list ->
            list.map {
              if (it.id == discovered.id) {
                it.copy(isResolvingUri = false, resolvedStreamUrl = streamUri)
              } else {
                it
              }
            }
          }
          val slotTitle =
            if (discovered.isNvr || channelIndex > 0) {
              "${discovered.name.take(18)} K${channelIndex + 1}"
            } else {
              discovered.name.take(24)
            }
          val updatedSlot =
            CameraSlot(
              slotIndex = targetSlotIndex,
              name = slotTitle,
              streamUrl = streamUri,
              username = username.trim(),
              password = password,
              onvifDeviceAddress = discovered.ipAddress,
              protocolLabel = discovered.discoveryType,
            )
          saveSingleSlot(updatedSlot)
          _onvifStatusMessage.value =
            "Kamera ${updatedSlot.name} (${discovered.discoveryType}) uložena do pozice ${targetSlotIndex + 1}."
        }
        .onFailure { err ->
          _discoveredCameras.update { list ->
            list.map {
              if (it.id == discovered.id) {
                it.copy(
                  isResolvingUri = false,
                  resolveError = err.localizedMessage ?: "Nepodařilo se získat adresu streamu",
                )
              } else {
                it
              }
            }
          }
          _onvifStatusMessage.value = err.localizedMessage ?: "Chyba při připojování ke kameře"
        }
      resetSettingsInactivityTimer()
    }
  }

  fun assignAllFourNvrChannels(
    discovered: DiscoveredNetworkCamera,
    username: String,
    password: String,
    startChannelZeroBased: Int = 0,
  ) {
    resetSettingsInactivityTimer()
    viewModelScope.launch {
      _discoveredCameras.update { list ->
        list.map {
          if (it.id == discovered.id) it.copy(isResolvingUri = true, resolveError = null) else it
        }
      }
      _onvifStatusMessage.value = "Připojuji 4 kanály NVR (${discovered.ipAddress})..."

      val preset =
        NvrProtocolPreset.entries.firstOrNull {
          it.id.equals(discovered.discoveryType, ignoreCase = true)
        } ?: NvrProtocolPreset.ESEECLOUD_JOOAN

      val batchResult =
        cameraDiscoveryManager.connectNvrFourChannels(
          ipAddress = discovered.ipAddress,
          preset = preset,
          rtspPort = discovered.rtspPort,
          username = username.trim(),
          password = password,
          startChannelZeroBased = startChannelZeroBased,
        )

      batchResult
        .onSuccess { res ->
          settingsRepo.saveCameras(res.slots)
          _discoveredCameras.update { list ->
            list.map {
              if (it.id == discovered.id) it.copy(isResolvingUri = false, resolveError = null) else it
            }
          }
          _onvifStatusMessage.value = res.statusMessage
        }
        .onFailure { err ->
          val msg = err.localizedMessage ?: "Nepodařilo se připojit kanály NVR"
          _discoveredCameras.update { list ->
            list.map {
              if (it.id == discovered.id) it.copy(isResolvingUri = false, resolveError = msg) else it
            }
          }
          _onvifStatusMessage.value = msg
        }
      resetSettingsInactivityTimer()
    }
  }

  fun connectManualNvr(
    ipAddress: String,
    preset: NvrProtocolPreset,
    rtspPort: Int,
    username: String,
    password: String,
    startChannelZeroBased: Int,
    onCompleted: (Boolean, String) -> Unit,
  ) {
    connectManualNvrChannels(
      ipAddress = ipAddress,
      preset = preset,
      rtspPort = rtspPort,
      username = username,
      password = password,
      channelsZeroBased = (0..3).map { (startChannelZeroBased + it).coerceIn(0, 7) },
      onCompleted = onCompleted,
    )
  }

  fun connectManualNvrChannels(
    ipAddress: String,
    preset: NvrProtocolPreset,
    rtspPort: Int,
    username: String,
    password: String,
    channelsZeroBased: List<Int>,
    onCompleted: (Boolean, String) -> Unit,
  ) {
    resetSettingsInactivityTimer()
    viewModelScope.launch {
      val result =
        cameraDiscoveryManager.connectNvrChannels(
          ipAddress = ipAddress,
          preset = preset,
          rtspPort = rtspPort,
          username = username.trim(),
          password = password,
          channelsZeroBased = channelsZeroBased,
          existingSlots = cameras.value,
        )
      result
        .onSuccess { res ->
          settingsRepo.saveCameras(res.slots)
          _onvifStatusMessage.value = res.statusMessage
          onCompleted(true, res.statusMessage)
        }
        .onFailure { err ->
          val msg = err.localizedMessage ?: "Chyba při připojování k NVR"
          _onvifStatusMessage.value = msg
          onCompleted(false, msg)
        }
      resetSettingsInactivityTimer()
    }
  }

  fun importStreamsFromGo2Rtc(
    host: String,
    apiPort: Int,
    rtspPort: Int,
    onCompleted: (Boolean, String) -> Unit,
  ) {
    resetSettingsInactivityTimer()
    viewModelScope.launch {
      go2RtcClient.fetchStreams(host = host, apiPort = apiPort, rtspPort = rtspPort)
        .onSuccess { res ->
          val topFour = res.streams.take(4)
          val updatedSlots =
            (0..3).map { idx ->
              val stream = topFour.getOrNull(idx)
              if (stream != null) {
                CameraSlot(
                  slotIndex = idx,
                  name = stream.name.take(24),
                  streamUrl = stream.rtspUrl,
                  onvifDeviceAddress = host.trim(),
                  protocolLabel = "go2rtc",
                )
              } else {
                cameras.value.getOrNull(idx) ?: CameraSlot(slotIndex = idx)
              }
            }
          settingsRepo.saveCameras(updatedSlots)
          _onvifStatusMessage.value = res.statusMessage
          onCompleted(true, res.statusMessage)
        }
        .onFailure { err ->
          val msg = err.localizedMessage ?: "Nepodařilo se připojit k go2rtc"
          _onvifStatusMessage.value = msg
          onCompleted(false, msg)
        }
      resetSettingsInactivityTimer()
    }
  }

  fun registerEseeCloudInGo2Rtc(
    go2RtcHost: String,
    apiPort: Int,
    rtspPort: Int,
    nvrIp: String,
    nvrPort: Int,
    username: String,
    password: String,
    channelsZeroBased: List<Int>,
    streamQualityIndex: Int,
    onCompleted: (Boolean, String) -> Unit,
  ) {
    resetSettingsInactivityTimer()
    viewModelScope.launch {
      go2RtcClient.registerEseeCloudBubbleStreams(
        go2RtcHost = go2RtcHost,
        apiPort = apiPort,
        rtspPort = rtspPort,
        nvrIp = nvrIp,
        nvrPort = nvrPort,
        username = username,
        password = password,
        channelsZeroBased = channelsZeroBased,
        streamQualityIndex = streamQualityIndex,
      )
        .onSuccess { res ->
          val current = cameras.value
          val updatedSlots =
            (0..3).map { idx ->
              val stream = res.streams.getOrNull(idx)
              if (stream != null) {
                CameraSlot(
                  slotIndex = idx,
                  name = stream.name.uppercase(),
                  streamUrl = stream.rtspUrl,
                  onvifDeviceAddress = nvrIp.trim(),
                  protocolLabel = "go2rtc",
                )
              } else {
                current.getOrNull(idx)?.copy(slotIndex = idx) ?: CameraSlot(slotIndex = idx)
              }
            }
          settingsRepo.saveCameras(updatedSlots)
          _onvifStatusMessage.value = res.statusMessage
          onCompleted(true, res.statusMessage)
        }
        .onFailure { err ->
          val msg = err.localizedMessage ?: "Nepodařilo se odeslat streamy do go2rtc"
          _onvifStatusMessage.value = msg
          onCompleted(false, msg)
        }
      resetSettingsInactivityTimer()
    }
  }

  fun saveCameraSlot(slot: CameraSlot) {
    resetSettingsInactivityTimer()
    viewModelScope.launch { saveSingleSlot(slot) }
  }

  fun clearCameraSlot(slotIndex: Int) {
    resetSettingsInactivityTimer()
    viewModelScope.launch { saveSingleSlot(CameraSlot(slotIndex = slotIndex)) }
  }

  fun moveCameraSlot(fromIndex: Int, toIndex: Int) {
    resetSettingsInactivityTimer()
    if (fromIndex !in 0..3 || toIndex !in 0..3 || fromIndex == toIndex) return
    val current = cameras.value.toMutableList()
    val a = current[fromIndex]
    val b = current[toIndex]
    current[fromIndex] = b.copy(slotIndex = fromIndex)
    current[toIndex] = a.copy(slotIndex = toIndex)
    viewModelScope.launch { settingsRepo.saveCameras(current) }
  }

  private suspend fun saveSingleSlot(slot: CameraSlot) {
    val updated =
      cameras.value.map { existing ->
        if (existing.slotIndex == slot.slotIndex) slot else existing
      }
    settingsRepo.saveCameras(updated)
  }

  // --- Online Radio Controls & Internet Directory ---

  fun selectRadioStation(station: RadioStation) {
    selectActiveRadioStation(ActiveRadioStation.fromPreset(station))
  }

  fun selectActiveRadioStation(station: ActiveRadioStation) {
    radioController.setSelectedActiveStation(
      station = station,
      preset = RadioStation.fromId(station.id),
    )
    viewModelScope.launch { settingsRepo.saveSelectedRadioId(station.id) }
  }

  fun searchInternetRadios(query: String) {
    resetSettingsInactivityTimer()
    if (query.trim().length < 2) {
      _radioSearchResults.value = emptyList()
      _radioSearchError.value = null
      return
    }
    viewModelScope.launch {
      _isSearchingRadios.value = true
      _radioSearchError.value = null
      runCatching { radioDirectoryClient.searchStations(query) }
        .onSuccess { results ->
          _radioSearchResults.value = results
          if (results.isEmpty()) {
            _radioSearchError.value = "Žádná stanice nenalezena"
          }
        }
        .onFailure { err ->
          _radioSearchError.value = err.localizedMessage ?: "Chyba vyhledávání rádií"
        }
      _isSearchingRadios.value = false
    }
  }

  fun loadCzechInternetRadios() {
    resetSettingsInactivityTimer()
    viewModelScope.launch {
      _isSearchingRadios.value = true
      _radioSearchError.value = null
      runCatching { radioDirectoryClient.fetchCzechStations() }
        .onSuccess { results ->
          _radioSearchResults.value = results
          if (results.isEmpty()) {
            _radioSearchError.value = "Žádná česká stanice nenalezena"
          }
        }
        .onFailure { err ->
          _radioSearchError.value = err.localizedMessage ?: "Nepodařilo se načíst česká rádia"
        }
      _isSearchingRadios.value = false
    }
  }

  fun toggleFavoriteRadio(station: RadioDirectoryStation) {
    resetSettingsInactivityTimer()
    val fav = station.toFavorite()
    val current = favoriteRadios.value
    val exists = current.any { it.id == fav.id || it.streamUrl.equals(fav.streamUrl, ignoreCase = true) }
    val updated =
      if (exists) {
        current.filterNot { it.id == fav.id || it.streamUrl.equals(fav.streamUrl, ignoreCase = true) }
      } else {
        current + fav
      }
    viewModelScope.launch { settingsRepo.saveFavoriteRadios(updated) }
  }

  fun removeFavoriteRadio(favoriteId: String) {
    resetSettingsInactivityTimer()
    val updated = favoriteRadios.value.filterNot { it.id == favoriteId }
    viewModelScope.launch {
      settingsRepo.saveFavoriteRadios(updated)
      if (radioState.value.activeStation.id == favoriteId) {
        selectRadioStation(RadioStation.KISS)
      }
    }
  }

  fun toggleRadioPlayStop() {
    radioController.togglePlayStop()
  }

  fun increaseRadioVolume() {
    radioController.increaseVolume()
  }

  fun decreaseRadioVolume() {
    radioController.decreaseVolume()
  }

  override fun onCleared() {
    super.onCleared()
    fullscreenTimerJob?.cancel()
    settingsInactivityJob?.cancel()
    ambientLightMonitor.stop()
    batteryMonitor.stop()
    radioController.release()
  }
}
