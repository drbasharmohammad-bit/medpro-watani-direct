package sy.medpro.watani.ui.explanation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.GoldAccentDark
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * One shared explanation section shown after a question is answered.
 * Renders only what the layer actually provides - never an empty card, never a
 * placeholder, never fake content. When there is no explanation it renders nothing.
 *
 * Order (all RTL Arabic):
 *   - Medium-confidence badge when confidence == "medium" ("ثقة متوسطة").
 *   - Long summary block.
 *   - Why the correct answer is correct.
 *   - Why each wrong option is wrong - one entry per option letter present in the
 *     data (any count, any letters, not hardcoded to 4), ordered by optionLetterRank.
 *   - Sources list - shown as given, never invented.
 *   - Flags, when the layer provides them.
 */
@Composable
fun ExplanationSection(content: ExplanationContent?, modifier: Modifier = Modifier) {
    if (content == null || !content.hasAnyText) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.25f)), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        // Medium-confidence badge - visible, never silent.
        if (content.isMediumConfidence) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(16.dp))
                Text(
                    text = "ثقة متوسطة",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaroonPrimary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Long summary block.
        val summary = content.summary
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Why the correct answer is correct.
        val whyCorrect = content.whyCorrect
        if (!whyCorrect.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "لماذا الإجابة الصحيحة صحيحة:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaroonPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = whyCorrect,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Why each wrong option is wrong - the letters and their texts come entirely
        // from the layer data (any count, any letters).
        if (content.whyWrong.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "لماذا الخيارات الأخرى غير صحيحة:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaroonPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                content.whyWrong.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${pair.first}.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaroonPrimary
                        )
                        Text(
                            text = pair.second,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Sources list, shown exactly as given.
        if (content.sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "المصادر:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaroonPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            content.sources.forEach { source ->
                Text(
                    text = source,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }

        // Flags, when the layer provides them.
        if (content.flags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = content.flags.joinToString(separator = "  •  "),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GoldAccentDark
            )
        }
    }
}
