package sy.medpro.watani.data

import android.content.Context

/**
 * Forced privacy consent before registration (build 1.2.28, doctor's
 * order 16 Nov 2026): no Google sign-in — indeed no app entry at
 * all — before the user has explicitly accepted the CURRENT policy
 * version on the dedicated blocking consent screen
 * (ui/auth/PrivacyConsentScreen).
 *
 * Semantics:
 *  - The acceptance is stored on the DEVICE (this gate runs before
 *    any account exists), keyed by the accepted document version.
 *  - [CURRENT_POLICY_VERSION] must always equal the version header
 *    of the approved document (~/workspace/privacy/POLICY_TEXT.md
 *    and the bundled assets/privacy_policy.txt). Bumping the
 *    document version re-asks every user once, exactly as the
 *    policy's own «تعديلات السياسة» clause promises.
 *  - The bundled text makes the gate fully offline: a saved Google
 *    session is never touched by this gate, and after one acceptance
 *    daily offline entry is exactly as before.
 */
object PrivacyConsent {

    /** Version of the approved policy document this build carries
     * (POLICY_TEXT.md header: «الإصدار 1.1»). */
    const val CURRENT_POLICY_VERSION = "1.1"

    /** The permanent official page (also printed in the document). */
    const val OFFICIAL_POLICY_URL = "https://watani-content.pages.dev/privacy.html"

    private const val PREFS_NAME = "privacy_consent"
    private const val KEY_ACCEPTED_VERSION = "accepted_policy_version"

    /** Pure decision (unit-tested): consent is required whenever the
     * stored acceptance is missing or names any other version. */
    fun isConsentRequired(acceptedVersion: String?): Boolean =
        acceptedVersion?.trim() != CURRENT_POLICY_VERSION

    fun acceptedVersion(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCEPTED_VERSION, null)

    fun isConsentRequired(context: Context): Boolean =
        isConsentRequired(acceptedVersion(context))

    /** Records acceptance of the current version on this device. */
    fun recordConsent(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCEPTED_VERSION, CURRENT_POLICY_VERSION)
            .apply()
    }
}
