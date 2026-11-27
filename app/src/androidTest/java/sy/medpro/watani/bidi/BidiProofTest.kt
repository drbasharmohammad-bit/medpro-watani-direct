package sy.medpro.watani.bidi

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sy.medpro.watani.ui.firstaid.FaQuizItem
import sy.medpro.watani.ui.firstaid.FaScreenHeader
import sy.medpro.watani.ui.firstaid.FirstAidQuizPlayer
import sy.medpro.watani.ui.firstaid.isolateLtrRun
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily
import java.io.File
import java.io.FileOutputStream

/**
 * Bidi display proof (build 1.2.55).
 *
 * Doctor's report on 1.2.54 (27 Nov 2026): First Aid Step 2 printed
 * bank test 3, question 1 — the Arabic explanation with Latin
 * parentheticals rendered scrambled (segments and parentheses out of
 * place, glued words), and the header page range showed reversed
 * («299-241»). Root cause was twofold: the stored bank text itself
 * was in visual order from the book-PDF extraction (repaired and
 * republished server-side, 27 Nov 2026), and the page-range subtitle
 * composed a bare Latin digit range inside an Arabic string (fixed
 * by [isolateLtrRun] at the composition sites).
 *
 * This test renders, on a real (emulated) Android runtime:
 *  1. the bank header exactly as production composes it (isolated
 *     range) — screenshot `bidi_1_header.png`;
 *  2. the doctor's exact question through the shipped
 *     [FirstAidQuizPlayer] with its repaired live text, answering it
 *     so the explanation shows — screenshot `bidi_2_quiz.png`;
 *  3. a real Watani-bank explanation sample (NV2-TWL-0150) in the
 *     same plain-Text rendering the banks use — screenshot
 *     `bidi_3_watani.png`.
 * Screenshots land in the app's external files dir and are pulled by
 * scripts/run-bidi-emulator-test.sh as the visual proof artifact.
 */
@RunWith(AndroidJUnit4::class)
class BidiProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.waitForIdle()
        Thread.sleep(600)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        // additionalTestOutputDir is the documented channel Gradle
        // pulls back with the test results (plain adb pull of the
        // app sandbox proved unreliable in the emulator action:
        // run 46027639852 produced reports but no screenshots).
        val dir = instrumentation.arguments.getString("additionalTestOutputDir")
            ?.let { File(it).apply { mkdirs() } }
            ?: instrumentation.targetContext.getExternalFilesDir(null)
        FileOutputStream(File(dir, name)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    @Test
    fun headerRange_rendersInOrder() {
        // ── 1) Header with the production-composed subtitle ──────
        composeRule.setContent {
            Column {
                FaScreenHeader(
                    title = "الاختبارات التدريبية 3",
                    subtitle = "بنوك Step 2 المطبوعة — صفحات ${isolateLtrRun("241-299")}",
                    onBack = {}
                )
            }
        }
        composeRule.onNode(hasText("241-299", substring = true)).assertIsDisplayed()
        shoot("bidi_1_header.png")
    }

    @Test
    fun quizExplanation_rendersInOrder() {
        // ── 2) The doctor's question, repaired live text ─────────
        val item = FaQuizItem(
            stem = "ما دراسة التصوير الأولية الموصى بها لمريض طفل بعد أول عدوى مسالك بولية حموية؟ (Urinary Tract Infection, UTI)",
            options = listOf(
                "تصوير الإحليل والمثانة أثناء التبول (Voiding Cystourethrogram, VCUG)",
                "إيكو الكلية والمثانة (Renal and Bladder Ultrasound)",
                "تصوير الحويضة الوريدي (Intravenous Pyelogram, IVP)",
                "صورة البطن الشعاعية البسيطة (Abdominal X-Ray)"
            ),
            correctIndex = 1,
            explanation = "أي: يعد إيكو الكلية والمثانة (Renal and Bladder Ultrasound) دراسة التصوير الأولية الموصى بها للمرضى الأطفال بعد أول عدوى مسالك بولية (Urinary Tract Infection, UTI) حموية. وهو غير باضع (Non-Invasive)، ولا يستخدم الإشعاع، ويمكن أن يكشف الشذوذات البنيوية (Structural Abnormalities) أو موه الكلية (Hydronephrosis) أو التندب الكلوي (Renal Scarring). ويُحفَظ تصوير الإحليل والمثانة أثناء التبول (Voiding Cystourethrogram, VCUG) عادةً أيضًا، وأكثر لبضع استطبابات نوعية أو للعدوى الناكسة",
            optionExplanations = null,
            tag = "سؤال 1",
            sourceIndex = 0
        )
        composeRule.setContent {
            FirstAidQuizPlayer(
                title = "الاختبارات التدريبية 3",
                subtitle = "بنوك Step 2 المطبوعة — صفحات ${isolateLtrRun("241-299")}",
                items = listOf(item),
                onClose = {}
            )
        }
        composeRule.onNode(hasText("إيكو الكلية والمثانة", substring = true))
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Renal Scarring", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("Renal Scarring", substring = true)).assertIsDisplayed()
        shoot("bidi_2_quiz.png")
    }

    @Test
    fun wataniSample_rendersInOrder() {
        // ── 3) Watani bank explanation sample (NV2-TWL-0150) ─────
        composeRule.setContent {
            Column {
                Text(
                    text = "يُفرز التستوستيرون من الخصية لا من المشيمة. أما بقية الهرمونات فتفرزها المشيمة فعليًا: الإستروجين، البروجسترون، موجهة الغدد التناسلية المشيمائية البشرية (hCG)، اللاكتوجين المشيمي البشري (hPL)، وهرمون النمو المشيمي البشري.",
                    fontFamily = NotoSansArabicFontFamily
                )
            }
        }
        composeRule.onNode(hasText("hCG", substring = true)).assertIsDisplayed()
        shoot("bidi_3_watani.png")
    }
}
