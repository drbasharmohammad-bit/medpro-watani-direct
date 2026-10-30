package sy.medpro.watani.data

import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1 — pins the gateway URL chain.
 *
 * The Worker gateway REJECTS %2F before decoding (encoded-slash
 * smuggling guard), so the `path` query value MUST carry literal '/'
 * separators with only each segment percent-encoded. OkHttp's
 * `addQueryParameter("path", rel)` encodes '/' as %2F and would get
 * every restricted path (all contain '/') rejected 400 — which is why
 * [WorkerActivationApi.buildGatewayUrl] builds the query manually.
 *
 * Only pure functions are tested here (no Android APIs, no Context),
 * so this test is JVM-safe.
 *
 * HONEST STATUS: this test was written in this fix batch but NOT RUN
 * locally — no JVM exists in this environment (java: command not found).
 * It runs in CI (GitHub Actions) with the main-branch build; do not
 * claim a local run.
 */
class WorkerActivationApiUrlTest {

    @Test
    fun gatewayUrl_keepsLiteralSlashes_noEncodedSlash() {
        val url = WorkerActivationApi.buildGatewayUrl("questions/foo.json")
        assertEquals(
            "https://watani-api.drbasharmohammad.workers.dev/v1/content?path=questions/foo.json",
            url
        )
        assertFalse("gateway URL must not contain %2F", url.contains("%2F"))
    }

    @Test
    fun gatewayUrl_encodesSegmentsButNotSeparators() {
        val url = WorkerActivationApi.buildGatewayUrl("dawrat_jami3at/damascus/أذنية.json")
        // Separators stay literal so the gateway allow-list sees segments.
        assertTrue(url.contains("dawrat_jami3at/damascus/"))
        assertFalse(url.contains("%2F"))
        // Non-ASCII inside a segment IS percent-encoded.
        assertFalse(url.contains("أذنية"))
        // A space inside a segment becomes %20, not '+'.
        val spaced = WorkerActivationApi.buildGatewayUrl("books/my book.pdf")
        assertTrue(spaced.contains("my%20book.pdf"))
        assertFalse(spaced.contains("my+book.pdf"))
    }

    @Test
    fun okHttpQueryParameter_wouldEncodeSlash_documentingWhyManualBuild() {
        // This is the rejected form: it proves the old builder was broken.
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("example.com")
            .addPathSegments("v1/content")
            .addQueryParameter("path", "questions/foo.json")
            .build()
        assertTrue(
            "OkHttp encodes '/' in query values as %2F",
            url.toString().contains("path=questions%2Ffoo.json")
        )
    }

    @Test
    fun decodedPath_rejectsRemainingPercent_doubleEncoding() {
        // Clean paths pass.
        assertTrue(WorkerActivationApi.validateDecodedPath("questions/foo.json"))
        assertTrue(WorkerActivationApi.validateDecodedPath("library.json"))
        // Double-encoded slash: after ONE decode "%252F" becomes "%2F",
        // which still carries '%' and must be rejected, never fetched.
        assertFalse(WorkerActivationApi.validateDecodedPath("questions/%2Ffoo.json"))
        // A literal '%' surviving a decode is rejected too (filenames
        // carrying a literal '%' are not served through this path).
        assertFalse(WorkerActivationApi.validateDecodedPath("books/100%.pdf"))
        assertFalse(WorkerActivationApi.validateDecodedPath("books/%25.pdf"))
    }
}
