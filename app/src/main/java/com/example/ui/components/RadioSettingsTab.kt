package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FavoriteRadioStation
import com.example.model.RadioDirectoryStation
import com.example.model.RadioStation
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalDashPalette
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.TempAmber
import com.example.ui.theme.tr

@Composable
fun RadioSettingsTab(
  searchResults: List<RadioDirectoryStation>,
  isSearching: Boolean,
  searchError: String?,
  favoriteRadios: List<FavoriteRadioStation>,
  onUserActivity: () -> Unit,
  onSearchRadios: (String) -> Unit,
  onLoadCzechRadios: () -> Unit,
  onToggleFavorite: (RadioDirectoryStation) -> Unit,
  onRemoveFavorite: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalDashPalette.current
  val lang = LocalAppLanguage.current
  var query by remember { mutableStateOf("") }

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

  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    // Left column: Search internet radios (Radio Browser API)
    Column(
      modifier =
        Modifier.weight(1.15f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(10.dp))
          .background(palette.surfaceElevated.copy(alpha = 0.6f))
          .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
          .padding(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Radio,
            contentDescription = null,
            tint = palette.accentBright,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = tr(lang, "Vyhledat internetová rádia", "Search Internet Radios"),
            style = MaterialTheme.typography.titleMedium,
            color = palette.textPrimary,
          )
        }
        OutlinedButton(
          onClick = {
            onUserActivity()
            onLoadCzechRadios()
          },
          enabled = !isSearching,
          modifier = Modifier.height(48.dp).testTag("load_czech_radios_button"),
        ) {
          Icon(Icons.Rounded.Public, contentDescription = null, tint = palette.accentBright, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            tr(lang, "Česká rádia", "Czech Stations"),
            style = MaterialTheme.typography.labelMedium,
            color = palette.textPrimary,
          )
        }
      }

      Spacer(Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = query,
          onValueChange = {
            onUserActivity()
            query = it
          },
          placeholder = {
            Text(
              tr(lang, "Např. Impuls, Radiožurnál, Beat...", "e.g. BBC, Jazz, Beat, Impuls..."),
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          singleLine = true,
          colors = fieldColors,
          modifier = Modifier.weight(1f).testTag("radio_search_input"),
        )
        Button(
          onClick = {
            onUserActivity()
            onSearchRadios(query)
          },
          enabled = !isSearching,
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
          modifier = Modifier.height(52.dp).testTag("radio_search_button"),
        ) {
          if (isSearching) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
          } else {
            Icon(Icons.Rounded.Search, contentDescription = tr(lang, "Hledat rádio", "Search radio"))
            Spacer(Modifier.width(4.dp))
            Text(tr(lang, "Hledat", "Search"))
          }
        }
      }

      Spacer(Modifier.height(6.dp))

      if (!searchError.isNullOrBlank()) {
        Text(
          text = searchError,
          style = MaterialTheme.typography.bodyMedium,
          color = OfflineRed,
          modifier = Modifier.padding(vertical = 4.dp),
        )
      }

      LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (searchResults.isEmpty() && !isSearching && searchError == null) {
          item(key = "radio_search_hint") {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(palette.surface.copy(alpha = 0.7f))
                  .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
                  .padding(10.dp)
            ) {
              Text(
                text =
                  tr(
                    lang,
                    "Zadejte název stanice nebo klepněte na „Česká rádia“. Vybrané stanice pak přidejte hvězdičkou do oblíbených.",
                    "Enter any global station name or genre, then star stations to add them to favorites on the home screen.",
                  ),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
              )
            }
          }
        }

        items(searchResults, key = { it.uuid }) { station ->
          val isFav =
            favoriteRadios.any {
              it.id == "fav_${station.uuid}" ||
                it.streamUrl.equals(station.streamUrl, ignoreCase = true)
            }
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFav) palette.accent.copy(alpha = 0.18f) else palette.surface)
                .border(
                  1.dp,
                  if (isFav) palette.accentBright else palette.surfaceBorder,
                  RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text(
                text = station.name,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              val meta =
                listOfNotNull(
                  station.countryCode.takeIf { it.isNotBlank() },
                  station.codec.takeIf { it.isNotBlank() },
                  station.bitrateKbps.takeIf { it > 0 }?.let { "${it} kbps" },
                  station.tags.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
              Text(
                text = meta.ifBlank { station.streamUrl },
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            OutlinedButton(
              onClick = { onToggleFavorite(station) },
              modifier = Modifier.height(48.dp),
            ) {
              Icon(
                imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription =
                  if (isFav) tr(lang, "Odebrat z oblíbených", "Remove favorite")
                  else tr(lang, "Přidat do oblíbených", "Add favorite"),
                tint = if (isFav) TempAmber else palette.accentBright,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = if (isFav) tr(lang, "Oblíbené", "Saved") else tr(lang, "Přidat", "Add"),
                style = MaterialTheme.typography.labelMedium,
                color = if (isFav) TempAmber else palette.textPrimary,
              )
            }
          }
        }
      }
    }

    // Right column: Saved Favorite Radios + Built-in presets info
    Column(
      modifier =
        Modifier.weight(0.85f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(10.dp))
          .background(palette.surfaceElevated.copy(alpha = 0.6f))
          .border(1.dp, palette.surfaceBorder, RoundedCornerShape(10.dp))
          .padding(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "${tr(lang, "Oblíbená rádia", "Favorite Radios")} (${favoriteRadios.size})",
          style = MaterialTheme.typography.titleMedium,
          color = palette.textPrimary,
        )
        Text(
          text = "+ ${RadioStation.entries.size} ${tr(lang, "předvolby", "presets")}",
          style = MaterialTheme.typography.labelSmall,
          color = LiveEmerald,
        )
      }

      Spacer(Modifier.height(6.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (favoriteRadios.isEmpty()) {
          item(key = "no_favs") {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(palette.surface)
                  .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
                  .padding(10.dp)
            ) {
              Text(
                text =
                  tr(
                    lang,
                    "Zatím nemáte žádná vlastní oblíbená rádia. Vyhledejte je vlevo a přidejte hvězdičkou – automaticky se zobrazí i na úvodní obrazovce.",
                    "No custom favorites yet. Search on the left and star stations to show them on the home screen.",
                  ),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
              )
            }
          }
        }

        items(favoriteRadios, key = { it.id }) { fav ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surface)
                .border(1.dp, palette.surfaceBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
              Text(
                text = fav.name,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              val sub =
                listOfNotNull(
                  fav.countryCode.takeIf { it.isNotBlank() },
                  fav.codec.takeIf { it.isNotBlank() },
                  fav.bitrateKbps.takeIf { it > 0 }?.let { "${it} kbps" },
                ).joinToString(" · ")
              Text(
                text = sub.ifBlank { fav.streamUrl },
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            IconButton(
              onClick = { onRemoveFavorite(fav.id) },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = tr(lang, "Odstranit z oblíbených", "Remove from favorites"),
                tint = OfflineRed,
              )
            }
          }
        }
      }
    }
  }
}
