package sy.medpro.watani.clip

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Text-clipping proof — BEFORE state (build 1.2.55, stage 1 of 2).
 *
 * Doctor's device report on 1.2.54 (27 Nov 2026): on the smart
 * flashcards study screen the four FSRS grade buttons clip their
 * labels («سهل» renders «سه», «صعب» renders «صع») and the card
 * text clips its Arabic diacritics at the tops of lines
 * («المجزّأً»).
 *
 * This stage renders the EXACT construction the shipped 1.2.54
 * code uses (copied verbatim from AnkiFlashcardScreen /
 * UserCardsScreen at 1f0c1b5):
 *  - grade buttons: Modifier.weight(1f).height(50.dp), DEFAULT
 *    Material3 content padding (24.dp on each side), one Text at
 *    13.sp — inside a ~328.dp-wide row each button's inner text
 *    width shrinks below the label's width and the button's clip
 *    cuts the final glyph;
 *  - card face text: Text at 20.sp bold with NO lineHeight — it
 *    inherits bodyLarge's fixed 26.sp line height (ratio 1.3),
 *    too tight for stacked Arabic marks, which overflow the line
 *    box and clip.
 * Screenshot: clip_before.png. Stage 2 replaces this file's body
 * with the production composables the fix introduces and shoots
 * clip_after.png.
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

    /** Verbatim 1.2.54 grade row (colors simplified to swatches). */
    @Composable
    private fun GradeRowBefore() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                modifier = Modifier.weight(1f).height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("إعادة", fontSize = 13.sp) }
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f).height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("صعب", fontSize = 13.sp) }
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E1224)),
                modifier = Modifier.weight(1f).height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("جيد", fontSize = 13.sp) }
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.weight(1f).height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("سهل", fontSize = 13.sp) }
        }
    }

    @Test
    fun gradeButtonsAndCardText_beforeFix() {
        composeRule.setContent {
            Surface {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    // Card face text exactly as shipped (20.sp, bold,
                    // no lineHeight) with a diacritic-stacked sample.
                    Text(
                        text = "الدَّواءُ المُجَزَّأً يُعطى للمَريضِ صَباحًا، والمُؤَكَّدُ أنَّ الجُرعةَ تُقَسَّمُ على دُفُعاتٍ مُتَساوِيَةٍ طَوالَ اليَومِ.",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    GradeRowBefore()
                }
            }
        }
        composeRule.onNode(hasText("سهل", substring = true)).assertIsDisplayed()
        shoot("clip_before.png")
    }
}
