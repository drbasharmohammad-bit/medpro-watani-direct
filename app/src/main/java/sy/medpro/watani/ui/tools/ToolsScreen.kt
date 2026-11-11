package sy.medpro.watani.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Tools hub — grid redesign (Nov 2026, doctor's approved mockup):
 * fixed global-search bar on top, then a two-per-row grid of large
 * tiles grouped under three headers. Every tile is a shortcut to an
 * existing screen — no content is duplicated here. Tool titles route
 * through MainActivity drawer-screen titles, so tapping a tile behaves
 * exactly like the matching route (including the activation gate for
 * the Anki tile, applied by the caller).
 *
 * Changes vs the old flat list:
 * - The golden-pearls tool was removed entirely (doctor's order, Nov 2026).
 * - The four scattered calculator entries merged into one
 *   «الحاسبات الطبية» tile (unified calculators screen), and
 *   «المختبرات وغازات الدم» became its own tile (values + ABG only).
 * - Every tile has its OWN icon and its OWN accent color — no icon is
 *   repeated (the old list repeated Assessment/LocalHospital/
 *   MedicalServices/Timer/Book/EmojiEvents across different tools).
 */
private data class ToolEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val target: String
)

private data class ToolGroup(
    val header: String,
    val tools: List<ToolEntry>
)

private val TOOL_GROUPS = listOf(
    ToolGroup(
        header = "تدرّب ومهارات سريرية",
        tools = listOf(
            ToolEntry("مدرّب التشخيص التفريقي", "رتّب التشخيصات التفريقية واكشف مراحل الحالة تدريجيًا", Icons.Default.AccountTree, Color(0xFF6A1B9A), "مدرّب التشخيص التفريقي"),
            ToolEntry("اقلبها", "ابنِ الصورة السريرية للتشخيص، ميّز العنصر الدخيل، وأكمل العبارة الناقصة", Icons.Default.FlipCameraAndroid, Color(0xFF00897B), "اقلبها"),
            ToolEntry("الحالات الألمانية", "حالات مريض افتراضي بالألمانية (Deutsche Fälle)", Icons.Default.Language, Color(0xFF00695C), "الحالات الألمانية"),
            ToolEntry("غرفة الطوارئ", "مناوبة إسعافية تفاعلية بحالات متغيرة", Icons.Default.Emergency, Color(0xFFC62828), "غرفة الطوارئ"),
            ToolEntry("محطات OSCE", "محطات فحص سريري تفاعلية", Icons.Default.FactCheck, Color(0xFF283593), "محطات OSCE"),
            // Fix 1.2.2 (doctor's order): ONE tools entry — «القلب والإصغاء».
            // The ECG atlas lives inside that same screen as its own tab;
            // a separate «أطلس تخطيط القلب» entry duplicated the content.
            ToolEntry("القلب والإصغاء", "كورس القلب المصغّر: قراءة ECG تفاعلية وفرجار قياس ومدرب إصغاء", Icons.Default.Favorite, Color(0xFFE65100), "القلب والإصغاء")
        )
    ),
    ToolGroup(
        header = "حاسبات ومراجع سريعة",
        tools = listOf(
            ToolEntry("الحاسبات الطبية", "الحاسبات السريرية الموسعة والجرعات والعناية والسوائل بمكان واحد", Icons.Default.Calculate, Color(0xFF1565C0), "الحاسبات الطبية"),
            ToolEntry("المختبرات وغازات الدم", "قيم مخبرية مرجعية ومفسّر غازات الدم", Icons.Default.Science, Color(0xFF00838F), "المختبرات وغازات الدم"),
            ToolEntry("خوارزميات التدبير", "خطوات التدبير الإسعافي والسريري المتسلسلة", Icons.Default.Schema, Color(0xFF2E7D32), "خوارزميات التدبير"),
            ToolEntry("الخطوط العلاجية الأولى", "خط العلاج الأول لكل مرض والبديل في الحمل والحساسية", Icons.Default.Medication, Color(0xFF7B1FA2), "الخطوط العلاجية الأولى"),
            ToolEntry("موسوعة المتلازمات", "العلامات الجراحية والباطنية والثلاثيات المسماة", Icons.Default.MenuBook, Color(0xFF5D4037), "موسوعة المتلازمات")
        )
    ),
    ToolGroup(
        header = "دراستي وتنظيمي",
        tools = listOf(
            ToolEntry("المراجعة الذكية Anki", "مراجعة بطاقات Anki والأخطاء المتباعدة", Icons.Default.Style, Color(0xFF0277BD), "المراجعة الذكية Anki"),
            ToolEntry("بنك أخطائي", "أخطاؤك من كل البنوك بمراجعة متباعدة حتى الإتقان", Icons.Default.Quiz, Color(0xFFAD1457), "بنك أخطائي"),
            ToolEntry("دليل الدراسة", "خطة دراسة منظمة للوطني", Icons.Default.Book, Color(0xFF33691E), "دليل الدراسة"),
            ToolEntry("مخطط جدول الدراسة", "توزيع الأيام المتبقية حسب ثقل المواد", Icons.Default.CalendarMonth, Color(0xFF455A64), "مخطط جدول الدراسة"),
            ToolEntry("سجل أخطائي والاختبارات", "أسئلتك الخاطئة واختباراتك السابقة", Icons.Default.History, Color(0xFFB71C1C), "سجل أخطائي والاختبارات"),
            ToolEntry("سجلّ حالاتي", "وثّق حالات مناوبتك — محفوظة بجهازك ومزامَنة مع حسابك", Icons.Default.Assignment, Color(0xFF6D4C41), "سجلّ حالاتي"),
            ToolEntry("مؤقت التركيز", "جلسات تركيز بأسلوب بومودورو", Icons.Default.Timer, Color(0xFF4527A0), "مؤقت التركيز")
        )
    )
)

@Composable
fun ToolsScreen(onToolClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed global-search bar (the old «البحث الشامل» tile is gone).
        Card(
            onClick = { onToolClick("البحث الشامل") },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "البحث الشامل — ابحث بكل محتوى التطبيق",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TOOL_GROUPS.forEach { group ->
                item(key = "header_${group.header}") {
                    Text(
                        text = group.header,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaroonPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                val rows = group.tools.chunked(2)
                rows.forEachIndexed { rowIndex, pair ->
                    item(key = "row_${group.header}_$rowIndex") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ToolTile(pair[0], onToolClick, Modifier.weight(1f))
                            if (pair.size > 1) {
                                ToolTile(pair[1], onToolClick, Modifier.weight(1f))
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }
        }
    }
}

@Composable
private fun ToolTile(tool: ToolEntry, onToolClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = { onToolClick(tool.target) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(tool.accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = null, tint = tool.accent, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(tool.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                tool.subtitle,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
