package sy.medpro.watani.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.Course
import sy.medpro.watani.data.CourseLesson
import sy.medpro.watani.data.CourseLogic
import sy.medpro.watani.data.LibraryItem
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Build 1.2.53 — the course detail page's body (split out of
 * CoursesScreens.kt: detekt length/complexity ceilings, same
 * split pattern as the 1.2.52 reader probe). Rendering only —
 * all state lives in CourseDetailScreen / LibraryViewModel.
 */

/** Everything the course detail body renders from, bundled as
 * one value object (detekt LongParameterList — the same pattern
 * as WidgetSizeBounds in the 1.2.52 detekt round). */
data class CourseDetailUiState(
    val course: Course,
    val lessons: List<CourseLesson>,
    val items: List<LibraryItem>,
    val canAccess: Boolean,
    val fullyDownloaded: Boolean,
    val anyDownloading: Boolean,
    val downloadedCount: Int,
    val downloadingIds: Set<String>,
    val openError: String?
)

@Composable
fun CourseDetailContent(
    state: CourseDetailUiState,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenLesson: (LibraryItem, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CoursesTopBar(title = state.course.title ?: "كورس", onBack = onBack)
        CourseLessonsList(
            state = state,
            viewModel = viewModel,
            onDownloadAll = {
                state.items.forEach { item ->
                    if (!viewModel.isItemDownloaded(item) && (item.id ?: "") !in state.downloadingIds) {
                        viewModel.downloadItem(item)
                    }
                }
            },
            onDeleteAll = { state.items.forEach { viewModel.deleteItem(it) } },
            onOpenLesson = onOpenLesson
        )
        if (state.anyDownloading && !state.fullyDownloaded && state.items.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { state.downloadedCount.toFloat() / state.items.size },
                modifier = Modifier.fillMaxWidth(),
                color = GoldAccent
            )
        }
    }
}

@Composable
private fun CourseLessonsList(
    state: CourseDetailUiState,
    viewModel: LibraryViewModel,
    onDownloadAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onOpenLesson: (LibraryItem, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!state.course.description.isNullOrBlank()) {
            item {
                Text(state.course.description ?: "", fontSize = 13.sp, lineHeight = 19.sp, color = MaroonPrimary)
            }
        }
        item {
            CourseAccessHeader(
                canAccess = state.canAccess,
                fullyDownloaded = state.fullyDownloaded,
                anyDownloading = state.anyDownloading,
                downloadedCount = state.downloadedCount,
                totalCount = state.items.size,
                onDownloadAll = onDownloadAll,
                onDeleteAll = onDeleteAll
            )
        }
        items(state.lessons, key = { it.id ?: "" }) { lesson ->
            LessonRow(
                lesson = lesson,
                item = CourseLogic.lessonItem(state.course, lesson),
                viewModel = viewModel,
                canAccess = state.canAccess,
                onOpen = onOpenLesson
            )
        }
        if (state.openError != null) {
            item {
                Text(state.openError ?: "", fontSize = 12.sp, color = MaroonPrimary)
            }
        }
    }
}
