package com.offcampus.app.ui.theme

import androidx.compose.ui.graphics.Color

// Phase 0 "streetlight & meter" palette. Every screen must pull colors from here (or from
// MaterialTheme.colorScheme, which Theme.kt builds out of these) rather than hardcoding hex values.

// --- Light mode ---
val LightBackground = Color(0xFFF7F5FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFEBF7)
val LightOnBackground = Color(0xFF1C1730)
val LightOnSurfaceVariant = Color(0xFF635C7A)
val LightBorder = Color(0xFFE3DEF0)

val LightViolet = Color(0xFF6753E8) // primary — buttons, links, active states
val LightAmber = Color(0xFFE8940E)  // action / secondary — ride-type chips, CTAs
val LightGreen = Color(0xFF1E9463)  // success — settled payments, online status
val LightCoral = Color(0xFFD8492F)  // alert — amount owed, errors, reports

// --- Dark mode (same five roles, re-tuned for contrast on a dark ground) ---
val DarkBackground = Color(0xFF171226)
val DarkSurface = Color(0xFF201A35)
val DarkSurfaceVariant = Color(0xFF2A2244)
val DarkOnBackground = Color(0xFFF4F1FC)
val DarkOnSurfaceVariant = Color(0xFFB3ABCC)
val DarkBorder = Color(0xFF362C55)

val DarkViolet = Color(0xFF9384FF)
val DarkAmber = Color(0xFFF5A524)
val DarkGreen = Color(0xFF3FCB90)
val DarkCoral = Color(0xFFFF7A5C)
