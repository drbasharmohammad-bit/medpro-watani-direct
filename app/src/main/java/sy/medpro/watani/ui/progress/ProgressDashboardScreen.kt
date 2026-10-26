package sy.medpro.watani.ui.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.OfflineStudyVault
import sy.medpro.watani.ui.theme.*

@Composable
fun ProgressDashboardScreen(
    onOpenStudyPlanner: () -> Unit = {},
    onOpenEponyms: () -> Unit = {},
    onOpenEcgAtlas: () -> Unit = {},
    onOpenCalculators: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        OfflineStudyVault.ensureInitialized(context)
    }
    val stats by OfflineStudyVault.statsFlow.collectAsState()
    val accuracyPercent = if (stats.totalAnswered > 0) (stats.totalCorrect * 100) / stats.totalAnswered else 0

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(MaroonPrimary, MaroonDark, MaroonDeep)))
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("لوحة تقدمي والإحصائيات الأوفلاين", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${stats.totalCorrect * 25 + 850} XP", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    Text("محفوظة محلياً على جهازك بدون إنترنت", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                }
                                Surface(
                                    color = Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("$accuracyPercent%", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                        Text("نسبة الدقة", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatPillCard("الأسئلة المحلولة", "${stats.totalAnswered}", Modifier.weight(1f))
                                StatPillCard("الإجابات الصحيحة", "${stats.totalCorrect}", Modifier.weight(1f))
                                StatPillCard("أخطاء للمراجعة", "${stats.savedMistakes.size}", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                Text("أدوات التخطيط والموسوعات الأوفلاين", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaroonPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OfflineQuickButton(
                        title = "مخطط جدول الدراسة",
                        subtitle = "باقي ${stats.examDaysRemaining} يوماً للوطني",
                        icon = Icons.Default.EventNote,
                        onClick = onOpenStudyPlanner,
                        modifier = Modifier.weight(1f)
                    )
                    OfflineQuickButton(
                        title = "أطلس تخطيط القلب ECG",
                        subtitle = "موجات تفاعلية أوفلاين",
                        icon = Icons.Default.MonitorHeart,
                        onClick = onOpenEcgAtlas,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OfflineQuickButton(
                        title = "موسوعة المتلازمات Eponyms",
                        subtitle = "العلامات والثلاثيات المسماة",
                        icon = Icons.Default.MenuBook,
                        onClick = onOpenEponyms,
                        modifier = Modifier.weight(1f)
                    )
                    OfflineQuickButton(
                        title = "الحاسبات السريرية",
                        subtitle = "CURB-65 • غازات الدم ABG",
                        icon = Icons.Default.Calculate,
                        onClick = onOpenCalculators,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("تحليل الأداء حسب الاختصاصات الوزارية", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(12.dp))

                        val specialties = listOf("الباطنة", "الجراحة", "الأطفال", "النسائية والتوليد", "الجلدية والعينية والأذنية")
                        specialties.forEach { spec ->
                            val ans = stats.specialtyAnswered[spec] ?: 0
                            val cor = stats.specialtyCorrect[spec] ?: 0
                            val ratio = if (ans > 0) cor.toFloat() / ans else 0f
                            val animatedRatio by animateFloatAsState(
                                targetValue = ratio,
                                animationSpec = tween(500),
                                label = "spec_$spec"
                            )

                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(spec, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("$cor / $ans (${(ratio * 100).toInt()}%)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaroonPrimary)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { animatedRatio },
                                    color = if (ratio >= 0.7f) SuccessGreen else if (ratio >= 0.5f) GoldAccentDark else ErrorRed,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("بنك أخطائي المحفوظة محلياً (${stats.savedMistakes.size})", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaroonPrimary)
                    Text("تُحفظ تلقائياً أثناء الحل", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
                }
            }

            items(stats.savedMistakes, key = { it.id }) { mistake ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(color = MaroonPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    mistake.subjectTitle,
                                    color = MaroonPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            TextButton(onClick = { OfflineStudyVault.removeMistake(context, mistake.id) }) {
                                Text("تم إتقانه ✓", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Text(mistake.questionText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 22.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("إجابتك السابقة: ${mistake.selectedAnswer}", fontSize = 12.sp, color = ErrorRed)
                        Text("الجواب الصحيح المعتمد: ${mistake.correctAnswer}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        if (mistake.explanation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = mistake.explanation,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPillCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun OfflineQuickButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaroonPrimary.copy(alpha = 0.12f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
        }
    }
}
