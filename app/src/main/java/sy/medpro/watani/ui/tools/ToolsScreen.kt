package sy.medpro.watani.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Tools hub (Nov 2026, doctor's order): replaces the deleted PROGRESS
 * tab one-for-one. Every card is a shortcut to an existing screen —
 * no content is duplicated here. Titles are the exact drawer-screen
 * titles MainActivity routes, so tapping a card behaves exactly like
 * tapping the same entry in the drawer (including the activation
 * gate for the Anki card, applied by the caller).
 */
private data class ToolEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val target: String
)

private val TOOLS = listOf(
    ToolEntry("خوارزميات التدبير", "خطوات التدبير الإسعافي والسريري المتسلسلة", Icons.Default.Assessment, "خوارزميات التدبير"),
    ToolEntry("الخطوط العلاجية الأولى", "خط العلاج الأول لكل مرض والبديل في الحمل والحساسية", Icons.Default.LocalHospital, "الخطوط العلاجية الأولى"),
    ToolEntry("النقاط الذهبية", "لآلئ سريرية عالية الورود بالوطني", Icons.Default.EmojiEvents, "النقاط الذهبية"),
    ToolEntry("موسوعة المتلازمات", "العلامات الجراحية والباطنية والثلاثيات المسماة", Icons.Default.Book, "موسوعة المتلازمات"),
    ToolEntry("مخطط جدول الدراسة", "توزيع الأيام المتبقية حسب ثقل المواد", Icons.Default.Timer, "مخطط جدول الدراسة"),
    ToolEntry("أطلس تخطيط القلب", "راسم موجات تفاعلي للحالات الحرجة وتوضع الاحتشاء", Icons.Default.MonitorHeart, "أطلس تخطيط القلب"),
    ToolEntry("القلب والإصغاء", "مدرب الإصغاء وأصوات القلب والرئة", Icons.Default.Favorite, "القلب والإصغاء"),
    ToolEntry("المختبرات والحاسبات", "قيم مخبرية وغازات دم وحاسبات سريرية", Icons.Default.Science, "المختبرات والحاسبات"),
    ToolEntry("الحاسبات السريرية", "CURB-65 ومفسر غازات الدم وغيرها", Icons.Default.Assessment, "الحاسبات السريرية الموسعة"),
    ToolEntry("حاسبات العناية والسوائل", "الرافعات الوعائية ومعادلة باركلاند للحروق", Icons.Default.LocalHospital, "حاسبات العناية والسوائل"),
    ToolEntry("الصادات والكلية والأطفال", "تعديل الجرعات حسب CrCl وجرعات الأطفال بالوزن", Icons.Default.MedicalServices, "الصادات والكلية والأطفال"),
    ToolEntry("محطات OSCE", "محطات فحص سريري تفاعلية", Icons.Default.MedicalServices, "محطات OSCE"),
    ToolEntry("مدرّب التشخيص التفريقي", "رتّب التشخيصات التفريقية واكشف مراحل الحالة تدريجيًا", Icons.Default.Assessment, "مدرّب التشخيص التفريقي"),
    ToolEntry("اقلبها", "ابنِ الصورة السريرية للتشخيص، ميّز العنصر الدخيل، وأكمل العبارة الناقصة", Icons.Default.EmojiEvents, "اقلبها"),
    ToolEntry("غرفة الطوارئ", "مناوبة إسعافية تفاعلية بحالات متغيرة", Icons.Default.LocalHospital, "غرفة الطوارئ"),
    ToolEntry("البطاقات والمراجعة الذكية", "بطاقات Anki ومراجعة الأخطاء", Icons.Default.Style, "البطاقات والمراجعة الذكية"),
    ToolEntry("دليل الدراسة", "خطة دراسة منظمة للوطني", Icons.Default.Book, "دليل الدراسة"),
    ToolEntry("سجل أخطائي والاختبارات", "أسئلتك الخاطئة واختباراتك السابقة", Icons.Default.History, "سجل أخطائي والاختبارات"),
    ToolEntry("سجلّ حالاتي", "وثّق حالات مناوبتك — محفوظة بجهازك ومزامَنة مع حسابك", Icons.Default.Assignment, "سجلّ حالاتي"),
    ToolEntry("مؤقت التركيز", "جلسات تركيز بأسلوب بومودورو", Icons.Default.Timer, "مؤقت التركيز"),
    ToolEntry("البحث الشامل", "ابحث بكل محتوى التطبيق", Icons.Default.Search, "البحث الشامل")
)

@Composable
fun ToolsScreen(onToolClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        item {
            Text(
                text = "كل أدواتك بمكان واحد للوصول السريع",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        items(TOOLS) { tool ->
            Card(
                onClick = { onToolClick(tool.target) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(tool.icon, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tool.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(tool.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
