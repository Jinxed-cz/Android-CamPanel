package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppColorSchemePreset
import com.example.model.AppLanguage
import com.example.model.AppearanceSettings
import com.example.model.CameraSlot
import com.example.model.ClockStylePreset
import com.example.model.DisplaySettings
import com.example.model.FavoriteRadioStation
import com.example.model.RadioStation
import com.example.model.WeatherColorThemePreset
import com.example.model.WeatherLocation
import com.example.model.WeatherMetricToggle
import com.example.model.WeatherProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dashboardDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "campanel_prefs")

class SettingsRepository(private val context: Context) {

  private val moshi = Moshi.Builder().build()
  private val cameraListType =
    Types.newParameterizedType(List::class.java, CameraSlot::class.java)
  private val cameraListAdapter = moshi.adapter<List<CameraSlot>>(cameraListType)
  private val weatherListType =
    Types.newParameterizedType(List::class.java, WeatherLocation::class.java)
  private val weatherListAdapter = moshi.adapter<List<WeatherLocation>>(weatherListType)
  private val favoriteRadioListType =
    Types.newParameterizedType(List::class.java, FavoriteRadioStation::class.java)
  private val favoriteRadioListAdapter =
    moshi.adapter<List<FavoriteRadioStation>>(favoriteRadioListType)

  private object Keys {
    val CAMERAS_JSON = stringPreferencesKey("cameras_json")
    val LOC_NAME = stringPreferencesKey("weather_loc_name")
    val LOC_REGION = stringPreferencesKey("weather_loc_region")
    val LOC_LAT = doublePreferencesKey("weather_loc_lat")
    val LOC_LON = doublePreferencesKey("weather_loc_lon")
    val WEATHER_PROVIDER = stringPreferencesKey("weather_provider")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val SHOW_RAIN = booleanPreferencesKey("show_rain")
    val SHOW_SNOW = booleanPreferencesKey("show_snow")
    val SHOW_TEMP = booleanPreferencesKey("show_temp")
    val SHOW_PRESSURE = booleanPreferencesKey("show_pressure")
    val RADIO_STATION_ID = stringPreferencesKey("radio_station_id")
    val DISPLAY_KEEP_SCREEN_ON = booleanPreferencesKey("display_keep_screen_on")
    val DISPLAY_AUTO_BRIGHTNESS = booleanPreferencesKey("display_auto_brightness")
    val DISPLAY_NIGHT_MODE_ENABLED = booleanPreferencesKey("display_night_mode_enabled")
    val DISPLAY_NIGHT_START_HOUR = intPreferencesKey("display_night_start_hour")
    val DISPLAY_NIGHT_START_MIN = intPreferencesKey("display_night_start_min")
    val DISPLAY_NIGHT_END_HOUR = intPreferencesKey("display_night_end_hour")
    val DISPLAY_NIGHT_END_MIN = intPreferencesKey("display_night_end_min")
    val DISPLAY_NIGHT_BRIGHTNESS_PCT = intPreferencesKey("display_night_brightness_pct")
    val SAVED_WEATHER_LOCATIONS_JSON = stringPreferencesKey("saved_weather_locations_json")
    val FAVORITE_RADIOS_JSON = stringPreferencesKey("favorite_radios_json")
    val APPEARANCE_APP_SCHEME = stringPreferencesKey("appearance_app_scheme")
    val APPEARANCE_WEATHER_THEME = stringPreferencesKey("appearance_weather_theme")
    val APPEARANCE_CLOCK_STYLE = stringPreferencesKey("appearance_clock_style")
    val APPEARANCE_CLOCK_SECONDS = booleanPreferencesKey("appearance_clock_seconds")
    val APPEARANCE_BATTERY_VISIBLE = booleanPreferencesKey("appearance_battery_visible")
  }

  val camerasFlow: Flow<List<CameraSlot>> =
    context.dashboardDataStore.data.map { prefs ->
      val raw = prefs[Keys.CAMERAS_JSON]
      val parsed =
        if (!raw.isNullOrBlank()) {
          runCatching { cameraListAdapter.fromJson(raw) }.getOrNull()
        } else {
          null
        }
      normalizeSlots(parsed)
    }

  val weatherLocationFlow: Flow<WeatherLocation> =
    context.dashboardDataStore.data.map { prefs ->
      WeatherLocation(
        name = prefs[Keys.LOC_NAME] ?: "Praha",
        region = prefs[Keys.LOC_REGION] ?: "Hlavní město Praha, CZ",
        latitude = prefs[Keys.LOC_LAT] ?: 50.0755,
        longitude = prefs[Keys.LOC_LON] ?: 14.4378,
      )
    }

  val weatherProviderFlow: Flow<WeatherProvider> =
    context.dashboardDataStore.data.map { prefs ->
      WeatherProvider.fromId(prefs[Keys.WEATHER_PROVIDER])
    }

  val appLanguageFlow: Flow<AppLanguage> =
    context.dashboardDataStore.data.map { prefs ->
      AppLanguage.fromId(prefs[Keys.APP_LANGUAGE])
    }

  val weatherTogglesFlow: Flow<WeatherMetricToggle> =
    context.dashboardDataStore.data.map { prefs ->
      WeatherMetricToggle(
        showRain = prefs[Keys.SHOW_RAIN] ?: true,
        showSnow = prefs[Keys.SHOW_SNOW] ?: true,
        showTemperature = prefs[Keys.SHOW_TEMP] ?: true,
        showPressure = prefs[Keys.SHOW_PRESSURE] ?: true,
      )
    }

  val radioStationFlow: Flow<RadioStation> =
    context.dashboardDataStore.data.map { prefs ->
      RadioStation.fromId(prefs[Keys.RADIO_STATION_ID])
    }

  val selectedRadioIdFlow: Flow<String> =
    context.dashboardDataStore.data.map { prefs ->
      prefs[Keys.RADIO_STATION_ID] ?: RadioStation.KISS.id
    }

  val savedWeatherLocationsFlow: Flow<List<WeatherLocation>> =
    context.dashboardDataStore.data.map { prefs ->
      val raw = prefs[Keys.SAVED_WEATHER_LOCATIONS_JSON]
      if (!raw.isNullOrBlank()) {
        runCatching { weatherListAdapter.fromJson(raw) }.getOrNull().orEmpty()
      } else {
        emptyList()
      }
    }

  val favoriteRadiosFlow: Flow<List<FavoriteRadioStation>> =
    context.dashboardDataStore.data.map { prefs ->
      val raw = prefs[Keys.FAVORITE_RADIOS_JSON]
      if (!raw.isNullOrBlank()) {
        runCatching { favoriteRadioListAdapter.fromJson(raw) }.getOrNull().orEmpty()
      } else {
        emptyList()
      }
    }

  val appearanceSettingsFlow: Flow<AppearanceSettings> =
    context.dashboardDataStore.data.map { prefs ->
      AppearanceSettings(
        appColorSchemeId =
          AppColorSchemePreset.fromId(prefs[Keys.APPEARANCE_APP_SCHEME]).id,
        weatherColorThemeId =
          WeatherColorThemePreset.fromId(prefs[Keys.APPEARANCE_WEATHER_THEME]).id,
        clockStyleId =
          ClockStylePreset.fromId(prefs[Keys.APPEARANCE_CLOCK_STYLE]).id,
        showClockSeconds = prefs[Keys.APPEARANCE_CLOCK_SECONDS] ?: true,
        showBatteryStatus = prefs[Keys.APPEARANCE_BATTERY_VISIBLE] ?: false,
      )
    }

  val displaySettingsFlow: Flow<DisplaySettings> =
    context.dashboardDataStore.data.map { prefs ->
      DisplaySettings(
        keepScreenOn = prefs[Keys.DISPLAY_KEEP_SCREEN_ON] ?: true,
        autoBrightnessEnabled = prefs[Keys.DISPLAY_AUTO_BRIGHTNESS] ?: true,
        nightModeEnabled = prefs[Keys.DISPLAY_NIGHT_MODE_ENABLED] ?: false,
        nightStartHour = (prefs[Keys.DISPLAY_NIGHT_START_HOUR] ?: 22).coerceIn(0, 23),
        nightStartMinute = (prefs[Keys.DISPLAY_NIGHT_START_MIN] ?: 0).coerceIn(0, 59),
        nightEndHour = (prefs[Keys.DISPLAY_NIGHT_END_HOUR] ?: 6).coerceIn(0, 23),
        nightEndMinute = (prefs[Keys.DISPLAY_NIGHT_END_MIN] ?: 0).coerceIn(0, 59),
        nightBrightnessPercent = (prefs[Keys.DISPLAY_NIGHT_BRIGHTNESS_PCT] ?: 3).coerceIn(1, 50),
      )
    }

  suspend fun saveCameras(slots: List<CameraSlot>) {
    val normalized = normalizeSlots(slots)
    context.dashboardDataStore.edit { prefs ->
      prefs[Keys.CAMERAS_JSON] = cameraListAdapter.toJson(normalized)
    }
  }

  suspend fun saveWeatherLocation(location: WeatherLocation) {
    context.dashboardDataStore.edit { prefs ->
      prefs[Keys.LOC_NAME] = location.name
      prefs[Keys.LOC_REGION] = location.region
      prefs[Keys.LOC_LAT] = location.latitude
      prefs[Keys.LOC_LON] = location.longitude
    }
  }

  suspend fun saveWeatherProvider(provider: WeatherProvider) {
    context.dashboardDataStore.edit { prefs -> prefs[Keys.WEATHER_PROVIDER] = provider.id }
  }

  suspend fun saveAppLanguage(language: AppLanguage) {
    context.dashboardDataStore.edit { prefs -> prefs[Keys.APP_LANGUAGE] = language.id }
  }

  suspend fun saveWeatherToggles(toggles: WeatherMetricToggle) {
    context.dashboardDataStore.edit { prefs ->
      prefs[Keys.SHOW_RAIN] = toggles.showRain
      prefs[Keys.SHOW_SNOW] = toggles.showSnow
      prefs[Keys.SHOW_TEMP] = toggles.showTemperature
      prefs[Keys.SHOW_PRESSURE] = toggles.showPressure
    }
  }

  suspend fun saveRadioStation(station: RadioStation) {
    context.dashboardDataStore.edit { prefs -> prefs[Keys.RADIO_STATION_ID] = station.id }
  }

  suspend fun saveSelectedRadioId(radioId: String) {
    context.dashboardDataStore.edit { prefs -> prefs[Keys.RADIO_STATION_ID] = radioId }
  }

  suspend fun saveSavedWeatherLocations(locations: List<WeatherLocation>) {
    context.dashboardDataStore.edit { prefs ->
      prefs[Keys.SAVED_WEATHER_LOCATIONS_JSON] = weatherListAdapter.toJson(locations)
    }
  }

  suspend fun saveFavoriteRadios(favorites: List<FavoriteRadioStation>) {
    context.dashboardDataStore.edit { prefs ->
      prefs[Keys.FAVORITE_RADIOS_JSON] = favoriteRadioListAdapter.toJson(favorites)
    }
  }

  suspend fun saveAppearanceSettings(settings: AppearanceSettings) {
    context.dashboardDataStore.edit { prefs ->
      prefs[Keys.APPEARANCE_APP_SCHEME] = settings.appColorSchemeId
      prefs[Keys.APPEARANCE_WEATHER_THEME] = settings.weatherColorThemeId
      prefs[Keys.APPEARANCE_CLOCK_STYLE] = settings.clockStyleId
      prefs[Keys.APPEARANCE_CLOCK_SECONDS] = settings.showClockSeconds
      prefs[Keys.APPEARANCE_BATTERY_VISIBLE] = settings.showBatteryStatus
    }
  }

  suspend fun saveDisplaySettings(settings: DisplaySettings) {
    context.dashboardDataStore.edit { prefs ->
      prefs[Keys.DISPLAY_KEEP_SCREEN_ON] = settings.keepScreenOn
      prefs[Keys.DISPLAY_AUTO_BRIGHTNESS] = settings.autoBrightnessEnabled
      prefs[Keys.DISPLAY_NIGHT_MODE_ENABLED] = settings.nightModeEnabled
      prefs[Keys.DISPLAY_NIGHT_START_HOUR] = settings.nightStartHour.coerceIn(0, 23)
      prefs[Keys.DISPLAY_NIGHT_START_MIN] = settings.nightStartMinute.coerceIn(0, 59)
      prefs[Keys.DISPLAY_NIGHT_END_HOUR] = settings.nightEndHour.coerceIn(0, 23)
      prefs[Keys.DISPLAY_NIGHT_END_MIN] = settings.nightEndMinute.coerceIn(0, 59)
      prefs[Keys.DISPLAY_NIGHT_BRIGHTNESS_PCT] = settings.nightBrightnessPercent.coerceIn(1, 50)
    }
  }

  private fun normalizeSlots(raw: List<CameraSlot>?): List<CameraSlot> {
    val byIdx = raw?.associateBy { it.slotIndex } ?: emptyMap()
    return (0..3).map { idx ->
      byIdx[idx]?.copy(slotIndex = idx) ?: CameraSlot(slotIndex = idx)
    }
  }
}
