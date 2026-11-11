package sy.medpro.watani.ui.ecg

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sy.medpro.watani.data.EcgEnvelope
import sy.medpro.watani.ui.isMediumWidth
import sy.medpro.watani.data.EcgPatternDto
import sy.medpro.watani.data.EcgStripDto
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.RoseSurfaceVariant
import kotlin.random.Random

private data class DrillQuestion(
    val strip: EcgStripDto,
    val correct: EcgPatternDto,
    val options: List<EcgPatternDto>
)

private fun buildQuestion(envelope: EcgEnvelope, store: EcgProgressStore, excludeStripIds: Set<String>): DrillQuestion? {
    val patterns = (envelope.patterns ?: emptyList()).filter { it.id != null }
    val patternsById = patterns.associateBy { it.id }
    val strips = (envelope.strips ?: emptyList()).filter { it.patternId != null && patternsById.containsKey(it.patternId) }
    if (strips.isEmpty() || patterns.size < 2) return null

    // Adaptive weighting: strips of often-missed patterns weigh more.
    val pool = strips.filter { it.id !in excludeStripIds }.ifEmpty { strips }
    val weighted = pool.flatMap { strip ->
        val w = store.drawWeight(strip.patternId!!).coerceIn(1, 6)
        List(w) { strip }
    }
    val strip = weighted[Random.nextInt(weighted.size)]
    val correct = patternsById[strip.patternId] ?: return null
    val sameCategory = patterns.filter { it.id != correct.id && it.category == correct.category }
    val others = patterns.filter { it.id != correct.id && it.category != correct.category }
    val distractors = (sameCategory.shuffled() + others.shuffled()).take(3)
    val options = (listOf(correct) + distractors).shuffled()
    return DrillQuestion(strip, correct, options)
}

/**
 * Flash drills (build 1.2.4): ten strips, ten seconds each, four
 * close options (same category first). Instant verdict; misses are
 * recorded per pattern and raise that pattern's draw weight in later
 * rounds and later sessions — the drill adapts to the student's own
 * errors. The end screen reviews every missed pattern.
 */
@Composable
fun FlashDrillScreen(
    envelope: EcgEnvelope,
    store: EcgProgressStore,
    onOpenPattern: (String) -> Unit,
    onBack: () -> Unit
) {
    val totalRounds = 10
    val secondsPerRound = 10

    var round by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf<DrillQuestion?>(null) }
    var usedStripIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var secondsLeft by remember { mutableIntStateOf(secondsPerRound) }
    var pickedId by remember { mutableStateOf<String?>(null) }
    var timedOut by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var missedPatterns by remember { mutableStateOf<List<EcgPatternDto>>(emptyList()) }
    var finished by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    fun nextRound() {
        val q = buildQuestion(envelope, store, usedStripIds)
        if (q == null || round >= totalRounds) {
            finished = true
            store.recordDrillScore(score, totalRounds)
            return
        }
        question = q
        q.strip.id?.let { usedStripIds = usedStripIds + it }
        pickedId = null
        timedOut = false
        secondsLeft = secondsPerRound
    }

    fun answer(patternId: String?) {
        val q = question ?: return
        if (pickedId != null) return
        if (patternId == null) timedOut = true
        pickedId = patternId ?: ""
        val correctId = q.correct.id
        if (patternId != null && patternId == correctId) {
            score++
            correctId?.let { store.recordHit(it) }
        } else {
            correctId?.let {
                store.recordMiss(it)
                if (missedPatterns.none { p -> p.id == it }) {
                    missedPatterns = missedPatterns + q.correct
                }
            }
        }
    }

    LaunchedEffect(started, round, pickedId, timedOut) {
        if (!started || finished || pickedId != null || timedOut) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        if (pickedId == null && !finished) {
            answer(null)
        }
    }

    EcgSubScaffold(title = "تدريب اللمحات", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!started) {
                Text(
                    "10 شرائط، لكل شريط 10 ثوانٍ لتلتقط التشخيص من لمحة واحدة. الخيارات الأربعة من عائلة متقاربة — والأنماط التي تخطئ فيها تعود إليك أكثر.",
                    fontSize = 14.sp
                )
                Button(onClick = {
                    started = true
                    nextRound()
                }, modifier = Modifier.fillMaxWidth()) { Text("ابدأ التدريب") }
                return@Column
            }
            if (finished) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = RoseSurfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("انتهى التدريب", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("نتيجتك: $score/$totalRounds — الأفضل حتى الآن: ${store.drillBestScore()}/$totalRounds", fontSize = 14.sp)
                    }
                }
                if (missedPatterns.isNotEmpty()) {
                    Text("أنماط أخطأت فيها — راجعها:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                    missedPatterns.forEach { pattern ->
                        OutlinedButton(
                            onClick = { pattern.id?.let { onOpenPattern(it) } },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(pattern.ar ?: pattern.en ?: "نمط") }
                    }
                }
                Button(onClick = {
                    round = 0
                    score = 0
                    missedPatterns = emptyList()
                    usedStripIds = emptySet()
                    finished = false
                    nextRound()
                }, modifier = Modifier.fillMaxWidth()) { Text("جولة جديدة") }
                return@Column
            }

            val q = question
            if (q == null) {
                Text("لا شرائط كافية للتدريب بعد.", fontSize = 14.sp)
                return@Column
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("الشريط ${round + 1}/$totalRounds", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    if (pickedId == null && !timedOut) "⏱ $secondsLeft ث" else "النقاط: $score",
                    fontSize = 14.sp,
                    color = MaroonPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { secondsLeft.toFloat() / secondsPerRound },
                modifier = Modifier.fillMaxWidth(),
                color = MaroonPrimary
            )
            EcgRhythmView(
                strip = q.strip,
                lead = q.strip.rhythmLead(),
                modifier = Modifier.fillMaxWidth().height(120.dp),
                interactive = false
            )
            val optionCard: @Composable (sy.medpro.watani.data.EcgPatternDto) -> Unit = { option ->
                val isCorrect = option.id == q.correct.id
                val isPicked = pickedId == option.id
                val answered = pickedId != null || timedOut
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !answered) {
                        answer(option.id)
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
                        (option.ar ?: option.en ?: "") +
                            if (answered && isCorrect) " ✓" else if (answered && isPicked) " ✗" else "",
                        modifier = Modifier.padding(13.dp),
                        fontSize = 14.sp
                    )
                }
            }
            // Tablet pass (1.2.5): answer options in two governed
            // columns at >=600 dp window width; stacked below it.
            if (isMediumWidth()) {
                q.options.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { option ->
                            Box(modifier = Modifier.weight(1f)) { optionCard(option) }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                q.options.forEach { option -> optionCard(option) }
            }
            if (pickedId != null || timedOut) {
                if (timedOut) {
                    Text("انتهى الوقت — الجواب: ${q.correct.ar ?: ""}", fontSize = 13.sp, color = MaroonPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Button(onClick = {
                    round++
                    if (round >= totalRounds) {
                        finished = true
                        store.recordDrillScore(score, totalRounds)
                    } else {
                        nextRound()
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (round + 1 >= totalRounds) "النتيجة" else "الشريط التالي")
                }
            }
        }
    }
}
