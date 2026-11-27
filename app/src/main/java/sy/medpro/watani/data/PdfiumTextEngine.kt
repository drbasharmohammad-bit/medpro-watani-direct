package sy.medpro.watani.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfTextPage
import io.legere.pdfiumandroid.api.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** One page's size in PDF points (1/72 inch), as the reader's
 * engines report it. Shared by the PDFium text engine (which
 * needs page sizes to convert FPDFText rects into fractions)
 * and the reader screen (which needs them for layout). */
data class PdfPagePoints(
    val widthPt: Int,
    val heightPt: Int
)

/**
 * PDF text layer on the PDFium engine (build 1.2.52).
 *
 * Since 1.2.52 the unified reader renders with PDFium
 * (PdfiumAndroidKt, Apache 2.0), and its text layer comes from
 * the SAME engine (FPDFText via PdfTextPage): the word geometry
 * the yellow highlighter draws is produced by the very engine
 * that rasterizes the page, so highlight coordinates can no
 * longer drift from the rendering the way the old split
 * (PdfRenderer for pixels, PDFBox for words) allowed.
 *
 * The output model is the existing [BookTextIndex] — words as
 * page fractions (y from the top) plus the document outline —
 * so search, highlighting and the outline UI consume it
 * unchanged, and the PDFBox engine ([PdfTextEngine]) remains a
 * drop-in fallback: [shouldFallbackToPdfBox] pins when the
 * reader falls back (PDFium produced nothing usable), and the
 * fallback's result wins whenever it has words.
 *
 * The disk cache mirrors [PdfTextEngine]'s contract — Moshi
 * JSON through [SectionCacheCrypto] (ciphertext at rest, in
 * the revocation wipe) — under its own directory
 * (pdf_text_index_pdfium), keyed by SHA-1 of the file's
 * absolute path + length + lastModified, so the two engines'
 * caches never alias and a replaced book re-extracts.
 *
 * Graceful degradation is the contract, exactly as with the
 * PDFBox engine: ANY failure (native library unavailable,
 * corrupt file, an odd producer) returns null and the reader
 * simply runs without its text features for this book.
 */
object PdfiumTextEngine {

    /**
     * Whether the reader must fall back to the PDFBox text
     * engine: the PDFium pass produced no index at all, or an
     * index without a single word (a scanned book legitimately
     * lands here too — the fallback then also finds no words
     * and the book honestly has no text layer).
     */
    fun shouldFallbackToPdfBox(index: BookTextIndex?): Boolean =
        index == null || index.pages.all { it.words.isEmpty() }

    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private fun cacheFile(context: Context, file: File): File {
        val raw = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, CACHE_DIR), "$digest.json")
    }

    /**
     * Loads the book's text index from the PDFium engine: the
     * encrypted disk cache when present, FPDFText extraction
     * from the already-open [document] (then cached) otherwise.
     * [pageSizes] are the document's page sizes in points, in
     * page order. Null on ANY failure — see the class contract.
     *
     * Threading: extraction walks the document page by page;
     * the caller runs this off the main thread (the library's
     * own global lock serializes its native calls against the
     * reader's renders).
     */
    suspend fun load(
        context: Context,
        file: File,
        document: PdfDocument,
        pageSizes: List<PdfPagePoints>
    ): BookTextIndex? =
        withContext(Dispatchers.IO) {
            try {
                val adapter = moshi.adapter(BookTextIndex::class.java)
                val cached = cacheFile(context, file)
                if (cached.isFile) {
                    try {
                        val text = SectionCacheCrypto.readText(context, cached)
                        if (text != null) {
                            adapter.fromJson(text)?.let { return@withContext it }
                        }
                    } catch (_: Exception) {
                        // Corrupt cache entry: fall through and re-extract.
                    }
                }
                val extracted = extract(document, pageSizes)
                try {
                    cached.parentFile?.mkdirs()
                    SectionCacheCrypto.writeText(context, cached, adapter.toJson(extracted))
                } catch (_: Exception) {
                    // An unwritable cache must not lose the extraction.
                }
                extracted
            } catch (_: Exception) {
                null
            }
        }

    /**
     * The FPDFText walk: every page's words via [extractPageWords]
     * plus the document outline. A page that fails on its own
     * contributes an empty word list (the book keeps its other
     * pages' text); the walk itself only fails wholesale when
     * the document is unusable, which the caller reads as
     * "fall back".
     */
    private fun extract(
        document: PdfDocument,
        pageSizes: List<PdfPagePoints>
    ): BookTextIndex {
        val pages = ArrayList<PageText>(pageSizes.size)
        for ((index, size) in pageSizes.withIndex()) {
            pages += PageText(
                page = index,
                words = extractPageWords(document, index, size)
            )
        }
        return BookTextIndex(pages = pages, outline = extractOutline(document))
    }

    /**
     * One page's words: open the page and its text page, read
     * the page string once, ask FPDFText for the rects of all
     * word ranges in ONE call (the per-character alternative is
     * a JNI call per glyph — a whole book of them), and fold
     * them into [WordBox]es with [PdfiumWordGrouper]. Every
     * handle is closed on every path, and ANY failure (a page
     * the engine chokes on, a native error) yields no words for
     * this page instead of failing the book.
     */
    private fun extractPageWords(
        document: PdfDocument,
        pageIndex: Int,
        size: PdfPagePoints
    ): List<WordBox> {
        if (size.widthPt <= 0 || size.heightPt <= 0) return emptyList()
        return runCatching { readPageWords(document, pageIndex, size) }
            .getOrDefault(emptyList())
    }

    /** Opens the page and its text page, reads the words, and
     * closes both handles on every path. */
    private fun readPageWords(
        document: PdfDocument,
        pageIndex: Int,
        size: PdfPagePoints
    ): List<WordBox> {
        val page = document.openPage(pageIndex) ?: return emptyList()
        try {
            val textPage = page.openTextPage()
            try {
                return wordsFromTextPage(textPage, size)
            } finally {
                runCatching { textPage.close() }
            }
        } finally {
            runCatching { page.close() }
        }
    }

    /** The words of one open text page: its string, its word
     * ranges, their rects, folded into page fractions. A page
     * with no characters or no word ranges has no words. */
    private fun wordsFromTextPage(
        textPage: PdfTextPage,
        size: PdfPagePoints
    ): List<WordBox> {
        val charCount = textPage.textPageCountChars()
        val text = if (charCount > 0) {
            textPage.textPageGetText(0, charCount).orEmpty()
        } else {
            ""
        }
        val ranges = PdfiumWordGrouper.wordRanges(text)
        return if (ranges.isEmpty()) {
            emptyList()
        } else {
            val flatRanges = IntArray(ranges.size * RANGE_FIELDS)
            ranges.forEachIndexed { i, range ->
                flatRanges[i * RANGE_FIELDS] = range.first
                flatRanges[i * RANGE_FIELDS + 1] = range.last - range.first + 1
            }
            val rects = textPage.textPageGetRectsForRanges(flatRanges)
                .orEmpty()
                .map { rangeRect ->
                    PdfiumWordGrouper.RangeRect(
                        start = rangeRect.rangeStart,
                        length = rangeRect.rangeLength,
                        left = rangeRect.rect.left,
                        top = rangeRect.rect.top,
                        right = rangeRect.rect.right,
                        bottom = rangeRect.rect.bottom
                    )
                }
            PdfiumWordGrouper.groupWords(
                text = text,
                rects = rects,
                pageWidthPt = size.widthPt.toFloat(),
                pageHeightPt = size.heightPt.toFloat()
            )
        }
    }

    /**
     * The document outline, flattened depth-first with levels —
     * the same shape the PDFBox engine produces. PDFium hands
     * back a bookmark tree; entries with a blank title are
     * skipped but their children are still walked, and a
     * broken outline yields whatever was collected (usually
     * the empty list) instead of failing the index.
     */
    private fun extractOutline(document: PdfDocument): List<OutlineItem> {
        val out = ArrayList<OutlineItem>()
        runCatching {
            flattenBookmarks(document.getTableOfContents(), 0, out)
        }
        return out
    }

    private fun flattenBookmarks(
        bookmarks: List<Bookmark>,
        level: Int,
        out: MutableList<OutlineItem>
    ) {
        for (bookmark in bookmarks) {
            val title = bookmark.title.trim()
            if (title.isNotEmpty()) {
                out += OutlineItem(
                    title = title,
                    page = bookmark.pageIdx.toInt().coerceAtLeast(0),
                    level = level
                )
            }
            if (bookmark.children.isNotEmpty()) {
                flattenBookmarks(bookmark.children, level + 1, out)
            }
        }
    }

    /** This engine's own encrypted-cache directory (see the
     * class contract): never shared with the PDFBox engine's,
     * so the two engines' indexes cannot alias. It is
     * registered in RestrictedInventory's revocation wipe
     * beside the PDFBox directory (same content class: the
     * full text of restricted books). */
    private const val CACHE_DIR = "pdf_text_index_pdfium"

    /** (start, length) pairs in the flat range array FPDFText
     * consumes. */
    private const val RANGE_FIELDS = 2
}
