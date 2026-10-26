package sy.medpro.watani.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MaroonLight,
    onPrimary = Color.White,
    primaryContainer = MaroonDark,
    onPrimaryContainer = MaroonContainer,
    secondary = GoldAccent,
    onSecondary = OnGoldContainer,
    secondaryContainer = DarkMaroonSurfaceVariant,
    onSecondaryContainer = TextOnDark,
    tertiary = MaroonPrimary,
    onTertiary = Color.White,
    background = DarkMaroonBackground,
    onBackground = TextOnDark,
    surface = DarkMaroonSurface,
    onSurface = TextOnDark,
    surfaceVariant = DarkMaroonSurfaceVariant,
    onSurfaceVariant = Color(0xFFDDC2C5),
    outline = DarkMaroonOutline,
    error = Color(0xFFEF5350),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = MaroonPrimary,
    onPrimary = Color.White,
    primaryContainer = MaroonContainer,
    onPrimaryContainer = OnMaroonContainer,
    secondary = MaroonDark,
    onSecondary = Color.White,
    secondaryContainer = RoseSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = GoldAccent,
    onTertiary = OnGoldContainer,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = OnGoldContainer,
    background = RoseBackground,
    onBackground = TextPrimary,
    surface = RoseSurface,
    onSurface = TextPrimary,
    surfaceVariant = RoseSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = RoseOutline,
    error = ErrorRed,
    onError = Color.White
)

val WataniShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WataniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = MaroonPrimary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = WataniShapes,
            content = content
        )
    }
}
