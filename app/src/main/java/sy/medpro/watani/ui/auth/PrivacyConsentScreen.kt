package sy.medpro.watani.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.PrivacyConsent
import sy.medpro.watani.ui.drawer.PolicyBlockView
import sy.medpro.watani.ui.drawer.splitPolicyBlocks
import sy.medpro.watani.ui.theme.CairoFontFamily
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonDark
import sy.medpro.watani.ui.theme.MaroonInk
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.TajawalFontFamily
import sy.medpro.watani.ui.theme.TextOnDark

/**
 * شاشة الموافقة القسرية على السياسة (بناء 1.2.28، بأمر الدكتور
 * 16 نوفمبر 2026: «تجبر الطلاب على الموافقة على سياسة الخصوصية
 * قبل التسجيل»): حاجب كامل يظهر قبل تسجيل الدخول بـGoogle لأي
 * مستخدم لم يقبل نسخة السياسة الحالية بعد (انظر
 * data/PrivacyConsent لقاعدة القرار وتخزين القبول برقم النسخة).
 *
 * التصميم: نفس عائلة شاشة الدخول الفخمة (تدرّج عنابي عميق) وبطاقة
 * عاجية تحمل النص الكامل المعتمد من الحزمة (يعمل أوفلاين تمامًا).
 * لا طريق حولها: زرّ المتابعة معطّل حتى يؤشّر المستخدم صراحةً على
 * إقرار القراءة والموافقة، والقبول يُحفظ برقم نسخة الوثيقة فتُعاد
 * المطالبة عند تحديثها جوهريًا — كما ينصّ بند «تعديلات السياسة»
 * بالوثيقة نفسها. أسفل البطاقة رابط النسخة الرسمية على الموقع.
 */
@Composable
fun PrivacyConsentScreen(onConsented: () -> Unit) {
    val context = LocalContext.current
    val policyText = remember(context) {
        try {
            context.assets.open("privacy_policy.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }
    val blocks = remember(policyText) { splitPolicyBlocks(policyText) }
    var agreed by remember { mutableStateOf(false) }

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
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                "سياسة الخصوصية وشروط الاستخدام",
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = TextOnDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "قبل تسجيل الدخول، اطّلع على السياسة ووافق عليها — الموافقة شرط لإنشاء الحساب واستخدام التطبيق.",
                fontFamily = TajawalFontFamily,
                fontSize = 13.5.sp,
                color = TextOnDark.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (policyText.isBlank()) {
                        Text(
                            "تعذّر تحميل نص السياسة من الحزمة.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(20.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            items(blocks) { block -> PolicyBlockView(block) }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = agreed,
                                onCheckedChange = { agreed = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaroonPrimary)
                            )
                            Text(
                                "قرأت سياسة الخصوصية وشروط الاستخدام وإخلاء المسؤولية الطبية وأوافق عليها.",
                                fontFamily = TajawalFontFamily,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                PrivacyConsent.recordConsent(context)
                                onConsented()
                            },
                            enabled = agreed,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            shape = RoundedCornerShape(percent = 50)
                        ) {
                            Text(
                                "موافق — المتابعة إلى تسجيل الدخول",
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OfficialPageLink()
                    }
                }
            }
        }
    }
}

/** رابط النسخة الرسمية الدائمة من السياسة على موقع المحتوى. */
@Composable
private fun OfficialPageLink() {
    val context = LocalContext.current
    val annotated = remember {
        buildAnnotatedString {
            append("النسخة الرسمية الكاملة متاحة دائمًا على ")
            pushStringAnnotation(tag = "url", annotation = PrivacyConsent.OFFICIAL_POLICY_URL)
            withStyle(
                SpanStyle(
                    color = MaroonPrimary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            ) { append("صفحة السياسة بالموقع") }
            pop()
            append(".")
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
            annotated.getStringAnnotations("url", offset, offset)
                .firstOrNull()?.item?.let { url ->
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
