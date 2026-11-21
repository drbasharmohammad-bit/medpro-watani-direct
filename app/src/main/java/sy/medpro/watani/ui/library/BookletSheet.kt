package sy.medpro.watani.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.BookletCatalog
import sy.medpro.watani.data.BookletDlState
import sy.medpro.watani.data.BookletDownloadManager
import sy.medpro.watani.data.BookletProgress
import sy.medpro.watani.data.LibraryItem
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily
import java.io.File

/** What the booklet sheet is showing: one video's linked booklet. */
data class BookletSheetRequest(
    val video: LibraryItem,
    val subjectName: String?,
    val bookletPath: String
)

/**
 * The booklet bottom sheet (approved model 03-booklet-sheet,
 * build 1.2.37): chapter title + chips, the linked video, and a
 * state block driven by [BookletDownloadManager.states] —
 * not downloaded (download button + the offline note), downloading
 * (spinner, % bar, cancel; «فتح الكتيّب» disabled), downloaded
 * (open + delete; reading progress from [BookletProgress] when it
 * exists), failed (reason + retry).
 *
 * Auto-open rule: a download STARTED FROM THIS SHEET opens the
 * reader by itself the moment it completes (the design's «يُفتح
 * فور اكتمال التنزيل»); a download that was already running when
 * the sheet opened does not hijack the sheet on completion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookletSheet(
    request: BookletSheetRequest,
    onDismiss: () -> Unit,
    onOpenBooklet: (file: File, title: String, path: String) -> Unit
) {
    val context = LocalContext.current
    val path = request.bookletPath
    val meta = remember(path) { BookletCatalog.meta(context, path) }
    val chapterTitle = meta?.title?.takeIf { it.isNotBlank() }
        ?: request.video.title?.takeIf { it.isNotBlank() }
        ?: "كتيّب القسم"
    val chapter = meta?.chapter ?: BookletCatalog.chapterForVideo(context, request.video.id)
    val subjectName = request.subjectName?.takeIf { it.isNotBlank() }

    val dlStates by BookletDownloadManager.states.collectAsState()
    val dlState = dlStates[path]
        ?: if (BookletDownloadManager.isDownloaded(context, path)) {
            BookletDlState.Downloaded
        } else {
            BookletDlState.NotDownloaded
        }
    val readingFractions by BookletProgress.flow.collectAsState()
    val readingSnapshot = remember(path, dlState, readingFractions) {
        if (dlState is BookletDlState.Downloaded) {
            BookletProgress.snapshot(context, path)
        } else {
            null
        }
    }
    val readingFraction = readingFractions[path]
        ?: if (dlState is BookletDlState.Downloaded) {
            BookletProgress.fraction(context, path)
        } else {
            0f
        }

    // Auto-open on completion of a download started from this sheet.
    var startedHere by remember(path) { mutableStateOf(false) }
    LaunchedEffect(dlState) {
        if (startedHere && dlState is BookletDlState.Downloaded) {
            startedHere = false
            val file = BookletDownloadManager.localFile(context, path)
            if (file.exists()) onOpenBooklet(file, chapterTitle, path)
        }
    }

    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = libSurface(),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFFE7DACB))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 30.dp)
        ) {
            // Header: 58dp book tile + «كتيّب القسم» + chapter title + chips.
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(LibColors.GoldTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MenuBook, contentDescription = null,
                        tint = LibColors.GoldText, modifier = Modifier.size(28.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "كتيّب القسم",
                        fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = LibColors.GoldText
                    )
                    Text(
                        chapterTitle,
                        fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 18.5.sp, color = libHeading(), lineHeight = 26.sp
                    )
                    Row(
                        modifier = Modifier.padding(top = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        val pages = meta?.pages
                        if (pages != null && pages > 0) {
                            LibChip("$pages صفحة", pdfStyle = true)
                        }
                        if (subjectName != null) {
                            LibChip("من كتاب $subjectName", pdfStyle = false)
                        }
                        LibChip("PDF", pdfStyle = false)
                    }
                }
            }

            // Linked video row (.vidlink).
            Row(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (androidx.compose.foundation.isSystemInDarkTheme()) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            LibColors.VidLinkBg
                        }
                    )
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(LibColors.Maroon),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(15.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "الفيديو المرتبط: ${request.video.title ?: "بدون عنوان"}",
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 13.sp, color = libInk()
                    )
                    if (chapter != null) {
                        Text(
                            if (subjectName != null) {
                                "الفصل $chapter من كتاب $subjectName"
                            } else {
                                "الفصل $chapter"
                            },
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 12.sp, color = libMuted()
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            when (val s = dlState) {
                is BookletDlState.Downloading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = LibColors.Gold, strokeWidth = 2.5.dp
                            )
                            Text(
                                "جارٍ التنزيل…",
                                fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp, color = LibColors.Maroon
                            )
                        }
                        Text(
                            "يُفتح فور اكتمال التنزيل",
                            fontFamily = NotoSansArabicFontFamily,
                            fontSize = 12.sp, color = libMuted()
                        )
                    }
                    LibGoldBar(
                        s.progress, height = 8.dp,
                        modifier = Modifier.padding(top = 9.dp)
                    )
                    Text(
                        "${(s.progress.coerceIn(0f, 1f) * 100).toInt()}٪",
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 12.sp, color = libMuted(),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    BookletOfflineNote()
                    Row(
                        modifier = Modifier.padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        LibPrimaryButton(
                            "فتح الكتيّب", enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                        LibGhostButton(
                            "إلغاء التنزيل",
                            modifier = Modifier.weight(1f)
                        ) {
                            startedHere = false
                            BookletDownloadManager.cancel(path)
                        }
                    }
                }
                BookletDlState.Downloaded -> {
                    if (readingSnapshot != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "تقدّم القراءة",
                                fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp, color = LibColors.Maroon
                            )
                            Text(
                                "صفحة ${readingSnapshot.first} من ${readingSnapshot.second}",
                                fontFamily = NotoSansArabicFontFamily,
                                fontSize = 12.sp, color = libMuted()
                            )
                        }
                        LibGoldBar(
                            readingFraction, height = 8.dp,
                            modifier = Modifier.padding(top = 9.dp)
                        )
                    } else {
                        BookletOfflineNote()
                    }
                    Row(
                        modifier = Modifier.padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        LibPrimaryButton("فتح الكتيّب", modifier = Modifier.weight(1f)) {
                            val file = BookletDownloadManager.localFile(context, path)
                            if (file.exists()) {
                                onOpenBooklet(file, chapterTitle, path)
                            } else {
                                BookletDownloadManager.refresh(context, listOf(path))
                            }
                        }
                        LibGhostButton("حذف من الجهاز", modifier = Modifier.weight(1f)) {
                            BookletDownloadManager.delete(context, path)
                        }
                    }
                }
                is BookletDlState.Failed -> {
                    Text(
                        s.message,
                        fontFamily = NotoSansArabicFontFamily,
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.error
                    )
                    Row(modifier = Modifier.padding(top = 20.dp)) {
                        LibPrimaryButton("إعادة المحاولة", modifier = Modifier.weight(1f)) {
                            startedHere = true
                            BookletDownloadManager.startDownload(context, path)
                        }
                    }
                }
                BookletDlState.NotDownloaded -> {
                    BookletOfflineNote()
                    Row(modifier = Modifier.padding(top = 20.dp)) {
                        LibPrimaryButton("تنزيل الكتيّب", modifier = Modifier.weight(1f)) {
                            startedHere = true
                            BookletDownloadManager.startDownload(context, path)
                        }
                    }
                }
            }
            // Keep the sheet content clear of the navigation bar.
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The design's offline note (.prog .note), verbatim from model 03. */
@Composable
private fun BookletOfflineNote() {
    Text(
        "بعد التنزيل يُحفظ الكتيّب على جهازك ويعمل بلا إنترنت، مثل الفيديوهات تمامًا.",
        fontFamily = NotoSansArabicFontFamily,
        fontSize = 12.sp, color = libMuted(),
        modifier = Modifier.padding(top = 9.dp)
    )
}
