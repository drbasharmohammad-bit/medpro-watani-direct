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
 * Missing layer, 404, parse failure and network error all resolve to [ExplanationUiState.Absent] -
 * explanations are an enhancement, never a blocker, never an error surface.
 */
@Composable
fun rememberExplanationState(questionKey: String?): ExplanationUiState {
    if (questionKey.isNullOrBlank()) return ExplanationUiState.Absent
    val context = LocalContext.current
    val repository = remember(context) { explanationRepository(context) }
    var state by remember(questionKey) { mutableStateOf<ExplanationUiState>(ExplanationUiState.Loading) }

    LaunchedEffect(questionKey) {
        state = ExplanationUiState.Loading
        val result = repository.fetchExplanation(questionKey).getOrNull()
        state = if (result == null) ExplanationUiState.Absent else ExplanationUiState.Present(result)
    }
    return state
}
