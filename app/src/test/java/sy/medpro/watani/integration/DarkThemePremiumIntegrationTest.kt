package sy.medpro.watani.integration

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.ui.firstaid.FaColors
import sy.medpro.watani.ui.firstaid.faBackground
import sy.medpro.watani.ui.firstaid.faGoldTint
import sy.medpro.watani.ui.firstaid.faHeading
import sy.medpro.watani.ui.firstaid.faSurface
import sy.medpro.watani.ui.library.LibColors
import sy.medpro.watani.ui.library.libBackground
import sy.medpro.watani.ui.library.libGoldTint
import sy.medpro.watani.ui.library.libSurface
import sy.medpro.watani.ui.theme.DarkFeaturedContainer
import sy.medpro.watani.ui.theme.FeaturedContainer
import sy.medpro.watani.ui.theme.WataniTheme
import sy.medpro.watani.ui.theme.featuredCardBackground
import sy.medpro.watani.ui.theme.wataniDarkTheme

/**
 * Layer 3 — the Premium identity follows the APP's dark-mode toggle
 * (build 1.2.43, doctor's device report: «الوضع الليلي ما يشتغل على
 * الواجهة الجديدة»).
 *
 * Root cause: the app theme state is the in-app «dark» setting fed
 * to WataniTheme(darkTheme=…), but the Premium design helpers
 * (First Aid Fa*, Library lib*, featured cards) branched on
 * isSystemInDarkTheme() — the PHONE's setting. With the toggle on
 * and the phone in light mode, MaterialTheme went dark while the
 * new surfaces stayed light. Robolectric's system theme is light,
 * which is exactly the failing combination: under the old code
 * every assertion in the dark half of this test returned the LIGHT
 * value. The helpers now read LocalWataniDarkTheme, provided by
 * WataniTheme from its own darkTheme argument.
 *
 * The light half pins the approved design values byte-for-byte —
 * the fix must not move a single light colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1600dp")
class DarkThemePremiumIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class Captured {
        var dark: Boolean? = null
        var faBg: Color? = null
        var faSurface: Color? = null
        var faHeading: Color? = null
        var faGoldTint: Color? = null
        var libBg: Color? = null
        var libSurface: Color? = null
        var libGoldTint: Color? = null
        var featured: Color? = null
    }

    private fun capture(darkTheme: Boolean): Captured {
        val out = Captured()
        composeRule.setContent {
            WataniTheme(darkTheme = darkTheme) {
                out.dark = wataniDarkTheme()
                out.faBg = faBackground()
                out.faSurface = faSurface()
                out.faHeading = faHeading()
                out.faGoldTint = faGoldTint()
                out.libBg = libBackground()
                out.libSurface = libSurface()
                out.libGoldTint = libGoldTint()
                out.featured = featuredCardBackground()
            }
        }
        composeRule.waitForIdle()
        return out
    }

    @Test
    fun appDarkToggleDrivesPremiumSurfacesDark() {
        val c = capture(darkTheme = true)
        assertEquals(true, c.dark)
        assertEquals(Color(0xFF1A0A10), c.faBg) // DarkMaroonBackground
        assertEquals(Color(0xFF26101A), c.faSurface) // DarkMaroonSurface
        assertEquals(Color(0xFFF5EDE3), c.faHeading) // TextOnDark
        assertEquals(Color(0xFF4A3418), c.faGoldTint) // dark gold tint
        assertEquals(Color(0xFF1A0A10), c.libBg)
        assertEquals(Color(0xFF26101A), c.libSurface)
        assertEquals(Color(0xFF4A3418), c.libGoldTint)
        assertEquals(DarkFeaturedContainer, c.featured)
    }

    @Test
    fun lightModeKeepsTheApprovedDesignValuesExactly() {
        val c = capture(darkTheme = false)
        assertEquals(false, c.dark)
        assertEquals(FaColors.Bg, c.faBg)
        assertEquals(Color.White, c.faSurface)
        assertEquals(FaColors.MaroonDeep, c.faHeading)
        assertEquals(FaColors.GoldTint, c.faGoldTint)
        assertEquals(LibColors.Bg, c.libBg)
        assertEquals(Color.White, c.libSurface)
        assertEquals(LibColors.GoldTint, c.libGoldTint)
        assertEquals(FeaturedContainer, c.featured)
    }

    @Test
    fun togglingTheThemeStateFlipsTheSurfaces() {
        var dark by mutableStateOf(false)
        val out = Captured()
        composeRule.setContent {
            WataniTheme(darkTheme = dark) {
                out.faBg = faBackground()
                out.libBg = libBackground()
            }
        }
        composeRule.waitForIdle()
        assertEquals(FaColors.Bg, out.faBg)
        dark = true
        composeRule.waitForIdle()
        assertEquals(Color(0xFF1A0A10), out.faBg)
        assertEquals(Color(0xFF1A0A10), out.libBg)
    }
}
