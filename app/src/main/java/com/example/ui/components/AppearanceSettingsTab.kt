package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppColorSchemePreset
import com.example.model.AppearanceSettings
import com.example.model.BatteryRuntimeStatus
import com.example.model.ClockStylePreset
import com.example.model.WeatherColorThemePreset
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalDashPalette
import com.example.ui.theme.tr

/**
 * Responsive non-scrolling Appearance ("Vzhled") tab: adapts to the available screen width & height
 * using weighted 2x2 subgrids so all color themes, clock styles, and toggles stay visible at once
 * without vertical scrolling on both phones and tablets.
 */
@Composable
fun AppearanceSettingsTab(
  settings: AppearanceSettings,
  batteryStatus: BatteryRuntimeStatus,
  onUserActivity: () -> Unit,
  onUpdateSettings: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  val lang = LocalAppLanguage.current

  BoxWithConstraints(modifier = modifier) {
    val isCompactHeight = maxHeight < 340.dp
    val outerGap = if (isCompactHeight) 8.dp else 10.dp
    val cardPad = if (isCompactHeight) 8.dp else 10.dp
    val innerGap = if (isCompactHeight) 5.dp else 7.dp

    Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.spacedBy(outerGap),
    ) {
      // LEFT COLUMN: App Color Scheme (3+2 grid) + Weather Color Theme (2x2 grid)
      Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(outerGap),
      ) {
        // 1. App Color Scheme
        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(palette.surfaceElevated.copy(alpha = 0.6f))
              .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
              .padding(cardPad),
          verticalArrangement = Arrangement.spacedBy(innerGap),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(Icons.Rounded.Palette, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(16.dp))
            Text(
              text = tr(lang, "Barevné schéma aplikace", "App Color Theme"),
              style = MaterialTheme.typography.titleMedium,
              fontSize = if (isCompactHeight) 14.sp else 15.sp,
              color = palette.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          val appPresets = AppColorSchemePreset.entries
          Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(innerGap),
          ) {
            Row(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(innerGap),
            ) {
              appPresets.take(3).forEach { preset ->
                AppThemeOptionCard(
                  label = preset.labelFor(lang),
                  swatchColor = Color(preset.swatchColorHex),
                  selected = settings.appColorSchemeId == preset.id,
                  testTag = "theme_app_${preset.id}",
                  onClick = {
                    onUserActivity()
                    onUpdateSettings { it.copy(appColorSchemeId = preset.id) }
                  },
                  modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                )
              }
            }
            Row(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(innerGap),
            ) {
              appPresets.drop(3).forEach { preset ->
                AppThemeOptionCard(
                  label = preset.labelFor(lang),
                  swatchColor = Color(preset.swatchColorHex),
                  selected = settings.appColorSchemeId == preset.id,
                  testTag = "theme_app_${preset.id}",
                  onClick = {
                    onUserActivity()
                    onUpdateSettings { it.copy(appColorSchemeId = preset.id) }
                  },
                  modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                )
              }
            }
          }
        }

        // 2. Weather Color Theme (2x2 grid)
        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(palette.surfaceElevated.copy(alpha = 0.6f))
              .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
              .padding(cardPad),
          verticalArrangement = Arrangement.spacedBy(innerGap),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(16.dp))
            Text(
              text = tr(lang, "Barevný styl počasí", "Weather Color Theme"),
              style = MaterialTheme.typography.titleMedium,
              fontSize = if (isCompactHeight) 14.sp else 15.sp,
              color = palette.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          val weatherPresets = WeatherColorThemePreset.entries
          Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(innerGap),
          ) {
            listOf(weatherPresets.take(2), weatherPresets.drop(2)).forEach { rowItems ->
              Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(innerGap),
              ) {
                rowItems.forEach { preset ->
                  WeatherThemeOptionCard(
                    label = preset.labelFor(lang),
                    warmColor = Color(preset.warmSwatchHex),
                    coldColor = Color(preset.coldSwatchHex),
                    selected = settings.weatherColorThemeId == preset.id,
                    testTag = "theme_weather_${preset.id}",
                    onClick = {
                      onUserActivity()
                      onUpdateSettings { it.copy(weatherColorThemeId = preset.id) }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                  )
                }
              }
            }
          }
        }
      }

      // RIGHT COLUMN: Clock & Date Style (2x2 grid) + Quick Switches (2 rows)
      Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(outerGap),
      ) {
        // 3. Clock & Date Style (2x2 grid)
        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(palette.surfaceElevated.copy(alpha = 0.6f))
              .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
              .padding(cardPad),
          verticalArrangement = Arrangement.spacedBy(innerGap),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(16.dp))
            Text(
              text = tr(lang, "Vzhled hodin a datumu", "Clock & Date Style"),
              style = MaterialTheme.typography.titleMedium,
              fontSize = if (isCompactHeight) 14.sp else 15.sp,
              color = palette.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          val clockPresets = ClockStylePreset.entries
          Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(innerGap),
          ) {
            listOf(clockPresets.take(2), clockPresets.drop(2)).forEach { rowItems ->
              Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(innerGap),
              ) {
                rowItems.forEach { preset ->
                  ClockStyleOptionCard(
                    label = preset.labelFor(lang),
                    selected = settings.clockStyleId == preset.id,
                    testTag = "clock_style_${preset.id}",
                    onClick = {
                      onUserActivity()
                      onUpdateSettings { it.copy(clockStyleId = preset.id) }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                  )
                }
              }
            }
          }
        }

        // 4. Clock Seconds & Battery Status Toggles (2 balanced switch rows)
        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(palette.surfaceElevated.copy(alpha = 0.6f))
              .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
              .padding(cardPad),
          verticalArrangement = Arrangement.spacedBy(innerGap),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = LiveEmerald, modifier = Modifier.size(16.dp))
            Text(
              text = tr(lang, "Zobrazení prvků", "Display Elements"),
              style = MaterialTheme.typography.titleMedium,
              fontSize = if (isCompactHeight) 14.sp else 15.sp,
              color = palette.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(innerGap),
          ) {
            // Toggle seconds
            Row(
              modifier =
                Modifier.weight(1f)
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(palette.surface)
                  .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
                  .clickable {
                    onUserActivity()
                    onUpdateSettings { it.copy(showClockSeconds = !it.showClockSeconds) }
                  }
                  .padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Text(
                  text = tr(lang, "Sekundy v hodinách", "Clock Seconds"),
                  style = MaterialTheme.typography.bodyLarge,
                  color = palette.textPrimary,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (!isCompactHeight) {
                  Text(
                    text = tr(lang, "Vypnutím větší číslice (HH:mm)", "Larger digits when off (HH:mm)"),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              Switch(
                checked = settings.showClockSeconds,
                onCheckedChange = { checked ->
                  onUserActivity()
                  onUpdateSettings { it.copy(showClockSeconds = checked) }
                },
                modifier = Modifier.testTag("toggle_clock_seconds"),
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = palette.accent),
              )
            }

            // Toggle battery badge beside menu button
            Row(
              modifier =
                Modifier.weight(1f)
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(palette.surface)
                  .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
                  .clickable {
                    onUserActivity()
                    onUpdateSettings { it.copy(showBatteryStatus = !it.showBatteryStatus) }
                  }
                  .padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                val pctText =
                  "${batteryStatus.levelPercent}%" +
                    if (batteryStatus.isCharging) " ⚡" else ""
                Text(
                  text = "${tr(lang, "Baterie vedle menu", "Battery Next to Menu")} ($pctText)",
                  style = MaterialTheme.typography.bodyLarge,
                  color = palette.textPrimary,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (!isCompactHeight) {
                  Text(
                    text = tr(lang, "Zarovná tlačítko menu vlevo", "Aligns menu button to the left"),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              Switch(
                checked = settings.showBatteryStatus,
                onCheckedChange = { checked ->
                  onUserActivity()
                  onUpdateSettings { it.copy(showBatteryStatus = checked) }
                },
                modifier = Modifier.testTag("toggle_battery_status"),
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = palette.accent),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AppThemeOptionCard(
  label: String,
  swatchColor: Color,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(if (selected) palette.accent.copy(alpha = 0.24f) else palette.surface)
        .border(
          1.dp,
          if (selected) palette.accentBright else palette.surfaceBorder,
          RoundedCornerShape(8.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier =
        Modifier.size(14.dp)
          .clip(CircleShape)
          .background(swatchColor)
          .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = palette.textPrimary,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(15.dp))
    }
  }
}

@Composable
private fun WeatherThemeOptionCard(
  label: String,
  warmColor: Color,
  coldColor: Color,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(if (selected) palette.accent.copy(alpha = 0.24f) else palette.surface)
        .border(
          1.dp,
          if (selected) palette.accentBright else palette.surfaceBorder,
          RoundedCornerShape(8.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
      Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(warmColor))
      Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(coldColor))
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = palette.textPrimary,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(15.dp))
    }
  }
}

@Composable
private fun ClockStyleOptionCard(
  label: String,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(if (selected) palette.accent.copy(alpha = 0.24f) else palette.surface)
        .border(
          1.dp,
          if (selected) palette.accentBright else palette.surfaceBorder,
          RoundedCornerShape(8.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = palette.textPrimary,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(15.dp))
    }
  }
}
