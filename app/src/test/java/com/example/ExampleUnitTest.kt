package com.example

import com.example.model.CameraSlot
import com.example.model.WeatherLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun cameraSlot_injectsCredentialsIntoRtspUrl() {
    val slot =
      CameraSlot(
        slotIndex = 0,
        name = "Brána",
        streamUrl = "rtsp://192.168.1.50:554/stream1",
        username = "admin",
        password = "secret",
      )
    assertTrue(slot.isConfigured)
    assertEquals("rtsp://admin:secret@192.168.1.50:554/stream1", slot.effectivePlaybackUrl())
  }

  @Test
  fun cameraSlot_masksEmbeddedPasswordInDisplay() {
    val slot =
      CameraSlot(
        slotIndex = 1,
        streamUrl = "rtsp://user:mypassword@192.168.1.51:554/h264",
      )
    assertEquals("rtsp://user:***@192.168.1.51:554/h264", slot.maskedUrlDisplay())
  }

  @Test
  fun emptyCameraSlot_isNotConfigured() {
    val slot = CameraSlot(slotIndex = 2)
    assertFalse(slot.isConfigured)
    assertEquals("Nenastaveno", slot.maskedUrlDisplay())
  }

  @Test
  fun czechWeatherPresets_includePraha() {
    assertTrue(WeatherLocation.CzechPresets.any { it.name == "Praha" })
  }

  @Test
  fun radioStations_containAllFourRequestedStations() {
    val names = com.example.model.RadioStation.entries.map { it.displayName }
    assertEquals(listOf("Radio Kiss", "Evropa 2", "Blaník", "Frekvence 1"), names)
    assertEquals(com.example.model.RadioStation.BLANIK, com.example.model.RadioStation.fromId("blanik"))
    assertEquals(com.example.model.RadioStation.KISS, com.example.model.RadioStation.fromId(null))
  }

  @Test
  fun discoveredNetworkCamera_supportsNonOnvifProtocolsAndPresets() {
    val rtspCam =
      com.example.model.DiscoveredNetworkCamera(
        id = "rtsp_192.168.1.40_554",
        name = "RTSP Kamera (192.168.1.40)",
        ipAddress = "192.168.1.40",
        discoveryType = "RTSP",
        candidateStreamUrl = "rtsp://192.168.1.40:554/stream1",
      )
    assertEquals("RTSP", rtspCam.discoveryType)
    assertTrue(com.example.model.RtspPathPreset.CommonPresets.any { it.path == "/Streaming/Channels/101" })
    assertTrue(com.example.model.RtspPathPreset.CommonPresets.any { it.path.contains("channel=1&stream=0.sdp") })
    assertTrue(com.example.model.RtspPathPreset.CommonPresets.any { it.path.contains("/cam/realmonitor") })
    assertTrue(com.example.model.RtspPathPreset.CommonPresets.any { it.path == "/ch0_0.264" })
    assertTrue(com.example.model.RtspPathPreset.CommonPresets.any { it.path == "/live/ch00_0" })
    assertTrue(com.example.model.RtspPathPreset.CommonPresets.any { it.path.contains("/cgi-bin/snapshot.cgi?chn=0") && it.isHttp })
  }

  @Test
  fun cameraSlot_populatesHttpQueryCredentialsWithoutBreakingAuthority() {
    val slot =
      CameraSlot(
        slotIndex = 0,
        streamUrl = "http://192.168.1.50/cgi-bin/snapshot.cgi?chn=0&u=admin&p=",
        username = "jooan",
        password = "pass123",
      )
    assertEquals(
      "http://192.168.1.50/cgi-bin/snapshot.cgi?chn=0&u=jooan&p=pass123",
      slot.effectivePlaybackUrl(),
    )
    assertEquals(
      "http://192.168.1.50/cgi-bin/snapshot.cgi?chn=0&u=admin&p=",
      slot.maskedUrlDisplay(),
    )
  }

  @Test
  fun temperatureGrid_includesZeroAndThirtyDegreeLines() {
    val freezingGrid = com.example.ui.components.computeTemperatureGrid(-3.2, 8.4)
    assertTrue(0 in freezingGrid.ticksDeg)
    assertTrue(freezingGrid.axisMinC <= -3.2)
    assertTrue(freezingGrid.axisMaxC >= 8.4)

    val heatGrid = com.example.ui.components.computeTemperatureGrid(16.0, 31.5)
    assertTrue(30 in heatGrid.ticksDeg)
    assertTrue(heatGrid.axisMaxC >= 31.5)
  }

  @Test
  fun go2RtcClient_generatesJooanYamlAndTermuxCommand() {
    val yaml =
      com.example.data.Go2RtcClient.buildJooanGo2RtcYaml(
        nvrIp = "192.168.1.50",
        nvrPort = 80,
        username = "admin",
        password = "secret",
        startChannelZeroBased = 0,
        streamQualityIndex = 1,
      )
    assertTrue(yaml.contains("kam1: bubble://admin:secret@192.168.1.50:80/bubble/live?ch=0&stream=1"))
    assertTrue(yaml.contains("kam4: bubble://admin:secret@192.168.1.50:80/bubble/live?ch=3&stream=1"))

    val cmd = com.example.data.Go2RtcClient.buildTermuxInstallCommand(yaml)
    assertTrue(cmd.contains("go2rtc_linux_arm64"))
    assertTrue(cmd.contains("./go2rtc -c go2rtc.yaml"))
  }

  @Test
  fun dashboardTimeouts_useThirtySecondsForCameraSetupAndTenForWeather() {
    assertEquals(10, com.example.ui.DashboardViewModel.SETTINGS_INACTIVITY_TIMEOUT_SEC)
    assertEquals(30, com.example.ui.DashboardViewModel.CAMERA_SETTINGS_INACTIVITY_TIMEOUT_SEC)
    assertEquals(10, com.example.ui.DashboardViewModel.CAMERA_FULLSCREEN_DURATION_SEC)
  }

  @Test
  fun manualRtspBuilder_defaultsToPort80AdminAndNoPassword() {
    val defaultUrl = CameraSlot.buildManualStreamUrl(ipAddress = "192.168.1.77")
    assertEquals("rtsp://192.168.1.77:80/stream1", defaultUrl)

    val customPortUrl =
      CameraSlot.buildManualStreamUrl(
        ipAddress = "192.168.1.77",
        portText = "554",
        path = "/ch1_0.264",
      )
    assertEquals("rtsp://192.168.1.77:554/ch1_0.264", customPortUrl)
    assertEquals("192.168.1.77", CameraSlot.extractHost(customPortUrl))
    assertEquals("554", CameraSlot.extractPort(customPortUrl))
    assertEquals("/ch1_0.264", CameraSlot.extractPath(customPortUrl))

    val slot =
      CameraSlot(
        slotIndex = 0,
        streamUrl = defaultUrl,
        username = CameraSlot.DEFAULT_MANUAL_USERNAME,
        password = "",
      )
    assertEquals("rtsp://admin:@192.168.1.77:80/stream1", slot.effectivePlaybackUrl())
  }

  @Test
  fun channelAndQuality_switchesChannelsOneToEightAndMainSub() {
    val ch0 =
      CameraSlot.applyChannelAndQuality(
        streamUrl = "",
        fallbackIp = "192.168.1.60",
        fallbackPort = "80",
        channelZeroBased = 0,
        qualityIndex = 0,
      )
    assertEquals("rtsp://192.168.1.60:80/ch0_0.264", ch0)

    val ch6Sub =
      CameraSlot.applyChannelAndQuality(
        streamUrl = ch0,
        fallbackIp = "192.168.1.60",
        fallbackPort = "80",
        channelZeroBased = 5,
        qualityIndex = 1,
      )
    assertEquals("rtsp://192.168.1.60:80/ch5_1.264", ch6Sub)
    assertEquals(5, CameraSlot.extractChannelZeroBased(ch6Sub))
    assertEquals(1, CameraSlot.extractStreamQualityIndex(ch6Sub))
  }

  @Test
  fun temperatureCurveColorMode_switchesToBlueBelowZero() {
    assertEquals(
      com.example.ui.components.TemperatureCurveColorMode.WARM_ONLY,
      com.example.ui.components.resolveTemperatureCurveColorMode(0.0, 14.5),
    )
    assertEquals(
      com.example.ui.components.TemperatureCurveColorMode.COLD_ONLY,
      com.example.ui.components.resolveTemperatureCurveColorMode(-12.0, -0.5),
    )
    assertEquals(
      com.example.ui.components.TemperatureCurveColorMode.SPLIT_AT_ZERO,
      com.example.ui.components.resolveTemperatureCurveColorMode(-4.0, 6.0),
    )
    assertEquals(
      0.5f,
      com.example.ui.components.temperatureZeroFraction(yZero = 110f, chartTop = 10f, chartBottom = 210f),
      0.001f,
    )
  }

  @Test
  fun dayTempRange_formatsCompactlyAndDoublesMaxFont() {
    assertEquals("15°/12°", com.example.ui.components.formatDayTempRange(14.8, 12.1))
    assertEquals("-11°/-3°", com.example.ui.components.formatDayTempRange(-10.6, -3.4))
    assertTrue(com.example.ui.components.DAY_TEMP_MAX_FONT_SP >= 24f)
    assertTrue(com.example.ui.components.DAY_TEMP_MIN_FONT_SP <= 13f)
    assertTrue(com.example.ui.components.DAY_DATE_MAX_FONT_SP >= 19f)
    assertTrue(com.example.ui.components.DAY_DATE_MIN_FONT_SP <= 13f)
  }

  @Test
  fun nightWindow_andAutoBrightness_resolveCorrectly() {
    val math = com.example.model.DisplayBrightnessMath
    // Overnight schedule 22:00 -> 06:00
    assertTrue(math.isInNightWindow(22, 0, 22, 0, 6, 0))
    assertTrue(math.isInNightWindow(2, 30, 22, 0, 6, 0))
    assertTrue(math.isInNightWindow(5, 59, 22, 0, 6, 0))
    assertFalse(math.isInNightWindow(6, 0, 22, 0, 6, 0))
    assertFalse(math.isInNightWindow(14, 0, 22, 0, 6, 0))

    val darkBrightness = math.luxToScreenBrightness(1f)
    val indoorBrightness = math.luxToScreenBrightness(150f)
    val brightBrightness = math.luxToScreenBrightness(3000f)
    assertTrue(darkBrightness in 0.05f..0.1f)
    assertTrue(indoorBrightness > darkBrightness && indoorBrightness < brightBrightness)
    assertEquals(1.0f, brightBrightness, 0.001f)

    val settings =
      com.example.model.DisplaySettings(
        keepScreenOn = true,
        autoBrightnessEnabled = true,
        nightModeEnabled = true,
        nightStartHour = 22,
        nightStartMinute = 0,
        nightEndHour = 6,
        nightEndMinute = 0,
        nightBrightnessPercent = 3,
      )
    val (nightLevel, nightActive) =
      math.resolveEffectiveScreenBrightness(settings, currentHour = 23, currentMinute = 15, ambientLux = 400f)
    assertTrue(nightActive)
    assertEquals(0.03f, nightLevel, 0.001f)

    val (dayLevel, dayNightActive) =
      math.resolveEffectiveScreenBrightness(settings, currentHour = 12, currentMinute = 0, ambientLux = 150f)
    assertFalse(dayNightActive)
    assertEquals(indoorBrightness, dayLevel, 0.001f)

    val (previewLevel, previewScheduleActive) =
      math.resolveEffectiveScreenBrightness(
        settings,
        currentHour = 12,
        currentMinute = 0,
        ambientLux = 150f,
        previewNightBrightness = true,
      )
    assertFalse(previewScheduleActive)
    assertEquals(0.03f, previewLevel, 0.001f)
  }

  @Test
  fun shiftHourMinute_wrapsAroundMidnightCleanly() {
    assertEquals(0 to 0, com.example.ui.components.shiftHourMinute(23, 45, 15))
    assertEquals(23 to 0, com.example.ui.components.shiftHourMinute(0, 0, -60))
    assertEquals(23 to 45, com.example.ui.components.shiftHourMinute(0, 0, -15))
  }

  @Test
  fun appearancePresets_andActiveFavoriteRadio_resolveCorrectly() {
    assertEquals(
      com.example.model.AppColorSchemePreset.EMERALD_DARK,
      com.example.model.AppColorSchemePreset.fromId("emerald"),
    )
    assertEquals(
      com.example.model.AppColorSchemePreset.COBALT_DARK,
      com.example.model.AppColorSchemePreset.fromId("unknown"),
    )
    assertEquals(
      com.example.model.WeatherColorThemePreset.NEON_CONTRAST,
      com.example.model.WeatherColorThemePreset.fromId("neon"),
    )
    assertEquals(
      com.example.model.ClockStylePreset.PILL_FRAMED,
      com.example.model.ClockStylePreset.fromId("pill_framed"),
    )

    val dirStation =
      com.example.model.RadioDirectoryStation(
        uuid = "abc-123",
        name = "Radio Beat",
        streamUrl = "https://icecast.play.cz/radiobeat128.mp3",
        countryCode = "cz",
        codec = "mp3",
        bitrateKbps = 128,
      )
    val fav = dirStation.toFavorite()
    assertEquals("fav_abc-123", fav.id)
    assertEquals("RABE", fav.shortTag)
    assertEquals("CZ", fav.countryCode)
    assertEquals("MP3", fav.codec)

    val active = com.example.model.ActiveRadioStation.fromFavorite(fav)
    assertTrue(active.isFavorite)
    assertEquals("Radio Beat", active.displayName)
    assertEquals("https://icecast.play.cz/radiobeat128.mp3", active.primaryStreamUrl)
    assertTrue(active.subtitle.contains("CZ"))
    assertTrue(active.subtitle.contains("128 kbps"))
  }

  @Test
  fun languageAndGlobalWeatherProviders_resolveAndParseCorrectly() {
    assertEquals(com.example.model.AppLanguage.EN, com.example.model.AppLanguage.fromId("en"))
    assertEquals(com.example.model.AppLanguage.CS, com.example.model.AppLanguage.fromId("unknown"))

    assertEquals(com.example.model.WeatherProvider.OPEN_METEO_GLOBAL, com.example.model.WeatherProvider.fromId("open_meteo"))
    assertEquals(com.example.model.WeatherProvider.MET_NORWAY, com.example.model.WeatherProvider.fromId("yr_no"))
    assertEquals(com.example.model.WeatherProvider.ALADIN, com.example.model.WeatherProvider.fromId(null))

    assertTrue(WeatherLocation.GlobalPresets.any { it.name == "London" })
    assertTrue(WeatherLocation.GlobalPresets.any { it.name == "Tokyo" })
    assertEquals("EseeCloud", com.example.model.NvrProtocolPreset.ESEECLOUD_JOOAN.label)
    assertFalse(com.example.model.NvrProtocolPreset.ESEECLOUD_JOOAN.label.contains("Jooan", ignoreCase = true))

    val yamlCustom =
      com.example.data.Go2RtcClient.buildEseeCloudGo2RtcYaml(
        nvrIp = "192.168.1.50",
        nvrPort = 80,
        username = "admin",
        password = "pw",
        channelsZeroBased = listOf(1, 4, 7),
        streamQualityIndex = 1,
      )
    assertTrue(yamlCustom.contains("kam2: bubble://admin:pw@192.168.1.50:80/bubble/live?ch=1&stream=1"))
    assertTrue(yamlCustom.contains("kam5: bubble://admin:pw@192.168.1.50:80/bubble/live?ch=4&stream=1"))
    assertTrue(yamlCustom.contains("kam8: bubble://admin:pw@192.168.1.50:80/bubble/live?ch=7&stream=1"))
    assertFalse(yamlCustom.contains("kam1:"))

    assertEquals(95, com.example.data.WeatherRepository.mapMetSymbolToWmoCode("rainandthunder"))
    assertEquals(71, com.example.data.WeatherRepository.mapMetSymbolToWmoCode("heavysnow"))
    assertEquals(61, com.example.data.WeatherRepository.mapMetSymbolToWmoCode("lightrain"))
    assertEquals(0, com.example.data.WeatherRepository.mapMetSymbolToWmoCode("clearsky_day"))

    val sampleMetJson =
      """
      {
        "properties": {
          "timeseries": [
            {
              "time": "2026-11-23T12:00:00Z",
              "data": {
                "instant": {
                  "details": {
                    "air_temperature": 7.5,
                    "air_pressure_at_sea_level": 1015.2
                  }
                },
                "next_1_hours": {
                  "summary": { "symbol_code": "lightrain" },
                  "details": { "precipitation_amount": 1.4 }
                }
              }
            },
            {
              "time": "2026-11-24T12:00:00Z",
              "data": {
                "instant": {
                  "details": {
                    "air_temperature": 11.0,
                    "air_pressure_at_sea_level": 1020.0
                  }
                },
                "next_1_hours": {
                  "summary": { "symbol_code": "clearsky_day" },
                  "details": { "precipitation_amount": 0.0 }
                }
              }
            }
          ]
        }
      }
      """.trimIndent()
    val parsedMet =
      com.example.data.WeatherRepository.parseMetNorwayResponse(
        jsonText = sampleMetJson,
        location = WeatherLocation("London", "England, GB", 51.5074, -0.1278),
        language = com.example.model.AppLanguage.EN,
        today = java.time.LocalDate.of(2026, 11, 23),
        zoneId = java.time.ZoneOffset.UTC,
      )
    assertEquals(com.example.model.WeatherProvider.MET_NORWAY, parsedMet.provider)
    assertEquals("London", parsedMet.locationName)
    assertEquals(2, parsedMet.hourlyPoints.size)
    assertEquals("Today", parsedMet.daySummaries[0].title)
    assertEquals("Tomorrow", parsedMet.daySummaries[1].title)
    assertEquals(1.4, parsedMet.hourlyPoints[0].rainMm, 0.01)
    assertEquals(61, parsedMet.hourlyPoints[0].weatherCode)
  }
}

