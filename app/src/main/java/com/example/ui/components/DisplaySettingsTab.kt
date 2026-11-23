package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DisplayBrightnessMath
import com.example.model.DisplayRuntimeStatus
import com.example.model.DisplaySettings
import com.example.ui.theme.DashTextPrimary
import com.example.ui.theme.DashTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalDashPalette
import com.example.ui.theme.RainCyan
import com.example.ui.theme.TempAmber
import com.example.ui.theme.tr
import kotlin.math.roundToInt

@Composable
fun DisplaySettingsTab(
  settings: DisplaySettings,
  runtimeStatus: DisplayRuntimeStatus,
  onUserActivity: () -> Unit,
  onUpdateSettings: ((DisplaySettings) -> DisplaySettings) -> Unit,
  onSetNightPreview: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  val lang = LocalAppLanguage.current
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Left column: Keep screen on + Auto ambient light brightness + live sensor readout
    Column(
      modifier =
        Modifier.weight(1f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(10.dp))
          .background(palette.surfaceElevated.copy(alpha = 0.6f))
          .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
          .padding(10.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = tr(lang, "Obrazovka a automatický jas", "Screen & Auto Brightness"),
        style = MaterialTheme.typography.titleMedium,
        color = DashTextPrimary,
      )

      DisplayToggleCard(
        icon = Icons.Rounded.ScreenLockPortrait,
        iconTint = LiveEmerald,
        title = tr(lang, "Stále zapnutý displej", "Keep Screen Always On"),
        subtitle =
          tr(
            lang,
            "Zabrání zhasnutí a uzamčení displeje při spuštěné aplikaci",
            "Prevents screen timeout and lock while the app is open",
          ),
        checked = settings.keepScreenOn,
        testTag = "toggle_keep_screen_on",
        onCheckedChange = { checked ->
          onUserActivity()
          onUpdateSettings { it.copy(keepScreenOn = checked) }
        },
      )

      DisplayToggleCard(
        icon = Icons.Rounded.BrightnessAuto,
        iconTint = RainCyan,
        title = tr(lang, "Automatický jas podle světla", "Auto Brightness by Light"),
        subtitle =
          tr(
            lang,
            "Přizpůsobí jas podle čidla okolního světla (mimo noční režim)",
            "Adapts screen brightness from ambient light sensor outside night hours",
          ),
        checked = settings.autoBrightnessEnabled,
        testTag = "toggle_auto_brightness",
        onCheckedChange = { checked ->
          onUserActivity()
          onUpdateSettings { it.copy(autoBrightnessEnabled = checked) }
        },
      )

      // Live light sensor & effective brightness readout
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surface)
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = tr(lang, "Aktuální stav jasu", "Current Brightness Status"),
          style = MaterialTheme.typography.labelMedium,
          color = DashTextSecondary,
        )
        val sensorText =
          if (runtimeStatus.hasLightSensor) {
            val luxLabel =
              runtimeStatus.ambientLux?.roundToInt()?.let { "$it lx" }
                ?: tr(lang, "čtu...", "reading...")
            "${tr(lang, "Čidlo okolního světla", "Ambient light sensor")}: $luxLabel"
          } else {
            tr(
              lang,
              "Čidlo světla nedostupné (použije se výchozí jas systému)",
              "Light sensor unavailable (using system default brightness)",
            )
          }
        Text(
          text = sensorText,
          style = MaterialTheme.typography.bodyMedium,
          color = DashTextPrimary,
        )
        val (statusText, statusColor) =
          when {
            runtimeStatus.isPreviewActive ->
              "${tr(lang, "Náhled nočního jasu", "Night preview brightness")}: ${settings.nightBrightnessPercent} %" to TempAmber
            runtimeStatus.isNightModeActive ->
              "${tr(lang, "Aktivní noční režim", "Night mode active")}: ${settings.nightBrightnessPercent} %" to palette.accentBright
            runtimeStatus.effectiveBrightness >= 0f ->
              "${tr(lang, "Automatický jas", "Auto brightness")}: ${(runtimeStatus.effectiveBrightness * 100f).roundToInt()} %" to
                LiveEmerald
            else ->
              "${tr(lang, "Režim jasu: Systémový výchozí", "Brightness mode: System default")}" to DashTextSecondary
          }
        Text(
          text = statusText,
          style = MaterialTheme.typography.labelMedium,
          color = statusColor,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    // Right column: Night mode schedule + minimum brightness intensity
    Column(
      modifier =
        Modifier.weight(1.1f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(10.dp))
          .background(palette.surfaceElevated.copy(alpha = 0.6f))
          .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
          .padding(10.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      DisplayToggleCard(
        icon = Icons.Rounded.DarkMode,
        iconTint = palette.accentBright,
        title = tr(lang, "Noční režim (minimální jas)", "Night Mode (Min Brightness)"),
        subtitle =
          tr(
            lang,
            "V nastaveném čase automaticky sníží jas na zvolené minimum",
            "Automatically dims display to selected minimum during night hours",
          ),
        checked = settings.nightModeEnabled,
        testTag = "toggle_night_mode",
        onCheckedChange = { checked ->
          onUserActivity()
          onUpdateSettings { it.copy(nightModeEnabled = checked) }
        },
      )

      // Time window From - To
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TimeStepperCard(
          label = tr(lang, "Čas OD", "From"),
          hour = settings.nightStartHour,
          minute = settings.nightStartMinute,
          testTagPrefix = "night_start",
          onShiftMinutes = { deltaMin ->
            onUserActivity()
            onUpdateSettings { current ->
              val (newH, newM) = shiftHourMinute(current.nightStartHour, current.nightStartMinute, deltaMin)
              current.copy(nightStartHour = newH, nightStartMinute = newM)
            }
          },
          modifier = Modifier.weight(1f),
        )
        TimeStepperCard(
          label = tr(lang, "Čas DO", "To"),
          hour = settings.nightEndHour,
          minute = settings.nightEndMinute,
          testTagPrefix = "night_end",
          onShiftMinutes = { deltaMin ->
            onUserActivity()
            onUpdateSettings { current ->
              val (newH, newM) = shiftHourMinute(current.nightEndHour, current.nightEndMinute, deltaMin)
              current.copy(nightEndHour = newH, nightEndMinute = newM)
            }
          },
          modifier = Modifier.weight(1f),
        )
      }

      // Quick time range presets
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        val schedulePresets =
          listOf(
            "21:00–06:00" to (21 to 6),
            "22:00–06:00" to (22 to 6),
            "23:00–07:00" to (23 to 7),
          )
        schedulePresets.forEach { (label, range) ->
          val selected =
            settings.nightStartHour == range.first &&
              settings.nightStartMinute == 0 &&
              settings.nightEndHour == range.second &&
              settings.nightEndMinute == 0
          FilterChip(
            selected = selected,
            onClick = {
              onUserActivity()
              onUpdateSettings {
                it.copy(
                  nightStartHour = range.first,
                  nightStartMinute = 0,
                  nightEndHour = range.second,
                  nightEndMinute = 0,
                )
              }
            },
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = palette.accent,
                selectedLabelColor = Color.White,
                containerColor = palette.surface,
                labelColor = DashTextSecondary,
              ),
          )
        }
      }

      // Night brightness intensity slider + preview
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surface)
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "${tr(lang, "Intenzita jasu v noci", "Night brightness")}: ${settings.nightBrightnessPercent} %",
            style = MaterialTheme.typography.bodyLarge,
            color = DashTextPrimary,
            fontWeight = FontWeight.SemiBold,
          )
          FilterChip(
            selected = runtimeStatus.isPreviewActive,
            onClick = {
              onUserActivity()
              onSetNightPreview(!runtimeStatus.isPreviewActive)
            },
            leadingIcon = {
              Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
            },
            label = {
              Text(
                if (runtimeStatus.isPreviewActive) tr(lang, "Ukončit náhled", "End Preview")
                else tr(lang, "Vyzkoušet", "Preview"),
                style = MaterialTheme.typography.labelMedium,
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = TempAmber,
                selectedLabelColor = Color.Black,
                containerColor = palette.surfaceElevated,
                labelColor = DashTextPrimary,
              ),
            modifier = Modifier.testTag("night_brightness_preview_button"),
          )
        }

        Slider(
          value = settings.nightBrightnessPercent.toFloat(),
          onValueChange = { value ->
            onUserActivity()
            onUpdateSettings {
              it.copy(nightBrightnessPercent = value.roundToInt().coerceIn(1, 50))
            }
          },
          valueRange = 1f..50f,
          colors =
            SliderDefaults.colors(
              thumbColor = palette.accentBright,
              activeTrackColor = palette.accent,
              inactiveTrackColor = palette.surfaceBorder,
            ),
          modifier = Modifier.fillMaxWidth().testTag("night_brightness_slider"),
        )

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf(1 to "1 % (min)", 3 to "3 %", 5 to "5 %", 10 to "10 %", 20 to "20 %").forEach {
            (pct, label) ->
            val selected = settings.nightBrightnessPercent == pct
            FilterChip(
              selected = selected,
              onClick = {
                onUserActivity()
                onUpdateSettings { it.copy(nightBrightnessPercent = pct) }
              },
              label = { Text(label, style = MaterialTheme.typography.labelSmall) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = palette.accent,
                  selectedLabelColor = Color.White,
                  containerColor = palette.surfaceElevated,
                  labelColor = DashTextSecondary,
                ),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DisplayToggleCard(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String,
  checked: Boolean,
  testTag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  val palette = LocalDashPalette.current
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(palette.surface)
        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
        .clickable { onCheckedChange(!checked) }
        .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f).padding(end = 6.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(20.dp),
      )
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          color = DashTextPrimary,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = DashTextSecondary,
        )
      }
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = Color.White,
          checkedTrackColor = palette.accent,
        ),
    )
  }
}

@Composable
private fun TimeStepperCard(
  label: String,
  hour: Int,
  minute: Int,
  testTagPrefix: String,
  onShiftMinutes: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(palette.surface)
        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = DashTextSecondary,
      )
      Text(
        text = DisplayBrightnessMath.formatHourMinute(hour, minute),
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = DashTextPrimary,
        modifier = Modifier.testTag("${testTagPrefix}_value"),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      TimeStepPill("-1h", "${testTagPrefix}_minus_h", Modifier.weight(1f)) { onShiftMinutes(-60) }
      TimeStepPill("+1h", "${testTagPrefix}_plus_h", Modifier.weight(1f)) { onShiftMinutes(60) }
      TimeStepPill("-15m", "${testTagPrefix}_minus_m", Modifier.weight(1f)) { onShiftMinutes(-15) }
      TimeStepPill("+15m", "${testTagPrefix}_plus_m", Modifier.weight(1f)) { onShiftMinutes(15) }
    }
  }
}

@Composable
private fun TimeStepPill(
  label: String,
  testTag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val palette = LocalDashPalette.current
  Box(
    modifier =
      modifier
        .height(48.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(palette.surfaceElevated)
        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(6.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = DashTextPrimary,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      softWrap = false,
    )
  }
}

internal fun shiftHourMinute(hour: Int, minute: Int, deltaMinutes: Int): Pair<Int, Int> {
  val total = (((hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59) + deltaMinutes) % 1440) + 1440) % 1440
  return (total / 60) to (total % 60)
}
