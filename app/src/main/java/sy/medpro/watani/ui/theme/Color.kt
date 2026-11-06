package sy.medpro.watani.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Featured/call-to-action card background: clean rose-ivory in light mode
 *  (no yellow tint, per the no-yellow brand rule), dark maroon in dark mode,
 *  so onSurface text stays readable in both. */
@Composable
fun featuredCardBackground(): Color =
    if (isSystemInDarkTheme()) DarkFeaturedContainer else FeaturedContainer

// Brand Primary Maroon Family
val MaroonPrimary = Color(0xFF7A1F2B)
val MaroonDark = Color(0xFF52131C)
val MaroonDeep = Color(0xFF3A0C13)
val MaroonLight = Color(0xFFA34855)
val MaroonContainer = Color(0xFFFADADD)
val OnMaroonContainer = Color(0xFF3E0A12)

// Muted copper/brass accent (no bright yellow anywhere in the app, per the
// no-yellow brand rule). The historical "Gold" names are kept because ~20
// files reference them; the values are now a subdued, less saturated copper.
val GoldAccent = Color(0xFFB08D57)
val GoldAccentDark = Color(0xFF8F6B3D)
// Featured container family (renamed from the old pale-yellow container
// family): clean rose-ivory with no yellow tint in light mode.
val FeaturedContainer = Color(0xFFF7E8EA)
val OnFeaturedContainer = Color(0xFF3E0A12)
// Dark-mode counterpart of FeaturedContainer: a dark maroon so featured
// cards keep the maroon identity with readable light text in dark mode.
val DarkFeaturedContainer = Color(0xFF402228)

// Light Theme Surfaces & Backgrounds
val RoseBackground = Color(0xFFFBF3F4)
val RoseSurface = Color(0xFFFFFFFF)
val RoseSurfaceVariant = Color(0xFFF4E4E6)
val RoseOutline = Color(0xFFD6BCC0)

// Dark Theme Surfaces & Backgrounds (Dark Maroon Surfaces)
val DarkMaroonBackground = Color(0xFF1A0E10)
val DarkMaroonSurface = Color(0xFF261518)
val DarkMaroonSurfaceVariant = Color(0xFF382024)
val DarkMaroonOutline = Color(0xFF6E4C51)

// Typography & Semantic Colors
val TextPrimary = Color(0xFF2C1518)
val TextSecondary = Color(0xFF6B5357)
val TextOnDark = Color(0xFFF7E6E8)
val SuccessGreen = Color(0xFF2E7D32)
val ErrorRed = Color(0xFFC62828)
val WarningAmber = Color(0xFFEF6C00)
