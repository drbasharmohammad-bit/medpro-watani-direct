package sy.medpro.watani.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import sy.medpro.watani.data.WorkerActivationApi
import sy.medpro.watani.ui.theme.MaroonPrimary

data class LeaderboardUser(val rank: Int, val name: String, val score: Int, val university: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen() {
    val context = LocalContext.current
    var users by remember { mutableStateOf<List<LeaderboardUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    var notActivated by remember { mutableStateOf(false) }
    var needsSignIn by remember { mutableStateOf(false) }

    LaunchedEffect(reloadTrigger) {
        isLoading = true
        errorMessage = null
        notActivated = false
        needsSignIn = false
        try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                needsSignIn = true
                users = emptyList()
                isLoading = false
                return@LaunchedEffect
            }
            // الصدارة الآن من الـWorker حصرًا (لا قراءة Firestore هنا أبدًا):
            // رمز Google ID للمصادقة، و403 تعني «للمفعّلين فقط».
            val result = WorkerActivationApi.getFromWorker(user, "/v1/leaderboard?limit=50")
            when (result.code) {
                200 -> {
                    val entries = JSONObject(result.body ?: "{}").optJSONArray("entries")
                    val parsed = mutableListOf<LeaderboardUser>()
                    if (entries != null) {
                        for (i in 0 until entries.length()) {
                            val obj = entries.getJSONObject(i)
                            parsed.add(
                                LeaderboardUser(
                                    rank = obj.optInt("rank", i + 1),
                                    name = obj.optString("name", "طبيب متميز").ifBlank { "طبيب متميز" },
                                    score = obj.optInt("points", 0),
                                    university = obj.optString("university", "")
                                )
                            )
                        }
                    }
                    users = parsed
                    isLoading = false
                }
                403 -> {
                    notActivated = true
                    users = emptyList()
                    isLoading = false
                }
                401, -1 -> {
                    needsSignIn = true
                    users = emptyList()
                    isLoading = false
                }
                else -> {
                    errorMessage = "تعذر الاتصال بالخادم. يرجى التحقق من اتصال الإنترنت والإعادة."
                    users = emptyList()
                    isLoading = false
                }
            }
        } catch (_: Exception) {
            errorMessage = "تعذر الاتصال بالخادم. يرجى التحقق من اتصال الإنترنت والإعادة."
            isLoading = false
            users = emptyList()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; reloadTrigger++ },
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaroonPrimary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("لوحة الصدارة الوطنية", style = MaterialTheme.typography.titleLarge, color = MaroonPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("تنافس مع زملائك الأطباء من مختلف الجامعات السورية", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (users.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                when {
                                    notActivated -> {
                                        Text("لوحة الصدارة للمفعّلين فقط", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                                        Text(
                                            "فعّل حسابك من «الاشتراك وتفعيل الحساب» باللوحة الجانبية لعرض ترتيبك بين زملائك.",
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    needsSignIn -> {
                                        Text("سجّل دخولك بحساب Google لعرض الصدارة", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
                                        Text(
                                            "الصدارة متاحة للطلاب المسجّلين بحساب Google.",
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = errorMessage ?: "لا توجد بيانات حالياً في لوحة الصدارة",
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Button(shape = MaterialTheme.shapes.medium,
                                    onClick = { reloadTrigger++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                                ) {
                                    Text("إعادة المحاولة")
                                }
                            }
                        }
                    } else {
                        itemsIndexed(users) { index, user ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(50.dp),
                                            color = when (user.rank) {
                                                1 -> MaroonPrimary
                                                2 -> MaroonPrimary.copy(alpha = 0.8f)
                                                3 -> MaroonPrimary.copy(alpha = 0.6f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    "${user.rank}",
                                                    color = if (user.rank <= 3) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(user.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            if (user.university.isNotBlank()) {
                                                Text(user.university, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            }
                                        }
                                    }
                                    Surface(
                                        color = MaroonPrimary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${user.score} نقطة",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            fontWeight = FontWeight.Bold,
                                            color = MaroonPrimary,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
                    }
    }
}