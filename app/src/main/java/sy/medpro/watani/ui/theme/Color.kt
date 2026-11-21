package sy.medpro.watani.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Featured/call-to-action card background: gold-tint ivory in light
 *  mode (Premium identity, LibColors.GoldTint), dark maroon in dark mode,
 *  so onSurface text stays readable in both. */
@Composable
fun featuredCardBackground(): Color =
    if (isSystemInDarkTheme()) DarkFeaturedContainer else FeaturedContainer

// Brand Primary Maroon Family
// Launcher/logo ink: the official app icon's own background (#2D1015 in
// ic_launcher_background) — the dark family the video splash and the
// luxury sign-in gate are built on, so cold start reads as one surface.
val MaroonInk = Color(0xFF2D1015)
val MaroonPrimary = Color(0xFF5E1224)
val MaroonDark = Color(0xFF42091A)
val MaroonDeep = Color(0xFF340713)
val MaroonLight = Color(0xFF7A2233)
val MaroonContainer = Color(0xFFF7EFDC)
val OnMaroonContainer = Color(0xFF42091A)

// Premium gold accent (LibColors.Gold / GoldText). The historical
// "Gold" names are kept because ~20 files reference them.
val GoldAccent = Color(0xFFC2A059)
val GoldAccentDark = Color(0xFF8A6D2F)
// Featured container family: gold-tint ivory of the Premium identity
// (LibColors.GoldTint) in light mode.
val FeaturedContainer = Color(0xFFF7EFDC)
val OnFeaturedContainer = Color(0xFF42091A)
// Dark-mode counterpart of FeaturedContainer: a dark maroon so featured
// cards keep the maroon identity with readable light text in dark mode.
val DarkFeaturedContainer = Color(0xFF402228)

// Light Theme Surfaces & Backgrounds (Premium ivory family; the
// historical "Rose" names are kept because app code references them)
val RoseBackground = Color(0xFFFAF6F0)
val RoseSurface = Color(0xFFFFFFFF)
val RoseSurfaceVariant = Color(0xFFF3E9E4)
val RoseOutline = Color(0xFFEFE6DA)

// Dark Theme Surfaces & Backgrounds (Dark Maroon Surfaces)
val DarkMaroonBackground = Color(0xFF1A0A10)
val DarkMaroonSurface = Color(0xFF26101A)
val DarkMaroonSurfaceVariant = Color(0xFF381824)
val DarkMaroonOutline = Color(0xFF6E4C55)

// Typography & Semantic Colors
val TextPrimary = Color(0xFF2B2320)
val TextSecondary = Color(0xFF8A7A6E)
val TextOnDark = Color(0xFFF5EDE3)
val SuccessGreen = Color(0xFF2E7D32)
val ErrorRed = Color(0xFFC62828)
val WarningAmber = Color(0xFFEF6C00)
