package sy.medpro.watani.ui.logbook

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import sy.medpro.watani.ui.auth.GoogleSignInHelper
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDay(millis: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date(millis))

/**
 * شاشة «سجلّ حالاتي السريرية» — قائمة منظمة + محرّر كتابة سريعة.
 * السجل بيانات الطالب الشخصية: متاح لكل مسجّل دخول Google (لا علاقة له
 * بالتفعيل)، ومحفوظ محليًا فورًا ومزامَن سحابيًا على حسابه.
 */
@Composable
fun LogbookScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth -> user = auth.currentUser }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }

    DrawerScreenScaffold(title = "سجلّ حالاتي السريرية", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val currentUser = user
            if (currentUser == null) {
                LogbookSignedOutState()
            } else {
                LogbookMain(context = context, user = currentUser)
            }
        }
    }
}

@Composable
private fun LogbookSignedOutState() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = GoldAccent.copy(alpha = 0.18f), modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.NoteAlt, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("سجلّك مربوط بحسابك", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "سجلّ الحالات بياناتك الشخصية: تُحفظ على جهازك فورًا وتُزامَن سحابيًا مع حساب Google الخاص بك، فتصلها من أي جهاز تدخل منه بنفس الحساب ولا تضيع بحذف التطبيق. سجّل دخولك لبدء التوثيق.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    GoogleSignInHelper.signInWithGoogle(context)
                    busy = false
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(if (busy) "جارٍ تسجيل الدخول…" else "تسجيل الدخول بـ Google") }
    }
}

@Composable
private fun LogbookMain(context: Context, user: FirebaseUser) {
    val scope = rememberCoroutineScope()
    val entries by LogbookRepository.entriesFlow.collectAsState()
    val syncInfo by LogbookRepository.syncInfo.collectAsState()

    var query by remember { mutableStateOf("") }
    var specialtyFilter by remember { mutableStateOf<String?>(null) }
    var showTemplates by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var readyUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(user.uid) {
        LogbookRepository.switchAccount(context, user.uid)
        // بوابة جاهزية: لا تُعرض القائمة قبل تبديل المخزن فعليًا — بلا هذا
        // قد يلمح إطارٌ أول إدخالات الحساب السابق (مراجعة مستقلة).
        readyUid = user.uid
        LogbookRepository.syncNow(context)
    }

    val filtered = remember(entries, query, specialtyFilter) {
        entries.filter { e ->
            (specialtyFilter == null || e.specialty == specialtyFilter) &&
                (query.isBlank() || listOf(
                    e.chiefComplaint, e.diagnosis, e.keyFindings, e.investigations,
                    e.management, e.lessons, e.ward, e.specialty
                ).any { it.contains(query, ignoreCase = true) } || e.tags.any { it.contains(query, ignoreCase = true) })
        }
    }
    val weekCount = remember(entries) {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        entries.count { it.seenDate >= weekAgo }
    }
    val presentSpecialties = remember(entries) { entries.map { it.specialty }.filter { it.isNotBlank() }.distinct() }

    // فرع المحرّر بعد كل نداءات remember أعلاه عمدًا: عودة مبكرة قبلها كانت
    // ستغيّر ترتيب الخطافات بين إعادة تركيب وأخرى (خطر فساد حالة Compose).
    val editing = editingId?.let { id -> entries.firstOrNull { it.id == id } }
    // خطّاف غير مشروط (نُقل خارج الفرع بمراجعة مستقلة): إدخال مفتوح حُذف أو
    // استُبدل من مزامنة/تبديل حساب — عودة هادئة للقائمة.
    LaunchedEffect(editingId, editing) {
        if (editingId != null && editing == null) editingId = null
    }
    if (readyUid != user.uid) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("جارٍ تحميل سجلّك…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (editing != null) {
        LogbookEditor(
            context = context,
            uid = user.uid,
            entry = editing,
            onClose = {
                editingId = null
                scope.launch { LogbookRepository.syncNow(context) }
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // ترويسة العدّادات + حالة المزامنة
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("${entries.size} حالة موثّقة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("$weekCount هذا الأسبوع", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SyncStatusBadge(syncInfo)
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { showTemplates = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("  حالة جديدة — اختر الاختصاص وابدأ")
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("ابحث بشكاية أو تشخيص أو وسم…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (presentSpecialties.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = specialtyFilter == null,
                        onClick = { specialtyFilter = null },
                        label = { Text("الكل") }
                    )
                }
                items(presentSpecialties) { sp ->
                    FilterChip(
                        selected = specialtyFilter == sp,
                        onClick = { specialtyFilter = if (specialtyFilter == sp) null else sp },
                        label = { Text(sp) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            LogbookEmptyState(hasEntries = entries.isNotEmpty(), onAdd = { showTemplates = true })
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)
            ) {
                items(filtered, key = { it.id }) { entry ->
                    LogbookEntryCard(entry = entry, onClick = { editingId = entry.id })
                }
            }
        }
    }

    if (showTemplates) {
        TemplatePickerDialog(
            onDismiss = { showTemplates = false },
            onPick = { specialty ->
                showTemplates = false
                val created = LogbookRepository.createEntry(context, user.uid, specialty)
                editingId = created.id
            }
        )
    }
}

@Composable
private fun SyncStatusBadge(info: LogbookSyncInfo) {
    if (info.blocked > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text(
                "${info.blocked} فوق حدّ الرفع — قلّص نصّها",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.error
            )
        }
        return
    }
    val (icon: ImageVector, text: String) = when (info.state) {
        LogbookSyncState.SYNCING -> Icons.Default.Sync to "جارٍ المزامنة…"
        LogbookSyncState.SYNCED -> Icons.Default.CheckCircle to "متزامن مع حسابك"
        LogbookSyncState.AUTH_ERROR -> Icons.Default.Schedule to "المزامنة بانتظار الدخول"
        else -> when {
            info.pending > 0 -> Icons.Default.Schedule to "${info.pending} بانتظار الرفع — محفوظة بجهازك"
            info.lastSyncAt > 0L -> Icons.Default.CheckCircle to "متزامن مع حسابك"
            else -> Icons.Default.Schedule to "محفوظ على جهازك"
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(16.dp))
        Text(" $text", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LogbookEmptyState(hasEntries: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = GoldAccent.copy(alpha = 0.18f), modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (hasEntries) "لا نتائج مطابقة" else "لا حالات موثّقة بعد",
            fontWeight = FontWeight.Bold, fontSize = 17.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasEntries) "جرّب كلمة بحث أخرى أو ألغِ فلتر الاختصاص."
            else "وثّق أول حالة تشاهدها بمناوبتك — القالب جاهز وتُحفظ كتابتك تلقائيًا على جهازك وتُزامَن مع حسابك.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        if (!hasEntries) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("أضف أول حالة") }
        }
    }
}

@Composable
private fun LogbookEntryCard(entry: LogbookEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(20.dp))
                Text(
                    " ${entry.specialty.ifBlank { "حالة" }}",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(formatDay(entry.seenDate), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val headline = entry.chiefComplaint.ifBlank { entry.diagnosis }
            if (headline.isNotBlank()) {
                Text(headline, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
            if (entry.diagnosis.isNotBlank() && entry.diagnosis != headline) {
                Text("التشخيص: ${entry.diagnosis}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            if (entry.tags.isNotEmpty()) {
                Text(
                    entry.tags.joinToString("  ") { "#$it" },
                    fontSize = 11.sp, color = MaroonPrimary, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TemplatePickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("اختر اختصاص الحالة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "لمسة واحدة تنشئ الحالة وتفتح المحرّر — الكتابة تُحفظ تلقائيًا.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(LOGBOOK_SPECIALTIES) { sp ->
                        TextButton(
                            onClick = { onPick(sp) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(18.dp))
                                Text("  $sp", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") }
            }
        }
    }
}

/**
 * محرّر الكتابة السريعة: كل تغيير يُحفظ محليًا فورًا عبر المستودع
 * (لا زر حفظ)، وupdatedAt يرتفع رتيبًا مع كل تغيير (الأكبر بين الآن
 * والطابع السابق +1) — خنقٌ زمني سابق كان يبتلع تحريرَ إعادةِ فتحٍ فورية
 * بصمت فأزالته المراجعة المستقلة: كل تحرير يجب أن يصير «معلّقًا» حتمًا.
 */
@Composable
private fun LogbookEditor(context: Context, uid: String, entry: LogbookEntry, onClose: () -> Unit) {
    var draft by remember(entry.id) { mutableStateOf(entry) }
    var expanded by remember(entry.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tagInput by remember(entry.id) { mutableStateOf("") }
    // قذارة حقيقية: هل حرّر المستخدم فعلًا بجلسة المحرّر هذه؟ التفريغ
    // الأخير مشروط بها — إغلاق بلا تحرير يجب أن يُبقي أي نسخة أحدث
    // استبدلها جلبٌ أثناء العرض (مراجعة مستقلة N2).
    var dirty by remember(entry.id) { mutableStateOf(false) }

    fun update(transform: (LogbookEntry) -> LogbookEntry) {
        val next = transform(draft).copy(
            updatedAt = maxOf(System.currentTimeMillis(), draft.updatedAt + 1),
            syncBlocked = false
        )
        draft = next
        dirty = true
        LogbookRepository.saveEntry(context, uid, next)
    }

    fun close() {
        // تفريغ أخير عند وجود تحرير فعلي فقط: يضمن أن محتوى المسودة
        // المحرَّر هو المخزَّن حتى لو استبدل جلبٌ نسخةَ المستودع أثناءه.
        if (dirty) LogbookRepository.flushDraft(context, uid, draft)
        // إدخال أُنشئ من قالب ثم تُرك فارغًا تمامًا ولم يُزامَن قط: يُرمى
        // محليًا بلا قبر (لا وجود له بأي مكان آخر أصلًا).
        if (!draft.hasContent() && draft.syncedUpdatedAt == 0L) {
            LogbookRepository.discardLocal(context, uid, draft.id)
        }
        onClose()
    }

    // زر الرجوع النظامي يمر بـclose() نفسها (رمي المسودة الفارغة والتفريغ
    // الأخير) بدل تجاوزها لإغلاق الشاشة كلها.
    BackHandler { close() }

    // تنبيه حجم حي: حدّ المزامنة السحابية 20KB بايتات — فوقه يبقى الإدخال
    // محفوظًا محليًا فقط، فيُقال للمستخدم هنا لا يُترك معلّقًا بصمت.
    val draftOversized = remember(draft) {
        draft.toDataJson().toString().toByteArray(Charsets.UTF_8).size > 20480
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { close() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
            }
            Text("توثيق حالة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text("يُحفظ تلقائيًا", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "وثّق بصورة مجهّلة تعليمية — دون أسماء مرضى أو أرقام ملفات أو أي ما يدلّ عليهم.",
                fontSize = 12.sp, modifier = Modifier.padding(12.dp)
            )
        }
        if (draftOversized) {
            Text(
                "هذا الإدخال تجاوز حدّ المزامنة السحابية (20KB) — محفوظ بجهازك، ولن يُرفع لحسابك حتى تقلّص نصّه.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("الاختصاص", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    items(LOGBOOK_SPECIALTIES) { sp ->
                        FilterChip(
                            selected = draft.specialty == sp,
                            onClick = { update { it.copy(specialty = sp) } },
                            label = { Text(sp) }
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تاريخ المشاهدة: ${formatDay(draft.seenDate)}", fontSize = 12.sp)
                    FilterChip(
                        selected = formatDay(draft.seenDate) == formatDay(System.currentTimeMillis()),
                        onClick = { update { it.copy(seenDate = System.currentTimeMillis()) } },
                        label = { Text("اليوم") }
                    )
                    FilterChip(
                        selected = formatDay(draft.seenDate) == formatDay(System.currentTimeMillis() - 86400000L),
                        onClick = { update { it.copy(seenDate = System.currentTimeMillis() - 86400000L) } },
                        label = { Text("أمس") }
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = draft.chiefComplaint,
                    onValueChange = { v -> update { it.copy(chiefComplaint = v) } },
                    label = { Text("الشكاية الرئيسية") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = draft.diagnosis,
                    onValueChange = { v -> update { it.copy(diagnosis = v) } },
                    label = { Text("التشخيص") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    Text(if (expanded) "إخفاء التفاصيل الإضافية" else "تفاصيل إضافية: القسم، الفحص، التحاليل، التدبير، الدروس")
                }
            }
            if (expanded) {
                item {
                    OutlinedTextField(
                        value = draft.ward,
                        onValueChange = { v -> update { it.copy(ward = v) } },
                        label = { Text("القسم / مكان المشاهدة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.keyFindings,
                        onValueChange = { v -> update { it.copy(keyFindings = v) } },
                        label = { Text("أهم موجودات الفحص") },
                        minLines = 2, modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.investigations,
                        onValueChange = { v -> update { it.copy(investigations = v) } },
                        label = { Text("التحاليل والاستقصاءات") },
                        minLines = 2, modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.management,
                        onValueChange = { v -> update { it.copy(management = v) } },
                        label = { Text("التدبير") },
                        minLines = 2, modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.lessons,
                        onValueChange = { v -> update { it.copy(lessons = v) } },
                        label = { Text("الدروس المستفادة") },
                        minLines = 2, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Text("الوسوم", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (draft.tags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(draft.tags) { tag ->
                            FilterChip(
                                selected = false,
                                onClick = { update { e -> e.copy(tags = e.tags - tag) } },
                                label = { Text("#$tag ✕") }
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("وسم جديد") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val t = tagInput.trim()
                            if (t.isNotEmpty() && t !in draft.tags) {
                                update { e -> e.copy(tags = e.tags + t) }
                            }
                            tagInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                    ) { Text("إضافة") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف هذه الحالة؟") },
            text = { Text("سيُحذف التوثيق من جهازك ومن حسابك عند المزامنة، ولا يمكن التراجع.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    LogbookRepository.deleteEntry(context, uid, draft.id)
                    onClose()
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") }
            }
        )
    }
}
