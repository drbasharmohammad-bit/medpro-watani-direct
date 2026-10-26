package sy.medpro.watani.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import sy.medpro.watani.ui.theme.MaroonPrimary

data class LeaderboardUser(val rank: Int, val name: String, val score: Int, val specialty: String)

@Composable
fun LeaderboardScreen() {
    val context = LocalContext.current
    var users by remember { mutableStateOf<List<LeaderboardUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTrigger) {
        isLoading = true
        errorMessage = null
        try {
            val list = mutableListOf<LeaderboardUser>()
            try {
                val db = FirebaseFirestore.getInstance()
                val snapshot = db.collection("users").orderBy("xp", Query.Direction.DESCENDING).limit(50).get().await()
                var r = 1
                for (doc in snapshot.documents) {
                    val name = doc.getString("name") ?: "طبيب مقيم"
                    val xp = (doc.getLong("xp") ?: 0L).toInt()
                    val specialty = doc.getString("specialty") ?: "طب بشري"
                    list.add(LeaderboardUser(rank = r++, name = name, score = xp, specialty = specialty))
                }
            } catch (_: Exception) {}

            if (list.isEmpty()) {
                val result = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val request = okhttp3.Request.Builder()
                        .url("https://watani-api.drbasharmohammed.workers.dev/v1/leaderboard")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: "[]"
                            val jsonArr = JSONArray(body)
                            val wList = mutableListOf<LeaderboardUser>()
                            for (i in 0 until jsonArr.length()) {
                                val obj = jsonArr.getJSONObject(i)
                                wList.add(
                                    LeaderboardUser(
                                        rank = obj.optInt("rank", i + 1),
                                        name = obj.optString("name", "طبيب متميز"),
                                        score = obj.optInt("score", 0),
                                        specialty = obj.optString("specialty", "طب بشري")
                                    )
                                )
                            }
                            wList
                        } else {
                            emptyList()
                        }
                    }
                }
                users = result
            } else {
                users = list
            }
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "تعذر الاتصال بالخادم. يرجى التحقق من اتصال الإنترنت والإعادة."
            isLoading = false
            users = emptyList()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                                Text(
                                    text = errorMessage ?: "لا توجد بيانات حالياً في لوحة الصدارة",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                                Button(
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
                                            Text(user.specialty, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
