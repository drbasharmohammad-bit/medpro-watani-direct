package sy.medpro.watani.data

import android.content.Context
import org.json.JSONObject

/**
 * The Tools gate free-use limit (build 1.2.54): the last value the
 * gateway served in GET /v1/config (`toolsFreeLimit`, from app_config
 * tools_free_limit — the doctor adjusts it in the admin panel with no
 * app update). Persisted locally and read synchronously at every gate
 * decision, so a cold start offline decides by the stored copy and by
 * [ToolGate.DEFAULT_FREE_LIMIT] when nothing was ever stored.
 * Refreshed by the same config fetch as the section switches
 * ([AppVersionGate.refreshFromServer]); a failed refresh or a config
 * without the field leaves the stored value untouched.
 */
object ToolsLimitStore {
    private const val PREFS_NAME = "tools_gate"
    private const val KEY_LIMIT = "free_limit"

    fun current(context: Context): Int {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LIMIT, -1)
        return if (stored >= 0) stored else ToolGate.DEFAULT_FREE_LIMIT
    }

    fun updateFromConfig(context: Context, config: JSONObject?) {
        if (config == null || !config.has("toolsFreeLimit")) return
        val value = config.optInt("toolsFreeLimit", -1)
        if (value < 0) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LIMIT, value).apply()
    }

    /** Test support. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
