package sy.medpro.watani.data

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.StringWriter
import java.security.MessageDigest

/**
 * PDF text layer for the internal reader (build 1.2.37).
 *
 * Rendering stays on `android.graphics.pdf.PdfRenderer`; this engine
 * is TEXT ONLY. PDFBox Android extracts, per page, every word with its
 * bounding box as page fractions (0..1, y measured from the TOP of the
 * page — the same normalized space the reader's freehand strokes use),
 * plus the document outline. The result is cached on disk as Moshi
 * JSON under filesDir/pdf_text_index/, keyed by a SHA-1 of the file's
 * absolute path + length + lastModified, so a replaced book file
 * re-extracts and an untouched one never does.
 *
 * Graceful degradation is the contract: ANY failure (corrupt file,
 * encrypted file, a PDFBox quirk on an odd producer) returns null and
 * the reader simply runs without its text features — search, text
 * highlighting and the outline stay silently absent.
 */

/** One extracted word and its box as page fractions, y from the top. */
@JsonClass(generateAdapter = true)
data class WordBox(
    @Json(name = "text") val text: String,
    @Json(name = "x0") val x0: Float,
    @Json(name = "y0") val y0: Float,
    @Json(name = "x1") val x1: Float,
    @Json(name = "y1") val y1: Float
)

/** All words of one page, in reading order (stripper position sort). */
@JsonClass(generateAdapter = true)
data class PageText(
    @Json(name = "page") val page: Int,
    @Json(name = "words") val words: List<WordBox>
)

/** One outline (bookmarks) entry of the PDF itself. */
@JsonClass(generateAdapter = true)
data class OutlineItem(
    @Json(name = "title") val title: String,
    @Json(name = "page") val page: Int,
    @Json(name = "level") val level: Int
)

/** The whole text index of one book: pages + outline. */
@JsonClass(generateAdapter = true)
data class BookTextIndex(
    @Json(name = "pages") val pages: List<PageText>,
    @Json(name = "outline") val outline: List<OutlineItem>
)

/**
 * One search hit: the words [wordStart]..[wordEnd] (inclusive) of
 * [page] cover the matched phrase.
 */
data class SearchHit(
    val page: Int,
    val wordStart: Int,
    val wordEnd: Int
)

object PdfTextEngine {

    // PDFBoxResourceLoader.init must run exactly once per process.
    @Volatile
    private var resourceLoaderReady = false

    private fun ensureResourceLoader(context: Context) {
        if (resourceLoaderReady) return
        synchronized(PdfTextEngine::class.java) {
            if (!resourceLoaderReady) {
                PDFBoxResourceLoader.init(context.applicationContext)
                resourceLoaderReady = true
            }
        }
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private fun cacheFile(context: Context, file: File): File {
        val raw = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "pdf_text_index"), "$digest.json")
    }

    /**
     * Loads the book's text index: disk cache when present, PDFBox
     * extraction (then cached) otherwise. Null on ANY failure — the
     * caller treats null as "this book has no text layer features".
     *
     * Audit fix (21 Nov 2026, SEC-S1): the index is the book's FULL
     * text (every word, with positions) and was cached as PLAINTEXT
     * JSON in filesDir/pdf_text_index — for library books that had
     * been decrypted only in memory, the index silently undid the
     * encryption at rest, survived temp cleanup, and was missing
     * from the revocation wipe. The cache now goes through
     * [SectionCacheCrypto] (the app's central encrypted store:
     * AES-GCM via ContentEncryption): writes are ciphertext, reads
     * decrypt, and a legacy plaintext index is migrated to
     * ciphertext on its first read. The directory is also in the
     * revocation wipe (RestrictedInventory).
     */
    suspend fun load(context: Context, file: File): BookTextIndex? =
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
                ensureResourceLoader(context)
                val extracted = extract(file) ?: return@withContext null
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

    private fun extract(file: File): BookTextIndex? {
        var document: PDDocument? = null
        return try {
            document = PDDocument.load(file)
            val stripper = WordCollectingStripper()
            stripper.setSortByPosition(true)
            val pages = ArrayList<PageText>(document.numberOfPages)
            for (i in 0 until document.numberOfPages) {
                val page = document.getPage(i)
                val crop = page.cropBox
                stripper.beginPage(crop.width, crop.height)
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                // writeText drives the writeString callbacks for this
                // one page; the produced string itself is discarded.
                stripper.writeText(document, StringWriter())
                pages += PageText(page = i, words = stripper.drainPageWords())
            }
            BookTextIndex(pages = pages, outline = collectOutline(document))
        } catch (_: Exception) {
            null
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Recursive walk of the document outline. Every element is guarded
     * on its own: one broken entry (no destination, a destination that
     * resolves to no page, a producer's malformed node) is skipped —
     * with its subtree still walked — instead of losing the outline.
     */
    private fun collectOutline(document: PDDocument): List<OutlineItem> {
        val out = ArrayList<OutlineItem>()
        try {
            val root = document.documentCatalog.documentOutline ?: return emptyList()
            collectOutlineInto(root, 0, out)
        } catch (_: Exception) {
            return out
        }
        return out
    }

    private fun collectOutlineInto(node: PDOutlineNode, level: Int, out: MutableList<OutlineItem>) {
        val children = try {
            node.children().toList()
        } catch (_: Exception) {
            emptyList()
        }
        for (child in children) {
            try {
                val page = resolveOutlinePage(child)
                val title = child.title?.trim().orEmpty()
                if (page != null && page >= 0 && title.isNotEmpty()) {
                    out += OutlineItem(title = title, page = page, level = level)
                }
            } catch (_: Exception) {
                // Skip this element only; siblings and children remain.
            }
            collectOutlineInto(child, level + 1, out)
        }
    }

    private fun resolveOutlinePage(item: PDOutlineItem): Int? {
        val destination = try {
            item.destination ?: (item.action as? PDActionGoTo)?.destination
        } catch (_: Exception) {
            null
        }
        val pageDestination = destination as? PDPageDestination ?: return null
        return try {
            pageDestination.retrievePageNumber()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * A PDFTextStripper that never builds the output string: it
     * captures every glyph's TextPosition via writeString and groups
     * glyphs into words at whitespace. A word's box is the union of
     * its glyphs' boxes, divided by the page's cropBox size so the
     * result is in page fractions with y from the top (the DirAdj
     * coordinates PDFBox reports once sortByPosition is on are
     * already top-origin display coordinates).
     */
    private class WordCollectingStripper : PDFTextStripper() {
        private var pageWidth = 0f
        private var pageHeight = 0f
        private val words = ArrayList<WordBox>()
        private val current = StringBuilder()
        private var ux0 = 0f
        private var uy0 = 0f
        private var ux1 = 0f
        private var uy1 = 0f
        private var hasGlyph = false

        fun beginPage(width: Float, height: Float) {
            pageWidth = width
            pageHeight = height
            words.clear()
            current.setLength(0)
            hasGlyph = false
        }

        fun drainPageWords(): List<WordBox> {
            flushWord()
            return ArrayList(words)
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (textPositions == null) return
            for (position in textPositions) {
                val unicode = try {
                    position.unicode
                } catch (_: Exception) {
                    null
                } ?: continue
                for (ch in unicode) {
                    if (ch.isWhitespace()) {
                        flushWord()
                    } else {
                        appendGlyph(ch, position)
                    }
                }
            }
        }

        private fun appendGlyph(ch: Char, position: TextPosition) {
            if (pageWidth <= 0f || pageHeight <= 0f) return
            val x0 = position.xDirAdj / pageWidth
            val x1 = (position.xDirAdj + position.widthDirAdj) / pageWidth
            val y0 = position.yDirAdj / pageHeight
            val y1 = (position.yDirAdj + position.heightDir) / pageHeight
            if (!hasGlyph) {
                ux0 = x0; uy0 = y0; ux1 = x1; uy1 = y1
                hasGlyph = true
            } else {
                ux0 = minOf(ux0, x0); uy0 = minOf(uy0, y0)
                ux1 = maxOf(ux1, x1); uy1 = maxOf(uy1, y1)
            }
            current.append(ch)
        }

        private fun flushWord() {
            if (hasGlyph && current.isNotEmpty()) {
                words += WordBox(
                    text = current.toString(),
                    x0 = ux0.coerceIn(0f, 1f),
                    y0 = uy0.coerceIn(0f, 1f),
                    x1 = ux1.coerceIn(0f, 1f),
                    y1 = uy1.coerceIn(0f, 1f)
                )
            }
            current.setLength(0)
            hasGlyph = false
        }
    }

    /**
     * Search normalization (pure): strips Arabic diacritics
     * (U+064B..U+0652 and U+0670) and the tatweel (U+0640), folds
     * آ/أ/إ to ا, ة to ه, ى and ئ to ي, ؤ to و, maps Eastern Arabic
     * digits ٠-٩ to 0-9, lowercases Latin letters and collapses every
     * whitespace run to a single space (trimmed). The same folding is
     * applied to the query and to the indexed words, so a student
     * finds text regardless of how either side was vocalized.
     */
    fun normalize(s: String): String {
        val out = StringBuilder(s.length)
        var pendingSpace = false
        for (raw in s) {
            val code = raw.code
            if (code in 0x064B..0x0652 || code == 0x0670 || code == 0x0640) {
                continue // diacritic or tatweel: dropped entirely
            }
            val ch = when (raw) {
                'آ', 'أ', 'إ' -> 'ا'
                'ة' -> 'ه'
                'ى', 'ئ' -> 'ي'
                'ؤ' -> 'و'
                in '٠'..'٩' -> '0' + (code - 0x0660)
                else -> raw.lowercaseChar()
            }
            if (ch.isWhitespace()) {
                pendingSpace = out.isNotEmpty()
            } else {
                if (pendingSpace) {
                    out.append(' ')
                    pendingSpace = false
                }
                out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * Phrase search over the index (pure). Per page, the normalized
     * words are joined with single spaces while an offset table maps
     * every character range back to word indices; every occurrence of
     * the normalized query as a substring (overlaps included) becomes
     * one [SearchHit] whose word range covers the whole match, even
     * when the match starts or ends inside a word. Pages are searched
     * independently — a phrase split across a page break never hits.
     * A query that normalizes to empty yields no hits.
     */
    fun search(index: BookTextIndex, query: String): List<SearchHit> {
        val needle = normalize(query)
        if (needle.isEmpty()) return emptyList()
        val hits = ArrayList<SearchHit>()
        for (page in index.pages) {
            val words = page.words
            if (words.isEmpty()) continue
            val starts = IntArray(words.size)
            val ends = IntArray(words.size)
            val joined = StringBuilder()
            for (i in words.indices) {
                if (i > 0) joined.append(' ')
                starts[i] = joined.length
                joined.append(normalize(words[i].text))
                ends[i] = joined.length
            }
            val text = joined.toString()
            var from = 0
            while (from <= text.length - needle.length) {
                val at = text.indexOf(needle, from)
                if (at < 0) break
                val matchEnd = at + needle.length // exclusive
                val wordStart = (words.indices).firstOrNull { ends[it] > at } ?: -1
                val wordEnd = (words.indices).lastOrNull { starts[it] < matchEnd } ?: -1
                if (wordStart >= 0 && wordEnd >= wordStart) {
                    hits += SearchHit(page = page.page, wordStart = wordStart, wordEnd = wordEnd)
                }
                from = at + 1
            }
        }
        return hits
    }
}
