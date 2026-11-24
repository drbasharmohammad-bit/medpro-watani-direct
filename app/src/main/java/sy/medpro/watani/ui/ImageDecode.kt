package sy.medpro.watani.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decode-at-display-size (build 1.2.48, performance list item 6 —
 * images decoded at the size they are shown, not at source
 * resolution). Before this helper, the app's downloaded/served
 * bitmaps were decoded at FULL source size and only then scaled by
 * the layout: a First Aid figure of several megapixels was held in
 * memory to fill a phone-width slot, and the auscultation waveform
 * was decoded whole to be drawn 54dp tall. The decoders below read
 * the bounds first and subsample by a power of two so the decoded
 * bitmap is the smallest one that still covers the display target —
 * same pixels on screen, a fraction of the memory and decode time.
 *
 * [sampleSize] is pure arithmetic and unit-tested; the decode entry
 * points never throw (null on any failure, exactly like the
 * call sites' previous try/catch behaviour).
 */
object ImageDecode {

    /** Generous width cap for full-width in-text figures: covers
     * the widest phone/tablet render slot without waste. */
    const val FIGURE_TARGET_WIDTH_PX = 1200

    /** Width cap for the auscultation waveform strip. */
    const val WAVEFORM_TARGET_WIDTH_PX = 1080

    /** Height cap for the auscultation waveform strip (54dp at the
     * highest common densities). */
    const val WAVEFORM_TARGET_HEIGHT_PX = 256

    /** Largest power-of-two sample size whose decode still covers
     * [targetWidthPx] (and [targetHeightPx] when > 0). Never
     * upsamples: a source at or under the target decodes at 1. */
    fun sampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidthPx: Int,
        targetHeightPx: Int = 0
    ): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1
        if (targetWidthPx <= 0 && targetHeightPx <= 0) return 1
        var sample = 1
        while (true) {
            val next = sample * 2
            val widthOk = targetWidthPx <= 0 || sourceWidth / next >= targetWidthPx
            val heightOk = targetHeightPx <= 0 || sourceHeight / next >= targetHeightPx
            if (widthOk && heightOk) sample = next else return sample
        }
    }

    /** Decodes the file at [path] subsampled toward the target. */
    fun decodeFileDownsampled(
        path: String,
        targetWidthPx: Int,
        targetHeightPx: Int = 0
    ): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            BitmapFactory.decodeFile(path)
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(
                    bounds.outWidth, bounds.outHeight, targetWidthPx, targetHeightPx
                )
            }
            BitmapFactory.decodeFile(path, options)
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    /** Decodes the drawable resource [resId] subsampled toward the
     * target (widget backgrounds are drawn at widget size). */
    fun decodeResourceDownsampled(
        resources: Resources,
        resId: Int,
        targetWidthPx: Int,
        targetHeightPx: Int = 0
    ): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            BitmapFactory.decodeResource(resources, resId)
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(
                    bounds.outWidth, bounds.outHeight, targetWidthPx, targetHeightPx
                )
            }
            BitmapFactory.decodeResource(resources, resId, options)
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}
