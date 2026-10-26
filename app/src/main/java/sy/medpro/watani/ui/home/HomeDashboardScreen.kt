package sy.medpro.watani.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import sy.medpro.watani.data.OfflineStudyVault
import sy.medpro.watani.ui.reference.AccreditedMedicalData
import sy.medpro.watani.ui.reference.QuickMedicalReferenceModal
import sy.medpro.watani.ui.theme.*

@Composable
fun HomeDashboardScreen(
    manifestVersion: String?,
    onNavigateBank: () -> Unit,
    onNavigateCases: () -> Unit,
    onNavigateLeaderboard: () -> Unit,
    onOpenDailyChallenge: () -> Unit = onNavigateBank,
    onOpenAlgorithms: () -> Unit = {},
    onOpenFirstLineDrugs: () -> Unit = {},
    onOpenGoldenPearls: () -> Unit = {},
    onOpenEponyms: () -> Unit = {},
    onOpenEcgAtlas: () -> Unit = {},
    onOpenStudyPlanner: () -> Unit = {},
    onOpenCalculators: () -> Unit = {},
    onOpenIcuAndDosing: () -> Unit = {},
    onOpenRenalAndPedsDosing: () -> Unit = {}
) {
    val context = LocalContext.current
    var showLabModal by remember { mutableStateOf(false) }
    var revealTodayPearl by remember { mutableStateOf(false) }
    var animateIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        OfflineStudyVault.ensureInitialized(context)
        animateIn = true
    }

    val stats by OfflineStudyVault.statsFlow.collectAsState()
    val days = stats.examDaysRemaining
    val totalAns = stats.totalAnswered
    val totalCor = stats.totalCorrect
    val accuracy = if (totalAns == 0) 0 else (totalCor * 100) / totalAns
    val progressVal = if (totalAns == 0) 0.05f else (totalCor.toFloat() / totalAns.toFloat()).coerceIn(0.05f, 1f)

    val ringProgress by animateFloatAsState(
        targetValue = if (animateIn) progressVal else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "countdownRing"
    )

    if (showLabModal) {
        QuickMedicalReferenceModal(onDismiss = { showLabModal = false })
    }

    val featuredPearl = AccreditedMedicalData.goldenPearls.first()

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
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(MaroonPrimary, MaroonDark, MaroonDeep)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = GoldAccent.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "منصة التحضير السريري المعتمدة",
                                        color = GoldAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "أهلاً بك يا طبيب المستقبل",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "نجاحك في الوطني يبدأ من هنا • تدريب سريري بمعايير عالمية",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(74.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawArc(
                                        color = Color.White.copy(alpha = 0.18f),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = GoldAccent,
                                        startAngle = -90f,
                                        sweepAngle = 360f * ringProgress,
                                        useCenter = false,
                                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$days", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("يوماً", color = GoldAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "المراجع المعتمدة: Harrison's • Oxford • ATLS • Nelson",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (totalAns == 0) "جاهزية البنك: مبدئي" else "جاهزية $accuracy%",
                                    color = GoldAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Standalone Bedside Clinical & Physical Exam Suite (Isolated from Question Bank)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, GoldAccent.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.LocalHospital, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "جناح الممارسة السريرية والمناوبات بالمشفى",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaroonPrimary
                                )
                                Text(
                                    text = "أدوات سريرية مهارية مستقلة عن بنك الأسئلة • Bates' & Sanford",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Surface(
                            color = GoldAccent.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "مبتكر • أوفلاين",
                                color = MaroonDeep,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactMedicalToolCard(
                            title = "تسريب العناية والسوائل",
                            subtitle = "الرافعات الوعائية mcg/kg/min ومعادلة باركلاند للحروق",
                            icon = Icons.Default.WaterDrop,
                            badgeColor = ErrorRed,
                            onClick = onOpenIcuAndDosing,
                            modifier = Modifier.weight(1f)
                        )
                        CompactMedicalToolCard(
                            title = "الصادات والكلية والأطفال",
                            subtitle = "تعديل الجرعات حسب CrCl وجرعات الأطفال بالوزن",
                            icon = Icons.Default.Vaccines,
                            badgeColor = SuccessGreen,
                            onClick = onOpenRenalAndPedsDosing,
                            modifier = Modifier.weight(1f)
                        )
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
                Text(
                    text = "الأدوات الطبية المرجعية المعتمدة دولياً",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaroonPrimary
                )
                Surface(
                    color = MaroonPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "أوفلاين 100% • بدون ذكاء اصطناعي",
                        color = MaroonPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccreditedToolBannerCard(
                    title = "خوارزميات التدبير السريري وجداول التشخيص التفريقي",
                    subtitle = "خطوات التدبير الإسعافي والسريري المتسلسلة مع جداول المقارنة الحاسمة",
                    badge = "Harrison's • ESC • ATLS",
                    icon = Icons.Default.AccountTree,
                    accentColor = MaroonPrimary,
                    onClick = onOpenAlgorithms
                )

                AccreditedToolBannerCard(
                    title = "دليل الخطوط العلاجية الأولى والأدوية الأساسية (DOC)",
                    subtitle = "خط العلاج الأول لكل مرض، البديل في الحمل والحساسية، ومضادات الاستطباب",
                    badge = "First-Line DOC",
                    icon = Icons.Default.Medication,
                    accentColor = SuccessGreen,
                    onClick = onOpenFirstLineDrugs
                )

                AccreditedToolBannerCard(
                    title = "دليل قراءة تخطيط القلب التفاعلي (ECG Atlas)",
                    subtitle = "راسم موجات تخطيط القلب التفاعلي للحالات الحرجة وتوضع الاحتشاءات أوفلاين",
                    badge = "ECG Waveforms • 100% Offline",
                    icon = Icons.Default.MonitorHeart,
                    accentColor = ErrorRed,
                    onClick = onOpenEcgAtlas
                )

                AccreditedToolBannerCard(
                    title = "موسوعة المتلازمات والعلامات السريرية المسماة (Eponyms)",
                    subtitle = "أطلس العلامات الجراحية والباطنية والثلاثيات المسماة عالية الورود بالوطني",
                    badge = "Named Signs & Triads",
                    icon = Icons.Default.MenuBook,
                    accentColor = MaroonDeep,
                    onClick = onOpenEponyms
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompactMedicalToolCard(
                        title = "مخطط جدول الدراسة",
                        subtitle = "توزيع الأيام المتبقية حسب ثقل المواد",
                        icon = Icons.Default.EventNote,
                        badgeColor = MaroonPrimary,
                        onClick = onOpenStudyPlanner,
                        modifier = Modifier.weight(1f)
                    )
                    CompactMedicalToolCard(
                        title = "الحاسبات السريرية",
                        subtitle = "CURB-65 ومفسر غازات الدم ABG",
                        icon = Icons.Default.Calculate,
                        badgeColor = SuccessGreen,
                        onClick = onOpenCalculators,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompactMedicalToolCard(
                        title = "النقاط الذهبية للوطني",
                        subtitle = "الثلاثيات والعلامات الواسمة عالية الورود",
                        icon = Icons.Default.AutoAwesome,
                        badgeColor = GoldAccentDark,
                        onClick = onOpenGoldenPearls,
                        modifier = Modifier.weight(1f)
                    )
                    CompactMedicalToolCard(
                        title = "القيم المخبرية الطبيعية",
                        subtitle = "مرجع التحاليل والغازات والشوارد الفوري",
                        icon = Icons.Default.Science,
                        badgeColor = MaroonPrimary,
                        onClick = { showLabModal = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.55f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { revealTodayPearl = !revealTodayPearl }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccentDark, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "النقطة الذهبية اليومية عالية الورود",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaroonPrimary
                            )
                        }
                        Text(
                            text = if (revealTodayPearl) "إخفاء الجواب ▲" else "اضغط لكشف التدبير ▼",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccentDark
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = featuredPearl.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = featuredPearl.classicPresentation,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )

                    AnimatedVisibility(
                        visible = revealTodayPearl,
                        enter = expandVertically(animationSpec = tween(250)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "التشخيص والتدبير المعتمد:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = SuccessGreen
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = featuredPearl.diagnosisOrAction,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "الوصول السريع للأقسام التدريبية",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaroonPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "بنك الأسئلة الشامل",
                    subtitle = "الوطني • الجامعات • UWorld",
                    icon = Icons.Default.Book,
                    onClick = onNavigateBank,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "المريض الافتراضي",
                    subtitle = "محاكاة حالات سريرية",
                    icon = Icons.Default.LocalHospital,
                    onClick = onNavigateCases,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "لوحة الصدارة",
                    subtitle = "ترتيب طلاب الوطني",
                    icon = Icons.Default.EmojiEvents,
                    onClick = onNavigateLeaderboard,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "التحدي اليومي",
                    subtitle = "سؤال سريري +20 نقطة",
                    icon = Icons.Default.Whatshot,
                    onClick = onOpenDailyChallenge,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("حالة المزامنة وقاعدة البيانات", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 14.sp)
                        Text(
                            text = "الفهرس متزامن مع الخادم الرسمي للبنك الوطني" + (if (manifestVersion != null) " • الإصدار $manifestVersion" else ""),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccreditedToolBannerCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactMedicalToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.25f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String = "",
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaroonPrimary.copy(alpha = 0.1f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
        }
    }
}
