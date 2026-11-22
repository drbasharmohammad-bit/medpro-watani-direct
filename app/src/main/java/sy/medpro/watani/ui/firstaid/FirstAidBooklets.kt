package sy.medpro.watani.ui.firstaid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sy.medpro.watani.data.FirstAidBookletDto
import sy.medpro.watani.data.FirstAidIndexDto
import sy.medpro.watani.data.WataniRepository
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily
import java.io.File

/**
 * Booklet helpers (merge phase 5): a booklet is «متاح دائمًا» — its
 * button shows regardless of progress; the PDF downloads on FIRST
 * open only (repository helper, library pattern) and then opens in
 * the existing PdfViewerScreen.
 *
 * System matching: Step-2 booklet files embed the system code
 * ("FirstAid-Step2-CARD-Booklet.pdf"); Step-1 Mobile booklet files
 * embed the system's English name ("FirstAid-Biochemistry-Booklet-
 * Mobile.pdf"). Matching is a normalized containment check; when no
 * booklet matches a system, that system simply shows no PDF chip —
 * the full booklet catalog still lists every booklet on the path and
 * section screens.
 */
fun bookletForSystem(index: FirstAidIndexDto, systemCode: String?, sectionEn: String?): FirstAidBookletDto? {
    val booklets = index.booklets.orEmpty()
    fun norm(s: String) = s.lowercase().replace(" ", "").replace("-", "").replace("_", "")
    val code = systemCode?.takeIf { it.isNotBlank() }
    if (code != null) {
        booklets.firstOrNull { b ->
            val f = b.file.orEmpty().substringAfterLast('/')
            f.contains("-$code-", ignoreCase = true) || f.contains("_$code.", ignoreCase = true)
        }?.let { return it }
    }
    val en = sectionEn?.takeIf { it.isNotBlank() } ?: return null
    val enNorm = norm(en)
    return booklets.firstOrNull { b -> norm(b.file.orEmpty()).contains(enNorm) }
}

/** Download/open state holder for booklets, shared by every screen
 * that offers a booklet button. */
class FirstAidBookletOpener(
    private val repo: WataniRepository,
    private val onOpenPdf: (File, String) -> Unit
) {
    var progress by mutableStateOf<Float?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun open(booklet: FirstAidBookletDto) {
        val file = booklet.file?.takeIf { it.isNotBlank() } ?: return
        val relativePath = "firstaid/$file"
        val expected = booklet.size ?: 0L
        val local = repo.getFirstAidBookletFile(relativePath)
        if (local.exists() && local.length() > 0 && (expected <= 0 || local.length() == expected)) {
            onOpenPdf(local, file.substringAfterLast('/'))
            return
        }
        progress = 0f
        val result = repo.downloadFirstAidBooklet(relativePath, expected) { p -> progress = p }
        progress = null
        result.fold(
            onSuccess = { onOpenPdf(it, file.substringAfterLast('/')) },
            onFailure = { error = it.message ?: "تعذّر تحميل الكتيّب" }
        )
    }

    fun dismissError() { error = null }

    /** Fire-and-forget open for click handlers. */
    fun openIn(scope: kotlinx.coroutines.CoroutineScope, booklet: FirstAidBookletDto) {
        scope.launch { open(booklet) }
    }
}

@Composable
fun rememberBookletOpener(
    repo: WataniRepository,
    onOpenPdf: (File, String) -> Unit
): FirstAidBookletOpener {
    val opener = remember(repo) { FirstAidBookletOpener(repo, onOpenPdf) }
    opener.RenderDialogs()
    return opener
}

@Composable
fun FirstAidBookletOpener.RenderDialogs() {
    val p = progress
    if (p != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("جارٍ تحميل الكتيّب", fontFamily = NotoSansArabicFontFamily) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator()
                    Text(
                        "${(p * 100).toInt()}%",
                        fontFamily = NotoSansArabicFontFamily, fontSize = 13.sp
                    )
                }
            },
            confirmButton = {}
        )
    }
    val e = error
    if (e != null) {
        AlertDialog(
            onDismissRequest = { dismissError() },
            title = { Text("الكتيّب", fontFamily = NotoSansArabicFontFamily) },
            text = {
                Text(e, fontFamily = NotoSansArabicFontFamily, fontSize = 13.5.sp)
            },
            confirmButton = {
                TextButton(onClick = { dismissError() }) {
                    Text("حسنًا", fontFamily = NotoSansArabicFontFamily)
                }
            }
        )
    }
}

@Composable
fun BookletRow(
    step: Int,
    booklet: FirstAidBookletDto,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { WataniRepository(context) }
    val fileName = booklet.file?.substringAfterLast('/').orEmpty()
    val local = remember(booklet.file) {
        repo.getFirstAidBookletFile("firstaid/${booklet.file.orEmpty()}")
    }
    val downloaded = local.exists() && local.length() > 0
    val sizeMb = (booklet.size ?: 0L) / 1_000_000.0
    FaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FaChip("PDF", faGoldTint(), faGoldText())
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fileName.removeSuffix(".pdf"),
                    fontFamily = NotoSansArabicFontFamily, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 13.sp, color = faHeading()
                )
                Text(
                    "Step $step • ${"%.1f".format(sizeMb)} MB • ${if (downloaded) "منزّل — يفتح فورًا" else "يُنزَّل عند أول فتح"}",
                    fontFamily = NotoSansArabicFontFamily, fontSize = 11.3.sp, color = faMuted()
                )
            }
        }
    }
}
