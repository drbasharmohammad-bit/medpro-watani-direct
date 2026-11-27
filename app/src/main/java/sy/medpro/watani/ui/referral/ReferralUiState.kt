package sy.medpro.watani.ui.referral

import sy.medpro.watani.data.ReferralMe

/** UI state of the «ادعُ أصدقاءك» screen (build 1.2.54). */
sealed interface ReferralUiState {
    data object Loading : ReferralUiState
    data object Failed : ReferralUiState
    data class Content(
        val me: ReferralMe,
        val claiming: Boolean = false,
        val claimNotice: String? = null,
        val claimSucceeded: Boolean = false,
        val copied: Boolean = false
    ) : ReferralUiState
}

/** The invite screen's callbacks, grouped (the repo's value-object
 * pattern for long parameter lists). */
data class ReferralActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onCopy: (String) -> Unit,
    val onShare: (String) -> Unit,
    val onClaim: (String) -> Unit
)
