package sy.medpro.watani.ui.ecg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.EcgEnvelope
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.RoseSurfaceVariant

/**
 * Interactive lead map (build 1.2.4): tap a lead group and see which
 * heart wall it watches and which artery supplies it — the mental map
 * behind localising an infarction. Data comes from the pack's leadMap;
 * viewing every group marks the unit complete.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeadMapScreen(envelope: EcgEnvelope, store: EcgProgressStore, onBack: () -> Unit) {
    val entries = envelope.leadMap ?: emptyList()
    var selected by remember { mutableStateOf<Int?>(null) }
    var viewed by remember { mutableStateOf<Set<Int>>(emptySet()) }

    EcgSubScaffold(title = "خريطة الاتجاهات", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "انقر أي مجموعة اتجاهات لترى الجدار الذي تراقبه والشريان المغذّي له. مرّ على المجموعات كلها لإتمام الوحدة.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            entries.forEachIndexed { index, entry ->
                val isSelected = selected == index
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selected = index
                        viewed = viewed + index
                        if (viewed.size + 1 >= entries.size && entries.isNotEmpty()) {
                            store.markLeadMapDone()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) RoseSurfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (entry.leads ?: emptyList()).forEach { lead ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(lead, fontWeight = FontWeight.Bold) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
                            @Suppress("UNUSED_EXPRESSION") Unit
                        }
                        if (isSelected) {
                            Spacer(Modifier.height(8.dp))
                            entry.wallAr?.let {
                                Text("الجدار: $it", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary)
                            }
                            entry.arteryAr?.let {
                                Text("الشريان: $it", fontSize = 14.sp)
                            }
                        } else if (viewed.contains(index)) {
                            Text(
                                "✓ ${entry.wallAr ?: ""}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (entries.isNotEmpty() && viewed.size >= entries.size) {
                Text(
                    "أتممت خريطة الاتجاهات كلها ✓",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaroonPrimary
                )
            }
        }
    }
}
