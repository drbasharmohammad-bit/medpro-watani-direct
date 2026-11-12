package sy.medpro.watani.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Phase-2 hardening (Nov 2026): the min-version block screen. Shown
 * instead of the whole app UI when the installed versionCode is below
 * the gateway's `minVersion` (see data/AppVersionGate for the exact
 * offline semantics). Arabic, explicit, and with no path around it —
 * an outdated build must not reach restricted content.
 */
@Composable
fun UpdateRequiredScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = MaroonPrimary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "يلزم تحديث التطبيق",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = MaroonPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "هذه النسخة من التطبيق قديمة ولم تعد مدعومة. حدّث التطبيق إلى أحدث نسخة من قناة Med Pro الرسمية على تيليجرام للمتابعة.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}
