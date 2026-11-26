package com.example.data

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class Go2RtcBridgeSourceType(val id: String) {
  ESEECLOUD("eseecloud"),
  TUYA_SMARTLIFE("tuya_smartlife"),
}

enum class TuyaRegion(
  val id: String,
  val shortLabel: String,
  val appHost: String,
  val openApiHost: String,
) {
  EU("eu", "EU", "protect-eu.ismartlife.me", "openapi.tuyaeu.com"),
  US("us", "US", "protect-us.ismartlife.me", "openapi.tuyaus.com"),
  CN("cn", "CN", "protect-cn.ismartlife.me", "openapi.tuyacn.com"),
  IN("in", "IN", "protect-in.ismartlife.me", "openapi.tuyain.com"),
}

enum class TuyaAuthMode(val id: String) {
  CLOUD_OPENAPI("openapi"),
  APP_ACCOUNT("app"),
}

data class Go2RtcStreamInfo(
  val name: String,
  val rtspUrl: String,
  val sourcesSummary: String,
)

data class Go2RtcSyncResult(
  val streams: List<Go2RtcStreamInfo>,
  val statusMessage: String,
)

/**
 * REST API client & configuration generator for a local or LAN `go2rtc` instance
 * (for example running directly on the phone in Termux at `127.0.0.1:1984`, or on Home Assistant).
 */
class Go2RtcClient {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(3, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .writeTimeout(4, TimeUnit.SECONDS)
      .build()

  suspend fun isReachable(host: String = "127.0.0.1", apiPort: Int = 1984): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        val req = Request.Builder().url("http://${host.trim()}:$apiPort/api/streams").get().build()
        client.newCall(req).execute().use { it.isSuccessful }
      }.getOrDefault(false)
    }

  suspend fun fetchStreams(
    host: String = "127.0.0.1",
    apiPort: Int = 1984,
    rtspPort: Int = 8554,
  ): Result<Go2RtcSyncResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        val url = "http://$cleanHost:$apiPort/api/streams"
        val req = Request.Builder().url(url).get().build()
        val bodyText =
          client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
              error("go2rtc na $cleanHost:$apiPort vrátil HTTP ${resp.code}.")
            }
            resp.body?.string().orEmpty()
          }

        val parsed = parseStreamsJson(bodyText, cleanHost, rtspPort)
        if (parsed.isEmpty()) {
          error("go2rtc na $cleanHost:$apiPort běží, ale nemá zatím žádné streamy. Nejprve odešlete konfiguraci nebo vložte go2rtc.yaml.")
        }
        Go2RtcSyncResult(
          streams = parsed,
          statusMessage = "Načteno ${parsed.size} streamů z go2rtc ($cleanHost:$apiPort).",
        )
      }
    }

  /**
   * Registers EseeCloud `bubble://` channels (`channelsZeroBased`, 1..4 channels in `0..7`) directly
   * into a running `go2rtc` server via `PUT /api/streams?name=...&src=...` and returns their RTSP URLs.
   */
  suspend fun registerEseeCloudBubbleStreams(
    go2RtcHost: String = "127.0.0.1",
    apiPort: Int = 1984,
    rtspPort: Int = 8554,
    nvrIp: String,
    nvrPort: Int = 80,
    username: String,
    password: String,
    channelsZeroBased: List<Int> = listOf(0, 1, 2, 3),
    streamQualityIndex: Int = 1, // 0 = Main HD, 1 = Sub SD
  ): Result<Go2RtcSyncResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanGoHost = go2RtcHost.trim().ifBlank { "127.0.0.1" }
        val cleanNvrIp = nvrIp.trim()
        require(cleanNvrIp.isNotBlank()) { "Zadejte místní IP adresu NVR (např. 192.168.1.50)." }
        val targetChannels =
          channelsZeroBased.map { it.coerceIn(0, 7) }.distinct().take(4).ifEmpty { listOf(0, 1, 2, 3) }

        verifyGo2RtcAlive(cleanGoHost, apiPort)

        val registered = mutableListOf<Go2RtcStreamInfo>()
        for (ch in targetChannels) {
          val streamName = "kam${ch + 1}"
          val bubbleSrc =
            buildBubbleSourceUrl(
              nvrIp = cleanNvrIp,
              nvrPort = nvrPort,
              username = username,
              password = password,
              channelZeroBased = ch,
              streamQualityIndex = streamQualityIndex,
            )
          putStreamToGo2Rtc(cleanGoHost, apiPort, streamName, bubbleSrc)
          registered.add(
            Go2RtcStreamInfo(
              name = streamName,
              rtspUrl = "rtsp://$cleanGoHost:$rtspPort/$streamName",
              sourcesSummary = bubbleSrc.replace(Regex("://([^:@]+):([^@]+)@"), "://$1:***@"),
            )
          )
        }

        val chSummary = targetChannels.joinToString(", ") { "K${it + 1}" }
        Go2RtcSyncResult(
          streams = registered,
          statusMessage = "Zaregistrovány kanály EseeCloud/Bubble ($chSummary) do go2rtc ($cleanGoHost:$rtspPort) a uloženy do CamPanelu.",
        )
      }
    }

  /**
   * Registers a Tuya / SmartLife `tuya://` camera stream into a running `go2rtc` instance
   * (requires go2rtc v1.9.13+) and returns its local RTSP URL.
   */
  suspend fun registerTuyaStream(
    go2RtcHost: String = "127.0.0.1",
    apiPort: Int = 1984,
    rtspPort: Int = 8554,
    streamName: String = "smartlife_1",
    region: TuyaRegion = TuyaRegion.EU,
    authMode: TuyaAuthMode = TuyaAuthMode.CLOUD_OPENAPI,
    deviceId: String,
    email: String = "",
    password: String = "",
    uid: String = "",
    clientId: String = "",
    clientSecret: String = "",
    useSubStream: Boolean = true,
  ): Result<Go2RtcSyncResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanGoHost = go2RtcHost.trim().ifBlank { "127.0.0.1" }
        val cleanName = sanitizeStreamName(streamName)
        val cleanDeviceId = deviceId.trim()
        require(cleanDeviceId.isNotBlank()) { "Zadejte Device ID (Virtuální ID) kamery." }

        if (authMode == TuyaAuthMode.CLOUD_OPENAPI) {
          require(uid.isNotBlank() && clientId.isNotBlank() && clientSecret.isNotBlank()) {
            "Zadejte UID, Client ID a Client Secret z Tuya IoT Platform."
          }
        } else {
          require(email.isNotBlank() && password.isNotBlank()) {
            "Zadejte e-mail a heslo účtu Tuya Smart."
          }
        }

        verifyGo2RtcAlive(cleanGoHost, apiPort)

        val tuyaSrc =
          buildTuyaSourceUrl(
            region = region,
            authMode = authMode,
            deviceId = cleanDeviceId,
            email = email,
            password = password,
            uid = uid,
            clientId = clientId,
            clientSecret = clientSecret,
            useSubStream = useSubStream,
          )
        putStreamToGo2Rtc(cleanGoHost, apiPort, cleanName, tuyaSrc)

        val stream =
          Go2RtcStreamInfo(
            name = cleanName,
            rtspUrl = "rtsp://$cleanGoHost:$rtspPort/$cleanName",
            sourcesSummary =
              tuyaSrc
                .replace(Regex("(password|client_secret)=([^&]+)"), "$1=***"),
          )
        Go2RtcSyncResult(
          streams = listOf(stream),
          statusMessage = "Kamera Tuya / SmartLife ($cleanName) připojena přes go2rtc ($cleanGoHost:$rtspPort).",
        )
      }
    }

  private fun verifyGo2RtcAlive(host: String, apiPort: Int) {
    val pingReq = Request.Builder().url("http://$host:$apiPort/api/streams").get().build()
    runCatching {
      client.newCall(pingReq).execute().use { resp ->
        if (!resp.isSuccessful) error("HTTP ${resp.code}")
      }
    }.onFailure {
      error("go2rtc na $host:$apiPort není spuštěné. Spusťte ho v Termuxu zkopírovaným příkazem a zkuste znovu.")
    }
  }

  private fun putStreamToGo2Rtc(host: String, apiPort: Int, streamName: String, sourceUrl: String) {
    val encodedName = URLEncoder.encode(streamName, "UTF-8")
    val encodedSrc = URLEncoder.encode(sourceUrl, "UTF-8")
    val putUrl = "http://$host:$apiPort/api/streams?name=$encodedName&src=$encodedSrc"
    val putReq =
      Request.Builder()
        .url(putUrl)
        .put("".toRequestBody(null))
        .build()
    client.newCall(putReq).execute().use { resp ->
      if (!resp.isSuccessful) {
        error("Nepodařilo se zaregistrovat $streamName do go2rtc (HTTP ${resp.code}).")
      }
    }
  }

  suspend fun registerJooanBubbleStreams(
    go2RtcHost: String = "127.0.0.1",
    apiPort: Int = 1984,
    rtspPort: Int = 8554,
    nvrIp: String,
    nvrPort: Int = 80,
    username: String,
    password: String,
    startChannelZeroBased: Int = 0,
    streamQualityIndex: Int = 1,
  ): Result<Go2RtcSyncResult> =
    registerEseeCloudBubbleStreams(
      go2RtcHost = go2RtcHost,
      apiPort = apiPort,
      rtspPort = rtspPort,
      nvrIp = nvrIp,
      nvrPort = nvrPort,
      username = username,
      password = password,
      channelsZeroBased = (0..3).map { (startChannelZeroBased + it).coerceIn(0, 7) },
      streamQualityIndex = streamQualityIndex,
    )

  companion object {
    fun sanitizeStreamName(raw: String): String =
      raw.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_-]"), "_")
        .trim('_')
        .ifBlank { "smartlife_1" }

    private fun urlParam(value: String): String =
      URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20")

    fun parseStreamsJson(
      jsonText: String,
      host: String,
      rtspPort: Int,
    ): List<Go2RtcStreamInfo> {
      if (jsonText.isBlank()) return emptyList()
      val root = JSONObject(jsonText)
      val keys = mutableListOf<String>()
      val iter = root.keys()
      while (iter.hasNext()) {
        keys.add(iter.next())
      }
      return keys.sorted().map { name ->
        val obj = root.optJSONObject(name)
        val producers = obj?.optJSONArray("producers")
        val firstUrl =
          if (producers != null && producers.length() > 0) {
            producers.optJSONObject(0)?.optString("url").orEmpty()
          } else {
            ""
          }
        Go2RtcStreamInfo(
          name = name,
          rtspUrl = "rtsp://$host:$rtspPort/$name",
          sourcesSummary = firstUrl.replace(Regex("://([^:@]+):([^@]+)@"), "://$1:***@"),
        )
      }
    }

    fun buildBubbleSourceUrl(
      nvrIp: String,
      nvrPort: Int,
      username: String,
      password: String,
      channelZeroBased: Int,
      streamQualityIndex: Int,
    ): String {
      val cleanIp = nvrIp.trim().ifBlank { "192.168.1.50" }
      val u = urlParam(username.trim().ifBlank { "admin" })
      val auth =
        if (password.isBlank()) {
          u
        } else {
          val p = URLEncoder.encode(password, "UTF-8").replace("+", "%20")
          "$u:$p"
        }
      return "bubble://$auth@$cleanIp:$nvrPort/bubble/live?ch=$channelZeroBased&stream=$streamQualityIndex"
    }

    fun buildTuyaSourceUrl(
      region: TuyaRegion = TuyaRegion.EU,
      authMode: TuyaAuthMode = TuyaAuthMode.CLOUD_OPENAPI,
      deviceId: String,
      email: String = "",
      password: String = "",
      uid: String = "",
      clientId: String = "",
      clientSecret: String = "",
      useSubStream: Boolean = true,
    ): String {
      val cleanDeviceId = deviceId.trim().ifBlank { "YOUR_DEVICE_ID" }
      val resSuffix = if (useSubStream) "&resolution=sd" else ""
      return when (authMode) {
        TuyaAuthMode.CLOUD_OPENAPI -> {
          val cleanUid = uid.trim().ifBlank { "YOUR_UID" }
          val cleanClientId = clientId.trim().ifBlank { "YOUR_CLIENT_ID" }
          val cleanSecret = clientSecret.trim().ifBlank { "YOUR_CLIENT_SECRET" }
          "tuya://${region.openApiHost}?device_id=${urlParam(cleanDeviceId)}&uid=${urlParam(cleanUid)}&client_id=${urlParam(cleanClientId)}&client_secret=${urlParam(cleanSecret)}$resSuffix"
        }
        TuyaAuthMode.APP_ACCOUNT -> {
          val cleanEmail = email.trim().ifBlank { "user@example.com" }
          val cleanPass = password.ifBlank { "YOUR_PASSWORD" }
          "tuya://${region.appHost}?device_id=${urlParam(cleanDeviceId)}&email=${urlParam(cleanEmail)}&password=${urlParam(cleanPass)}$resSuffix"
        }
      }
    }

    fun buildEseeCloudGo2RtcYaml(
      nvrIp: String,
      nvrPort: Int = 80,
      username: String = "admin",
      password: String = "",
      channelsZeroBased: List<Int> = listOf(0, 1, 2, 3),
      streamQualityIndex: Int = 1,
      apiPort: Int = 1984,
      rtspPort: Int = 8554,
    ): String {
      val targetChannels =
        channelsZeroBased.map { it.coerceIn(0, 7) }.distinct().take(4).ifEmpty { listOf(0, 1, 2, 3) }
      val sb = StringBuilder()
      sb.appendLine("api:")
      sb.appendLine("  listen: \":$apiPort\"")
      sb.appendLine("rtsp:")
      sb.appendLine("  listen: \":$rtspPort\"")
      sb.appendLine("streams:")
      for (ch in targetChannels) {
        val src =
          buildBubbleSourceUrl(
            nvrIp = nvrIp,
            nvrPort = nvrPort,
            username = username,
            password = password,
            channelZeroBased = ch,
            streamQualityIndex = streamQualityIndex,
          )
        sb.appendLine("  kam${ch + 1}: $src")
      }
      return sb.toString().trimEnd()
    }

    fun buildTuyaGo2RtcYaml(
      streamName: String = "smartlife_1",
      region: TuyaRegion = TuyaRegion.EU,
      authMode: TuyaAuthMode = TuyaAuthMode.CLOUD_OPENAPI,
      deviceId: String,
      email: String = "",
      password: String = "",
      uid: String = "",
      clientId: String = "",
      clientSecret: String = "",
      useSubStream: Boolean = true,
      apiPort: Int = 1984,
      rtspPort: Int = 8554,
    ): String {
      val cleanName = sanitizeStreamName(streamName)
      val src =
        buildTuyaSourceUrl(
          region = region,
          authMode = authMode,
          deviceId = deviceId,
          email = email,
          password = password,
          uid = uid,
          clientId = clientId,
          clientSecret = clientSecret,
          useSubStream = useSubStream,
        )
      return buildString {
        appendLine("api:")
        appendLine("  listen: \":$apiPort\"")
        appendLine("rtsp:")
        appendLine("  listen: \":$rtspPort\"")
        appendLine("streams:")
        append("  $cleanName: $src")
      }
    }

    fun buildJooanGo2RtcYaml(
      nvrIp: String,
      nvrPort: Int = 80,
      username: String = "admin",
      password: String = "",
      startChannelZeroBased: Int = 0,
      streamQualityIndex: Int = 1,
      apiPort: Int = 1984,
      rtspPort: Int = 8554,
    ): String =
      buildEseeCloudGo2RtcYaml(
        nvrIp = nvrIp,
        nvrPort = nvrPort,
        username = username,
        password = password,
        channelsZeroBased = (0..3).map { (startChannelZeroBased + it).coerceIn(0, 7) },
        streamQualityIndex = streamQualityIndex,
        apiPort = apiPort,
        rtspPort = rtspPort,
      )

    fun buildTermuxInstallCommand(yamlConfig: String): String {
      return """
pkg update -y && pkg install -y wget && \
([ -f go2rtc ] || wget -O go2rtc https://github.com/AlexxIT/go2rtc/releases/latest/download/go2rtc_linux_arm64) && \
chmod +x go2rtc && \
cat << 'EOF' > go2rtc.yaml
$yamlConfig
EOF
./go2rtc -c go2rtc.yaml
      """.trimIndent()
    }
  }
}
