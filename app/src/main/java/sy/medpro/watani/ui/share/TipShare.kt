package sy.medpro.watani.ui.share

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import sy.medpro.watani.data.ShareLinks
import sy.medpro.watani.data.SupportMessage

/**
 * Image-only share of a support tip (build 1.2.11) — mirrors the
 * approved question share exactly: the tip leaves the app ONLY as a
 * rendered card image; the ONLY accompanying text is the channel URL
 * ([ShareLinks.FALLBACK_URL]). On any rendering/IO failure nothing is
 * shared at all and the user is told — no silent dead tap and no text
 * fallback.
 */
fun shareTipAsImage(context: Context, message: SupportMessage) {
    try {
        val bitmap = TipCardRenderer.render(context, message)
        val file = TipCardRenderer.saveToCache(context, bitmap)
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, ShareLinks.FALLBACK_URL)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "مشاركة الرسالة").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    } catch (_: Exception) {
        try {
            Toast.makeText(context, "تعذّرت مشاركة الرسالة — أعد المحاولة.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { }
    }
}
