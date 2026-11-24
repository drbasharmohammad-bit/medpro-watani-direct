package sy.medpro.watani.ui.library

/**
 * SharedPreferences keys for the reader's text highlights
 * (build 1.2.50).
 *
 * Up to 1.2.49 the highlights of a book were stored under
 * its bare file name ("texthighlights_<name>"). A booklet
 * that was re-downloaded — updated content under the same
 * file name — silently inherited the old file's highlights,
 * which then landed on shifted text, and two different
 * books sharing a file name shared one highlight set. The
 * key now carries the file's length (the text index cache
 * in PdfTextEngine keys by path + length + lastModified
 * for the same reason), and [legacyTextHighlightsKey] pins
 * the old format so the reader can migrate a student's
 * existing highlights forward on first open instead of
 * losing them.
 */
object ReaderMarkingKeys {

    /** The current key: file name + file length. */
    fun textHighlightsKey(nameWithoutExtension: String, fileLength: Long): String =
        "texthighlights_${nameWithoutExtension}_$fileLength"

    /** The pre-1.2.50 key, kept for one-time migration. */
    fun legacyTextHighlightsKey(nameWithoutExtension: String): String =
        "texthighlights_$nameWithoutExtension"
}
