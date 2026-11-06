package sy.medpro.watani.data

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Migration phase 3 — user doc + progress sync on the Worker gateway
 * (GET/PUT /v1/user/me), replacing the Firestore path of
 * [WataniFirestoreSync.ensureAndSyncUser] call-for-call.
 *
 * Semantics are copied LITERALLY from the Firestore implementation
 * (see INVENTORY.md §2.1):
 *  1. Read the doc first; when absent, create it (merge) with
 *     {name (displayName or «طبيب مقيم»), email, createdAt (millis)}.
 *  2. Bind the vault to THIS account before any stat is read or pushed
 *     ([OfflineStudyVault.switchAccount] + ensureInitialized).
 *  3. Pull BEFORE any push: a second read supplies the remote doc.
 *     Remote totals are adopted only when the remote xp wins —
 *     the judgement is on xp ALONE (same expression as the old code):
 *     when it wins, totalAnswered/totalCorrect are adopted as-is.
 *  4. Detail lists are restored on first sync only, gated on the local
 *     store being empty: mistakes (vault), favorites (prefs), notes
 *     (prefs, stored as a serialized JSON string — unchanged shape).
 *  5. Push is a partial merge (PUT /v1/user/me) of exactly the same
 *     fields the Firestore merge wrote; the mistakes list is re-read
 *     fresh from the flow after the restore step.
 *  6. After a successful push only: seed the Worker leaderboard via
 *     POST /v1/score/set — its failure is silent and never touches
 *     the sync itself (same as the old code).
 *
 * Transport: the gateway client of [WorkerActivationApi] — same
 * WORKER_BASE, same OkHttp client, same Firebase ID-token fetch
 * (getFromWorker / putToWorker / postToWorker).
 *
 * Error handling (documented choice, mirroring the old code): every
 * failure is swallowed silently — no retry is scheduled; the next
 * sync happens at the next existing trigger (sign-in / cold start,
 * quiz completion, quiz exit). A failed pull ABORTS the sync before
 * any push, so a fresh zeroed vault can never overwrite server
 * totals. There is NO automatic fallback write to Firestore: the
 * Firestore object stays in the tree, uncalled, as an inactive
 * reserve by this round's explicit decision.
 */
object WataniGatewaySync {

    /** One gateway pull. Null = any failure (network, non-200, bad
     * body): the caller must abort the sync without pushing. */
    private data class PullResult(val exists: Boolean, val doc: JSONObject?)

    private suspend fun pull(user: FirebaseUser): PullResult? {
        val res = WorkerActivationApi.getFromWorker(user, "/v1/user/me")
        if (res.code != 200) return null
        val root = try {
            JSONObject(res.body ?: "")
        } catch (_: Exception) {
            return null
        }
        if (!root.optBoolean("ok", false)) return null
        val exists = root.optBoolean("exists", false)
        return PullResult(exists, if (exists) root.optJSONObject("user") else null)
    }

    /** One gateway partial-merge push. True only on 200 + ok:true. */
    private suspend fun put(user: FirebaseUser, patch: JSONObject): Boolean {
        val res = WorkerActivationApi.putToWorker(user, "/v1/user/me", patch)
        if (res.code != 200) return false
        return try {
            JSONObject(res.body ?: "").optBoolean("ok", false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureAndSyncUser(context: Context, user: FirebaseUser) = withContext(Dispatchers.IO) {
        try {
            // 1) Ensure the doc exists (create with merge when absent).
            val firstPull = pull(user) ?: return@withContext
            if (!firstPull.exists) {
                val initialData = JSONObject().apply {
                    put("name", user.displayName ?: "طبيب مقيم")
                    put("email", user.email ?: "")
                    put("createdAt", System.currentTimeMillis())
                }
                if (!put(user, initialData)) return@withContext
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
            val remoteData = pull(user)?.doc ?: return@withContext
            val remoteXp = if (remoteData.has("xp") && !remoteData.isNull("xp")) {
                remoteData.optLong("xp", 0L)
            } else {
                0L
            }
            if (remoteXp > statsBeforePull.totalCorrect * 25) {
                val rAns = (if (remoteData.has("totalAnswered") && !remoteData.isNull("totalAnswered")) {
                    remoteData.optLong("totalAnswered")
                } else {
                    statsBeforePull.totalAnswered.toLong()
                }).toInt()
                val rCor = (if (remoteData.has("totalCorrect") && !remoteData.isNull("totalCorrect")) {
                    remoteData.optLong("totalCorrect")
                } else {
                    statsBeforePull.totalCorrect.toLong()
                }).toInt()
                OfflineStudyVault.adoptRemoteTotals(context, rAns, rCor)
            }
            val stats = OfflineStudyVault.statsFlow.value

            // Favorites/notes are per-account too (same leak class).
            val favsPrefsName = if (user.uid.isBlank()) "watani_favorites" else "watani_favorites_${user.uid}"
            val favsPrefs = context.getSharedPreferences(favsPrefsName, Context.MODE_PRIVATE)
            var favs = favsPrefs.getStringSet("fav_list", emptySet())?.toList() ?: emptyList()

            val notesPrefsName = if (user.uid.isBlank()) "watani_notes" else "watani_notes_${user.uid}"
            val notesPrefs = context.getSharedPreferences(notesPrefsName, Context.MODE_PRIVATE)
            var notesJson = notesPrefs.getString("notes_json", "[]") ?: "[]"

            // Restore the account's own detail lists on first sync only:
            // the local stores are empty on a fresh per-uid vault, so the
            // server copies refill them — and the push below then keeps
            // them instead of overwriting the cloud lists with empty ones.
            if (stats.savedMistakes.isEmpty()) {
                val remoteMistakesArr = remoteData.optJSONArray("mistakes")
                val remoteMistakes = buildList {
                    if (remoteMistakesArr != null) {
                        for (i in 0 until remoteMistakesArr.length()) {
                            val m = remoteMistakesArr.optJSONObject(i) ?: continue
                            val mistakeId = m.optString("id", "")
                            if (mistakeId.isEmpty()) continue
                            try {
                                add(
                                    SavedMistakeItem(
                                        id = mistakeId,
                                        subjectTitle = m.optString("subjectTitle", ""),
                                        questionText = m.optString("questionText", ""),
                                        selectedAnswer = m.optString("selectedAnswer", ""),
                                        correctAnswer = m.optString("correctAnswer", ""),
                                        explanation = m.optString("explanation", ""),
                                        timestamp = m.optLong("timestamp", 0L)
                                    )
                                )
                            } catch (_: Exception) {
                                // Skip the malformed item, like the old mapNotNull.
                            }
                        }
                    }
                }
                if (remoteMistakes.isNotEmpty()) OfflineStudyVault.adoptRemoteMistakes(context, remoteMistakes)
            }
            if (favs.isEmpty()) {
                val remoteFavsArr = remoteData.optJSONArray("favorites")
                val remoteFavs = buildList {
                    if (remoteFavsArr != null) {
                        for (i in 0 until remoteFavsArr.length()) {
                            (remoteFavsArr.opt(i) as? String)?.let { add(it) }
                        }
                    }
                }
                if (remoteFavs.isNotEmpty()) {
                    favsPrefs.edit().putStringSet("fav_list", remoteFavs.toSet()).apply()
                    favs = remoteFavs
                }
            }
            if (notesJson.isBlank() || notesJson == "[]") {
                val remoteNotes = remoteData.optString("notes", "")
                if (remoteNotes.isNotBlank() && remoteNotes != "[]") {
                    notesPrefs.edit().putString("notes_json", remoteNotes).apply()
                    notesJson = remoteNotes
                }
            }

            // Fresh flow read: the detail-pull above may have restored the
            // mistakes list into the vault — pushing the stale copy would
            // overwrite the cloud list with empty.
            val mistakesArr = JSONArray()
            OfflineStudyVault.statsFlow.value.savedMistakes.forEach { m ->
                mistakesArr.put(
                    JSONObject().apply {
                        put("id", m.id)
                        put("subjectTitle", m.subjectTitle)
                        put("questionText", m.questionText)
                        put("selectedAnswer", m.selectedAnswer)
                        put("correctAnswer", m.correctAnswer)
                        put("explanation", m.explanation)
                        put("timestamp", m.timestamp)
                    }
                )
            }
            val favsArr = JSONArray()
            favs.forEach { favsArr.put(it) }

            val syncJson = JSONObject().apply {
                put("totalAnswered", stats.totalAnswered)
                put("totalCorrect", stats.totalCorrect)
                put("xp", stats.totalCorrect * 25)
                put("mistakes", mistakesArr)
                put("favorites", favsArr)
                put("notes", notesJson)
                put("lastSyncedAt", System.currentTimeMillis())
            }

            if (!put(user, syncJson)) return@withContext

            // تثبيت النقاط على الـWorker (بذر الصدارة الجديدة؛ فشلها صامت ولا يمس المزامنة)
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
