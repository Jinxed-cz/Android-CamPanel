package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.DiscoveredNetworkCamera
import com.example.ui.theme.DashTextPrimary
import com.example.ui.theme.DashTextSecondary
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalDashPalette
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.tr

/**
 * Fullscreen network camera discovery dialog for a spacious, easy-to-read overview of detected
 * ONVIF, EseeCloud, XMEye, Dahua, RTSP, and HTTP cameras/NVRs.
 */
@Composable
fun CameraDiscoveryFullscreenDialog(
  discoveredCameras: List<DiscoveredNetworkCamera>,
  isScanning: Boolean,
  statusMessage: String?,
  initialUsername: String,
  initialPassword: String,
  onUserActivity: () -> Unit,
  onScanOnvif: () -> Unit,
  onAssignDiscovered: (DiscoveredNetworkCamera, Int, String, String, Int) -> Unit,
  onAssignAllFourNvr: (DiscoveredNetworkCamera, String, String, Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalDashPalette.current
  val lang = LocalAppLanguage.current
  var onvifUser by remember { mutableStateOf(initialUsername) }
  var onvifPass by remember { mutableStateOf(initialPassword) }
  val selectedChannelByDevice = remember { mutableStateMapOf<String, Int>() }

  val fieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedBorderColor = palette.accentBright,
      unfocusedBorderColor = palette.surfaceBorder,
      focusedTextColor = palette.textPrimary,
      unfocusedTextColor = palette.textPrimary,
      focusedLabelColor = palette.accentBright,
      unfocusedLabelColor = palette.textSecondary,
      cursorColor = palette.accentBright,
    )

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize().padding(10.dp).testTag("camera_discovery_fullscreen_dialog"),
      shape = RoundedCornerShape(14.dp),
      color = palette.surface,
      border = androidx.compose.foundation.BorderStroke(1.dp, palette.surfaceBorder),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Header bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
              text = tr(lang, "Vyhledávání kamer v síti", "Network Camera Discovery"),
              style = MaterialTheme.typography.headlineMedium,
              color = palette.textPrimary,
            )
            Text(
              text = "ONVIF, EseeCloud, XMEye, Dahua, RTSP, HTTP",
              style = MaterialTheme.typography.labelSmall,
              color = palette.textSecondary,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                onUserActivity()
                onScanOnvif()
              },
              enabled = !isScanning,
              colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
              modifier = Modifier.height(48.dp).testTag("fullscreen_scan_button"),
            ) {
              if (isScanning) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  color = Color.White,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
              }
              Spacer(Modifier.width(6.dp))
              Text(
                if (isScanning) tr(lang, "Prohledávám síť...", "Scanning LAN...")
                else tr(lang, "Spustit sken sítě", "Scan Network")
              )
            }

            OutlinedButton(
              onClick = {
                onUserActivity()
                onDismiss()
              },
              modifier = Modifier.height(48.dp).testTag("close_discovery_dialog_button"),
            ) {
              Icon(Icons.Rounded.Close, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text(tr(lang, "Zavřít", "Close"), color = palette.textPrimary)
            }
          }
        }

        // Credentials + status row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = onvifUser,
            onValueChange = {
              onUserActivity()
              onvifUser = it
            },
            label = { Text(tr(lang, "Jméno kamery/NVR", "Camera/NVR Username"), style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.weight(0.9f),
          )
          OutlinedTextField(
            value = onvifPass,
            onValueChange = {
              onUserActivity()
              onvifPass = it
            },
            label = { Text(tr(lang, "Heslo kamery/NVR", "Camera/NVR Password"), style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = fieldColors,
            modifier = Modifier.weight(0.9f),
          )
          if (!statusMessage.isNullOrBlank()) {
            Box(
              modifier =
                Modifier.weight(1.4f)
                  .clip(RoundedCornerShape(8.dp))
                  .background(palette.surfaceElevated)
                  .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
                  .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
              Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.accentBright,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // Results list
        LazyColumn(
          modifier =
            Modifier.fillMaxWidth()
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(palette.surfaceElevated.copy(alpha = 0.55f))
              .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
              .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (discoveredCameras.isEmpty() && !isScanning) {
            item(key = "empty_discovery") {
              Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text =
                    tr(
                      lang,
                      "Zatím nebylo nalezeno žádné zařízení. Klepněte na „Spustit sken sítě“.",
                      "No devices found yet. Tap \"Scan Network\" to start.",
                    ),
                  style = MaterialTheme.typography.bodyLarge,
                  color = DashTextSecondary,
                )
              }
            }
          }

          items(discoveredCameras, key = { it.id }) { cam ->
            val chosenCh = selectedChannelByDevice[cam.id] ?: 0
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(palette.surface)
                  .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
                  .padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Box(
                      modifier =
                        Modifier.clip(RoundedCornerShape(5.dp))
                          .background(palette.accentBright.copy(alpha = 0.22f))
                          .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = if (cam.isNvr) "${cam.discoveryType} NVR" else cam.discoveryType,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accentBright,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                    Text(
                      text = cam.name,
                      style = MaterialTheme.typography.titleMedium,
                      color = DashTextPrimary,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Text(
                    text =
                      "${cam.ipAddress} · RTSP port ${cam.rtspPort}" +
                        if (cam.hardwareInfo.isNotBlank()) " · ${cam.hardwareInfo}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashTextSecondary,
                  )
                  val urlInfo = cam.resolvedStreamUrl ?: cam.candidateStreamUrl
                  if (urlInfo.isNotBlank()) {
                    Text(
                      text = urlInfo,
                      style = MaterialTheme.typography.labelSmall,
                      color = palette.textMuted,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                if (cam.isResolvingUri) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = palette.accentBright,
                    strokeWidth = 2.dp,
                  )
                }
              }

              if (!cam.resolveError.isNullOrBlank()) {
                Text(
                  text = cam.resolveError,
                  style = MaterialTheme.typography.labelSmall,
                  color = OfflineRed,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (cam.isNvr) {
                  Button(
                    onClick = {
                      onUserActivity()
                      onAssignAllFourNvr(cam, onvifUser, onvifPass, 0)
                    },
                    enabled = !cam.isResolvingUri,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    modifier = Modifier.height(48.dp),
                  ) {
                    Text(
                      tr(lang, "Připojit všechny 4 (K1–K4)", "Connect all 4 (CH1–CH4)"),
                      style = MaterialTheme.typography.labelMedium,
                    )
                  }
                  (0..3).forEach { ch ->
                    FilterChip(
                      selected = chosenCh == ch,
                      onClick = {
                        onUserActivity()
                        selectedChannelByDevice[cam.id] = ch
                      },
                      label = { Text("K${ch + 1}", style = MaterialTheme.typography.labelSmall) },
                      colors =
                        FilterChipDefaults.filterChipColors(
                          selectedContainerColor = palette.accent,
                          selectedLabelColor = Color.White,
                          containerColor = palette.surfaceElevated,
                          labelColor = palette.textSecondary,
                        ),
                    )
                  }
                }

                Spacer(Modifier.weight(1f))

                (0..3).forEach { targetIdx ->
                  OutlinedButton(
                    onClick = {
                      onUserActivity()
                      onAssignDiscovered(cam, targetIdx, onvifUser, onvifPass, chosenCh)
                    },
                    modifier = Modifier.height(48.dp),
                  ) {
                    Text(
                      "${tr(lang, "Kamera", "Camera")} ${targetIdx + 1}",
                      style = MaterialTheme.typography.labelMedium,
                      color = palette.textPrimary,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
