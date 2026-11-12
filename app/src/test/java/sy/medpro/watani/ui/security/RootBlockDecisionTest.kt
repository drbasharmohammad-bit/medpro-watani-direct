package sy.medpro.watani.ui.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.ui.security.RootBlock.Category

/**
 * Pure-decision tests for the whole-app root block (build 1.2.8).
 *
 * THE INVARIANT under test is the doctor's false-positive firewall:
 * a single signal category must NEVER block the app — budget devices
 * shipping test-keys alone, an odd property alone, or a stray busybox
 * alone all keep working. Only a convergence of >= 2 independent
 * categories (what every real root method produces) blocks.
 */
class RootBlockDecisionTest {

    @Test
    fun cleanDeviceIsNotBlocked() {
        assertFalse(RootBlock.isBlocked(emptySet()))
    }

    @Test
    fun everySingleCategoryAloneIsNotBlocking() {
        for (category in Category.entries) {
            assertFalse(
                "single category $category must never block",
                RootBlock.isBlocked(setOf(category)),
            )
        }
    }

    @Test
    fun testKeysAloneNeverBlocks() {
        // The classic false-positive trap: a stock budget ROM whose
        // only anomaly is test-keys in the build tags.
        assertFalse(RootBlock.isBlocked(setOf(Category.BUILD_PROPS)))
    }

    @Test
    fun magiskStyleConvergenceBlocks() {
        // Magisk: su binary + manager package + /data/adb artifacts.
        assertTrue(
            RootBlock.isBlocked(
                setOf(Category.SU_BINARY, Category.ROOT_PACKAGE, Category.MAGISK_ARTIFACT),
            ),
        )
    }

    @Test
    fun supersuStyleConvergenceBlocks() {
        assertTrue(
            RootBlock.isBlocked(setOf(Category.SU_BINARY, Category.ROOT_PACKAGE)),
        )
    }

    @Test
    fun remountedSystemConvergenceBlocks() {
        assertTrue(
            RootBlock.isBlocked(setOf(Category.SU_BINARY, Category.WRITABLE_SYSTEM)),
        )
    }

    @Test
    fun signalListsAreNonTrivial() {
        // Guards against an accidental empty-list refactor silently
        // disabling a whole category.
        assertTrue(RootBlock.SU_PATHS.size >= 10)
        assertTrue(RootBlock.MAGISK_PATHS.size >= 5)
        assertTrue(RootBlock.BUSYBOX_PATHS.size >= 4)
        assertTrue(RootBlock.ROOT_PACKAGES.size >= 8)
        assertTrue(RootBlock.WRITABLE_WATCH_PATHS.size >= 4)
    }
}
