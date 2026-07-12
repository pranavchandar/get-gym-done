package com.getgymdone.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.getgymdone.app.R
import androidx.compose.ui.text.googlefonts.Font as GoogleFontResource

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

// Anton is bundled as an asset so the heavy condensed display face renders even when the
// emulator/device can't reach Google Fonts. It only ships as a single weight (400), but
// Compose can synthesize bold from it when `FontSynthesis.Weight` is set on a TextStyle.
val AntonFamily = FontFamily(
    Font(resId = R.font.anton_regular, weight = FontWeight.Normal),
)

val InterFamily = FontFamily(
    GoogleFontResource(googleFont = GoogleFont("Inter"), fontProvider = googleFontProvider, weight = FontWeight.Normal),
    GoogleFontResource(googleFont = GoogleFont("Inter"), fontProvider = googleFontProvider, weight = FontWeight.Medium),
    GoogleFontResource(googleFont = GoogleFont("Inter"), fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    GoogleFontResource(googleFont = GoogleFont("Inter"), fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

val JetBrainsMonoFamily = FontFamily(
    GoogleFontResource(googleFont = GoogleFont("JetBrains Mono"), fontProvider = googleFontProvider, weight = FontWeight.Medium),
    GoogleFontResource(googleFont = GoogleFont("JetBrains Mono"), fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

// Material 3 Typography — sport-magazine treatment: Anton condensed for display + numerals,
// Inter for body/UI, JetBrains Mono for data/dates.
val GymTypography = Typography(
    displayLarge = TextStyle(fontFamily = AntonFamily, fontWeight = FontWeight.Normal, fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = 0.5.sp),
    displayMedium = TextStyle(fontFamily = AntonFamily, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = 0.5.sp),
    displaySmall = TextStyle(fontFamily = AntonFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = 0.5.sp),

    headlineLarge = TextStyle(fontFamily = AntonFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = 0.6.sp),
    headlineMedium = TextStyle(fontFamily = AntonFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 24.sp, letterSpacing = 0.6.sp),
    headlineSmall = TextStyle(fontFamily = AntonFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 20.sp, letterSpacing = 0.6.sp),

    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),

    bodyLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),

    labelLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp),
    labelMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp),
    labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.6.sp),
)
