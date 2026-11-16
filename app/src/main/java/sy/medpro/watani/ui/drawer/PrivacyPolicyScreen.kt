package sy.medpro.watani.ui.drawer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.concepts.ConceptRichText
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * سياسة الخصوصية وشروط الاستخدام وإخلاء المسؤولية الطبية (بناء
 * 1.2.19، بأمر الدكتور): شاشة نصية كاملة قابلة للتمرير تعرض النص
 * المعتمد حرفيًا من الأصل المضمّن بالحزمة (assets/privacy_policy.txt
 * — الأقسام الثلاثة من POLICY_TEXT.md المعتمدة: الخصوصية، الشروط،
 * إخلاء المسؤولية)، فتُقرأ بلا أي شبكة. تُفتح من ثلاثة مواضع:
 * روابط صيغة الموافقة أسفل شاشة تسجيل الدخول (كل رابط يفتح الشاشة
 * عند قسمه: [anchorPart] ‏0=الخصوصية، 1=الشروط، 2=إخلاء المسؤولية)،
 * وبند دائم في الدرج (من الأعلى).
 *
 * العرض: عناوين ماركداون (# / ## / ###) تتحول لترويسات مهيكلة،
 * وخط «---» فاصل أفقي، وبقية الأسطر تمرّ بمرسّم النص الغني نفسه
 * المستخدم بشروحات المفاهيم (**عريض** وبنود «- »)، فالهوية واحدة.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, anchorPart: Int = -1) {
    val context = LocalContext.current
    val policyText = remember(context) {
        try {
            context.assets.open("privacy_policy.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }
    val blocks = remember(policyText) { splitPolicyBlocks(policyText) }
    // موضع بداية كل قسم رئيسي (كتل عناوين # بعد عنوان الوثيقة):
    // الأول «أولًا: الخصوصية»، الثاني «ثانيًا: الشروط»، الثالث
    // «ثالثًا: إخلاء المسؤولية». الدرج يفتح من الأعلى (قسم 0 بلا
    // تمرير فعلي لأنه بداية المحتوى بعد الترويسة).
    val partStartIndices = remember(blocks) {
        blocks.mapIndexedNotNull { index, block ->
            if (block is PolicyBlock.Title && index > 0) index else null
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(anchorPart, partStartIndices) {
        if (anchorPart >= 0) {
            val target = partStartIndices.getOrNull(anchorPart)
            if (target != null && target > 0) {
                listState.scrollToItem(target)
            }
        }
    }
    DrawerScreenScaffold(title = "سياسة الخصوصية وشروط الاستخدام", onBack = onBack) { padding ->
        if (policyText.isBlank()) {
            Text(
                "تعذّر تحميل نص السياسة من الحزمة.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(padding).padding(20.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
            ) {
                items(blocks) { block ->
                    PolicyBlockView(block)
                }
            }
        }
    }
}

/**
 * عرض كتلة واحدة من نص السياسة — مشترك بين شاشة السياسة بالدرج
 * وشاشة الموافقة القسرية قبل الدخول (بناء 1.2.28)، فالهوية واحدة
 * والنص واحد.
 */
@Composable
fun PolicyBlockView(block: PolicyBlock) {
    when (block) {
        is PolicyBlock.Title -> {
            Text(
                block.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaroonPrimary,
                lineHeight = 28.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        is PolicyBlock.Heading -> {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                block.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaroonPrimary,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        is PolicyBlock.SubHeading -> {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                block.text,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        is PolicyBlock.Paragraph -> {
            ConceptRichText(text = block.text)
            Spacer(modifier = Modifier.height(6.dp))
        }
        PolicyBlock.Divider -> {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

sealed interface PolicyBlock {
    data class Title(val text: String) : PolicyBlock
    data class Heading(val text: String) : PolicyBlock
    data class SubHeading(val text: String) : PolicyBlock
    data class Paragraph(val text: String) : PolicyBlock
    data object Divider : PolicyBlock
}

/**
 * تقسيم نص السياسة (ماركداون بسيط) إلى كتل عرض: سطر يبدأ بـ«# »
 * عنوان رئيسي، «## » عنوان قسم، «### » عنوان فرعي، سطر «---»
 * فاصل، وما عداها يُجمّع فقراتٍ (أسطر متتالية بلا سطر فارغ فاصل)
 * تبقى أسطرها الداخلية كما هي ليتولاها المرسّم (بنود «- » وغيرها).
 */
fun splitPolicyBlocks(text: String): List<PolicyBlock> {
    val blocks = mutableListOf<PolicyBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        val p = paragraph.toString().trim()
        if (p.isNotEmpty()) blocks += PolicyBlock.Paragraph(p)
        paragraph.clear()
    }
    for (raw in text.lines()) {
        val line = raw.trimEnd()
        when {
            line.startsWith("### ") -> { flush(); blocks += PolicyBlock.SubHeading(line.removePrefix("### ").trim()) }
            line.startsWith("## ") -> { flush(); blocks += PolicyBlock.Heading(line.removePrefix("## ").trim()) }
            line.startsWith("# ") -> { flush(); blocks += PolicyBlock.Title(line.removePrefix("# ").trim()) }
            line.trim() == "---" -> { flush(); blocks += PolicyBlock.Divider }
            line.isBlank() -> flush()
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
    }
    flush()
    return blocks
}
