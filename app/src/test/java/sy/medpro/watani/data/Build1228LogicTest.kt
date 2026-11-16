package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.28 pure-logic pins for the doctor's three orders of
 * 16 Nov 2026 (the two that carry logic; the policy text itself is
 * pinned by the release gate against POLICY_TEXT.md):
 *
 * 1. Forced privacy consent: required whenever the stored
 *    acceptance is missing or names any version other than the
 *    current document version — so a policy update re-asks once,
 *    and an accepted current version never re-asks.
 * 2. In-app update: the installer only ever trusts an https URL on
 *    the content host itself; every other config value resolves to
 *    the one fixed update address.
 */
class Build1228LogicTest {

    // ---------------- PrivacyConsent ----------------

    @Test
    fun consentRequiredWhenNeverAccepted() {
        assertTrue(PrivacyConsent.isConsentRequired(null))
        assertTrue(PrivacyConsent.isConsentRequired(""))
        assertTrue(PrivacyConsent.isConsentRequired("   "))
    }

    @Test
    fun consentRequiredForOlderPolicyVersion() {
        // 1.0 was the pre-gate document (build 1.2.19 consent line):
        // its users must see the blocking screen once on 1.2.28.
        assertTrue(PrivacyConsent.isConsentRequired("1.0"))
    }

    @Test
    fun consentNotRequiredForCurrentVersion() {
        assertFalse(PrivacyConsent.isConsentRequired(PrivacyConsent.CURRENT_POLICY_VERSION))
        // A stray-whitespace stored value still counts as accepted.
        assertFalse(PrivacyConsent.isConsentRequired("  ${PrivacyConsent.CURRENT_POLICY_VERSION}  "))
    }

    @Test
    fun currentPolicyVersionMatchesApprovedDocument() {
        // POLICY_TEXT.md header: «الإصدار 1.1 — تاريخ السريان:
        // 16 تشرين الثاني 2026». If the document is bumped, this
        // pin fails until the constant follows it.
        assertEquals("1.1", PrivacyConsent.CURRENT_POLICY_VERSION)
        assertEquals(
            "https://watani-content.pages.dev/privacy.html",
            PrivacyConsent.OFFICIAL_POLICY_URL
        )
    }

    // ---------------- AppUpdate ----------------

    @Test
    fun fixedFallbackIsTheContentSiteApk() {
        assertEquals(
            "https://watani-content.pages.dev/apk/watani-latest.apk",
            AppUpdate.FALLBACK_APK_URL
        )
        assertTrue(AppUpdate.isApkUrlAllowed(AppUpdate.FALLBACK_APK_URL))
    }

    @Test
    fun configUrlOnContentHostIsTrusted() {
        assertEquals(
            "https://watani-content.pages.dev/apk/watani-latest.apk",
            AppUpdate.resolveApkUrl("https://watani-content.pages.dev/apk/watani-latest.apk")
        )
        // Any path on the same host over https is acceptable.
        assertTrue(AppUpdate.isApkUrlAllowed("https://watani-content.pages.dev/apk/v2/app.apk"))
    }

    @Test
    fun untrustedConfigUrlsFallBack() {
        assertEquals(AppUpdate.FALLBACK_APK_URL, AppUpdate.resolveApkUrl(null))
        assertEquals(AppUpdate.FALLBACK_APK_URL, AppUpdate.resolveApkUrl(""))
        // Plain http on the right host is still refused.
        assertEquals(
            AppUpdate.FALLBACK_APK_URL,
            AppUpdate.resolveApkUrl("http://watani-content.pages.dev/apk/watani-latest.apk")
        )
        // A foreign host is refused…
        assertEquals(
            AppUpdate.FALLBACK_APK_URL,
            AppUpdate.resolveApkUrl("https://evil.example/apk/watani-latest.apk")
        )
        // …and so is a look-alike host that merely starts alike.
        assertEquals(
            AppUpdate.FALLBACK_APK_URL,
            AppUpdate.resolveApkUrl("https://watani-content.pages.dev.evil.example/x.apk")
        )
        assertFalse(AppUpdate.isApkUrlAllowed("file:///sdcard/x.apk"))
    }
}
