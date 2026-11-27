package sy.medpro.watani.data

import android.content.Context
import io.legere.pdfiumandroid.PdfDocument
import java.io.File

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
    fun shouldFallbackToPdfBox(index: BookTextIndex?): Boolean {
        // Stub (red-first commit of 1.2.52): implemented by the
        // build commit that follows.
        return false
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
    ): BookTextIndex? {
        // Stub (red-first commit of 1.2.52): implemented by the
        // build commit that follows.
        return null
    }
}
