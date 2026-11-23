package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CamPanel", appName)
  }

  @Test
  fun `go2RtcClient parses streams json and masks credentials`() {
    val parsed =
      com.example.data.Go2RtcClient.parseStreamsJson(
        """{"kam1":{"producers":[{"url":"bubble://admin:secret@192.168.1.50:80/bubble/live?ch=0&stream=1"}]},"kam2":{"producers":[]}}""",
        host = "127.0.0.1",
        rtspPort = 8554,
      )
    assertEquals(2, parsed.size)
    assertEquals("rtsp://127.0.0.1:8554/kam1", parsed[0].rtspUrl)
    assertTrue(parsed[0].sourcesSummary.contains("admin:***@192.168.1.50"))
  }

  @Test
  fun `radioDirectoryClient parses json filters invalid schemes and deduplicates`() {
    val raw =
      """
      [
        {"stationuuid":"u1","name":"Rádio Impuls","url_resolved":"https://ice.impuls.cz/impuls128.mp3","countrycode":"CZ","codec":"MP3","bitrate":128,"tags":"pop"},
        {"stationuuid":"u2","name":"Dup Impuls","url_resolved":"https://ice.impuls.cz/impuls128.mp3","countrycode":"CZ","codec":"MP3","bitrate":128},
        {"stationuuid":"u3","name":"NonHttp","url_resolved":"mms://bad.stream/radio","countrycode":"CZ"},
        {"stationuuid":"u4","name":"ČRo Radiožurnál","url":"https://amp.cesnet.cz:8443/cro1.ogg","countrycode":"CZ","codec":"OGG","bitrate":256}
      ]
      """.trimIndent()
    val stations = com.example.data.RadioDirectoryClient.parseStationsJson(raw)
    assertEquals(2, stations.size)
    assertEquals("Rádio Impuls", stations[0].name)
    assertEquals("https://ice.impuls.cz/impuls128.mp3", stations[0].streamUrl)
    assertEquals("ČRo Radiožurnál", stations[1].name)
    assertEquals(256, stations[1].bitrateKbps)
  }

  @Test
  fun `batteryMonitor parses charging and discharging battery intent`() {
    val chargingIntent =
      android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED).apply {
        putExtra(android.os.BatteryManager.EXTRA_LEVEL, 72)
        putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_CHARGING)
        putExtra(android.os.BatteryManager.EXTRA_PLUGGED, android.os.BatteryManager.BATTERY_PLUGGED_USB)
      }
    val charging = com.example.data.BatteryMonitor.parseBatteryIntent(chargingIntent)
    assertEquals(72, charging.levelPercent)
    assertTrue(charging.isCharging)

    val dischargingIntent =
      android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED).apply {
        putExtra(android.os.BatteryManager.EXTRA_LEVEL, 45)
        putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING)
        putExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
      }
    val discharging = com.example.data.BatteryMonitor.parseBatteryIntent(dischargingIntent)
    assertEquals(45, discharging.levelPercent)
    org.junit.Assert.assertFalse(discharging.isCharging)
  }
}
