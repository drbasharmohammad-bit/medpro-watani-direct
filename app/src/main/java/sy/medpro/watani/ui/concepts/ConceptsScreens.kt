package sy.medpro.watani.ui.concepts

import sy.medpro.watani.ui.PremiumListSkeleton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.ConceptDiffTableDto
import sy.medpro.watani.data.ConceptSelfTestDto
import sy.medpro.watani.data.ConceptUnitDto
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.data.conceptUnitExamOrder
import sy.medpro.watani.data.groupConceptsBySubject
import sy.medpro.watani.data.resolveSelfTestCorrectIndex
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.*

/**
 * «ادرس مع ميد برو» (build 1.2.13, doctor's order 14 Nov 2026) —
 * a fixed concept curriculum INSIDE the Library tab: subjects ->
 * topics -> one concept unit page. Since build 1.2.14 the Library
 * opens on parallel section entries and this section is one entry
 * beside «الفيديوهات»; subjects and topics order watani-first
 * (national-exam counts lead, damascus breaks ties). Content is the
 * server pack concepts.json, loaded exactly like dosing_drills.json
 * (downloaded section first, manifest + downloadSection on first
 * use, silent refresh when the manifest moved on) — but with NO
 * bundled fallback: the section's CONTENT exists only once a real
 * pack is downloaded. Build 1.2.21 (root-cause fix): the Library
 * card itself now renders in EVERY state (entry / download /
 * «could not verify» — see conceptsCardKind in the data layer);
 * the pre-1.2.21 rule «renders nothing for an empty pack» is what
 * made the whole section vanish whenever the first download had
 * not completed, and it is gone.
 */

data class ConceptUnitsState(
    val units: List<ConceptUnitDto> = emptyList(),
    val loaded: Boolean = false,
    // Item Z (21 Nov 2026): renderable counts, seeded SYNCHRONOUSLY
    // from the persisted pack summary (see
    // WataniRepository.conceptsInstalledSummary) so the Library
    // entry card renders on the first frame; the background pack
    // load then replaces them with the parsed pack's own counts.
    val unitCount: Int = 0,
    val subjectCount: Int = 0,
    // Build 1.2.17: what the server currently offers (manifest count)
    // and whether a newer pack than the on-device one exists — the
    // Library renders the explicit download card / «new topics» badge
    // from these, so additions are a content publish the student
    // pulls on demand (doctor's order), never a new APK.
    val serverCount: Int? = null,
    val updateAvailable: Boolean = false,
    // Build 1.2.21: whether the last server check actually READ a
    // manifest carrying a concepts entry. False means «the check
    // itself failed» (offline, no cached manifest) — the Library
    // shows the explicit «تعذّر التحقق» card then, never silence.
    // A failed re-check must never clear a previously known value:
    // [rememberConceptUnits] preserves the last known status.
    val serverKnown: Boolean = false
) {
    /** A pack is installed when the parsed units are in hand OR the
     * persisted summary already proves one (item Z). */
    val hasPack: Boolean get() = units.isNotEmpty() || unitCount > 0
}

/**
 * Loads the downloaded concepts pack state (build 1.2.21 rework —
 * root-cause fix, see invest_concepts.md):
 *
 * 1. The state is assigned IMMEDIATELY from the local cache
 *    (getConcepts reads only the encrypted on-device pack — no
 *    network), so an installed pack renders and browses fully
 *    offline from the first frame.
 * 2. The server status comes from the LIGHT manifest check alone
 *    (conceptsServerStatusDetailed) and is merged in afterwards.
 *    NO download is started here: the old effect downloaded the
 *    whole 5.2MB pack silently before assigning ANY state, so the
 *    download card's own visibility condition (serverCount != null)
 *    could only become true after the download it was meant to
 *    start had already finished — and any cancellation (leaving
 *    the tab, ON_RESUME, process death) left the section invisible
 *    forever. Downloads now start ONLY from the Library card
 *    button, through ConceptsPackManager, which outlives the
 *    screen exactly like the bank downloads outlive theirs.
 * 3. A failed step preserves the last known values (units,
 *    serverCount, serverKnown) instead of resetting them — the old
 *    catch rebuilt the state WITHOUT serverCount and silently
 *    un-rendered the download card.
 *
 * [refreshKey] bumps (pull-to-refresh, ON_RESUME) therefore only
 * re-read the cache and re-check the manifest: they update the
 * «new topics» badge/status and can never cancel or restart a
 * running pack download.
 */
@Composable
fun rememberConceptUnits(refreshKey: Int = 0): ConceptUnitsState {
    val context = LocalContext.current
    // Item Z (21 Nov 2026): the initial state is seeded from the
    // persisted pack summary — synchronously, during composition —
    // so an installed pack's entry card renders on the FIRST frame
    // with its true counts, exactly like the videos card. The full
    // pack load below then fills in the unit list (the parse itself
    // is now process-cached in WataniRepository, so it is paid once
    // per process, not once per screen).
    var state by remember {
        mutableStateOf(
            WataniRepository.conceptsInstalledSummary(context)?.let { summary ->
                ConceptUnitsState(
                    loaded = true,
                    unitCount = summary.unitCount,
                    subjectCount = summary.subjectCount
                )
            } ?: ConceptUnitsState()
        )
    }
    LaunchedEffect(refreshKey) {
        val repo = WataniRepository(context)
        // (1) Cache first, assigned at once — offline browsing of an
        // installed pack never waits for (or needs) the network.
        // The parsed pack is authoritative for the counts: a pack
        // that no longer parses/reads resets them to zero, so a
        // stale summary can never leave a phantom card behind.
        try {
            val cachedUnits = repo.getConcepts()?.units.orEmpty()
                .filter { !it.id.isNullOrBlank() }
            state = state.copy(
                units = cachedUnits,
                loaded = true,
                unitCount = cachedUnits.size,
                subjectCount = groupConceptsBySubject(cachedUnits).size
            )
        } catch (_: Exception) {
            state = state.copy(loaded = true)
        }
        // (2) Light manifest check only; merge, never reset. Build
        // 1.2.17 (doctor's order): a republished pack is NOT swapped
        // in silently — the state only REPORTS that an addition
        // exists, and the Library entry card shows the «new topics»
        // badge with an explicit update button («يجيهم إشعار: حمّلوا
        // هاي الزيادة»). The on-device pack keeps working fully
        // offline meanwhile.
        try {
            val status = repo.conceptsServerStatusDetailed()
            state = if (status.known) {
                state.copy(
                    serverCount = status.serverCount,
                    serverKnown = true,
                    updateAvailable = status.updateAvailable &&
                        state.hasPack
                )
            } else {
                // Check failed: keep the last known serverCount /
                // serverKnown exactly as they were.
                state.copy(loaded = true)
            }
        } catch (_: Exception) {
            // Same rule: a failed re-check erases nothing.
            state = state.copy(loaded = true)
        }
    }
    return state
}

/**
 * The Library entry card for «ادرس مع ميد برو» — rendered when a
 * downloaded pack with units exists on the device (the ENTRY branch
 * of conceptsCardKind). Since build 1.2.21 the caller always renders
 * ONE of the three concepts cards; this one is the installed-pack
 * branch.
 */
// LongParameterList/LongMethod suppressed deliberately (audit
// fix-loop, 21 Nov 2026): the parameters are the card's display
// state — each one an independent input the Library screen already
// holds (pack counts, update-offer state, progress) — and the body
// is one cohesive visual unit in the library design language.
// Splitting either for a style counter would scatter the card.
@Suppress("LongParameterList", "LongMethod")
@Composable
fun ConceptsEntryCard(
    // Item Z (21 Nov 2026): the card renders COUNTS, not the pack —
    // it used to take the whole parsed unit list just to print two
    // numbers, which chained its first render to the full 11.9MB
    // parse. Callers pass ConceptUnitsState's counts (seeded from
    // the persisted summary, then confirmed by the parse).
    unitCount: Int,
    subjectCount: Int,
    onClick: () -> Unit,
    // Build 1.2.17: «new topics» notice (doctor's order) — rendered
    // only when the server pack is newer than the on-device one; the
    // button pulls the addition on demand (content only, no APK).
    updateAvailable: Boolean = false,
    serverCount: Int? = null,
    updating: Boolean = false,
    updateProgress: Float = 0f,
    onUpdateClick: (() -> Unit)? = null
) {
    val subjectsCount = subjectCount
    // Items 4+5 migration: LibCard surface (white, one soft shadow,
    // no border) — foreground follows the library language (maroon
    // heading, muted body, gold accents) instead of white-on-maroon.
    sy.medpro.watani.ui.library.LibCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 20.dp,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = sy.medpro.watani.ui.library.LibColors.Gold,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ادرس مع ميد برو",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = sy.medpro.watani.ui.library.libHeading()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "شرح المفاهيم الأهم في الدورات — $unitCount موضوعًا في $subjectsCount مادة، مرتّبة بأسئلة الوطني أولًا",
                        fontSize = 12.sp,
                        color = sy.medpro.watani.ui.library.libMuted(),
                        lineHeight = 17.sp
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = sy.medpro.watani.ui.library.LibColors.Gold,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (updateAvailable && onUpdateClick != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = sy.medpro.watani.ui.library.LibColors.Gold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (serverCount != null && serverCount > unitCount)
                            "أُضيفت مواضيع جديدة — صار المحتوى $serverCount موضوعًا"
                        else
                            "توجد إضافة جديدة للمحتوى",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = sy.medpro.watani.ui.library.libHeading(),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onUpdateClick,
                        enabled = !updating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = MaroonDeep
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (updating) "جارٍ التحديث..." else "حدّث الآن", fontSize = 13.sp)
                    }
                }
                if (updating) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { updateProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = GoldAccent
                    )
                }
            }
        }
    }
}

/**
 * The «ادرس مع ميد برو» download card (build 1.2.17, doctor's
 * order): rendered in the Library entries when the server offers a
 * concepts pack but nothing is on the device yet (e.g. the first
 * automatic fetch failed on a weak connection). One tap downloads
 * the whole pack once; afterwards the section works fully offline.
 */
@Composable
fun ConceptsDownloadCard(
    serverCount: Int,
    downloading: Boolean,
    progress: Float,
    failed: Boolean,
    onDownloadClick: () -> Unit
) {
    // Items 4+5 migration: LibCard surface, library foreground.
    sy.medpro.watani.ui.library.LibCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 20.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = sy.medpro.watani.ui.library.LibColors.Gold,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ادرس مع ميد برو",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = sy.medpro.watani.ui.library.libHeading()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "شرح المفاهيم الأهم في الدورات — $serverCount موضوعًا. حمّلها مرة واحدة وادرسها بعدها بلا إنترنت.",
                        fontSize = 12.sp,
                        color = sy.medpro.watani.ui.library.libMuted(),
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDownloadClick,
                enabled = !downloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = MaroonDeep
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (downloading) "جارٍ تحميل المواضيع..." else "تحميل المواضيع ($serverCount)", fontSize = 14.sp)
            }
            if (downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = GoldAccent
                )
            }
            if (failed && !downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "تعذّر التحميل — تحقق من الاتصال وحاول مجددًا.",
                    fontSize = 12.sp,
                    color = sy.medpro.watani.ui.library.libMuted()
                )
            }
        }
    }
}

/**
 * The «ادرس مع ميد برو» unverified card (build 1.2.21): rendered
 * when no pack is on the device AND the light server check could
 * not be completed (offline with no cached manifest, fetch error).
 * Before this card existed, that state rendered NOTHING — the
 * section silently did not exist in the Library, which is exactly
 * the doctor's complaint. The button re-runs the check; once the
 * server is reachable this card is replaced by the download card.
 */
@Composable
fun ConceptsUnverifiedCard(
    onRetryClick: () -> Unit
) {
    // Items 4+5 migration: LibCard surface, library foreground.
    sy.medpro.watani.ui.library.LibCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 20.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = sy.medpro.watani.ui.library.LibColors.Gold,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ادرس مع ميد برو",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = sy.medpro.watani.ui.library.libHeading()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "شرح المفاهيم الأهم في الدورات — تعذّر التحقق من توفر المواضيع حاليًا. تحقق من الاتصال وأعد المحاولة.",
                        fontSize = 12.sp,
                        color = sy.medpro.watani.ui.library.libMuted(),
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRetryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = MaroonDeep
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("إعادة التحقق", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun ConceptsSubjectsScreen(
    units: List<ConceptUnitDto>,
    onSubjectClick: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val groups = remember(units) { groupConceptsBySubject(units) }
    // 1.2.31: saveable list state — leaving for a bank question and
    // coming back restores this list's exact scroll spot instead of
    // restarting it at the top (the route itself was already
    // saveable in the Library; the scroll was not).
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    // Item ح (21 Nov 2026, doctor's order): this screen — and the
    // topics list below — now speak the LIBRARY's approved design
    // language (the 1.2.37 design the doctor signed off): the same
    // two-column subject grid, LibCard clean cards with one soft
    // shadow and no borders, the 42dp gold subject tile with the
    // app's own subject icons, Kufi headings / Noto Sans body, bold
    // maroon counts, and the gold bar. Content and information are
    // UNCHANGED: same groups, same order, same three counts; the
    // gold bar visualizes the subject's watani weight relative to
    // the heaviest subject (the very number the list is sorted by).
    val maxWatani = remember(groups) { groups.maxOfOrNull { it.totalWatani } ?: 0 }
    DrawerScreenScaffold(title = "ادرس مع ميد برو", onBack = onBack) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(sy.medpro.watani.ui.library.libBackground())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "المواد مرتّبة بمجموع أسئلة الوطني في مواضيعها — الأعلى أولًا.",
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 12.5.sp,
                    color = sy.medpro.watani.ui.library.libMuted()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(groups.chunked(2), key = { it.first().subject }) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { group ->
                        ConceptSubjectGridCard(
                            group = group,
                            maxWatani = maxWatani,
                            modifier = Modifier.weight(1f),
                            onClick = { onSubjectClick(group.subject) }
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** The counts line of a concepts subject card, in the library's
 * meta style (bold maroon numbers, muted words). */
private fun conceptSubjectMeta(
    group: sy.medpro.watani.data.ConceptSubjectGroup
): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    val num = SpanStyle(
        color = sy.medpro.watani.ui.library.LibColors.Maroon,
        fontWeight = FontWeight.Bold
    )
    withStyle(num) { append(group.units.size.toString()) }
    append(" موضوعًا")
    append("  ·  ")
    withStyle(num) { append(group.totalWatani.toString()) }
    append(" وطني")
    append("  ·  ")
    withStyle(num) { append(group.totalDamascus.toString()) }
    append(" دمشق")
}

/** One subject card of the concepts grid — the library's
 * SubjectGridCard shape, carrying concept counts instead of video
 * counts (item ح). */
@Composable
private fun ConceptSubjectGridCard(
    group: sy.medpro.watani.data.ConceptSubjectGroup,
    maxWatani: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    sy.medpro.watani.ui.library.LibCard(modifier = modifier, radius = 20.dp, onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(sy.medpro.watani.ui.library.libGoldTint()),
                    contentAlignment = Alignment.Center
                ) {
                    if (sy.medpro.watani.ui.icons.librarySubjectIconRes(null, group.subject) != null) {
                        sy.medpro.watani.ui.icons.LibrarySubjectIcon(null, group.subject, Modifier.size(30.dp))
                    } else {
                        Text(
                            group.subject.trim().firstOrNull()?.toString() ?: "؟",
                            fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 17.sp, color = sy.medpro.watani.ui.library.LibColors.Maroon
                        )
                    }
                }
                Text(
                    group.subject,
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = sy.medpro.watani.ui.library.libInk(), lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                conceptSubjectMeta(group),
                fontFamily = NotoSansArabicFontFamily,
                fontSize = 12.sp, color = sy.medpro.watani.ui.library.libMuted(),
                modifier = Modifier.padding(top = 8.dp)
            )
            sy.medpro.watani.ui.library.LibGoldBar(
                fraction = if (maxWatani > 0) group.totalWatani.toFloat() / maxWatani else 0f,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "وزن المادة في أسئلة الوطني",
                fontFamily = NotoSansArabicFontFamily,
                fontSize = 10.5.sp, color = sy.medpro.watani.ui.library.libBarLabel(),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun ConceptsTopicsScreen(
    subject: String,
    units: List<ConceptUnitDto>,
    onUnitClick: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val topics = remember(units, subject) {
        units.filter { it.subject?.trim() == subject }
            .sortedWith(conceptUnitExamOrder)
    }
    // 1.2.31: saveable scroll, same rule as the subjects list.
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    // Item ح (21 Nov 2026): topics rows in the library language —
    // LibCard rows with the gold rank tile (the list is ordered, so
    // the tile carries the topic's rank, the same role the letter
    // mark plays on library cards), Kufi titles, bold-maroon counts,
    // and the weight as a LibChip. Same topics, same order, same
    // numbers — presentation only.
    DrawerScreenScaffold(title = subject, onBack = onBack) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(sy.medpro.watani.ui.library.libBackground())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "المواضيع مرتّبة بعدد أسئلة الوطني — الأعلى أولًا.",
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 12.5.sp,
                    color = sy.medpro.watani.ui.library.libMuted()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(topics.size, key = { idx -> topics[idx].id ?: topics[idx].topic ?: idx }) { idx ->
                val unit = topics[idx]
                sy.medpro.watani.ui.library.LibCard(
                    modifier = Modifier.fillMaxWidth(),
                    radius = 20.dp,
                    onClick = { unit.id?.let(onUnitClick) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(sy.medpro.watani.ui.library.libGoldTint()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (idx + 1).toString(),
                                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, color = sy.medpro.watani.ui.library.LibColors.Maroon
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                unit.topic ?: "",
                                fontFamily = NotoKufiFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = sy.medpro.watani.ui.library.libInk(),
                                lineHeight = 20.sp
                            )
                            val counts = listOfNotNull(
                                unit.watani?.let { "وطني: $it" },
                                unit.damascus?.let { "دمشق: $it" }
                            )
                            if (counts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    counts.joinToString(" • ") + " سؤالًا",
                                    fontFamily = NotoSansArabicFontFamily,
                                    fontSize = 12.sp,
                                    color = sy.medpro.watani.ui.library.libMuted()
                                )
                            }
                        }
                        sy.medpro.watani.ui.library.LibChip(
                            text = "الوزن ${unit.weight ?: 0}",
                            pdfStyle = true
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = sy.medpro.watani.ui.library.LibColors.Chevron,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConceptUnitScreen(
    unitId: String,
    onBack: () -> Unit,
    onOpenQuestionById: (String) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val loadState = rememberConceptUnits()
    val unit = remember(loadState.units, unitId) {
        loadState.units.firstOrNull { it.id == unitId }
    }

    // «افتح السؤال في البنك» is shown ONLY for self-test questions
    // actually found in a downloaded bank on this device.
    var bankQids by remember(unitId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(unit?.id) {
        val selfTests = unit?.selfTest.orEmpty()
        if (selfTests.isEmpty()) return@LaunchedEffect
        try {
            val repo = WataniRepository(context)
            // 1.2.31: ONE batch lookup through the repository's
            // question-id index. The old loop called findQuestionById
            // per self-test, and each call rescanned every downloaded
            // shard from the top — the unit page's buttons took
            // seconds to appear on a big install.
            val qids = selfTests.mapNotNull {
                it.qid?.trim()?.takeIf { qid -> qid.isNotEmpty() }
            }
            bankQids = repo.findQuestionsByIds(qids).keys
        } catch (_: Exception) {
            // Lookup failure hides the buttons — never a dead button.
        }
    }

    DrawerScreenScaffold(title = unit?.topic ?: "ادرس مع ميد برو", onBack = onBack) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when {
                unit != null -> ConceptUnitContent(
                    unit = unit,
                    bankQids = bankQids,
                    onOpenQuestionById = onOpenQuestionById
                )
                loadState.loaded -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "هذه الوحدة غير متاحة على الجهاز حاليًا.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                else -> PremiumListSkeleton(caption = "جاري تحميل الوحدة...")
            }
        }
    }
}

@Composable
private fun ConceptUnitContent(
    unit: ConceptUnitDto,
    bankQids: Set<String>,
    onOpenQuestionById: (String) -> Unit
) {
    // 1.2.31: the unit page's scroll is saveable — the doctor's
    // exact complaint: open the source question, come back, and
    // land where you were on THIS page, not at its top.
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header: subject + counts.
        item {
            // Items 4+5 migration: LibCard surface, library foreground.
            sy.medpro.watani.ui.library.LibCard(
                modifier = Modifier.fillMaxWidth(),
                radius = 20.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        unit.subject ?: "",
                        color = sy.medpro.watani.ui.library.LibColors.GoldText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        unit.topic ?: "",
                        color = sy.medpro.watani.ui.library.libHeading(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    val meta = listOfNotNull(
                        unit.weight?.let { "الوزن في الدورات: $it" },
                        unit.watani?.let { "أسئلة الوطني: $it" },
                        unit.damascus?.let { "أسئلة دمشق: $it" }
                    )
                    if (meta.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            meta.joinToString(" • "),
                            color = sy.medpro.watani.ui.library.libMuted(),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 1) شرح الفكرة — explanation sections in pack order.
        val sections = unit.explanation.orEmpty()
            .filter { !it.h.isNullOrBlank() || !it.body.isNullOrBlank() }
        if (sections.isNotEmpty()) {
            item { ConceptBlockTitle("شرح الفكرة", Icons.Default.MenuBook) }
            items(sections) { section ->
                Column {
                    if (!section.h.isNullOrBlank()) {
                        Text(
                            section.h,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaroonPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (!section.body.isNullOrBlank()) {
                        ConceptRichText(section.body)
                    }
                }
            }
        }

        // 2) أفخاخ الدورات.
        val traps = unit.traps.orEmpty().filter { it.isNotBlank() }
        if (traps.isNotEmpty()) {
            item { ConceptBlockTitle("أفخاخ الدورات", Icons.Default.Warning) }
            items(traps) { trap ->
                Surface(
                    color = ErrorRed.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ConceptRichText(trap, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 3) اختبر نفسك.
        val selfTests = unit.selfTest.orEmpty()
            .filter { !it.text.isNullOrBlank() && !it.options.isNullOrEmpty() }
        if (selfTests.isNotEmpty()) {
            item { ConceptBlockTitle("اختبر نفسك", Icons.Default.Quiz) }
            items(selfTests, key = { it.qid ?: it.text ?: "" }) { test ->
                ConceptSelfTestCard(
                    test = test,
                    bankAvailable = bankQids.contains(test.qid?.trim() ?: ""),
                    onOpenQuestionById = onOpenQuestionById
                )
            }
        }

        // 4) الجدول الفارق.
        val diffTable = unit.diffTable
        if (diffTable != null &&
            !diffTable.columns.isNullOrEmpty() && !diffTable.rows.isNullOrEmpty()
        ) {
            item { ConceptBlockTitle("الجدول الفارق", Icons.Default.TableChart) }
            item { ConceptDiffTable(table = diffTable) }
        }

        // 5) ربط بين المواد.
        val crossLinks = unit.crossLinks.orEmpty()
            .filter { !it.text.isNullOrBlank() || !it.subject.isNullOrBlank() }
        if (crossLinks.isNotEmpty()) {
            item { ConceptBlockTitle("ربط بين المواد", Icons.Default.Link) }
            items(crossLinks) { link ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (!link.subject.isNullOrBlank()) {
                            Text(
                                link.subject,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaroonPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        if (!link.text.isNullOrBlank()) {
                            ConceptRichText(link.text)
                        }
                    }
                }
            }
        }

        // 6) حيلة الذاكرة — the hook is set apart from the body.
        val mnemonic = unit.mnemonic
        if (mnemonic != null && (!mnemonic.body.isNullOrBlank() || !mnemonic.hook.isNullOrBlank())) {
            item { ConceptBlockTitle("حيلة الذاكرة", Icons.Default.Lightbulb) }
            item {
                // Items 4+5 migration: LibCard surface, library foreground.
                sy.medpro.watani.ui.library.LibCard(
                    modifier = Modifier.fillMaxWidth(),
                    radius = 20.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (!mnemonic.body.isNullOrBlank()) {
                            Text(
                                mnemonic.body,
                                color = sy.medpro.watani.ui.library.libInk(),
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
                        }
                        if (!mnemonic.hook.isNullOrBlank()) {
                            if (!mnemonic.body.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            Surface(
                                color = GoldAccent.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = sy.medpro.watani.ui.library.LibColors.GoldText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        mnemonic.hook,
                                        color = sy.medpro.watani.ui.library.LibColors.GoldText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConceptBlockTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
    }
}

@Composable
private fun ConceptSelfTestCard(
    test: ConceptSelfTestDto,
    bankAvailable: Boolean,
    onOpenQuestionById: (String) -> Unit
) {
    val options = test.options.orEmpty()
    val correctIndex = remember(test.correctIndex, options.size) {
        resolveSelfTestCorrectIndex(test.correctIndex, options.size)
    }
    // 1.2.31: saveable — a revealed answer stays revealed across
    // the trip to the bank question and back (was plain remember,
    // so the whole card reset on return).
    var revealed by rememberSaveable(test.qid ?: "", test.text ?: "") { mutableStateOf(false) }

    // Items 4+5 migration: LibCard surface (white, soft shadow, no
    // border). Option rows keep a thin functional line border —
    // libLine() normally, the semantic green only once the
    // correct answer is revealed.
    sy.medpro.watani.ui.library.LibCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 20.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!test.bank.isNullOrBlank()) {
                Surface(
                    color = MaroonPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (test.bank == "watani") "من بنك الوطني" else "من بنك دمشق",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaroonPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            ConceptRichText(test.text ?: "", fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(10.dp))
            options.forEachIndexed { idx, option ->
                val isCorrect = revealed && idx == correctIndex
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCorrect) {
                        SuccessGreen.copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isCorrect) SuccessGreen
                        else sy.medpro.watani.ui.library.libLine()
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            option,
                            fontSize = 13.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCorrect) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (!revealed) {
                OutlinedButton(
                    onClick = { revealed = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, sy.medpro.watani.ui.library.libLine()),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaroonPrimary)
                ) {
                    Text("إظهار الجواب", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                if (correctIndex != null) {
                    Text(
                        "الإجابة الصحيحة: ${options.getOrNull(correctIndex) ?: ""}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = SuccessGreen
                    )
                }
                if (!test.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ConceptRichText(test.note, fontSize = 13.sp)
                }
                val qid = test.qid?.trim().orEmpty()
                if (bankAvailable && qid.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onOpenQuestionById(qid) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("افتح السؤال في البنك", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConceptDiffTable(table: ConceptDiffTableDto) {
    val columns = table.columns.orEmpty()
    val rows = table.rows.orEmpty()
    val scrollState = rememberScrollState()
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, sy.medpro.watani.ui.library.libLine()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            // Header row.
            Row(modifier = Modifier.background(MaroonPrimary)) {
                columns.forEach { column ->
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Text(
                            column,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.background(
                        if (rowIndex % 2 == 0) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                    )
                ) {
                    columns.indices.forEach { colIndex ->
                        Box(
                            modifier = Modifier
                                .width(150.dp)
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Text(
                                row.getOrNull(colIndex) ?: "",
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                fontWeight = if (colIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }
        }
    }
}

/**
 * Concept body text in the app's explanation style (plain 14sp text,
 * 22sp line height on the surface colour) with the pack's simple
 * markup honoured: **bold** spans render bold, lines starting with
 * "- " render as bullets, and numbered lines keep their numbers.
 * Anything else renders verbatim.
 */
@Composable
fun ConceptRichText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val lines = remember(text) { text.split('\n') }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            } else if (line.startsWith("- ")) {
                Row {
                    Text(
                        "• ",
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaroonPrimary
                    )
                    Text(
                        text = conceptAnnotated(line.removePrefix("- ")),
                        fontSize = fontSize,
                        lineHeight = 22.sp,
                        fontWeight = fontWeight,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = conceptAnnotated(line),
                    fontSize = fontSize,
                    lineHeight = 22.sp,
                    fontWeight = fontWeight,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun conceptAnnotated(line: String): AnnotatedString {
    if (!line.contains("**")) return AnnotatedString(line)
    return buildAnnotatedString {
        var index = 0
        while (index < line.length) {
            val start = line.indexOf("**", index)
            if (start < 0) {
                append(line.substring(index))
                break
            }
            if (start > index) append(line.substring(index, start))
            val end = line.indexOf("**", start + 2)
            if (end < 0) {
                append(line.substring(start))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(line.substring(start + 2, end))
            }
            index = end + 2
        }
    }
}
