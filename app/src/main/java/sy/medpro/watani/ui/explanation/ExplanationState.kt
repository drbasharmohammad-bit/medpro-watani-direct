package sy.medpro.watani.ui.explanation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Sealed UI state so screens can tell "not asked yet", "loading", "no explanation"
 * and "present" apart. Only [Present] renders a section; [Absent] renders nothing.
 */
sealed interface ExplanationUiState {
    data object Idle : ExplanationUiState
    data object Loading : ExplanationUiState
    data object Absent : ExplanationUiState
    /** Load failed (network/timeout/corrupt shard) — the UI shows a retry
     * button instead of silent nothing. Distinct from Absent, which strictly
     * means "the layer loaded and this question has no record". */
    data class Failed(val retry: () -> Unit) : ExplanationUiState
    data class Present(val content: ExplanationContent) : ExplanationUiState
}

private val sharedRepositories = mutableMapOf<String, ExplanationRepository>()

/** One repository per (context, layer path) for the process lifetime so shards are not re-downloaded. */
fun explanationRepository(context: Context, layerBasePath: String = "explanations/"): ExplanationRepository {
    val appContext = context.applicationContext
    val key = appContext.hashCode().toString() + "|" + layerBasePath
    return sharedRepositories.getOrPut(key) { ExplanationRepository(appContext, layerBasePath) }
}

/**
 * Looks up the explanation for an already-normalized question key.
 * Callers normalize with [normalizeExplanationKey] (id first, then qid, both to string).
 * Missing layer, 404 and "question not covered" resolve to [ExplanationUiState.Absent];
 * network/timeout/shard errors resolve to [ExplanationUiState.Failed] with a retry
 * action — explanations are never silently missing on a failed load again.
 */
@Composable
fun rememberExplanationState(questionKey: String?): ExplanationUiState {
    if (questionKey.isNullOrBlank()) return ExplanationUiState.Absent
    val context = LocalContext.current
    val repository = remember(context) { explanationRepository(context) }
    var state by remember(questionKey) { mutableStateOf<ExplanationUiState>(ExplanationUiState.Loading) }
    var retryNonce by remember(questionKey) { mutableStateOf(0) }

    LaunchedEffect(questionKey, retryNonce) {
        state = ExplanationUiState.Loading
        val result = repository.fetchExplanation(questionKey)
        state = when {
            result.isFailure -> ExplanationUiState.Failed(retry = { retryNonce++ })
            result.getOrNull() == null -> ExplanationUiState.Absent
            else -> ExplanationUiState.Present(result.getOrNull()!!)
        }
    }
    return state
}
