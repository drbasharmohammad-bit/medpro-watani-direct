package sy.medpro.watani.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import sy.medpro.watani.data.ProfileNameStore
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Entry gate (gate logic from build 1.2.3, presentation + name
 * capture from build 1.2.5, doctor's orders): MainActivity shows
 * this screen after the splash whenever there is NO locally
 * persisted Google session. A saved session never reaches it — the
 * gate checks local session presence only, never the network — so
 * daily offline entry for signed-in users is untouched. After
 * sign-in, free sections open at once; Questions/Library keep their
 * activation gate as before.
 *
 * Two modes, one screen:
 *  - Full mode ([existingUser] == null): a prominent name field on
 *    top of the card, then «المتابعة عبر Google». The name comes
 *    FIRST; an empty name never blocks entry — the Google account's
 *    displayName is stored instead (see ProfileNameStore).
 *  - Name-only mode ([existingUser] != null): a later sign-in whose
 *    account has no saved name yet sees the same card once, asking
 *    for the name only — no Google step, no network.
 *
 * The Google flow delegates to [GoogleSignInHelper], the same helper
 * used by the profile screen and the activation prompt.
 */
@Composable
fun SignInGate(
    onSignedIn: () -> Unit,
    existingUser: FirebaseUser? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var nameInput by remember(existingUser?.uid) {
        mutableStateOf(
            existingUser?.let {
                ProfileNameStore.savedName(context, it.uid) ?: (it.displayName ?: "")
            } ?: ""
        )
    }

    fun finishWithUser(user: FirebaseUser?) {
        val resolved = ProfileNameStore.resolveName(nameInput, user?.displayName)
        if (user != null) {
            if (resolved != null) {
                ProfileNameStore.saveName(context, user.uid, resolved)
            }
            ProfileNameStore.markNamePromptDone(context, user.uid)
        }
        onSignedIn()
    }

    fun signInWithGoogle() {
        scope.launch {
            isLoading = true
            errorMessage = null
            val result = GoogleSignInHelper.signInWithGoogle(context)
            isLoading = false
            result.fold(
                onSuccess = { finishWithUser(FirebaseAuth.getInstance().currentUser) },
                onFailure = { errorMessage = it.localizedMessage }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The official app logo (the launcher icon itself — the fixed
        // white M + gold plus on maroon). Rasterized from the installed
        // application icon so adaptive-icon resources render correctly;
        // painterResource on a mipmap can resolve to the anydpi XML.
        val appIconBitmap = remember(context) {
            try {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                val bmp = android.graphics.Bitmap.createBitmap(
                    240, 240, android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, 240, 240)
                drawable.draw(canvas)
                bmp.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
        if (appIconBitmap != null) {
            Image(
                bitmap = appIconBitmap,
                contentDescription = "شعار وطني",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaroonPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = androidx.compose.ui.graphics.Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "وطني",
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            color = MaroonPrimary
        )
        Text(
            "Med Pro",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "نجاحك في الوطني يبدأ من هنا",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 80) nameInput = it },
                    label = { Text("اسمك يا دكتور/ة") },
                    placeholder = { Text("الاسم الكامل") },
                    supportingText = {
                        Text("هيك رح نناديك جوّا التطبيق — وفيك تعدّله بأي وقت من ملفك الشخصي.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                if (existingUser != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "مسجّل الدخول: ${existingUser.email ?: ""}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (isLoading) {
                    CircularProgressIndicator(color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("جاري تسجيل الدخول...", fontSize = 13.sp)
                } else if (existingUser != null) {
                    Button(
                        onClick = { finishWithUser(existingUser) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("متابعة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    Button(
                        onClick = { signInWithGoogle() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("المتابعة عبر Google", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "الدخول بحساب Google مطلوب لفتح التطبيق — وجلستك المحفوظة بتضل تدخّلك أوفلاين كل يوم بلا ما تنقفل.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
