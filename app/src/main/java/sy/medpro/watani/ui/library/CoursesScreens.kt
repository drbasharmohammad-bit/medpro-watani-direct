package sy.medpro.watani.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.launch
import sy.medpro.watani.data.Course
import sy.medpro.watani.data.CourseLesson
import sy.medpro.watani.data.CourseLogic
import sy.medpro.watani.data.LibraryItem
import sy.medpro.watani.data.WorkerActivationApi
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonDeep
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Build 1.2.53 — library courses UI. The catalog is server data
 * (courses.json); these screens render whatever it carries, so a
 * course published tomorrow appears here with no app update.
 * Downloads and playback reuse the library machinery end to end
 * (LibraryViewModel → WataniRepository): a lesson downloads once
 * and then opens fully offline — PDFs in the unified reader,
 * videos in the existing player.
 */

/** The «الكورسات» entry card on the Library entries list —
 * visual twin of the videos entry card. */
@Composable
fun CoursesEntryCard(
    coursesCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaroonDeep),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.School, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(30.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("الكورسات", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "$coursesCount كورس — حمّل الكورس وادرسه بلا إنترنت",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 17.sp
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun CoursesListScreen(
    courses: List<Course>,
    onCourseClick: (Course) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CoursesTopBar(title = "الكورسات", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(courses, key = { it.id ?: "" }) { course ->
                CourseCard(course = course, onClick = { onCourseClick(course) })
            }
        }
    }
}

@Composable
private fun CourseCard(course: Course, onClick: () -> Unit) {
    val context = LocalContext.current
    val locked = !CourseLogic.canAccess(course, WorkerActivationApi.isActivated(context))
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaroonDeep),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.School, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(30.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(course.title ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                if (!course.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(course.description ?: "", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 17.sp)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${course.lessons?.size ?: 0} درس" + if (locked) " — للمفعّلين" else "",
                    fontSize = 12.sp, color = GoldAccent, lineHeight = 17.sp
                )
            }
            if (locked) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun CoursesTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "رجوع", tint = MaroonPrimary)
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaroonPrimary)
    }
}

/** An open lesson inside the course page: the local file the
 * existing machinery decrypted/built a playback session for. */
private data class OpenLesson(val file: File, val title: String, val isPdf: Boolean, val item: LibraryItem)

@Composable
fun CourseDetailScreen(
    course: Course,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onReaderFullscreenChange: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var openLesson by remember { mutableStateOf<OpenLesson?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    val isActivated = WorkerActivationApi.isActivated(context)
    val canAccess = CourseLogic.canAccess(course, isActivated)
    val lessons = course.lessons.orEmpty()
    val items = lessons.map { CourseLogic.lessonItem(course, it) }
    val downloadedCount = items.count { viewModel.isItemDownloaded(it) }
    val fullyDownloaded = CourseLogic.isCourseDownloaded(course) { viewModel.isItemDownloaded(it) }
    val anyDownloading = items.any { (it.id ?: "") in state.downloadingIds }

    val opened = openLesson
    if (opened != null) {
        if (opened.isPdf) {
            PdfViewerScreen(
                pdfFile = opened.file,
                title = opened.title,
                onBack = {
                    viewModel.closeItem(opened.item)
                    openLesson = null
                    onReaderFullscreenChange?.invoke(false)
                },
                onDeleteCorrupt = {
                    viewModel.deleteItem(opened.item)
                    openLesson = null
                    onReaderFullscreenChange?.invoke(false)
                },
                onFullscreenChange = onReaderFullscreenChange
            )
        } else {
            VideoPlayerScreen(
                opened.file, opened.title,
                onBack = {
                    viewModel.closeItem(opened.item)
                    openLesson = null
                    onReaderFullscreenChange?.invoke(false)
                },
                onFullscreenChange = onReaderFullscreenChange
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CoursesTopBar(title = course.title ?: "كورس", onBack = onBack)
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
            if (!canAccess) {
                item {
                    Text(
                        "هذا الكورس للمفعّلين — فعّل حسابك بكود التفعيل لتحميله ودراسته.",
                        fontSize = 13.sp, lineHeight = 19.sp, color = MaroonPrimary
                    )
                }
            } else if (!fullyDownloaded) {
                item {
                    Button(
                        onClick = {
                            items.forEach { item ->
                                if (!viewModel.isItemDownloaded(item) && (item.id ?: "") !in state.downloadingIds) {
                                    viewModel.downloadItem(item)
                                }
                            }
                        },
                        enabled = !anyDownloading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (anyDownloading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جارٍ تحميل الكورس… ($downloadedCount من ${items.size})")
                        } else {
                            Text(if (downloadedCount > 0) "إكمال تحميل الكورس ($downloadedCount من ${items.size})" else "تحميل الكورس كاملًا")
                        }
                    }
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الكورس محمّل بالكامل — يعمل بلا إنترنت", fontSize = 13.sp, color = MaroonPrimary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { items.forEach { viewModel.deleteItem(it) } }) {
                            Text("حذف الكورس", color = MaroonPrimary)
                        }
                    }
                }
            }
            items(lessons, key = { it.id ?: "" }) { lesson ->
                LessonRow(
                    lesson = lesson,
                    item = CourseLogic.lessonItem(course, lesson),
                    viewModel = viewModel,
                    canAccess = canAccess,
                    onOpen = { item, isPdf ->
                        scope.launch {
                            viewModel.openItem(item).fold(
                                onSuccess = { file -> openLesson = OpenLesson(file, item.title ?: "", isPdf, item) },
                                onFailure = { e -> openError = e.localizedMessage ?: "تعذر فتح الدرس" }
                            )
                        }
                    }
                )
            }
            if (openError != null) {
                item {
                    Text(openError ?: "", fontSize = 12.sp, color = MaroonPrimary)
                }
            }
        }
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
private fun LessonRow(
    lesson: CourseLesson,
    item: LibraryItem,
    viewModel: LibraryViewModel,
    canAccess: Boolean,
    onOpen: (LibraryItem, Boolean) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val isPdf = CourseLogic.isPdfLesson(lesson)
    val downloaded = viewModel.isItemDownloaded(item)
    val downloading = (item.id ?: "") in state.downloadingIds
    val progress = state.downloadingProgress[item.id ?: ""]
    val error = state.downloadErrors[item.id ?: ""]
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isPdf) Icons.Default.MenuBook else Icons.Default.PlayCircle,
                contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(lesson.title ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaroonPrimary)
                Text(
                    when {
                        downloaded -> "محمّل — يفتح بلا إنترنت"
                        downloading -> "جارٍ التحميل…"
                        else -> "غير محمّل"
                    },
                    fontSize = 11.sp, color = MaroonPrimary.copy(alpha = 0.7f)
                )
                if (error != null) {
                    Text(error, fontSize = 11.sp, color = MaroonPrimary)
                }
                if (downloading && progress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { progress }, color = GoldAccent, modifier = Modifier.fillMaxWidth())
                }
            }
            when {
                !canAccess -> Icon(Icons.Default.Lock, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(18.dp))
                downloading -> CircularProgressIndicator(color = MaroonPrimary, modifier = Modifier.size(20.dp))
                downloaded -> Button(
                    onClick = { onOpen(item, isPdf) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) { Text("فتح") }
                else -> Button(
                    onClick = { viewModel.downloadItem(item) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) { Text("تحميل") }
            }
        }
    }
}
