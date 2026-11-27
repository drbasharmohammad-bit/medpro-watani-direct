package sy.medpro.watani.data

import android.content.Context
import org.json.JSONObject

/**
 * Build 1.2.53 — persistence for [SectionSwitches]: the last map
 * the gateway served, kept in plain SharedPreferences (the repo's
 * store pattern — ProfileNameStore), read synchronously at every
 * gate so a cold start offline decides by the stored copy, and by
 * [SectionSwitches.DEFAULTS] when nothing was ever stored
 * (== today's behaviour). Refreshed in the background by the same
 * GET /v1/config fetch that carries minVersion
 * ([AppVersionGate.refreshFromServer]) — cache-first, never a
 * loading state, never a block on failure: a failed refresh keeps
 * the stored map untouched.
 */
object SectionSwitchStore {

    private const val PREFS_NAME = "section_switches"
    private const val KEY_PREFIX = "switch_"

    /** The current effective map: stored flips over the defaults. */
    fun current(context: Context): Map<SectionKey, SwitchState> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val merged = SectionSwitches.DEFAULTS.toMutableMap()
        for (key in SectionKey.values()) {
            val stored = prefs.getString(KEY_PREFIX + key.configName, null)
            val state = SwitchState.fromConfigValue(stored)
            if (state != null) merged[key] = state
        }
        return merged
    }

    fun state(context: Context, key: SectionKey): SwitchState =
        SectionSwitches.stateOf(current(context), key)

    /** The pure entry decision for a section right now. */
    fun decision(context: Context, key: SectionKey, isActivated: Boolean): SectionSwitches.EntryDecision =
        SectionSwitches.entryDecision(state(context, key), isActivated)

    /**
     * Learns from one GET /v1/config body: when the body carries a
     * `sectionSwitches` object it REPLACES the stored flips (the
     * gateway always serves the complete effective map); when it
     * does not (older gateway, failed fetch), the stored map is
     * left exactly as it was.
     */
    fun updateFromConfig(context: Context, config: JSONObject?) {
        val switches = config?.optJSONObject("sectionSwitches") ?: return
        val parsed = SectionSwitches.parse(switches)
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (key in SectionKey.values()) {
            editor.putString(KEY_PREFIX + key.configName, SectionSwitches.stateOf(parsed, key).configValue)
        }
        editor.apply()
    }

    /** Test support: back to never-stored (defaults decide). */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
