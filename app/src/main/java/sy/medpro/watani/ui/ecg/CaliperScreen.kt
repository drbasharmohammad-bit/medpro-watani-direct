package sy.medpro.watani.ui.ecg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.EcgEnvelope
import sy.medpro.watani.ui.isExpandedWidth
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Measurement bench (build 1.2.4): draggable calipers over a rhythm
 * strip with live Δt / implied-rate readout, plus a Bazett QTc helper.
 * All math is EcgMath (unit-tested); this screen is presentation only.
 */
@Composable
fun CaliperScreen(envelope: EcgEnvelope, onBack: () -> Unit) {
    val strips = envelope.strips ?: emptyList()
    val patternsById = remember(envelope) { (envelope.patterns ?: emptyList()).associateBy { it.id } }
    var selectedStripId by remember { mutableStateOf(strips.firstOrNull()?.id) }
    val strip = strips.firstOrNull { it.id == selectedStripId } ?: strips.firstOrNull()

    var readout by remember { mutableStateOf("") }
    var qtText by remember { mutableStateOf("") }
    var rrText by remember { mutableStateOf("") }
    val qt = qtText.toDoubleOrNull()
    val rr = rrText.toDoubleOrNull()
    val qtc = if (qt != null && rr != null) EcgMath.qtcBazettMs(qt, rr) else null

    EcgSubScaffold(title = "الفرجار والقياس", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (strip == null) {
                Text("لا شرائط متاحة للقياس بعد.", fontSize = 14.sp)
                return@Column
            }
            // Strip picker: a wrapping FlowRow at natural chip width.
            // The previous plain Row squeezed every chip into the
            // remaining row width on narrow screens — the labels were
            // measured a few dp wide and stacked glyph-over-glyph in a
            // tall narrow box (doctor's device screenshot, 1.2.4).
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                strips.take(6).forEach { candidate ->
                    SuggestionChip(
                        onClick = { selectedStripId = candidate.id },
                        label = {
                            Text(
                                patternsById[candidate.patternId]?.ar ?: (candidate.id ?: "شريط"),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            val stripBlock: @Composable () -> Unit = {
            EcgCalipersView(
                strip = strip,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                onMeasurement = { ms, hr ->
                    readout = "Δt بين الفرجارين: ${ms.toInt()} مللي ثانية" +
                        if (hr > 0) "\nلو وُضعا على نبضتين متتاليتين: المعدّل ≈ ${hr.toInt()}/دقيقة" else ""
                }
            )
            if (readout.isNotBlank()) {
                Text(readout, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaroonPrimary)
            }
            Text(
                "تلميح: الفرجار يلتصق بأقرب قمّة عند إفلاته. جرّب قياس PR (من بداية P إلى بداية QRS) وQT (من بداية QRS إلى نهاية T).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
            val qtcCard: @Composable () -> Unit = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("حاسبة QTc (بازيت)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = qtText,
                            onValueChange = { qtText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("QT (ms)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = rrText,
                            onValueChange = { rrText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("RR (ms)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    if (qtc != null && qtc > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "QTc ≈ ${qtc.toInt()}ms" + when {
                                qtc > 470 -> " — مطوَّل، راجع الأسباب (شوارد/أدوية/خلقي)."
                                qtc < 350 -> " — قصير، راجع الشوارد والسياق."
                                else -> " — ضمن الحدود المعتادة."
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            }
            // Tablet pass (1.2.5): at ≥840 dp window width the strip and
            // the QTc bench sit side by side; narrower windows keep the
            // stacked phone order. Width-keyed, never orientation-keyed.
            if (isExpandedWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        modifier = Modifier.weight(1.3f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { stripBlock() }
                    Box(modifier = Modifier.weight(1f)) { qtcCard() }
                }
            } else {
                stripBlock()
                qtcCard()
            }
        }
    }
}
