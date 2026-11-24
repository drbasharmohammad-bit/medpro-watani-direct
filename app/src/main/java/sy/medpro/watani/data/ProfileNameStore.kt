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
        if (name.isNullOrBlank()) "مرحباً بك زميلي الطبيب" else prefixedName(name.trim())

    /**
     * Pure: the student's name WITH its title prefix, the prefix
     * derived from the language of the name itself (doctor's device
     * report, build 1.2.51: «د.» used to sit in the wrong place next
     * to a Latin name like "Bashar Al Mohammad"):
     * - A name whose FIRST LETTER is Arabic → «د.» (Arabic form).
     * - A name whose FIRST LETTER is Latin → "Dr." before it.
     * - Mixed names: the first letter decides (a name that opens
     *   Arabic is an Arabic name with a Latin tail, and vice versa).
     * - No letter at all (digits/symbols only) → the Arabic form,
     *   the app's first language.
     * This is the ONE place the prefix is built — every surface
     * that shows the student's name with a title goes through here.
     */
    fun prefixedName(name: String): String {
        val firstLetter = name.firstOrNull { it.isLetter() }
        return when {
            firstLetter == null -> "د. $name"
            isArabicLetter(firstLetter) -> "د. $name"
            else -> "Dr. $name"
        }
    }

    /** Arabic-script letters: the Arabic block, its supplements and
     * presentation forms — what a student typing an Arabic name
     * actually produces. */
    fun isArabicLetter(ch: Char): Boolean =
        ch in '\u0620'..'\u064A' || // Arabic
            ch in '\u066E'..'\u06D3' || // Arabic Supplement
            ch in '\u0750'..'\u077F' || // Arabic Extended-A
            ch in '\u08A0'..'\u08FF' || // Arabic Extended-B
            ch in '\uFB50'..'\uFDFF' || // Presentation Forms-A
            ch in '\uFE70'..'\uFEFF' // Presentation Forms-B

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
