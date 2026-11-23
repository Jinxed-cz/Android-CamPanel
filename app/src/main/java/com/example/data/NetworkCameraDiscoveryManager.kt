package com.example.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Base64
import com.example.model.CameraSlot
import com.example.model.DiscoveredNetworkCamera
import com.example.model.NvrProtocolPreset
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Discovers ALL IP cameras and multi-channel NVRs on the local LAN across multiple protocols:
 * 1. ONVIF WS-Discovery (UDP multicast 239.255.255.250:3702) + TCP port 8899/80 ONVIF probe
 * 2. EseeCloud / Juanvision NVR & IP cameras (TCP 10000 / 8000 / 8899 / 80 / 554)
 * 3. XMEye / NetSurveillance / XiongMai Sofia (UDP broadcast 34569 + TCP 34567)
 * 4. Dahua / Amcrest DHIP DHDiscover (UDP multicast/broadcast 37810 + TCP 37777)
 * 5. SSDP / UPnP M-SEARCH (UDP multicast 239.255.255.250:1900)
 * 6. mDNS / Bonjour (_rtsp._tcp.) via Android NsdManager
 * 7. Fast parallel /24 local subnet port sweep (RTSP 554/8554/80 + HTTP/MJPEG 81/8080/80 + SDK ports)
 */
class NetworkCameraDiscoveryManager(private val context: Context) {

  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(2, TimeUnit.SECONDS)
      .readTimeout(3, TimeUnit.SECONDS)
      .writeTimeout(2, TimeUnit.SECONDS)
      .build()

  private val commonRtspPaths =
    listOf(
      "/stream1",
      "/stream2",
      "/ch0_0.264",
      "/ch0_1.264",
      "/live/ch00_0",
      "/live/ch00_1",
      "/ch0_0",
      "/ch0_1",
      "/live",
      "/live/ch0",
      "/h264Preview_01_sub",
      "/h264Preview_01_main",
      "/Streaming/Channels/101",
      "/Streaming/Channels/102",
      "/cam/realmonitor?channel=1&subtype=1",
      "/cam/realmonitor?channel=1&subtype=0",
      "/ch0_0.h264",
      "/11",
      "/12",
      "/unicast",
      "/",
    )

  private val commonHttpSnapshotPaths =
    listOf(
      "/cgi-bin/snapshot.cgi?chn=0&u=admin&p=",
      "/cgi-bin/snapshot.cgi?chn=1&u=admin&p=",
      "/capture",
      "/snapshot.jpg",
      "/snap.jpg",
      "/jpg/image.jpg",
      "/mjpeg",
      "/video.mjpg",
      "/axis-cgi/jpg/image.cgi",
    )

  suspend fun discoverAllCameras(): List<DiscoveredNetworkCamera> =
    withContext(Dispatchers.IO) {
      val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      val multicastLock =
        wifiManager?.createMulticastLock("campanel_camera_discovery")?.apply {
          setReferenceCounted(true)
          runCatching { acquire() }
        }

      val resultsByKey = ConcurrentHashMap<String, DiscoveredNetworkCamera>()
      val localIp = findLocalLanIpv4()
      val subnetBroadcast = localIp?.let {
        val parts = it.split(".")
        if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.255" else null
      }

      try {
        coroutineScope {
          val onvifJob = async {
            discoverOnvif().forEach { cam ->
              val key = cam.ipAddress.ifBlank { cam.id }
              resultsByKey[key] = cam
            }
          }
          val xmeyeJob = async {
            discoverXmeye(subnetBroadcast).forEach { cam ->
              val key = cam.ipAddress.ifBlank { cam.id }
              resultsByKey.putIfAbsent(key, cam)
            }
          }
          val dahuaJob = async {
            discoverDahua(subnetBroadcast).forEach { cam ->
              val key = cam.ipAddress.ifBlank { cam.id }
              resultsByKey.putIfAbsent(key, cam)
            }
          }
          val ssdpJob = async {
            discoverSsdp().forEach { cam ->
              val key = cam.ipAddress.ifBlank { cam.id }
              resultsByKey.putIfAbsent(key, cam)
            }
          }
          val mdnsJob = async {
            discoverMdnsRtsp().forEach { cam ->
              val key = cam.ipAddress.ifBlank { cam.id }
              resultsByKey.putIfAbsent(key, cam)
            }
          }
          val subnetJob = async {
            scanLocalSubnetCameras(localIp).forEach { cam ->
              val key = cam.ipAddress.ifBlank { cam.id }
              val existing = resultsByKey[key]
              if (existing == null) {
                resultsByKey[key] = cam
              } else {
                resultsByKey[key] =
                  existing.copy(
                    candidateStreamUrl = existing.candidateStreamUrl.ifBlank { cam.candidateStreamUrl },
                    rtspPort = if (existing.rtspPort != 554) existing.rtspPort else cam.rtspPort,
                    hardwareInfo = existing.hardwareInfo.ifBlank { cam.hardwareInfo },
                    isNvr = existing.isNvr || cam.isNvr,
                    detectedChannels = maxOf(existing.detectedChannels, cam.detectedChannels),
                    xAddrs = existing.xAddrs.ifBlank { cam.xAddrs },
                  )
              }
            }
          }
          awaitAll(onvifJob, xmeyeJob, dahuaJob, ssdpJob, mdnsJob, subnetJob)
        }
      } finally {
        if (multicastLock?.isHeld == true) {
          runCatching { multicastLock.release() }
        }
      }

      resultsByKey.values.sortedWith(
        compareBy<DiscoveredNetworkCamera> {
          when (it.discoveryType) {
            "ONVIF" -> 0
            "EseeCloud" -> 1
            "XMEye" -> 2
            "Dahua" -> 3
            "RTSP" -> 4
            "mDNS" -> 5
            "HTTP/MJPEG" -> 6
            else -> 7
          }
        }.thenBy { it.ipAddress }
      )
    }

  /**
   * Resolves a playable RTSP or HTTP URL for any discovered camera (channel 0 by default).
   */
  suspend fun resolveStreamUri(
    camera: DiscoveredNetworkCamera,
    username: String,
    password: String,
  ): Result<String> = resolveChannelStreamUri(camera, channelIndex = 0, username = username, password = password)

  /**
   * Resolves a playable RTSP or HTTP URL for a specific NVR/camera channel (`channelIndex` 0 = K1).
   */
  suspend fun resolveChannelStreamUri(
    camera: DiscoveredNetworkCamera,
    channelIndex: Int,
    username: String,
    password: String,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val ch = channelIndex.coerceAtLeast(0)
        val ip = camera.ipAddress.trim()

        // 1. Try ONVIF GetProfiles + GetStreamUri (supports multi-channel NVRs on xAddrs or port 8899/80)
        val candidateXAddrs =
          buildList {
            if (camera.xAddrs.isNotBlank()) add(camera.xAddrs)
            if (ip.isNotBlank()) {
              if (isTcpPortOpen(ip, 8899, 250)) add("http://$ip:8899/onvif/device_service")
              if (isTcpPortOpen(ip, 80, 250)) add("http://$ip/onvif/device_service")
            }
          }.distinct()

        for (xAddr in candidateXAddrs) {
          val onvifUri =
            runCatching {
              resolveOnvifStreamUriForChannel(xAddr, ch, username, password)
            }.getOrNull()
          if (!onvifUri.isNullOrBlank()) {
            return@runCatching onvifUri
          }
        }

        // 2. If single-channel HTTP/MJPEG camera already has a verified snapshot URL (and channel == 0)
        if (ch == 0 && camera.discoveryType == "HTTP/MJPEG" && camera.candidateStreamUrl.startsWith("http")) {
          return@runCatching camera.candidateStreamUrl
        }

        // 3. Probe RTSP paths prioritized by camera/NVR family across candidate RTSP ports (configured, 554, 80)
        var sawAuthFailure = false
        if (ip.isNotBlank()) {
          val portsToTry = listOf(camera.rtspPort, 554, 80, 8554).distinct().filter { isTcpPortOpen(ip, it, 280) }
          val candidatePaths = buildPrioritizedRtspPaths(camera.discoveryType, ch, username, password)
          for (port in portsToTry) {
            val probeOutcome =
              probeWorkingRtspPath(
                ip = ip,
                port = port,
                username = username,
                password = password,
                paths = candidatePaths,
              )
            if (probeOutcome.workingUrl != null) {
              return@runCatching probeOutcome.workingUrl
            }
            if (probeOutcome.sawUnauthorized) {
              sawAuthFailure = true
            }
          }
        }

        // 4. Probe EseeCloud HTTP /livestream/ & CGI snapshot (works on firmware without RTSP)
        if (ip.isNotBlank() && (camera.discoveryType == "EseeCloud" || camera.isNvr)) {
          val httpLive = probeEseeCloudHttpLivestream(ip, ch, username, password)
          if (httpLive != null) {
            return@runCatching httpLive
          }
          val httpSnap = probeEseeCloudHttpSnapshot(ip, ch, username, password)
          if (httpSnap != null) {
            return@runCatching httpSnap
          }
        }

        if (sawAuthFailure) {
          error("Kamera/NVR na $ip odmítla přihlášení (401). Zkontrolujte jméno a heslo zařízení.")
        }

        // 5. Fallback ONLY when RTSP port actually responds on the host
        if (ip.isNotBlank() && rtspPortAvailable(ip, camera.rtspPort)) {
          val u = username.ifBlank { "admin" }
          return@runCatching when (camera.discoveryType) {
            "EseeCloud" -> "rtsp://$ip:${camera.rtspPort}/ch${ch}_0.264"
            "XMEye" -> "rtsp://$ip:${camera.rtspPort}/user=$u&password=$password&channel=${ch + 1}&stream=0.sdp"
            "Dahua" -> "rtsp://$ip:${camera.rtspPort}/cam/realmonitor?channel=${ch + 1}&subtype=1"
            "Hikvision" -> "rtsp://$ip:${camera.rtspPort}/Streaming/Channels/${ch + 1}02"
            else ->
              if (ch == 0 && camera.candidateStreamUrl.isNotBlank()) camera.candidateStreamUrl
              else "rtsp://$ip:${camera.rtspPort}/ch${ch}_0.264"
          }
        }

        if (ch == 0 && camera.candidateStreamUrl.startsWith("http")) {
          return@runCatching camera.candidateStreamUrl
        }

        error(
          "Na $ip (kanál ${ch + 1}) neodpovídá RTSP (port ${camera.rtspPort}/80), ONVIF (8899) ani HTTP snímek. V menu NVR na monitoru povolte RTSP/ONVIF a ověřte heslo."
        )
      }
    }

  data class NvrBatchResult(
    val slots: List<CameraSlot>,
    val connectedCount: Int,
    val statusMessage: String,
  )

  /**
   * Connects 4 consecutive channels (`startChannelZeroBased` .. `+3`) from a multi-channel NVR
   * such as EseeCloud, ONVIF NVR, XMEye, Dahua, or Hikvision.
   */
  suspend fun connectNvrFourChannels(
    ipAddress: String,
    preset: NvrProtocolPreset,
    rtspPort: Int,
    username: String,
    password: String,
    startChannelZeroBased: Int = 0,
  ): Result<NvrBatchResult> =
    connectNvrChannels(
      ipAddress = ipAddress,
      preset = preset,
      rtspPort = rtspPort,
      username = username,
      password = password,
      channelsZeroBased = (0..3).map { (startChannelZeroBased + it).coerceIn(0, 7) },
    )

  /**
   * Connects specific NVR channels (`channelsZeroBased`, 1..4 unique channels in `0..7`) sequentially
   * into dashboard slots `0 until channels.size`, preserving any untouched slots from `existingSlots`.
   */
  suspend fun connectNvrChannels(
    ipAddress: String,
    preset: NvrProtocolPreset,
    rtspPort: Int,
    username: String,
    password: String,
    channelsZeroBased: List<Int>,
    existingSlots: List<CameraSlot> = (0..3).map { CameraSlot(slotIndex = it) },
  ): Result<NvrBatchResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val ip = ipAddress.trim()
        require(ip.isNotBlank()) { "Zadejte IP adresu NVR v místní síti." }

        val targetChannels =
          channelsZeroBased.map { it.coerceIn(0, 7) }.distinct().take(4).ifEmpty { listOf(0, 1, 2, 3) }

        val reachablePorts =
          listOf(rtspPort, 554, 80, 8080, 8899, 8000, 5555, 10000, 34567, 37777, 64444)
            .distinct()
            .filter { isTcpPortOpen(ip, it, timeoutMs = 450) }

        if (reachablePorts.isEmpty()) {
          error("NVR na adrese $ip není v síti dostupné (neodpovídá žádný port 80/554/8899/10000). Zkontrolujte místní IP adresu (najdete ji v aplikaci EseeCloud u vašeho Cloud ID) a Wi-Fi.")
        }

        val xAddrs =
          when {
            8899 in reachablePorts -> "http://$ip:8899/onvif/device_service"
            80 in reachablePorts -> "http://$ip/onvif/device_service"
            else -> ""
          }

        val nvrDevice =
          DiscoveredNetworkCamera(
            id = "nvr_manual_$ip",
            name = preset.label,
            ipAddress = ip,
            discoveryType = preset.id,
            xAddrs = xAddrs,
            rtspPort = rtspPort.takeIf { it > 0 } ?: 554,
            isNvr = true,
            detectedChannels = targetChannels.size,
          )

        val resolvedSlots =
          (0..3).map { idx -> existingSlots.getOrNull(idx)?.copy(slotIndex = idx) ?: CameraSlot(slotIndex = idx) }.toMutableList()
        var successCount = 0
        var lastError: String? = null

        targetChannels.forEachIndexed { slotIdx, ch ->
          val res =
            resolveChannelStreamUri(
              camera = nvrDevice,
              channelIndex = ch,
              username = username.trim(),
              password = password,
            )
          res
            .onSuccess { uri ->
              successCount++
              resolvedSlots[slotIdx] =
                CameraSlot(
                  slotIndex = slotIdx,
                  name = "NVR K${ch + 1}",
                  streamUrl = uri,
                  username = username.trim(),
                  password = password,
                  onvifDeviceAddress = ip,
                  protocolLabel = preset.id,
                )
            }
            .onFailure { err ->
              lastError = err.localizedMessage
              resolvedSlots[slotIdx] =
                CameraSlot(
                  slotIndex = slotIdx,
                  name = "NVR K${ch + 1}",
                  streamUrl = "",
                  username = username.trim(),
                  password = password,
                  onvifDeviceAddress = ip,
                  protocolLabel = preset.id,
                )
            }
        }

        if (successCount == 0) {
          val portsHint = reachablePorts.joinToString(", ")
          val chSummary = targetChannels.joinToString(", ") { "K${it + 1}" }
          error(
            lastError
              ?: "NVR na $ip odpovídá na portech [$portsHint], ale nepodařilo se otevřít kanály ($chSummary)."
          )
        }

        val chSummary = targetChannels.joinToString(", ") { "K${it + 1}" }
        val msg =
          if (successCount == targetChannels.size) {
            "Připojeno $successCount kanálů NVR (${preset.label}: $chSummary) z $ip."
          } else {
            "Připojeno $successCount z ${targetChannels.size} kanálů NVR ($chSummary) z $ip.${lastError?.let { " ($it)" } ?: ""}"
          }
        NvrBatchResult(slots = resolvedSlots, connectedCount = successCount, statusMessage = msg)
      }
    }

  private fun rtspPortAvailable(ip: String, port: Int): Boolean =
    isTcpPortOpen(ip, port, timeoutMs = 250)

  private fun buildPrioritizedRtspPaths(
    discoveryType: String,
    channelIndex: Int,
    username: String,
    password: String,
  ): List<String> {
    val u = username.ifBlank { "admin" }
    val ch = channelIndex.coerceAtLeast(0)
    val chTwoDigit = String.format(Locale.US, "%02d", ch)
    val chOneBased = ch + 1
    val chOneTwoDigit = String.format(Locale.US, "%02d", chOneBased)

    val eseeJooanPaths =
      listOf(
        "/ch${ch}_1.264",
        "/ch${ch}_0.264",
        "/live/ch${chTwoDigit}_1",
        "/live/ch${chTwoDigit}_0",
        "/ch${ch}_1",
        "/ch${ch}_0",
        "/live/ch$ch",
        "/live/ch$chOneBased",
        "/11",
        "/12",
      )
    val xmeyePaths =
      listOf(
        "/user=$u&password=$password&channel=$chOneBased&stream=1.sdp",
        "/user=${u}_password=${password}_channel=${chOneBased}_stream=1.sdp",
        "/user=$u&password=$password&channel=$chOneBased&stream=0.sdp",
        "/user=${u}_password=${password}_channel=${chOneBased}_stream=0.sdp",
      )
    val dahuaPaths =
      listOf(
        "/cam/realmonitor?channel=$chOneBased&subtype=1",
        "/cam/realmonitor?channel=$chOneBased&subtype=0",
      )
    val hikvisionPaths =
      listOf(
        "/Streaming/Channels/${chOneBased}02",
        "/Streaming/Channels/${chOneBased}01",
      )
    val reolinkPaths =
      listOf(
        "/h264Preview_${chOneTwoDigit}_sub",
        "/h264Preview_${chOneTwoDigit}_main",
      )

    return when (discoveryType) {
      "EseeCloud" -> (eseeJooanPaths + xmeyePaths + dahuaPaths + commonRtspPaths).distinct()
      "XMEye" -> (xmeyePaths + eseeJooanPaths + commonRtspPaths).distinct()
      "Dahua" -> (dahuaPaths + eseeJooanPaths + commonRtspPaths).distinct()
      "Hikvision" -> (hikvisionPaths + eseeJooanPaths + commonRtspPaths).distinct()
      else ->
        if (ch == 0) {
          (commonRtspPaths + eseeJooanPaths + xmeyePaths + dahuaPaths + hikvisionPaths + reolinkPaths).distinct()
        } else {
          (eseeJooanPaths + xmeyePaths + dahuaPaths + hikvisionPaths + reolinkPaths).distinct()
        }
    }
  }

  // --- 1. ONVIF WS-Discovery ---

  private fun discoverOnvif(timeoutMs: Long = 2600L): List<DiscoveredNetworkCamera> {
    val discovered = LinkedHashMap<String, DiscoveredNetworkCamera>()
    var socket: DatagramSocket? = null
    try {
      socket =
        DatagramSocket(null).apply {
          reuseAddress = true
          bind(InetSocketAddress(0))
          soTimeout = 500
        }
      val multicastAddr = InetAddress.getByName("239.255.255.250")
      val probes =
        listOf(
          buildWsDiscoveryProbe("dn:NetworkVideoTransmitter"),
          buildWsDiscoveryProbe("tds:Device"),
        )
      for (probe in probes) {
        val bytes = probe.toByteArray(Charsets.UTF_8)
        runCatching { socket.send(DatagramPacket(bytes, bytes.size, multicastAddr, 3702)) }
      }
      val deadline = System.currentTimeMillis() + timeoutMs
      val buf = ByteArray(16384)
      while (System.currentTimeMillis() < deadline) {
        val rxPacket = DatagramPacket(buf, buf.size)
        try {
          socket.receive(rxPacket)
          val xml = String(rxPacket.data, 0, rxPacket.length, Charsets.UTF_8)
          val senderIp = rxPacket.address?.hostAddress.orEmpty()
          val parsed = parseProbeMatch(xml, senderIp)
          if (parsed != null && parsed.xAddrs.isNotBlank()) {
            discovered[parsed.ipAddress.ifBlank { parsed.xAddrs }] = parsed
          }
        } catch (_: SocketTimeoutException) {
          // continue until deadline
        }
      }
    } finally {
      runCatching { socket?.close() }
    }
    return discovered.values.toList()
  }

  // --- 2. SSDP / UPnP Discovery ---

  private fun discoverSsdp(timeoutMs: Long = 2200L): List<DiscoveredNetworkCamera> {
    val found = LinkedHashMap<String, DiscoveredNetworkCamera>()
    var socket: DatagramSocket? = null
    try {
      socket =
        DatagramSocket(null).apply {
          reuseAddress = true
          bind(InetSocketAddress(0))
          soTimeout = 450
        }
      val mSearch =
        "M-SEARCH * HTTP/1.1\r\n" +
          "HOST: 239.255.255.250:1900\r\n" +
          "MAN: \"ssdp:discover\"\r\n" +
          "MX: 2\r\n" +
          "ST: ssdp:all\r\n\r\n"
      val bytes = mSearch.toByteArray(Charsets.UTF_8)
      val addr = InetAddress.getByName("239.255.255.250")
      runCatching { socket.send(DatagramPacket(bytes, bytes.size, addr, 1900)) }

      val deadline = System.currentTimeMillis() + timeoutMs
      val buf = ByteArray(8192)
      while (System.currentTimeMillis() < deadline) {
        val pkt = DatagramPacket(buf, buf.size)
        try {
          socket.receive(pkt)
          val resp = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
          val ip = pkt.address?.hostAddress.orEmpty()
          if (ip.isBlank()) continue
          val lower = resp.lowercase(Locale.US)
          val looksLikeCamera =
            lower.contains("camera") ||
              lower.contains("ipcam") ||
              lower.contains("nvr") ||
              lower.contains("dvr") ||
              lower.contains("hikvision") ||
              lower.contains("dahua") ||
              lower.contains("axis") ||
              lower.contains("reolink") ||
              lower.contains("foscam") ||
              lower.contains("digitalsecuritycamera")
          if (!looksLikeCamera) continue

          val serverHeader =
            Regex("(?im)^SERVER:\\s*(.+)$").find(resp)?.groupValues?.getOrNull(1)?.trim().orEmpty()
          found[ip] =
            DiscoveredNetworkCamera(
              id = "ssdp_$ip",
              name = serverHeader.take(28).ifBlank { "UPnP Kamera ($ip)" },
              ipAddress = ip,
              discoveryType = "SSDP",
              candidateStreamUrl = "rtsp://$ip:554/stream1",
              hardwareInfo = serverHeader.take(40),
            )
        } catch (_: SocketTimeoutException) {
          // continue
        }
      }
    } finally {
      runCatching { socket?.close() }
    }
    return found.values.toList()
  }

  // --- 3. mDNS / Bonjour (_rtsp._tcp.) ---

  private suspend fun discoverMdnsRtsp(timeoutMs: Long = 2400L): List<DiscoveredNetworkCamera> {
    val found = ConcurrentHashMap<String, DiscoveredNetworkCamera>()
    val nsdManager =
      context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        ?: return emptyList()

    val listener =
      object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
        override fun onDiscoveryStarted(serviceType: String?) = Unit
        override fun onDiscoveryStopped(serviceType: String?) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
          val info = serviceInfo ?: return
          @Suppress("DEPRECATION")
          nsdManager.resolveService(
            info,
            object : NsdManager.ResolveListener {
              override fun onResolveFailed(si: NsdServiceInfo?, err: Int) = Unit
              override fun onServiceResolved(resolved: NsdServiceInfo?) {
                val res = resolved ?: return
                val hostAddr =
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    res.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                  } else {
                    @Suppress("DEPRECATION")
                    res.host?.hostAddress
                  } ?: return
                val port = res.port.takeIf { it > 0 } ?: 554
                val name = res.serviceName?.takeIf { it.isNotBlank() } ?: "RTSP ($hostAddr)"
                found[hostAddr] =
                  DiscoveredNetworkCamera(
                    id = "mdns_${hostAddr}_$port",
                    name = name.take(28),
                    ipAddress = hostAddr,
                    discoveryType = "mDNS",
                    rtspPort = port,
                    candidateStreamUrl = "rtsp://$hostAddr:$port/stream1",
                    hardwareInfo = "Bonjour _rtsp._tcp:$port",
                  )
              }
            },
          )
        }
      }

    runCatching {
      nsdManager.discoverServices("_rtsp._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
      delay(timeoutMs)
    }
    runCatching { nsdManager.stopServiceDiscovery(listener) }
    return found.values.toList()
  }

  // --- 1b. XMEye / NetSurveillance / XiongMai Discovery (UDP 34569) ---

  private fun discoverXmeye(
    subnetBroadcast: String?,
    timeoutMs: Long = 2200L,
  ): List<DiscoveredNetworkCamera> {
    val found = LinkedHashMap<String, DiscoveredNetworkCamera>()
    var socket: DatagramSocket? = null
    try {
      socket =
        DatagramSocket(null).apply {
          reuseAddress = true
          broadcast = true
          bind(InetSocketAddress(0))
          soTimeout = 450
        }
      // XiongMai / Sofia 20-byte search packet: ff 00..00 fa 05 00 00 00 00
      val probe =
        byteArrayOf(
          0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
          0x00, 0x00, 0x00, 0x00, 0xFA.toByte(), 0x05, 0x00, 0x00, 0x00, 0x00,
        )
      val targets =
        listOfNotNull("255.255.255.255", subnetBroadcast).distinct()
      for (host in targets) {
        runCatching {
          socket.send(DatagramPacket(probe, probe.size, InetAddress.getByName(host), 34569))
        }
      }

      val deadline = System.currentTimeMillis() + timeoutMs
      val buf = ByteArray(8192)
      while (System.currentTimeMillis() < deadline) {
        val pkt = DatagramPacket(buf, buf.size)
        try {
          socket.receive(pkt)
          val ip = pkt.address?.hostAddress.orEmpty()
          if (ip.isBlank()) continue
          val payload = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
          val hostName =
            Regex("\"HostName\"\\s*:\\s*\"([^\"]+)\"")
              .find(payload)?.groupValues?.getOrNull(1)?.trim()
          val version =
            Regex("\"Version\"\\s*:\\s*\"([^\"]+)\"")
              .find(payload)?.groupValues?.getOrNull(1)?.trim().orEmpty()
          val tcpPort =
            Regex("\"TCPPort\"\\s*:\\s*(\\d+)")
              .find(payload)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 34567

          found[ip] =
            DiscoveredNetworkCamera(
              id = "xmeye_$ip",
              name = hostName?.takeIf { it.isNotBlank() } ?: "XMEye Kamera ($ip)",
              ipAddress = ip,
              discoveryType = "XMEye",
              rtspPort = 554,
              candidateStreamUrl = "rtsp://$ip:554/user=admin&password=&channel=1&stream=0.sdp",
              hardwareInfo =
                "Sofia :$tcpPort" +
                  if (version.isNotBlank()) " · ${version.take(18)}" else "",
            )
        } catch (_: SocketTimeoutException) {
          // continue
        }
      }
    } finally {
      runCatching { socket?.close() }
    }
    return found.values.toList()
  }

  // --- 1c. Dahua / Amcrest / Lorex DHDiscover (UDP 37810) ---

  private fun discoverDahua(
    subnetBroadcast: String?,
    timeoutMs: Long = 2200L,
  ): List<DiscoveredNetworkCamera> {
    val found = LinkedHashMap<String, DiscoveredNetworkCamera>()
    var socket: DatagramSocket? = null
    try {
      socket =
        DatagramSocket(null).apply {
          reuseAddress = true
          broadcast = true
          bind(InetSocketAddress(0))
          soTimeout = 450
        }
      // Dahua DHIP/DHDiscover JSON-RPC search payload
      val jsonSearch =
        """{"method":"DHDiscover.search","params":{"mac":"","uni":1}}"""
          .toByteArray(Charsets.UTF_8)
      val dhipHeader =
        byteArrayOf(
          0x20, 0x00, 0x00, 0x00, 0x44, 0x48, 0x49, 0x50, // "DHIP"
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
          jsonSearch.size.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
          jsonSearch.size.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        ) + jsonSearch

      val targets =
        listOfNotNull("239.255.255.251", "255.255.255.255", subnetBroadcast).distinct()
      for (host in targets) {
        val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: continue
        runCatching { socket.send(DatagramPacket(dhipHeader, dhipHeader.size, addr, 37810)) }
        runCatching { socket.send(DatagramPacket(jsonSearch, jsonSearch.size, addr, 37810)) }
      }

      val deadline = System.currentTimeMillis() + timeoutMs
      val buf = ByteArray(8192)
      while (System.currentTimeMillis() < deadline) {
        val pkt = DatagramPacket(buf, buf.size)
        try {
          socket.receive(pkt)
          val ip = pkt.address?.hostAddress.orEmpty()
          if (ip.isBlank()) continue
          val payload = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
          val deviceType =
            Regex("\"DeviceType\"\\s*:\\s*\"([^\"]+)\"")
              .find(payload)?.groupValues?.getOrNull(1)?.trim()
          val machineName =
            Regex("\"MachineName\"\\s*:\\s*\"([^\"]+)\"")
              .find(payload)?.groupValues?.getOrNull(1)?.trim()
          val serial =
            Regex("\"SerialNo\"\\s*:\\s*\"([^\"]+)\"")
              .find(payload)?.groupValues?.getOrNull(1)?.trim().orEmpty()

          found[ip] =
            DiscoveredNetworkCamera(
              id = "dahua_$ip",
              name = (machineName?.takeIf { it.isNotBlank() } ?: deviceType ?: "Dahua ($ip)").take(28),
              ipAddress = ip,
              discoveryType = "Dahua",
              rtspPort = 554,
              candidateStreamUrl = "rtsp://$ip:554/cam/realmonitor?channel=1&subtype=1",
              hardwareInfo =
                listOfNotNull(deviceType, serial.takeIf { it.isNotBlank() })
                  .joinToString(" · ")
                  .ifBlank { "DHIP :37777" },
            )
        } catch (_: SocketTimeoutException) {
          // continue
        }
      }
    } finally {
      runCatching { socket?.close() }
    }
    return found.values.toList()
  }

  // --- 4. Fast /24 Local Subnet Port & Protocol Sweep ---

  private suspend fun scanLocalSubnetCameras(localIp: String?): List<DiscoveredNetworkCamera> =
    coroutineScope {
      val resolvedIp = localIp ?: findLocalLanIpv4() ?: return@coroutineScope emptyList()
      val parts = resolvedIp.split(".")
      if (parts.size != 4) return@coroutineScope emptyList()
      val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
      val selfLastOctet = parts[3].toIntOrNull() ?: -1

      val semaphore = Semaphore(42)
      val jobs =
        (1..254)
          .filter { it != selfLastOctet }
          .map { lastOctet ->
            val ip = "$prefix.$lastOctet"
            async(Dispatchers.IO) {
              semaphore.withPermit {
                probeHostForCameraServices(ip)
              }
            }
          }

      jobs.awaitAll().filterNotNull()
    }

  private fun probeHostForCameraServices(ip: String): DiscoveredNetworkCamera? {
    // Check proprietary & standard camera/NVR ports (RTSP 554/8554, XMEye 34567, Dahua 37777, EseeCloud 10000, ONVIF 8899)
    val rtspPort =
      when {
        isTcpPortOpen(ip, 554, timeoutMs = 210) -> 554
        isTcpPortOpen(ip, 8554, timeoutMs = 210) -> 8554
        else -> null
      }
    val hasXmeyePort = isTcpPortOpen(ip, 34567, timeoutMs = 170)
    val hasDahuaPort = !hasXmeyePort && isTcpPortOpen(ip, 37777, timeoutMs = 170)
    val hasEseePort = !hasXmeyePort && !hasDahuaPort && isTcpPortOpen(ip, 10000, timeoutMs = 180)
    val hasOnvif8899 = isTcpPortOpen(ip, 8899, timeoutMs = 170)
    val onvifXAddr = if (hasOnvif8899) "http://$ip:8899/onvif/device_service" else ""

    if (hasXmeyePort) {
      val port = rtspPort ?: 554
      return DiscoveredNetworkCamera(
        id = "xmeye_tcp_$ip",
        name = "XMEye / NetSurv ($ip)",
        ipAddress = ip,
        discoveryType = "XMEye",
        xAddrs = onvifXAddr,
        rtspPort = port,
        candidateStreamUrl = "rtsp://$ip:$port/user=admin&password=&channel=1&stream=0.sdp",
        hardwareInfo = "Port 34567 (Sofia) · RTSP $port",
        isNvr = true,
        detectedChannels = 4,
      )
    }

    if (hasDahuaPort) {
      val port = rtspPort ?: 554
      return DiscoveredNetworkCamera(
        id = "dahua_tcp_$ip",
        name = "Dahua / Generic ($ip)",
        ipAddress = ip,
        discoveryType = "Dahua",
        xAddrs = onvifXAddr,
        rtspPort = port,
        candidateStreamUrl = "rtsp://$ip:$port/cam/realmonitor?channel=1&subtype=1",
        hardwareInfo = "Port 37777 (DHIP) · RTSP $port",
        isNvr = true,
        detectedChannels = 4,
      )
    }

    if (hasEseePort) {
      val effectiveRtspPort =
        rtspPort ?: if (isTcpPortOpen(ip, 80, 170) && probeRtspServerBanner(ip, 80) != null) 80 else 554
      return DiscoveredNetworkCamera(
        id = "esee_tcp_$ip",
        name = "EseeCloud ($ip)",
        ipAddress = ip,
        discoveryType = "EseeCloud",
        xAddrs = onvifXAddr,
        rtspPort = effectiveRtspPort,
        candidateStreamUrl =
          if (rtspPort != null || effectiveRtspPort == 80) "rtsp://$ip:$effectiveRtspPort/ch0_0.264"
          else "http://$ip/cgi-bin/snapshot.cgi?chn=0&u=admin&p=",
        hardwareInfo =
          buildList {
            add("Port 10000 (EseeCloud)")
            if (rtspPort != null) add("RTSP $rtspPort")
            if (hasOnvif8899) add("ONVIF 8899")
          }.joinToString(" · "),
        isNvr = true,
        detectedChannels = 4,
      )
    }

    if (hasOnvif8899) {
      val port = rtspPort ?: 554
      return DiscoveredNetworkCamera(
        id = "onvif_8899_$ip",
        name = "ONVIF NVR ($ip)",
        ipAddress = ip,
        discoveryType = "ONVIF",
        xAddrs = onvifXAddr,
        rtspPort = port,
        candidateStreamUrl = "rtsp://$ip:$port/ch0_0.264",
        hardwareInfo = "ONVIF :8899" + if (rtspPort != null) " · RTSP $rtspPort" else "",
        isNvr = true,
        detectedChannels = 4,
      )
    }

    if (rtspPort != null) {
      val serverBanner = probeRtspServerBanner(ip, rtspPort)
      val displayName =
        serverBanner?.takeIf { it.isNotBlank() }?.let { "$it ($ip)" } ?: "Generic RTSP ($ip)"
      return DiscoveredNetworkCamera(
        id = "rtsp_${ip}_$rtspPort",
        name = displayName.take(30),
        ipAddress = ip,
        discoveryType = "RTSP",
        rtspPort = rtspPort,
        candidateStreamUrl = "rtsp://$ip:$rtspPort/stream1",
        hardwareInfo = serverBanner?.let { "Port $rtspPort · $it" } ?: "Port $rtspPort",
      )
    }

    // Check common HTTP/MJPEG webcam ports (81 for ESP32-CAM, 8080, 80) + RTSP-over-80 on EseeCloud
    for (httpPort in listOf(81, 8080, 80)) {
      if (!isTcpPortOpen(ip, httpPort, timeoutMs = 180)) continue
      if (httpPort == 80 && probeRtspServerBanner(ip, 80) != null) {
        return DiscoveredNetworkCamera(
          id = "rtsp_${ip}_80",
          name = "EseeCloud / RTSP:80 ($ip)",
          ipAddress = ip,
          discoveryType = "EseeCloud",
          rtspPort = 80,
          candidateStreamUrl = "rtsp://$ip:80/ch0_0.264",
          hardwareInfo = "RTSP na portu 80",
          isNvr = true,
          detectedChannels = 4,
        )
      }
      val httpCam = probeHttpCameraEndpoint(ip, httpPort)
      if (httpCam != null) return httpCam
    }

    return null
  }

  private fun isTcpPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
    return runCatching {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, port), timeoutMs)
        true
      }
    }.getOrDefault(false)
  }

  private fun probeRtspServerBanner(ip: String, port: Int): String? {
    return runCatching {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, port), 500)
        socket.soTimeout = 700
        val req = "OPTIONS rtsp://$ip:$port/ RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: CamPanel\r\n\r\n"
        socket.getOutputStream().write(req.toByteArray(Charsets.US_ASCII))
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        var server: String? = null
        var lines = 0
        while (lines < 12) {
          val line = reader.readLine() ?: break
          if (line.isBlank()) break
          if (line.startsWith("Server:", ignoreCase = true)) {
            server = line.substringAfter(":").trim()
          }
          lines++
        }
        server
      }
    }.getOrNull()
  }

  private fun probeEseeCloudHttpLivestream(
    ip: String,
    channelIndex: Int,
    username: String,
    password: String,
  ): String? {
    val authHeader = if (username.isNotBlank()) Credentials.basic(username, password) else null
    val candidatePorts = listOf(80, 8080, 5555).filter { isTcpPortOpen(ip, it, 220) }
    val chOne = channelIndex + 1
    val paths = listOf("/livestream/${chOne}2", "/livestream/${chOne}1", "/livestream/1$chOne").distinct()

    for (port in candidatePorts) {
      for (path in paths) {
        val url = "http://$ip:$port$path"
        val reqBuilder = Request.Builder().url(url).get()
        if (authHeader != null) reqBuilder.addHeader("Authorization", authHeader)
        val ok =
          runCatching {
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
              val ct = resp.header("Content-Type").orEmpty().lowercase(Locale.US)
              resp.isSuccessful &&
                !ct.contains("text/html") &&
                (ct.contains("video/") ||
                  ct.contains("octet-stream") ||
                  ct.contains("mp2t") ||
                  ct.isBlank())
            }
          }.getOrDefault(false)
        if (ok) return url
      }
    }
    return null
  }

  private fun probeEseeCloudHttpSnapshot(
    ip: String,
    channelIndex: Int,
    username: String,
    password: String,
  ): String? {
    val u = URLEncoder.encode(username.ifBlank { "admin" }, "UTF-8").replace("+", "%20")
    val p = URLEncoder.encode(password, "UTF-8").replace("+", "%20")
    val authHeader = if (username.isNotBlank()) Credentials.basic(username, password) else null
    val candidatePorts = listOf(80, 8080).filter { isTcpPortOpen(ip, it, 220) }
    val channelVariants = listOf(channelIndex, channelIndex + 1).distinct()
    val querySuffixes = listOf("", "&q=0&d=1")

    for (port in candidatePorts) {
      for (chn in channelVariants) {
        for (suffix in querySuffixes) {
          val url = "http://$ip:$port/cgi-bin/snapshot.cgi?chn=$chn&u=$u&p=$p$suffix"
          val reqBuilder = Request.Builder().url(url).get()
          if (authHeader != null) reqBuilder.addHeader("Authorization", authHeader)
          val ok =
            runCatching {
              httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val ct = resp.header("Content-Type").orEmpty().lowercase(Locale.US)
                resp.isSuccessful && (ct.contains("image/") || ct.contains("octet-stream"))
              }
            }.getOrDefault(false)
          if (ok) return url
        }
      }
    }
    return null
  }

  private fun probeHttpCameraEndpoint(ip: String, port: Int): DiscoveredNetworkCamera? {
    for (path in commonHttpSnapshotPaths) {
      val url = "http://$ip:$port$path"
      val req = Request.Builder().url(url).get().build()
      val matched =
        runCatching {
          httpClient.newCall(req).execute().use { resp ->
            val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.US)
            val server = resp.header("Server").orEmpty()
            val authRealm = resp.header("WWW-Authenticate").orEmpty()
            val isMediaStream =
              resp.isSuccessful &&
                (contentType.contains("image/") ||
                  contentType.contains("multipart/x-mixed-replace"))
            val isAuthCamera =
              resp.code == 401 &&
                (authRealm.contains("cam", ignoreCase = true) ||
                  authRealm.contains("ipcam", ignoreCase = true) ||
                  authRealm.contains("axis", ignoreCase = true) ||
                  authRealm.contains("foscam", ignoreCase = true) ||
                  path.contains("snapshot.cgi", ignoreCase = true))

            if (isMediaStream || isAuthCamera) {
              val isEseeSnap = path.contains("snapshot.cgi", ignoreCase = true)
              DiscoveredNetworkCamera(
                id = "http_${ip}_$port",
                name =
                  if (isEseeSnap) "EseeCloud / HTTP ($ip)"
                  else server.take(20).ifBlank { "HTTP Kamera ($ip)" },
                ipAddress = ip,
                discoveryType = if (isEseeSnap) "EseeCloud" else "HTTP/MJPEG",
                candidateStreamUrl = url,
                hardwareInfo = "HTTP :$port${path.substringBefore("?")}",
                isNvr = isEseeSnap,
                detectedChannels = if (isEseeSnap) 4 else 1,
              )
            } else {
              null
            }
          }
        }.getOrNull()
      if (matched != null) return matched
    }
    return null
  }

  private data class RtspProbeOutcome(
    val workingUrl: String?,
    val sawUnauthorized: Boolean,
  )

  private fun probeWorkingRtspPath(
    ip: String,
    port: Int,
    username: String,
    password: String,
    paths: List<String> = commonRtspPaths,
  ): RtspProbeOutcome {
    var sawUnauthorized = false
    for (path in paths) {
      val rawUrl = "rtsp://$ip:$port$path"
      val status = sendRtspDescribe(ip, port, rawUrl, username, password)
      if (status == 200) {
        return RtspProbeOutcome(workingUrl = rawUrl, sawUnauthorized = false)
      }
      if (status == 401) {
        sawUnauthorized = true
      }
    }
    return RtspProbeOutcome(workingUrl = null, sawUnauthorized = sawUnauthorized)
  }

  private fun sendRtspDescribe(
    ip: String,
    port: Int,
    rtspUrl: String,
    username: String,
    password: String,
  ): Int {
    return runCatching {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, port), 500)
        socket.soTimeout = 700
        val out = socket.getOutputStream()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        val firstReq =
          "DESCRIBE $rtspUrl RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\nUser-Agent: CamPanel\r\n\r\n"
        out.write(firstReq.toByteArray(Charsets.US_ASCII))
        val firstResp = readRtspHeaders(reader)
        if (firstResp.statusCode == 200 || firstResp.statusCode != 401 || username.isBlank()) {
          return@use firstResp.statusCode
        }

        // Handle Basic or Digest RTSP authentication
        val authHeader =
          buildRtspAuthorization(
            wwwAuth = firstResp.wwwAuthenticate,
            method = "DESCRIBE",
            uri = rtspUrl,
            username = username,
            password = password,
          ) ?: return@use 401

        val secondReq =
          "DESCRIBE $rtspUrl RTSP/1.0\r\nCSeq: 2\r\nAccept: application/sdp\r\nAuthorization: $authHeader\r\nUser-Agent: CamPanel\r\n\r\n"
        out.write(secondReq.toByteArray(Charsets.US_ASCII))
        val secondResp = readRtspHeaders(reader)
        secondResp.statusCode
      }
    }.getOrDefault(-1)
  }

  private data class RtspHeaderResult(val statusCode: Int, val wwwAuthenticate: String)

  private fun readRtspHeaders(reader: BufferedReader): RtspHeaderResult {
    val statusLine = reader.readLine().orEmpty()
    val code =
      Regex("RTSP/1\\.0\\s+(\\d{3})").find(statusLine)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: -1
    var wwwAuth = ""
    var count = 0
    while (count < 20) {
      val line = reader.readLine() ?: break
      if (line.isBlank()) break
      if (line.startsWith("WWW-Authenticate:", ignoreCase = true)) {
        wwwAuth = line.substringAfter(":").trim()
      }
      count++
    }
    return RtspHeaderResult(code, wwwAuth)
  }

  private fun buildRtspAuthorization(
    wwwAuth: String,
    method: String,
    uri: String,
    username: String,
    password: String,
  ): String? {
    if (wwwAuth.startsWith("Basic", ignoreCase = true)) {
      val token = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
      return "Basic $token"
    }
    if (wwwAuth.startsWith("Digest", ignoreCase = true)) {
      val realm = Regex("realm=\"([^\"]*)\"").find(wwwAuth)?.groupValues?.getOrNull(1).orEmpty()
      val nonce = Regex("nonce=\"([^\"]*)\"").find(wwwAuth)?.groupValues?.getOrNull(1).orEmpty()
      if (nonce.isBlank()) return null
      val ha1 = md5Hex("$username:$realm:$password")
      val ha2 = md5Hex("$method:$uri")
      val response = md5Hex("$ha1:$nonce:$ha2")
      return "Digest username=\"$username\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$response\""
    }
    return null
  }

  private fun md5Hex(input: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private fun findLocalLanIpv4(): String? {
    return runCatching {
      val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
      var fallback: String? = null
      for (iface in interfaces) {
        if (!iface.isUp || iface.isLoopback) continue
        val name = iface.name.lowercase(Locale.US)
        if (name.contains("rmnet") || name.contains("tun") || name.contains("ppp")) continue
        for (addr in iface.inetAddresses) {
          if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
            val host = addr.hostAddress ?: continue
            if (name.contains("wlan") || name.contains("eth")) return host
            if (fallback == null) fallback = host
          }
        }
      }
      fallback
    }.getOrNull()
  }

  // --- ONVIF SOAP Media Resolution ---

  private fun resolveOnvifStreamUriForChannel(
    xAddrs: String,
    channelIndex: Int,
    username: String,
    password: String,
  ): String {
    val primaryXAddr = xAddrs.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    require(primaryXAddr.startsWith("http")) { "Neplatná adresa ONVIF služby: $xAddrs" }

    val mediaUrl = fetchMediaServiceUrl(primaryXAddr, username, password) ?: primaryXAddr
    val profileTokens = fetchProfileTokens(mediaUrl, username, password)
    require(profileTokens.isNotEmpty()) { "Kamera nevrátila žádný ONVIF video profil" }

    val selectedToken = selectOnvifProfileForChannel(profileTokens, channelIndex)
    val streamUri = fetchStreamUri(mediaUrl, selectedToken, username, password)
    require(streamUri.isNotBlank()) { "Kamera nevrátila RTSP URI" }
    return streamUri
  }

  private fun selectOnvifProfileForChannel(profileTokens: List<String>, channelIndex: Int): String {
    if (profileTokens.size == 1) return profileTokens.first()

    val chZero = channelIndex.coerceAtLeast(0)
    val chOne = chZero + 1
    // Match explicit channel markers commonly returned by Jooan / EseeCloud / XM / Dahua ONVIF NVRs
    val explicitMatches =
      profileTokens.filter { token ->
        val low = token.lowercase(Locale.US)
        low.contains("ch0$chZero") ||
          low.contains("ch${chZero}_") ||
          low.contains("channel$chOne") ||
          low.contains("profile_$chOne") ||
          low.contains("profile$chOne") ||
          Regex("(?:^|[_-])0*$chOne(?:sub|main|[_-]|\$)").containsMatchIn(low)
      }
    if (explicitMatches.isNotEmpty()) {
      return explicitMatches.lastOrNull { it.contains("sub", ignoreCase = true) }
        ?: explicitMatches.first()
    }

    // If NVR returns main+sub pairs per channel (e.g. 8 or 16 profiles for 4/8 channels)
    if (profileTokens.size >= 8 && (chZero * 2 + 1) < profileTokens.size) {
      return profileTokens[chZero * 2 + 1]
    }
    if (chZero < profileTokens.size) {
      return profileTokens[chZero]
    }
    return profileTokens.lastOrNull { it.contains("sub", ignoreCase = true) }
      ?: profileTokens.first()
  }

  private fun fetchMediaServiceUrl(deviceUrl: String, username: String, password: String): String? {
    val body =
      """<tds:GetCapabilities xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
           <tds:Category>Media</tds:Category>
         </tds:GetCapabilities>"""
    val xml = postSoap(deviceUrl, body, username, password) ?: return null
    return extractFirstTagText(xml, "XAddr")?.takeIf { it.startsWith("http") }
  }

  private fun fetchProfileTokens(mediaUrl: String, username: String, password: String): List<String> {
    val body = """<trt:GetProfiles xmlns:trt="http://www.onvif.org/ver10/media/wsdl"/>"""
    val xml = postSoap(mediaUrl, body, username, password) ?: return emptyList()
    val tokens = mutableListOf<String>()
    val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG && parser.name.equals("Profiles", ignoreCase = true)) {
        val token = parser.getAttributeValue(null, "token")
        if (!token.isNullOrBlank()) tokens.add(token)
      }
      event = parser.next()
    }
    return tokens
  }

  private fun fetchStreamUri(
    mediaUrl: String,
    profileToken: String,
    username: String,
    password: String,
  ): String {
    val safeToken = xmlEscape(profileToken)
    val body =
      """<trt:GetStreamUri xmlns:trt="http://www.onvif.org/ver10/media/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">
           <trt:StreamSetup>
             <tt:Stream>RTP-Unicast</tt:Stream>
             <tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport>
           </trt:StreamSetup>
           <trt:ProfileToken>$safeToken</trt:ProfileToken>
         </trt:GetStreamUri>"""
    val xml = postSoap(mediaUrl, body, username, password) ?: return ""
    return extractFirstTagText(xml, "Uri").orEmpty().trim()
  }

  private fun postSoap(
    url: String,
    bodyContent: String,
    username: String,
    password: String,
  ): String? {
    val headerXml = if (username.isNotBlank()) buildWsSecurityHeader(username, password) else ""
    val envelope =
      """<?xml version="1.0" encoding="utf-8"?>
         <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
           $headerXml
           <s:Body>$bodyContent</s:Body>
         </s:Envelope>"""
    val req =
      Request.Builder()
        .url(url)
        .post(envelope.toRequestBody("application/soap+xml; charset=utf-8".toMediaType()))
        .build()
    return runCatching {
      httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) null else resp.body?.string()
      }
    }.getOrNull()
  }

  private fun buildWsSecurityHeader(username: String, password: String): String {
    val nonceBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    val created = sdf.format(Date())
    val sha1 =
      MessageDigest.getInstance("SHA-1").apply {
        update(nonceBytes)
        update(created.toByteArray(Charsets.UTF_8))
        update(password.toByteArray(Charsets.UTF_8))
      }.digest()
    val digestB64 = Base64.encodeToString(sha1, Base64.NO_WRAP)
    val nonceB64 = Base64.encodeToString(nonceBytes, Base64.NO_WRAP)
    return """
      <s:Header>
        <Security s:mustUnderstand="1" xmlns="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
          <UsernameToken>
            <Username>${xmlEscape(username)}</Username>
            <Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">$digestB64</Password>
            <Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">$nonceB64</Nonce>
            <Created xmlns="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">$created</Created>
          </UsernameToken>
        </Security>
      </s:Header>
    """.trimIndent()
  }

  private fun buildWsDiscoveryProbe(types: String): String {
    val msgId = UUID.randomUUID().toString()
    return """<?xml version="1.0" encoding="utf-8"?>
      <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
                  xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                  xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
                  xmlns:dn="http://www.onvif.org/ver10/network/wsdl"
                  xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
        <e:Header>
          <w:MessageID>uuid:$msgId</w:MessageID>
          <w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
          <w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
        </e:Header>
        <e:Body>
          <d:Probe>
            <d:Types>$types</d:Types>
          </d:Probe>
        </e:Body>
      </e:Envelope>""".trimIndent()
  }

  private fun parseProbeMatch(xml: String, senderIp: String): DiscoveredNetworkCamera? {
    var xAddrs = ""
    var scopes = ""
    var address = ""
    runCatching {
      val parser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
      parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
      var event = parser.eventType
      while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
          when {
            parser.name.equals("XAddrs", ignoreCase = true) -> xAddrs = parser.nextText().trim()
            parser.name.equals("Scopes", ignoreCase = true) -> scopes = parser.nextText().trim()
            parser.name.equals("Address", ignoreCase = true) -> address = parser.nextText().trim()
          }
        }
        event = parser.next()
      }
    }
    if (xAddrs.isBlank()) return null

    val scopeTokens = scopes.split(Regex("\\s+"))
    val nameFromScope =
      scopeTokens
        .firstOrNull { it.contains("onvif://www.onvif.org/name/", ignoreCase = true) }
        ?.substringAfter("name/")
        ?.replace("%20", " ")
        ?.trim()
    val hwFromScope =
      scopeTokens
        .firstOrNull { it.contains("onvif://www.onvif.org/hardware/", ignoreCase = true) }
        ?.substringAfter("hardware/")
        ?.replace("%20", " ")
        ?.trim()
        .orEmpty()

    val resolvedIp =
      senderIp.ifBlank {
        Regex("https?://([^:/]+)").find(xAddrs)?.groupValues?.getOrNull(1).orEmpty()
      }
    val combinedHint = "${nameFromScope.orEmpty()} $hwFromScope $scopes".lowercase(Locale.US)
    val looksLikeNvr =
      combinedHint.contains("nvr") ||
        combinedHint.contains("dvr") ||
        combinedHint.contains("ja-") ||
        combinedHint.contains("8108") ||
        combinedHint.contains("juan") ||
        combinedHint.contains("esee") ||
        xAddrs.contains(":8899")

    return DiscoveredNetworkCamera(
      id = address.ifBlank { xAddrs },
      name = nameFromScope?.takeIf { it.isNotBlank() } ?: "ONVIF Kamera ($resolvedIp)",
      ipAddress = resolvedIp,
      discoveryType = "ONVIF",
      xAddrs = xAddrs,
      hardwareInfo = hwFromScope,
      isNvr = looksLikeNvr,
      detectedChannels = if (looksLikeNvr) 4 else 1,
    )
  }

  private fun extractFirstTagText(xml: String, targetTag: String): String? {
    return runCatching {
      val parser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
      parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
      var event = parser.eventType
      while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name.equals(targetTag, ignoreCase = true)) {
          return parser.nextText()
        }
        event = parser.next()
      }
      null
    }.getOrNull()
  }

  private fun xmlEscape(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}
