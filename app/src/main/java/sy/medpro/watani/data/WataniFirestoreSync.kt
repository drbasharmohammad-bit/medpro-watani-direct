package sy.medpro.watani.data

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object WataniFirestoreSync {

    suspend fun ensureAndSyncUser(context: Context, user: FirebaseUser) = withContext(Dispatchers.IO) {
        try {
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(user.uid)
            val snapshot = userRef.get().await()

            if (!snapshot.exists()) {
                val initialData = mapOf(
                    "name" to (user.displayName ?: "طبيب مقيم"),
                    "email" to (user.email ?: ""),
                    "createdAt" to System.currentTimeMillis()
                )
                userRef.set(initialData, SetOptions.merge()).await()
            }

            // Pull & Merge / Push local stats
            OfflineStudyVault.ensureInitialized(context)
            val stats = OfflineStudyVault.statsFlow.value
            val favsPrefs = context.getSharedPreferences("watani_favorites", Context.MODE_PRIVATE)
            val favs = favsPrefs.getStringSet("fav_list", emptySet())?.toList() ?: emptyList()

            val notesPrefs = context.getSharedPreferences("watani_notes", Context.MODE_PRIVATE)
            val notesJson = notesPrefs.getString("notes_json", "[]") ?: "[]"

            val mistakesList = stats.savedMistakes.map { m ->
                mapOf(
                    "id" to m.id,
                    "subjectTitle" to m.subjectTitle,
                    "questionText" to m.questionText,
                    "selectedAnswer" to m.selectedAnswer,
                    "correctAnswer" to m.correctAnswer,
                    "explanation" to m.explanation,
                    "timestamp" to m.timestamp
                )
            }

            val syncMap = mapOf(
                "totalAnswered" to stats.totalAnswered,
                "totalCorrect" to stats.totalCorrect,
                "xp" to (stats.totalCorrect * 25),
                "mistakes" to mistakesList,
                "favorites" to favs,
                "notes" to notesJson,
                "lastSyncedAt" to System.currentTimeMillis()
            )

            userRef.set(syncMap, SetOptions.merge()).await()

            // Pull remote if newer
            val remoteData = userRef.get().await()
            val remoteXp = remoteData.getLong("xp") ?: 0L
            val localXp = stats.totalCorrect * 25
            if (remoteXp > localXp) {
                val rAns = (remoteData.getLong("totalAnswered") ?: stats.totalAnswered.toLong()).toInt()
                val rCor = (remoteData.getLong("totalCorrect") ?: stats.totalCorrect.toLong()).toInt()
                val prefs = context.getSharedPreferences("watani_offline_study_vault", Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt("total_answered", rAns)
                    .putInt("total_correct", rCor)
                    .apply()
            }
        } catch (e: Exception) {
            // Offline or sync error, fallback silently
        }
    }

    suspend fun validateAndBindActivation(context: Context, user: FirebaseUser, code: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmed = code.trim()
        if (trimmed.isBlank()) return@withContext Pair(false, "يرجى إدخال كود تفعيل صالح.")
        try {
            // 1. REST check activation_codes/{code}
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://firestore.googleapis.com/v1/projects/national-exam-8ecba/databases/ai-studio-nationalexamapp-330e566e-ddc6-4e62-86a9-8009ee9cde64/documents/activation_codes/$trimmed")
                .build()
            val response = client.newCall(request).execute()
            if (response.code == 404) {
                return@withContext Pair(false, "كود تفعيل غير صالح أو غير موجود في قاعدة البيانات.")
            }
            if (!response.isSuccessful) {
                return@withContext Pair(false, "خطأ في الاتصال بالخادم (HTTP ${response.code})")
            }

            val bodyStr = response.body?.string() ?: "{}"
            val docJson = JSONObject(bodyStr)
            val fields = docJson.optJSONObject("fields")

            val boundUidField = fields?.optJSONObject("boundUid")?.optString("stringValue")
            if (!boundUidField.isNullOrBlank() && boundUidField != user.uid) {
                return@withContext Pair(false, "هذا الكود مستخدم بالفعل ومسجل بحساب آخر.")
            }

            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(user.uid)
            val userSnap = userRef.get().await()
            val devices = (userSnap.get("devices") as? List<*>) ?: emptyList<String>()
            val androidId = try {
                android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            } catch (_: Exception) { null }
            val deviceId = if (!androidId.isNullOrBlank()) androidId else (Build.MODEL ?: "AndroidDevice")

            if (!devices.contains(deviceId) && devices.size >= 2) {
                return@withContext Pair(false, "تم تجاوز الحد الأقصى للأجهزة المسموحة (حهازان كحد أقصى للحساب الواحد).")
            }

            val nextDevices = if (devices.contains(deviceId)) devices else devices + deviceId

            val activationPayload = mapOf(
                "activationCode" to trimmed,
                "activatedAt" to System.currentTimeMillis(),
                "devices" to nextDevices
            )
            userRef.set(activationPayload, SetOptions.merge()).await()

            // Bind boundUid on activation_codes/{code} via SDK
            val codeRef = db.collection("activation_codes").document(trimmed)
            codeRef.set(mapOf("boundUid" to user.uid), SetOptions.merge()).await()

            val prefs = context.getSharedPreferences("watani_activation", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_activated", true).putString("activated_code", trimmed).apply()

            Pair(true, "تم تفعيل الحساب والجهاز بنجاح عبر الخادم!")
        } catch (e: Exception) {
            Pair(false, "تعذر التحقق من الكود: ${e.localizedMessage ?: "خطأ شبكة"}")
        }
    }

    suspend fun deactivateDevice(context: Context, user: FirebaseUser) = withContext(Dispatchers.IO) {
        try {
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(user.uid)
            val userSnap = userRef.get().await()
            val devices = (userSnap.get("devices") as? List<*>) ?: emptyList<Any>()
            val androidId = try {
                android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            } catch (_: Exception) { null }
            val deviceId = if (!androidId.isNullOrBlank()) androidId else (Build.MODEL ?: "AndroidDevice")

            val nextDevices = devices.filter { it != deviceId }
            userRef.set(mapOf("devices" to nextDevices), SetOptions.merge()).await()
        } catch (_: Exception) {}
    }
}
