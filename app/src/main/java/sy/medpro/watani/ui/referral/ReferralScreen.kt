package sy.medpro.watani.ui.referral

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sy.medpro.watani.data.ReferralGateway
import sy.medpro.watani.data.ReferralMe
import sy.medpro.watani.data.WorkerReferralGateway
import sy.medpro.watani.data.referralClaimErrorMessage
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «ادعُ أصدقاءك» (build 1.2.54): the student's referral home —
 * their personal code with copy/share, the ACTIVATED-referrals
 * counter («فعّل عن طريقك X من 7» — registrations alone never
 * move it, per the doctor's corrected spec), any referral-reward
 * activations earned, and the one-time claim field for a new
 * account that has not entered a referrer's code yet.
 *
 * Premium identity: ivory surfaces, burgundy headers/accents,
 * antique-gold progress and reward cards — never a saturated
 * full-screen wash.
 */

sealed interface ReferralUiState {
    data object Loading : ReferralUiState
    data object Failed : ReferralUiState
    data class Content(
        val me: ReferralMe,
        val claiming: Boolean = false,
        val claimNotice: String? = null,
        val claimSucceeded: Boolean = false,
        val copied: Boolean = false
    ) : ReferralUiState
}

@Composable
fun ReferralRoute(
    onBack: () -> Unit,
    gateway: ReferralGateway = remember { WorkerReferralGateway() }
) {
    var state by remember { mutableStateOf<ReferralUiState>(ReferralUiState.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        val reply = gateway.fetchMine()
        state = when {
            reply.isSuccess && reply.me != null -> ReferralUiState.Content(reply.me)
            else -> ReferralUiState.Failed
        }
    }

    val context = LocalContext.current
    ReferralScreen(
        state = state,
        onBack = onBack,
        onRetry = { state = ReferralUiState.Loading; refreshKey++ },
        onCopy = { code ->
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("referral", code))
            val s = state
            if (s is ReferralUiState.Content) state = s.copy(copied = true)
        },
        onShare = { displayCode ->
            val text = "سجّل في تطبيق Med Pro وأدخل رمز الإحالة الخاص بي عند التسجيل: $displayCode"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة رمز الإحالة"))
        },
        onClaim = { rawCode ->
            val s = state
            if (s is ReferralUiState.Content && !s.claiming) {
                state = s.copy(claiming = true, claimNotice = null)
                scope.launch {
                    val reply = gateway.claim(rawCode)
                    state = if (reply.isSuccess) {
                        // Refresh so referredBy/canClaim reflect the server.
                        val meReply = gateway.fetchMine()
                        if (meReply.isSuccess && meReply.me != null) {
                            ReferralUiState.Content(
                                meReply.me,
                                claimNotice = "تم تسجيل رمز الإحالة بنجاح",
                                claimSucceeded = true
                            )
                        } else {
                            s.copy(claiming = false, claimNotice = "تم تسجيل رمز الإحالة بنجاح", claimSucceeded = true)
                        }
                    } else {
                        s.copy(claiming = false, claimNotice = referralClaimErrorMessage(reply.error))
                    }
                }
            }
        }
    )
}

@Composable
fun ReferralScreen(
    state: ReferralUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onClaim: (String) -> Unit
) {
    DrawerScreenScaffold(title = "ادعُ أصدقاءك", onBack = onBack) {
        when (state) {
            ReferralUiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = MaroonPrimary)
                Spacer(Modifier.height(12.dp))
                Text("جارٍ تحميل بيانات الإحالة…", color = MaroonPrimary)
            }
            ReferralUiState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "تعذّر تحميل بيانات الإحالة — تحقق من الاتصال وحاول مجددًا",
                    color = MaroonPrimary, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) { Text("إعادة المحاولة") }
            }
            is ReferralUiState.Content -> ReferralContent(state, onCopy, onShare, onClaim)
        }
    }
}

@Composable
private fun ReferralContent(
    state: ReferralUiState.Content,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onClaim: (String) -> Unit
) {
    val me = state.me
    var claimText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "شارك رمزك مع أصدقائك، وعندما يُفعّل ${me.threshold} منهم حساباتهم " +
                "يصلك تفعيل مجاني لمدة 18 شهرًا تلقائيًا.",
            fontSize = 14.sp, color = MaroonPrimary
        )

        // ── My code ──
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
                        Text(if (state.copied) "تم النسخ" else "نسخ الرمز", color = Color.White)
                    }
                    Button(
                        onClick = { onShare(me.displayCode) },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                    ) { Text("مشاركة", color = MaroonPrimary) }
                }
            }
        }

        // ── Counter (ACTIVATED referrals only) ──
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
                } else 0f
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
                        "سجّل عن طريقك ${me.registeredCount} طالبًا، منهم ${me.registeredCount - me.count} لم يُفعّلوا بعد — العدّاد يزيد عند التفعيل",
                        fontSize = 12.sp, color = MaroonPrimary
                    )
                }
            }
        }

        // ── Rewards earned ──
        for (reward in me.rewards) {
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
        if (me.rewards.isEmpty() && me.activationActivated && me.activationExpiresAt > 0) {
            Text(
                "حسابك مفعّل حاليًا حتى ${formatReferralDate(me.activationExpiresAt)}",
                fontSize = 12.sp, color = MaroonPrimary
            )
        }

        // ── Who referred me / claim a code ──
        if (me.referredByCode != null) {
            Text(
                "سجّلت برمز إحالة" +
                    (me.referredByName?.let { " من $it" } ?: "") +
                    " (${me.referredByCode})",
                fontSize = 12.sp, color = MaroonPrimary
            )
        } else if (me.canClaim) {
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
                            color = if (state.claimSucceeded) MaroonPrimary else Color(0xFFB3261E)
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
        } else {
            state.claimNotice?.let { notice ->
                Text(notice, fontSize = 13.sp, color = MaroonPrimary)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun formatReferralDate(epochMs: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ar")).format(Date(epochMs))
