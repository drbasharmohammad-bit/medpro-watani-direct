package sy.medpro.watani.ui.tips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sy.medpro.watani.data.OutboxTip
import sy.medpro.watani.data.ProfileNameStore
import sy.medpro.watani.data.TipItem
import sy.medpro.watani.data.TipLocalStatus
import sy.medpro.watani.data.TipsContract
import sy.medpro.watani.data.TipsOutboxLogic
import sy.medpro.watani.data.TipsPack
import sy.medpro.watani.data.TipsRepository
import sy.medpro.watani.data.TipsValidation
import sy.medpro.watani.data.tipAuthorDisplay
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.library.LibCard
import sy.medpro.watani.ui.library.LibColors
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * «زوادة الطالب» (build 1.2.51) — tips from students, for students.
 *
 * Navigation is internal state (the drawer-screen pattern of this
 * app): Subjects → Subject (six sections) → Section (tip list) →
 * Add form / «إضافاتي». Premium identity: burgundy/gold on ivory,
 * clean cards with a light shadow (LibCard), no yellow UI.
 *
 * Cache standard (the doctor's professional-cache rule): every
 * screen paints its cached content FIRST (packs and the outbox are
 * read synchronously from disk), refreshes silently in the
 * background, and every loading state settles — content, or an
 * Arabic error with a retry. No endless spinners anywhere here.
 */

private sealed interface TipsRoute {
    data object Subjects : TipsRoute
    data class Subject(val subject: String) : TipsRoute
    data class Section(val subject: String, val sectionId: String) : TipsRoute
    data class Add(val subject: String, val sectionId: String?) : TipsRoute
    data object Mine : TipsRoute
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun TipsRootScreen(
    onBack: () -> Unit,
    initialSubject: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var route by remember {
        mutableStateOf<TipsRoute>(
            if (initialSubject != null && TipsContract.isTipsSubject(initialSubject)) {
                TipsRoute.Subject(initialSubject)
            } else {
                TipsRoute.Subjects
            }
        )
    }

    // Open triggers: flush the outbox + refresh «mine» + learn the
    // writing gate — all silent, all best-effort.
    LaunchedEffect(Unit) {
        TipsRepository.syncInBackground(context)
        TipsRepository.refreshWritingMode(context)
    }

    val title = when (val r = route) {
        TipsRoute.Subjects -> "زوادة الطالب"
        is TipsRoute.Subject -> r.subject
        is TipsRoute.Section -> TipsContract.sectionTitle(r.sectionId) ?: "زوادة الطالب"
        is TipsRoute.Add -> "أضف نصيحة"
        TipsRoute.Mine -> "إضافاتي"
    }
    val back: () -> Unit = {
        route = when (val r = route) {
            TipsRoute.Subjects -> {
                onBack()
                r
            }
            is TipsRoute.Subject -> TipsRoute.Subjects
            is TipsRoute.Section -> TipsRoute.Subject(r.subject)
            is TipsRoute.Add -> if (r.sectionId != null) {
                TipsRoute.Section(r.subject, r.sectionId)
            } else {
                TipsRoute.Subject(r.subject)
            }
            TipsRoute.Mine -> TipsRoute.Subjects
        }
    }

    DrawerScreenScaffold(title = title, onBack = back) { padding ->
        Box(modifier = modifier.fillMaxSize().padding(padding)) {
            when (val r = route) {
                TipsRoute.Subjects -> TipsSubjectsContent(
                    onOpenSubject = { route = TipsRoute.Subject(it) },
                    onOpenMine = { route = TipsRoute.Mine }
                )
                is TipsRoute.Subject -> TipsSubjectContent(
                    subject = r.subject,
                    onOpenSection = { route = TipsRoute.Section(r.subject, it) },
                    onAdd = { route = TipsRoute.Add(r.subject, null) }
                )
                is TipsRoute.Section -> TipsSectionContent(
                    subject = r.subject,
                    sectionId = r.sectionId,
                    onAdd = { route = TipsRoute.Add(r.subject, r.sectionId) }
                )
                is TipsRoute.Add -> TipsAddContent(
                    subject = r.subject,
                    initialSectionId = r.sectionId,
                    onDone = {
                        route = if (r.sectionId != null) {
                            TipsRoute.Section(r.subject, r.sectionId)
                        } else {
                            TipsRoute.Subject(r.subject)
                        }
                    }
                )
                TipsRoute.Mine -> TipsMineContent()
            }
        }
    }
}

// ---------------------------------------------------------------
// Subjects list
// ---------------------------------------------------------------

@Composable
private fun TipsSubjectsContent(
    onOpenSubject: (String) -> Unit,
    onOpenMine: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var counts by remember { mutableStateOf(TipsRepository.cachedCounts(context)) }
    LaunchedEffect(Unit) {
        // Silent background fill: subjects without a cached pack try
        // one download pass (a subject with no published pack stays
        // a normal empty entry — never an error).
        withContext(Dispatchers.IO) {
            for (subject in TipsContract.SUBJECTS) {
                if (!TipsRepository.hasCachedPack(context, subject)) {
                    TipsRepository.ensurePack(context, subject, null)
                }
            }
        }
        counts = TipsRepository.cachedCounts(context)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "tips_intro") {
            Column {
                Text(
                    "نصائح من زملائك الطلاب، مرتّبة حسب المادة والقسم — وما تضيفه يصل بعد مراجعة سريعة.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                LibCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenMine) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaroonPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إضافاتي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "نصائحك وحالتها: بانتظار الإرسال، قيد المراجعة، منشورة، أو مرفوضة مع السبب.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        items(TipsContract.SUBJECTS, key = { it }) { subject ->
            LibCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenSubject(subject) }) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = LibColors.Gold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(subject, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    val count = counts[subject]
                    if (count != null && count > 0) {
                        Text(
                            "$count نصيحة",
                            fontSize = 12.sp,
                            color = LibColors.GoldText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// Subject: the six sections
// ---------------------------------------------------------------

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun TipsSubjectContent(
    subject: String,
    onOpenSection: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pack by remember(subject) { mutableStateOf(TipsRepository.cachedPack(context, subject)) }
    var settled by remember(subject) { mutableStateOf(pack != null) }
    LaunchedEffect(subject) {
        // Cache first (already painted); then: download when missing,
        // silent refresh when the manifest hash moved.
        val entry = TipsRepository.freshPackEntry(context, subject)
        val fresh = TipsRepository.ensurePack(context, subject, entry)
        if (fresh != null) pack = fresh
        settled = true
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (pack == null && settled) {
                item(key = "tips_empty_subject") {
                    Text(
                        "لا نصائح منشورة في هذه المادة بعد — كن أول من يضيف زوادة لزملائه.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(TipsContract.SECTIONS, key = { it.first }) { (sectionId, sectionTitle) ->
                val count = pack?.countFor(sectionId) ?: 0
                LibCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenSection(sectionId) }) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sectionTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (count > 0) "$count" else "—",
                            fontSize = 13.sp,
                            color = LibColors.GoldText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            containerColor = MaroonPrimary,
            contentColor = LibColors.Bg,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "أضف نصيحة")
        }
    }
}

// ---------------------------------------------------------------
// Section: the tip list
// ---------------------------------------------------------------

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun TipsSectionContent(
    subject: String,
    sectionId: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pack by remember(subject, sectionId) { mutableStateOf(TipsRepository.cachedPack(context, subject)) }
    var settled by remember(subject, sectionId) { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(subject) {
        val entry = TipsRepository.freshPackEntry(context, subject)
        val fresh = TipsRepository.ensurePack(context, subject, entry)
        if (fresh != null) pack = fresh
        settled = true
    }
    val tips: List<TipItem> = pack?.tipsFor(sectionId) ?: emptyList()
    Box(modifier = modifier.fillMaxSize()) {
        when {
            tips.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tips, key = { it.id.ifEmpty { it.text.hashCode().toString() } }) { tip ->
                    TipCard(
                        tip = tip,
                        onReport = {
                            scope.launch {
                                val engine = TipsRepository.engine(context)
                                val ok = if (engine != null && tip.id.isNotEmpty()) {
                                    engine.report(tip.id)
                                } else {
                                    false
                                }
                                message = if (ok) "تم إرسال البلاغ — شكرًا لك."
                                    else "تعذر إرسال البلاغ الآن — حاول مجددًا عند عودة الاتصال."
                            }
                        }
                    )
                }
            }
            settled -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "لا نصائح في هذا القسم بعد.",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "أضف أول نصيحة هنا وستظهر لزملائك بعد المراجعة.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("جارٍ جلب النصائح…", fontSize = 14.sp)
            }
        }
        if (message != null) {
            AlertDialog(
                onDismissRequest = { message = null },
                confirmButton = {
                    TextButton(onClick = { message = null }) { Text("حسنًا") }
                },
                text = { Text(message ?: "") }
            )
        }
        FloatingActionButton(
            onClick = onAdd,
            containerColor = MaroonPrimary,
            contentColor = LibColors.Bg,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "أضف نصيحة")
        }
    }
}

@Composable
private fun TipCard(tip: TipItem, onReport: () -> Unit, modifier: Modifier = Modifier) {
    LibCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(tip.text, fontSize = 15.sp, lineHeight = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    // The author's name — soft and small under the tip
                    // (the doctor's spec), «بدون اسم» when the pack
                    // carries none.
                    Text(
                        tipAuthorDisplay(tip.author),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tip.createdAt.isNotEmpty()) {
                        Text(
                            tip.createdAt.take(10),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (tip.id.isNotEmpty()) {
                    IconButton(onClick = onReport) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = "إبلاغ عن النصيحة",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// Add form
// ---------------------------------------------------------------

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun TipsAddContent(
    subject: String,
    initialSectionId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    var sectionId by remember { mutableStateOf(initialSectionId ?: TipsContract.SECTION_IDS.first()) }
    var text by remember { mutableStateOf("") }
    var name by remember {
        mutableStateOf(
            ProfileNameStore.savedName(context, uid)
                ?: FirebaseAuth.getInstance().currentUser?.displayName
                ?: ""
        )
    }
    var anonymous by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf(false) }
    val canWrite = TipsRepository.canWrite(context)
    val validationError = TipsValidation.errorFor(text)
    val counter = text.trim().length

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("المادة: $subject", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("القسم", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TipsContract.SECTIONS.chunked(2).forEach { rowSections ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowSections.forEach { (id, title) ->
                        FilterChip(
                            selected = sectionId == id,
                            onClick = { sectionId = id },
                            label = { Text(title, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("نص النصيحة") },
            placeholder = { Text("اكتب نصيحة قصيرة ومفيدة لزملائك…") },
            minLines = 4,
            maxLines = 8,
            isError = text.isNotEmpty() && validationError != null,
            supportingText = {
                Text(
                    when {
                        text.isNotEmpty() && validationError != null -> validationError
                        else -> "$counter / ${TipsValidation.MAX_LENGTH} حرف (الحد الأدنى ${TipsValidation.MIN_LENGTH})"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("الاسم الذي سيظهر مع النصيحة") },
            singleLine = true,
            enabled = !anonymous,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = anonymous,
                onCheckedChange = { anonymous = it },
                colors = SwitchDefaults.colors(checkedThumbColor = MaroonPrimary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("بدون اسم", fontSize = 14.sp)
        }
        if (!canWrite) {
            Text(
                "إضافة النصائح متاحة حاليًا للمفعّلين فقط — يمكنك تصفّح كل النصائح المنشورة بحرية.",
                fontSize = 13.sp,
                color = LibColors.GoldText,
                fontWeight = FontWeight.Bold
            )
        }
        Button(
            onClick = {
                val engine = TipsRepository.engine(context)
                if (engine != null) {
                    engine.add(
                        subject = subject,
                        sectionId = sectionId,
                        text = text,
                        authorName = if (anonymous) null else name
                    )
                    // Best-effort immediate flush; failure leaves the
                    // tip in the outbox (بانتظار الإرسال) — the local
                    // save above is the success the student sees.
                    scope.launch { engine.flush() }
                }
                savedMessage = true
            },
            enabled = canWrite && validationError == null,
            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("إرسال النصيحة")
        }
        Text(
            "تُحفظ نصيحتك على جهازك فورًا وتُرسل عند توفر الاتصال، ثم تظهر لزملائك بعد المراجعة.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (savedMessage) {
        AlertDialog(
            onDismissRequest = { savedMessage = false; onDone() },
            confirmButton = {
                TextButton(onClick = { savedMessage = false; onDone() }) { Text("حسنًا") }
            },
            title = { Text("تم الحفظ") },
            text = { Text("حُفظت نصيحتك وستُرسل للمراجعة — تابع حالتها من «إضافاتي».") }
        )
    }
}

// ---------------------------------------------------------------
// Mine
// ---------------------------------------------------------------

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun TipsMineContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { TipsRepository.engine(context) }
    val mine by (engine?.mine?.collectAsState() ?: remember { mutableStateOf(emptyList<OutboxTip>()) })
    var editing by remember { mutableStateOf<OutboxTip?>(null) }
    var editText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<OutboxTip?>(null) }
    var confirmEditPublished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        engine?.let { scope.launch { it.syncNow() } }
    }

    if (engine == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("سجّل الدخول أولًا لترى إضافاتك.", fontWeight = FontWeight.Bold)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "حالة نصائحك كما هي الآن — المحلية أولًا، وحالة الخادم تُحدَّث عند كل مزامنة.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { scope.launch { engine.syncNow() } }) {
                Icon(Icons.Default.Refresh, contentDescription = "تحديث الحالة", tint = MaroonPrimary)
            }
        }
        if (mine.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("لا إضافات بعد.", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "أضف أول نصيحة من صفحة أي مادة وستظهر هنا مع حالتها.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(mine, key = { it.localId }) { tip ->
                    MineTipCard(
                        tip = tip,
                        onEdit = {
                            editing = tip
                            editText = tip.text
                            confirmEditPublished = false
                        },
                        onDelete = { confirmDelete = tip }
                    )
                }
            }
        }
    }

    val editTarget = editing
    if (editTarget != null) {
        val editError = TipsValidation.errorFor(editText)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("تعديل النصيحة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editTarget.status == TipLocalStatus.PUBLISHED && !confirmEditPublished) {
                        Text(
                            "هذه النصيحة منشورة — تعديلها يعيدها إلى المراجعة قبل أن تظهر مجددًا.",
                            fontSize = 13.sp,
                            color = LibColors.GoldText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        minLines = 3,
                        maxLines = 6,
                        isError = editError != null,
                        supportingText = { Text(editError ?: "${editText.trim().length} / ${TipsValidation.MAX_LENGTH}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editError == null,
                    onClick = {
                        if (editTarget.status == TipLocalStatus.PUBLISHED && !confirmEditPublished) {
                            confirmEditPublished = true
                        } else {
                            engine.edit(editTarget.localId, editText)
                            scope.launch { engine.flush() }
                            editing = null
                        }
                    }
                ) { Text(if (editTarget.status == TipLocalStatus.PUBLISHED && !confirmEditPublished) "أفهم، متابعة" else "حفظ التعديل") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("إلغاء") }
            }
        )
    }

    val deleteTarget = confirmDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف النصيحة") },
            text = { Text("سيتم حذف هذه النصيحة نهائيًا. متابعة؟") },
            confirmButton = {
                TextButton(onClick = {
                    engine.delete(deleteTarget.localId)
                    scope.launch { engine.flush() }
                    confirmDelete = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun MineTipCard(
    tip: OutboxTip,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    LibCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${tip.subject} • ${TipsContract.sectionTitle(tip.sectionId) ?: tip.sectionId}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    TipsOutboxLogic.statusLabel(tip.status),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (tip.status) {
                        TipLocalStatus.PUBLISHED -> LibColors.GoldText
                        TipLocalStatus.REJECTED -> MaterialTheme.colorScheme.error
                        else -> MaroonPrimary
                    }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(tip.text, fontSize = 14.sp, lineHeight = 22.sp)
            if (tip.status == TipLocalStatus.REJECTED && !tip.rejectReason.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "سبب الرفض: ${tip.rejectReason}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                tipAuthorDisplay(tip.authorName),
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaroonPrimary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
