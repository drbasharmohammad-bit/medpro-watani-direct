package sy.medpro.watani.ui.ecg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.EcgEnvelope
import sy.medpro.watani.data.EcgMethodLessonDto
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * «منهج القراءة المنهجية» (build 1.2.5): the long-form reading
 * curriculum — numbered lessons from first principles to heavy
 * pathology, each with sourced sections, practice strips that open
 * in the existing guided reader, and a closing self-check list.
 * Content comes from the optional `methodLessons` field of ecg.json;
 * when the pack carries none, the home tile stays hidden and this
 * screen shows a plain empty state.
 */
@Composable
fun MethodCourseScreen(
    envelope: EcgEnvelope?,
    onOpenStrip: (String) -> Unit,
    onBack: () -> Unit
) {
    val lessons = envelope?.methodLessons ?: emptyList()
    var selectedLessonId by remember { mutableStateOf<String?>(null) }
    val selected = lessons.firstOrNull { it.id == selectedLessonId }

    if (selected != null) {
        MethodLessonDetail(
            envelope = envelope,
            lesson = selected,
            onOpenStrip = onOpenStrip,
            onBack = { selectedLessonId = null }
        )
        return
    }

    EcgSubScaffold(title = "منهج القراءة — من الأساس إلى الاحتراف", onBack = onBack) {
        if (lessons.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text("دروس المنهج لم تصل بعد — تُنزَّل تلقائيًا مع حزمة الكورس الكاملة.", fontSize = 14.sp)
            }
            return@EcgSubScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(lessons) { index, lesson ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedLessonId = lesson.id
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaroonPrimary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lesson.title ?: "درس", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                listOfNotNull(
                                    lesson.level,
                                    lesson.minutes?.takeIf { it > 0 }?.let { "$it دقيقة" },
                                    lesson.sections?.size?.takeIf { it > 0 }?.let { "$it أقسام" }
                                ).joinToString(" • "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaroonPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodLessonDetail(
    envelope: EcgEnvelope?,
    lesson: EcgMethodLessonDto,
    onOpenStrip: (String) -> Unit,
    onBack: () -> Unit
) {
    val stripsById = remember(envelope) { (envelope?.strips ?: emptyList()).associateBy { it.id } }
    val patternsById = remember(envelope) { (envelope?.patterns ?: emptyList()).associateBy { it.id } }
    // Self-check state is local to this visit (per parent spec:
    // interactive local checkboxes, nothing persisted).
    var checked by remember(lesson.id) { mutableStateOf<Set<Int>>(emptySet()) }

    EcgSubScaffold(title = lesson.title ?: "درس", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    listOfNotNull(
                        lesson.level,
                        lesson.minutes?.takeIf { it > 0 }?.let { "$it دقيقة" }
                    ).joinToString(" • "),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val sections = lesson.sections ?: emptyList()
            items(sections) { section ->
                Column {
                    section.h?.let {
                        Text(it, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        Spacer(Modifier.height(4.dp))
                    }
                    section.t?.let {
                        Text(it, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                    val points = section.points ?: emptyList()
                    points.forEach { point ->
                        Text("• $point", fontSize = 14.sp, lineHeight = 22.sp)
                    }
                    section.source?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "المصدر: $it",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            val stripIds = (lesson.stripIds ?: emptyList()).filter { stripsById.containsKey(it) }
            if (stripIds.isNotEmpty()) {
                item {
                    Text(
                        "شرائط هذا الدرس — افتحها بالقارئ الموجَّه",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaroonPrimary
                    )
                }
                items(stripIds) { stripId ->
                    val strip = stripsById[stripId]
                    val patternName = patternsById[strip?.patternId]?.ar
                    OutlinedButton(
                        onClick = { onOpenStrip(stripId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("  ${patternName ?: "شريط تدريبي"}")
                    }
                }
            }
            val checklist = lesson.checklist ?: emptyList()
            if (checklist.isNotEmpty()) {
                item {
                    Text(
                        "قائمة التحقق الختامية",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaroonPrimary
                    )
                }
                itemsIndexed(checklist) { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            checked = if (index in checked) checked - index else checked + index
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = index in checked,
                            onCheckedChange = { on ->
                                checked = if (on) checked + index else checked - index
                            },
                            colors = CheckboxDefaults.colors(checkedColor = MaroonPrimary)
                        )
                        Text(item, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
