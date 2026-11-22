package sy.medpro.watani.ui.firstaid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.FirstAidGlossaryDto
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily

/** One card in a review deck: its owning unit + index inside that
 * unit's flashcards (the FirstAidSrs key is "<unitId>#<index>"). */
data class FaDeckCard(
    val unitId: String,
    val cardIndex: Int,
    val front: String,
    val back: String
)

/**
 * Flashcard player (merge phase 5): tap to flip, then self-grade —
 * «أعرفها» advances the card on the packs' own repetition ladder,
 * «لا أعرفها» lapses it to 1 day, both through [FirstAidSrs]. Used
 * for a unit's full deck and for the section's due-today review
 * (cards grouped from every unit via FirstAidSrs.dueKeysAll).
 * [onDeckFinished] fires once when the last card is graded.
 */
@Composable
fun FirstAidFlashcardsPlayer(
    title: String,
    cards: List<FaDeckCard>,
    onClose: () -> Unit,
    onDeckFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    var index by remember(cards) { mutableIntStateOf(0) }
    var flipped by remember(cards) { mutableStateOf(false) }
    var finished by remember(cards) { mutableStateOf(false) }

    fun grade(correct: Boolean) {
        val card = cards[index]
        FirstAidSrs.recordReview(context, card.unitId, card.cardIndex, correct = correct)
        if (index < cards.lastIndex) {
            index++
            flipped = false
        } else {
            finished = true
            onDeckFinished()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(faBackground())) {
        FaScreenHeader(
            title = title,
            subtitle = if (cards.isEmpty()) null else "البطاقة ${minOf(index + 1, cards.size)} من ${cards.size}",
            onBack = onClose
        )
        when {
            cards.isEmpty() -> Text(
                "لا بطاقات مستحقة الآن — أحسنت. ستظهر هنا البطاقات عند استحقاقها.",
                fontFamily = NotoSansArabicFontFamily, fontSize = 13.5.sp,
                color = faMuted(), modifier = Modifier.padding(top = 12.dp)
            )
            finished -> {
                FaCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "انتهت مراجعة البطاقات",
                            fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = faHeading()
                        )
                        Text(
                            "راجعت ${cards.size} بطاقة — جُدولت كل بطاقة على سلّم التكرار الخاص بالكورس.",
                            fontFamily = NotoSansArabicFontFamily, fontSize = 13.sp, color = faMuted()
                        )
                    }
                }
                FaPrimaryButton(
                    text = "عودة",
                    modifier = Modifier.padding(top = 12.dp),
                    onClick = onClose
                )
            }
            else -> {
                val card = cards[index]
                FaProgressBar(
                    fraction = (index + 1).toFloat() / cards.size.toFloat(),
                    gold = true,
                    modifier = Modifier.padding(top = 6.dp)
                )
                FaCard(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    onClick = { flipped = !flipped }
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FaChip(
                            if (flipped) "الإجابة" else "السؤال — انقر لقلب البطاقة",
                            faGoldTint(), faGoldText()
                        )
                        Text(
                            if (flipped) card.back else card.front,
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 16.sp, lineHeight = 28.sp, color = faInk()
                        )
                    }
                }
                if (flipped) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FaGradeButton(
                            text = "لا أعرفها",
                            container = faRedTint(), fg = faRedText(),
                            modifier = Modifier.weight(1f)
                        ) { grade(correct = false) }
                        FaGradeButton(
                            text = "أعرفها",
                            container = faGreenTint(), fg = faGreenText(),
                            modifier = Modifier.weight(1f)
                        ) { grade(correct = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaGradeButton(
    text: String,
    container: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = container
    ) {
        Text(
            text, fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, color = fg,
            modifier = Modifier.padding(vertical = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}

/** Unit glossary screen: Arabic term, English term, note. */
@Composable
fun FirstAidGlossaryScreen(
    title: String,
    glossary: List<FirstAidGlossaryDto>,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(faBackground())) {
        FaScreenHeader(title = "قاموس الوحدة", subtitle = title, onBack = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(glossary) { _, term ->
                FaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                term.ar.orEmpty(),
                                fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 14.sp, color = faAccentText()
                            )
                            if (!term.en.isNullOrBlank()) {
                                Text(
                                    term.en,
                                    fontFamily = NotoSansArabicFontFamily,
                                    fontSize = 12.5.sp, color = faMuted()
                                )
                            }
                        }
                        if (!term.note.isNullOrBlank()) {
                            Text(
                                term.note,
                                fontFamily = NotoSansArabicFontFamily,
                                fontSize = 12.3.sp, lineHeight = 20.sp, color = faInk()
                            )
                        }
                    }
                }
            }
            if (glossary.isEmpty()) {
                item {
                    Text(
                        "لا مصطلحات في قاموس هذه الوحدة.",
                        fontFamily = NotoSansArabicFontFamily, fontSize = 13.sp, color = faMuted()
                    )
                }
            }
        }
    }
}
