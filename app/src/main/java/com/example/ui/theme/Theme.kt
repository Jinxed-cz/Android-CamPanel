package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.model.AppColorSchemePreset
import com.example.model.AppLanguage
import com.example.model.AppearanceSettings
import com.example.model.WeatherColorThemePreset
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class DashPalette(
  val background: Color,
  val surface: Color,
  val surfaceElevated: Color,
  val surfaceBorder: Color,
  val accent: Color,
  val accentBright: Color,
  val accentContainer: Color,
  val textPrimary: Color = DashTextPrimary,
  val textSecondary: Color = DashTextSecondary,
  val textMuted: Color = DashTextMuted,
)

data class WeatherGraphPalette(
  val chartBackground: Color,
  val tempWarm: Color,
  val tempHot: Color,
  val tempColdTop: Color,
  val tempColdBottom: Color,
  val rainBar: Color,
  val snowBar: Color,
  val pressureLine: Color,
)

private val CobaltDashPalette =
  DashPalette(
    background = Color(0xFF070B12),
    surface = Color(0xFF0F172A),
    surfaceElevated = Color(0xFF16213A),
    surfaceBorder = Color(0xFF243454),
    accent = Color(0xFF2563EB),
    accentBright = Color(0xFF3B82F6),
    accentContainer = Color(0xFF1E3A8A),
  )

private val EmeraldDashPalette =
  DashPalette(
    background = Color(0xFF050D0B),
    surface = Color(0xFF0C1D18),
    surfaceElevated = Color(0xFF132D25),
    surfaceBorder = Color(0xFF204A3D),
    accent = Color(0xFF059669),
    accentBright = Color(0xFF10B981),
    accentContainer = Color(0xFF064E3B),
  )

private val AmberDashPalette =
  DashPalette(
    background = Color(0xFF0F0B06),
    surface = Color(0xFF1C160E),
    surfaceElevated = Color(0xFF2B2114),
    surfaceBorder = Color(0xFF473620),
    accent = Color(0xFFD97706),
    accentBright = Color(0xFFF59E0B),
    accentContainer = Color(0xFF78350F),
  )

private val VioletDashPalette =
  DashPalette(
    background = Color(0xFF090712),
    surface = Color(0xFF141026),
    surfaceElevated = Color(0xFF1F183B),
    surfaceBorder = Color(0xFF342961),
    accent = Color(0xFF7C3AED),
    accentBright = Color(0xFF8B5CF6),
    accentContainer = Color(0xFF4C1D95),
  )

private val PureOledDashPalette =
  DashPalette(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0D14),
    surfaceElevated = Color(0xFF141926),
    surfaceBorder = Color(0xFF253047),
    accent = Color(0xFF0284C7),
    accentBright = Color(0xFF38BDF8),
    accentContainer = Color(0xFF0C4A6E),
  )

private val ClassicWeatherPalette =
  WeatherGraphPalette(
    chartBackground = Color(0xFF0A101E),
    tempWarm = TempAmber,
    tempHot = TempWarm,
    tempColdTop = MenuBlueBright,
    tempColdBottom = RainCyan,
    rainBar = RainCyan,
    snowBar = SnowIce,
    pressureLine = PressureViolet,
  )

private val NeonWeatherPalette =
  WeatherGraphPalette(
    chartBackground = Color(0xFF070C17),
    tempWarm = Color(0xFFFACC15),
    tempHot = Color(0xFFFB7185),
    tempColdTop = Color(0xFF22D3EE),
    tempColdBottom = Color(0xFF38BDF8),
    rainBar = Color(0xFF06B6D4),
    snowBar = Color(0xFFF0F9FF),
    pressureLine = Color(0xFFC084FC),
  )

private val IceWeatherPalette =
  WeatherGraphPalette(
    chartBackground = Color(0xFF08101C),
    tempWarm = Color(0xFFBAE6FD),
    tempHot = Color(0xFFFDE68A),
    tempColdTop = Color(0xFF38BDF8),
    tempColdBottom = Color(0xFF2563EB),
    rainBar = Color(0xFF7DD3FC),
    snowBar = Color(0xFFFFFFFF),
    pressureLine = Color(0xFF818CF8),
  )

private val AutumnWeatherPalette =
  WeatherGraphPalette(
    chartBackground = Color(0xFF110D0A),
    tempWarm = Color(0xFFFB923C),
    tempHot = Color(0xFFEF4444),
    tempColdTop = Color(0xFF60A5FA),
    tempColdBottom = Color(0xFF38BDF8),
    rainBar = Color(0xFF2DD4BF),
    snowBar = Color(0xFFE2E8F0),
    pressureLine = Color(0xFFF472B6),
  )

fun resolveDashPalette(preset: AppColorSchemePreset): DashPalette =
  when (preset) {
    AppColorSchemePreset.COBALT_DARK -> CobaltDashPalette
    AppColorSchemePreset.EMERALD_DARK -> EmeraldDashPalette
    AppColorSchemePreset.AMBER_DARK -> AmberDashPalette
    AppColorSchemePreset.VIOLET_DARK -> VioletDashPalette
    AppColorSchemePreset.PURE_OLED -> PureOledDashPalette
  }

fun resolveWeatherGraphPalette(preset: WeatherColorThemePreset): WeatherGraphPalette =
  when (preset) {
    WeatherColorThemePreset.CLASSIC_ALADIN -> ClassicWeatherPalette
    WeatherColorThemePreset.NEON_CONTRAST -> NeonWeatherPalette
    WeatherColorThemePreset.ICE_BLUE -> IceWeatherPalette
    WeatherColorThemePreset.AUTUMN_WARM -> AutumnWeatherPalette
  }

val LocalDashPalette = staticCompositionLocalOf { CobaltDashPalette }
val LocalWeatherGraphPalette = staticCompositionLocalOf { ClassicWeatherPalette }

@Composable
fun CamPanelTheme(
  appearance: AppearanceSettings = AppearanceSettings(),
  language: AppLanguage = AppLanguage.CS,
  content: @Composable () -> Unit,
) {
  val palette = resolveDashPalette(AppColorSchemePreset.fromId(appearance.appColorSchemeId))
  val weatherPalette =
    resolveWeatherGraphPalette(WeatherColorThemePreset.fromId(appearance.weatherColorThemeId))

  val colorScheme =
    darkColorScheme(
      primary = palette.accentBright,
      onPrimary = Color.White,
      primaryContainer = palette.accentContainer,
      onPrimaryContainer = palette.textPrimary,
      secondary = RainCyan,
      onSecondary = palette.background,
      tertiary = weatherPalette.tempWarm,
      onTertiary = palette.background,
      background = palette.background,
      onBackground = palette.textPrimary,
      surface = palette.surface,
      onSurface = palette.textPrimary,
      surfaceVariant = palette.surfaceElevated,
      onSurfaceVariant = palette.textSecondary,
      outline = palette.surfaceBorder,
      error = OfflineRed,
      onError = Color.White,
    )

  CompositionLocalProvider(
    LocalDashPalette provides palette,
    LocalWeatherGraphPalette provides weatherPalette,
    LocalAppLanguage provides language,
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
