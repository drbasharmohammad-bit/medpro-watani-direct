package sy.medpro.watani.ui.referral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.ReferralMe
import sy.medpro.watani.data.ReferralReward
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The invite screen's section cards (build 1.2.54), split from
 * ReferralScreen.kt for the repo's per-file function ceiling.
 */

@Composable
fun ReferralCodeCard(
    me: ReferralMe,
    copied: Boolean,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaroonPrimary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("رمز الإحالة الخاص بك", color = GoldAccent, fontSize = 13.sp)
            Text(
                me.displayCode,
                color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCopy(me.displayCode) }) {
                    Text(if (copied) "تم النسخ" else "نسخ الرمز", color = Color.White)
                }
                Button(
                    onClick = { onShare(me.displayCode) },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) { Text("مشاركة", color = MaroonPrimary) }
            }
        }
    }
}

@Composable
fun ReferralCounterCard(me: ReferralMe) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "فعّل عن طريقك ${me.count} من ${me.threshold}",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary
            )
            Spacer(Modifier.height(8.dp))
            val progress = if (me.threshold > 0) {
                (me.count % me.threshold).toFloat() / me.threshold.toFloat()
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress },
                color = GoldAccent,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            if (me.remainingForNextReward > 0) {
                Text(
                    "بقي ${me.remainingForNextReward} تفعيلًا وتصلك مكافأة التفعيل المجاني",
                    fontSize = 13.sp, color = MaroonPrimary
                )
            }
            if (me.registeredCount > me.count) {
                Text(
                    "سجّل عن طريقك ${me.registeredCount} طالبًا، منهم " +
                        "${me.registeredCount - me.count} لم يُفعّلوا بعد — العدّاد يزيد عند التفعيل",
                    fontSize = 12.sp, color = MaroonPrimary
                )
            }
        }
    }
}

@Composable
fun ReferralRewardCard(reward: ReferralReward) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GoldAccent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "وصلتك مكافأة إحالة: تفعيل مجاني",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary
            )
            Text(
                if (reward.expiresAt > 0) {
                    "تفعيلك ساري حتى ${formatReferralDate(reward.expiresAt)}"
                } else {
                    "تفعيلك ساري بلا انتهاء"
                },
                fontSize = 13.sp, color = MaroonPrimary
            )
        }
    }
}

@Composable
fun ReferralAttributionBlock(state: ReferralUiState.Content, onClaim: (String) -> Unit) {
    val me = state.me
    when {
        me.referredByCode != null -> Text(
            "سجّلت برمز إحالة" +
                (me.referredByName?.let { " من $it" } ?: "") +
                " (${me.referredByCode})",
            fontSize = 12.sp, color = MaroonPrimary
        )
        me.canClaim -> ReferralClaimCard(state = state, onClaim = onClaim)
        else -> state.claimNotice?.let { notice ->
            Text(notice, fontSize = 13.sp, color = MaroonPrimary)
        }
    }
}

@Composable
fun ReferralClaimCard(state: ReferralUiState.Content, onClaim: (String) -> Unit) {
    var claimText by remember { mutableStateOf("") }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "عندك رمز إحالة من صديق؟",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaroonPrimary
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = claimText,
                onValueChange = { claimText = it },
                singleLine = true,
                label = { Text("رمز الإحالة") },
                modifier = Modifier.fillMaxWidth()
            )
            state.claimNotice?.let { notice ->
                Spacer(Modifier.height(6.dp))
                Text(
                    notice, fontSize = 13.sp,
                    color = if (state.claimSucceeded) MaroonPrimary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onClaim(claimText) },
                enabled = claimText.isNotBlank() && !state.claiming,
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.claiming) "جارٍ التسجيل…" else "تسجيل الرمز")
            }
        }
    }
}

fun formatReferralDate(epochMs: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ar")).format(Date(epochMs))
