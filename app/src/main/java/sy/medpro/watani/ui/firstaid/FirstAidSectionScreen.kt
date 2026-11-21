package sy.medpro.watani.ui.firstaid

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sy.medpro.watani.data.FirstAidBanksDto
import sy.medpro.watani.data.FirstAidBookletDto
import sy.medpro.watani.data.FirstAidIndexDto
import sy.medpro.watani.data.FirstAidIntegrationDto
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.data.WorkerActivationApi
import sy.medpro.watani.ui.activation.ActivationPromptDialog
import sy.medpro.watani.ui.library.PdfViewerScreen
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily
import java.io.File

/** Section destinations (internal navigation of the First Aid tab). */
private sealed interface FaDest {
    data object Home : FaDest
    data class Path(val step: Int) : FaDest
    data class Unit(val step: Int, val unitId: String, val isSession: Boolean) : FaDest
    data object Integration : FaDest
    data object Banks : FaDest
    data class BankTest(val testIndex: Int) : FaDest
    data object DueReview : FaDest
    data class Pdf(val file: File, val title: String) : FaDest
}

/**
 * «First Aid - Med Pro» section (build 1.2.36, merge phases 5+6):
 * the approved design applied verbatim — 01-home (maroon hero with
 * the section progress, the two book paths, the gold integration
 * strip), 02-path per book, the unit player, the integration layer
 * (157 documented topic links + the INT/RAPI/FA2-INT sessions,
 * playable unlocked), the five printed banks (499 questions as
 * printed; the 13 unkeyed shown without auto-grading), the booklet
 * catalog (always available, download-on-first-open, read in the
 * existing PdfViewerScreen), and the flashcards due-today review.
 * All data flows through the phase-4 repository getters (cache-first
 * + silent refresh); progress from FirstAidProgress, card scheduling
 * from FirstAidSrs, wrong answers to MistakesBank.
 *
 * Build 1.2.38 (doctor's decisions): (أ) the home resume card and the
 * due counter derive from the student's OWN FirstAidProgress /
 * FirstAidSrs state — no fixed card for a student who never started;
 * (ب) the section is gated by the app's own activation
 * ([WorkerActivationApi.isActivated] + [ActivationPromptDialog]):
 * the first 3 path units of each step are free, everything else
 * (remaining units, integration sessions, printed banks, and the
 * booklets that follow gated units) needs an activated account;
 * (ج) no hard locks anywhere — order is advisory only.
 */
@Composable
fun FirstAidSectionScreen(onReaderFullscreenChange: ((Boolean) -> Unit)? = null) {
    val context = LocalContext.current
    val repo = remember { WataniRepository(context) }
    val scope = rememberCoroutineScope()

    var index1 by remember { mutableStateOf<FirstAidIndexDto?>(null) }
    var index2 by remember { mutableStateOf<FirstAidIndexDto?>(null) }
    var integration by remember { mutableStateOf<FirstAidIntegrationDto?>(null) }
    var banks by remember { mutableStateOf<FirstAidBanksDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    var stack by remember { mutableStateOf<List<FaDest>>(listOf(FaDest.Home)) }
    val dest = stack.last()
    fun push(d: FaDest) { stack = stack + d }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    val bookletOpener = rememberBookletOpener(repo) { file, title -> push(FaDest.Pdf(file, title)) }

    val srsChanges by FirstAidSrs.changes.collectAsState()
    val dueToday = remember(srsChanges) { FirstAidSrs.dueCountAll(context) }

    LaunchedEffect(Unit) {
        index1 = repo.getFirstAidIndex(1)
        index2 = repo.getFirstAidIndex(2)
        integration = repo.getFirstAidIntegration()
        banks = repo.getFirstAidBanks()
        failed = index1 == null && index2 == null
        loading = false
    }

    // The unit player registers its own BackHandler for its inner
    // stages (composed later, so it wins while a stage is open); this
    // host handler covers every other destination, including the unit
    // home itself.
    BackHandler(enabled = stack.size > 1) { pop() }

    fun indexOf(step: Int) = if (step == 1) index1 else index2
    fun unitTitleOf(step: Int, unitId: String): String? =
        indexOf(step)?.units.orEmpty().firstOrNull { it.id == unitId }?.titleAr
    fun isSessionId(step: Int, unitId: String): Boolean =
        indexOf(step)?.units.orEmpty().firstOrNull { it.id == unitId }?.kind == "integration"

    // ---- Activation gate (1.2.38, item ب) -------------------------------
    // The entitlement check is the app's own WorkerActivationApi —
    // the same one guarding the library and the other paid sections;
    // it reads the stored token + expiry, so an activated account
    // stays activated offline. The gate lives HERE, at the host's
    // navigation choke points, so every entry path (home cards, path
    // levels, integration screen, unit cross-links, banks, booklet
    // rows) passes through it exactly once.
    var activated by remember { mutableStateOf(WorkerActivationApi.isActivated(context)) }
    var showActivation by remember { mutableStateOf(false) }
    LaunchedEffect(dest) {
        // Re-read on every return Home (the student may have
        // activated from another section meanwhile).
        if (dest == FaDest.Home) {
            activated = WorkerActivationApi.isActivated(context)
        }
    }
    if (showActivation) {
        ActivationPromptDialog(
            onDismiss = { showActivation = false },
            onActivated = {
                activated = true
                showActivation = false
            }
        )
    }

    /** Path unit ids of [step] in course order (sessions excluded). */
    fun pathIdsOf(step: Int): List<String> =
        indexOf(step)?.units.orEmpty()
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .filter { it.kind != "integration" }
            .mapNotNull { it.id }

    fun openUnit(step: Int, id: String, sess: Boolean) {
        val isInt = isSessionId(step, id)
        if (FirstAidAccessRules.canOpenUnit(pathIdsOf(step), id, isInt, activated)) {
            // Record the opening for path units (incl. RAPI): this is
            // the trail the home resume card derives from (item أ).
            if (!isInt) FirstAidProgress.recordUnitOpened(context, step, id)
            push(FaDest.Unit(step, id, sess || isInt))
        } else {
            showActivation = true
        }
    }

    fun openBooklet(step: Int, booklet: FirstAidBookletDto) {
        val idx = indexOf(step)
        val systems = idx?.systems.orEmpty().map {
            FirstAidAccessRules.SystemInfo(it.code, it.sectionEn, it.units.orEmpty())
        }
        val gateUnit = FirstAidAccessRules.bookletGateUnitId(
            booklet.file, systems, pathIdsOf(step)
        )
        if (FirstAidAccessRules.canOpenBooklet(gateUnit, pathIdsOf(step), activated)) {
            bookletOpener.openIn(scope, booklet)
        } else {
            showActivation = true
        }
    }

    fun openBanks() {
        if (FirstAidAccessRules.canOpenBanks(activated)) push(FaDest.Banks)
        else showActivation = true
    }

    fun openBankTest(testIndex: Int) {
        if (FirstAidAccessRules.canOpenBanks(activated)) push(FaDest.BankTest(testIndex))
        else showActivation = true
    }

    Box(modifier = Modifier.fillMaxSize().background(faBackground())) {
        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FaColors.Maroon)
            }

            failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "تعذّر تحميل قسم First Aid — تحقق من الاتصال وحاول مجددًا.",
                    fontFamily = NotoSansArabicFontFamily,
                    modifier = Modifier.padding(24.dp)
                )
            }

            else -> {
                val current = dest
                if (current is FaDest.Pdf) {
                    // Booklet integrity gate (1.2.37): before the
                    // reader opens, the file must actually parse as a
                    // PDF (PdfRenderer, pageCount > 0, on IO). A file
                    // that fails is deleted and re-downloaded ONCE
                    // automatically (matched back to its index booklet
                    // by its local file); a second failure surfaces
                    // the REAL reason (exception class + message) —
                    // never a bare «check your connection».
                    var pdfGate by remember(current.file) { mutableStateOf(0) } // 0 checking, 1 ready, 2 failed
                    var pdfGateError by remember(current.file) { mutableStateOf<String?>(null) }
                    LaunchedEffect(current.file) {
                        pdfGate = 0
                        pdfGateError = null
                        val firstError = validateFaPdfFile(current.file)
                        if (firstError == null) {
                            pdfGate = 1
                        } else {
                            val booklet = (index1?.booklets.orEmpty() + index2?.booklets.orEmpty())
                                .firstOrNull { b ->
                                    !b.file.isNullOrBlank() &&
                                        repo.getFirstAidBookletFile("firstaid/${b.file}") == current.file
                                }
                            var secondError: String? = firstError
                            if (booklet?.file != null) {
                                try { current.file.delete() } catch (_: Exception) {}
                                val dl = repo.downloadFirstAidBooklet(
                                    "firstaid/${booklet.file}",
                                    booklet.size ?: 0L
                                )
                                secondError = dl.fold(
                                    onSuccess = { validateFaPdfFile(current.file) },
                                    onFailure = { e ->
                                        "${e.javaClass.simpleName}: ${e.message ?: "فشل إعادة تحميل الكتيّب"}"
                                    }
                                )
                            } else {
                                try { current.file.delete() } catch (_: Exception) {}
                            }
                            if (secondError == null) {
                                pdfGate = 1
                            } else {
                                pdfGateError = secondError
                                pdfGate = 2
                            }
                        }
                    }
                    when (pdfGate) {
                        1 -> {
                            // The PDF reader owns the whole window (never the
                            // width-governed reading column). Recents from
                            // inside the reader open as new Pdf destinations.
                            PdfViewerScreen(
                                pdfFile = current.file,
                                title = current.title,
                                onBack = { pop() },
                                // 1.2.38 (item هـ): the First Aid host now
                                // reports reader fullscreen exactly like
                                // the Library host, so the app scaffold
                                // hides its bars during reading here too.
                                onFullscreenChange = onReaderFullscreenChange,
                                onOpenRecent = { f, t -> push(FaDest.Pdf(f, t)) }
                            )
                        }
                        2 -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    "تعذّر فتح الكتيّب — ${pdfGateError ?: "خطأ غير معروف"}",
                                    fontFamily = NotoSansArabicFontFamily,
                                    fontSize = 13.5.sp, color = faHeading()
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(onClick = { pop() }) {
                                    Text("رجوع", fontFamily = NotoSansArabicFontFamily)
                                }
                            }
                        }
                        else -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = FaColors.Maroon)
                        }
                    }
                } else {
                FaReadingColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    when (current) {
                        FaDest.Home -> FaHome(
                            index1 = index1, index2 = index2,
                            integration = integration, banks = banks,
                            dueToday = dueToday,
                            activated = activated,
                            onOpenPath = { push(FaDest.Path(it)) },
                            onOpenUnit = { s, id, sess -> openUnit(s, id, sess) },
                            onOpenIntegration = { push(FaDest.Integration) },
                            onOpenBanks = { openBanks() },
                            onOpenDueReview = { push(FaDest.DueReview) },
                            onOpenBooklet = { s, booklet -> openBooklet(s, booklet) }
                        )

                        is FaDest.Path -> indexOf(current.step)?.let { idx ->
                            FirstAidPathScreen(
                                step = current.step, index = idx,
                                onBack = { pop() },
                                onOpenUnit = { id -> openUnit(current.step, id, false) },
                                onOpenBooklet = { booklet -> openBooklet(current.step, booklet) },
                                activated = activated,
                                freeUnitIds = FirstAidAccessRules.freePathUnitIds(pathIdsOf(current.step))
                            )
                        }

                        is FaDest.Unit -> indexOf(current.step)?.let { idx ->
                            FirstAidUnitPlayer(
                                step = current.step,
                                unitId = current.unitId,
                                isSession = current.isSession,
                                index = idx,
                                integration = integration,
                                unitTitleOf = { s, id -> unitTitleOf(s, id) },
                                onBack = { pop() },
                                onOpenUnit = { s, id, _ -> openUnit(s, id, false) },
                                onOpenBookletPdf = { file, title -> push(FaDest.Pdf(file, title)) }
                            )
                        }

                        FaDest.Integration -> FaIntegrationScreen(
                            integration = integration,
                            unitTitleOf = { s, id -> unitTitleOf(s, id) },
                            onBack = { pop() },
                            onOpenUnit = { s, id -> openUnit(s, id, true) },
                            onOpenLinkedUnit = { s, id -> openUnit(s, id, false) }
                        )

                        FaDest.Banks -> FaBanksList(
                            banks = banks,
                            onBack = { pop() },
                            onOpenTest = { openBankTest(it) }
                        )

                        is FaDest.BankTest -> {
                            val test = banks?.tests.orEmpty().getOrNull(current.testIndex)
                            if (test == null) {
                                FaScreenHeader(title = "بنوك الكتاب المطبوعة", onBack = { pop() })
                            } else {
                                FirstAidQuizPlayer(
                                    title = test.test ?: "اختبار مطبوع",
                                    subtitle = "بنوك Step 2 المطبوعة — صفحات ${test.sourcePages ?: "—"}",
                                    items = test.questions.orEmpty()
                                        .mapIndexed { i, q -> q.toFaQuizItem(i) },
                                    onWrong = { item ->
                                        FirstAidMistakeWiring.recordWrong(
                                            context = context,
                                            file = "banks_step2/${test.test ?: "test"}",
                                            subject = "بنوك Step 2 المطبوعة — ${test.test ?: ""}",
                                            item = item
                                        )
                                    },
                                    onClose = { pop() }
                                )
                            }
                        }

                        FaDest.DueReview -> FaDueReview(
                            repo = repo,
                            onBack = { pop() }
                        )

                        is FaDest.Pdf -> Unit // handled full-screen above
                    }
                }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// 01-home
// ---------------------------------------------------------------------

@Composable
private fun FaHome(
    index1: FirstAidIndexDto?,
    index2: FirstAidIndexDto?,
    integration: FirstAidIntegrationDto?,
    banks: FirstAidBanksDto?,
    dueToday: Int,
    activated: Boolean,
    onOpenPath: (Int) -> Unit,
    onOpenUnit: (Int, String, Boolean) -> Unit,
    onOpenIntegration: () -> Unit,
    onOpenBanks: () -> Unit,
    onOpenDueReview: () -> Unit,
    onOpenBooklet: (Int, sy.medpro.watani.data.FirstAidBookletDto) -> Unit
) {
    val context = LocalContext.current

    // Package stats (1.2.38, item D): the totals count EVERY unit in
    // the step's published index — path units AND integration
    // sessions — so the card matches the package on disk and the
    // index's own question totals (Step 1: 87 = 77 path + 10
    // sessions, 2,075 questions; Step 2: 67 = 61 + 6). In 1.2.37 the
    // card counted path units only (77) beside the package-wide
    // question total — the mismatch the doctor flagged.
    fun packageStats(step: Int, index: FirstAidIndexDto?): Pair<Int, Int> {
        val units = index?.units.orEmpty()
        val done = units.count { u ->
            u.id != null &&
                FirstAidProgress.isUnitDone(context, step, u.id!!, u.kind == "integration")
        }
        return done to units.size
    }
    fun pathState(step: Int, index: FirstAidIndexDto?): FirstAidAccessRules.PathState {
        val pathIds = index?.units.orEmpty()
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .filter { it.kind != "integration" }
            .mapNotNull { it.id }
        val done = pathIds.filterTo(HashSet()) {
            FirstAidProgress.isUnitDone(context, step, it)
        }
        return FirstAidAccessRules.PathState(pathIds, done)
    }
    val (done1, total1) = packageStats(1, index1)
    val (done2, total2) = packageStats(2, index2)
    val totalDone = done1 + done2
    val totalUnits = total1 + total2
    // Resume (1.2.38, item أ): derived from the student's OWN trail —
    // the last unit they actually opened (FirstAidProgress) plus
    // their done sets, via FirstAidAccessRules.resumeTarget. Root
    // cause of the 1.2.37 defect: the card showed the first undone
    // unit of the Step 2 path for EVERY student (no last-opened
    // record existed), so even a brand-new account saw a fixed
    // «تابع من حيث توقفت». Now a zero-progress student gets null
    // here and the start card below instead.
    val lastOpened = FirstAidProgress.lastOpened(context)
    val resume = FirstAidAccessRules.resumeTarget(
        lastOpened?.first, lastOpened?.second,
        pathState(1, index1), pathState(2, index2)
    )
    val linkCount = integration?.count ?: integration?.links?.size ?: 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }

        // Hero (.hero): maroon gradient, section progress + stats.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(26.dp), clip = false)
                    .clip(RoundedCornerShape(26.dp))
                    .background(FaColors.MaroonBrush)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    "قسم التعلّم التفاعلي",
                    fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp, color = FaColors.Gold
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "First Aid",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 27.sp, color = Color(0xFFFFF7EA)
                    )
                    Text(
                        " - Med Pro",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp, color = FaColors.GoldSoft,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    "كورس First Aid بمسارين مترابطين — أساسيات Step 1 وتطبيق Step 2، بترتيب الكتابين وطبقة تكامل بينهما.",
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 12.sp, lineHeight = 19.sp, color = FaColors.HeroTextSoft,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 11.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    val f = if (totalUnits == 0) 0f else totalDone.toFloat() / totalUnits
                    if (f > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(f)
                                .height(7.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(FaColors.GoldBrush)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    FaHeroStat("$totalDone", "وحدة منجزة", Modifier.weight(1f))
                    FaHeroStat("$dueToday", "بطاقة مستحقة اليوم", Modifier.weight(1f))
                    FaHeroStat("$linkCount", "رابط تكامل", Modifier.weight(1f))
                }
            }
        }

        // Resume card (.resume): the student's own stopping point —
        // shown only when they actually started something.
        if (resume != null) {
            val rStep = resume.step
            val rUnit = resume.unitId
            val rTitle = (if (rStep == 1) index1 else index2)?.units.orEmpty()
                .firstOrNull { it.id == rUnit }?.titleAr ?: rUnit
            item {
                FaCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenUnit(rStep, rUnit, false) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(41.dp)
                                .shadow(6.dp, CircleShape, clip = false)
                                .clip(CircleShape)
                                .background(FaColors.Maroon),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "تابع من حيث توقفت",
                                fontFamily = NotoSansArabicFontFamily,
                                fontSize = 11.3.sp, color = faMuted()
                            )
                            Text(
                                "Step $rStep — $rTitle",
                                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp, color = faHeading()
                            )
                        }
                    }
                }
            }
        }

        // Start card (1.2.38, item أ): a student with zero progress
        // gets NO resume card — this start card takes its place.
        if (resume == null && (index1 != null || index2 != null)) {
            item {
                FaCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenPath(1) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(41.dp)
                                .shadow(6.dp, CircleShape, clip = false)
                                .clip(CircleShape)
                                .background(FaColors.Maroon),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "ابدأ الكورس",
                                fontFamily = NotoSansArabicFontFamily,
                                fontSize = 11.3.sp, color = faMuted()
                            )
                            Text(
                                "ابدأ من Step 1 — الأساسيات بترتيب الكتاب المقترح",
                                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp, color = faHeading()
                            )
                        }
                    }
                }
            }
        }

        // The two paths (.paths / .path).
        item {
            Text(
                "المساران",
                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp, color = faHeading(),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (index1 != null) {
            item {
                FaPathCard(
                    badge = "S1", step = 1, index = index1!!,
                    done = done1, total = total1,
                    sub = "الأساسيات: كيمياء حيوية ومناعة وأحياء دقيقة وباثولوجيا وأدوية…",
                    gateNote = if (!activated) "أول 3 وحدات بالمسار مجانية — كل ما عداها بالتفعيل" else null,
                    onClick = { onOpenPath(1) }
                )
            }
        }
        if (index2 != null) {
            item {
                FaPathCard(
                    badge = "S2", step = 2, index = index2!!,
                    done = done2, total = total2,
                    sub = "التطبيق السريري: مدخل ومرجع وتدريب وخروج لكل وحدة",
                    gateNote = if (!activated) "أول 3 وحدات بالمسار مجانية — كل ما عداها بالتفعيل" else null,
                    onClick = { onOpenPath(2) }
                )
            }
        }

        // Integration strip (.integ).
        if (integration != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFFFBF4E2), FaColors.GoldTint)
                            )
                        )
                        .clickable { onOpenIntegration() }
                        .padding(horizontal = 15.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "طبقة التكامل بين الكتابين",
                            fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp, color = FaColors.GoldDeepText
                        )
                        Text(
                            "$linkCount رابط مواضيع موثّقًا + " +
                                "${integration.integrationSessions?.step1.orEmpty().size} جلسة Step 1 و" +
                                "${integration.integrationSessions?.step2.orEmpty().size} جلسات Step 2 — كلها قابلة للعب بلا قفل.",
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 11.3.sp, lineHeight = 18.sp, color = Color(0xFF97824F)
                        )
                    }
                    FaChip("افتح", Color.White, FaColors.GoldText)
                }
            }
        }

        // Due flashcards entry.
        if (dueToday > 0) {
            item {
                FaCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenDueReview) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "مراجعة البطاقات المستحقة",
                                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp, color = faHeading()
                            )
                            Text(
                                "$dueToday بطاقة مستحقة اليوم على سلّم تكرار الكورس",
                                fontFamily = NotoSansArabicFontFamily,
                                fontSize = 11.3.sp, color = faMuted()
                            )
                        }
                        FaChip("ابدأ المراجعة", FaColors.GoldTint, FaColors.GoldText)
                    }
                }
            }
        }

        // Printed banks entry.
        if (banks != null) {
            item {
                FaCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenBanks) {
                    Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
                        Text(
                            "بنوك الكتاب المطبوعة — Step 2",
                            fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp, color = faHeading()
                        )
                        Text(
                            "${banks.count ?: 499} سؤالًا كما هي مطبوعة في خمسة اختبارات (${banks.keyed ?: 486} بمفتاح مطبوع، ${banks.unkeyed ?: 13} بلا مفتاح تُعرض بلا تصحيح آلي)",
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 11.8.sp, lineHeight = 18.sp, color = faMuted()
                        )
                    }
                }
            }
        }

        // Booklet catalog: always available.
        val booklets = (index1?.booklets.orEmpty().map { 1 to it } +
            index2?.booklets.orEmpty().map { 2 to it })
        if (booklets.isNotEmpty()) {
            item {
                Text(
                    "الكتيّبات (PDF — متاحة دائمًا)",
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp, color = faHeading(),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(booklets.size) { i ->
                val (bStep, booklet) = booklets[i]
                BookletRow(step = bStep, booklet = booklet) { onOpenBooklet(bStep, booklet) }
            }
        }

        item { Spacer(modifier = Modifier.height(10.dp)) }
    }
}

@Composable
private fun FaHeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value, fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp, color = Color.White
        )
        Text(
            label, fontFamily = NotoSansArabicFontFamily,
            fontSize = 10.5.sp, color = Color(0xFFE4CFAF)
        )
    }
}

@Composable
private fun FaPathCard(
    badge: String,
    step: Int,
    index: FirstAidIndexDto,
    done: Int,
    total: Int,
    sub: String,
    gateNote: String? = null,
    onClick: () -> Unit
) {
    FaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (step == 1) FaColors.MaroonMidBrush else FaColors.GoldBrush),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badge, fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp, color = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (step == 1) "Step 1 — الأساسيات" else "Step 2 — التطبيق السريري",
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = faHeading()
                )
                Text(
                    sub, fontFamily = NotoSansArabicFontFamily,
                    fontSize = 11.8.sp, color = faMuted()
                )
                FaProgressBar(
                    fraction = if (total == 0) 0f else done.toFloat() / total,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$total وحدة · ${index.totals?.quiz ?: "—"} سؤال تدريب",
                        fontFamily = NotoSansArabicFontFamily, fontSize = 11.sp, color = faMuted()
                    )
                    Text(
                        "أُنجز $done",
                        fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = FaColors.Maroon
                    )
                }
                if (gateNote != null) {
                    Text(
                        gateNote,
                        fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp, color = FaColors.GoldText,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Integration layer screen: the 157 links + the sessions.
// ---------------------------------------------------------------------

@Composable
private fun FaIntegrationScreen(
    integration: FirstAidIntegrationDto?,
    unitTitleOf: (Int, String) -> String?,
    onBack: () -> Unit,
    onOpenUnit: (Int, String) -> Unit,
    onOpenLinkedUnit: (Int, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(faBackground())) {
        FaScreenHeader(
            title = "طبقة التكامل",
            subtitle = "روابط مواضيع موثّقة بين الكتابين وجلسات تكامل بلا قفل",
            onBack = onBack
        )
        if (integration == null) {
            Text(
                "تعذّر تحميل طبقة التكامل.",
                fontFamily = NotoSansArabicFontFamily, fontSize = 13.sp, color = faMuted(),
                modifier = Modifier.padding(top = 12.dp)
            )
            return
        }
        val sessions1 = integration.integrationSessions?.step1.orEmpty()
        val sessions2 = integration.integrationSessions?.step2.orEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (sessions1.isNotEmpty() || sessions2.isNotEmpty()) {
                item {
                    Text(
                        "جلسات التكامل (اختبار 25 سؤالًا — بلا قفل)",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp, color = faHeading(),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(sessions1.size) { i ->
                    val id = sessions1[i]
                    FaSessionRow("Step 1", id, unitTitleOf(1, id)) { onOpenUnit(1, id) }
                }
                items(sessions2.size) { i ->
                    val id = sessions2[i]
                    FaSessionRow("Step 2", id, unitTitleOf(2, id)) { onOpenUnit(2, id) }
                }
            }
            item {
                Text(
                    "روابط المواضيع (${integration.links.orEmpty().size})",
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp, color = faHeading(),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            val links = integration.links.orEmpty()
            items(links.size) { i ->
                val link = links[i]
                FaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                link.topicAr ?: link.topicEn ?: "",
                                fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, color = FaColors.Maroon,
                                modifier = Modifier.weight(1f)
                            )
                            if (link.crossSystem == true) {
                                FaChip("عابر للأنظمة", FaColors.GoldTint, FaColors.GoldText)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            link.step1Unit?.let { id ->
                                FaChip(
                                    "Step 1: ${unitTitleOf(1, id) ?: id}",
                                    FaColors.ChipCodeBg, FaColors.Maroon,
                                    modifier = Modifier.clickable { onOpenLinkedUnit(1, id) }
                                )
                            }
                            link.step2Unit?.let { id ->
                                FaChip(
                                    "Step 2: ${unitTitleOf(2, id) ?: id}",
                                    FaColors.GoldTint, FaColors.GoldText,
                                    modifier = Modifier.clickable { onOpenLinkedUnit(2, id) }
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun FaSessionRow(stepLabel: String, id: String, title: String?, onClick: () -> Unit) {
    FaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FaChip(stepLabel, FaColors.GoldTint, FaColors.GoldText)
            Text(
                title ?: id,
                fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 13.sp, color = faHeading(),
                modifier = Modifier.weight(1f)
            )
            Text(
                id, fontFamily = NotoSansArabicFontFamily,
                fontSize = 11.sp, color = faMuted()
            )
        }
    }
}

// ---------------------------------------------------------------------
// Printed banks list + due review.
// ---------------------------------------------------------------------

@Composable
private fun FaBanksList(
    banks: FirstAidBanksDto?,
    onBack: () -> Unit,
    onOpenTest: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(faBackground())) {
        FaScreenHeader(
            title = "بنوك الكتاب المطبوعة — Step 2",
            subtitle = "خمسة اختبارات كما هي مطبوعة — الأسئلة بلا مفتاح مطبوع تُعرض بلا تصحيح آلي",
            onBack = onBack
        )
        val tests = banks?.tests.orEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(tests.size) { i ->
                val test = tests[i]
                val unkeyed = test.questions.orEmpty().count { it.key.isNullOrBlank() }
                FaCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenTest(i) }) {
                    Column(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            test.test ?: "اختبار ${i + 1}",
                            fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp, color = faHeading()
                        )
                        Text(
                            "${test.questions.orEmpty().size} سؤالًا · صفحات ${test.sourcePages ?: "—"}" +
                                if (unkeyed > 0) " · $unkeyed بلا مفتاح مطبوع" else "",
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 11.8.sp, color = faMuted()
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

/** Due-today review: groups every due FirstAidSrs key by unit, loads
 * those units' packages, and plays the due cards as one deck. */
@Composable
private fun FaDueReview(
    repo: WataniRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var deck by remember { mutableStateOf<List<FaDeckCard>?>(null) }
    LaunchedEffect(Unit) {
        val dueKeys = FirstAidSrs.dueKeysAll(context)
        val byUnit = dueKeys.mapNotNull { key ->
            val unitId = key.substringBefore('#')
            val idx = key.substringAfter('#').toIntOrNull() ?: return@mapNotNull null
            unitId to idx
        }.groupBy({ it.first }, { it.second })
        val cards = mutableListOf<FaDeckCard>()
        for ((unitId, indices) in byUnit) {
            val step = if (unitId.startsWith("FA2-")) 2 else 1
            val pack = repo.getFirstAidUnit(step, unitId) ?: continue
            val flashcards = pack.flashcards.orEmpty()
            for (i in indices.sorted()) {
                val card = flashcards.getOrNull(i) ?: continue
                cards += FaDeckCard(unitId, i, card.front.orEmpty(), card.back.orEmpty())
            }
        }
        deck = cards
    }
    val cards = deck
    if (cards == null) {
        Column(modifier = Modifier.fillMaxSize().background(faBackground())) {
            FaScreenHeader(title = "مراجعة المستحق اليوم", onBack = onBack)
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = FaColors.Maroon) }
        }
    } else {
        FirstAidFlashcardsPlayer(
            title = "مراجعة المستحق اليوم",
            cards = cards,
            onClose = onBack
        )
    }
}

/**
 * Booklet integrity probe (1.2.37): opens [file] with PdfRenderer on
 * the IO dispatcher and returns null only when it parses with at
 * least one page. Any failure returns the REAL reason as
 * "ExceptionClass: message" for the caller's error path. Every
 * handle (renderer + descriptor) is closed on every path.
 */
private suspend fun validateFaPdfFile(file: File): String? = withContext(Dispatchers.IO) {
    var fd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
        if (!file.exists() || file.length() == 0L) {
            return@withContext "الملف غير موجود على الجهاز أو فارغ"
        }
        fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(fd)
        if (renderer.pageCount > 0) {
            null
        } else {
            "PdfRenderer: الملف بلا صفحات (pageCount = 0)"
        }
    } catch (e: Exception) {
        "${e.javaClass.simpleName}: ${e.message ?: "خطأ غير معروف أثناء فتح ملف PDF"}"
    } finally {
        try { renderer?.close() } catch (_: Exception) {}
        try { fd?.close() } catch (_: Exception) {}
    }
}
