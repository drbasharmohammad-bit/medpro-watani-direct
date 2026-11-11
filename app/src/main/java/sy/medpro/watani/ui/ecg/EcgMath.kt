package sy.medpro.watani.ui.ecg

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure ECG measurement math for the mini-course (build 1.2.4). No
 * Android imports — everything here is unit-tested off-device
 * (EcgMathTest, run by build-final.yml).
 *
 * Units: strip samples are microvolts (µV) at [fs] samples/second,
 * drawn at the standard paper speed 25 mm/s and gain 10 mm/mV.
 */
object EcgMath {

    /** Paper speed of a standard ECG printout, mm per second. */
    const val PAPER_SPEED_MM_PER_S = 25.0

    /** Standard gain, mm per millivolt. */
    const val GAIN_MM_PER_MV = 10.0

    /** Milliseconds between two sample indices at [fs] Hz. */
    fun deltaMs(sampleA: Int, sampleB: Int, fs: Int): Double {
        if (fs <= 0) return 0.0
        return abs(sampleB - sampleA) * 1000.0 / fs
    }

    /** Heart rate (bpm) from an RR interval expressed in samples. */
    fun heartRateFromRrSamples(rrSamples: Int, fs: Int): Double {
        if (rrSamples <= 0 || fs <= 0) return 0.0
        return 60.0 * fs / rrSamples
    }

    /** Heart rate (bpm) from an RR interval expressed in milliseconds. */
    fun heartRateFromRrMs(rrMs: Double): Double {
        if (rrMs <= 0.0) return 0.0
        return 60000.0 / rrMs
    }

    /** The classic 300-method on paper: HR = 300 / large squares (5 mm). */
    fun heartRateFromLargeSquares(largeSquares: Double): Double {
        if (largeSquares <= 0.0) return 0.0
        return 300.0 / largeSquares
    }

    /** Bazett QTc: QT / sqrt(RR in seconds). Inputs in milliseconds. */
    fun qtcBazettMs(qtMs: Double, rrMs: Double): Double {
        if (qtMs <= 0.0 || rrMs <= 0.0) return 0.0
        return qtMs / sqrt(rrMs / 1000.0)
    }

    /** Horizontal distance of one sample on paper, in mm (25 mm/s). */
    fun mmPerSample(fs: Int): Double {
        if (fs <= 0) return 0.0
        return PAPER_SPEED_MM_PER_S / fs
    }

    /** Vertical deflection of a sample value on paper, in mm (10 mm/mV). */
    fun mmForMicrovolts(microvolts: Int): Double = microvolts / 1000.0 * GAIN_MM_PER_MV

    /**
     * Index of the strongest R peak near [hintIndex] inside a window of
     * ±[windowSamples]: the sample with the largest absolute value.
     * Used by the calipers to snap onto a QRS when the student taps
     * near a beat. Returns [hintIndex] for empty data.
     */
    fun snapToPeak(samples: List<Int>, hintIndex: Int, windowSamples: Int = 8): Int {
        if (samples.isEmpty()) return hintIndex
        val center = hintIndex.coerceIn(0, samples.lastIndex)
        val from = (center - windowSamples).coerceAtLeast(0)
        val to = (center + windowSamples).coerceAtMost(samples.lastIndex)
        var best = center
        var bestAbs = -1
        for (i in from..to) {
            val v = abs(samples[i])
            if (v > bestAbs) {
                bestAbs = v
                best = i
            }
        }
        return best
    }
}
