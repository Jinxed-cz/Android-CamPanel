package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AladinDaySummary
import com.example.model.AladinForecastData
import com.example.model.AladinHourPoint
import com.example.model.AppLanguage
import com.example.model.WeatherMetricToggle
import com.example.model.WeatherProvider
import com.example.model.WeatherUiState
import com.example.ui.theme.DashTextMuted
import com.example.ui.theme.DashTextPrimary
import com.example.ui.theme.DashTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalDashPalette
import com.example.ui.theme.LocalWeatherGraphPalette
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.WeatherGraphPalette
import com.example.ui.theme.tr
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Passive, non-interactive 3-day weather forecast panel. Intentionally has zero clickable
 * modifiers or tap feedback ("samotná předpověď počasí by byla bez reakce při kliknutí").
 */
@Composable
fun AladinWeatherPanel(
  weatherState: WeatherUiState,
  toggles: WeatherMetricToggle,
  locationName: String,
  activeProvider: WeatherProvider = WeatherProvider.ALADIN,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  val lang = LocalAppLanguage.current
  val shape = RoundedCornerShape(12.dp)
  val displayedProvider =
    when (weatherState) {
      is WeatherUiState.Ready -> weatherState.data.provider
      is WeatherUiState.Error -> weatherState.cachedData?.provider ?: activeProvider
      is WeatherUiState.Loading -> activeProvider
    }
  Column(
    modifier =
      modifier
        .clip(shape)
        .background(palette.surface)
        .border(1.dp, palette.surfaceBorder, shape)
        .padding(horizontal = 10.dp, vertical = 7.dp)
        .testTag("aladin_weather_panel")
  ) {
    // Header row: bounded location name never pushes status label off-screen
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f).padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(5.dp))
              .background(palette.accentBright.copy(alpha = 0.22f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = displayedProvider.badgeText,
            style = MaterialTheme.typography.labelSmall,
            color = palette.accentBright,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
          )
        }
        Text(
          text = locationName,
          style = MaterialTheme.typography.titleMedium,
          color = DashTextPrimary,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
      }

      val statusLabel =
        when (weatherState) {
          is WeatherUiState.Loading -> tr(lang, "Načítám...", "Loading...")
          is WeatherUiState.Ready ->
            "${tr(lang, "Akt.", "Upd.")} ${weatherState.data.updatedAtLabel} · 10m"
          is WeatherUiState.Error ->
            if (weatherState.cachedData != null) {
              "Offline (${weatherState.cachedData.updatedAtLabel})"
            } else {
              tr(lang, "Chyba sítě", "Network error")
            }
        }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        if (weatherState is WeatherUiState.Ready && weatherState.isRefreshing) {
          CircularProgressIndicator(
            modifier = Modifier.size(11.dp),
            strokeWidth = 1.5.dp,
            color = palette.accentBright,
          )
        }
        Text(
          text = statusLabel,
          style = MaterialTheme.typography.labelSmall,
          color = DashTextMuted,
          maxLines = 1,
          softWrap = false,
        )
      }
    }

    Spacer(Modifier.height(5.dp))

    when (weatherState) {
      is WeatherUiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = palette.accentBright,
            strokeWidth = 2.5.dp,
          )
        }
      }
      is WeatherUiState.Error -> {
        val cached = weatherState.cachedData
        if (cached != null) {
          ForecastBody(data = cached, toggles = toggles)
        } else {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
              text = weatherState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = OfflineRed,
            )
          }
        }
      }
      is WeatherUiState.Ready -> {
        ForecastBody(data = weatherState.data, toggles = toggles)
      }
    }
  }
}

@Composable
private fun MetricLegendRow(toggles: WeatherMetricToggle, modifier: Modifier = Modifier) {
  val weatherPalette = LocalWeatherGraphPalette.current
  val lang = LocalAppLanguage.current
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (toggles.showTemperature) LegendDot("°C", weatherPalette.tempWarm)
    if (toggles.showRain) LegendDot(tr(lang, "Déšť", "Rain"), Color(0xFF38BDF8))
    if (toggles.showSnow) LegendDot(tr(lang, "Sníh", "Snow"), Color.White)
    if (toggles.showPressure) LegendDot("hPa", weatherPalette.pressureLine)
  }
}

@Composable
private fun LegendDot(label: String, color: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = DashTextSecondary)
  }
}

@Composable
private fun ForecastBody(data: AladinForecastData, toggles: WeatherMetricToggle) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isVeryCompactHeight = maxHeight < 185.dp
    val sectionGap = if (isVeryCompactHeight) 3.dp else 5.dp

    Column(modifier = Modifier.fillMaxSize()) {
      // 3-Day Summary Columns
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        data.daySummaries.forEach { summary ->
          DaySummaryStrip(
            summary = summary,
            toggles = toggles,
            compact = isVeryCompactHeight,
            modifier = Modifier.weight(1f),
          )
        }
      }

      Spacer(Modifier.height(sectionGap))

      // 72-hour Meteogram Chart
      AladinMeteogramCanvas(
        points = data.hourlyPoints,
        toggles = toggles,
        modifier = Modifier.fillMaxWidth().weight(1f),
      )
    }
  }
}

internal const val DAY_TEMP_MAX_FONT_SP = 26f
internal const val DAY_TEMP_MIN_FONT_SP = 12f
internal const val DAY_DATE_MAX_FONT_SP = 20f
internal const val DAY_DATE_MIN_FONT_SP = 12f

internal fun formatDayTempRange(minTempC: Double, maxTempC: Double): String =
  "${minTempC.roundToInt()}°/${maxTempC.roundToInt()}°"

private fun fitSingleLineFontSizeSp(
  textMeasurer: androidx.compose.ui.text.TextMeasurer,
  text: String,
  baseStyle: TextStyle,
  maxWidthPx: Float,
  maxFontSp: Float,
  minFontSp: Float,
): Float {
  var candidateSp = maxFontSp
  while (candidateSp > minFontSp) {
    val measuredWidth =
      textMeasurer
        .measure(
          text = text,
          style = baseStyle.copy(fontSize = candidateSp.sp),
          maxLines = 1,
          softWrap = false,
        )
        .size
        .width
    if (measuredWidth <= maxWidthPx) break
    candidateSp -= 0.5f
  }
  return candidateSp.coerceAtLeast(minFontSp)
}

@Composable
private fun DaySummaryStrip(
  summary: AladinDaySummary,
  toggles: WeatherMetricToggle,
  compact: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val dashPalette = LocalDashPalette.current
  val weatherPalette = LocalWeatherGraphPalette.current
  val (icon, iconTint) = wmoWeatherIcon(summary.dominantWeatherCode, weatherPalette)
  val density = LocalDensity.current
  val textMeasurer = rememberTextMeasurer()
  val titleStyle =
    MaterialTheme.typography.titleMedium.copy(
      fontSize = if (compact) 12.sp else 13.5.sp,
      lineHeight = if (compact) 13.sp else 15.sp,
      fontWeight = FontWeight.Bold,
    )
  val baseDateStyle =
    MaterialTheme.typography.titleMedium.copy(
      fontWeight = FontWeight.SemiBold,
      letterSpacing = (-0.2).sp,
    )
  val baseTempStyle =
    MaterialTheme.typography.labelMedium.copy(
      fontWeight = FontWeight.Bold,
      letterSpacing = (-0.3).sp,
    )

  BoxWithConstraints(
    modifier =
      modifier
        .clip(RoundedCornerShape(7.dp))
        .background(dashPalette.surfaceElevated.copy(alpha = 0.72f))
        .padding(horizontal = 6.dp, vertical = if (compact) 2.5.dp else 4.dp)
  ) {
    val innerWidthPx = constraints.maxWidth.toFloat()
    val iconAndGapPx = with(density) { (18.dp + 4.dp).toPx() }
    val betweenColsPx = with(density) { 4.dp.toPx() }

    val maxLeftPx = (innerWidthPx * 0.48f).coerceAtLeast(iconAndGapPx)
    val leftTextMaxWidthPx = (maxLeftPx - iconAndGapPx).coerceAtLeast(1f)
    val fittedDateFontSp =
      remember(summary.dateShort, leftTextMaxWidthPx, baseDateStyle) {
        fitSingleLineFontSizeSp(
          textMeasurer = textMeasurer,
          text = summary.dateShort,
          baseStyle = baseDateStyle,
          maxWidthPx = leftTextMaxWidthPx,
          maxFontSp = DAY_DATE_MAX_FONT_SP,
          minFontSp = DAY_DATE_MIN_FONT_SP,
        )
      }
    val naturalLeftPx =
      remember(summary.title, summary.dateShort, titleStyle, baseDateStyle, fittedDateFontSp) {
        val titleWidth = textMeasurer.measure(summary.title, style = titleStyle, maxLines = 1).size.width
        val dateWidth =
          textMeasurer
            .measure(
              text = summary.dateShort,
              style = baseDateStyle.copy(fontSize = fittedDateFontSp.sp),
              maxLines = 1,
            )
            .size
            .width
        iconAndGapPx + maxOf(titleWidth, dateWidth)
      }
    val reservedLeftPx = naturalLeftPx.coerceAtMost(maxLeftPx)
    val minTempColPx = with(density) { 44.dp.toPx() }
    val tempMaxWidthPx =
      (innerWidthPx - reservedLeftPx - betweenColsPx).coerceAtLeast(minTempColPx)
    val tempMaxWidthDp = with(density) { tempMaxWidthPx.toDp() }

    val tempText = formatDayTempRange(summary.minTempC, summary.maxTempC)
    val fittedTempFontSp =
      remember(tempText, tempMaxWidthPx, baseTempStyle) {
        fitSingleLineFontSizeSp(
          textMeasurer = textMeasurer,
          text = tempText,
          baseStyle = baseTempStyle,
          maxWidthPx = tempMaxWidthPx,
          maxFontSp = DAY_TEMP_MAX_FONT_SP,
          minFontSp = DAY_TEMP_MIN_FONT_SP,
        )
      }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(18.dp),
        )
        Column {
          Text(
            text = summary.title,
            style = titleStyle,
            color = DashTextPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = summary.dateShort,
            style = baseDateStyle,
            color = DashTextPrimary.copy(alpha = 0.88f),
            fontSize = fittedDateFontSp.sp,
            lineHeight = (fittedDateFontSp + 1f).sp,
            maxLines = 1,
            softWrap = false,
          )
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        if (toggles.showTemperature) {
          val tempTint =
            if (summary.maxTempC < 0.0) weatherPalette.tempColdTop else weatherPalette.tempWarm
          Text(
            text = tempText,
            style = baseTempStyle,
            color = tempTint,
            fontSize = fittedTempFontSp.sp,
            lineHeight = (fittedTempFontSp + 1f).sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(max = tempMaxWidthDp),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (toggles.showRain) {
            Text(
              text = String.format(Locale.US, "%.1fmm", summary.totalRainMm),
              style = MaterialTheme.typography.labelSmall,
              color = weatherPalette.rainBar,
              fontSize = 10.sp,
              lineHeight = 11.sp,
            )
          }
          if (toggles.showSnow && (summary.totalSnowCm > 0.0 || !toggles.showRain)) {
            Text(
              text = String.format(Locale.US, "%.1fcm", summary.totalSnowCm),
              style = MaterialTheme.typography.labelSmall,
              color = weatherPalette.snowBar,
              fontSize = 10.sp,
              lineHeight = 11.sp,
            )
          }
        }
      }
    }
  }
}

internal data class TemperatureGridSpec(
  val axisMinC: Double,
  val axisMaxC: Double,
  val stepDeg: Int,
  val ticksDeg: List<Int>,
)

internal fun computeTemperatureGrid(minTempC: Double, maxTempC: Double): TemperatureGridSpec {
  val safeMin = minTempC
  val safeMax = maxTempC.coerceAtLeast(safeMin + 2.0)

  val candidateSteps = listOf(5, 10, 15, 20)
  for (step in candidateSteps) {
    val gridMin = (floor((safeMin - 0.5) / step) * step).toInt()
    val gridMax = (ceil((safeMax + 0.5) / step) * step).toInt().coerceAtLeast(gridMin + step)
    val count = ((gridMax - gridMin) / step) + 1
    if (count <= 6 || step == candidateSteps.last()) {
      val ticks = (gridMin..gridMax step step).toList()
      return TemperatureGridSpec(
        axisMinC = gridMin.toDouble(),
        axisMaxC = gridMax.toDouble(),
        stepDeg = step,
        ticksDeg = ticks,
      )
    }
  }
  return TemperatureGridSpec(0.0, 30.0, 10, listOf(0, 10, 20, 30))
}

internal enum class TemperatureCurveColorMode {
  WARM_ONLY,
  COLD_ONLY,
  SPLIT_AT_ZERO,
}

internal fun resolveTemperatureCurveColorMode(
  minTempC: Double,
  maxTempC: Double,
): TemperatureCurveColorMode =
  when {
    maxTempC < 0.0 -> TemperatureCurveColorMode.COLD_ONLY
    minTempC >= 0.0 -> TemperatureCurveColorMode.WARM_ONLY
    else -> TemperatureCurveColorMode.SPLIT_AT_ZERO
  }

internal fun temperatureZeroFraction(
  yZero: Float,
  chartTop: Float,
  chartBottom: Float,
): Float {
  val height = (chartBottom - chartTop).coerceAtLeast(1f)
  return ((yZero - chartTop) / height).coerceIn(0.02f, 0.98f)
}

private fun buildTemperatureCurveBrush(
  minTempC: Double,
  maxTempC: Double,
  yZero: Float,
  chartTop: Float,
  chartBottom: Float,
  weatherPalette: WeatherGraphPalette,
): Brush =
  when (resolveTemperatureCurveColorMode(minTempC, maxTempC)) {
    TemperatureCurveColorMode.WARM_ONLY ->
      Brush.horizontalGradient(
        listOf(weatherPalette.tempWarm, weatherPalette.tempHot, weatherPalette.tempWarm)
      )
    TemperatureCurveColorMode.COLD_ONLY ->
      Brush.verticalGradient(
        colors = listOf(weatherPalette.tempColdTop, weatherPalette.tempColdBottom),
        startY = chartTop,
        endY = chartBottom,
      )
    TemperatureCurveColorMode.SPLIT_AT_ZERO -> {
      val zeroFrac = temperatureZeroFraction(yZero, chartTop, chartBottom)
      val warmEnd = (zeroFrac - 0.005f).coerceAtLeast(0.01f)
      val coldStart = (zeroFrac + 0.005f).coerceAtMost(0.99f)
      Brush.verticalGradient(
        colorStops =
          arrayOf(
            0f to weatherPalette.tempHot,
            warmEnd to weatherPalette.tempWarm,
            coldStart to weatherPalette.tempColdTop,
            1f to weatherPalette.tempColdBottom,
          ),
        startY = chartTop,
        endY = chartBottom,
      )
    }
  }

internal data class PrecipGridSpec(
  val maxValue: Double,
  val midValue: Double,
  val unitLabel: String,
)

internal fun computePrecipGrid(
  points: List<AladinHourPoint>,
  showRain: Boolean,
  showSnow: Boolean,
): PrecipGridSpec {
  val maxRain = if (showRain) points.maxOfOrNull { it.rainMm } ?: 0.0 else 0.0
  val maxSnow = if (showSnow) points.maxOfOrNull { it.snowCm } ?: 0.0 else 0.0
  val rawPeak = maxOf(maxRain, maxSnow)
  val candidates = listOf(2.0, 4.0, 6.0, 10.0, 15.0, 20.0, 30.0, 50.0)
  val maxValue = candidates.firstOrNull { rawPeak <= it } ?: ceil(rawPeak / 10.0) * 10.0
  val unitLabel = if (showSnow && !showRain) "cm" else "mm"
  return PrecipGridSpec(
    maxValue = maxValue,
    midValue = maxValue / 2.0,
    unitLabel = unitLabel,
  )
}

private fun formatPrecipTick(value: Double, unit: String): String {
  val roundedInt = value.roundToInt()
  return if (kotlin.math.abs(value - roundedInt) < 0.05) {
    "$roundedInt$unit"
  } else {
    String.format(Locale.US, "%.1f%s", value, unit)
  }
}

@Composable
private fun AladinMeteogramCanvas(
  points: List<AladinHourPoint>,
  toggles: WeatherMetricToggle,
  modifier: Modifier = Modifier,
) {
  if (points.isEmpty()) return

  val dashPalette = LocalDashPalette.current
  val weatherPalette = LocalWeatherGraphPalette.current
  val minTemp = points.minOf { it.temperatureC }
  val maxTemp = points.maxOf { it.temperatureC }
  val tempGrid = computeTemperatureGrid(minTemp, maxTemp)
  val showPrecipAxis = toggles.showRain || toggles.showSnow
  val precipGrid = computePrecipGrid(points, toggles.showRain, toggles.showSnow)
  val textMeasurer = rememberTextMeasurer()

  // Distinct high-visibility rain blue & pure white snow colors
  val rainTopColor = Color(0xFF38BDF8)
  val rainBottomColor = Color(0xFF1D4ED8)
  val snowBrightColor = Color.White
  val snowBottomColor = Color(0xFFDCEEFF)

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(weatherPalette.chartBackground)
        .border(1.dp, dashPalette.surfaceBorder.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp)) {
      val n = points.size
      if (n < 2) return@Canvas

      val leftGutterPx = if (toggles.showTemperature) 31.dp.toPx() else 0f
      val rightGutterPx = if (showPrecipAxis) 31.dp.toPx() else 0f
      val topPad = 6.dp.toPx()
      val bottomPad = 5.dp.toPx()
      val chartLeft = leftGutterPx
      val chartRight = (size.width - rightGutterPx).coerceAtLeast(chartLeft + 10f)
      val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
      val chartTop = topPad
      val chartBottom = (size.height - bottomPad).coerceAtLeast(chartTop + 1f)
      val chartHeight = chartBottom - chartTop

      fun tempToY(tempC: Double): Float {
        val norm = ((tempC - tempGrid.axisMinC) / (tempGrid.axisMaxC - tempGrid.axisMinC))
          .toFloat()
          .coerceIn(0f, 1f)
        return chartBottom - norm * chartHeight
      }

      val precipMaxHeight = chartHeight * 0.72f
      fun precipToY(amount: Double): Float {
        val norm = (amount / precipGrid.maxValue).toFloat().coerceIn(0f, 1f)
        return chartBottom - norm * precipMaxHeight
      }

      // Freeze (< 0°C) & Hot (>= +30°C) subtle background bands + degree horizontal gridlines
      if (toggles.showTemperature) {
        if (tempGrid.axisMinC < 0.0 && tempGrid.axisMaxC >= 0.0) {
          val yZero = tempToY(0.0)
          drawRect(
            color = weatherPalette.tempColdBottom.copy(alpha = 0.09f),
            topLeft = Offset(chartLeft, yZero),
            size = Size(chartWidth, (chartBottom - yZero).coerceAtLeast(0f)),
          )
        }
        if (tempGrid.axisMaxC > 30.0 && tempGrid.axisMinC <= 30.0) {
          val yThirty = tempToY(30.0)
          drawRect(
            color = weatherPalette.tempHot.copy(alpha = 0.09f),
            topLeft = Offset(chartLeft, chartTop),
            size = Size(chartWidth, (yThirty - chartTop).coerceAtLeast(0f)),
          )
        }

        val gridDash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        tempGrid.ticksDeg.forEach { deg ->
          val y = tempToY(deg.toDouble())
          val isZero = deg == 0
          val isHot = deg >= 30
          val lineColor =
            when {
              isZero -> weatherPalette.tempColdBottom.copy(alpha = 0.80f)
              isHot -> weatherPalette.tempHot.copy(alpha = 0.75f)
              else -> Color(0xFF1E2D47)
            }
          val labelColor =
            when {
              isZero -> weatherPalette.tempColdBottom
              isHot -> weatherPalette.tempHot
              deg < 0 -> weatherPalette.tempColdBottom.copy(alpha = 0.90f)
              else -> DashTextSecondary
            }

          drawLine(
            color = lineColor,
            start = Offset(chartLeft, y),
            end = Offset(chartRight, y),
            strokeWidth = if (isZero || isHot) 1.5.dp.toPx() else 1f,
            pathEffect = if (isZero || isHot) null else gridDash,
          )

          val degText =
            when {
              deg > 0 -> "+$deg°"
              else -> "$deg°"
            }
          val textLayout =
            textMeasurer.measure(
              text = degText,
              style =
                TextStyle(
                  fontFamily = JetBrainsMonoFamily,
                  fontSize = 8.5.sp,
                  fontWeight = if (isZero || isHot) FontWeight.Bold else FontWeight.Medium,
                  color = labelColor,
                ),
            )
          val textX = (chartLeft - textLayout.size.width - 4.dp.toPx()).coerceAtLeast(0f)
          val textY =
            (y - textLayout.size.height / 2f).coerceIn(0f, (size.height - textLayout.size.height).coerceAtLeast(0f))
          drawText(textLayout, topLeft = Offset(textX, textY))
        }
      } else {
        drawLine(
          Color(0xFF172236),
          Offset(chartLeft, chartTop + chartHeight * 0.5f),
          Offset(chartRight, chartTop + chartHeight * 0.5f),
          strokeWidth = 1f,
        )
      }

      // Right-hand precipitation intensity reference ticks (mid & top)
      if (showPrecipAxis) {
        val precipTickColor = if (toggles.showRain) rainTopColor else snowBrightColor
        val precipTicks = listOf(precipGrid.midValue, precipGrid.maxValue)
        precipTicks.forEach { tickVal ->
          val y = precipToY(tickVal)
          drawLine(
            color = precipTickColor.copy(alpha = 0.22f),
            start = Offset(chartLeft, y),
            end = Offset(chartRight, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f), 0f),
          )
          val tickLabel = formatPrecipTick(tickVal, precipGrid.unitLabel)
          val textLayout =
            textMeasurer.measure(
              text = tickLabel,
              style =
                TextStyle(
                  fontFamily = JetBrainsMonoFamily,
                  fontSize = 8.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = precipTickColor.copy(alpha = 0.88f),
                ),
            )
          val textX = (chartRight + 4.dp.toPx()).coerceAtMost((size.width - textLayout.size.width).coerceAtLeast(0f))
          val textY =
            (y - textLayout.size.height / 2f).coerceIn(0f, (size.height - textLayout.size.height).coerceAtLeast(0f))
          drawText(textLayout, topLeft = Offset(textX, textY))
        }
      }

      // Day separator lines at 1/3 and 2/3 of the chart plot area
      val sepColor = Color(0xFF23324D)
      drawLine(
        sepColor,
        Offset(chartLeft + chartWidth / 3f, chartTop),
        Offset(chartLeft + chartWidth / 3f, chartBottom),
        strokeWidth = 1.4f,
      )
      drawLine(
        sepColor,
        Offset(chartLeft + 2f * chartWidth / 3f, chartTop),
        Offset(chartLeft + 2f * chartWidth / 3f, chartBottom),
        strokeWidth = 1.4f,
      )

      val stepX = chartWidth / (n - 1).coerceAtLeast(1)
      val slotWidth = (chartWidth / n).coerceAtLeast(3.dp.toPx())
      val minBarHeightPx = 4.dp.toPx()

      // 1. Rain area + vivid blue bars (bottom-aligned)
      if (toggles.showRain) {
        val rainAreaPath = Path().apply { moveTo(chartLeft, chartBottom) }
        val rainLinePath = Path()
        var hasAnyRain = false
        var peakRainIdx = -1
        var peakRainVal = 0.0

        points.forEachIndexed { idx, pt ->
          val x = (chartLeft + idx * stepX).coerceIn(chartLeft, chartRight)
          val y =
            if (pt.rainMm >= 0.05) {
              hasAnyRain = true
              if (pt.rainMm > peakRainVal) {
                peakRainVal = pt.rainMm
                peakRainIdx = idx
              }
              precipToY(pt.rainMm).coerceAtMost(chartBottom - minBarHeightPx)
            } else {
              chartBottom
            }
          rainAreaPath.lineTo(x, y)
          if (idx == 0) rainLinePath.moveTo(x, y) else rainLinePath.lineTo(x, y)
        }
        rainAreaPath.lineTo(chartRight, chartBottom)
        rainAreaPath.close()

        if (hasAnyRain) {
          drawPath(
            path = rainAreaPath,
            brush =
              Brush.verticalGradient(
                colors = listOf(rainTopColor.copy(alpha = 0.34f), rainBottomColor.copy(alpha = 0.08f)),
                startY = chartBottom - precipMaxHeight,
                endY = chartBottom,
              ),
          )
          drawPath(
            path = rainLinePath,
            color = rainTopColor.copy(alpha = 0.85f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        }

        points.forEachIndexed { idx, pt ->
          if (pt.rainMm >= 0.05) {
            val hasCoincidentSnow = toggles.showSnow && pt.snowCm >= 0.05
            val barW =
              if (hasCoincidentSnow) (slotWidth * 0.45f).coerceAtLeast(2.2.dp.toPx())
              else (slotWidth * 0.82f).coerceAtLeast(2.8.dp.toPx())
            val centerX = chartLeft + idx * stepX
            val leftX =
              if (hasCoincidentSnow) (centerX - barW).coerceIn(chartLeft, (chartRight - barW).coerceAtLeast(chartLeft))
              else (centerX - barW / 2f).coerceIn(chartLeft, (chartRight - barW).coerceAtLeast(chartLeft))
            val barTop = precipToY(pt.rainMm).coerceAtMost(chartBottom - minBarHeightPx)
            val barH = (chartBottom - barTop).coerceAtLeast(minBarHeightPx)

            drawRoundRect(
              brush =
                Brush.verticalGradient(
                  colors = listOf(rainTopColor, rainBottomColor),
                  startY = barTop,
                  endY = chartBottom,
                ),
              topLeft = Offset(leftX, barTop),
              size = Size(barW, barH),
              cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
            // Bright top edge for instant bar readability
            drawLine(
              color = Color(0xFFBAE6FD),
              start = Offset(leftX, barTop),
              end = Offset(leftX + barW, barTop),
              strokeWidth = 1.3.dp.toPx(),
            )
          }
        }

        if (peakRainIdx >= 0 && peakRainVal >= 0.2) {
          val label = String.format(Locale.US, "%.1fmm", peakRainVal)
          val textLayout =
            textMeasurer.measure(
              text = label,
              style =
                TextStyle(
                  fontFamily = JetBrainsMonoFamily,
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFE0F2FE),
                ),
            )
          val peakY = precipToY(peakRainVal).coerceAtMost(chartBottom - minBarHeightPx)
          val badgeW = textLayout.size.width + 6.dp.toPx()
          val badgeH = textLayout.size.height + 2.dp.toPx()
          val badgeX =
            (chartLeft + peakRainIdx * stepX - badgeW / 2f)
              .coerceIn(chartLeft + 2.dp.toPx(), (chartRight - badgeW - 2.dp.toPx()).coerceAtLeast(chartLeft))
          val badgeY = (peakY - badgeH - 3.dp.toPx()).coerceAtLeast(chartTop)
          drawRoundRect(
            color = Color(0xFF0C4A6E).copy(alpha = 0.88f),
            topLeft = Offset(badgeX, badgeY),
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
          )
          drawText(textLayout, topLeft = Offset(badgeX + 3.dp.toPx(), badgeY + 1.dp.toPx()))
        }
      }

      // 2. Snow area + crisp pure-white bars (bottom-aligned, side-by-side when overlapping rain)
      if (toggles.showSnow) {
        val snowAreaPath = Path().apply { moveTo(chartLeft, chartBottom) }
        val snowLinePath = Path()
        var hasAnySnow = false
        var peakSnowIdx = -1
        var peakSnowVal = 0.0

        points.forEachIndexed { idx, pt ->
          val x = (chartLeft + idx * stepX).coerceIn(chartLeft, chartRight)
          val y =
            if (pt.snowCm >= 0.05) {
              hasAnySnow = true
              if (pt.snowCm > peakSnowVal) {
                peakSnowVal = pt.snowCm
                peakSnowIdx = idx
              }
              precipToY(pt.snowCm).coerceAtMost(chartBottom - minBarHeightPx)
            } else {
              chartBottom
            }
          snowAreaPath.lineTo(x, y)
          if (idx == 0) snowLinePath.moveTo(x, y) else snowLinePath.lineTo(x, y)
        }
        snowAreaPath.lineTo(chartRight, chartBottom)
        snowAreaPath.close()

        if (hasAnySnow) {
          drawPath(
            path = snowAreaPath,
            brush =
              Brush.verticalGradient(
                colors = listOf(snowBrightColor.copy(alpha = 0.28f), snowBottomColor.copy(alpha = 0.06f)),
                startY = chartBottom - precipMaxHeight,
                endY = chartBottom,
              ),
          )
          drawPath(
            path = snowLinePath,
            color = snowBrightColor.copy(alpha = 0.90f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        }

        points.forEachIndexed { idx, pt ->
          if (pt.snowCm >= 0.05) {
            val hasCoincidentRain = toggles.showRain && pt.rainMm >= 0.05
            val barW =
              if (hasCoincidentRain) (slotWidth * 0.45f).coerceAtLeast(2.2.dp.toPx())
              else (slotWidth * 0.80f).coerceAtLeast(2.8.dp.toPx())
            val centerX = chartLeft + idx * stepX
            val leftX =
              if (hasCoincidentRain) centerX.coerceIn(chartLeft, (chartRight - barW).coerceAtLeast(chartLeft))
              else (centerX - barW / 2f).coerceIn(chartLeft, (chartRight - barW).coerceAtLeast(chartLeft))
            val barTop = precipToY(pt.snowCm).coerceAtMost(chartBottom - minBarHeightPx)
            val barH = (chartBottom - barTop).coerceAtLeast(minBarHeightPx)

            drawRoundRect(
              brush =
                Brush.verticalGradient(
                  colors = listOf(snowBrightColor, snowBottomColor),
                  startY = barTop,
                  endY = chartBottom,
                ),
              topLeft = Offset(leftX, barTop),
              size = Size(barW, barH),
              cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
            drawRoundRect(
              color = Color.White,
              topLeft = Offset(leftX, barTop),
              size = Size(barW, barH),
              cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
              style = Stroke(width = 0.9.dp.toPx()),
            )
          }
        }

        if (peakSnowIdx >= 0 && peakSnowVal >= 0.2) {
          val label = String.format(Locale.US, "%.1fcm", peakSnowVal)
          val textLayout =
            textMeasurer.measure(
              text = label,
              style =
                TextStyle(
                  fontFamily = JetBrainsMonoFamily,
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF0F172A),
                ),
            )
          val peakY = precipToY(peakSnowVal).coerceAtMost(chartBottom - minBarHeightPx)
          val badgeW = textLayout.size.width + 6.dp.toPx()
          val badgeH = textLayout.size.height + 2.dp.toPx()
          val badgeX =
            (chartLeft + peakSnowIdx * stepX - badgeW / 2f)
              .coerceIn(chartLeft + 2.dp.toPx(), (chartRight - badgeW - 2.dp.toPx()).coerceAtLeast(chartLeft))
          val badgeY = (peakY - badgeH - 3.dp.toPx()).coerceAtLeast(chartTop)
          drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(badgeX, badgeY),
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
          )
          drawText(textLayout, topLeft = Offset(badgeX + 3.dp.toPx(), badgeY + 1.dp.toPx()))
        }
      }

      // 3. Atmospheric Pressure curve (dashed violet)
      if (toggles.showPressure) {
        val minP = points.minOf { it.pressureHpa } - 2.0
        val maxP = (points.maxOf { it.pressureHpa } + 2.0).coerceAtLeast(minP + 6.0)
        val pPath = Path()
        points.forEachIndexed { idx, pt ->
          val x = chartLeft + idx * stepX
          val norm = ((pt.pressureHpa - minP) / (maxP - minP)).toFloat()
          val y = chartBottom - chartHeight * 0.1f - norm * (chartHeight * 0.75f)
          if (idx == 0) pPath.moveTo(x, y) else pPath.lineTo(x, y)
        }
        drawPath(
          path = pPath,
          color = weatherPalette.pressureLine.copy(alpha = 0.85f),
          style =
            Stroke(
              width = 1.8.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
            ),
        )
      }

      // 4. Bold Temperature smooth line mapped accurately onto degree gridlines
      if (toggles.showTemperature) {
        val tPath = Path()
        val coords =
          points.mapIndexed { idx, pt ->
            Offset(chartLeft + idx * stepX, tempToY(pt.temperatureC))
          }
        coords.forEachIndexed { idx, pt ->
          if (idx == 0) {
            tPath.moveTo(pt.x, pt.y)
          } else {
            val prev = coords[idx - 1]
            val midX = (prev.x + pt.x) / 2f
            tPath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
          }
        }
        // Dark contrast casing so thick temperature line stays crisp over bright rain/snow bars
        drawPath(
          path = tPath,
          color = Color.Black.copy(alpha = 0.62f),
          style = Stroke(width = 5.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
          path = tPath,
          brush =
            buildTemperatureCurveBrush(
              minTempC = minTemp,
              maxTempC = maxTemp,
              yZero = tempToY(0.0),
              chartTop = chartTop,
              chartBottom = chartBottom,
              weatherPalette = weatherPalette,
            ),
          style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }
    }

    // Compact legend pill tucked cleanly in top-right corner inside the plot area
    if (toggles.showTemperature || toggles.showRain || toggles.showSnow || toggles.showPressure) {
      MetricLegendRow(
        toggles = toggles,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(top = 4.dp, end = if (showPrecipAxis) 35.dp else 4.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(weatherPalette.chartBackground.copy(alpha = 0.82f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
      )
    }
  }
}

private fun wmoWeatherIcon(code: Int, weatherPalette: WeatherGraphPalette): Pair<ImageVector, Color> =
  when (code) {
    0, 1 -> Icons.Rounded.WbSunny to weatherPalette.tempWarm
    2, 3, 45, 48 -> Icons.Rounded.Cloud to DashTextSecondary
    in 51..67, in 80..82 -> Icons.Rounded.WaterDrop to weatherPalette.rainBar
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit to weatherPalette.snowBar
    in 95..99 -> Icons.Rounded.Thunderstorm to weatherPalette.tempHot
    else -> Icons.Rounded.Cloud to DashTextSecondary
  }
