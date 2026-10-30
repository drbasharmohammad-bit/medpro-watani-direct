package sy.medpro.watani.ui.clinical

import android.content.Context
import android.media.MediaPlayer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sy.medpro.watani.data.*
import sy.medpro.watani.ui.theme.MaroonPrimary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuscultationTrainerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var auscultationEnvelope by remember { mutableStateOf<AuscultationEnvelope?>(null) }
    var arabicClips by remember { mutableStateOf<Map<String, AuscultationArClipDto>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("جاري تحميل حزمة الأصوات الطبيعية والسريرية...") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Library, 1: Quiz

    // MediaPlayer state
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingClipId by remember { mutableStateOf<String?>(null) }

    // Quiz state
    var quizClip by remember { mutableStateOf<AuscultationClipDto?>(null) }
    var quizOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }

    val setupQuiz: (List<AuscultationClipDto>) -> Unit = { clips ->
        if (clips.isNotEmpty()) {
            val randomClip = clips.random()
            val displayTitle: (AuscultationClipDto) -> String? = { clip ->
                arabicClips[clip.id ?: ""]?.arTitle ?: clip.label
            }
            quizClip = randomClip
            val correct = displayTitle(randomClip) ?: "صوت طبيعي"
            val distractors = clips.mapNotNull { displayTitle(it) }.filter { it != correct }.distinct().shuffled().take(3)
            quizOptions = (listOf(correct) + distractors).shuffled()
            selectedOption = null
            isSubmitted = false
        }
    }

    val loadPack: () -> Unit = {
        scope.launch {
            isLoading = true
            val repo = WataniRepository(context)
            var env = repo.getAuscultationEnvelope()
            if (env == null) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val entry = manifestRes.getOrNull()?.files?.get("auscultation.json")
                    if (entry != null) {
                        repo.downloadSection("auscultation.json", entry) { prog -> downloadProgress = prog }
                        env = repo.getAuscultationEnvelope()
                    }
                }
            }
            auscultationEnvelope = env
            if (env?.clips != null && env.clips.isNotEmpty()) {
                var arEnv = repo.getAuscultationArEnvelope()
                if (arEnv?.clips.isNullOrEmpty()) {
                    if (repo.downloadAuscultationAr().isSuccess) {
                        arEnv = repo.getAuscultationArEnvelope()
                    }
                }
                arabicClips = arEnv?.clips.orEmpty()
                    .mapNotNull { clip -> clip.id?.let { it to clip } }
                    .toMap()
                setupQuiz(env.clips)
            } else {
                arabicClips = emptyMap()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPack()
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    fun playAudio(clip: AuscultationClipDto) {
        val fileName = clip.file ?: return
        scope.launch {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null

                statusText = "جاري تجهيز الملف الصوتي..."
                val repo = WataniRepository(context)
                val res = repo.downloadAuscultationClip(fileName) { prog ->
                    downloadProgress = prog
                }
                if (res.isSuccess) {
                    val file = res.getOrThrow()
                    val mp = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener {
                            playingClipId = null
                        }
                    }
                    mediaPlayer = mp
                    playingClipId = clip.id
                }
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدرب الإصغاء السريري", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when {
                isLoading -> {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(statusText, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = MaroonPrimary)
                    }
                }
                auscultationEnvelope?.clips.isNullOrEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("تعذر تحميل حزمة الأصوات.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(shape = MaterialTheme.shapes.medium, onClick = { loadPack() }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)) {
                            Text("إعادة المحاولة")
                        }
                    }
                }
                else -> {
                    val clips = auscultationEnvelope!!.clips!!
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("مكتبة الأصوات الحقيقية", fontWeight = FontWeight.Bold) })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; setupQuiz(clips) }, text = { Text("اختبار الإصغاء (Quiz)", fontWeight = FontWeight.Bold) })
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (selectedTab == 0) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                                items(clips, key = { it.id ?: it.file ?: "" }) { clip ->
                                    val isPlaying = playingClipId == clip.id
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                IconButton(onClick = { playAudio(clip) }) {
                                                    Icon(
                                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = "تشغيل",
                                                        tint = MaroonPrimary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    val ar = arabicClips[clip.id ?: ""]
                                                    Text(ar?.arTitle ?: clip.label ?: "صوت إصغائي", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                    Text("الفئة: ${clip.category} • المصدر: ${clip.dataset ?: "CirCor/ICBHI"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                                    if (ar != null) {
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(ar.arDescription ?: "", fontSize = 14.sp, fontWeight = FontWeight.Normal)
                                                        if (clip.category == "lung") {
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                "ملاحظة: التشخيص المعروض هو تشخيص المريض المسجّل، وليس استنتاجًا من هذا الصوت.",
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaroonPrimary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Quiz Mode
                            val qClip = quizClip
                            if (qClip != null) {
                                val qAr = arabicClips[qClip.id ?: ""]
                                val qCorrect = qAr?.arTitle ?: qClip.label ?: ""
                                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("استمع للصوت وحدد النتيجة السريرية الأرجح:", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(shape = MaterialTheme.shapes.medium,
                                                onClick = { playAudio(qClip) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                                            ) {
                                                Icon(Icons.Default.VolumeUp, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("تشغيل مقطع الصوت للإصغاء")
                                            }
                                        }
                                    }
                                    quizOptions.forEach { opt ->
                                        val isSelected = selectedOption == opt
                                        val correct = qCorrect
                                        val bg = when {
                                            isSubmitted && opt == correct -> MaterialTheme.colorScheme.primaryContainer
                                            isSubmitted && isSelected && opt != correct -> MaterialTheme.colorScheme.errorContainer
                                            isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                        Card(
                                            modifier = Modifier.fillMaxWidth().clickable(enabled = !isSubmitted) { selectedOption = opt },
                                            colors = CardDefaults.cardColors(containerColor = bg)
                                        ) {
                                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = isSelected, onClick = { if (!isSubmitted) selectedOption = opt })
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(opt, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                    Button(shape = MaterialTheme.shapes.medium,
                                        onClick = {
                                            if (selectedOption != null) {
                                                isSubmitted = true
                                                val isCorrect = selectedOption == qCorrect
                                                OfflineStudyVault.recordQuestionAttempt(
                                                    context = context,
                                                    subjectTitle = "مدرب الإصغاء السريري",
                                                    questionId = qClip.id ?: "ausc_${System.currentTimeMillis()}",
                                                    questionText = "ما هو التشخيص السريري للإصغاء المطابق؟",
                                                    selectedOptionText = selectedOption!!,
                                                    correctOptionText = qCorrect,
                                                    explanation = qAr?.arDescription
                                                        ?: "التسجيل الصوتي الحقيقي يعكس: ${qClip.label ?: ""} (${qClip.category})",
                                                    isCorrect = isCorrect
                                                )
                                            }
                                        },
                                        enabled = selectedOption != null && !isSubmitted,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                                    ) {
                                        Text("تأكيد الإجابة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    if (isSubmitted) {
                                        Button(shape = MaterialTheme.shapes.medium,
                                            onClick = { setupQuiz(clips) },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                                        ) {
                                            Text("السؤال التالي ➔", fontWeight = FontWeight.Bold)
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
