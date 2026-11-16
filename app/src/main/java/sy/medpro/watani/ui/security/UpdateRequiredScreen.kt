package sy.medpro.watani.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.io.File
import kotlinx.coroutines.launch
import sy.medpro.watani.data.AppUpdate
import sy.medpro.watani.data.WorkerActivationApi
import sy.medpro.watani.ui.theme.CairoFontFamily
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Phase-2 hardening (Nov 2026): the min-version block screen. Shown
 * instead of the whole app UI when the installed versionCode is below
 * the gateway's `minVersion` (see data/AppVersionGate for the exact
 * offline semantics). Arabic, explicit, and with no path around it —
 * an outdated build must not reach restricted content.
 *
 * Build 1.2.28 (doctor's order): the screen now carries a «تحميل
 * التحديث وتثبيته» button — the student no longer hunts for the new
 * build on Telegram. The button downloads the new APK from the fixed
 * update address (data/AppUpdate; learned from GET /v1/config or the
 * built-in fallback) with a progress bar, then hands it to the
 * system installer, which installs it OVER this build (same signing
 * certificate — no data loss). On Android 8+ the first run detours
 * once through the system «install unknown apps» settings page for
 * this app; afterwards the button installs directly.
 */
private enum class UpdatePhase { Idle, Downloading, Downloaded, Error }

@Composable
fun UpdateRequiredScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(UpdatePhase.Idle) }
    var progress by remember { mutableStateOf(0f) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var needsPermission by remember { mutableStateOf(false) }

    fun launchInstall(file: File) {
        if (AppUpdate.canInstallPackages(context)) {
            needsPermission = false
            try {
                context.startActivity(AppUpdate.installIntent(context, file))
            } catch (_: Exception) {
            }
        } else {
            // One-time detour: the system page where the user allows
            // this app to install packages; the button then installs.
            needsPermission = true
            try {
                context.startActivity(AppUpdate.installPermissionIntent(context))
            } catch (_: Exception) {
            }
        }
    }

    fun startDownload() {
        phase = UpdatePhase.Downloading
        progress = 0f
        scope.launch {
            try {
                val file = AppUpdate.downloadApk(
                    context,
                    WorkerActivationApi.knownApkUrl(context)
                ) { downloaded, total ->
                    if (total > 0) progress = downloaded.toFloat() / total.toFloat()
                }
                downloadedFile = file
                phase = UpdatePhase.Downloaded
                launchInstall(file)
            } catch (_: Exception) {
                phase = UpdatePhase.Error
            }
        }
    }

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
            "هذه النسخة من التطبيق قديمة ولم تعد مدعومة. حمّل النسخة الجديدة من هنا وستُثبّت فوق نسختك الحالية مباشرة — دون أن تفقد أيًّا من بياناتك أو تقدّمك.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        when (phase) {
            UpdatePhase.Downloading -> {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaroonPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "جاري تحميل التحديث... ${(progress * 100).toInt()}٪",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            UpdatePhase.Error -> {
                Text(
                    "تعذّر تحميل التحديث — تحقق من اتصالك بالإنترنت وحاول مجددًا.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { startDownload() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Text(
                        "إعادة المحاولة",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
            UpdatePhase.Downloaded -> {
                if (needsPermission) {
                    Text(
                        "فعّل خيار «تثبيت التطبيقات» لتطبيق وطني من صفحة الإعدادات التي فُتحت لك، ثم ارجع واضغط الزرّ من جديد ليبدأ التثبيت.",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
                Button(
                    onClick = { downloadedFile?.let { launchInstall(it) } },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Text(
                        "تثبيت التحديث الآن",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
            UpdatePhase.Idle -> {
                Button(
                    onClick = { startDownload() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Text(
                        "تحميل التحديث وتثبيته",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "أو حمّل أحدث نسخة من قناة Med Pro الرسمية على تيليجرام.",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
