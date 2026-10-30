package sy.medpro.watani.data

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Team 1 note (entry-activation): code validation NO LONGER lives here.
 * [validateAndBindActivation] and [deactivateDevice] were removed — the
 * Worker contract replaces them (see WorkerActivationApi: POST
 * /v1/activate, /v1/activate/refresh, and the /v1/content gateway).
 * [ensureAndSyncUser] (user doc + progress sync) is kept unchanged.
 */
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

            // تثبيت النقاط على الـWorker (بذر الصدارة الجديدة؛ فشلها صامت ولا يمس مزامنة Firestore)
            try {
                val xp = stats.totalCorrect * 25
                WorkerActivationApi.postToWorker(user, "/v1/score/set", JSONObject().put("points", xp))
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            // Offline or sync error, fallback silently
        }
    }
}
