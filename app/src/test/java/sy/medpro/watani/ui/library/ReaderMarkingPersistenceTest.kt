package sy.medpro.watani.ui.library

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import sy.medpro.watani.data.WordBox

/**
 * Gate for the Crashlytics c3b30976 fix (build 1.2.37): the reader's
 * marking DTOs (freehand strokes, annotations, text highlights) are
 * persisted as JSON in SharedPreferences by PdfViewerScreen. On the
 * 1.2.36 release build their Moshi adapters could not be created at
 * runtime (R8 had obfuscated the un-kept ui.library DTOs), and the
 * exception escaped composition and crashed every book open.
 *
 * These tests pin the JSON round-trip of all three marking types
 * through the exact Moshi setup the reader uses, plus the typed
 * List<Int> bookmark adapter that replaced the old raw
 * adapter(List::class.java). The release-side proof is layered:
 * @JsonClass(generateAdapter = true) on the DTOs, explicit ProGuard
 * keeps for the DTOs and their generated adapters in
 * app/proguard-rules.pro, runCatching-armored adapter creation in
 * the reader, and a dex name check on the shipped binary recorded
 * in the 1.2.37 build log.
 */
class ReaderMarkingPersistenceTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Test
    fun highlightStrokesRoundTrip() {
        val adapter = moshi.adapter<List<HighlightStrokeDto>>(
            Types.newParameterizedType(List::class.java, HighlightStrokeDto::class.java)
        )
        val strokes = listOf(
            HighlightStrokeDto(page = 2, points = listOf(0.1f, 0.2f, 0.3f, 0.4f), color = -769226, width = 0.012f),
            HighlightStrokeDto(page = 5, points = listOf(0.5f, 0.5f), color = -769226, width = 0.02f)
        )
        assertEquals(strokes, adapter.fromJson(adapter.toJson(strokes)))
    }

    @Test
    fun annotationsRoundTrip() {
        val adapter = moshi.adapter<List<PdfAnnotationDto>>(
            Types.newParameterizedType(List::class.java, PdfAnnotationDto::class.java)
        )
        val annotations = listOf(
            PdfAnnotationDto(
                page = 1, type = "TEXT",
                x1 = 0.25f, y1 = 0.4f, x2 = 0f, y2 = 0f,
                text = "ملاحظة مهمة", color = -16777216
            ),
            PdfAnnotationDto(
                page = 3, type = "RECT",
                x1 = 0.1f, y1 = 0.1f, x2 = 0.6f, y2 = 0.3f,
                text = "", color = -65536
            )
        )
        assertEquals(annotations, adapter.fromJson(adapter.toJson(annotations)))
    }

    @Test
    fun textHighlightsRoundTrip() {
        val adapter = moshi.adapter<List<TextHighlightDto>>(
            Types.newParameterizedType(List::class.java, TextHighlightDto::class.java)
        )
        val highlights = listOf(
            TextHighlightDto(
                page = 4,
                rects = listOf(
                    WordBox(text = "heart", x0 = 0.1f, y0 = 0.2f, x1 = 0.3f, y1 = 0.24f),
                    WordBox(text = "failure", x0 = 0.32f, y0 = 0.2f, x1 = 0.55f, y1 = 0.24f)
                ),
                text = "heart failure"
            )
        )
        assertEquals(highlights, adapter.fromJson(adapter.toJson(highlights)))
    }

    @Test
    fun bookmarksTypedAdapterRoundTripAndLegacyFormat() {
        // The bookmarks adapter is built WITHOUT the Kotlin reflective
        // factory in the reader (plain Moshi + typed List<Int>).
        val adapter = Moshi.Builder().build().adapter<List<Int>>(
            Types.newParameterizedType(List::class.java, Integer::class.java)
        )
        val pages = listOf(0, 3, 17, 42)
        assertEquals(pages, adapter.fromJson(adapter.toJson(pages)))
        // Legacy payloads written by the old raw-List adapter are the
        // same JSON array of numbers and must still load.
        assertEquals(pages, adapter.fromJson("[0,3,17,42]"))
    }

    @Test
    fun emptyMarkingsRoundTrip() {
        val strokeAdapter = moshi.adapter<List<HighlightStrokeDto>>(
            Types.newParameterizedType(List::class.java, HighlightStrokeDto::class.java)
        )
        assertEquals(emptyList<HighlightStrokeDto>(), strokeAdapter.fromJson("[]"))
        assertEquals("[]", strokeAdapter.toJson(emptyList()))
    }
}
