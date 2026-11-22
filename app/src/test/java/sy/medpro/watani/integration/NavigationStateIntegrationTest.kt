package sy.medpro.watani.integration

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sy.medpro.watani.ui.viewmodel.AppTab
import sy.medpro.watani.ui.viewmodel.MainViewModel
import sy.medpro.watani.ui.viewmodel.ScreenState

/**
 * Layer 3 — navigation state machine: the REAL MainViewModel the
 * drawer and the bottom bar both drive. Tab switches land on the
 * tab's home and clear the back stack; opening «دورة كاملة» pushes
 * its list state; back pops to where the student was. The tab set
 * itself — the bottom bar's universe — is pinned: nine tabs with
 * the First Aid tab carrying its published name.
 *
 * Scope note: the drawer composable and the bottom bar composable
 * live inside MainActivity's content, behind a persisted Firebase
 * session; a session cannot be fabricated offline (FirebaseAuth
 * has no test seam and sessions are server-issued), so the
 * widgets themselves are not composed in this suite — the state
 * machine they render from is what is proven here. Recorded as a
 * known gap in docs/RELEASE_CHECKLIST.md.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationStateIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
    }

    private fun viewModel(): MainViewModel =
        MainViewModel(context.applicationContext as Application)

    @Test
    fun initialStateIsHomeTabAtHomeScreen() {
        val state = viewModel().uiState.value
        assertEquals(AppTab.HOME, state.currentTab)
        assertEquals(ScreenState.Home, state.screenState)
    }

    @Test
    fun tabSetIsTheNinePublishedTabs() {
        assertEquals(EXPECTED_TAB_COUNT, AppTab.entries.size)
        assertEquals("First Aid - Med Pro", AppTab.FIRSTAID.title)
        assertTrue(AppTab.entries.contains(AppTab.FIRSTAID))
        assertTrue(AppTab.entries.contains(AppTab.CASES))
        assertTrue(AppTab.entries.contains(AppTab.BANK))
    }

    @Test
    fun tabSwitchResetsToTabHomeAndFullDawraPushesAndPops() {
        val vm = viewModel()
        vm.setTab(AppTab.CASES)
        var state = vm.uiState.value
        assertEquals(AppTab.CASES, state.currentTab)
        assertEquals(ScreenState.Home, state.screenState)

        vm.openFullDawraList()
        state = vm.uiState.value
        assertEquals(ScreenState.FullDawraList, state.screenState)

        vm.goBack()
        state = vm.uiState.value
        assertEquals(ScreenState.Home, state.screenState)
        assertEquals(AppTab.CASES, state.currentTab)
    }

    private companion object {
        const val EXPECTED_TAB_COUNT = 9
    }
}
