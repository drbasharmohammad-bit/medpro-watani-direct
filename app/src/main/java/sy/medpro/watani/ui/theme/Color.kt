package sy.medpro.watani.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Featured/call-to-action card background: light gold in light mode,
 *  dark bronze-gold in dark mode, so onSurface text stays readable in both. */
@Composable
fun featuredCardBackground(): Color =
    if (isSystemInDarkTheme()) DarkGoldContainer else GoldContainer

// Brand Primary Maroon Family
val MaroonPrimary = Color(0xFF7A1F2B)
val MaroonDark = Color(0xFF52131C)
val MaroonDeep = Color(0xFF3A0C13)
val MaroonLight = Color(0xFFA34855)
val MaroonContainer = Color(0xFFFADADD)
val OnMaroonContainer = Color(0xFF3E0A12)

// Brand Gold / Brass Medical Accent
val GoldAccent = Color(0xFFD4AF37)
val GoldAccentDark = Color(0xFFA67C1E)
val GoldContainer = Color(0xFFFFF0C2)
val OnGoldContainer = Color(0xFF3D2E00)
// Dark-mode counterpart of GoldContainer: a dark bronze-gold so featured
// cards keep gold identity with readable light text in dark mode.
val DarkGoldContainer = Color(0xFF4A3510)

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
