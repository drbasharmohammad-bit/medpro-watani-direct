package sy.medpro.watani.ui.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.anki.AnkiFlashcardScreen
import sy.medpro.watani.ui.clinical.AuscultationTrainerScreen
import sy.medpro.watani.ui.clinical.BedsideClinicalTab
import sy.medpro.watani.ui.clinical.BedsideDosingAndIcuSuiteScreen
import sy.medpro.watani.ui.features.AbgLabScreen
import sy.medpro.watani.ui.features.ClinicalCalculatorsExpandedScreen
import sy.medpro.watani.ui.features.EcgTrainerScreen
import sy.medpro.watani.ui.features.LabValuesLabScreen
import sy.medpro.watani.ui.offline.OfflineEcgAtlasScreen
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * تجميع التبويبات المكررة (28 أكتوبر 2026، بأمر الدكتور):
 * كل فكرة واحدة تحت تبويب واحد باللوحة الجانبية، بلا حذف أي محتوى.
 * الشاشات الداخلية تُستدعى كما هي (بـ Scaffold الخاص بها) فلا يُعاد كتابة
 * محتواها ولا يُحذف منه شيء؛ التبويب العلوي هو فقط بوابة التنقل بينها.
 * الأسماء القديمة ما زالت تعمل كأسماء مستعارة من MainActivity (تفتح المجموعة
 * على التبويب المقابل)، فلا تنكسر روابط الرئيسية.
 */

@Composable
private fun GroupedTabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selected,
            containerColor = MaroonPrimary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
fun HeartAuscultationScreen(onBack: () -> Unit, initialTab: Int = 0) {
    var selected by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    GroupedTabs(
        titles = listOf("مدرب تخطيط القلب", "أطلس تخطيط القلب", "مدرب الإصغاء"),
        selected = selected,
        onSelect = { selected = it }
    ) {
        when (selected) {
            0 -> EcgTrainerScreen(onBack = onBack)
            1 -> OfflineEcgAtlasScreen(onBack = onBack)
            else -> AuscultationTrainerScreen(onBack = onBack)
        }
    }
}

/**
 * المختبرات وغازات الدم (إعادة تنظيم لوحة الأدوات، نوفمبر 2026):
 * القيم المرجعية ومفسّر غازات الدم فقط — أجزاء الحاسبات التي كانت هنا
 * انتقلت إلى شاشة «الحاسبات الطبية» الموحّدة، فلا تكرار بين اللوحتين.
 */
@Composable
fun LabsAndAbgScreen(
    onBack: () -> Unit,
    initialTab: Int = 0
) {
    var selected by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    GroupedTabs(
        titles = listOf(
            "مختبر غازات الدم",
            "مختبر القيم المخبرية"
        ),
        selected = selected,
        onSelect = { selected = it }
    ) {
        when (selected) {
            0 -> AbgLabScreen(onBack = onBack)
            else -> LabValuesLabScreen(onBack = onBack)
        }
    }
}

/**
 * الحاسبات الطبية الموحّدة (إعادة التنظيم حسب الاختصاص، نوفمبر 2026):
 * شاشة دليل واحدة بلا تبويبات — كل الحاسبات الـ66 مرتبة في 10 فئات
 * حسب الاختصاص الطبي (calcCatalog.kt)، وكل حاسبة تظهر مرة واحدة
 * باسمها ووصف قصير، وتفاصيلها الكاملة داخل شاشتها عند النقر عليها.
 * التوقيع باقٍ للتوافق الخلفي مع روابط MainActivity المستعارة:
 * التبويب 0 يفتح الدليل على أول فئة، والتبويب 1 يفتح الدليل على فئة
 * «صدرية وتنفسية» (فئة CURB-65 ومفسّر غازات الدم)، والتبويب 2 يفتح
 * مباشرة شاشة الألواح السريرية على لوح initialBedsideTab كما كان.
 */
@Composable
fun MedicalCalculatorsScreen(
    onBack: () -> Unit,
    initialTab: Int = 0,
    initialBedsideTab: BedsideClinicalTab = BedsideClinicalTab.ICU_INFUSIONS
) {
    when (initialTab.coerceIn(0, 2)) {
        2 -> BedsideDosingAndIcuSuiteScreen(
            initialTab = initialBedsideTab,
            onBack = onBack
        )
        1 -> ClinicalCalculatorsExpandedScreen(
            onBack = onBack,
            initialCategory = "صدرية وتنفسية"
        )
        else -> ClinicalCalculatorsExpandedScreen(onBack = onBack)
    }
}

@Composable
fun FlashcardsReviewScreen(onBack: () -> Unit, initialTab: Int = 0, ankiWrongOnly: Boolean = false) {
    // التبويب حُذف نهائيًا — تبقى المراجعة الذكية Anki وحدها،
    // و initialTab صار غير مستخدم ويُبقى للتوافق مع المستدعين.
    AnkiFlashcardScreen(onBack = onBack, wrongOnly = ankiWrongOnly)
}
