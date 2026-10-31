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

            // Account binding: the vault must belong to THIS user before
            // any stat is read or pushed — otherwise the device's previous
            // account progress would be pushed to the new account.
            OfflineStudyVault.switchAccount(context, user.uid)
            OfflineStudyVault.ensureInitialized(context)
            val statsBeforePull = OfflineStudyVault.statsFlow.value

            // Pull remote FIRST: a fresh per-uid vault starts at zero, so
            // the account's own server totals must be restored BEFORE
            // anything is pushed — pushing first would overwrite the
            // server totals with zero. Only higher remote totals win.
            val remoteData = userRef.get().await()
            val remoteXp = remoteData.getLong("xp") ?: 0L
            if (remoteXp > statsBeforePull.totalCorrect * 25) {
                val rAns = (remoteData.getLong("totalAnswered") ?: statsBeforePull.totalAnswered.toLong()).toInt()
                val rCor = (remoteData.getLong("totalCorrect") ?: statsBeforePull.totalCorrect.toLong()).toInt()
                OfflineStudyVault.adoptRemoteTotals(context, rAns, rCor)
            }
            val stats = OfflineStudyVault.statsFlow.value

            // Favorites/notes are per-account too (same leak class).
            val favsPrefsName = if (user.uid.isBlank()) "watani_favorites" else "watani_favorites_${user.uid}"
            val favsPrefs = context.getSharedPreferences(favsPrefsName, Context.MODE_PRIVATE)
            val favs = favsPrefs.getStringSet("fav_list", emptySet())?.toList() ?: emptyList()

            val notesPrefsName = if (user.uid.isBlank()) "watani_notes" else "watani_notes_${user.uid}"
            val notesPrefs = context.getSharedPreferences(notesPrefsName, Context.MODE_PRIVATE)
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
