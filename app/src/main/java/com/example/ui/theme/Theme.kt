package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = EmberCopperBright,
    onPrimary = Color(0xFF1B0D03),
    primaryContainer = EmberCopperContainer,
    onPrimaryContainer = Color(0xFFFFD8C2),
    secondary = Color(0xFF5FA8E8),
    onSecondary = Color(0xFF061E33),
    secondaryContainer = OutgoingBubbleDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = OnlineEmerald,
    background = ObsidianCanvas,
    onBackground = TextPrimaryDark,
    surface = GraphiteSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedSteel,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateBorder,
    error = ErrorCrimson,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CopperAccentLight,
    onPrimary = Color.White,
    primaryContainer = CopperContainerLight,
    onPrimaryContainer = Color(0xFF3E1803),
    secondary = RustCobalt,
    onSecondary = Color.White,
    secondaryContainer = OutgoingBubbleLight,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = OnlineEmerald,
    background = AlabasterCanvas,
    onBackground = TextPrimaryLight,
    surface = PureWhiteSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SoftSteelSurface,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder,
    error = ErrorCrimson,
  )

@Composable
fun RustgramTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
