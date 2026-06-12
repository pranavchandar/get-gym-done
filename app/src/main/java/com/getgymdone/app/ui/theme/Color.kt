package com.getgymdone.app.ui.theme

import androidx.compose.ui.graphics.Color

// Dark palette — primary aesthetic from the design.
val GymBgDark        = Color(0xFF0A0A09)
val GymSurfaceDark   = Color(0xFF16140F)
val GymSurface2Dark  = Color(0xFF1F1C14)
val GymSurface3Dark  = Color(0xFF2A2620)
val GymLineDark      = Color(0xFF2E2B22)
val GymFgDark        = Color(0xFFF7F5F0)
val GymFg2Dark       = Color(0xFFA8A59A)
val GymFg3Dark       = Color(0xFF696657)

// Light palette
val GymBgLight       = Color(0xFFF5F3EE)
val GymSurfaceLight  = Color(0xFFFFFFFF)
val GymSurface2Light = Color(0xFFEBE8E0)
val GymSurface3Light = Color(0xFFDDD9CD)
val GymLineLight     = Color(0xFFD6D2C4)
val GymFgLight       = Color(0xFF14130F)
val GymFg2Light      = Color(0xFF5A564A)
val GymFg3Light      = Color(0xFF908A78)

// Accents — sRGB approximations of the design's oklch values.
val AccentLime       = Color(0xFFC1F038)  // oklch(0.88 0.22 125)
val AccentLimeFg     = Color(0xFF0A0A09)
val AccentCoral      = Color(0xFFF76E5C)  // oklch(0.70 0.22 25)

// Body-metric trend arrows.
val TrendUp          = Color(0xFF55C46E)  // green — value rose vs the previous entry
val TrendDown        = AccentCoral        // red — value fell vs the previous entry
