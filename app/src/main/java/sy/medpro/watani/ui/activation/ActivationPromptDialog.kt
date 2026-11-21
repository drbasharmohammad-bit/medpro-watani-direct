package sy.medpro.watani.ui.activation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import sy.medpro.watani.data.WorkerActivationApi
import sy.medpro.watani.ui.auth.GoogleSignInHelper
import sy.medpro.watani.ui.library.LibCard
import sy.medpro.watani.ui.library.LibColors
import sy.medpro.watani.ui.library.LibIconButton
import sy.medpro.watani.ui.library.libBackground
import sy.medpro.watani.ui.library.libHeading
import sy.medpro.watani.ui.library.libInk
import sy.medpro.watani.ui.library.libMuted
import sy.medpro.watani.ui.library.libSurface
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily

/**
 * Team 1 — activation prompt shown when a non-activated user opens the
 * Questions section or the Library.
 *
 * Flow: Google sign-in (only if not signed in) -> code entry ->
 * POST {WORKER_BASE}/v1/activate -> on success the caller unlocks the
 * requested section. On failure an ARABIC message is shown (M14: the
 * raw server `error` string is only logged, never displayed).
 *
 * If a previous token expired, one refresh is attempted first WITH a
 * visible status message ("جاري تجديد التفعيل...") — NOT silent, the
 * earlier "silent refresh" comment was wrong and is corrected here.
 * Only when the refresh proves revocation (server 401/403) is the
 * activation cleared, restricted downloads wiped (M4) and the code
 * asked for again; a mere network failure keeps the old state.
 *
 * Presentation (home-package round): a full-screen subscription sheet
 * transcribed from brand-premium/mockups/03-app-activation.png — slim
 * app header, hero logo, maroon price card, feature rows, code field
 * and the gold activate button. ONLY the presentation changed; the
 * activation state and logic above are byte-for-byte the old ones.
 */
@Composable
fun ActivationPromptDialog(
    onDismiss: () -> Unit,
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Visible refresh attempt for an expired token before asking for
    // the code again (status message shown above, not silent).
    LaunchedEffect(user) {
        val u = user ?: return@LaunchedEffect
        if (WorkerActivationApi.hasExpiredToken(context)) {
            isLoading = true
            message = "جاري تجديد التفعيل..."
            isError = false
            when (WorkerActivationApi.tryRefresh(context)) {
                sy.medpro.watani.data.RefreshOutcome.REFRESHED -> {
                    isLoading = false
                    onActivated()
                }
                sy.medpro.watani.data.RefreshOutcome.REVOKED -> {
                    // Activation is lost (M4): clear + wipe + ask code.
                    WorkerActivationApi.notifyActivationLost(context)
                    isLoading = false
                    message = "انتهى التفعيل السابق — أدخل كود تفعيل لتجديده. " +
                        "(حُذف المحتوى المقيّد المنزّل من الجهاز.)"
                }
                sy.medpro.watani.data.RefreshOutcome.UNAVAILABLE -> {
                    isLoading = false
                    message = "تعذر الاتصال بخادم التفعيل — تحقق من الشبكة ثم أعد المحاولة."
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(libBackground())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Slim app header (mockup .apphead): logo + «وطني» +
            // slogan; the model's search button becomes the sheet's
            // close button (the old dialog's «إلغاء» affordance).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ActivationLogo(size = 46.dp, radius = 14.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "وطني",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 21.sp, color = libHeading(), lineHeight = 28.sp
                    )
                    Text(
                        "نجاحك في الوطني يبدأ من هنا",
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 12.5.sp, color = libMuted()
                    )
                }
                LibIconButton(
                    Icons.Filled.Close, "إغلاق",
                    onClick = { if (!isLoading) onDismiss() }
                )
            }

            // Hero logo + titles.
            ActivationLogo(size = 92.dp, radius = 24.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "اشتراك وطني الكامل",
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp, color = libHeading(), textAlign = TextAlign.Center
                )
                Text(
                    "كل المحتوى... باشتراك واحد",
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 14.sp, color = libMuted(), textAlign = TextAlign.Center
                )
            }

            // Hero price card: maroon gradient, radius 22.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp))
                    .background(LibColors.MaroonBrush)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "اشتراك كامل لمدة 18 شهرًا",
                        fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = LibColors.GoldSoft,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "1,500",
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 56.sp, color = LibColors.Bg,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "ليرة جديدة — دفعة واحدة",
                        fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, color = LibColors.GoldSoft,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "بلا أي رسوم إضافية",
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "≈ 83 ليرة شهريًا فقط",
                        fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = LibColors.GoldSoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // Feature rows: white LibCards with a gold check disc.
            ActivationFeatureRow("بنك الوطني كاملًا بشروحاته — 8,513 سؤالًا")
            ActivationFeatureRow("كورس First Aid التفاعلي بمساريه")
            ActivationFeatureRow("المكتبة والكتيّبات — 581 فيديو · 245 كتيّبًا")
            ActivationFeatureRow("يعمل بلا إنترنت بعد التنزيل")

            // Activation area — same logic as the old dialog, fed by
            // the new field and the gold button.
            if (user == null) {
                Text(
                    "سجّل الدخول بحساب Google أولاً، ثم أدخل كود التفعيل.",
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 13.sp, color = libMuted(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                ActivationGoldButton(
                    text = "تسجيل الدخول بحساب Google",
                    enabled = !isLoading,
                    showSpinner = false,
                    onClick = {
                        isLoading = true
                        message = null
                        isError = false
                        scope.launch {
                            val result = GoogleSignInHelper.signInWithGoogle(context)
                            isLoading = false
                            result.fold(
                                onSuccess = {
                                    user = FirebaseAuth.getInstance().currentUser
                                    if (WorkerActivationApi.isActivated(context)) {
                                        onActivated()
                                    }
                                },
                                onFailure = {
                                    message = it.localizedMessage
                                    isError = true
                                }
                            )
                        }
                    }
                )
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = {
                        Text(
                            "أدخل كود التفعيل",
                            fontFamily = NotoSansArabicFontFamily, color = libMuted()
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = libSurface(),
                        unfocusedContainerColor = libSurface(),
                        focusedBorderColor = LibColors.Gold,
                        unfocusedBorderColor = LibColors.Line,
                        focusedTextColor = libInk(),
                        unfocusedTextColor = libInk()
                    )
                )
                ActivationGoldButton(
                    text = "تفعيل الاشتراك",
                    enabled = !isLoading,
                    showSpinner = isLoading,
                    onClick = {
                        val trimmed = code.trim()
                        if (trimmed.isBlank()) {
                            message = "يرجى إدخال كود تفعيل صالح أولاً."
                            isError = true
                            return@ActivationGoldButton
                        }
                        val currentUser = user ?: return@ActivationGoldButton
                        isLoading = true
                        message = "جاري التحقق من الكود عبر الخادم..."
                        isError = false
                        scope.launch {
                            val (success, msg) = WorkerActivationApi.activate(
                                context, currentUser, trimmed
                            )
                            isLoading = false
                            if (success) {
                                onActivated()
                            } else {
                                // Arabic user text from arabicActivationError
                                // (the raw server string is only logged).
                                message = msg
                                isError = true
                            }
                        }
                    }
                )
            }
            if (message != null) {
                Text(
                    message!!,
                    color = if (isError) MaterialTheme.colorScheme.error else libMuted(),
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Footer: payment line, then the slogan.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "الدفع عبر شام كاش لدى إدارة ميد برو",
                    fontFamily = NotoSansArabicFontFamily,
                    fontSize = 13.sp, color = libMuted(), textAlign = TextAlign.Center
                )
                Text(
                    "نجاحك في الوطني يبدأ من هنا",
                    fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, color = LibColors.GoldText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** One feature row of the mockup: white LibCard, gold check disc. */
@Composable
private fun ActivationFeatureRow(text: String) {
    LibCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(LibColors.GoldTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check, contentDescription = null,
                    tint = LibColors.GoldText, modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text,
                fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, color = libInk(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** The mockup's gold primary button (gold gradient, maroon-deep
 * Kufi text, 54dp, radius 16). [showSpinner] mirrors the old
 * activate button's in-button progress indicator. */
@Composable
private fun ActivationGoldButton(
    text: String,
    enabled: Boolean,
    showSpinner: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(if (enabled) 4.dp else 0.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (enabled) Modifier.background(LibColors.GoldBrush)
                else Modifier.background(LibColors.Gold.copy(alpha = 0.45f))
            )
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = LibColors.MaroonDeep
            )
        } else {
            Text(
                text,
                fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = LibColors.MaroonDeep,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The official app logo at an arbitrary size — the same bitmap
 * route as LibAppLogo (painterResource on the adaptive-icon mipmap
 * can resolve to the anydpi XML), parameterized because LibAppLogo
 * itself is fixed at the header's 46dp. */
@Composable
private fun ActivationLogo(size: Dp, radius: Dp) {
    val context = LocalContext.current
    val bitmap = remember(context) {
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
    Box(
        modifier = Modifier
            .size(size)
            .shadow(4.dp, RoundedCornerShape(radius), clip = false)
            .clip(RoundedCornerShape(radius))
            .background(LibColors.MaroonDeep),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "شعار وطني", modifier = Modifier.size(size))
        }
    }
}
