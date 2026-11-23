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
import sy.medpro.watani.data.FirstAidSystemDto
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

/**
 * The display title of a booklet (build 1.2.47 — the doctor's
 * device report: the reader showed the RAW FILE NAME, truncated
 * mid-word, as the book's title). The index carries no title
 * field for booklets (file + size only), so the title is derived
 * from the booklet's own system — matched with the same rules as
 * [bookletForSystem] — and reads «كتيّب <system in Arabic>», like
 * the catalog titles the library's booklets carry. A booklet no
 * system claims falls back to a HUMANIZED file name
 * ("FirstAid-Abbreviations-Booklet-Mobile.pdf" → "Abbreviations")
 * — never the raw name, never an extension.
 */
fun bookletDisplayTitle(booklet: FirstAidBookletDto, systems: List<FirstAidSystemDto>): String {
    val file = booklet.file.orEmpty()
    val base = file.substringAfterLast('/')
    // Letters/digits only: "Hematology & Oncology" and the file's
    // "HematologyOncology" must meet, and a file core that is the
    // head of a longer system name ("Neurology" inside "Neurology
    // & Special Senses", "PublicHealth" inside "Public Health
    // Sciences") must match it — exact containment alone missed
    // five of the thirty-four live booklets.
    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    val core = norm(humanizedBookletFileTitle(base))
    val matched = systems.firstOrNull { system ->
        val code = system.code?.takeIf { it.isNotBlank() }
        val byCode = code != null &&
            (base.contains("-$code-", ignoreCase = true) || base.contains("_$code.", ignoreCase = true))
        val enNorm = norm(system.sectionEn.orEmpty())
        val byName = enNorm.isNotEmpty() && core.isNotEmpty() &&
            (norm(file).contains(enNorm) || enNorm.startsWith(core) || core.startsWith(enNorm))
        byCode || byName
    }
    val arabic = matched?.sectionAr?.takeIf { it.isNotBlank() }
    if (arabic != null) return "كتيّب $arabic"
    return humanizedBookletFileTitle(base)
}

/** Humanized fallback title from a booklet file name: strips the
 * First Aid prefixes/suffixes and the extension, spaces the words
 * (dashes and CamelCase boundaries alike). */
fun humanizedBookletFileTitle(fileName: String): String {
    var name = fileName.substringAfterLast('/').removeSuffix(".pdf")
    name = name.removePrefix("FirstAid-").removePrefix("FirstAid_")
    name = name.removePrefix("Step2-").removePrefix("Step1-")
    name = name.removeSuffix("-Booklet-Mobile").removeSuffix("-Booklet")
    name = name.replace('-', ' ').replace('_', ' ')
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        .replace(Regex("\\s+"), " ").trim()
    return name.ifBlank { "كتيّب First Aid" }
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

    /** The systems of the host's loaded indexes, used only to title
     * booklets ([bookletDisplayTitle]). Hosts assign it whenever
     * their indexes land; empty simply means the humanized fallback. */
    var systems: List<FirstAidSystemDto> = emptyList()

    suspend fun open(booklet: FirstAidBookletDto) {
        val file = booklet.file?.takeIf { it.isNotBlank() } ?: return
        val relativePath = "firstaid/$file"
        val expected = booklet.size ?: 0L
        // 1.2.47: the reader's title is the booklet's display title,
        // never the raw file name (the doctor's screenshot showed
        // «FirstAi…iochemistry-Bookl…» over the biochemistry book).
        val title = bookletDisplayTitle(booklet, systems)
        val local = repo.getFirstAidBookletFile(relativePath)
        if (local.exists() && local.length() > 0 && (expected <= 0 || local.length() == expected)) {
            onOpenPdf(local, title)
            return
        }
        progress = 0f
        val result = repo.downloadFirstAidBooklet(relativePath, expected) { p -> progress = p }
        progress = null
        result.fold(
            onSuccess = { onOpenPdf(it, title) },
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
    title: String? = null,
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
                    // 1.2.47: the row shows the same display title
                    // the reader will carry — never the raw file name.
                    title ?: humanizedBookletFileTitle(fileName),
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
