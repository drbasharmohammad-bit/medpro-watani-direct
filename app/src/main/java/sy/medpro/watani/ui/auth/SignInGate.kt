package sy.medpro.watani.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import sy.medpro.watani.R
import sy.medpro.watani.data.PrivacyConsent
import sy.medpro.watani.data.ProfileNameStore
import sy.medpro.watani.ui.drawer.PrivacyPolicyScreen
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.DarkMaroonOutline
import sy.medpro.watani.ui.theme.ErrorRed
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonDark
import sy.medpro.watani.ui.theme.MaroonInk
import sy.medpro.watani.ui.theme.MaroonLight
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.RoseBackground
import sy.medpro.watani.ui.theme.RoseOutline
import sy.medpro.watani.ui.theme.TajawalFontFamily
import sy.medpro.watani.ui.theme.TextOnDark
import sy.medpro.watani.ui.theme.ThmanyahFontFamily

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
 *
 * Luxury redesign (doctor's order, Nov 2026 — «فخمة مثل التطبيقات
 * العالمية»): the gate logic above is byte-for-byte the old one;
 * only the presentation changed. A deep-maroon immersive hero in the
 * splash's own dark family (#2D1015 ← #42091A ← #5E1224, with a
 * Premium-gold glow behind the logo) carries the official app icon
 * as the hero, the «وطني» wordmark and the official slogan; an ivory
 * sheet with 32dp top corners holds the form. The Google button now
 * follows Google's branding guidelines literally (white surface,
 * neutral border, the official multicolour "G" at the start side,
 * «تسجيل الدخول عبر Google» centred) and owns the loading state;
 * errors arrive as a soft banner inside the sheet, never raw text.
 * Entrance is a short staggered fade/rise (alpha + translation
 * only, cheap on weak devices).
 *
 * Privacy consent (build 1.2.29, doctor's correction — the global
 * apps' pattern): NO separate consent screen. In full mode a
 * consent row sits directly under the Google button — a checkbox
 * plus «أوافق على سياسة الخصوصية», the policy phrase itself a link
 * opening the full document — and the Google button stays disabled
 * until the box is ticked. The tick is stored per policy version
 * (data/PrivacyConsent): already accepted → pre-ticked; a material
 * policy update → unticked again. Name-only mode keeps the 1.2.19
 * consent line: its user already holds a session, and a saved
 * session is never gated or touched.
 */
@Composable
fun SignInGate(
    onSignedIn: () -> Unit,
    existingUser: FirebaseUser? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = sy.medpro.watani.ui.theme.wataniDarkTheme()
    // Privacy/terms screen (build 1.2.19): opened from the consent
    // links under the sign-in button, scrolled to the tapped part
    // (0 privacy / 1 terms / 2 medical disclaimer); a local swap
    // because the gate lives outside the main navigation, and back
    // returns here.
    var policyAnchor by remember { mutableStateOf<Int?>(null) }
    policyAnchor?.let { anchor ->
        PrivacyPolicyScreen(onBack = { policyAnchor = null }, anchorPart = anchor)
        return
    }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var nameInput by remember(existingUser?.uid) {
        mutableStateOf(
            existingUser?.let {
                ProfileNameStore.savedName(context, it.uid) ?: (it.displayName ?: "")
            } ?: ""
        )
    }
    // Consent checkbox (build 1.2.29): starts ticked when this
    // device already accepted the current policy version, unticked
    // otherwise; ticking records the acceptance immediately, keyed
    // by the document version (data/PrivacyConsent).
    var consentChecked by remember {
        mutableStateOf(PrivacyConsent.initialCheckboxChecked(PrivacyConsent.acceptedVersion(context)))
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

    // Entrance choreography: one trigger, staggered fades/rises.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    fun entrance(delayMs: Int, riseFraction: Float) = fadeIn(
        animationSpec = tween(durationMillis = 550, delayMillis = delayMs)
    ) + slideInVertically(
        animationSpec = tween(durationMillis = 550, delayMillis = delayMs)
    ) { (it * riseFraction).toInt() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to MaroonInk,
                    0.48f to MaroonDark,
                    1.0f to MaroonPrimary
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------- Hero: logo, wordmark, slogan ----------
            AnimatedVisibility(visible = shown, enter = entrance(0, 0.22f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(48.dp))
                    // The official app logo (the launcher icon itself —
                    // the fixed white M + gold plus on maroon).
                    // Rasterized from the installed application icon so
                    // adaptive-icon resources render correctly;
                    // painterResource on a mipmap can resolve to the
                    // anydpi XML.
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
                    Box(contentAlignment = Alignment.Center) {
                        // Premium-gold halo behind the logo (brand
                        // accent only — never a yellow UI surface).
                        Box(
                            modifier = Modifier
                                .size(300.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            GoldAccent.copy(alpha = 0.20f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                )
                        )
                        if (appIconBitmap != null) {
                            Image(
                                bitmap = appIconBitmap,
                                contentDescription = "شعار وطني",
                                modifier = Modifier
                                    .size(112.dp)
                                    .shadow(18.dp, RoundedCornerShape(28.dp))
                                    .clip(RoundedCornerShape(28.dp))
                                    .border(
                                        1.dp,
                                        GoldAccent.copy(alpha = 0.55f),
                                        RoundedCornerShape(28.dp)
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(112.dp)
                                    .shadow(18.dp, RoundedCornerShape(28.dp))
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaroonInk)
                                    .border(
                                        1.dp,
                                        GoldAccent.copy(alpha = 0.55f),
                                        RoundedCornerShape(28.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "M",
                                    color = Color.White,
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "وطني",
                        fontFamily = ThmanyahFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        color = TextOnDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "MED PRO",
                        style = TextStyle(
                            fontFamily = NotoKufiFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 4.5.sp,
                            color = GoldAccent
                        )
                    )
                }
            }
            AnimatedVisibility(visible = shown, enter = entrance(140, 0.22f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "نجاحك في الوطني يبدأ من هنا",
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        color = TextOnDark.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    // Hairline divider with a centre diamond.
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .width(74.dp)
                                .height(1.5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            GoldAccent,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .rotate(45f)
                                .background(GoldAccent)
                        )
                    }
                    Spacer(modifier = Modifier.height(26.dp))
                }
            }

            // ---------- Sheet: the form ----------
            AnimatedVisibility(visible = shown, enter = entrance(220, 0.10f)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 452.dp),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "أهلاً بك",
                                fontFamily = NotoKufiFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "سجّل دخولك بحساب Google لفتح التطبيق ومتابعة دراستك",
                                fontFamily = TajawalFontFamily,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.height(22.dp))
                            Text(
                                "اسمك يا دكتور/ة",
                                fontFamily = NotoKufiFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (dark) MaroonLight else MaroonPrimary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { if (it.length <= 80) nameInput = it },
                                placeholder = { Text("الاسم الكامل") },
                                supportingText = {
                                    Text("هيك رح نناديك جوّا التطبيق — وفيك تعدّله بأي وقت من ملفك الشخصي.")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaroonPrimary,
                                    unfocusedBorderColor = if (dark) DarkMaroonOutline else RoseOutline,
                                    focusedContainerColor = if (dark) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        RoseBackground
                                    },
                                    unfocusedContainerColor = if (dark) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        RoseBackground
                                    }
                                )
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
                            AnimatedVisibility(visible = errorMessage != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(ErrorRed.copy(alpha = 0.08f))
                                        .border(
                                            1.dp,
                                            ErrorRed.copy(alpha = 0.28f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = ErrorRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        errorMessage
                                            ?: "تعذّر تسجيل الدخول — تحقق من الاتصال وحاول مجددًا.",
                                        color = ErrorRed,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (existingUser != null) {
                                Button(
                                    onClick = { finishWithUser(existingUser) },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaroonPrimary
                                    ),
                                    shape = RoundedCornerShape(percent = 50)
                                ) {
                                    Text(
                                        "متابعة",
                                        fontFamily = NotoKufiFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                PrivacyConsentLine(onOpenPolicy = { policyAnchor = it })
                            } else {
                                // Google's branding guidelines, literally:
                                // white surface, neutral border, official
                                // multicolour "G" at the start side, label
                                // centred. The button owns the loading
                                // state so its shell never changes shape.
                                Button(
                                    onClick = { signInWithGoogle() },
                                    // Consent gate (1.2.29): the button
                                    // cannot start sign-in until the
                                    // policy checkbox under it is ticked;
                                    // it dims while unticked and works
                                    // the moment the box is checked.
                                    enabled = PrivacyConsent.isSignInEnabled(consentChecked, isLoading),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .alpha(if (consentChecked) 1f else 0.55f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFF1F1F1F),
                                        disabledContainerColor = Color.White,
                                        disabledContentColor = Color(0xFF1F1F1F)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 0.dp,
                                        pressedElevation = 0.dp,
                                        disabledElevation = 0.dp
                                    ),
                                    shape = RoundedCornerShape(percent = 50),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoading) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = MaroonPrimary,
                                                    strokeWidth = 2.5.dp
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    "جاري تسجيل الدخول...",
                                                    fontFamily = TajawalFontFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF1F1F1F)
                                                )
                                            }
                                        } else {
                                            Text(
                                                "تسجيل الدخول عبر Google",
                                                fontFamily = TajawalFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 16.sp,
                                                color = Color(0xFF1F1F1F)
                                            )
                                            Icon(
                                                painter = painterResource(R.drawable.ic_google_g),
                                                contentDescription = null,
                                                tint = Color.Unspecified,
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .padding(start = 18.dp)
                                                    .size(21.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                ConsentCheckboxRow(
                                    checked = consentChecked,
                                    onCheckedChange = { ticked ->
                                        consentChecked = ticked
                                        if (ticked) PrivacyConsent.recordConsent(context)
                                    },
                                    onOpenPolicy = { policyAnchor = 0 }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
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
        }
    }
}
/**
 * سطر الموافقة بمربّع الصحّ تحت زرّ Google (بناء 1.2.29، تصحيح
 * الدكتور — نمط التطبيقات العالمية): مربّع صحّ + «أوافق على» +
 * «سياسة الخصوصية» رابطًا يفتح النص الكامل بشاشة السياسة الداخلية.
 * التأشير يُحفظ فورًا برقم نسخة السياسة (data/PrivacyConsent)،
 * وزرّ Google لا يعمل قبل التأشير (انظر isSignInEnabled).
 */
@Composable
private fun ConsentCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenPolicy: () -> Unit
) {
    val annotated = remember {
        buildAnnotatedString {
            append("أوافق على ")
            pushStringAnnotation(tag = "policyLink", annotation = "open")
            withStyle(
                SpanStyle(
                    color = MaroonPrimary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            ) { append("سياسة الخصوصية") }
            pop()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = MaroonPrimary)
        )
        ClickableText(
            text = annotated,
            style = TextStyle(
                fontFamily = TajawalFontFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            onClick = { offset ->
                annotated.getStringAnnotations("policyLink", offset, offset)
                    .firstOrNull()?.let { onOpenPolicy() }
            }
        )
    }
}

/**
 * صيغة الموافقة أسفل زرّ تسجيل الدخول (بناء 1.2.19) — نصّها حرفيًا
 * من القسم الرابع من وثيقة السياسة المعتمدة (POLICY_TEXT.md):
 * «بالمتابعة وتسجيل الدخول، أنت توافق على سياسة الخصوصية وشروط
 * الاستخدام وإخلاء المسؤولية الطبية الخاصة بتطبيق «وطني» — Med Pro.»
 * عبارات الأقسام الثلاثة روابط تفتح شاشة السياسة عند قسمها،
 * والمتابعة بالدخول نفسه إقرار بها (بلا خطوة تأكيد إضافية).
 */
@Composable
private fun PrivacyConsentLine(onOpenPolicy: (Int) -> Unit) {
    val annotated = remember {
        buildAnnotatedString {
            append("بالمتابعة وتسجيل الدخول، أنت توافق على ")
            pushStringAnnotation(tag = "policyPart", annotation = "0")
            withStyle(
                SpanStyle(
                    color = MaroonPrimary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            ) { append("سياسة الخصوصية") }
            pop()
            append(" و")
            pushStringAnnotation(tag = "policyPart", annotation = "1")
            withStyle(
                SpanStyle(
                    color = MaroonPrimary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            ) { append("شروط الاستخدام") }
            pop()
            append(" و")
            pushStringAnnotation(tag = "policyPart", annotation = "2")
            withStyle(
                SpanStyle(
                    color = MaroonPrimary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            ) { append("إخلاء المسؤولية الطبية") }
            pop()
            append(" الخاصة بتطبيق «وطني» — Med Pro.")
        }
    }
    ClickableText(
        text = annotated,
        style = TextStyle(
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        ),
        onClick = { offset ->
            annotated.getStringAnnotations("policyPart", offset, offset)
                .firstOrNull()?.item?.toIntOrNull()?.let { onOpenPolicy(it) }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
