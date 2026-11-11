package sy.medpro.watani.ui.ecg

import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sy.medpro.watani.data.AuscultationClipDto
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.RoseSurfaceVariant

/**
 * Auscultation quiz inside the cardiology course (build 1.2.4): ten
 * rounds, one random real clip each, four close label options
 * (same-category labels preferred as distractors), instant verdict
 * and a running score; the best score persists locally. Loading and
 * playback mirror AuscultationTrainerScreen exactly (manifest-driven
 * pack download, clips cached on device).
 */
@Composable
fun AuscultationQuizScreen(store: EcgProgressStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { WataniRepository(context) }

    var clips by remember { mutableStateOf<List<AuscultationClipDto>>(emptyList()) }
    var arabicTitles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }

    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var currentClip by remember { mutableStateOf<AuscultationClipDto?>(null) }
    var options by remember { mutableStateOf<List<String>>(emptyList()) }
    var usedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var picked by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    val totalRounds = 10

    fun titleOf(clip: AuscultationClipDto): String =
        arabicTitles[clip.id ?: ""] ?: clip.label ?: "صوت قلبي"

    fun stopPlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
    }

    fun nextRound() {
        stopPlayer()
        val pool = clips.filter { it.id !in usedIds }.ifEmpty { clips }
        if (pool.isEmpty() || round >= totalRounds) {
            finished = true
            store.recordAuscQuizScore(score)
            return
        }
        val clip = pool.random()
        currentClip = clip
        clip.id?.let { usedIds = usedIds + it }
        val correct = titleOf(clip)
        val sameCategory = clips.filter { it.category == clip.category && it.id != clip.id }
            .map { titleOf(it) }.distinct().filter { it != correct }
        val otherCategory = clips.filter { it.category != clip.category }
            .map { titleOf(it) }.distinct().filter { it != correct }
        val distractors = (sameCategory.shuffled() + otherCategory.shuffled()).take(3)
        options = (listOf(correct) + distractors).shuffled()
        picked = null
    }

    LaunchedEffect(Unit) {
        var env = repo.getAuscultationEnvelope()
        if (env == null) {
            val manifestRes = repo.fetchManifest()
            val entry = manifestRes.getOrNull()?.files?.get("auscultation.json")
            if (entry != null) {
                repo.downloadSection("auscultation.json", entry)
                env = repo.getAuscultationEnvelope()
            }
        }
        // 1.2.6: silently refresh a stale local pack (see
        // WataniRepository.refreshSectionIfStale).
        if (env != null && repo.refreshSectionIfStale("auscultation.json")) {
            env = repo.getAuscultationEnvelope() ?: env
        }
        val arEnv = repo.getAuscultationArEnvelope()
        arabicTitles = arEnv?.clips?.mapNotNull { clip ->
            val id = clip.id
            val title = clip.arTitle
            if (id != null && title != null) id to title else null
        }?.toMap() ?: emptyMap()
        clips = env?.clips ?: emptyList()
        isLoading = false
        if (clips.isEmpty()) {
            loadFailed = true
        } else {
            nextRound()
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopPlayer() }
    }

    fun playCurrent() {
        val clip = currentClip ?: return
        val fileName = clip.file ?: return
        if (isPlaying) {
            stopPlayer()
            return
        }
        scope.launch {
            preparing = true
            try {
                stopPlayer()
                val res = repo.downloadAuscultationClip(fileName)
                if (res.isSuccess) {
                    val file = res.getOrThrow()
                    val mp = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener { isPlaying = false }
                    }
                    mediaPlayer = mp
                    isPlaying = true
                }
            } catch (_: Exception) {
            }
            preparing = false
        }
    }

    EcgSubScaffold(title = "اختبار الإصغاء", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = MaroonPrimary)
                loadFailed -> Text("حزمة الأصوات غير متاحة — نزّلها أولًا من وحدة الإصغاء القلبي.", fontSize = 14.sp)
                finished -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = RoseSurfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("انتهى الاختبار", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                "نتيجتك: $score/$totalRounds — الأفضل حتى الآن: ${store.auscQuizBestScore()}/$totalRounds",
                                fontSize = 14.sp
                            )
                        }
                    }
                    Button(onClick = {
                        round = 0
                        score = 0
                        usedIds = emptySet()
                        finished = false
                        nextRound()
                    }, modifier = Modifier.fillMaxWidth()) { Text("اختبار جديد") }
                }
                else -> {
                    val clip = currentClip
                    Text(
                        "الصوت ${round + 1}/$totalRounds — النقاط: $score",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Button(
                        onClick = { playCurrent() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !preparing && clip != null
                    ) {
                        Text(
                            when {
                                preparing -> "جاري تجهيز الصوت..."
                                isPlaying -> "إيقاف الصوت"
                                else -> "▶ تشغيل الصوت"
                            }
                        )
                    }
                    Text("ما هذا الصوت؟", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                    val correct = clip?.let { titleOf(it) }
                    options.forEach { option ->
                        val answered = picked != null
                        val isCorrect = option == correct
                        val isPicked = picked == option
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !answered) {
                                picked = option
                                if (isCorrect) score++
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    !answered -> MaterialTheme.colorScheme.surface
                                    isCorrect -> RoseSurfaceVariant
                                    isPicked -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Text(
                                option + if (answered && isCorrect) " ✓" else if (answered && isPicked) " ✗" else "",
                                modifier = Modifier.padding(13.dp),
                                fontSize = 14.sp
                            )
                        }
                    }
                    if (picked != null) {
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = {
                            round++
                            if (round >= totalRounds) {
                                finished = true
                                store.recordAuscQuizScore(score)
                            } else {
                                nextRound()
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (round + 1 >= totalRounds) "النتيجة" else "الصوت التالي")
                        }
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("إنهاء الاختبار")
                    }
                }
            }
        }
    }
}
