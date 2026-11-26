package com.example.model

import com.squareup.moshi.JsonClass
import java.net.URLEncoder

@JsonClass(generateAdapter = true)
data class CameraSlot(
  val slotIndex: Int,
  val name: String = "Kamera ${slotIndex + 1}",
  val streamUrl: String = "",
  val username: String = "",
  val password: String = "",
  val onvifDeviceAddress: String = "",
  val protocolLabel: String = "RTSP",
  val recordingEnabled: Boolean = false,
  val recordingQualityId: String = RecordingQuality.SD.id,
  val recordingMaxSizeMb: Int = 2048,
  val recordingRetentionDays: Int = 7,
  val recordingModeId: String = RecordingMode.CONTINUOUS.id,
  val motionSensitivity: Int = 50,
  val motionEventDurationMinutes: Int = 2,
) {
  val isConfigured: Boolean
    get() = streamUrl.isNotBlank()

  val recordingQuality: RecordingQuality
    get() = RecordingQuality.fromId(recordingQualityId)

  val recordingMode: RecordingMode
    get() = RecordingMode.fromId(recordingModeId)

  /**
   * Builds the effective playback URL, injecting RTSP Basic/Digest credentials if provided and not
   * already embedded in the URL, or populating `u=`/`p=` query parameters for HTTP CGI streams.
   */
  fun effectivePlaybackUrl(): String {
    val trimmed = streamUrl.trim()
    if (trimmed.isEmpty()) return ""
    val isHttp =
      trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)

    if (isHttp) {
      if (username.isBlank()) return trimmed
      val encodedUser = URLEncoder.encode(username.trim(), "UTF-8").replace("+", "%20")
      val encodedPass = URLEncoder.encode(password, "UTF-8").replace("+", "%20")
      return trimmed
        .replace(Regex("([?&])u=[^&]*"), "$1u=$encodedUser")
        .replace(Regex("([?&])p=[^&]*"), "$1p=$encodedPass")
    }

    if (username.isBlank() || trimmed.contains("@")) return trimmed

    val schemeIdx = trimmed.indexOf("://")
    if (schemeIdx <= 0) return trimmed

    val scheme = trimmed.substring(0, schemeIdx + 3)
    val rest = trimmed.substring(schemeIdx + 3)
    val encodedUser = URLEncoder.encode(username.trim(), "UTF-8").replace("+", "%20")
    val encodedPass = URLEncoder.encode(password, "UTF-8").replace("+", "%20")
    return "$scheme$encodedUser:$encodedPass@$rest"
  }

  /** Safe display label without exposing passwords. */
  fun maskedUrlDisplay(language: AppLanguage = AppLanguage.CS): String {
    val trimmed = streamUrl.trim()
    if (trimmed.isEmpty()) return if (language == AppLanguage.EN) "Not configured" else "Nenastaveno"
    return trimmed
      .replace(Regex("://([^:@]+):([^@]+)@"), "://$1:***@")
      .replace(Regex("([?&])p=[^&]+"), "$1p=***")
  }

  companion object {
    const val DEFAULT_MANUAL_RTSP_PORT = 80
    const val DEFAULT_MANUAL_USERNAME = "admin"
    const val DEFAULT_MANUAL_RTSP_PATH = "/stream1"

    fun extractHost(streamUrl: String, fallbackAddress: String = ""): String {
      val trimmed = streamUrl.trim()
      val match =
        Regex("^(?:rtsp|https?)://(?:[^:@/]+(?::[^@/]*)?@)?([^:/?#]+)", RegexOption.IGNORE_CASE)
          .find(trimmed)
      return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: fallbackAddress.trim()
    }

    fun extractPort(streamUrl: String, defaultPort: String = DEFAULT_MANUAL_RTSP_PORT.toString()): String {
      val trimmed = streamUrl.trim()
      val match =
        Regex("^(?:rtsp|https?)://(?:[^:@/]+(?::[^@/]*)?@)?[^:/?#]+:(\\d+)", RegexOption.IGNORE_CASE)
          .find(trimmed)
      return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: defaultPort
    }

    fun extractPath(streamUrl: String, defaultPath: String = DEFAULT_MANUAL_RTSP_PATH): String {
      val trimmed = streamUrl.trim()
      if (trimmed.isEmpty()) return defaultPath
      val match =
        Regex("^(?:rtsp|https?)://(?:[^:@/]+(?::[^@/]*)?@)?[^:/?#]+(?::\\d+)?(/.*)$", RegexOption.IGNORE_CASE)
          .find(trimmed)
      val path = match?.groupValues?.getOrNull(1)
      return if (path.isNullOrBlank()) defaultPath else path
    }

    fun buildManualStreamUrl(
      ipAddress: String,
      portText: String = DEFAULT_MANUAL_RTSP_PORT.toString(),
      path: String = DEFAULT_MANUAL_RTSP_PATH,
      isHttp: Boolean = false,
      useHttps: Boolean = false,
    ): String {
      val cleanIp = ipAddress.trim()
      if (cleanIp.isEmpty()) return ""
      val defaultPort = if (useHttps) 443 else DEFAULT_MANUAL_RTSP_PORT
      val cleanPort = portText.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: defaultPort
      val normalizedPath =
        if (path.isBlank()) "/"
        else if (path.startsWith("/")) path else "/$path"
      val scheme =
        when {
          useHttps -> "https"
          isHttp -> "http"
          else -> "rtsp"
        }
      return "$scheme://$cleanIp:$cleanPort$normalizedPath"
    }

    fun normalizeManualUrl(
      rawInput: String,
      defaultIsHttp: Boolean = false,
      useHttps: Boolean = false,
    ): String {
      val trimmed = rawInput.trim()
      if (trimmed.isEmpty()) return ""
      if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) {
        return trimmed
      }
      val scheme =
        when {
          useHttps -> "https://"
          defaultIsHttp -> "http://"
          else -> "rtsp://"
        }
      return "$scheme$trimmed"
    }

    fun isHttpVideoOrMjpegUrl(url: String): Boolean {
      val lower = url.trim().lowercase(java.util.Locale.ROOT)
      if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
      return lower.contains("/livestream/") ||
        lower.contains(".m3u8") ||
        lower.contains(".ts") ||
        lower.contains(".mp4") ||
        lower.contains(".mjpg") ||
        lower.contains("/mjpeg") ||
        lower.contains("/video") ||
        lower.contains("videostream") ||
        lower.contains("action=stream")
    }

    fun extractChannelZeroBased(streamUrl: String, defaultChannel: Int = 0): Int {
      val trimmed = streamUrl.trim()
      if (trimmed.isEmpty()) return defaultChannel.coerceIn(0, 7)
      Regex("/live/ch(\\d{2})_").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it.coerceIn(0, 7)
      }
      Regex("/ch(\\d+)_").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it.coerceIn(0, 7)
      }
      Regex("chn=(\\d+)").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it.coerceIn(0, 7)
      }
      Regex("channel=(\\d+)").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return (it - 1).coerceIn(0, 7)
      }
      Regex("/kam(\\d+)$").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return (it - 1).coerceIn(0, 7)
      }
      Regex("/Streaming/Channels/(\\d+)0[12]").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return (it - 1).coerceIn(0, 7)
      }
      return defaultChannel.coerceIn(0, 7)
    }

    fun extractStreamQualityIndex(streamUrl: String, defaultQuality: Int = 0): Int {
      val trimmed = streamUrl.trim()
      if (trimmed.isEmpty()) return defaultQuality.coerceIn(0, 1)
      Regex("/(?:live/)?ch\\d+_(\\d+)").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return if (it > 0) 1 else 0
      }
      Regex("(?:subtype|stream)=(\\d+)").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return if (it > 0) 1 else 0
      }
      Regex("/Streaming/Channels/\\d+0([12])").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return if (it == 2) 1 else 0
      }
      return defaultQuality.coerceIn(0, 1)
    }

    fun applyChannelAndQuality(
      streamUrl: String,
      fallbackIp: String,
      fallbackPort: String,
      channelZeroBased: Int,
      qualityIndex: Int,
    ): String {
      val ch0 = channelZeroBased.coerceIn(0, 7)
      val q = qualityIndex.coerceIn(0, 1)
      val chTwoDigit = String.format(java.util.Locale.US, "%02d", ch0)
      val chOneBased = ch0 + 1
      val trimmed = streamUrl.trim()
      val host = extractHost(trimmed, fallbackIp).ifBlank { "192.168.1.50" }
      val port = extractPort(trimmed, fallbackPort.ifBlank { DEFAULT_MANUAL_RTSP_PORT.toString() })

      if (trimmed.isEmpty()) {
        return buildManualStreamUrl(host, port, "/ch${ch0}_${q}.264")
      }
      return when {
        Regex("/live/ch\\d{2}_\\d+").containsMatchIn(trimmed) ->
          trimmed.replace(Regex("/live/ch\\d{2}_\\d+"), "/live/ch${chTwoDigit}_$q")
        Regex("/ch\\d+_\\d+").containsMatchIn(trimmed) ->
          trimmed.replace(Regex("/ch\\d+_\\d+"), "/ch${ch0}_$q")
        Regex("chn=\\d+").containsMatchIn(trimmed) ->
          trimmed.replace(Regex("chn=\\d+"), "chn=$ch0")
        Regex("channel=\\d+").containsMatchIn(trimmed) -> {
          var updated = trimmed.replace(Regex("channel=\\d+"), "channel=$chOneBased")
          updated = updated.replace(Regex("subtype=\\d+"), "subtype=$q")
          updated = updated.replace(Regex("stream=\\d+"), "stream=$q")
          updated
        }
        Regex("/kam\\d+$").containsMatchIn(trimmed) ->
          trimmed.replace(Regex("/kam\\d+$"), "/kam$chOneBased")
        Regex("/Streaming/Channels/\\d+0[12]").containsMatchIn(trimmed) ->
          trimmed.replace(Regex("/Streaming/Channels/\\d+0[12]"), "/Streaming/Channels/${chOneBased}0${q + 1}")
        else ->
          buildManualStreamUrl(host, port, "/ch${ch0}_${q}.264")
      }
    }
  }
}

data class DiscoveredNetworkCamera(
  val id: String,
  val name: String,
  val ipAddress: String,
  val discoveryType: String, // "ONVIF", "EseeCloud", "XMEye", "Dahua", "RTSP", "HTTP/MJPEG", "mDNS", "SSDP"
  val xAddrs: String = "",
  val candidateStreamUrl: String = "",
  val rtspPort: Int = 554,
  val hardwareInfo: String = "",
  val isNvr: Boolean = false,
  val detectedChannels: Int = 1,
  val resolvedStreamUrl: String? = null,
  val isResolvingUri: Boolean = false,
  val resolveError: String? = null,
)

enum class NvrProtocolPreset(
  val id: String,
  val label: String,
  val defaultRtspPort: Int,
) {
  ESEECLOUD_JOOAN("EseeCloud", "EseeCloud", 554),
  ONVIF("ONVIF", "ONVIF NVR (port 8899 / 80)", 554),
  XMEYE("XMEye", "XMEye / NetSurveillance", 554),
  DAHUA("Dahua", "Dahua / Generic NVR", 554),
  HIKVISION("Hikvision", "Hikvision NVR", 554);

  fun labelFor(language: AppLanguage): String =
    if (language == AppLanguage.EN) {
      when (this) {
        ESEECLOUD_JOOAN -> "EseeCloud"
        ONVIF -> "ONVIF NVR (port 8899 / 80)"
        XMEYE -> "XMEye / NetSurveillance"
        DAHUA -> "Dahua / Generic NVR"
        HIKVISION -> "Hikvision NVR"
      }
    } else {
      label
    }
}

data class RtspPathPreset(
  val label: String,
  val path: String,
  val isHttp: Boolean = false,
  val defaultPort: Int? = null,
) {
  companion object {
    val CommonPresets =
      listOf(
        RtspPathPreset("Tapo / Obecná", "/stream1"),
        RtspPathPreset("EseeCloud (RTSP)", "/ch0_0.264"),
        RtspPathPreset("EseeCloud NVR (/live)", "/live/ch00_0"),
        RtspPathPreset("EseeCloud (HTTP)", "/cgi-bin/snapshot.cgi?chn=0&u=admin&p=", isHttp = true),
        RtspPathPreset("XMEye / NetSurv", "/user=admin&password=&channel=1&stream=0.sdp"),
        RtspPathPreset("Dahua / Generic", "/cam/realmonitor?channel=1&subtype=1"),
        RtspPathPreset("Hikvision", "/Streaming/Channels/101"),
        RtspPathPreset("Reolink", "/h264Preview_01_sub"),
        RtspPathPreset("Xiaomi / Dafang", "/unicast"),
      )

    val IpCameraPresets =
      listOf(
        RtspPathPreset("IP Webcam (/video)", "/video", isHttp = true, defaultPort = 8080),
        RtspPathPreset("IP Webcam (/shot.jpg)", "/shot.jpg", isHttp = true, defaultPort = 8080),
        RtspPathPreset("MJPEG (/mjpeg)", "/mjpeg", isHttp = true, defaultPort = 80),
        RtspPathPreset("MJPEG (/mjpg/video.mjpg)", "/mjpg/video.mjpg", isHttp = true, defaultPort = 80),
        RtspPathPreset("Snapshot (/snapshot.jpg)", "/snapshot.jpg", isHttp = true, defaultPort = 80),
        RtspPathPreset("ESP32-CAM (/capture)", "/capture", isHttp = true, defaultPort = 81),
        RtspPathPreset("EseeCloud (HTTP)", "/cgi-bin/snapshot.cgi?chn=0&u=admin&p=", isHttp = true, defaultPort = 80),
        RtspPathPreset("HLS (/stream.m3u8)", "/stream.m3u8", isHttp = true, defaultPort = 80),
      )
  }
}

enum class AppLanguage(val id: String, val shortLabel: String) {
  CS("cs", "CZ"),
  EN("en", "ENG");

  companion object {
    fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: CS
  }
}

enum class WeatherProvider(
  val id: String,
  val badgeText: String,
  val labelCs: String,
  val labelEn: String,
) {
  ALADIN("aladin", "ALADIN", "Aladin (ČR / Střední Evropa)", "Aladin (Central Europe)"),
  OPEN_METEO_GLOBAL("open_meteo", "GLOBAL", "Open-Meteo (Celosvětově)", "Open-Meteo (Worldwide)"),
  MET_NORWAY("yr_no", "YR.NO", "Yr.no / Met.no (Celosvětově)", "Yr.no / Met.no (Worldwide)");

  fun labelFor(language: AppLanguage): String = if (language == AppLanguage.EN) labelEn else labelCs

  companion object {
    fun fromId(id: String?): WeatherProvider = entries.firstOrNull { it.id == id } ?: ALADIN
  }
}

@JsonClass(generateAdapter = true)
data class WeatherLocation(
  val name: String = "Praha",
  val region: String = "Hlavní město Praha, CZ",
  val latitude: Double = 50.0755,
  val longitude: Double = 14.4378,
) {
  companion object {
    val CzechPresets =
      listOf(
        WeatherLocation("Praha", "Hlavní město Praha, CZ", 50.0755, 14.4378),
        WeatherLocation("Brno", "Jihomoravský kraj, CZ", 49.1951, 16.6068),
        WeatherLocation("Ostrava", "Moravskoslezský kraj, CZ", 49.8209, 18.2625),
        WeatherLocation("Plzeň", "Plzeňský kraj, CZ", 49.7384, 13.3736),
        WeatherLocation("Liberec", "Liberecký kraj, CZ", 50.7663, 15.0543),
        WeatherLocation("Olomouc", "Olomoucký kraj, CZ", 49.5938, 17.2509),
        WeatherLocation("České Budějovice", "Jihočeský kraj, CZ", 48.9745, 14.4743),
        WeatherLocation("Hradec Králové", "Královéhradecký kraj, CZ", 50.2092, 15.8328),
      )

    val GlobalPresets =
      listOf(
        WeatherLocation("London", "England, GB", 51.5074, -0.1278),
        WeatherLocation("New York", "New York, US", 40.7128, -74.0060),
        WeatherLocation("Tokyo", "Tokyo, JP", 35.6762, 139.6503),
        WeatherLocation("Paris", "Île-de-France, FR", 48.8566, 2.3522),
        WeatherLocation("Berlin", "Berlin, DE", 52.5200, 13.4050),
        WeatherLocation("Sydney", "New South Wales, AU", -33.8688, 151.2093),
      )
  }
}

@JsonClass(generateAdapter = true)
data class WeatherMetricToggle(
  val showRain: Boolean = true,
  val showSnow: Boolean = true,
  val showTemperature: Boolean = true,
  val showPressure: Boolean = true,
)

data class AladinHourPoint(
  val isoTime: String,
  val hourInt: Int,
  val hourLabel: String,
  val dayIndex: Int,
  val temperatureC: Double,
  val rainMm: Double,
  val snowCm: Double,
  val pressureHpa: Double,
  val weatherCode: Int,
)

data class AladinDaySummary(
  val dayIndex: Int,
  val title: String,
  val dateShort: String,
  val minTempC: Double,
  val maxTempC: Double,
  val totalRainMm: Double,
  val totalSnowCm: Double,
  val avgPressureHpa: Double,
  val dominantWeatherCode: Int,
)

data class AladinForecastData(
  val locationName: String,
  val updatedAtLabel: String,
  val hourlyPoints: List<AladinHourPoint>,
  val daySummaries: List<AladinDaySummary>,
  val provider: WeatherProvider = WeatherProvider.ALADIN,
)

sealed interface WeatherUiState {
  data object Loading : WeatherUiState

  data class Ready(val data: AladinForecastData, val isRefreshing: Boolean = false) : WeatherUiState

  data class Error(val message: String, val cachedData: AladinForecastData? = null) :
    WeatherUiState
}

enum class RadioStation(
  val id: String,
  val displayName: String,
  val shortTag: String,
  val primaryStreamUrl: String,
  val fallbackStreamUrl: String,
  val accentColorHex: Long,
) {
  KISS(
    id = "kiss",
    displayName = "Radio Kiss",
    shortTag = "KISS",
    primaryStreamUrl = "http://icecast1.play.cz/kiss128.mp3",
    fallbackStreamUrl = "http://icecast4.play.cz/kiss128.mp3",
    accentColorHex = 0xFFEC4899,
  ),
  EVROPA_2(
    id = "evropa2",
    displayName = "Evropa 2",
    shortTag = "E2",
    primaryStreamUrl = "http://ice.actve.net/fm-evropa2-128",
    fallbackStreamUrl = "https://22593.live.streamtheworld.com/EVROPA2AAC.aac",
    accentColorHex = 0xFFEF4444,
  ),
  BLANIK(
    id = "blanik",
    displayName = "Blaník",
    shortTag = "BLN",
    primaryStreamUrl = "http://ice.abradio.cz/blanikcz128.mp3",
    fallbackStreamUrl = "http://kocka.limemedia.cz:8000/blanikcz128.mp3",
    accentColorHex = 0xFFF59E0B,
  ),
  FREKVENCE_1(
    id = "frekvence1",
    displayName = "Frekvence 1",
    shortTag = "F1",
    primaryStreamUrl = "http://ice.actve.net/fm-frekvence1-128",
    fallbackStreamUrl = "https://22593.live.streamtheworld.com/FREKVENCE1AAC.aac",
    accentColorHex = 0xFF38BDF8,
  );

  companion object {
    fun fromId(id: String?): RadioStation = entries.firstOrNull { it.id == id } ?: KISS
  }
}

@JsonClass(generateAdapter = true)
data class FavoriteRadioStation(
  val id: String,
  val name: String,
  val shortTag: String,
  val streamUrl: String,
  val countryCode: String = "",
  val codec: String = "",
  val bitrateKbps: Int = 0,
  val accentColorHex: Long = 0xFF38BDF8,
)

data class RadioDirectoryStation(
  val uuid: String,
  val name: String,
  val streamUrl: String,
  val countryCode: String = "",
  val codec: String = "",
  val bitrateKbps: Int = 0,
  val tags: String = "",
) {
  fun toFavorite(): FavoriteRadioStation {
    val cleanName = name.trim().ifEmpty { "Rádio" }
    val tag =
      cleanName
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(2).uppercase() }
        .take(4)
        .ifEmpty { "RAD" }
    return FavoriteRadioStation(
      id = "fav_${uuid}",
      name = cleanName,
      shortTag = tag,
      streamUrl = streamUrl.trim(),
      countryCode = countryCode.trim().uppercase(),
      codec = codec.trim().uppercase(),
      bitrateKbps = bitrateKbps,
      accentColorHex = 0xFF38BDF8,
    )
  }
}

data class ActiveRadioStation(
  val id: String,
  val displayName: String,
  val shortTag: String,
  val primaryStreamUrl: String,
  val fallbackStreamUrl: String = "",
  val accentColorHex: Long = 0xFF38BDF8,
  val isFavorite: Boolean = false,
  val subtitle: String = "",
) {
  companion object {
    fun fromPreset(preset: RadioStation): ActiveRadioStation =
      ActiveRadioStation(
        id = preset.id,
        displayName = preset.displayName,
        shortTag = preset.shortTag,
        primaryStreamUrl = preset.primaryStreamUrl,
        fallbackStreamUrl = preset.fallbackStreamUrl,
        accentColorHex = preset.accentColorHex,
        isFavorite = false,
        subtitle = "Předvolba",
      )

    fun fromFavorite(fav: FavoriteRadioStation): ActiveRadioStation {
      val meta =
        listOfNotNull(
          fav.countryCode.takeIf { it.isNotBlank() },
          fav.codec.takeIf { it.isNotBlank() },
          fav.bitrateKbps.takeIf { it > 0 }?.let { "${it} kbps" },
        ).joinToString(" · ")
      return ActiveRadioStation(
        id = fav.id,
        displayName = fav.name,
        shortTag = fav.shortTag,
        primaryStreamUrl = fav.streamUrl,
        fallbackStreamUrl = "",
        accentColorHex = fav.accentColorHex,
        isFavorite = true,
        subtitle = meta.ifBlank { "Oblíbené" },
      )
    }
  }
}

data class RadioUiState(
  val selectedStation: RadioStation = RadioStation.KISS,
  val activeStation: ActiveRadioStation = ActiveRadioStation.fromPreset(RadioStation.KISS),
  val isPlaying: Boolean = false,
  val isBuffering: Boolean = false,
  val errorMessage: String? = null,
  val volumePercent: Int = 50,
  val isBluetoothConnected: Boolean = false,
  val outputDeviceName: String = "Reproduktor",
)

enum class AppColorSchemePreset(
  val id: String,
  val label: String,
  val labelEn: String,
  val swatchColorHex: Long,
) {
  COBALT_DARK("cobalt", "Tmavá modrá", "Cobalt Dark", 0xFF2563EB),
  EMERALD_DARK("emerald", "Smaragdová", "Emerald", 0xFF10B981),
  AMBER_DARK("amber", "Jantarová", "Amber", 0xFFF59E0B),
  VIOLET_DARK("violet", "Fialová", "Violet", 0xFF8B5CF6),
  PURE_OLED("oled", "Černá OLED", "Pure OLED", 0xFF38BDF8);

  fun labelFor(language: AppLanguage): String = if (language == AppLanguage.EN) labelEn else label

  companion object {
    fun fromId(id: String?): AppColorSchemePreset = entries.firstOrNull { it.id == id } ?: COBALT_DARK
  }
}

enum class WeatherColorThemePreset(
  val id: String,
  val label: String,
  val labelEn: String,
  val warmSwatchHex: Long,
  val coldSwatchHex: Long,
) {
  CLASSIC_ALADIN("classic", "Klasický Aladin", "Classic", 0xFFF59E0B, 0xFF3B82F6),
  NEON_CONTRAST("neon", "Kontrastní neon", "Neon Contrast", 0xFFFB7185, 0xFF22D3EE),
  ICE_BLUE("ice", "Ledově modrý", "Ice Blue", 0xFFE0F2FE, 0xFF38BDF8),
  AUTUMN_WARM("autumn", "Teplý podzimní", "Warm Autumn", 0xFFFB923C, 0xFF60A5FA);

  fun labelFor(language: AppLanguage): String = if (language == AppLanguage.EN) labelEn else label

  companion object {
    fun fromId(id: String?): WeatherColorThemePreset =
      entries.firstOrNull { it.id == id } ?: CLASSIC_ALADIN
  }
}

enum class ClockStylePreset(
  val id: String,
  val label: String,
  val labelEn: String,
) {
  DIGITAL_MONO("digital_mono", "Digitální (Mono)", "Digital (Mono)"),
  MODERN_LARGE("modern_large", "Velký moderní", "Modern Large"),
  PILL_FRAMED("pill_framed", "V rámečku", "Framed Card"),
  ACCENT_TINT("accent_tint", "Barevný akcent", "Accent Tint");

  fun labelFor(language: AppLanguage): String = if (language == AppLanguage.EN) labelEn else label

  companion object {
    fun fromId(id: String?): ClockStylePreset = entries.firstOrNull { it.id == id } ?: DIGITAL_MONO
  }
}

@JsonClass(generateAdapter = true)
data class AppearanceSettings(
  val appColorSchemeId: String = AppColorSchemePreset.COBALT_DARK.id,
  val weatherColorThemeId: String = WeatherColorThemePreset.CLASSIC_ALADIN.id,
  val clockStyleId: String = ClockStylePreset.DIGITAL_MONO.id,
  val showClockSeconds: Boolean = true,
  val showBatteryStatus: Boolean = false,
)

data class BatteryRuntimeStatus(
  val levelPercent: Int = 100,
  val isCharging: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class DisplaySettings(
  val keepScreenOn: Boolean = true,
  val autoBrightnessEnabled: Boolean = true,
  val nightModeEnabled: Boolean = false,
  val nightStartHour: Int = 22,
  val nightStartMinute: Int = 0,
  val nightEndHour: Int = 6,
  val nightEndMinute: Int = 0,
  val nightBrightnessPercent: Int = 3,
)

data class DisplayRuntimeStatus(
  val effectiveBrightness: Float = -1f, // WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
  val isNightModeActive: Boolean = false,
  val isPreviewActive: Boolean = false,
  val ambientLux: Float? = null,
  val hasLightSensor: Boolean = false,
)

object DisplayBrightnessMath {
  const val BRIGHTNESS_SYSTEM_DEFAULT = -1f
  const val MIN_SAFE_BRIGHTNESS = 0.01f
  const val MIN_AUTO_BRIGHTNESS = 0.06f
  const val MAX_AUTO_BRIGHTNESS = 1.0f

  fun formatHourMinute(hour: Int, minute: Int): String =
    String.format(java.util.Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

  fun isInNightWindow(
    hour: Int,
    minute: Int,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
  ): Boolean {
    val nowMin = (hour.coerceIn(0, 23) * 60) + minute.coerceIn(0, 59)
    val startMin = (startHour.coerceIn(0, 23) * 60) + startMinute.coerceIn(0, 59)
    val endMin = (endHour.coerceIn(0, 23) * 60) + endMinute.coerceIn(0, 59)
    return when {
      startMin == endMin -> true
      startMin < endMin -> nowMin in startMin until endMin
      else -> nowMin >= startMin || nowMin < endMin
    }
  }

  /**
   * Maps ambient light illuminance in lux onto a smooth logarithmic display brightness range
   * [0.06f..1.0f] suitable for dark rooms up to bright daylight.
   */
  fun luxToScreenBrightness(lux: Float): Float {
    val clamped = lux.coerceIn(1f, 3000f)
    val norm = (kotlin.math.ln(clamped.toDouble()) / kotlin.math.ln(3000.0)).toFloat().coerceIn(0f, 1f)
    return (MIN_AUTO_BRIGHTNESS + norm * (MAX_AUTO_BRIGHTNESS - MIN_AUTO_BRIGHTNESS))
      .coerceIn(MIN_AUTO_BRIGHTNESS, MAX_AUTO_BRIGHTNESS)
  }

  fun resolveEffectiveScreenBrightness(
    settings: DisplaySettings,
    currentHour: Int,
    currentMinute: Int,
    ambientLux: Float?,
    previewNightBrightness: Boolean = false,
  ): Pair<Float, Boolean> {
    val inNightSchedule =
      settings.nightModeEnabled &&
        isInNightWindow(
          hour = currentHour,
          minute = currentMinute,
          startHour = settings.nightStartHour,
          startMinute = settings.nightStartMinute,
          endHour = settings.nightEndHour,
          endMinute = settings.nightEndMinute,
        )
    if (previewNightBrightness || inNightSchedule) {
      val pct = settings.nightBrightnessPercent.coerceIn(1, 50)
      val level = (pct / 100f).coerceAtLeast(MIN_SAFE_BRIGHTNESS)
      return level to inNightSchedule
    }
    if (settings.autoBrightnessEnabled && ambientLux != null) {
      return luxToScreenBrightness(ambientLux) to false
    }
    return BRIGHTNESS_SYSTEM_DEFAULT to false
  }
}

enum class RecordingQuality(
  val id: String,
  val labelCs: String,
  val labelEn: String,
  val targetWidth: Int,
  val targetHeight: Int,
  val targetBitrateBps: Int,
  val targetFps: Int,
  val estimatedMbPer15Min: Int,
) {
  SD(
    id = "sd",
    labelCs = "SD (úsporné)",
    labelEn = "SD (compact)",
    targetWidth = 640,
    targetHeight = 360,
    targetBitrateBps = 420_000,
    targetFps = 10,
    estimatedMbPer15Min = 47,
  ),
  HD(
    id = "hd",
    labelCs = "HD (vysoké)",
    labelEn = "HD (high)",
    targetWidth = 1280,
    targetHeight = 720,
    targetBitrateBps = 1_300_000,
    targetFps = 15,
    estimatedMbPer15Min = 146,
  );

  fun labelFor(language: AppLanguage): String =
    if (language == AppLanguage.EN) labelEn else labelCs

  companion object {
    fun fromId(id: String?): RecordingQuality =
      entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SD
  }
}

enum class RecordingStorageLocation(
  val id: String,
  val labelCs: String,
  val labelEn: String,
) {
  UNSET(
    id = "",
    labelCs = "Složka není vybrána",
    labelEn = "No folder selected",
  ),
  APP_INTERNAL(
    id = "app_internal",
    labelCs = "Místní úložiště",
    labelEn = "Local storage",
  ),
  APP_EXTERNAL_PRIMARY(
    id = "app_external",
    labelCs = "Místní úložiště",
    labelEn = "Local storage",
  ),
  SD_CARD_APP(
    id = "sd_card",
    labelCs = "Externí úložiště (karta SD)",
    labelEn = "External storage (SD card)",
  ),
  CUSTOM_SAF_TREE(
    id = "custom_saf",
    labelCs = "Externí úložiště (karta SD)",
    labelEn = "External storage (SD card)",
  );

  fun labelFor(language: AppLanguage): String =
    if (language == AppLanguage.EN) labelEn else labelCs

  companion object {
    fun fromId(id: String?): RecordingStorageLocation =
      when {
        id.equals(APP_EXTERNAL_PRIMARY.id, ignoreCase = true) ||
          id.equals(APP_INTERNAL.id, ignoreCase = true) -> APP_EXTERNAL_PRIMARY
        id.equals(SD_CARD_APP.id, ignoreCase = true) ||
          id.equals(CUSTOM_SAF_TREE.id, ignoreCase = true) -> SD_CARD_APP
        else -> UNSET
      }
  }
}

data class RecordingStorageSettings(
  val locationTypeId: String = RecordingStorageLocation.UNSET.id,
  val customTreeUri: String = "",
  val customFolderLabel: String = "",
) {
  val locationType: RecordingStorageLocation
    get() = RecordingStorageLocation.fromId(locationTypeId)

  val isFolderConfigured: Boolean
    get() = customTreeUri.isNotBlank() && locationType != RecordingStorageLocation.UNSET
}

data class RecordedSegmentInfo(
  val id: String,
  val slotIndex: Int,
  val fileName: String,
  val uriString: String,
  val filePath: String? = null,
  val sizeBytes: Long = 0L,
  val createdAtMs: Long = 0L,
  val qualityId: String = RecordingQuality.SD.id,
  val isCustomSaf: Boolean = false,
  val isCurrentlyRecording: Boolean = false,
)

enum class RecordingMode(
  val id: String,
  val labelCs: String,
  val labelEn: String,
) {
  CONTINUOUS(
    id = "continuous",
    labelCs = "Kontinuální (15 min)",
    labelEn = "Continuous (15 min)",
  ),
  MOTION_EVENTS(
    id = "motion",
    labelCs = "Pouze při pohybu",
    labelEn = "Motion events only",
  );

  fun labelFor(language: AppLanguage): String =
    if (language == AppLanguage.EN) labelEn else labelCs

  companion object {
    fun fromId(id: String?): RecordingMode =
      entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CONTINUOUS
  }
}

data class CameraRecordingRuntimeStatus(
  val slotIndex: Int,
  val isRecording: Boolean = false,
  val isMotionArmed: Boolean = false,
  val isMotionTriggered: Boolean = false,
  val activeFileName: String? = null,
  val activeSegmentStartedMs: Long = 0L,
  val usedBytes: Long = 0L,
  val segmentCount: Int = 0,
  val lastError: String? = null,
)

data class RecordingStorageStats(
  val usedByAppBytes: Long = 0L,
  val availableFreeBytes: Long = 0L,
  val totalDeviceBytes: Long = 0L,
  val resolvedPathLabel: String = "",
  val hasSdCard: Boolean = false,
)

object RecordingRetentionPolicy {
  const val SEGMENT_DURATION_MS = 15L * 60L * 1000L // 15-minute segments
  const val MIN_DISK_FREE_BYTES = 200L * 1024L * 1024L // keep 200 MB headroom on filesystem
  val RETENTION_DAY_OPTIONS = listOf(1, 2, 5, 7, 14)
  val MAX_SIZE_MB_OPTIONS = listOf(512, 1024, 2048, 5120, 10240, 20480, 51200)
  val MOTION_DURATION_MINUTES_OPTIONS = listOf(1, 2, 5)
  val MOTION_SENSITIVITY_PRESETS = listOf(25, 50, 75, 90)

  fun normalizeRetentionDays(days: Int): Int =
    RETENTION_DAY_OPTIONS.minByOrNull { kotlin.math.abs(it - days) } ?: 7

  fun normalizeMotionDurationMinutes(minutes: Int): Int =
    MOTION_DURATION_MINUTES_OPTIONS.minByOrNull { kotlin.math.abs(it - minutes) } ?: 2

  fun normalizeMotionSensitivity(sensitivity: Int): Int =
    sensitivity.coerceIn(10, 95)

  fun normalizeMaxSizeMb(sizeMb: Int): Int =
    sizeMb.coerceIn(256, 102_400)

  fun formatSizeLabel(sizeMb: Int): String =
    if (sizeMb >= 1024) {
      val gb = sizeMb / 1024.0
      if (kotlin.math.abs(gb - gb.toInt()) < 0.05) "${gb.toInt()} GB"
      else String.format(java.util.Locale.US, "%.1f GB", gb)
    } else {
      "$sizeMb MB"
    }

  fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
  }

  /**
   * Builds compact, lexicographically sortable `.mp4` filename:
   * - Continuous: `cam1_2026-11-26_08-15-00_sd.mp4`
   * - Motion event: `cam1_2026-11-26_08-15-00_evt_sd.mp4`
   */
  fun buildSegmentFileName(
    slotIndex: Int,
    timestampMs: Long,
    quality: RecordingQuality,
    isMotionEvent: Boolean = false,
  ): String {
    val instant = java.time.Instant.ofEpochMilli(timestampMs)
    val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
    val stamp = local.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val suffix = if (isMotionEvent) "evt_${quality.id}" else quality.id
    return "cam${slotIndex + 1}_${stamp}_${suffix}.mp4"
  }

  fun isMotionEventFileName(fileName: String): Boolean =
    fileName.contains("_evt_", ignoreCase = true)

  fun parseSlotIndexFromFileName(fileName: String): Int? =
    Regex("^cam(\\d+)_", RegexOption.IGNORE_CASE)
      .find(fileName)
      ?.groupValues
      ?.getOrNull(1)
      ?.toIntOrNull()
      ?.let { (it - 1).takeIf { idx -> idx in 0..3 } }

  fun parseQualityFromFileName(fileName: String): RecordingQuality =
    if (fileName.contains("_hd.", ignoreCase = true)) RecordingQuality.HD else RecordingQuality.SD

  /**
   * Resolves the most storage-efficient stream URL for the requested recording quality.
   * For RTSP dual-stream URLs (`/ch0_0.264`, `/live/ch00_0`, `subtype=0`, `/Streaming/Channels/101`),
   * `SD` switches automatically to the lightweight sub-stream (`_1`, `subtype=1`, `102`) while
   * `HD` selects the main high-resolution stream (`_0`, `subtype=0`, `101`).
   */
  fun resolveStreamUrlForQuality(slot: CameraSlot, quality: RecordingQuality): String {
    val rawPlayback = slot.effectivePlaybackUrl()
    if (rawPlayback.isBlank()) return ""
    val lower = rawPlayback.lowercase(java.util.Locale.ROOT)
    val isRtsp = lower.startsWith("rtsp://") || lower.startsWith("rtspt://")
    if (!isRtsp) return rawPlayback

    val targetQualityIdx = if (quality == RecordingQuality.SD) 1 else 0
    val hasQualityPattern =
      Regex("/(?:live/)?ch\\d+_\\d+").containsMatchIn(rawPlayback) ||
        Regex("(?:subtype|stream)=\\d+").containsMatchIn(rawPlayback) ||
        Regex("/Streaming/Channels/\\d+0[12]").containsMatchIn(rawPlayback)
    if (!hasQualityPattern) return rawPlayback

    val channelZeroBased = CameraSlot.extractChannelZeroBased(rawPlayback, slot.slotIndex)
    val withChannel =
      CameraSlot.applyChannelAndQuality(
        streamUrl = rawPlayback,
        fallbackIp = CameraSlot.extractHost(rawPlayback, slot.onvifDeviceAddress),
        fallbackPort = CameraSlot.extractPort(rawPlayback),
        channelZeroBased = channelZeroBased,
        qualityIndex = targetQualityIdx,
      )
    // Preserve injected username:password credentials if present in rawPlayback
    val credMatch = Regex("^(rtspt?://[^/@]+@)", RegexOption.IGNORE_CASE).find(rawPlayback)
    return if (credMatch != null && !withChannel.contains("@")) {
      withChannel.replaceFirst(Regex("^rtspt?://", RegexOption.IGNORE_CASE), credMatch.groupValues[1])
    } else {
      withChannel
    }
  }

  /**
   * Computes which finished recording files must be deleted (oldest-first) so that:
   * 1) No recording older than [retentionDays] remains,
   * 2) Total stored size for this camera stays within [maxStorageBytes] (leaving optional [reserveForNextSegmentBytes]),
   * 3) Device free space stays above [minRequiredFreeBytes].
   */
  fun planFilesToDeleteForRingBuffer(
    segments: List<RecordedSegmentInfo>,
    maxStorageBytes: Long,
    retentionDays: Int,
    nowMs: Long,
    reserveForNextSegmentBytes: Long = 0L,
    availableFreeBytes: Long = Long.MAX_VALUE,
    minRequiredFreeBytes: Long = MIN_DISK_FREE_BYTES,
  ): List<RecordedSegmentInfo> {
    val candidates =
      segments
        .filterNot { it.isCurrentlyRecording }
        .sortedBy { it.createdAtMs }
    if (candidates.isEmpty()) return emptyList()

    val toDelete = mutableListOf<RecordedSegmentInfo>()
    val remaining = candidates.toMutableList()
    val cutoffMs = nowMs - retentionDays.coerceAtLeast(1) * 86_400_000L

    // 1. Delete files older than retention window
    val expired = remaining.filter { it.createdAtMs > 0L && it.createdAtMs < cutoffMs }
    for (item in expired) {
      toDelete += item
      remaining.remove(item)
    }

    // 2. Enforce per-camera size quota (oldest overwritten first)
    val effectiveLimit = (maxStorageBytes - reserveForNextSegmentBytes).coerceAtLeast(0L)
    var currentTotal = remaining.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    while (remaining.isNotEmpty() && currentTotal > effectiveLimit) {
      val oldest = remaining.removeAt(0)
      toDelete += oldest
      currentTotal -= oldest.sizeBytes.coerceAtLeast(0L)
    }

    // 3. Protect underlying filesystem from running completely out of free space
    var simulatedFree = availableFreeBytes
    while (remaining.isNotEmpty() && simulatedFree < minRequiredFreeBytes) {
      val oldest = remaining.removeAt(0)
      toDelete += oldest
      simulatedFree += oldest.sizeBytes.coerceAtLeast(0L)
    }

    return toDelete
  }
}

object MotionDetectionMath {
  private const val PIXEL_LUMA_DELTA_THRESHOLD = 20

  /**
   * Maps user sensitivity [10..95] onto the percentage of changed luma blocks [0.8% .. 12.0%]
   * required to trigger a motion recording event. Higher sensitivity -> lower threshold.
   */
  fun sensitivityToChangedPercentThreshold(sensitivity: Int): Float {
    val clamped = sensitivity.coerceIn(10, 95)
    val norm = (clamped - 10) / 85f
    return 12.0f - norm * 10.8f
  }

  fun computeChangedPercent(
    previousLuma: ByteArray,
    currentLuma: ByteArray,
    pixelDeltaThreshold: Int = PIXEL_LUMA_DELTA_THRESHOLD,
  ): Float {
    val count = minOf(previousLuma.size, currentLuma.size)
    if (count <= 0) return 0f
    var changed = 0
    for (i in 0 until count) {
      val a = previousLuma[i].toInt() and 0xFF
      val b = currentLuma[i].toInt() and 0xFF
      if (kotlin.math.abs(a - b) >= pixelDeltaThreshold) {
        changed++
      }
    }
    return (changed * 100f) / count.toFloat()
  }

  fun isMotionDetected(
    previousLuma: ByteArray,
    currentLuma: ByteArray,
    sensitivity: Int,
  ): Boolean {
    val changedPct = computeChangedPercent(previousLuma, currentLuma)
    val thresholdPct = sensitivityToChangedPercentThreshold(sensitivity)
    return changedPct >= thresholdPct
  }
}


