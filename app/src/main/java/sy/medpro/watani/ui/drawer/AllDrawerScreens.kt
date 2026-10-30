package sy.medpro.watani.ui.drawer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import sy.medpro.watani.data.*
import sy.medpro.watani.ui.anki.AnkiFlashcardScreen
import sy.medpro.watani.ui.courses.CoursesScreen
import sy.medpro.watani.ui.explanation.ExplanationSection
import sy.medpro.watani.ui.explanation.ExplanationUiState
import sy.medpro.watani.ui.explanation.normalizeExplanationKey
import sy.medpro.watani.ui.explanation.rememberExplanationState
import sy.medpro.watani.ui.simulator.ExamSimulatorScreen
import sy.medpro.watani.ui.theme.MaroonPrimary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerScreenScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        content = content
    )
}

// 1. Profile — Team 1: must NOT crash when signed out. Signed-out users
// see a signed-out state with an optional Google sign-in button; sign-in
// never blocks entry or free sections (MainActivity no longer gates).
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    val isSignedIn = firebaseUser != null
    val userName = firebaseUser?.displayName ?: "زائر"
    val email = firebaseUser?.email ?: "غير مسجل الدخول — الدخول اختياري"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { OfflineStudyVault.ensureInitialized(context) }
    val stats by OfflineStudyVault.statsFlow.collectAsState()

    DrawerScreenScaffold(title = "الملف الشخصي", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(50.dp), color = MaroonPrimary.copy(alpha = 0.2f), modifier = Modifier.size(96.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(userName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(email, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("حساب Google المرتبط (Firebase Auth)", fontWeight = FontWeight.Bold, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isSignedIn) {
                        Text("الحالة: متصل بنجاح ✓")
                    } else {
                        Text("الحالة: غير مسجل الدخول (اختياري — التصفح المجاني متاح بدونه)")
                    }
                    Text("إجمالي النقاط المكتسبة: ${stats.totalCorrect * 25} XP")
                    Text("الأسئلة المجابة محلياً: ${stats.totalAnswered} (${stats.totalCorrect} صحيحة)")
                }
            }
            if (!isSignedIn) {
                Spacer(modifier = Modifier.height(16.dp))
                if (isSigningIn) {
                    CircularProgressIndicator(color = MaroonPrimary, modifier = Modifier.size(28.dp))
                } else {
                    Button(
                        onClick = {
                            isSigningIn = true
                            signInError = null
                            scope.launch {
                                val result = sy.medpro.watani.ui.auth.GoogleSignInHelper.signInWithGoogle(context)
                                isSigningIn = false
                                result.onFailure { signInError = it.localizedMessage }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تسجيل الدخول بحساب Google (اختياري)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (signInError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(signInError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }
    }
}

// 2. Subscription & Activation — Team 1: Worker activation API.
// Firestore code validation was replaced (see WorkerActivationApi).
// Device list display was removed: the Worker contract has no
// list-devices endpoint, so bound devices can no longer be shown here.
@Composable
fun ActivationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("watani_activation", Context.MODE_PRIVATE) }
    var code by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf(prefs.getString("activated_code", null)?.let { "الحساب مفعل بالكود: $it" } ?: "الحساب غير مفعل. يرجى إدخال كود تفعيل صحيح.") }
    var isActivated by remember { mutableStateOf(WorkerActivationApi.isActivated(context)) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DrawerScreenScaffold(title = "الاشتراك وتفعيل الحساب", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = if (isActivated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(if (isActivated) "حالة الباقة: مفعلة ✓" else "حالة الباقة: غير مفعلة", fontWeight = FontWeight.Bold, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(statusMessage, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            if (!isActivated) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("أدخل كود التفعيل الرسمي") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val trimmed = code.trim()
                        if (trimmed.isBlank()) {
                            statusMessage = "يرجى إدخال كود تفعيل صالح أولاً."
                            return@Button
                        }
                        isLoading = true
                        statusMessage = "جاري التحقق من الكود عبر الخادم..."
                        scope.launch {
                            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            if (user == null) {
                                statusMessage = "يرجى تسجيل الدخول أولاً لتفعيل الحساب."
                                isLoading = false
                                return@launch
                            }
                            // Exact server error text is returned verbatim on failure.
                            val (success, msg) = WorkerActivationApi.activate(context, user, trimmed)
                            isActivated = success
                            statusMessage = msg
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("التحقق والتفعيل", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            // No revoke endpoint exists in the Worker contract, so
                            // deactivation clears local state only; the server-side
                            // device binding is untouched (open issue, see report).
                            WorkerActivationApi.clearLocalActivation(context)
                            isActivated = false
                            statusMessage = "تم إلغاء التفعيل على هذا الجهاز."
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("إلغاء التفعيل", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 3. Courses
@Composable
fun CoursesDrawerScreen(onBack: () -> Unit) {
    CoursesScreen(onBack = onBack)
}

// 4. Daily Challenge (dynamic from downloaded sections)
@Composable
fun DailyChallengeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var allQuestions by remember { mutableStateOf<List<Pair<String, QuestionDto>>>(emptyList()) }
    var selectedQuestion by remember { mutableStateOf<Pair<String, QuestionDto>?>(null) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val dir = File(context.filesDir, "sections_cache")
        val list = mutableListOf<Pair<String, QuestionDto>>()
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".json") && !file.name.contains("cases") && !file.name.contains("guide") && !file.name.contains("osce")) {
                    try {
                        val text = file.readText(Charsets.UTF_8)
                        val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                        val env = moshi.adapter(SubjectEnvelope::class.java).fromJson(text)
                        val subjName = env?.subjectName ?: file.name.substringBefore(".json")
                        env?.topics?.forEach { topic ->
                            topic.questions?.forEach { q ->
                                if (!q.text.isNullOrBlank() && !q.options.isNullOrEmpty()) {
                                    list.add(Pair(subjName, q))
                                }
                            }
                        }
                        env?.sessions?.forEach { session ->
                            session.questions?.forEach { q ->
                                if (!q.text.isNullOrBlank() && !q.options.isNullOrEmpty()) {
                                    list.add(Pair(subjName, q))
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        allQuestions = list
        if (list.isNotEmpty()) {
            val dayIndex = (System.currentTimeMillis() / (1000 * 60 * 60 * 24)).toInt() % list.size
            selectedQuestion = list[dayIndex]
        }
    }

    DrawerScreenScaffold(title = "التحدي اليومي الوطني", onBack = onBack) { padding ->
        if (selectedQuestion == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("لا توجد بنوك أسئلة محملة حالياً.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("يرجى تحميل بعض الأقسام من الشاشة الرئيسية لتوليد التحدي اليومي من البنوك الحقيقية.", fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            val (subj, q) = selectedQuestion!!
            val options = q.options ?: emptyList()
            val correctIdx = getCorrectIndices(q, options).firstOrNull() ?: 0
            val dailyExplanationState = rememberExplanationState(normalizeExplanationKey(q.id))

            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Whatshot, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("سؤال التحدي اليومي • مادة: $subj", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                            Text("مستخرج من بنوك الأسئلة المعتمدة", fontSize = 12.sp)
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(q.text ?: "", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
                options.forEachIndexed { idx, opt ->
                    val isSelected = selectedOptionIndex == idx
                    val bg = when {
                        submitted && idx == correctIdx -> MaterialTheme.colorScheme.primaryContainer
                        submitted && isSelected && idx != correctIdx -> MaterialTheme.colorScheme.errorContainer
                        isSelected -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !submitted) { selectedOptionIndex = idx },
                        colors = CardDefaults.cardColors(containerColor = bg)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSelected, onClick = { if (!submitted) selectedOptionIndex = idx })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(opt, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Button(
                    onClick = {
                        if (selectedOptionIndex != null) {
                            submitted = true
                            val isCorrect = selectedOptionIndex == correctIdx
                            OfflineStudyVault.recordQuestionAttempt(
                                context = context,
                                subjectTitle = subj,
                                questionId = q.id ?: "daily_${System.currentTimeMillis()}",
                                questionText = q.text ?: "",
                                selectedOptionText = options[selectedOptionIndex!!],
                                correctOptionText = options[correctIdx],
                                explanation = q.explanation ?: "لا توجد تفاصيل إضافية.",
                                isCorrect = isCorrect
                            )
                        }
                    },
                    enabled = selectedOptionIndex != null && !submitted,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) {
                    Text("تأكيد الإجابة", fontWeight = FontWeight.Bold)
                }
                if (submitted) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(if (selectedOptionIndex == correctIdx) "إجابة صحيحة!" else "إجابة غير صحيحة. الجواب الصحيح: ${options.getOrNull(correctIdx)}", fontWeight = FontWeight.Bold, color = MaroonPrimary)
                            if (!q.explanation.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("الشرح: ${q.explanation}", fontSize = 13.sp)
                            }
                            when (val ex = dailyExplanationState) {
                                is ExplanationUiState.Present -> ExplanationSection(content = ex.content)
                                ExplanationUiState.Loading -> Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaroonPrimary
                                    )
                                }
                                ExplanationUiState.Idle, ExplanationUiState.Absent -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

// 5. Flashcards (dynamic from downloaded sections)
@Composable
fun FlashcardsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var flashcards by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val dir = File(context.filesDir, "sections_cache")
        val list = mutableListOf<Pair<String, String>>()
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".json") && !file.name.contains("cases") && !file.name.contains("guide") && !file.name.contains("osce")) {
                    try {
                        val text = file.readText(Charsets.UTF_8)
                        val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                        val env = moshi.adapter(SubjectEnvelope::class.java).fromJson(text)
                        env?.topics?.forEach { topic ->
                            topic.questions?.forEach { q ->
                                val qText = q.text
                                val options = q.options
                                val correctIndices = getCorrectIndices(q, options)
                                val correctAns = correctIndices.firstOrNull()?.let { options?.getOrNull(it) } ?: q.explanation
                                if (!qText.isNullOrBlank() && !correctAns.isNullOrBlank()) {
                                    list.add(Pair(qText, correctAns))
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        flashcards = list
    }

    DrawerScreenScaffold(title = "البطاقات التعليمية (Flashcards)", onBack = onBack) { padding ->
        if (flashcards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Style, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("لا توجد بطاقات تعليمية متاحة.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("يرجى تحميل أقسام من الشاشة الرئيسية لتوليد البطاقات التعليمية من بنوك الأسئلة.", fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            val currentCard = flashcards[currentIndex]
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                LinearProgressIndicator(progress = { (currentIndex + 1).toFloat() / flashcards.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp), color = MaroonPrimary)
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp).clickable { showAnswer = !showAnswer },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (!showAnswer) "السؤال (انقر للقلب)" else "الإجابة النموذجية:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (!showAnswer) currentCard.first else currentCard.second,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 28.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(if (!showAnswer) "انقر لعرض الجواب" else "انقر للعودة للسؤال", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        showAnswer = false
                        currentIndex = (currentIndex + 1) % flashcards.size
                    }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("التالي (مراجعة لاحقاً)")
                    }
                    Button(onClick = {
                        showAnswer = false
                        currentIndex = (currentIndex + 1) % flashcards.size
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary), modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("أتقنتها ✓")
                    }
                }
            }
        }
    }
}

// 6. Study Guide (loaded from guide.json)
@Composable
fun StudyGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var guideEnvelope by remember { mutableStateOf<GuideEnvelope?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedGroup by remember { mutableStateOf<GuideGroupDto?>(null) }

    fun loadGuide() {
        scope.launch {
            isLoading = true
            val repo = WataniRepository(context)
            var env = repo.getGuideEnvelope()
            if (env == null) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val files = manifestRes.getOrNull()?.files
                    val guideEntry = files?.get("guide.json")
                    if (guideEntry != null) {
                        repo.downloadSection("guide.json", guideEntry)
                        env = repo.getGuideEnvelope()
                    }
                }
            }
            guideEnvelope = env
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadGuide() }

    DrawerScreenScaffold(title = "دليل الدراسة", onBack = { if (selectedGroup != null) selectedGroup = null else onBack() }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaroonPrimary)
                    }
                }
                guideEnvelope == null || guideEnvelope?.groups.isNullOrEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("لم يتم تحميل دليل الدراسة (guide.json) بعد.", fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { loadGuide() }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)) {
                            Text("تحميل دليل الدراسة")
                        }
                    }
                }
                selectedGroup == null -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(guideEnvelope!!.groups!!) { group ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { selectedGroup = group }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(group.title ?: "موضوع", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                        Text(selectedGroup!!.title ?: "", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(selectedGroup!!.content ?: selectedGroup!!.items?.joinToString("\n• ") ?: "", fontSize = 15.sp, lineHeight = 24.sp)
                    }
                }
            }
        }
    }
}

// 7. Wrong Answers
@Composable
fun WrongAnswersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { OfflineStudyVault.ensureInitialized(context) }
    val stats by OfflineStudyVault.statsFlow.collectAsState()

    DrawerScreenScaffold(title = "سجل أخطائي والاختبارات", onBack = onBack) { padding ->
        if (stats.savedMistakes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("لا توجد أخطاء مسجلة حالياً.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("أي سؤال تخطئ في حله سيُحفظ هنا تلقائياً للمراجعة بدون إنترنت.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaroonPrimary)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("إجمالي الأخطاء المحفوظة للمراجعة: ${stats.savedMistakes.size}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                items(stats.savedMistakes, key = { it.id }) { mistake ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = MaroonPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                                    Text(mistake.subjectTitle, color = MaroonPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                                TextButton(onClick = { OfflineStudyVault.removeMistake(context, mistake.id) }) {
                                    Text("حذف", color = MaroonPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(mistake.questionText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("الجواب الصحيح: ${mistake.correctAnswer}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                        }
                    }
                }
            }
        }
    }
}

// 8. Anki Smart Review
@Composable
fun AnkiSmartReviewScreen(onBack: () -> Unit) {
    AnkiFlashcardScreen(onBack = onBack)
}

// 9. Exam Simulation
@Composable
fun ExamSimulationScreen(onBack: () -> Unit) {
    ExamSimulatorScreen(onBack = onBack)
}

// 10. Virtual Patient
@Composable
fun VirtualPatientDrawerScreen(onBack: () -> Unit) {
    onBack()
}

// 11. Weak Points
@Composable
fun WeakPointsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { OfflineStudyVault.ensureInitialized(context) }
    val stats by OfflineStudyVault.statsFlow.collectAsState()

    DrawerScreenScaffold(title = "نقاط الضعف والتحليل", onBack = onBack) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("إجمالي الأسئلة المجابة: ${stats.totalAnswered} | الصحيحة: ${stats.totalCorrect}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
            val specs = listOf("الباطنة", "الجراحة", "الأطفال", "النسائية والتوليد", "الجلدية والعينية والأذنية")
            items(specs) { spec ->
                val ans = stats.specialtyAnswered[spec] ?: 0
                val cor = stats.specialtyCorrect[spec] ?: 0
                val ratio = if (ans > 0) (cor * 100 / ans) else 0
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(spec, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("$ratio% دقة ($cor / $ans)", fontWeight = FontWeight.Bold, color = MaroonPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { (ratio.toFloat() / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = MaroonPrimary)
                    }
                }
            }
        }
    }
}

// 12. Focus Timer
@Composable
fun FocusTimerScreen(onBack: () -> Unit) {
    var timeLeft by remember { mutableStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, timeLeft) {
        if (isRunning && timeLeft > 0) {
            kotlinx.coroutines.delay(1000L)
            timeLeft -= 1
        } else if (timeLeft == 0) {
            isRunning = false
            timeLeft = 25 * 60
        }
    }

    DrawerScreenScaffold(title = "مؤقت التركيز (Pomodoro)", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("جلسة تركيز دراسية طبية", fontSize = 18.sp, color = MaroonPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            val m = timeLeft / 60
            val s = timeLeft % 60
            Text(text = String.format("%02d:%02d", m, s), fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { isRunning = !isRunning }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)) {
                    Text(if (isRunning) "إيقاف مؤقت" else "بدء التركيز")
                }
                OutlinedButton(onClick = { isRunning = false; timeLeft = 25 * 60 }) {
                    Text("إعادة ضبط")
                }
            }
        }
    }
}

// 13. Badges (Computed from real stats)
@Composable
fun BadgesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { OfflineStudyVault.ensureInitialized(context) }
    val stats by OfflineStudyVault.statsFlow.collectAsState()

    DrawerScreenScaffold(title = "الأوسمة ونقاط الخبرة", onBack = onBack) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaroonPrimary)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("إجمالي النقاط: ${stats.totalCorrect * 25} XP", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("الأسئلة المحلولة: ${stats.totalAnswered}", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
            }
            val badges = listOf(
                Triple("طبيب نشط", "حل 10 أسئلة على الأقل", stats.totalAnswered >= 10),
                Triple("مثابر متقدم", "حل 50 سؤالاً على الأقل", stats.totalAnswered >= 50),
                Triple("خبير البنوك", "حل 200 سؤال على الأقل", stats.totalAnswered >= 200),
                Triple("مستوى البورد", "تجاوز 500 نقطة XP", (stats.totalCorrect * 25) >= 500)
            )
            items(badges) { (title, desc, unlocked) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (unlocked) Icons.Default.WorkspacePremium else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (unlocked) MaroonPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

// 14. Global Search (over downloaded section files)
@Composable
fun GlobalSearchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        val dir = File(context.filesDir, "sections_cache")
        val list = mutableListOf<String>()
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".json") && !file.name.contains("guide")) {
                    try {
                        val text = file.readText(Charsets.UTF_8)
                        if (text.contains(query, ignoreCase = true)) {
                            list.add("مطابقة في ملف: ${file.nameWithoutExtension}")
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        results = list
    }

    DrawerScreenScaffold(title = "البحث الشامل في المحتوى المحمل", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("ابحث عن مصطلح في الملفات المحملة...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (query.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("أدخل كلمة للبحث في الأقسام المحملة.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            } else if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد نتائج مطابقة في الملفات المحلية المحملة.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { item ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaroonPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(item, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 15. Favorites
@Composable
fun FavoritesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("watani_favorites", Context.MODE_PRIVATE) }
    var favs by remember { mutableStateOf(prefs.getStringSet("fav_list", emptySet()) ?: emptySet()) }

    DrawerScreenScaffold(title = "المفضلة", onBack = onBack) { padding ->
        if (favs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("لا توجد عناصر مضافة للمفضلة حالياً.\nيمكنك إضافة الأسئلة أو الحالات إلى المفضلة بالضغط على أيقونة القلب في بطاقة السؤال أو شاشة الحالة السريرية.", fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(favs.toList()) { item ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val next = favs - item
                                prefs.edit().putStringSet("fav_list", next).apply()
                                favs = next
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 16. My Notes
@Composable
fun MyNotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("watani_notes", Context.MODE_PRIVATE) }
    var noteText by remember { mutableStateOf("") }
    var notesList by remember { mutableStateOf(prefs.getString("notes_json", "[]")?.let { json ->
        try {
            val arr = JSONArray(json)
            buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
        } catch (_: Exception) { emptyList<String>() }
    } ?: emptyList<String>()) }

    DrawerScreenScaffold(title = "ملاحظاتي الشخصية", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("أضف ملاحظة جديدة...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (noteText.isNotBlank()) {
                        val next = listOf(noteText) + notesList
                        notesList = next
                        val arr = JSONArray()
                        next.forEach { arr.put(it) }
                        prefs.edit().putString("notes_json", arr.toString()).apply()
                        noteText = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) {
                Text("حفظ الملاحظة محلياً")
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (notesList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("لا توجد ملاحظات محفوظة حالياً.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(notesList) { note ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(note, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    val next = notesList - note
                                    notesList = next
                                    val arr = JSONArray()
                                    next.forEach { arr.put(it) }
                                    prefs.edit().putString("notes_json", arr.toString()).apply()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 17. Settings
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("watani_settings", Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark", false)) }

    DrawerScreenScaffold(title = "الإعدادات", onBack = onBack) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("الوضع الليلي الداكن", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Switch(checked = darkMode, onCheckedChange = { darkMode = it; prefs.edit().putBoolean("dark", it).apply() }, colors = SwitchDefaults.colors(checkedThumbColor = MaroonPrimary))
                    }
                }
            }
        }
    }
}

// 18. Privacy (loaded from guide.json)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var guideEnvelope by remember { mutableStateOf<GuideEnvelope?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun loadGuide() {
        scope.launch {
            isLoading = true
            val repo = WataniRepository(context)
            var env = repo.getGuideEnvelope()
            if (env == null) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val files = manifestRes.getOrNull()?.files
                    val guideEntry = files?.get("guide.json")
                    if (guideEntry != null) {
                        repo.downloadSection("guide.json", guideEntry)
                        env = repo.getGuideEnvelope()
                    }
                }
            }
            guideEnvelope = env
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadGuide() }

    DrawerScreenScaffold(title = "الخصوصية وشروط الاستخدام", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaroonPrimary)
                    }
                }
                guideEnvelope?.privacy.isNullOrEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("لم يتم تحميل تفاصيل الخصوصية بعد.", fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { loadGuide() }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)) {
                            Text("تحميل التفاصيل")
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(guideEnvelope!!.privacy!!) { priv ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(priv.title ?: "", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(priv.text ?: "", fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildTelegramUrl(raw: String?): String {
    if (raw.isNullOrBlank()) return "https://t.me"
    val trimmed = raw.trim()
    if (trimmed.startsWith("http", ignoreCase = true)) {
        return trimmed
    }
    val clean = trimmed.removePrefix("@")
    return "https://t.me/$clean"
}

// 19. Contact Admin (loaded dynamically from guide.json info)
@Composable
fun ContactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var guideEnvelope by remember { mutableStateOf<GuideEnvelope?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun loadGuide() {
        scope.launch {
            isLoading = true
            val repo = WataniRepository(context)
            var env = repo.getGuideEnvelope()
            if (env == null) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val files = manifestRes.getOrNull()?.files
                    val guideEntry = files?.get("guide.json")
                    if (guideEntry != null) {
                        repo.downloadSection("guide.json", guideEntry)
                        env = repo.getGuideEnvelope()
                    }
                }
            }
            guideEnvelope = env
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadGuide() }

    DrawerScreenScaffold(title = "التواصل مع الإدارة والدعم الفني", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaroonPrimary)
                    }
                }
                guideEnvelope?.info == null -> {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("لم يتم تحميل معلومات التواصل بعد.", fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { loadGuide() }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)) {
                            Text("تحميل التفاصيل")
                        }
                    }
                }
                else -> {
                    val info = guideEnvelope!!.info!!
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("قنوات الدعم الرسمي", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(info.supportMessage ?: "تواصل معنا عبر القنوات الرسمية أدناه:", fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (!info.telegramChannel.isNullOrBlank()) {
                                        Button(onClick = {
                                            val url = buildTelegramUrl(info.telegramChannel)
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary), modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.Send, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("قناة التليجرام الرسمية")
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    if (!info.telegramBot.isNullOrBlank()) {
                                        OutlinedButton(onClick = {
                                            val url = buildTelegramUrl(info.telegramBot)
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.SupportAgent, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("بوت الدعم الفني")
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    if (!info.adminEmail.isNullOrBlank()) {
                                        OutlinedButton(onClick = {
                                            val uriStr = "mailto:${info.adminEmail}"
                                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriStr))
                                            context.startActivity(intent)
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.Email, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("راسلنا عبر البريد الإلكتروني")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 20. Social Links & Sign-Out
@Composable
fun SocialLinksScreen(onBack: () -> Unit) {
    ContactScreen(onBack = onBack)
}
