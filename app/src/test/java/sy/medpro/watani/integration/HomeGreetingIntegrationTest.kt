package sy.medpro.watani.integration

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.CountdownDigits
import sy.medpro.watani.data.ProfileNameStore
import sy.medpro.watani.ui.theme.WataniTheme

/**
 * Layer 3 — build 1.2.51 home fixes, rendered end-to-end:
 *  1. The greeting line renders the title prefix in the NAME's
 *     language: «د.» for an Arabic name, "Dr." for a Latin one
 *     (the doctor's device photo showed «د.» misplaced next to
 *     "Bashar Al Mohammad"). The home composes exactly this
 *     greetingFor() output, so rendering it here is rendering the
 *     home's first line.
 *  2. A countdown cell renders Eastern digits only — the value
 *     the home's CountdownUnit receives for the «27 days» cell.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class HomeGreetingIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun latinNameRendersWithDrPrefix() {
        composeRule.setContent {
            WataniTheme {
                Text(ProfileNameStore.greetingFor("Bashar Al Mohammad"))
            }
        }
        composeRule.onNodeWithText("Dr. Bashar Al Mohammad").assertIsDisplayed()
    }

    @Test
    fun arabicNameRendersWithArabicPrefix() {
        composeRule.setContent {
            WataniTheme {
                Text(ProfileNameStore.greetingFor("بشار المحمد"))
            }
        }
        composeRule.onNodeWithText("د. بشار المحمد").assertIsDisplayed()
    }

    @Test
    fun countdownCellRendersEasternDigitsOnly() {
        composeRule.setContent {
            WataniTheme {
                Text(CountdownDigits.unit(27))
            }
        }
        composeRule.onNodeWithText("٢٧").assertIsDisplayed()
    }
}
