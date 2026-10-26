package sy.medpro.watani.ui.osce

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import sy.medpro.watani.data.OsceEnvelope
import sy.medpro.watani.data.OsceStation
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale

import androidx.compose.ui.platform.LocalContext
import sy.medpro.watani.data.WataniRepository

@Composable
fun OsceStationsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var stations by remember { mutableStateOf<List<OsceStation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedStation by remember { mutableStateOf<OsceStation?>(null) }
    var activeExamMode by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    suspend fun loadOsce() {
        isLoading = true
        errorMessage = null
        try {
            val repo = WataniRepository(context)
            var env = repo.getOsceEnvelope()
            if (env == null) {
                val manifestRes = repo.fetchManifest()
                if (manifestRes.isSuccess) {
                    val files = manifestRes.getOrNull()?.files
                    val osceEntry = files?.get("osce.json")
                    if (osceEntry != null) {
                        repo.downloadSection("osce.json", osceEntry)
                        env = repo.getOsceEnvelope()
                    }
                }
            }
            if (env != null && !env.stations.isNullOrEmpty()) {
                stations = env.stations!!
            } else {
                errorMessage = "تعذر تحميل محطات الـ OSCE. يرجى التحقق من اتصال الإنترنت وإعادة المحاولة."
            }
        } catch (e: Exception) {
            errorMessage = "خطأ في الاتصال: ${e.localizedMessage ?: "تأكد من اتصالك بالإنترنت"}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadOsce()
    }

    DrawerScreenScaffold(
        title = "محطات الامتحان السريري المنهجي (OSCE Stations)",
        onBack = {
            if (activeExamMode) {
                activeExamMode = false
            } else if (selectedStation != null) {
                selectedStation = null
            } else {
                onBack()
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("جاري تحميل محطات الـ OSCE...", fontWeight = FontWeight.Bold, color = MaroonPrimary)
                }
            } else if (errorMessage != null || stations.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PendingActions, contentDescription = null, tint = GoldAccentDark, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("تعذر تحميل محطات الامتحان السريري", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "حدث خطأ أثناء تحميل محطات الـ OSCE. يرجى إعادة المحاولة.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { coroutineScope.launch { loadOsce() } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
            val currentStation = selectedStation
            if (activeExamMode && currentStation != null) {
                OsceExamRunnerScreen(
                    station = currentStation,
                    onFinish = { activeExamMode = false },
                    onExit = { activeExamMode = false; selectedStation = null }
                )
            } else if (currentStation != null) {
                OsceStationDetailScreen(
                    station = currentStation,
                    onStartExam = { activeExamMode = true },
                    onBack = { selectedStation = null }
                )
            } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("محطات الامتحان السريري العملي (OSCE)", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("محطات تقييم سريري معيارية تغطي مهارات الفحص، التواصل، قراءة الاستقصاءات، وإعطاء خطة التدبير الإسعافي.", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                }
                            }
                        }

                        items(stations, key = { it.id ?: it.title ?: "" }) { station ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedStation = station }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaroonPrimary.copy(alpha = 0.1f),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaroonPrimary)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(station.title ?: "", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("التخصص: ${station.specialty ?: "عام"} • المدة: ${(station.durationSec ?: 300) / 60} دقائق", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    }
                                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaroonPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OsceStationDetailScreen(
    station: OsceStation,
    onStartExam: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                }
                Text(station.specialty ?: "", fontWeight = FontWeight.Bold, color = MaroonPrimary, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(station.title ?: "", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaroonPrimary)
            Text("المدة الزمنية للمحطة: ${(station.durationSec ?: 300) / 60} دقائق", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("السيناريو السريري (Clinical Scenario):", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(station.scenario ?: "", fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("تعليمات الطبيب المرشح (Candidate Instructions):", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(station.instructions ?: "", fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }

        if (!station.tasks.isNullOrEmpty()) {
            item {
                Text("المطلوب منك تنفيذه في هذه المحطة:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
            }
            items(station.tasks!!) { task ->
                Surface(
                    color = MaroonPrimary.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(task, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onStartExam,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("بدء وضع الامتحان الفعلي مع مؤقت المحطة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun OsceExamRunnerScreen(
    station: OsceStation,
    onFinish: () -> Unit,
    onExit: () -> Unit
) {
    var timeLeft by remember { mutableIntStateOf(station.durationSec ?: 300) }
    var checkedItems by remember { mutableStateOf(setOf<Int>()) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0 && !submitted) {
            delay(1000L)
            timeLeft--
        }
    }

    val checklist = station.checklist ?: emptyList()
    val totalPoints = checklist.sumOf { it.points ?: 1 }
    val earnedPoints = checklist.indices.filter { checkedItems.contains(it) }.sumOf { checklist[it].points ?: 1 }
    val scorePercent = if (totalPoints > 0) (earnedPoints * 100) / totalPoints else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(station.title ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary, modifier = Modifier.weight(1f))
                Surface(
                    color = if (timeLeft < 30) ErrorRed else MaroonPrimary,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    val mins = timeLeft / 60
                    val secs = timeLeft % 60
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", mins, secs),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        if (!submitted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("تعليمات الفاحص والتقييم المعياري:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(station.instructions ?: "", fontSize = 12.sp)
                    }
                }
            }

            if (checklist.isNotEmpty()) {
                item {
                    Text("بنود قائمة الفحص والإنجاز (Examiner Checklist):", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(checklist.size) { idx ->
                    val checkItem = checklist[idx]
                    val isChecked = checkedItems.contains(idx)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isChecked) SuccessGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isChecked) SuccessGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checkedItems = if (isChecked) checkedItems - idx else checkedItems + idx
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    checkedItems = if (isChecked) checkedItems - idx else checkedItems + idx
                                },
                                colors = CheckboxDefaults.colors(checkedColor = MaroonPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = checkItem.item ?: "",
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Surface(
                                color = MaroonPrimary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "+${checkItem.points ?: 1} درجات",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaroonPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { submitted = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إنهاء المحطة وإصدار تقرير الأداء", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaroonDeep),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("نتيجة أداء محطة الـ OSCE", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$scorePercent%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 42.sp
                        )
                        Text(
                            text = "الدرجة المكتسبة: $earnedPoints من أصل $totalPoints نقطة",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (!station.keyPoints.isNullOrEmpty()) {
                item {
                    Text("النقاط الحرجة المفصلية (Key Performance Points):", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(station.keyPoints!!) { kp ->
                    Surface(
                        color = MaroonPrimary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccentDark, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(kp, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (!station.learningPoints.isNullOrEmpty()) {
                item {
                    Text("الدروس المستفادة ونقاط التعلم الطبية:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                }
                items(station.learningPoints!!) { lp ->
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(lp, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("العودة لقائمة محطات OSCE", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
