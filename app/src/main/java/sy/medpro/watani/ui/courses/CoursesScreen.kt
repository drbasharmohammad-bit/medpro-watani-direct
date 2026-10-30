package sy.medpro.watani.ui.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import sy.medpro.watani.ui.theme.MaroonPrimary

data class CourseModel(val id: String, val title: String, val unitsCount: Int, val description: String, val status: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var courses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun fetchCourses() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://firestore.googleapis.com/v1/projects/national-exam-8ecba/databases/ai-studio-nationalexamapp-330e566e-ddc6-4e62-86a9-8009ee9cde64/documents/courses")
                    .build()
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    val jsonObj = JSONObject(bodyString)
                    val documents = jsonObj.optJSONArray("documents")
                    val list = mutableListOf<CourseModel>()
                    if (documents != null) {
                        for (i in 0 until documents.length()) {
                            val doc = documents.getJSONObject(i)
                            val namePath = doc.optString("name", "")
                            val id = namePath.substringAfterLast("/")
                            val fields = doc.optJSONObject("fields")
                            val title = fields?.optJSONObject("title")?.optString("stringValue") ?: "كورس تخصصي"
                            val unitsCount = fields?.optJSONObject("unitsCount")?.optString("integerValue")?.toIntOrNull() ?: 10
                            val description = fields?.optJSONObject("description")?.optString("stringValue") ?: "مادة تعليمية ومحاضرات الامتحان الوطني"
                            val status = fields?.optJSONObject("status")?.optString("stringValue") ?: "نشط"
                            list.add(CourseModel(id, title, unitsCount, description, status))
                        }
                    }
                    courses = list
                } else {
                    errorMessage = "تعذر جلب الكورسات من الخادم (HTTP ${response.code})"
                }
            } catch (e: Exception) {
                errorMessage = "خطأ في الاتصال: ${e.localizedMessage ?: "تحقق من الإنترنت"}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchCourses()
    }

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الكورسات التخصصية", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; fetchCourses() },
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaroonPrimary)
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { fetchCourses() }, colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إعادة المحاولة")
                        }
                    }
                }
                courses.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد كورسات متاحة حالياً في قاعدة البيانات.", fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("كورسات منهجية معتمدة للامتحان الوطني السوري", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("تتضمن الوحدات والدروس المستخرجة رسمياً.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(courses) { course ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(28.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(course.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                                        }
                                        Surface(color = MaroonPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                                            Text(course.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = MaroonPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(course.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("عدد الوحدات: ${course.unitsCount} وحدة تعليمية", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
                    }
    }
}