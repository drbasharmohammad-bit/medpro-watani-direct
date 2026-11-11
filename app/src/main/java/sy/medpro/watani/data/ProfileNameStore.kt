package sy.medpro.watani.data

import android.content.Context

/**
 * The student's display name (build 1.2.5): captured once at the
 * sign-in gate, kept in the SAME SharedPreferences store the profile
 * screen edits — `watani_profile_<uid>` (or `watani_profile` signed
 * out), key `display_name` — so the gate, the profile screen and the
 * home greeting can never drift apart. Account-scoped on purpose:
 * the previous shared store is exactly how one account's edited name
 * used to leak into the next account (see ProfileScreen).
 *
 * Resolution priority (pinned by ProfileNameStoreTest):
 * a name the student typed wins; otherwise the Google account's
 * displayName; otherwise no name — the greeting falls back to the
 * generic «مرحباً بك زميلي الطبيب».
 */
object ProfileNameStore {

    const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_NAME_PROMPT_DONE = "name_prompt_done"

    fun prefsNameFor(uid: String?): String =
        if (uid.isNullOrBlank()) "watani_profile" else "watani_profile_$uid"

    /** Pure: which name wins, or null when neither source has one. */
    fun resolveName(manual: String?, googleDisplayName: String?): String? {
        val typed = manual?.trim()?.takeIf { it.isNotEmpty() }
        if (typed != null) return typed
        return googleDisplayName?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Pure: the approved home greeting for a resolved name. */
    fun greetingFor(name: String?): String =
        if (name.isNullOrBlank()) "مرحباً بك زميلي الطبيب" else "د. $name"

    fun savedName(context: Context, uid: String?): String? =
        context.getSharedPreferences(prefsNameFor(uid), Context.MODE_PRIVATE)
            .getString(KEY_DISPLAY_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun saveName(context: Context, uid: String?, name: String) {
        context.getSharedPreferences(prefsNameFor(uid), Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISPLAY_NAME, name.trim())
            .apply()
    }

    /** The one-time name ask at the gate: shown at most once per account. */
    fun isNamePromptDone(context: Context, uid: String?): Boolean =
        context.getSharedPreferences(prefsNameFor(uid), Context.MODE_PRIVATE)
            .getBoolean(KEY_NAME_PROMPT_DONE, false)

    fun markNamePromptDone(context: Context, uid: String?) {
        context.getSharedPreferences(prefsNameFor(uid), Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NAME_PROMPT_DONE, true)
            .apply()
    }
}
