package sy.medpro.watani.ui.anki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.ErrorRed
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.SuccessGreen
import kotlin.math.roundToInt

/**
 * FSRS grade row + card face text — the text-clipping fix
 * (build 1.2.55, doctor's device report on 1.2.54).
 *
 * The shipped 1.2.54 row used Modifier.weight(1f).height(50.dp)
 * Buttons with the DEFAULT Material3 content padding (24.dp on
 * each side): inside a phone-width row each button's inner text
 * width fell below the Arabic label's width, and the button clip
 * cut the final glyph («سهل» rendered «سه», «صعب» rendered «صع»).
 * This row keeps the four grades but gives the text its room:
 * 4.dp horizontal content padding, a 58.dp height that fits the
 * label AND the FSRS interval preview under it (the preview API
 * FsrsStore.previewIntervalDays existed since 1.2.54 but was
 * never wired into the buttons), and single-line labels that can
 * never wrap or overflow.
 */
@Composable
fun FsrsGradeButtons(
    intervals: Map<AnkiGrade, String>,
    onGrade: (AnkiGrade) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GradeButton(
            label = "إعادة",
            interval = intervals[AnkiGrade.AGAIN],
            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
            modifier = Modifier.weight(1f),
            onClick = { onGrade(AnkiGrade.AGAIN) }
        )
        GradeButton(
            label = "صعب",
            interval = intervals[AnkiGrade.HARD],
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.weight(1f),
            onClick = { onGrade(AnkiGrade.HARD) }
        )
        GradeButton(
            label = "جيد",
            interval = intervals[AnkiGrade.GOOD],
            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
            modifier = Modifier.weight(1f),
            onClick = { onGrade(AnkiGrade.GOOD) }
        )
        GradeButton(
            label = "سهل",
            interval = intervals[AnkiGrade.EASY],
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
            modifier = Modifier.weight(1f),
            onClick = { onGrade(AnkiGrade.EASY) }
        )
    }
}

@Composable
private fun GradeButton(
    label: String,
    interval: String?,
    colors: androidx.compose.material3.ButtonColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = colors,
        modifier = modifier.height(58.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            interval?.let {
                Text(
                    it,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    color = LocalContentColor.current.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * The flashcard face (front/back) text. At 20.sp the shipped code
 * set no lineHeight, so the text inherited bodyLarge's fixed
 * 26.sp line height (ratio 1.3) — too tight for stacked Arabic
 * diacritics, which overflowed the line box and clipped at line
 * tops («المجزّأً»). 31.sp (1.55×) with the theme's Trim.None
 * body style gives the marks their room.
 */
@Composable
fun FlashcardFaceText(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        lineHeight = 31.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

private const val MINUTES_PER_DAY = 24.0 * 60.0
private const val MINUTES_PER_HOUR = 60
private const val DAYS_PER_MONTH = 30.0
private const val WHOLE_TOLERANCE = 0.05
private const val TENTHS = 10

/** Student-facing FSRS interval label (Latin digits, app style). */
internal fun formatFsrsInterval(days: Double): String = when {
    days < 1.0 -> {
        val minutes = (days * MINUTES_PER_DAY).roundToInt().coerceAtLeast(1)
        if (minutes >= MINUTES_PER_HOUR) "${minutes / MINUTES_PER_HOUR} س" else "$minutes د"
    }
    days < DAYS_PER_MONTH -> wholeOrTenths(days, "يوم")
    else -> wholeOrTenths(days / DAYS_PER_MONTH, "شهر")
}

private fun wholeOrTenths(value: Double, unit: String): String {
    val whole = value.roundToInt()
    return if (kotlin.math.abs(value - whole) < WHOLE_TOLERANCE) "$whole $unit"
    else "${(value * TENTHS).roundToInt() / TENTHS.toDouble()} $unit"
}
