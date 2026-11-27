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

@Composable
fun CourseDetailContent(
    course: Course,
    lessons: List<CourseLesson>,
    items: List<LibraryItem>,
    viewModel: LibraryViewModel,
    canAccess: Boolean,
    fullyDownloaded: Boolean,
    anyDownloading: Boolean,
    downloadedCount: Int,
    downloadingIds: Set<String>,
    openError: String?,
    onBack: () -> Unit,
    onOpenLesson: (LibraryItem, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CoursesTopBar(title = course.title ?: "كورس", onBack = onBack)
        CourseLessonsList(
            course = course,
            lessons = lessons,
            viewModel = viewModel,
            canAccess = canAccess,
            fullyDownloaded = fullyDownloaded,
            anyDownloading = anyDownloading,
            downloadedCount = downloadedCount,
            totalCount = items.size,
            openError = openError,
            onDownloadAll = {
                items.forEach { item ->
                    if (!viewModel.isItemDownloaded(item) && (item.id ?: "") !in downloadingIds) {
                        viewModel.downloadItem(item)
                    }
                }
            },
            onDeleteAll = { items.forEach { viewModel.deleteItem(it) } },
            onOpenLesson = onOpenLesson
        )
        if (anyDownloading && !fullyDownloaded && items.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { downloadedCount.toFloat() / items.size },
                modifier = Modifier.fillMaxWidth(),
                color = GoldAccent
            )
        }
    }
}

@Composable
private fun CourseLessonsList(
    course: Course,
    lessons: List<CourseLesson>,
    viewModel: LibraryViewModel,
    canAccess: Boolean,
    fullyDownloaded: Boolean,
    anyDownloading: Boolean,
    downloadedCount: Int,
    totalCount: Int,
    openError: String?,
    onDownloadAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onOpenLesson: (LibraryItem, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!course.description.isNullOrBlank()) {
            item {
                Text(course.description ?: "", fontSize = 13.sp, lineHeight = 19.sp, color = MaroonPrimary)
            }
        }
        item {
            CourseAccessHeader(
                canAccess = canAccess,
                fullyDownloaded = fullyDownloaded,
                anyDownloading = anyDownloading,
                downloadedCount = downloadedCount,
                totalCount = totalCount,
                onDownloadAll = onDownloadAll,
                onDeleteAll = onDeleteAll
            )
        }
        items(lessons, key = { it.id ?: "" }) { lesson ->
            LessonRow(
                lesson = lesson,
                item = CourseLogic.lessonItem(course, lesson),
                viewModel = viewModel,
                canAccess = canAccess,
                onOpen = onOpenLesson
            )
        }
        if (openError != null) {
            item {
                Text(openError ?: "", fontSize = 12.sp, color = MaroonPrimary)
            }
        }
    }
}
