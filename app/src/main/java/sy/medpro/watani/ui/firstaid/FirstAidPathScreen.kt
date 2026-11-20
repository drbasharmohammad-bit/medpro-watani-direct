package sy.medpro.watani.ui.firstaid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.FirstAidIndexDto
import sy.medpro.watani.data.FirstAidUnitSummaryDto
import sy.medpro.watani.ui.isMediumWidth
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily

/**
 * Path screen (merge phase 5, design model 02-path): one book's
 * systems in book order, each system's units as levels with their
 * live state from [FirstAidProgress] (done / current / next /
 * locked), a booklet chip on every system that has one (always
 * available), and the path's overall progress card. On medium+
 * widths the systems flow in two columns.
 */
@Composable
fun FirstAidPathScreen(
    step: Int,
    index: FirstAidIndexDto,
    onBack: () -> Unit,
    onOpenUnit: (unitId: String) -> Unit,
    onOpenBooklet: (sy.medpro.watani.data.FirstAidBookletDto) -> Unit
) {
    val context = LocalContext.current
    val units = remember(index) { index.units.orEmpty().sortedBy { it.order ?: Int.MAX_VALUE } }
    val byId = remember(units) { units.associateBy { it.id } }
    val sessionIds = remember(units) {
        units.filter { it.kind == "integration" }.mapNotNull { it.id }.toSet()
    }
    val pathIds = remember(units) {
        units.filter { it.kind != "integration" }.mapNotNull { it.id }
    }
    val doneCount = remember(units) {
        pathIds.count { FirstAidProgress.isUnitDone(context, step, it) }
    }
    val currentSystemAr = remember(units, doneCount) {
        val nextId = pathIds.firstOrNull { !FirstAidProgress.isUnitDone(context, step, it) }
        nextId?.let { byId[it]?.sectionAr }
    }
    val wide = isMediumWidth()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(faBackground()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FaScreenHeader(
                title = if (step == 2) "Step 2 — التطبيق السريري" else "Step 1 — الأساسيات",
                subtitle = "${pathIds.size} وحدة في ${index.systems.orEmpty().size} نظامًا · بترتيب فصول الكتاب",
                onBack = onBack
            )
        }

        // Overall progress card (.overall).
        item {
            FaCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "تقدّم المسار",
                            fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, color = faHeading()
                        )
                        Text(
                            if (doneCount >= pathIds.size && pathIds.isNotEmpty()) {
                                "أكملت المسار كاملًا — أحسنت"
                            } else {
                                "أكملت $doneCount من ${pathIds.size} وحدة" +
                                    (currentSystemAr?.let { " · الآن: $it" } ?: "")
                            },
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 11.sp, color = faMuted()
                        )
                        FaProgressBar(
                            fraction = if (pathIds.isEmpty()) 0f else doneCount.toFloat() / pathIds.size,
                            modifier = Modifier.padding(top = 7.dp)
                        )
                    }
                    Text(
                        "${if (pathIds.isEmpty()) 0 else (doneCount * 100) / pathIds.size}%",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp, color = FaColors.Maroon
                    )
                }
            }
        }

        // Systems, each with its level rows.
        val systems = index.systems.orEmpty()
        if (wide) {
            items(systems.chunked(2).size) { pairIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    systems.chunked(2)[pairIndex].forEach { system ->
                        Box(modifier = Modifier.weight(1f)) {
                            FaSystemBlock(step, index, system, byId, pathIds, sessionIds, onOpenUnit, onOpenBooklet)
                        }
                    }
                    if (systems.chunked(2)[pairIndex].size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            items(systems.size) { i ->
                FaSystemBlock(step, index, systems[i], byId, pathIds, sessionIds, onOpenUnit, onOpenBooklet)
            }
        }

        item {
            Text(
                if (step == 2) {
                    "يُفتح المستوى التالي بإتمام اختبار الخروج بنسبة 60% فأكثر"
                } else {
                    "يُفتح المستوى التالي بإتمام تدريب الوحدة السابقة"
                },
                fontFamily = NotoSansArabicFontFamily, fontSize = 10.8.sp, color = faMuted(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FaSystemBlock(
    step: Int,
    index: FirstAidIndexDto,
    system: sy.medpro.watani.data.FirstAidSystemDto,
    byId: Map<String?, FirstAidUnitSummaryDto>,
    pathIds: List<String>,
    sessionIds: Set<String>,
    onOpenUnit: (String) -> Unit,
    onOpenBooklet: (sy.medpro.watani.data.FirstAidBookletDto) -> Unit
) {
    val context = LocalContext.current
    val systemUnits = system.units.orEmpty().filter { byId[it]?.kind != "integration" }
    val doneInSystem = systemUnits.count { FirstAidProgress.isUnitDone(context, step, it) }
    val booklet = remember(index, system.code) {
        bookletForSystem(index, system.code, system.sectionEn)
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                system.sectionAr ?: system.sectionEn ?: system.code ?: "",
                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp, color = faHeading(),
                modifier = Modifier.weight(1f)
            )
            Text(
                "${systemUnits.size} وحدات" + if (doneInSystem > 0) " · أكملت $doneInSystem" else "",
                fontFamily = NotoSansArabicFontFamily, fontSize = 11.sp, color = faMuted()
            )
            if (booklet != null) {
                FaChip("كتيّب PDF", FaColors.GoldTint, FaColors.GoldText)
            }
        }
        if (booklet != null) {
            BookletRow(step = step, booklet = booklet) { onOpenBooklet(booklet) }
        }
        systemUnits.forEachIndexed { positionInSystem, unitId ->
            val summary = byId[unitId]
            val status = FirstAidProgress.status(context, step, pathIds, unitId, sessionIds)
            FaLevelRow(
                position = (pathIds.indexOf(unitId) + 1).coerceAtLeast(1),
                title = summary?.titleAr ?: unitId,
                sub = "تدريب ${summary?.quizCount ?: "—"}" +
                    (if (step == 2) " · مدخل ${summary?.precheckCount ?: "—"} · خروج ${summary?.exitcheckCount ?: "—"}" else "") +
                    " · بطاقات ${summary?.flashcardCount ?: "—"}",
                status = status,
                exitFraction = if (step == 2) FirstAidProgress.exitBestFraction(context, unitId) else null,
                trainDone = FirstAidProgress.isTrainingDone(context, unitId),
                onClick = { if (status != FirstAidProgressRules.UnitStatus.LOCKED) onOpenUnit(unitId) }
            )
            if (positionInSystem < systemUnits.lastIndex) {
                // connector tick between levels (.connector)
                Box(
                    modifier = Modifier
                        .padding(start = 32.dp)
                        .size(width = 2.dp, height = 0.dp)
                ) {}
            }
        }
    }
}

@Composable
private fun FaLevelRow(
    position: Int,
    title: String,
    sub: String,
    status: FirstAidProgressRules.UnitStatus,
    exitFraction: Double?,
    trainDone: Boolean,
    onClick: () -> Unit
) {
    val isNow = status == FirstAidProgressRules.UnitStatus.AVAILABLE && (trainDone || exitFraction != null)
    FaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (status == FirstAidProgressRules.UnitStatus.LOCKED) null else onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            FirstAidProgressRules.UnitStatus.DONE -> FaColors.Maroon
                            FirstAidProgressRules.UnitStatus.AVAILABLE ->
                                if (isNow) Color.White else Color(0xFFF3E9E4)
                            FirstAidProgressRules.UnitStatus.LOCKED -> Color(0xFFF1EAE1)
                        }
                    )
                    .then(
                        // .lvl.now .st in the design: white disc with a
                        // 2dp gold ring.
                        if (isNow) {
                            Modifier.border(2.dp, FaColors.Gold, CircleShape)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (status) {
                    FirstAidProgressRules.UnitStatus.DONE -> androidx.compose.material3.Icon(
                        Icons.Filled.Check, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(19.dp)
                    )
                    FirstAidProgressRules.UnitStatus.LOCKED -> androidx.compose.material3.Icon(
                        Icons.Filled.Lock, contentDescription = null,
                        tint = Color(0xFFB4A696), modifier = Modifier.size(17.dp)
                    )
                    FirstAidProgressRules.UnitStatus.AVAILABLE -> Text(
                        "$position",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp, color = FaColors.Maroon
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = if (status == FirstAidProgressRules.UnitStatus.LOCKED) {
                        Color(0xFF9A8B7C)
                    } else faHeading()
                )
                Text(
                    sub,
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 11.3.sp, color = faMuted()
                )
                if (status == FirstAidProgressRules.UnitStatus.AVAILABLE && exitFraction != null) {
                    FaProgressBar(
                        fraction = exitFraction.toFloat(),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            when (status) {
                FirstAidProgressRules.UnitStatus.DONE ->
                    FaChip("مكتمل", FaColors.GreenTint, FaColors.Green)
                FirstAidProgressRules.UnitStatus.AVAILABLE ->
                    FaChip(
                        if (isNow) "جارٍ الآن" else "التالي",
                        FaColors.GoldTint, FaColors.GoldText
                    )
                FirstAidProgressRules.UnitStatus.LOCKED ->
                    FaChip("مقفل", Color(0xFFF5F0E9), Color(0xFFA08D78))
            }
        }
    }
}
