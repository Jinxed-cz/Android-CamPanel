package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.model.AppLanguage
import com.example.model.CameraSlot
import com.example.model.RecordedSegmentInfo
import com.example.model.RecordingMode
import com.example.model.RecordingQuality
import com.example.model.RecordingRetentionPolicy
import com.example.model.RecordingStorageLocation
import com.example.model.RecordingStorageSettings
import com.example.model.RecordingStorageStats
import com.example.ui.theme.DashSurface
import com.example.ui.theme.DashSurfaceBorder
import com.example.ui.theme.DashSurfaceElevated
import com.example.ui.theme.DashTextMuted
import com.example.ui.theme.DashTextPrimary
import com.example.ui.theme.DashTextSecondary
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalDashPalette
import com.example.ui.theme.MenuBlue
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.TempAmber
import com.example.ui.theme.tr
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecordingArchiveDialog(
  cameras: List<CameraSlot>,
  storageSettings: RecordingStorageSettings,
  storageStats: RecordingStorageStats,
  recordedSegments: List<RecordedSegmentInfo>,
  onUserActivity: () -> Unit,
  onSelectStorageLocation: (RecordingStorageLocation) -> Unit,
  onSelectCustomSafFolder: (Uri, String) -> Unit = { _, _ -> },
  onSelectFolderForLocation: (RecordingStorageLocation, Uri, String) -> Unit = { _, uri, label ->
    onSelectCustomSafFolder(uri, label)
  },
  onRefreshRecordings: () -> Unit,
  onDeleteSegment: (RecordedSegmentInfo) -> Unit,
  onClearCameraRecordings: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val lang = LocalAppLanguage.current
  val palette = LocalDashPalette.current
  var filterSlotIndex by remember { mutableIntStateOf(-1) } // -1 = all
  var activePlaybackSegment by remember { mutableStateOf<RecordedSegmentInfo?>(null) }
  var pendingStorageLocation by remember { mutableStateOf<RecordingStorageLocation?>(null) }

  val folderPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      onUserActivity()
      val targetLoc = pendingStorageLocation ?: RecordingStorageLocation.APP_EXTERNAL_PRIMARY
      pendingStorageLocation = null
      if (uri != null) {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
        val rawPath = docId.substringAfter(':', docId).trim('/')
        val label =
          rawPath.ifBlank {
            uri.lastPathSegment?.substringAfter(':', uri.lastPathSegment.orEmpty()).orEmpty()
          }.ifBlank { tr(lang, "Vybraná složka", "Selected folder") }
        onSelectFolderForLocation(targetLoc, uri, label)
      }
    }

  val filteredSegments =
    remember(recordedSegments, filterSlotIndex) {
      if (filterSlotIndex < 0) recordedSegments
      else recordedSegments.filter { it.slotIndex == filterSlotIndex }
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.95f)
          .widthIn(max = 940.dp)
          .fillMaxHeight(0.92f)
          .heightIn(max = 600.dp)
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                onUserActivity()
              }
            }
          }
          .testTag("recording_archive_dialog"),
      shape = RoundedCornerShape(16.dp),
      color = DashSurface,
      border = BorderStroke(1.dp, DashSurfaceBorder),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = palette.accentBright)
            Column {
              Text(
                text = tr(lang, "Místo ukládání záznamů", "Recording Storage Location"),
                style = MaterialTheme.typography.titleMedium,
                color = DashTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  "${tr(lang, "Využito", "Used")}: ${RecordingRetentionPolicy.formatBytes(storageStats.usedByAppBytes)} · " +
                    "${tr(lang, "Volné místo", "Free space")}: ${RecordingRetentionPolicy.formatBytes(storageStats.availableFreeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = DashTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                onUserActivity()
                onRefreshRecordings()
              },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                Icons.Rounded.Refresh,
                contentDescription = tr(lang, "Obnovit seznam záznamů", "Refresh recordings"),
                tint = palette.accentBright,
              )
            }
            Button(
              onClick = onDismiss,
              colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
              contentPadding = PaddingValues(horizontal = 14.dp),
              modifier = Modifier.height(48.dp).testTag("close_recording_archive_button"),
            ) {
              Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(tr(lang, "Zavřít", "Close"), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
          }
        }

        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Left Column: Compact Non-Scrolling 2-Option Storage Selector + Active Path
          Column(
            modifier =
              Modifier.weight(0.85f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(DashSurfaceElevated.copy(alpha = 0.6f))
                .border(1.dp, DashSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(Icons.Rounded.SdStorage, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(18.dp))
              Text(
                text = tr(lang, "Kam ukládat záznamy", "Recording storage path"),
                style = MaterialTheme.typography.labelLarge,
                color = DashTextPrimary,
                fontWeight = FontWeight.SemiBold,
              )
            }

            val options =
              listOf(
                RecordingStorageLocation.APP_EXTERNAL_PRIMARY,
                RecordingStorageLocation.SD_CARD_APP,
              )

            options.forEach { loc ->
              val isSelected = storageSettings.isFolderConfigured && storageSettings.locationType == loc
              val isSdOption = loc == RecordingStorageLocation.SD_CARD_APP
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) palette.accent.copy(alpha = 0.28f) else DashSurface)
                    .border(
                      1.dp,
                      if (isSelected) palette.accentBright else DashSurfaceBorder,
                      RoundedCornerShape(8.dp),
                    )
                    .clickable {
                      onUserActivity()
                      pendingStorageLocation = loc
                      folderPickerLauncher.launch(null)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("storage_option_${loc.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = loc.labelFor(lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashTextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text =
                      when {
                        isSelected && storageStats.resolvedPathLabel.isNotBlank() ->
                          storageStats.resolvedPathLabel
                        isSdOption && !storageStats.hasSdCard ->
                          tr(lang, "Klepnutím vyberte složku (SD nenalezena)", "Tap to pick folder (SD not detected)")
                        else ->
                          tr(lang, "Klepnutím vyberte složku", "Tap to choose folder")
                      },
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      when {
                        isSelected -> LiveEmerald
                        isSdOption && !storageStats.hasSdCard -> DashTextMuted
                        else -> DashTextSecondary
                      },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = tr(lang, "Vybráno", "Selected"),
                    tint = palette.accentBright,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Icon(
                  imageVector = Icons.Rounded.FolderOpen,
                  contentDescription = tr(lang, "Vybrat složku", "Choose folder"),
                  tint = palette.accentBright,
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(DashSurface)
                  .border(
                    1.dp,
                    if (storageSettings.isFolderConfigured) DashSurfaceBorder else TempAmber.copy(alpha = 0.55f),
                    RoundedCornerShape(8.dp),
                  )
                  .padding(8.dp),
            ) {
              Text(
                text =
                  if (storageSettings.isFolderConfigured) {
                    "${tr(lang, "Aktivní složka", "Active folder")}: ${storageStats.resolvedPathLabel}"
                  } else {
                    tr(
                      lang,
                      "Složka není vybrána – klepněte na Místní nebo Externí úložiště a zvolte cílovou složku.",
                      "No folder selected – tap Local or External storage to choose a destination folder.",
                    )
                  },
                style = MaterialTheme.typography.labelSmall,
                color = if (storageSettings.isFolderConfigured) DashTextSecondary else TempAmber,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          // Right Column: Filter + List of Recorded MP4 Segments
          Column(
            modifier =
              Modifier.weight(1.15f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(DashSurfaceElevated.copy(alpha = 0.6f))
                .border(1.dp, DashSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                FilterChip(
                  selected = filterSlotIndex == -1,
                  onClick = {
                    onUserActivity()
                    filterSlotIndex = -1
                  },
                  label = { Text("${tr(lang, "Vše", "All")} (${recordedSegments.size})", style = MaterialTheme.typography.labelSmall) },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = palette.accent,
                      selectedLabelColor = Color.White,
                    ),
                )
                (0..3).forEach { idx ->
                  val count = recordedSegments.count { it.slotIndex == idx }
                  FilterChip(
                    selected = filterSlotIndex == idx,
                    onClick = {
                      onUserActivity()
                      filterSlotIndex = idx
                    },
                    label = { Text("K${idx + 1} ($count)", style = MaterialTheme.typography.labelSmall) },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accent,
                        selectedLabelColor = Color.White,
                      ),
                  )
                }
              }

              if (filterSlotIndex in 0..3 && filteredSegments.any { !it.isCurrentlyRecording }) {
                OutlinedButton(
                  onClick = {
                    onUserActivity()
                    onClearCameraRecordings(filterSlotIndex)
                  },
                  contentPadding = PaddingValues(horizontal = 8.dp),
                  modifier = Modifier.height(40.dp).testTag("clear_camera_recordings_button"),
                ) {
                  Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = OfflineRed, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text(tr(lang, "Smazat vše", "Clear"), style = MaterialTheme.typography.labelSmall, color = OfflineRed)
                }
              }
            }

            if (filteredSegments.isEmpty()) {
              Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = DashTextMuted,
                    modifier = Modifier.size(34.dp),
                  )
                  Spacer(Modifier.height(6.dp))
                  Text(
                    text = tr(lang, "Zatím nejsou uloženy žádné záznamy", "No recorded video segments yet"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashTextSecondary,
                  )
                  Text(
                    text =
                      tr(
                        lang,
                        "Zapněte nahrávání v úpravě konkrétní kamery",
                        "Enable recording inside an individual camera's settings",
                      ),
                    style = MaterialTheme.typography.labelSmall,
                    color = DashTextMuted,
                  )
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                items(filteredSegments, key = { it.id }) { segment ->
                  val camName =
                    cameras.getOrNull(segment.slotIndex)?.name?.ifBlank { "K${segment.slotIndex + 1}" }
                      ?: "K${segment.slotIndex + 1}"
                  val isMotion = RecordingRetentionPolicy.isMotionEventFileName(segment.fileName)
                  val dateText = formatSegmentTimestamp(segment.createdAtMs)
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DashSurface)
                        .border(
                          1.dp,
                          if (segment.isCurrentlyRecording) OfflineRed.copy(alpha = 0.65f) else DashSurfaceBorder,
                          RoundedCornerShape(8.dp),
                        )
                        .clickable(enabled = !segment.isCurrentlyRecording) {
                          onUserActivity()
                          activePlaybackSegment = segment
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Box(
                      modifier =
                        Modifier.clip(RoundedCornerShape(6.dp))
                          .background(if (isMotion) TempAmber.copy(alpha = 0.2f) else palette.accent.copy(alpha = 0.22f))
                          .padding(horizontal = 7.dp, vertical = 4.dp),
                    ) {
                      Text(
                        text = "K${segment.slotIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isMotion) TempAmber else palette.accentBright,
                        fontWeight = FontWeight.Bold,
                      )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        Text(
                          text = "$camName · $dateText",
                          style = MaterialTheme.typography.bodyMedium,
                          color = DashTextPrimary,
                          fontWeight = FontWeight.SemiBold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.weight(1f, fill = false),
                        )
                        if (segment.isCurrentlyRecording) {
                          Text(
                            text = "LIVE REC",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = OfflineRed,
                            fontWeight = FontWeight.Bold,
                          )
                        }
                      }
                      Text(
                        text =
                          buildString {
                            append(segment.qualityId.uppercase())
                            append(" · ")
                            append(
                              if (isMotion) tr(lang, "Pohyb", "Motion")
                              else tr(lang, "Kontinuální 15m", "Continuous 15m")
                            )
                            append(" · ")
                            append(RecordingRetentionPolicy.formatBytes(segment.sizeBytes))
                          },
                        style = MaterialTheme.typography.labelSmall,
                        color = DashTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }

                    IconButton(
                      onClick = {
                        onUserActivity()
                        activePlaybackSegment = segment
                      },
                      enabled = !segment.isCurrentlyRecording,
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = tr(lang, "Přehrát záznam", "Play recording"),
                        tint = if (segment.isCurrentlyRecording) DashTextMuted else LiveEmerald,
                      )
                    }

                    IconButton(
                      onClick = {
                        onUserActivity()
                        onDeleteSegment(segment)
                      },
                      enabled = !segment.isCurrentlyRecording,
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = tr(lang, "Smazat záznam", "Delete recording"),
                        tint = if (segment.isCurrentlyRecording) DashTextMuted else OfflineRed,
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

  activePlaybackSegment?.let { segment ->
    RecordedVideoPlayerDialog(
      segment = segment,
      onUserActivity = onUserActivity,
      onDismiss = { activePlaybackSegment = null },
    )
  }
}

@Composable
private fun RecordedVideoPlayerDialog(
  segment: RecordedSegmentInfo,
  onUserActivity: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val lang = LocalAppLanguage.current
  val palette = LocalDashPalette.current

  val exoPlayer =
    remember(segment.uriString) {
      ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(Uri.parse(segment.uriString)))
        playWhenReady = true
        prepare()
      }
    }

  DisposableEffect(exoPlayer) {
    onDispose {
      exoPlayer.release()
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.92f)
          .widthIn(max = 920.dp)
          .fillMaxHeight(0.88f)
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                onUserActivity()
              }
            }
          },
      shape = RoundedCornerShape(14.dp),
      color = Color.Black,
      border = BorderStroke(1.dp, palette.surfaceBorder),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(DashSurface)
              .padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = segment.fileName,
              style = MaterialTheme.typography.titleSmall,
              color = DashTextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${formatSegmentTimestamp(segment.createdAtMs)} · ${RecordingRetentionPolicy.formatBytes(segment.sizeBytes)}",
              style = MaterialTheme.typography.labelSmall,
              color = DashTextSecondary,
            )
          }
          TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = DashTextPrimary)
            Spacer(Modifier.width(4.dp))
            Text(tr(lang, "Zavřít", "Close"), color = DashTextPrimary)
          }
        }

        AndroidView(
          factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.exo_texture_player_view, null, false) as PlayerView).apply {
              useController = true
              layoutParams =
                ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT,
                )
              resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
              player = exoPlayer
            }
          },
          update = { view ->
            if (view.player !== exoPlayer) view.player = exoPlayer
          },
          modifier = Modifier.fillMaxWidth().weight(1f),
        )
      }
    }
  }
}

private fun formatSegmentTimestamp(epochMs: Long): String {
  if (epochMs <= 0L) return ""
  return runCatching {
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
    ldt.format(DateTimeFormatter.ofPattern("d. M. HH:mm:ss"))
  }.getOrDefault("")
}

@Composable
fun CameraRecordingSubmenuDialog(
  slotIndex: Int,
  cameraName: String,
  initialEnabled: Boolean,
  initialMode: RecordingMode,
  initialQuality: RecordingQuality,
  initialMaxSizeMb: Int,
  initialRetentionDays: Int,
  initialMotionSensitivity: Int,
  initialMotionDurationMinutes: Int,
  onUserActivity: () -> Unit,
  onConfirm: (Boolean, RecordingMode, RecordingQuality, Int, Int, Int, Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val lang = LocalAppLanguage.current
  val palette = LocalDashPalette.current
  val context = LocalContext.current

  val notificationPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      onUserActivity()
    }

  var enabled by remember { mutableStateOf(initialEnabled) }
  var mode by remember { mutableStateOf(initialMode) }
  var quality by remember { mutableStateOf(initialQuality) }
  var maxSizeMb by remember { mutableIntStateOf(initialMaxSizeMb) }
  var retentionDays by remember { mutableIntStateOf(initialRetentionDays) }
  var motionSensitivity by remember {
    mutableIntStateOf(RecordingRetentionPolicy.normalizeMotionSensitivity(initialMotionSensitivity))
  }
  var motionDurationMinutes by remember {
    mutableIntStateOf(RecordingRetentionPolicy.normalizeMotionDurationMinutes(initialMotionDurationMinutes))
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.92f)
          .widthIn(max = 880.dp)
          .fillMaxHeight(0.88f)
          .heightIn(max = 560.dp)
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                onUserActivity()
              }
            }
          }
          .testTag("camera_recording_submenu_dialog"),
      shape = RoundedCornerShape(16.dp),
      color = DashSurface,
      border = BorderStroke(1.dp, DashSurfaceBorder),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Submenu Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.FiberManualRecord,
              contentDescription = null,
              tint = if (enabled) OfflineRed else DashTextMuted,
              modifier = Modifier.size(18.dp),
            )
            Column {
              Text(
                text =
                  "${tr(lang, "Nahrávání", "Recording")} · " +
                    cameraName.ifBlank { "${tr(lang, "Kamera", "Camera")} ${slotIndex + 1}" },
                style = MaterialTheme.typography.titleMedium,
                color = DashTextPrimary,
              )
              Text(
                text =
                  tr(
                    lang,
                    "Aktivace, režim záznamu (kontinuální / pohyb), kvalita, velikost a doba uchování",
                    "Activation, mode (continuous / motion), quality, max size & retention days",
                  ),
                style = MaterialTheme.typography.labelSmall,
                color = DashTextSecondary,
              )
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = tr(lang, "Zavřít", "Close"), tint = DashTextSecondary)
          }
        }

        // Two-column spacious configuration body
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Left Column: Activation + Recording Mode + Motion Detection
          Column(
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(DashSurfaceElevated.copy(alpha = 0.6f))
                .border(1.dp, DashSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(DashSurface)
                  .border(
                    1.dp,
                    if (enabled) OfflineRed.copy(alpha = 0.65f) else DashSurfaceBorder,
                    RoundedCornerShape(8.dp),
                  )
                  .clickable {
                    onUserActivity()
                    val next = !enabled
                    enabled = next
                    if (next && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                      val granted =
                        ContextCompat.checkSelfPermission(
                          context,
                          Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                      if (!granted) {
                        notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                      }
                    }
                  }
                  .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                  text = tr(lang, "Aktivovat nahrávání (MP4)", "Enable recording (MP4)"),
                  style = MaterialTheme.typography.labelLarge,
                  color = DashTextPrimary,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text =
                    if (enabled) {
                      tr(lang, "Nahrávání je zapnuté", "Recording is active")
                    } else {
                      tr(lang, "Nahrávání je vypnuté", "Recording is turned off")
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color = if (enabled) LiveEmerald else DashTextSecondary,
                )
              }
              Switch(
                checked = enabled,
                onCheckedChange = { next ->
                  onUserActivity()
                  enabled = next
                  if (next && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted =
                      ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                      ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                      notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                  }
                },
                modifier = Modifier.testTag("camera_recording_enabled_switch"),
                colors =
                  SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = OfflineRed,
                  ),
              )
            }

            Text(
              text = tr(lang, "Režim nahrávání", "Recording mode"),
              style = MaterialTheme.typography.labelMedium,
              color = DashTextPrimary,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              RecordingMode.entries.forEach { modeOption ->
                val selected = mode == modeOption
                FilterChip(
                  selected = selected,
                  onClick = {
                    onUserActivity()
                    mode = modeOption
                  },
                  label = {
                    Text(
                      text = modeOption.labelFor(lang),
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = if (modeOption == RecordingMode.MOTION_EVENTS) TempAmber else palette.accent,
                      selectedLabelColor = if (modeOption == RecordingMode.MOTION_EVENTS) Color.Black else Color.White,
                    ),
                  modifier = Modifier.weight(1f).height(48.dp).testTag("recording_mode_${modeOption.id}"),
                )
              }
            }

            if (mode == RecordingMode.MOTION_EVENTS) {
              HorizontalDivider(color = DashSurfaceBorder)

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = tr(lang, "Citlivost pohybu", "Motion sensitivity"),
                  style = MaterialTheme.typography.labelMedium,
                  color = DashTextPrimary,
                )
                Text(
                  text = "$motionSensitivity%",
                  style = MaterialTheme.typography.titleSmall,
                  color = TempAmber,
                  fontWeight = FontWeight.Bold,
                )
              }
              Slider(
                value = motionSensitivity.toFloat(),
                onValueChange = {
                  onUserActivity()
                  motionSensitivity = RecordingRetentionPolicy.normalizeMotionSensitivity(it.toInt())
                },
                valueRange = 10f..95f,
                colors =
                  SliderDefaults.colors(
                    thumbColor = TempAmber,
                    activeTrackColor = TempAmber,
                    inactiveTrackColor = DashSurfaceBorder,
                  ),
                modifier = Modifier.fillMaxWidth().testTag("motion_sensitivity_slider"),
              )
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                RecordingRetentionPolicy.MOTION_SENSITIVITY_PRESETS.forEach { presetSens ->
                  FilterChip(
                    selected = kotlin.math.abs(motionSensitivity - presetSens) <= 5,
                    onClick = {
                      onUserActivity()
                      motionSensitivity = presetSens
                    },
                    label = { Text("$presetSens%", style = MaterialTheme.typography.labelSmall) },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TempAmber,
                        selectedLabelColor = Color.Black,
                      ),
                  )
                }
              }

              Text(
                text = tr(lang, "Délka záznamu při události", "Clip length on motion event"),
                style = MaterialTheme.typography.labelMedium,
                color = DashTextPrimary,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                RecordingRetentionPolicy.MOTION_DURATION_MINUTES_OPTIONS.forEach { mins ->
                  val selected = motionDurationMinutes == mins
                  FilterChip(
                    selected = selected,
                    onClick = {
                      onUserActivity()
                      motionDurationMinutes = mins
                    },
                    label = {
                      Text(
                        text = "$mins min",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                      )
                    },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TempAmber,
                        selectedLabelColor = Color.Black,
                      ),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("motion_duration_${mins}m"),
                  )
                }
              }
            } else {
              Box(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DashSurface)
                    .border(1.dp, DashSurfaceBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
              ) {
                Text(
                  text =
                    tr(
                      lang,
                      "Kontinuální režim ukládá video nepřetržitě v 15minutových MP4 blocích a automaticky udržuje velikost i počet dní.",
                      "Continuous mode records nonstop in 15-minute MP4 segments and automatically enforces size and day limits.",
                    ),
                  style = MaterialTheme.typography.bodySmall,
                  color = DashTextSecondary,
                )
              }
            }
          }

          // Right Column: Quality (SD/HD), Max Storage Size, Retention Days
          Column(
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(DashSurfaceElevated.copy(alpha = 0.6f))
                .border(1.dp, DashSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = tr(lang, "Kvalita záznamu", "Recording quality"),
              style = MaterialTheme.typography.labelMedium,
              color = DashTextPrimary,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              RecordingQuality.entries.forEach { q ->
                FilterChip(
                  selected = quality == q,
                  onClick = {
                    onUserActivity()
                    quality = q
                  },
                  label = {
                    Text(
                      text = "${q.labelFor(lang)} · ~${q.estimatedMbPer15Min} MB/15m",
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = palette.accent,
                      selectedLabelColor = Color.White,
                    ),
                  modifier = Modifier.weight(1f).height(48.dp).testTag("recording_quality_${q.id}"),
                )
              }
            }

            Text(
              text = tr(lang, "Max. velikost pro tuto kameru (přepis nejstarších)", "Max storage for this camera (oldest overwrite)"),
              style = MaterialTheme.typography.labelMedium,
              color = DashTextPrimary,
            )
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              RecordingRetentionPolicy.MAX_SIZE_MB_OPTIONS.forEach { sizeMb ->
                FilterChip(
                  selected = maxSizeMb == sizeMb,
                  onClick = {
                    onUserActivity()
                    maxSizeMb = sizeMb
                  },
                  label = {
                    Text(
                      RecordingRetentionPolicy.formatSizeLabel(sizeMb),
                      style = MaterialTheme.typography.labelSmall,
                    )
                  },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = palette.accent,
                      selectedLabelColor = Color.White,
                    ),
                  modifier = Modifier.height(48.dp),
                )
              }
            }

            Text(
              text = tr(lang, "Doba uchování záznamů (dny)", "Retention duration (days)"),
              style = MaterialTheme.typography.labelMedium,
              color = DashTextPrimary,
            )
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              RecordingRetentionPolicy.RETENTION_DAY_OPTIONS.forEach { days ->
                val dayLabel =
                  if (lang == AppLanguage.EN) {
                    if (days == 1) "1 day" else "$days days"
                  } else {
                    when (days) {
                      1 -> "1 den"
                      2 -> "2 dny"
                      else -> "$days dní"
                    }
                  }
                FilterChip(
                  selected = retentionDays == days,
                  onClick = {
                    onUserActivity()
                    retentionDays = days
                  },
                  label = { Text(dayLabel, style = MaterialTheme.typography.labelSmall) },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = palette.accent,
                      selectedLabelColor = Color.White,
                    ),
                  modifier = Modifier.height(48.dp).testTag("recording_days_$days"),
                )
              }
            }
          }
        }

        HorizontalDivider(color = DashSurfaceBorder)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
            Text(tr(lang, "Zrušit", "Cancel"), color = DashTextSecondary)
          }
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = {
              onUserActivity()
              onConfirm(
                enabled,
                mode,
                quality,
                maxSizeMb,
                retentionDays,
                motionSensitivity,
                motionDurationMinutes,
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = MenuBlue),
            modifier = Modifier.height(48.dp).testTag("camera_recording_submenu_confirm"),
          ) {
            Text(tr(lang, "Hotovo", "Done"), color = Color.White)
          }
        }
      }
    }
  }
}
