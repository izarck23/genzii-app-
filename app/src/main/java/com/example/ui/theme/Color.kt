package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Genzii App-Store Luxury Palette
val GenziiPurple = Color(0xFF6366F1) // Electric Indigo (Primary Brand)
val GenziiPurpleLight = Color(0xFF818CF8) // Luminous Periwinkle
val GenziiPurpleDark = Color(0xFF4F46E5) // Royal Violet (Deep Accent)
val GenziiPurpleSoft = Color(0xFFEEF2FF) // Periwinkle Tint
val GenziiPurpleContainer = Color(0xFFE0E7FF) // Soft Indigo Container
val GenziiPurpleGlow = Color(0xFFA5B4FC) // Indigo Glow
val GenziiCyan = Color(0xFF06B6D4) // Luminous Cyan

// Brand Gradients
val GenziiLuxuryGradient = Brush.linearGradient(
  listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
)
val GenziiHeroGradient = Brush.linearGradient(
  listOf(Color(0xFF6366F1), Color(0xFF4F46E5), Color(0xFF3730A3))
)

// Light Theme Neutrals
val GenziiWhite = Color(0xFFFFFFFF)
val GenziiBackgroundLight = Color(0xFFF9FAFB)
val GenziiSurfaceLight = Color(0xFFFFFFFF)
val GenziiSurfaceVariantLight = Color(0xFFF3F4F6)
val GenziiBorderLight = Color(0xFFE5E7EB)

// Text Colors
val GenziiTextPrimary = Color(0xFF111827)
val GenziiTextSecondary = Color(0xFF4B5563)
val GenziiTextTertiary = Color(0xFF9CA3AF)

// Semantic Refined Badging Colors
val GenziiSuccess = Color(0xFF059669) // Emerald Slate Text
val GenziiSuccessContainer = Color(0xFFECFDF5) // Emerald Container
val GenziiWarning = Color(0xFFD97706) // Amber Glow Text
val GenziiWarningContainer = Color(0xFFFFFBEB) // Amber Container
val GenziiError = Color(0xFFE11D48) // Rose Red Text
val GenziiErrorContainer = Color(0xFFFFF1F2) // Rose Red Container
val GenziiAiProbabilistic = Color(0xFF7C3AED) // Iris Violet Text
val GenziiAiContainer = Color(0xFFF5F3FF) // Iris Violet Container
val GenziiInfo = Color(0xFF2563EB) // Royal Blue Text

// Dark Theme Colors - OLED Obsidian & Slate Surfaces
val GenziiDarkBackground = Color(0xFF0B0B0E) // Dark Neutral (OLED Obsidian)
val GenziiDarkSurface = Color(0xFF13141C) // Slate Surface
val GenziiDarkSurfaceVariant = Color(0xFF1E202E) // Dark Elevated
val GenziiDarkBorder = Color(0xFF282A3A) // Subtle Dark Border
val GenziiDarkTextPrimary = Color(0xFFFFFFFF)
val GenziiDarkTextSecondary = Color(0xFF94A3B8)
val GenziiDarkTextTertiary = Color(0xFF64748B)
val GenziiDarkDivider = Color(0xFF1F2937)
val GenziiDarkSwitchTrack = Color(0xFF1E202E)
val GenziiDarkSwitchBorder = Color(0xFF374151)
val GenziiDestructiveRed = Color(0xFFF43F5E)
