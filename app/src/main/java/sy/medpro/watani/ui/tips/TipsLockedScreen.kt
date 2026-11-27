package sy.medpro.watani.ui.tips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.MaroonPrimary

// ---------------------------------------------------------------
// Section-switch lock (build 1.2.53): rendered by MainActivity
// instead of TipsRootScreen when the admin panel's tips switch
// says ACTIVATED (for a non-activated student) or LOCKED.
// (Own file: TipsScreens.kt sits at the detekt file-function
// ceiling — same split pattern as the 1.2.52 reader probe.)
// ---------------------------------------------------------------

@Composable
fun TipsLockedScreen(
    needsActivation: Boolean,
    onBack: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    DrawerScreenScaffold(title = "زوادة الطالب", onBack = onBack) { padding ->
        Column(
            modifier = modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (needsActivation) "زوادة الطالب للمفعّلين" else "زوادة الطالب مقفلة حاليًا",
                style = MaterialTheme.typography.titleMedium,
                color = MaroonPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (needsActivation) {
                    "فعّل حسابك بكود التفعيل لتفتح هذا القسم."
                } else {
                    "هذا القسم مقفل حاليًا — جرّب لاحقًا."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (needsActivation) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onActivate,
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) {
                    Text("تفعيل الحساب")
                }
            }
        }
    }
}
