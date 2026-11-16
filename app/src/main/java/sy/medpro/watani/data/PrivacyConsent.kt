package sy.medpro.watani.data

import android.content.Context

/**
 * Privacy consent on the sign-in screen (build 1.2.29, doctor's
 * correction of the same day): the global-apps pattern — NO
 * separate blocking consent screen (the 1.2.28 full-screen gate
 * was rejected: «مو هيج تنحط… خربت المود»). Instead, the sign-in
 * screen carries one consent row directly under the Google button:
 * a checkbox plus «أوافق على سياسة الخصوصية», with «سياسة
 * الخصوصية» itself a link that opens the full document. The
 * Google button stays disabled until the box is ticked; ticking
 * enables it at once.
 *
 * Semantics:
 *  - The acceptance is stored on the DEVICE (it precedes any
 *    account), keyed by the accepted document version.
 *  - [CURRENT_POLICY_VERSION] must always equal the version header
 *    of the approved document (~/workspace/privacy/POLICY_TEXT.md
 *    and the bundled assets/privacy_policy.txt). Bumping the
 *    document version returns the checkbox to unticked for
 *    everyone, exactly as the policy's own «تعديلات السياسة»
 *    clause promises; a user who already accepted the CURRENT
 *    version finds the box pre-ticked on any later sign-in.
 *  - Nothing here ever blocks or signs out an existing session:
 *    the consent row lives only on the sign-in screen, which a
 *    saved Google session never reaches.
 */
object PrivacyConsent {

    /** Version of the approved policy document this build carries
     * (POLICY_TEXT.md header: «الإصدار 1.1»). */
    const val CURRENT_POLICY_VERSION = "1.1"

    /** The permanent official page (also printed in the document). */
    const val OFFICIAL_POLICY_URL = "https://watani-content.pages.dev/privacy.html"

    private const val PREFS_NAME = "privacy_consent"
    private const val KEY_ACCEPTED_VERSION = "accepted_policy_version"

    /** Pure decision (unit-tested): consent is still outstanding
     * whenever the stored acceptance is missing or names any other
     * version. */
    fun isConsentRequired(acceptedVersion: String?): Boolean =
        acceptedVersion?.trim() != CURRENT_POLICY_VERSION

    /** Pure (unit-tested): the sign-in checkbox starts ticked
     * exactly when the current policy version was already accepted
     * on this device; a missing or older acceptance starts it
     * unticked, so a material policy update is re-agreed. */
    fun initialCheckboxChecked(acceptedVersion: String?): Boolean =
        !isConsentRequired(acceptedVersion)

    /** Pure (unit-tested): the Google button is enabled only while
     * the consent box is ticked and no sign-in is in flight. */
    fun isSignInEnabled(consentChecked: Boolean, isLoading: Boolean): Boolean =
        consentChecked && !isLoading

    fun acceptedVersion(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCEPTED_VERSION, null)

    fun isConsentRequired(context: Context): Boolean =
        isConsentRequired(acceptedVersion(context))

    /** True when this device already accepted the current version
     * (the checkbox's initial state on the sign-in screen). */
    fun isConsentGiven(context: Context): Boolean =
        !isConsentRequired(context)

    /** Records acceptance of the current version on this device. */
    fun recordConsent(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCEPTED_VERSION, CURRENT_POLICY_VERSION)
            .apply()
    }
}
