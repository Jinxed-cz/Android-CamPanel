package com.example.data

import com.example.model.RadioDirectoryStation
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Queries the open, zero-key Radio Browser public directory for playable internet radio streams.
 */
class RadioDirectoryClient(
  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(7, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .build(),
) {

  suspend fun searchStations(query: String): List<RadioDirectoryStation> =
    withContext(Dispatchers.IO) {
      val cleanQuery = query.trim()
      if (cleanQuery.length < 2) return@withContext emptyList()
      val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
      fetchFromMirrors(
        "json/stations/search?name=$encoded&limit=35&hidebroken=true&order=clickcount&reverse=true"
      )
    }

  suspend fun fetchCzechStations(): List<RadioDirectoryStation> =
    withContext(Dispatchers.IO) {
      fetchFromMirrors(
        "json/stations/search?countrycode=CZ&limit=40&hidebroken=true&order=clickcount&reverse=true"
      )
    }

  private fun fetchFromMirrors(relativeQuery: String): List<RadioDirectoryStation> {
    val mirrors =
      listOf(
        "https://de1.api.radio-browser.info/$relativeQuery",
        "https://at1.api.radio-browser.info/$relativeQuery",
        "https://fi1.api.radio-browser.info/$relativeQuery",
      )
    var lastError: Throwable? = null
    for (url in mirrors) {
      val result =
        runCatching {
          val req =
            Request.Builder()
              .url(url)
              .header("User-Agent", "CamPanel-Android/1.0")
              .header("Accept", "application/json")
              .build()
          httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
              error("HTTP ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            parseStationsJson(body)
          }
        }
      if (result.isSuccess) return result.getOrThrow()
      lastError = result.exceptionOrNull()
    }
    throw (lastError ?: IllegalStateException("Vyhledávání rádií selhalo"))
  }

  companion object {
    fun parseStationsJson(rawJson: String): List<RadioDirectoryStation> {
      if (rawJson.isBlank()) return emptyList()
      val array = JSONArray(rawJson)
      val seenUrls = HashSet<String>()
      val output = ArrayList<RadioDirectoryStation>()
      for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val resolved = obj.optString("url_resolved", "").trim()
        val fallback = obj.optString("url", "").trim()
        val streamUrl = resolved.ifBlank { fallback }
        val isHttp =
          streamUrl.startsWith("http://", ignoreCase = true) ||
            streamUrl.startsWith("https://", ignoreCase = true)
        if (!isHttp) continue
        if (!seenUrls.add(streamUrl.lowercase())) continue

        val name = obj.optString("name", "").trim()
        if (name.isBlank()) continue
        val uuid = obj.optString("stationuuid", "").trim().ifBlank { streamUrl.hashCode().toString() }
        val country = obj.optString("countrycode", "").trim()
        val codec = obj.optString("codec", "").trim()
        val bitrate = obj.optInt("bitrate", 0)
        val tags = obj.optString("tags", "").trim()

        output.add(
          RadioDirectoryStation(
            uuid = uuid,
            name = name,
            streamUrl = streamUrl,
            countryCode = country,
            codec = codec,
            bitrateKbps = bitrate,
            tags = tags,
          )
        )
      }
      return output
    }
  }
}
