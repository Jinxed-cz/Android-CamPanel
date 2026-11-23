package com.example.data

import com.example.model.AladinDaySummary
import com.example.model.AladinForecastData
import com.example.model.AladinHourPoint
import com.example.model.AppLanguage
import com.example.model.WeatherLocation
import com.example.model.WeatherProvider
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class OpenMeteoForecastResponse(
  val hourly: OpenMeteoHourly? = null,
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourly(
  val time: List<String> = emptyList(),
  @Json(name = "temperature_2m") val temperature2m: List<Double?> = emptyList(),
  val rain: List<Double?> = emptyList(),
  val snowfall: List<Double?> = emptyList(),
  @Json(name = "surface_pressure") val surfacePressure: List<Double?> = emptyList(),
  @Json(name = "weather_code") val weatherCode: List<Int?> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
  val results: List<GeocodingResultDto>? = null,
)

@JsonClass(generateAdapter = true)
data class GeocodingResultDto(
  val name: String,
  val latitude: Double,
  val longitude: Double,
  val admin1: String? = null,
  val country: String? = null,
  @Json(name = "country_code") val countryCode: String? = null,
)

// --- Yr.no / Met.no Locationforecast 2.0 DTOs ---

@JsonClass(generateAdapter = true)
data class MetNorwayResponse(
  val properties: MetNorwayProperties? = null,
)

@JsonClass(generateAdapter = true)
data class MetNorwayProperties(
  val timeseries: List<MetNorwayTimeseries> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MetNorwayTimeseries(
  val time: String = "",
  val data: MetNorwayEntryData? = null,
)

@JsonClass(generateAdapter = true)
data class MetNorwayEntryData(
  val instant: MetNorwayInstant? = null,
  @Json(name = "next_1_hours") val next1Hours: MetNorwayNextPeriod? = null,
  @Json(name = "next_6_hours") val next6Hours: MetNorwayNextPeriod? = null,
)

@JsonClass(generateAdapter = true)
data class MetNorwayInstant(
  val details: MetNorwayInstantDetails? = null,
)

@JsonClass(generateAdapter = true)
data class MetNorwayInstantDetails(
  @Json(name = "air_temperature") val airTemperature: Double? = null,
  @Json(name = "air_pressure_at_sea_level") val airPressureAtSeaLevel: Double? = null,
)

@JsonClass(generateAdapter = true)
data class MetNorwayNextPeriod(
  val summary: MetNorwaySummary? = null,
  val details: MetNorwayNextDetails? = null,
)

@JsonClass(generateAdapter = true)
data class MetNorwaySummary(
  @Json(name = "symbol_code") val symbolCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class MetNorwayNextDetails(
  @Json(name = "precipitation_amount") val precipitationAmount: Double? = null,
)

private interface OpenMeteoForecastApi {
  @GET("v1/forecast")
  suspend fun getThreeDayForecast(
    @Query("latitude") latitude: Double,
    @Query("longitude") longitude: Double,
    @Query("hourly")
    hourly: String = "temperature_2m,rain,snowfall,surface_pressure,weather_code",
    @Query("forecast_days") forecastDays: Int = 3,
    @Query("timezone") timezone: String = "auto",
  ): OpenMeteoForecastResponse
}

private interface OpenMeteoGeocodingApi {
  @GET("v1/search")
  suspend fun searchLocations(
    @Query("name") query: String,
    @Query("count") count: Int = 12,
    @Query("language") language: String = "cs",
    @Query("format") format: String = "json",
  ): GeocodingResponse
}

class WeatherRepository {

  private val okHttp =
    OkHttpClient.Builder()
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build()

  private val moshi = Moshi.Builder().build()

  private val forecastApi: OpenMeteoForecastApi =
    Retrofit.Builder()
      .baseUrl("https://api.open-meteo.com/")
      .client(okHttp)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(OpenMeteoForecastApi::class.java)

  private val geocodingApi: OpenMeteoGeocodingApi =
    Retrofit.Builder()
      .baseUrl("https://geocoding-api.open-meteo.com/")
      .client(okHttp)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(OpenMeteoGeocodingApi::class.java)

  suspend fun fetchThreeDayForecast(
    location: WeatherLocation,
    provider: WeatherProvider = WeatherProvider.ALADIN,
    language: AppLanguage = AppLanguage.CS,
  ): AladinForecastData {
    return when (provider) {
      WeatherProvider.ALADIN,
      WeatherProvider.OPEN_METEO_GLOBAL -> {
        val response =
          forecastApi.getThreeDayForecast(
            latitude = location.latitude,
            longitude = location.longitude,
          )
        buildOpenMeteoForecast(
          response = response,
          location = location,
          provider = provider,
          language = language,
        )
      }
      WeatherProvider.MET_NORWAY -> fetchMetNorwayForecast(location, language)
    }
  }

  private suspend fun fetchMetNorwayForecast(
    location: WeatherLocation,
    language: AppLanguage,
  ): AladinForecastData =
    withContext(Dispatchers.IO) {
      val lat = String.format(Locale.US, "%.4f", location.latitude)
      val lon = String.format(Locale.US, "%.4f", location.longitude)
      val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$lat&lon=$lon"
      val req =
        Request.Builder()
          .url(url)
          .header("User-Agent", "CamPanel-Android/1.0 https://github.com/campanel")
          .get()
          .build()
      val bodyText =
        okHttp.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            error(
              if (language == AppLanguage.EN) "Yr.no returned HTTP ${resp.code}"
              else "Yr.no vrátil chybu HTTP ${resp.code}"
            )
          }
          resp.body?.string().orEmpty()
        }
      parseMetNorwayResponse(
        jsonText = bodyText,
        location = location,
        language = language,
      )
    }

  suspend fun searchLocations(
    query: String,
    language: AppLanguage = AppLanguage.CS,
  ): List<WeatherLocation> {
    val trimmed = query.trim()
    if (trimmed.length < 2) return emptyList()
    val res =
      geocodingApi.searchLocations(
        query = trimmed,
        count = 12,
        language = if (language == AppLanguage.EN) "en" else "cs",
      )
    val fallbackRegion = if (language == AppLanguage.EN) "World" else "Svět"
    return (res.results ?: emptyList()).map { dto ->
      val regionParts = listOfNotNull(dto.admin1, dto.countryCode ?: dto.country)
      WeatherLocation(
        name = dto.name,
        region = regionParts.joinToString(", ").ifBlank { fallbackRegion },
        latitude = dto.latitude,
        longitude = dto.longitude,
      )
    }
  }

  companion object {
    private val moshiStatic = Moshi.Builder().build()
    private val metNorwayAdapter = moshiStatic.adapter(MetNorwayResponse::class.java)

    fun buildOpenMeteoForecast(
      response: OpenMeteoForecastResponse,
      location: WeatherLocation,
      provider: WeatherProvider,
      language: AppLanguage,
      today: LocalDate = LocalDate.now(),
    ): AladinForecastData {
      val hourly =
        response.hourly
          ?: error(
            if (language == AppLanguage.EN) "Missing hourly forecast data"
            else "Chybí hodinová data předpovědi"
          )
      val times = hourly.time
      if (times.isEmpty()) {
        error(
          if (language == AppLanguage.EN) "Empty weather server response"
          else "Prázdná odpověď meteorologického serveru"
        )
      }

      val locale =
        if (language == AppLanguage.EN) Locale.ENGLISH else Locale.forLanguageTag("cs-CZ")
      val updatedFmt = DateTimeFormatter.ofPattern("HH:mm", locale)

      val points =
        times.indices.mapNotNull { idx ->
          val rawTime = times[idx]
          val parsedDt = runCatching { LocalDateTime.parse(rawTime) }.getOrNull() ?: return@mapNotNull null
          val dayOffset = ChronoUnit.DAYS.between(today, parsedDt.toLocalDate()).toInt().coerceIn(0, 2)

          AladinHourPoint(
            isoTime = rawTime,
            hourInt = parsedDt.hour,
            hourLabel = String.format(Locale.US, "%02d:00", parsedDt.hour),
            dayIndex = dayOffset,
            temperatureC = hourly.temperature2m.getOrNull(idx) ?: 0.0,
            rainMm = hourly.rain.getOrNull(idx) ?: 0.0,
            snowCm = hourly.snowfall.getOrNull(idx) ?: 0.0,
            pressureHpa = hourly.surfacePressure.getOrNull(idx) ?: 1013.0,
            weatherCode = hourly.weatherCode.getOrNull(idx) ?: 0,
          )
        }

      return AladinForecastData(
        locationName = location.name,
        updatedAtLabel = LocalTime.now().format(updatedFmt),
        hourlyPoints = points,
        daySummaries = buildDaySummaries(points, today, language),
        provider = provider,
      )
    }

    fun parseMetNorwayResponse(
      jsonText: String,
      location: WeatherLocation,
      language: AppLanguage = AppLanguage.CS,
      today: LocalDate = LocalDate.now(),
      zoneId: ZoneId = ZoneId.systemDefault(),
    ): AladinForecastData {
      val parsed =
        runCatching { metNorwayAdapter.fromJson(jsonText) }.getOrNull()
          ?: error(
            if (language == AppLanguage.EN) "Invalid Yr.no JSON response"
            else "Neplatná odpověď Yr.no"
          )
      val series = parsed.properties?.timeseries.orEmpty()
      if (series.isEmpty()) {
        error(
          if (language == AppLanguage.EN) "Empty Yr.no forecast"
          else "Prázdná předpověď Yr.no"
        )
      }

      val points =
        series.mapNotNull { entry ->
          val instant = runCatching { Instant.parse(entry.time) }.getOrNull() ?: return@mapNotNull null
          val localDt = instant.atZone(zoneId).toLocalDateTime()
          val rawDayDiff = ChronoUnit.DAYS.between(today, localDt.toLocalDate()).toInt()
          if (rawDayDiff !in 0..2) return@mapNotNull null

          val instantDetails = entry.data?.instant?.details
          val tempC = instantDetails?.airTemperature ?: 0.0
          val pressure = instantDetails?.airPressureAtSeaLevel ?: 1013.0
          val next1 = entry.data?.next1Hours
          val next6 = entry.data?.next6Hours
          val precipMm =
            next1?.details?.precipitationAmount
              ?: (next6?.details?.precipitationAmount?.div(6.0))
              ?: 0.0
          val symbol = next1?.summary?.symbolCode ?: next6?.summary?.symbolCode ?: "cloudy"
          val wmoCode = mapMetSymbolToWmoCode(symbol)
          val isSnowy = symbol.contains("snow", ignoreCase = true) || tempC <= 1.0
          val rainMm = if (isSnowy && tempC <= 0.5) 0.0 else precipMm
          val snowCm = if (isSnowy && precipMm > 0.0) precipMm else 0.0

          AladinHourPoint(
            isoTime = localDt.toString(),
            hourInt = localDt.hour,
            hourLabel = String.format(Locale.US, "%02d:00", localDt.hour),
            dayIndex = rawDayDiff,
            temperatureC = tempC,
            rainMm = rainMm,
            snowCm = snowCm,
            pressureHpa = pressure,
            weatherCode = wmoCode,
          )
        }

      if (points.isEmpty()) {
        error(
          if (language == AppLanguage.EN) "No 3-day points in Yr.no forecast"
          else "Chybí 3denní data v předpovědi Yr.no"
        )
      }

      val locale =
        if (language == AppLanguage.EN) Locale.ENGLISH else Locale.forLanguageTag("cs-CZ")
      val updatedFmt = DateTimeFormatter.ofPattern("HH:mm", locale)

      return AladinForecastData(
        locationName = location.name,
        updatedAtLabel = LocalTime.now().format(updatedFmt),
        hourlyPoints = points,
        daySummaries = buildDaySummaries(points, today, language),
        provider = WeatherProvider.MET_NORWAY,
      )
    }

    fun mapMetSymbolToWmoCode(symbolCode: String): Int {
      val s = symbolCode.lowercase(Locale.US)
      return when {
        s.contains("thunder") -> 95
        s.contains("snow") || s.contains("sleet") -> 71
        s.contains("heavyrain") -> 65
        s.contains("rain") || s.contains("drizzle") -> 61
        s.contains("fog") -> 45
        s.contains("cloudy") -> 3
        s.contains("partlycloudy") -> 2
        s.contains("fair") -> 1
        s.contains("clearsky") -> 0
        else -> 2
      }
    }

    private fun buildDaySummaries(
      points: List<AladinHourPoint>,
      today: LocalDate,
      language: AppLanguage,
    ): List<AladinDaySummary> {
      val locale =
        if (language == AppLanguage.EN) Locale.ENGLISH else Locale.forLanguageTag("cs-CZ")
      val dayOfWeekFmt = DateTimeFormatter.ofPattern("EEEE", locale)
      val dateShortFmt =
        DateTimeFormatter.ofPattern(if (language == AppLanguage.EN) "MMM d" else "d. M.", locale)

      return (0..2).map { dayIdx ->
        val dayPoints = points.filter { it.dayIndex == dayIdx }
        val targetDate = today.plusDays(dayIdx.toLong())
        val title =
          when (dayIdx) {
            0 -> if (language == AppLanguage.EN) "Today" else "Dnes"
            1 -> if (language == AppLanguage.EN) "Tomorrow" else "Zítra"
            else -> targetDate.format(dayOfWeekFmt).replaceFirstChar { it.titlecase(locale) }
          }
        val minT = dayPoints.minOfOrNull { it.temperatureC } ?: 0.0
        val maxT = dayPoints.maxOfOrNull { it.temperatureC } ?: 0.0
        val totalRain = dayPoints.sumOf { it.rainMm }
        val totalSnow = dayPoints.sumOf { it.snowCm }
        val avgPressure =
          if (dayPoints.isNotEmpty()) dayPoints.map { it.pressureHpa }.average() else 1013.0
        val middayPoint =
          dayPoints.minByOrNull { kotlin.math.abs(it.hourInt - 12) } ?: dayPoints.firstOrNull()

        AladinDaySummary(
          dayIndex = dayIdx,
          title = title,
          dateShort = targetDate.format(dateShortFmt),
          minTempC = minT,
          maxTempC = maxT,
          totalRainMm = totalRain,
          totalSnowCm = totalSnow,
          avgPressureHpa = avgPressure,
          dominantWeatherCode = middayPoint?.weatherCode ?: 0,
        )
      }
    }
  }
}
