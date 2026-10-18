package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
  primary = GenziiPurpleLight,
  onPrimary = GenziiWhite,
  primaryContainer = GenziiPurpleDark,
  onPrimaryContainer = GenziiPurpleSoft,
  secondary = GenziiPurpleLight,
  onSecondary = GenziiWhite,
  secondaryContainer = GenziiDarkSurfaceVariant,
  onSecondaryContainer = GenziiWhite,
  background = GenziiDarkBackground,
  onBackground = GenziiDarkTextPrimary,
  surface = GenziiDarkSurface,
  onSurface = GenziiDarkTextPrimary,
  surfaceVariant = GenziiDarkSurfaceVariant,
  onSurfaceVariant = GenziiDarkTextSecondary,
  outline = GenziiDarkBorder
)

private val LightColorScheme = lightColorScheme(
  primary = GenziiPurple,
  onPrimary = GenziiWhite,
  primaryContainer = GenziiPurpleSoft,
  onPrimaryContainer = GenziiPurpleDark,
  secondary = GenziiPurpleLight,
  onSecondary = GenziiWhite,
  secondaryContainer = GenziiPurpleContainer,
  onSecondaryContainer = GenziiPurpleDark,
  background = GenziiBackgroundLight,
  onBackground = GenziiTextPrimary,
  surface = GenziiSurfaceLight,
  onSurface = GenziiTextPrimary,
  surfaceVariant = GenziiSurfaceVariantLight,
  onSurfaceVariant = GenziiTextSecondary,
  outline = GenziiBorderLight
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        window.statusBarColor = colorScheme.background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = GenziiTypography,
    content = content
  )
}
