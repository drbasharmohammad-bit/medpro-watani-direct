package sy.medpro.watani.ui.ecg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.EcgEnvelope
import sy.medpro.watani.ui.isMediumWidth
import sy.medpro.watani.data.EcgPatternDto
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Side-by-side learning (build 1.2.4): a NORM strip stacked directly
 * above a strip of the chosen pattern, same lead and same scale, with
 * the pattern's glance hooks listed beneath — pattern recognition is
 * built by comparison.
 */
@Composable
fun CompareScreen(envelope: EcgEnvelope, pattern: EcgPatternDto, onBack: () -> Unit) {
    val patternsById = remember(envelope) { (envelope.patterns ?: emptyList()).associateBy { it.id } }
    val normStrip = remember(envelope) {
        (envelope.strips ?: emptyList()).firstOrNull { strip ->
            val p = patternsById[strip.patternId]
            p?.en?.equals("NORM", ignoreCase = true) == true ||
                p?.id?.equals("norm", ignoreCase = true) == true
        }
    }
    val patternStrip = remember(envelope, pattern.id) {
        (envelope.strips ?: emptyList()).firstOrNull { it.patternId == pattern.id }
    }
    val lead = patternStrip?.rhythmLead() ?: "II"

    EcgSubScaffold(title = "المقارنة مع الطبيعي", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (normStrip == null || patternStrip == null) {
                Text("لا تتوفر شرائط مقارنة لهذا النمط بعد.", fontSize = 14.sp)
                return@Column
            }
            val normBlock: @Composable () -> Unit = {
            Text("الطبيعي", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
            EcgRhythmView(
                strip = normStrip,
                lead = lead,
                modifier = Modifier.fillMaxWidth().height(110.dp),
                interactive = false
            )
            }
            val patternBlock: @Composable () -> Unit = {
            Text(pattern.ar ?: "النمط", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
            EcgRhythmView(
                strip = patternStrip,
                lead = lead,
                modifier = Modifier.fillMaxWidth().height(110.dp),
                interactive = false
            )
            }
            // Tablet pass (1.2.5): at >=600 dp window width the two
            // strips sit side by side at the same scale; narrower
            // windows keep the stacked phone order.
            if (isMediumWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { normBlock() }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { patternBlock() }
                }
            } else {
                normBlock()
                patternBlock()
            }
            Text(
                "نفس الاتجاه ($lead) وبنفس المقياس — قارن شكل المركّب وقطعة ST وموجة T بين الشريطين.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val hooks = pattern.hooks ?: emptyList()
            if (hooks.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("ما الذي يختلف؟ علامات اللمحة:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                hooks.forEach { hook -> Text("• $hook", fontSize = 14.sp) }
            }
        }
    }
}
