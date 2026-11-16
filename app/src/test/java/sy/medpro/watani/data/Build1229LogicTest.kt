package sy.medpro.watani.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 1.2.29 pure-logic pins for the doctor's design correction
 * of 16 Nov 2026: the 1.2.28 blocking consent SCREEN is gone; the
 * consent is a checkbox on the sign-in screen itself, under the
 * Google button (the global-apps pattern).
 *
 * 1. The checkbox starts ticked exactly when the CURRENT policy
 *    version was already accepted on this device — a returning
 *    user is never re-asked for the same version.
 * 2. A missing or older-version acceptance starts it unticked, so
 *    a material policy update is re-agreed before sign-in.
 * 3. The Google button is enabled only while the box is ticked
 *    and no sign-in is in flight.
 */
class Build1229LogicTest {

    // ---------------- Checkbox initial state ----------------

    @Test
    fun checkboxStartsUntickedWhenNeverAccepted() {
        assertFalse(PrivacyConsent.initialCheckboxChecked(null))
        assertFalse(PrivacyConsent.initialCheckboxChecked(""))
        assertFalse(PrivacyConsent.initialCheckboxChecked("   "))
    }

    @Test
    fun checkboxStartsUntickedForOlderPolicyVersion() {
        // 1.0 was the pre-checkbox document: its acceptance does
        // not carry over to the 1.1 checkbox.
        assertFalse(PrivacyConsent.initialCheckboxChecked("1.0"))
    }

    @Test
    fun checkboxStartsTickedForCurrentPolicyVersion() {
        assertTrue(PrivacyConsent.initialCheckboxChecked(PrivacyConsent.CURRENT_POLICY_VERSION))
        // A stray-whitespace stored value still counts as accepted.
        assertTrue(PrivacyConsent.initialCheckboxChecked("  ${PrivacyConsent.CURRENT_POLICY_VERSION}  "))
    }

    @Test
    fun checkboxStateIsTheInverseOfConsentRequired() {
        // One decision, two views: the 1.2.28 "required" predicate
        // and the 1.2.29 checkbox state must never disagree.
        for (v in listOf(null, "", "1.0", "1.1", " 1.1 ", "2.0")) {
            assertTrue(PrivacyConsent.initialCheckboxChecked(v) == !PrivacyConsent.isConsentRequired(v))
        }
    }

    // ---------------- Google button enablement ----------------

    @Test
    fun signInEnabledOnlyWhenTickedAndIdle() {
        assertTrue(PrivacyConsent.isSignInEnabled(consentChecked = true, isLoading = false))
        assertFalse(PrivacyConsent.isSignInEnabled(consentChecked = false, isLoading = false))
        assertFalse(PrivacyConsent.isSignInEnabled(consentChecked = true, isLoading = true))
        assertFalse(PrivacyConsent.isSignInEnabled(consentChecked = false, isLoading = true))
    }
}
