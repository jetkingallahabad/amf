package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
  primary = EmeraldPrimaryDark,
  onPrimary = EmeraldOnPrimaryDark,
  primaryContainer = EmeraldContainerDark,
  onPrimaryContainer = OnEmeraldContainerDark,
  secondary = GoldSecondaryDark,
  onSecondary = OnGoldSecondaryDark,
  secondaryContainer = GoldContainerDark,
  onSecondaryContainer = OnGoldContainerDark,
  tertiary = SlateTertiaryDark,
  onTertiary = OnSlateTertiaryDark,
  tertiaryContainer = SlateContainerDark,
  onTertiaryContainer = OnSlateContainerDark,
  background = BackgroundDark,
  onBackground = OnSurfaceDark,
  surface = SurfaceDark,
  onSurface = OnSurfaceDark,
  surfaceVariant = SurfaceVariantDark,
  onSurfaceVariant = OnSurfaceVariantDark,
  outline = OutlineDark,
  error = Color(0xFFFCA5A5),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF7F1D1D),
  onErrorContainer = Color(0xFFFEE2E2),
)

private val LightColorScheme = lightColorScheme(
  primary = EmeraldPrimaryLight,
  onPrimary = EmeraldOnPrimaryLight,
  primaryContainer = EmeraldContainerLight,
  onPrimaryContainer = OnEmeraldContainerLight,
  secondary = GoldSecondaryLight,
  onSecondary = OnGoldSecondaryLight,
  secondaryContainer = GoldContainerLight,
  onSecondaryContainer = OnGoldContainerLight,
  tertiary = SlateTertiaryLight,
  onTertiary = OnSlateTertiaryLight,
  tertiaryContainer = SlateContainerLight,
  onTertiaryContainer = OnSlateContainerLight,
  background = BackgroundLight,
  onBackground = OnSurfaceLight,
  surface = SurfaceLight,
  onSurface = OnSurfaceLight,
  surfaceVariant = SurfaceVariantLight,
  onSurfaceVariant = OnSurfaceVariantLight,
  outline = OutlineLight,
  error = OverdueCrimson,
  onError = Color.White,
  errorContainer = OverdueCrimsonContainer,
  onErrorContainer = Color(0xFF410002),
)

val FinanceShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(18.dp),
  extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun LoanFinanceTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = FinanceShapes,
    content = content,
  )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  LoanFinanceTheme(darkTheme = darkTheme, content = content)
}
