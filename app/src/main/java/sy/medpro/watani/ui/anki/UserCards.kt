package sy.medpro.watani.ui.anki

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A flashcard authored by the student (doctor's order 27 Nov 2026). */
data class UserCard(
    val id: String,
    val front: String,
    val back: String,
    val createdAtMillis: Long
)

/**
 * Student-authored flashcards store: per-account SharedPreferences
 * (`user_cards_<uid>`, guest file without a suffix — the same
 * account-binding pattern as the SRS store), one JSON array under a
 * single key. Cards live only on the device and are scheduled by the
 * same FSRS store as the built-in deck (their ids are the FSRS card
 * keys). Deleting a card also drops its scheduling state.
 */
object UserCards {
    private const val KEY = "cards"
    private const val ID_RANDOM_CHARS = 16

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            FirebaseAuth.getInstance().currentUser?.uid
                ?.takeIf { it.isNotBlank() }
                ?.let { "user_cards_$it" } ?: "user_cards",
            Context.MODE_PRIVATE
        )

    fun list(context: Context): List<UserCard> {
        val arr = prefs(context).getString(KEY, null)?.let { raw ->
            try {
                JSONArray(raw)
            } catch (_: Exception) {
                null
            }
        } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)
                ?.takeIf { it.optString("id").isNotBlank() }
                ?.let { o ->
                    UserCard(
                        id = o.optString("id"),
                        front = o.optString("front"),
                        back = o.optString("back"),
                        createdAtMillis = o.optLong("created", 0L)
                    )
                }
        }
    }

    private fun save(context: Context, cards: List<UserCard>) {
        val arr = JSONArray()
        for (c in cards) {
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("front", c.front)
                    .put("back", c.back)
                    .put("created", c.createdAtMillis)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
        _changes.value = _changes.value + 1
    }

    fun add(context: Context, front: String, back: String): UserCard {
        val card = UserCard(
            id = "u" + UUID.randomUUID().toString().replace("-", "").take(ID_RANDOM_CHARS),
            front = front.trim(),
            back = back.trim(),
            createdAtMillis = System.currentTimeMillis()
        )
        save(context, list(context) + card)
        return card
    }

    fun update(context: Context, id: String, front: String, back: String) {
        save(
            context,
            list(context).map {
                if (it.id == id) it.copy(front = front.trim(), back = back.trim()) else it
            }
        )
    }

    fun delete(context: Context, id: String) {
        save(context, list(context).filterNot { it.id == id })
        FsrsStore.forget(context, id)
    }
}
