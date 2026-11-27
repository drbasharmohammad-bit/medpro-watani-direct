package sy.medpro.watani.ui.anki

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.ErrorRed
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.SuccessGreen

/**
 * «بطاقاتي» home (build 1.2.54, doctor's order): the student authors
 * their own flashcards (front + back), stored locally per account,
 * and studies them with the same FSRS scheduler as the built-in
 * deck, with a daily due counter. Three internal states — list,
 * editor (create/edit), study session — with sequential back
 * navigation (session/editor step back to the list first).
 */
@Composable
fun UserCardsHome() {
    val context = LocalContext.current
    val cardsChange by UserCards.changes.collectAsState()
    val srsChange by FsrsStore.changes.collectAsState()
    val cards = remember(cardsChange) { UserCards.list(context) }
    val dueCount = remember(cards, srsChange) {
        FsrsStore.dueCount(context, cards.map { it.id })
    }

    var editing by remember { mutableStateOf<UserCard?>(null) }
    var creating by remember { mutableStateOf(false) }
    var sessionQueue by remember { mutableStateOf<List<UserCard>?>(null) }

    val inSubState = editing != null || creating || sessionQueue != null
    BackHandler(enabled = inSubState) {
        editing = null
        creating = false
        sessionQueue = null
    }

    when {
        creating || editing != null -> {
            UserCardEditor(
                initial = editing,
                onSave = { front, back ->
                    if (editing != null) {
                        UserCards.update(context, editing!!.id, front, back)
                    } else {
                        UserCards.add(context, front, back)
                    }
                    editing = null
                    creating = false
                },
                onCancel = {
                    editing = null
                    creating = false
                }
            )
        }
        sessionQueue != null -> {
            UserCardStudySession(
                queue = sessionQueue!!,
                onExit = { sessionQueue = null }
            )
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("بطاقاتي", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
                        Text(
                            when {
                                cards.isEmpty() -> "لم تنشئ أي بطاقة بعد — اكتب بطاقاتك بنفسك وادرسها بتكرار متباعد ذكي (FSRS)."
                                dueCount > 0 -> "لديك ${cards.size} بطاقة، منها $dueCount مستحقة اليوم."
                                else -> "لديك ${cards.size} بطاقة، ولا مستحقات اليوم."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Button(
                        onClick = { creating = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("بطاقة جديدة", fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (cards.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val due = FsrsStore.dueKeys(context, cards.map { it.id }).toSet()
                                val fresh = cards.filter { FsrsStore.state(context, it.id) == null }
                                val dueCards = cards.filter { it.id in due }
                                sessionQueue = dueCards + fresh
                            },
                            enabled = dueCount > 0 || cards.any { FsrsStore.state(context, it.id) == null },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) { Text("دراسة المستحقة ($dueCount)", fontSize = 13.sp) }
                        OutlinedButton(
                            onClick = { sessionQueue = cards },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) { Text("دراسة الكل", fontSize = 13.sp) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (cards.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Style, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "بطاقة جيدة = معلومة واحدة: الوجه يطلبها والخلف يعطيها.",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(cards, key = { it.id }) { card ->
                            val state = FsrsStore.state(context, card.id)
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(card.front, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
                                        Text(
                                            card.back,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            when {
                                                state == null -> "جديدة — لم تُدرس بعد"
                                                state.dueAtMillis <= System.currentTimeMillis() -> "مستحقة الآن"
                                                else -> "مراجعتها القادمة مجدولة"
                                            },
                                            fontSize = 11.sp,
                                            color = MaroonPrimary
                                        )
                                    }
                                    IconButton(onClick = { editing = card }) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaroonPrimary)
                                    }
                                    IconButton(onClick = { UserCards.delete(context, card.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ErrorRed)
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

@Composable
private fun UserCardEditor(
    initial: UserCard?,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var front by remember(initial) { mutableStateOf(initial?.front ?: "") }
    var back by remember(initial) { mutableStateOf(initial?.back ?: "") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (initial == null) "بطاقة جديدة" else "تعديل البطاقة",
            fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary
        )
        Text(
            "الوجه يطلب معلومة واحدة محددة، والخلف يعطي المعلومة نفسها بصياغة نظيفة.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        OutlinedTextField(
            value = front,
            onValueChange = { front = it },
            label = { Text("الوجه — ماذا تطلب البطاقة؟") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        OutlinedTextField(
            value = back,
            onValueChange = { back = it },
            label = { Text("الخلف — المعلومة") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(front, back) },
                enabled = front.isNotBlank() && back.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) { Text("حفظ البطاقة") }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) { Text("إلغاء") }
        }
    }
}

@Composable
private fun UserCardStudySession(queue: List<UserCard>, onExit: () -> Unit) {
    val context = LocalContext.current
    var index by remember(queue) { mutableStateOf(0) }
    var showAnswer by remember(queue) { mutableStateOf(false) }
    var reviewed by remember(queue) { mutableStateOf(0) }
    var useCounted by remember(queue) { mutableStateOf(false) }

    if (index >= queue.size) {
        // بوابة الأدوات (1.2.54): إنهاء جلسة بطاقاتي = استخدام واحد
        // لأداة البطاقات (مرة واحدة لكل جلسة).
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (reviewed > 0 && !useCounted) {
                useCounted = true
                sy.medpro.watani.data.ToolUsageStore.recordUse(context, "flashcards")
            }
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Style, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("أنهيت الجلسة", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
            Text("راجعت $reviewed بطاقة — ستعود إليك كل بطاقة في موعدها المجدول بـFSRS.", fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                shape = MaterialTheme.shapes.medium
            ) { Text("عودة لبطاقاتي") }
        }
        return
    }

    val card = queue[index]
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "بطاقة ${index + 1} من ${queue.size}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).clickable { showAnswer = !showAnswer }
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (!showAnswer) "الوجه (انقر لعرض الخلف):" else "الخلف:",
                    fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    if (!showAnswer) card.front else card.back,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (showAnswer) {
            fun gradeCurrent(grade: AnkiGrade) {
                FsrsStore.grade(context, card.id, grade)
                showAnswer = false
                reviewed++
                index++
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { gradeCurrent(AnkiGrade.AGAIN) },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text("إعادة", fontSize = 13.sp) }
                Button(
                    onClick = { gradeCurrent(AnkiGrade.HARD) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text("صعب", fontSize = 13.sp) }
                Button(
                    onClick = { gradeCurrent(AnkiGrade.GOOD) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text("جيد", fontSize = 13.sp) }
                Button(
                    onClick = { gradeCurrent(AnkiGrade.EASY) },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text("سهل", fontSize = 13.sp) }
            }
        } else {
            Button(
                onClick = { showAnswer = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("إظهار الخلف", fontSize = 16.sp) }
        }
        TextButton(onClick = onExit, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("إنهاء الجلسة")
        }
    }
}
