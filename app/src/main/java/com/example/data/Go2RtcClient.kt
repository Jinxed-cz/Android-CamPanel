package com.example.data

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

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

        // Verify go2rtc API is alive first
        val pingReq = Request.Builder().url("http://$cleanGoHost:$apiPort/api/streams").get().build()
        runCatching {
          client.newCall(pingReq).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
          }
        }.onFailure {
          error("go2rtc na $cleanGoHost:$apiPort není spuštěné. Spusťte ho v Termuxu zkopírovaným příkazem a zkuste znovu.")
        }

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
          val encodedName = URLEncoder.encode(streamName, "UTF-8")
          val encodedSrc = URLEncoder.encode(bubbleSrc, "UTF-8")
          val putUrl = "http://$cleanGoHost:$apiPort/api/streams?name=$encodedName&src=$encodedSrc"
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
      val u = URLEncoder.encode(username.trim().ifBlank { "admin" }, "UTF-8").replace("+", "%20")
      val auth =
        if (password.isBlank()) {
          u
        } else {
          val p = URLEncoder.encode(password, "UTF-8").replace("+", "%20")
          "$u:$p"
        }
      return "bubble://$auth@$cleanIp:$nvrPort/bubble/live?ch=$channelZeroBased&stream=$streamQualityIndex"
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
