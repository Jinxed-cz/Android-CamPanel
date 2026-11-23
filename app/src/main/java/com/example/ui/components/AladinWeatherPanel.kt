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
    if (toggles.showRain) LegendDot(tr(lang, "Déšť", "Rain"), weatherPalette.rainBar)
    if (toggles.showSnow) LegendDot(tr(lang, "Sníh", "Snow"), weatherPalette.snowBar)
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
          modifier = Modifier.weight(1f),
        )
      }
    }

    Spacer(Modifier.height(5.dp))

    // 72-hour Meteogram Chart
    AladinMeteogramCanvas(
      points = data.hourlyPoints,
      toggles = toggles,
      modifier = Modifier.fillMaxWidth().weight(1f),
    )
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
  modifier: Modifier = Modifier,
) {
  val dashPalette = LocalDashPalette.current
  val weatherPalette = LocalWeatherGraphPalette.current
  val (icon, iconTint) = wmoWeatherIcon(summary.dominantWeatherCode, weatherPalette)
  val density = LocalDensity.current
  val textMeasurer = rememberTextMeasurer()
  val titleStyle =
    MaterialTheme.typography.titleMedium.copy(
      fontSize = 13.5.sp,
      lineHeight = 15.sp,
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
        .padding(horizontal = 6.dp, vertical = 4.dp)
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
  val textMeasurer = rememberTextMeasurer()

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
      val topPad = 6.dp.toPx()
      val bottomPad = 5.dp.toPx()
      val chartLeft = leftGutterPx
      val chartRight = size.width
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

      // Freeze (< 0°C) & Hot (>= +30°C) subtle background bands + degree horizontal gridlines
      if (toggles.showTemperature) {
        if (tempGrid.axisMinC < 0.0 && tempGrid.axisMaxC >= 0.0) {
          val yZero = tempToY(0.0)
          drawRect(
            color = weatherPalette.tempColdBottom.copy(alpha = 0.08f),
            topLeft = Offset(chartLeft, yZero),
            size = Size(chartWidth, (chartBottom - yZero).coerceAtLeast(0f)),
          )
        }
        if (tempGrid.axisMaxC > 30.0 && tempGrid.axisMinC <= 30.0) {
          val yThirty = tempToY(30.0)
          drawRect(
            color = weatherPalette.tempHot.copy(alpha = 0.08f),
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
              isZero -> weatherPalette.tempColdBottom.copy(alpha = 0.72f)
              isHot -> weatherPalette.tempHot.copy(alpha = 0.68f)
              else -> Color(0xFF1E2D47)
            }
          val labelColor =
            when {
              isZero -> weatherPalette.tempColdBottom
              isHot -> weatherPalette.tempHot
              deg < 0 -> weatherPalette.tempColdBottom.copy(alpha = 0.85f)
              else -> DashTextSecondary
            }

          drawLine(
            color = lineColor,
            start = Offset(chartLeft, y),
            end = Offset(chartRight, y),
            strokeWidth = if (isZero || isHot) 1.6f else 1f,
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
      val barWidth = (chartWidth / n) * 0.65f

      // 1. Rain bars (bottom-aligned)
      if (toggles.showRain) {
        val maxRain = (points.maxOf { it.rainMm }).coerceAtLeast(3.0)
        points.forEachIndexed { idx, pt ->
          if (pt.rainMm > 0.01) {
            val barH = ((pt.rainMm / maxRain) * (chartHeight * 0.55f)).toFloat().coerceAtLeast(2f)
            val x = chartLeft + idx * stepX - barWidth / 2f
            drawRoundRect(
              color = weatherPalette.rainBar.copy(alpha = 0.65f),
              topLeft = Offset(x.coerceAtLeast(chartLeft), chartBottom - barH),
              size = Size(barWidth, barH),
              cornerRadius = CornerRadius(2f, 2f),
            )
          }
        }
      }

      // 2. Snow bars (bottom-aligned, bright ice)
      if (toggles.showSnow) {
        val maxSnow = (points.maxOf { it.snowCm }).coerceAtLeast(2.0)
        points.forEachIndexed { idx, pt ->
          if (pt.snowCm > 0.01) {
            val barH = ((pt.snowCm / maxSnow) * (chartHeight * 0.55f)).toFloat().coerceAtLeast(2f)
            val x = chartLeft + idx * stepX - barWidth / 2f
            drawRoundRect(
              color = weatherPalette.snowBar.copy(alpha = 0.85f),
              topLeft = Offset(x.coerceAtLeast(chartLeft), chartBottom - barH),
              size = Size(barWidth * 0.7f, barH),
              cornerRadius = CornerRadius(2f, 2f),
            )
          }
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
              width = 2f,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
            ),
        )
      }

      // 4. Temperature smooth line mapped accurately onto the degree gridlines
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
          style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }
    }

    // Compact legend pill tucked cleanly in top-right corner of the meteogram
    if (toggles.showTemperature || toggles.showRain || toggles.showSnow || toggles.showPressure) {
      MetricLegendRow(
        toggles = toggles,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(4.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(weatherPalette.chartBackground.copy(alpha = 0.78f))
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
