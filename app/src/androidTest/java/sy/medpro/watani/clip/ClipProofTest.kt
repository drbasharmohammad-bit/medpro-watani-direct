package sy.medpro.watani.clip

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sy.medpro.watani.ui.anki.AnkiGrade
import sy.medpro.watani.ui.anki.FlashcardFaceText
import sy.medpro.watani.ui.anki.FsrsGradeButtons
import sy.medpro.watani.ui.home.HomeBrandHeader
import sy.medpro.watani.ui.widget.WidgetSettingsScreen
import java.io.File
import java.io.FileOutputStream

/**
 * Text-clipping proof — AFTER state (build 1.2.55, stage 2 of 2).
 *
 * Stage 1 (commit 02bfdd4) rendered the verbatim 1.2.54
 * construction and shot clip_before.png: grade labels clipped by
 * the default 24.dp button content padding, card diacritics
 * clipped by the inherited 26.sp line height at 20.sp.
 *
 * This stage renders the PRODUCTION composables the fix ships:
 *  1. [FlashcardFaceText] (31.sp line height) over the same
 *     diacritic-stacked sample + [FsrsGradeButtons] (4.dp content
 *     padding, 58.dp height, label + FSRS interval) —
 *     clip_after.png;
 *  2. [HomeBrandHeader] — the doctor's home-header ruling: name
 *     beside the M logo, no «Med Pro» word in the block, and the
 *     study line complete on one line — header_after.png;
 *  3. the restored pre-glass [WidgetSettingsScreen] (solid v3
 *     family; the glass family was removed for good by the
 *     doctor's 27 Nov 2026 ruling) — widget_after.png.
 */
@RunWith(AndroidJUnit4::class)
class ClipProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.waitForIdle()
        Thread.sleep(600)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = instrumentation.targetContext.getExternalFilesDir(null)
        FileOutputStream(File(dir, name)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    @Test
    fun gradeButtonsAndCardText_afterFix() {
        composeRule.setContent {
            Surface {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    FlashcardFaceText(
                        text = "الدَّواءُ المُجَزَّأً يُعطى للمَريضِ صَباحًا، والمُؤَكَّدُ أنَّ الجُرعةَ تُقَسَّمُ على دُفُعاتٍ مُتَساوِيَةٍ طَوالَ اليَومِ."
                    )
                    FsrsGradeButtons(
                        intervals = mapOf(
                            AnkiGrade.AGAIN to "10 د",
                            AnkiGrade.HARD to "1 يوم",
                            AnkiGrade.GOOD to "3 يوم",
                            AnkiGrade.EASY to "7 يوم"
                        ),
                        onGrade = {}
                    )
                }
            }
        }
        composeRule.onNode(hasText("سهل", substring = true)).assertIsDisplayed()
        composeRule.onNode(hasText("صعب", substring = true)).assertIsDisplayed()
        composeRule.onNode(hasText("10 د", substring = true)).assertIsDisplayed()
        shoot("clip_after.png")
    }

    @Test
    fun homeHeader_afterRedesign() {
        composeRule.setContent {
            Surface {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    HomeBrandHeader(displayName = "د. بشار المحمد")
                }
            }
        }
        composeRule.onNode(hasText("بشار المحمد", substring = true)).assertIsDisplayed()
        composeRule.onNode(hasText("دراستك اليوم", substring = true)).assertIsDisplayed()
        shoot("header_after.png")
    }

    @Test
    fun widgetSettings_restoredSolidFamily() {
        composeRule.setContent {
            WidgetSettingsScreen(onBack = {})
        }
        composeRule.onNode(hasText("خلفية الويدجت", substring = true)).assertIsDisplayed()
        shoot("widget_after.png")
    }
}
