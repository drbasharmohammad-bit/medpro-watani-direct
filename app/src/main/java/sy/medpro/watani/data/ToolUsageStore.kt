package sy.medpro.watani.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Per-tool free-use counters for the Tools gate (see [ToolGate]).
 * Local-first per-account SharedPreferences (`tool_usage_<uid>`),
 * so counting never needs a network and never breaks offline use.
 * Sync rides the existing user-doc channel: [snapshotJson] is pushed
 * with the progress doc and the worker merges `toolUsage` as a
 * monotonic map (per-tool max wins), [applyRemoteMax] folds a pulled
 * map back in — counts never decrease on any device.
 */
object ToolUsageStore {
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            FirebaseAuth.getInstance().currentUser?.uid
                ?.takeIf { it.isNotBlank() }
                ?.let { "tool_usage_$it" } ?: "tool_usage",
            Context.MODE_PRIVATE
        )

    fun count(context: Context, toolId: String): Int =
        prefs(context).getInt(toolId, 0)

    fun counts(context: Context): Map<String, Int> =
        ToolGate.TOOLS.associate { it.id to count(context, it.id) }

    fun recordUse(context: Context, toolId: String) {
        val p = prefs(context)
        p.edit().putInt(toolId, p.getInt(toolId, 0) + 1).apply()
        _changes.value = _changes.value + 1
    }

    fun snapshotJson(context: Context): JSONObject {
        val obj = JSONObject()
        for ((id, n) in counts(context)) if (n > 0) obj.put(id, n)
        return obj
    }

    /** Folds a pulled remote map in, per-tool max (monotonic). */
    fun applyRemoteMax(context: Context, remote: JSONObject?) {
        if (remote == null) return
        val p = prefs(context)
        val editor = p.edit()
        var changed = false
        val keys = remote.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val remoteCount = remote.optInt(key, 0)
            if (remoteCount > p.getInt(key, 0)) {
                editor.putInt(key, remoteCount)
                changed = true
            }
        }
        if (changed) {
            editor.apply()
            _changes.value = _changes.value + 1
        }
    }
}
