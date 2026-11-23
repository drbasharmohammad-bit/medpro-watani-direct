package sy.medpro.watani.ui.firstaid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.FirstAidBookletDto
import sy.medpro.watani.data.FirstAidIndexDto
import sy.medpro.watani.data.FirstAidIntegrationDto
import sy.medpro.watani.data.FirstAidUnitPackageDto
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily
import java.io.File

/** Stages of the unit player (03-unit flow). */
private enum class FaStage { HOME, PRECHECK, REFERENCE, TRAINING, EXIT, INTERACTIVE, FLASHCARDS, GLOSSARY }

// 1.2.38: LOCKED removed — no stage is ever locked (doctor's
// no-hard-locks decision); stages are DONE or openable (NOW/NEXT).
private enum class FaStepState { DONE, NOW, NEXT }

/**
 * The unit player (merge phase 5, design models 03-unit + 04-quiz +
 * 05-reference):
 *
 * - Step 2 path unit, the literal flow: مدخل 5 ← مرجع ← تدريب 25 ←
 *   خروج 5 timed at [FA_EXIT_TIME_SECONDS], exit ≥60% opens the next
 *   unit via [FirstAidProgress]. The exit stage stays locked until
 *   the training is finished.
 * - Step 1 unit (no pre/exit/interactive in the source packs): مرجع ←
 *   تدريب كامل (15–25) ← بطاقات/قاموس; finishing the training
 *   completes the unit. Its media figures download WITH the unit and
 *   render at their in-text markers in the reference.
 * - Integration sessions: a single unlocked 25-question training.
 *
 * Wrong answers in every quiz stage go through
 * [FirstAidMistakeWiring] (phase 6). Integration links of the unit
 * navigate to the linked unit of the other book.
 */
@Composable
fun FirstAidUnitPlayer(
    step: Int,
    unitId: String,
    isSession: Boolean,
    index: FirstAidIndexDto,
    integration: FirstAidIntegrationDto?,
    unitTitleOf: (Int, String) -> String?,
    onBack: () -> Unit,
    onOpenUnit: (step: Int, unitId: String, isSession: Boolean) -> Unit,
    onOpenBookletPdf: (File, String) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { WataniRepository(context) }
    val scope = rememberCoroutineScope()
    val bookletOpener = rememberBookletOpener(repo, onOpenBookletPdf)

    var pkg by remember(unitId) { mutableStateOf<FirstAidUnitPackageDto?>(null) }
    var loadFailed by remember(unitId) { mutableStateOf(false) }
    // 1.2.45: the failure state carries a real retry — bumping the
    // attempt re-runs the load effect below (the doctor's rule: no
    // loading state without a resolution — content, or an error the
    // student can act on).
    var loadAttempt by remember(unitId) { mutableIntStateOf(0) }
    var stage by remember(unitId) { mutableStateOf(FaStage.HOME) }
    var progressTick by remember(unitId) { mutableIntStateOf(0) }
    var mediaFiles by remember(unitId) { mutableStateOf<Map<String, File>>(emptyMap()) }

    LaunchedEffect(unitId, loadAttempt) {
        loadFailed = false
        val loaded = repo.getFirstAidUnit(step, unitId)
        pkg = loaded
        loadFailed = loaded == null
        // Step-1 figures download WITH the unit (plan §4.3).
        val media = loaded?.media.orEmpty().filter { !it.file.isNullOrBlank() && !it.id.isNullOrBlank() }
        if (media.isNotEmpty()) {
            val resolved = mutableMapOf<String, File>()
            for (m in media) {
                val local = repo.getFirstAidMediaFile(unitId, m.file!!)
                if (local.exists() && local.length() > 0) {
                    resolved[m.id!!] = local
                } else {
                    repo.downloadFirstAidMedia(unitId, m.file!!).getOrNull()?.let { resolved[m.id!!] = it }
                }
            }
            mediaFiles = resolved
        }
    }

    BackHandler(enabled = stage != FaStage.HOME) { stage = FaStage.HOME }

    val pack = pkg
    if (pack == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(faBackground())
                .padding(horizontal = 16.dp)
        ) {
            FaScreenHeader(title = unitTitleOf(step, unitId) ?: unitId, onBack = onBack)
            if (loadFailed) {
                FaCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "تعذّر تحميل الوحدة — تحقق من الاتصال وحاول مجددًا.",
                            fontFamily = NotoSansArabicFontFamily, fontSize = 13.5.sp, color = faMuted()
                        )
                        Spacer(modifier = Modifier.height(13.dp))
                        FaPrimaryButton(text = "إعادة المحاولة") { loadAttempt++ }
                    }
                }
            } else {
                FaUnitLoadingSkeleton()
            }
        }
        return
    }

    val meta = pack.unit
    val title = meta?.titleAr ?: unitTitleOf(step, unitId) ?: unitId
    val flashcardCount = pack.flashcards.orEmpty().size

    fun wireWrong(item: FaQuizItem) {
        FirstAidMistakeWiring.recordWrong(
            context = context,
            file = unitId,
            subject = title,
            item = item,
            flashcardCount = flashcardCount
        )
    }

    when (stage) {
        FaStage.HOME -> FaUnitHome(
            step = step,
            isSession = isSession,
            pack = pack,
            title = title,
            index = index,
            integration = integration,
            progressTick = progressTick,
            unitTitleOf = unitTitleOf,
            onBack = onBack,
            onStage = { stage = it },
            onOpenUnit = onOpenUnit,
            onOpenBooklet = { booklet -> bookletOpener.openIn(scope, booklet) }
        )

        FaStage.PRECHECK -> FirstAidQuizPlayer(
            title = "اختبار المدخل",
            subtitle = title,
            items = pack.precheck.orEmpty().mapIndexed { i, q -> q.toFaQuizItem(i) },
            onWrong = { wireWrong(it) },
            onFinish = { _, _ ->
                FirstAidProgress.markPrecheckDone(context, unitId)
                progressTick++
            },
            onClose = { stage = FaStage.HOME }
        )

        FaStage.REFERENCE -> Column(
            modifier = Modifier.fillMaxSize().background(faBackground())
        ) {
            FaScreenHeader(title = "مرجع الوحدة", subtitle = title, onBack = { stage = FaStage.HOME })
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    FaCard(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                            FirstAidReferenceView(
                                markdown = pack.referenceMd.orEmpty(),
                                figureFile = { id -> mediaFiles[id] },
                                figureCaption = { id ->
                                    pack.media.orEmpty().firstOrNull { it.id == id }?.captionAr
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        FaStage.TRAINING -> FirstAidQuizPlayer(
            title = if (isSession) "جلسة التكامل" else "تدريب الوحدة",
            subtitle = title,
            items = pack.quiz.orEmpty().mapIndexed { i, q -> q.toFaQuizItem(i) },
            onWrong = { wireWrong(it) },
            onFinish = { _, _ ->
                FirstAidProgress.markTrainingDone(context, unitId)
                progressTick++
            },
            onClose = { stage = FaStage.HOME }
        )

        FaStage.EXIT -> FirstAidQuizPlayer(
            title = "اختبار الخروج",
            subtitle = "$title • ${FA_EXIT_TIME_SECONDS / 60} دقائق • النجاح من 60%",
            items = pack.exitcheck.orEmpty().mapIndexed { i, q -> q.toFaQuizItem(i) },
            timedSeconds = FA_EXIT_TIME_SECONDS,
            onWrong = { wireWrong(it) },
            onFinish = { correct, total ->
                FirstAidProgress.recordExit(context, unitId, correct, total)
                progressTick++
            },
            onClose = { stage = FaStage.HOME }
        )

        FaStage.INTERACTIVE -> {
            val interactive = pack.interactive
            if (interactive != null) {
                FirstAidInteractivePlayer(interactive = interactive, onClose = { stage = FaStage.HOME })
            } else {
                Column(modifier = Modifier.fillMaxSize().background(faBackground())) {
                    FaScreenHeader(title = "التفاعليات", subtitle = title, onBack = { stage = FaStage.HOME })
                    Text(
                        "لا تفاعليات في هذه الوحدة.",
                        fontFamily = NotoSansArabicFontFamily, fontSize = 13.sp, color = faMuted()
                    )
                }
            }
        }

        FaStage.FLASHCARDS -> FirstAidFlashcardsPlayer(
            title = "بطاقات الوحدة",
            cards = pack.flashcards.orEmpty().mapIndexed { i, card ->
                FaDeckCard(unitId, i, card.front.orEmpty(), card.back.orEmpty())
            },
            onClose = { stage = FaStage.HOME },
            onDeckFinished = {
                FirstAidProgress.markFlashcardsDone(context, unitId)
                progressTick++
            }
        )

        FaStage.GLOSSARY -> FirstAidGlossaryScreen(
            title = title,
            glossary = pack.glossary.orEmpty(),
            onClose = { stage = FaStage.HOME }
        )
    }
}

/**
 * First-open loading shape of the unit player (1.2.45). Before the
 * background warm-up existed, this state was a lone spinner on the
 * bare ivory field — the whole screen read as a washed-out blank page
 * for the full duration of the one genuine network fetch (device
 * report, 23 Nov 2026). The skeleton mirrors the unit home instead —
 * title block and the white stage card in the design's own track
 * colour — with a small spinner and caption, so the wait reads as the
 * unit arriving, not as an empty screen.
 */
@Composable
private fun FaUnitLoadingSkeleton() {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth(0.55f).height(15.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                .background(faTrack())
        )
        Spacer(modifier = Modifier.height(9.dp))
        Box(
            modifier = Modifier.fillMaxWidth(0.85f).height(24.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(faTrack())
        )
        FaCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                repeat(3) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(faTrack())
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(0.5f).height(13.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                    .background(faTrack())
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth(0.75f).height(10.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                                    .background(faTrack())
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = faAccentText(), modifier = Modifier.size(19.dp), strokeWidth = 2.2.dp
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                "جارٍ تحميل الوحدة…",
                fontFamily = NotoSansArabicFontFamily, fontSize = 12.5.sp, color = faMuted()
            )
        }
    }
}

// ---------------------------------------------------------------------
// 03-unit home: header + the step timeline + booklet button +
// flashcards card + integration links.
// ---------------------------------------------------------------------

@Composable
private fun FaUnitHome(
    step: Int,
    isSession: Boolean,
    pack: FirstAidUnitPackageDto,
    title: String,
    index: FirstAidIndexDto,
    integration: FirstAidIntegrationDto?,
    progressTick: Int,
    unitTitleOf: (Int, String) -> String?,
    onBack: () -> Unit,
    onStage: (FaStage) -> Unit,
    onOpenUnit: (Int, String, Boolean) -> Unit,
    onOpenBooklet: (FirstAidBookletDto) -> Unit
) {
    val context = LocalContext.current
    val meta = pack.unit
    val unitId = meta?.id ?: ""
    val srsChanges by FirstAidSrs.changes.collectAsState()
    val unitDue = remember(srsChanges, progressTick, unitId) {
        val keys = pack.flashcards.orEmpty().indices.map { FirstAidSrs.cardKey(unitId, it) }
        FirstAidSrs.dueCount(context, keys)
    }

    // Stage states from the account-bound progress store.
    val preDone = remember(progressTick) { FirstAidProgress.isPrecheckDone(context, unitId) }
    val trainDone = remember(progressTick) { FirstAidProgress.isTrainingDone(context, unitId) }
    val exitFraction = remember(progressTick) { FirstAidProgress.exitBestFraction(context, unitId) }
    val exitDone = exitFraction != null && exitFraction >= FirstAidProgressRules.EXIT_PASS_THRESHOLD

    val steps = mutableListOf<FaStepRowData>()
    if (isSession) {
        steps += FaStepRowData(
            FaStage.TRAINING, "اختبار الجلسة",
            "${pack.quiz.orEmpty().size} سؤالًا — جلسة تكامل للمفعّلين بالكود",
            if (trainDone) FaStepState.DONE else FaStepState.NOW,
            if (trainDone) "منجزة" else "ابدأ"
        )
    } else if (step == 2) {
        steps += FaStepRowData(
            FaStage.PRECHECK, "اختبار المدخل",
            "${pack.precheck.orEmpty().size} أسئلة تقيس جاهزيتك قبل بدء الوحدة",
            if (preDone) FaStepState.DONE else FaStepState.NOW,
            if (preDone) "منجز" else "ابدأ"
        )
        steps += FaStepRowData(
            FaStage.REFERENCE, "المرجع",
            "نص الدروس كاملًا مع صناديق «اشرحها ببساطة» وجداول المقارنة",
            FaStepState.NOW, "اقرأ"
        )
        steps += FaStepRowData(
            FaStage.TRAINING, "التدريب",
            "${pack.quiz.orEmpty().size} سؤالًا سريريًا بتغذية راجعة فورية",
            if (trainDone) FaStepState.DONE else FaStepState.NOW,
            if (trainDone) "منجز" else "ابدأ"
        )
        steps += FaStepRowData(
            FaStage.EXIT, "اختبار الخروج",
            // 1.2.38: the exit check is openable directly like every
            // stage (no hard locks); ≥60% stays the DEFINITION of
            // completing the unit, not an opening gate.
            "${pack.exitcheck.orEmpty().size} أسئلة · موقوت ${FA_EXIT_TIME_SECONDS / 60} دقائق · النجاح بـ60% فأكثر يُحسب إتمامًا للوحدة",
            when {
                exitDone -> FaStepState.DONE
                else -> FaStepState.NOW
            },
            when {
                exitDone -> "ناجح ${((exitFraction ?: 0.0) * 100).toInt()}%"
                else -> "ابدأ"
            }
        )
    } else {
        steps += FaStepRowData(
            FaStage.REFERENCE, "المرجع",
            "نص الدروس كاملًا مع صناديق «اشرحها ببساطة» والأشكال بأماكنها",
            FaStepState.NOW, "اقرأ"
        )
        steps += FaStepRowData(
            FaStage.TRAINING, "التدريب",
            "${pack.quiz.orEmpty().size} سؤالًا بتغذية راجعة فورية — إتمام التدريب يُحسب إتمامًا للوحدة، وكل المراحل قابلة للفتح مباشرة",
            if (trainDone) FaStepState.DONE else FaStepState.NOW,
            if (trainDone) "منجز" else "ابدأ"
        )
    }
    if (pack.interactive != null) {
        steps += FaStepRowData(
            FaStage.INTERACTIVE, "التفاعليات",
            "حالة متفرعة وتمارين ترتيب وجداول مقارنة",
            FaStepState.NOW, "العب"
        )
    }
    if (pack.glossary.orEmpty().isNotEmpty()) {
        steps += FaStepRowData(
            FaStage.GLOSSARY, "القاموس",
            "${pack.glossary.orEmpty().size} مصطلحًا بمقابله الإنجليزي وشرحه",
            FaStepState.NOW, "تصفح"
        )
    }

    val systemBooklet = remember(index, meta?.section) {
        bookletForSystem(index, meta?.section, meta?.sectionEn)
    }
    val links = integration?.byUnit?.get(unitId).orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(faBackground()),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            FaScreenHeader(
                title = meta?.sectionAr ?: "Step $step",
                subtitle = "Step $step" + (meta?.sourceSpan?.let { " · $it" } ?: ""),
                onBack = onBack
            )
            Text(
                meta?.sectionEn ?: "",
                fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp, color = FaColors.Gold
            )
            Text(
                title,
                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 19.5.sp, lineHeight = 29.sp, color = faHeading(),
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                FaChip(meta?.code ?: unitId, faChipCodeBg(), faCodeText())
                if (pack.flashcards.orEmpty().isNotEmpty()) {
                    FaChip("${pack.flashcards.orEmpty().size} بطاقة", faGoldTint(), faGoldText())
                }
                FaChip("تعمل دون اتصال", faGreenTint(), faGreenText())
            }
        }

        // The flow timeline card (.flow of 03-unit).
        item {
            FaCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 16.dp)) {
                    steps.forEachIndexed { i, row ->
                        FaFlowStep(
                            number = i + 1,
                            r = row,
                            last = i == steps.lastIndex,
                            onClick = { onStage(row.stage) }
                        )
                    }
                }
            }
        }

        // Booklet button (.pdfbtn): always available.
        if (systemBooklet != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, androidx.compose.foundation.shape.RoundedCornerShape(20.dp), clip = false)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(FaColors.MaroonMidBrush)
                        .clickable { onOpenBooklet(systemBooklet) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MenuBook, contentDescription = null,
                            tint = FaColors.GoldSoft, modifier = Modifier.size(21.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "كتيّب هذا النظام (PDF)",
                            fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp, color = Color.White
                        )
                        Text(
                            "متاح دائمًا بجانب المسار — يُنزَّل عند أول فتح ويُقرأ بقارئ التطبيق",
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 11.3.sp, color = FaColors.HeroTextSoft
                        )
                    }
                    Icon(
                        Icons.Filled.Download, contentDescription = null,
                        tint = FaColors.GoldSoft, modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Flashcards card (.flash of 03-unit).
        if (pack.flashcards.orEmpty().isNotEmpty()) {
            item {
                FaCard(modifier = Modifier.fillMaxWidth(), onClick = { onStage(FaStage.FLASHCARDS) }) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(13.dp))
                                .background(faGoldTint()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Style, contentDescription = null,
                                tint = faGoldText(), modifier = Modifier.size(21.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "بطاقات الوحدة",
                                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp, color = faHeading()
                            )
                            Text(
                                if (unitDue > 0) "$unitDue بطاقة مستحقة اليوم من هذه الوحدة"
                                else "مراجعة متباعدة بسلّم الكورس نفسه",
                                fontFamily = NotoSansArabicFontFamily,
                                fontSize = 11.3.sp, color = faMuted()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(11.dp))
                                .background(FaColors.Maroon)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "ابدأ", fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 12.3.sp, color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Integration links (.link of 03-unit): navigate to the linked
        // unit of the other book.
        if (links.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        "روابط التكامل — وحدات مرتبطة في الكتاب الآخر",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp, color = faHeading()
                    )
                    links.forEach { link ->
                        val targetId = link.targetUnit ?: return@forEach
                        val targetStep = link.targetStep ?: return@forEach
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(13.dp))
                                .background(faGoldTint())
                                .clickable { onOpenUnit(targetStep, targetId, false) }
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = null,
                                tint = FaColors.Gold, modifier = Modifier.size(17.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    link.topicAr ?: link.topicEn ?: targetId,
                                    fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                                    fontSize = 12.3.sp, color = faAccentText()
                                )
                                Text(
                                    "Step $targetStep — ${unitTitleOf(targetStep, targetId) ?: targetId}",
                                    fontFamily = NotoSansArabicFontFamily,
                                    fontSize = 11.3.sp, color = faMuted()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Watani topic tags (Step-2 tagging round, phase 2).
        val topics = pack.wataniTopics.orEmpty()
        if (topics.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        "مواضيع الوطني المرتبطة",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp, color = faHeading()
                    )
                    topics.forEach { topic ->
                        Text(
                            "• ${topic.subject ?: ""} — ${topic.topic ?: ""}",
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 12.sp, color = faMuted()
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun FaFlowStep(
    number: Int,
    r: FaStepRowData,
    last: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .then(
                        // .step.now .n in the design: maroon disc with
                        // a soft maroon ring shadow.
                        if (r.state == FaStepState.NOW) {
                            Modifier.shadow(6.dp, CircleShape, clip = false)
                        } else Modifier
                    )
                    .clip(CircleShape)
                    .background(
                        when (r.state) {
                            FaStepState.DONE -> FaColors.Green
                            FaStepState.NOW -> FaColors.Maroon
                            FaStepState.NEXT -> Color(0xFFF3E9E4)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (r.state) {
                    FaStepState.DONE -> Icon(
                        Icons.Filled.Check, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(18.dp)
                    )
                    else -> Text(
                        "$number",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = if (r.state == FaStepState.NOW) Color.White else FaColors.Maroon
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(top = 1.dp)) {
                Text(
                    r.label,
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = faHeading()
                )
                Text(
                    r.desc,
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 11.8.sp, lineHeight = 18.5.sp, color = faMuted()
                )
                if (r.barFraction != null) {
                    FaProgressBar(
                        fraction = r.barFraction,
                        modifier = Modifier.width(130.dp).padding(top = 7.dp)
                    )
                }
            }
            FaChip(
                r.stateLabel,
                bg = when (r.state) {
                    FaStepState.DONE -> faGreenTint()
                    FaStepState.NOW -> faChipCodeBg()
                    FaStepState.NEXT -> faChipCodeBg()
                },
                fg = when (r.state) {
                    FaStepState.DONE -> faGreenText()
                    FaStepState.NOW -> faCodeText()
                    FaStepState.NEXT -> faCodeText()
                },
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (!last) {
            Box(
                modifier = Modifier
                    .padding(start = 18.dp, top = 2.dp, bottom = 2.dp)
                    .width(2.dp)
                    .height(13.dp)
                    .background(Color(0xFFEFE3D3))
            )
        }
    }
}

/** Structural mirror of the step row built in FaUnitHome (kept as a
 * data class so the timeline helper stays decoupled). */
private data class FaStepRowData(
    val stage: FaStage,
    val label: String,
    val desc: String,
    val state: FaStepState,
    val stateLabel: String,
    val barFraction: Float? = null
)
