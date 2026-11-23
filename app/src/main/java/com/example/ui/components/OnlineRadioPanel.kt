package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActiveRadioStation
import com.example.model.RadioStation
import com.example.model.RadioUiState
import com.example.ui.theme.DashTextMuted
import com.example.ui.theme.DashTextPrimary
import com.example.ui.theme.DashTextSecondary
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalDashPalette
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.TempAmber
import com.example.ui.theme.tr

/**
 * Online radio panel placed to the right of the weather forecast.
 * Top half: Station icon card (tap to pick presets + saved favorite internet radios).
 * Bottom half: Play/Stop toggle button alongside - and + volume buttons.
 */
@Composable
fun OnlineRadioPanel(
  radioState: RadioUiState,
  availableStations: List<ActiveRadioStation> =
    RadioStation.entries.map { ActiveRadioStation.fromPreset(it) },
  onSelectStation: (ActiveRadioStation) -> Unit,
  onTogglePlayStop: () -> Unit,
  onVolumeUp: () -> Unit,
  onVolumeDown: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  val lang = LocalAppLanguage.current
  var showStationPicker by remember { mutableStateOf(false) }

  val shape = RoundedCornerShape(12.dp)
  val station = radioState.activeStation
  val accentColor = Color(station.accentColorHex)

  Column(
    modifier =
      modifier
        .clip(shape)
        .background(palette.surface)
        .border(1.dp, palette.surfaceBorder, shape)
        .padding(8.dp)
        .testTag("online_radio_panel"),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // TOP HALF: Station Icon & Info (clickable to open station selection)
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .weight(1f)
          .clip(RoundedCornerShape(9.dp))
          .background(
            Brush.verticalGradient(
              listOf(accentColor.copy(alpha = 0.22f), palette.surfaceElevated)
            )
          )
          .border(
            1.dp,
            if (radioState.isPlaying) accentColor.copy(alpha = 0.7f) else palette.surfaceBorder,
            RoundedCornerShape(9.dp),
          )
          .clickable { showStationPicker = true }
          .padding(horizontal = 8.dp, vertical = 6.dp)
          .testTag("radio_station_button"),
    ) {
      // Output route indicator (Bluetooth speaker vs internal speaker)
      Row(
        modifier = Modifier.align(Alignment.TopEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Icon(
          imageVector =
            if (radioState.isBluetoothConnected) Icons.Rounded.BluetoothAudio
            else Icons.Rounded.VolumeUp,
          contentDescription = radioState.outputDeviceName,
          tint = if (radioState.isBluetoothConnected) palette.accentBright else DashTextMuted,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text = if (radioState.isBluetoothConnected) "BT" else "${radioState.volumePercent}%",
          style = MaterialTheme.typography.labelSmall,
          color = if (radioState.isBluetoothConnected) palette.accentBright else DashTextSecondary,
          fontSize = 9.sp,
        )
      }

      // Station Icon + Name centered in top half
      Column(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Box(
          modifier =
            Modifier.size(34.dp)
              .clip(CircleShape)
              .background(accentColor.copy(alpha = 0.24f))
              .border(1.5.dp, accentColor, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
        Icon(
          imageVector = Icons.Rounded.Radio,
          contentDescription = tr(lang, "Vybrat rozhlasovou stanici", "Select radio station"),
          tint = accentColor,
          modifier = Modifier.size(18.dp),
        )
        }
        Spacer(Modifier.height(4.dp))
        Text(
          text = station.displayName,
          style = MaterialTheme.typography.titleMedium,
          color = DashTextPrimary,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val outputName =
          if (!radioState.isBluetoothConnected && radioState.outputDeviceName == "Reproduktor")
            tr(lang, "Reproduktor", "Speaker")
          else radioState.outputDeviceName
        Text(
          text =
            when {
              radioState.errorMessage != null -> radioState.errorMessage
              radioState.isBuffering -> tr(lang, "Připojuji...", "Connecting...")
              radioState.isPlaying -> "${tr(lang, "Hraje", "Playing")} · $outputName"
              else -> tr(lang, "Klikni pro stanici", "Tap for station")
            },
          style = MaterialTheme.typography.labelSmall,
          color =
            when {
              radioState.errorMessage != null -> OfflineRed
              radioState.isPlaying -> LiveEmerald
              else -> DashTextMuted
            },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontSize = 9.sp,
        )
      }
    }

    Spacer(Modifier.height(6.dp))

    // Volume level thin bar
    LinearProgressIndicator(
      progress = { (radioState.volumePercent / 100f).coerceIn(0f, 1f) },
      modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
      color = accentColor,
      trackColor = palette.surfaceElevated,
    )

    Spacer(Modifier.height(6.dp))

    // BOTTOM HALF: Play/Stop button + Volume - and + buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Volume -
      Box(
        modifier =
          Modifier.size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceElevated)
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onVolumeDown)
            .testTag("radio_volume_down"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Remove,
          contentDescription = tr(lang, "Snížit hlasitost", "Volume down"),
          tint = DashTextPrimary,
          modifier = Modifier.size(20.dp),
        )
      }

      // Play / Stop toggle
      val isActive = radioState.isPlaying || radioState.isBuffering
      Box(
        modifier =
          Modifier.weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) OfflineRed else palette.accent)
            .clickable(onClick = onTogglePlayStop)
            .testTag("radio_play_stop_button"),
        contentAlignment = Alignment.Center,
      ) {
        if (radioState.isBuffering) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color.White,
            strokeWidth = 2.5.dp,
          )
        } else {
          Icon(
            imageVector = if (isActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            contentDescription = if (isActive) "Stop" else "Play",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
          )
        }
      }

      // Volume +
      Box(
        modifier =
          Modifier.size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceElevated)
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onVolumeUp)
            .testTag("radio_volume_up"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = tr(lang, "Zvýšit hlasitost", "Volume up"),
          tint = DashTextPrimary,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }

  if (showStationPicker) {
    StationPickerDialog(
      stations = availableStations,
      selectedStation = station,
      onSelect = { chosen ->
        onSelectStation(chosen)
        showStationPicker = false
      },
      onDismiss = { showStationPicker = false },
    )
  }
}

@Composable
private fun StationPickerDialog(
  stations: List<ActiveRadioStation>,
  selectedStation: ActiveRadioStation,
  onSelect: (ActiveRadioStation) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalDashPalette.current
  val lang = LocalAppLanguage.current
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = palette.surface,
    title = {
      Text(
        text = tr(lang, "Výběr online rádia", "Select Online Radio"),
        style = MaterialTheme.typography.titleMedium,
        color = DashTextPrimary,
      )
    },
    text = {
      LazyColumn(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(max = 260.dp)
            .testTag("radio_station_picker_dialog"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(stations, key = { it.id }) { station ->
          val isSelected = station.id == selectedStation.id
          val accent = Color(station.accentColorHex)
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) accent.copy(alpha = 0.22f) else palette.surfaceElevated)
                .border(
                  1.dp,
                  if (isSelected) accent else palette.surfaceBorder,
                  RoundedCornerShape(10.dp),
                )
                .clickable { onSelect(station) }
                .padding(horizontal = 12.dp)
                .testTag("station_option_${station.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f).padding(end = 6.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(30.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = station.shortTag,
                  style = MaterialTheme.typography.labelSmall,
                  color = accent,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  if (station.isFavorite) {
                    Icon(
                      imageVector = Icons.Rounded.Star,
                      contentDescription = tr(lang, "Oblíbené", "Favorite"),
                      tint = TempAmber,
                      modifier = Modifier.size(13.dp),
                    )
                  }
                  Text(
                    text = station.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DashTextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                if (station.subtitle.isNotBlank() && station.isFavorite) {
                  Text(
                    text = station.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = DashTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
            if (isSelected) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = tr(lang, "Vybráno", "Selected"),
                tint = accent,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(tr(lang, "Zavřít", "Close"), color = DashTextSecondary)
      }
    },
  )
}
