package com.offcampus.app.ui.theme

import androidx.compose.ui.graphics.Color

// Lloyds Mobile Banking inspired "Deep Forest Green" institutional design system palette.
// Calm, bank-grade aesthetic combining quiet neutral surfaces, institutional Deep Forest Green (#005A36)
// primary brand color, warm amber transit indicators, and refined hairline borders.

// --- Light mode ---
val LightBackground = Color(0xFFF3F4F6) // calm institutional light grey canvas (iOS Inset Grouped)
val LightSurface = Color(0xFFFFFFFF) // crisp pure white card surfaces
val LightSurfaceVariant = Color(0xFFF8FAFC)
val LightOnBackground = Color(0xFF0F172A) // rich high-contrast dark slate
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceVariant = Color(0xFF475569) // muted neutral slate text
val LightBorder = Color(0xFFE5E7EB) // 1px hairline card border
val LightOutlineVariant = Color(0xFFE2E8F0)

val LightForestGreen = Color(0xFF005A36) // primary brand: Lloyds Deep Forest Green
val LightViolet = LightForestGreen // legacy alias for backward compatibility
val LightPrimaryContainer = Color(0xFF005A36) // deep forest green container
val LightOnPrimaryContainer = Color(0xFFFFFFFF)

val LightAmber = Color(0xFFD97706) // secondary brand: transit amber
val LightSecondaryContainer = Color(0xFFFEF3C7) // soft warm amber pill tint
val LightOnSecondaryContainer = Color(0xFF78350F)

val LightGreen = Color(0xFF00724D) // tertiary brand (rich emerald)
val LightTertiaryContainer = Color(0xFFDCFCE7) // soft emerald mint
val LightTertiaryFixed = Color(0xFFA7F3D0) // fresh mint badge
val LightOnTertiaryFixed = Color(0xFF064E3B)

val LightCoral = Color(0xFFDC2626) // error / alert

// --- Dark mode ---
val DarkBackground = Color(0xFF0B0F14) // deep OLED slate (bank-grade dark mode)
val DarkSurface = Color(0xFF161F28) // elevated dark slate cards
val DarkSurfaceVariant = Color(0xFF242E3B)
val DarkOnBackground = Color(0xFFF8FAFC)
val DarkOnSurface = Color(0xFFF8FAFC)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkBorder = Color(0xFF2A3644) // subtle 1px card hairline border
val DarkOutlineVariant = Color(0xFF374151)

val DarkForestGreen = Color(0xFF10B981) // primary brand in dark: refined emerald green
val DarkViolet = DarkForestGreen // legacy alias for backward compatibility
val DarkPrimaryContainer = Color(0xFF005A36)
val DarkOnPrimaryContainer = Color(0xFFFFFFFF)

val DarkAmber = Color(0xFFF5A524)
val DarkSecondaryContainer = Color(0xFF451A03)
val DarkOnSecondaryContainer = Color(0xFFFFDDB8)

val DarkGreen = Color(0xFF10B981) // emerald
val DarkTertiaryContainer = Color(0xFF064E3B)
val DarkTertiaryFixed = Color(0xFF6EE7B7)
val DarkOnTertiaryFixed = Color(0xFF002113)

val DarkCoral = Color(0xFFFF7A5C)
