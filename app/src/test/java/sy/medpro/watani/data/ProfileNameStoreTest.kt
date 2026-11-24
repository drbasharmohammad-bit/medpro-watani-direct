package sy.medpro.watani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Build 1.2.5: the sign-in gate's name resolution, pinned off-device.
 * Priority: a name the student typed > the Google account's
 * displayName > no name (generic greeting). The store the gate
 * writes is the same one the profile screen edits and the home
 * greeting reads (watani_profile_<uid> / display_name).
 */
class ProfileNameStoreTest {

    @Test
    fun typedNameWinsOverGoogle() {
        assertEquals("بشار المحمد", ProfileNameStore.resolveName("بشار المحمد", "Bashar Google"))
    }

    @Test
    fun typedNameIsTrimmed() {
        assertEquals("بشار", ProfileNameStore.resolveName("  بشار  ", null))
    }

    @Test
    fun blankTypedNameFallsBackToGoogle() {
        assertEquals("Bashar Google", ProfileNameStore.resolveName("", "Bashar Google"))
        assertEquals("Bashar Google", ProfileNameStore.resolveName("   ", "Bashar Google"))
        assertEquals("Bashar Google", ProfileNameStore.resolveName(null, "Bashar Google"))
    }

    @Test
    fun noNameAnywhereResolvesToNull() {
        assertNull(ProfileNameStore.resolveName(null, null))
        assertNull(ProfileNameStore.resolveName("", ""))
        assertNull(ProfileNameStore.resolveName("  ", "  "))
    }

    @Test
    fun greetingUsesDoctorPrefixOrGenericFallback() {
        assertEquals("د. بشار المحمد", ProfileNameStore.greetingFor("بشار المحمد"))
        assertEquals("مرحباً بك زميلي الطبيب", ProfileNameStore.greetingFor(null))
        assertEquals("مرحباً بك زميلي الطبيب", ProfileNameStore.greetingFor(""))
    }

    @Test
    fun prefsAreScopedPerAccount() {
        assertEquals("watani_profile", ProfileNameStore.prefsNameFor(null))
        assertEquals("watani_profile", ProfileNameStore.prefsNameFor(""))
        assertEquals("watani_profile_uid-123", ProfileNameStore.prefsNameFor("uid-123"))
    }
}

/**
 * Build 1.2.51 — the title prefix follows the NAME's language
 * (doctor's device photo: «د.» sat wrong next to a Latin name).
 * Arabic name → «د.»; Latin name → "Dr."; mixed names are decided
 * by their FIRST letter; letter-less names fall back to Arabic.
 */
class NamePrefixTest {

    @Test
    fun arabicNameGetsArabicPrefix() {
        assertEquals("د. بشار المحمد", ProfileNameStore.prefixedName("بشار المحمد"))
        assertEquals("د. بشار المحمد", ProfileNameStore.greetingFor("بشار المحمد"))
    }

    @Test
    fun latinNameGetsDrPrefix() {
        assertEquals("Dr. Bashar Al Mohammad", ProfileNameStore.prefixedName("Bashar Al Mohammad"))
        assertEquals("Dr. Bashar Al Mohammad", ProfileNameStore.greetingFor("Bashar Al Mohammad"))
    }

    @Test
    fun mixedNameStartingArabicGetsArabicPrefix() {
        assertEquals("د. بشار Bashar", ProfileNameStore.prefixedName("بشار Bashar"))
    }

    @Test
    fun mixedNameStartingLatinGetsDrPrefix() {
        assertEquals("Dr. Bashar بشار", ProfileNameStore.prefixedName("Bashar بشار"))
    }

    @Test
    fun leadingNonLettersAreSkippedForTheDecision() {
        // A leading digit/space does not decide — the first LETTER does.
        assertEquals("Dr. 9Bashar", ProfileNameStore.prefixedName("9Bashar"))
        assertEquals("د. بشار", ProfileNameStore.greetingFor("  بشار  "))
    }

    @Test
    fun letterlessNameFallsBackToArabicPrefix() {
        assertEquals("د. 123", ProfileNameStore.prefixedName("123"))
    }

    @Test
    fun blankNameKeepsTheGenericGreeting() {
        assertEquals("مرحباً بك زميلي الطبيب", ProfileNameStore.greetingFor(null))
        assertEquals("مرحباً بك زميلي الطبيب", ProfileNameStore.greetingFor("   "))
    }
}
