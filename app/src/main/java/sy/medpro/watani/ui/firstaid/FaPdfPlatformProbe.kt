package sy.medpro.watani.ui.firstaid

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/** The booklet probe's engine-outage fallback (1.2.52): the
 * platform PdfRenderer check the probe ran on before PDFium —
 * used ONLY when the PDFium native library cannot load at all,
 * so an engine outage never condemns every booklet on the
 * device. Lives in its own file: FirstAidSectionScreen sits at
 * the detekt file-function ceiling, and this probe is a
 * self-contained unit with no screen state. Returns null for a
 * valid booklet, else the failure's real reason — the same
 * contract (and the same runCatching/fold shape) as the PDFium
 * probe it backs up. */
internal suspend fun validateFaPdfFileWithPlatformRenderer(file: File): String? {
    var fd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    val result = runCatching {
        fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(fd)
        if (renderer.pageCount > 0) {
            null
        } else {
            "PdfRenderer: الملف بلا صفحات (pageCount = 0)"
        }
    }
    try { renderer?.close() } catch (_: Exception) {}
    try { fd?.close() } catch (_: Exception) {}
    return result.fold(
        onSuccess = { it },
        onFailure = { failure ->
            "${failure.javaClass.simpleName}: " +
                (failure.message ?: "خطأ غير معروف أثناء فتح ملف PDF")
        }
    )
}
