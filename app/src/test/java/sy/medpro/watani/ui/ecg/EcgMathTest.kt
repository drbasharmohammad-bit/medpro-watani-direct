package sy.medpro.watani.ui.ecg

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sy.medpro.watani.data.EcgEnvelope

/**
 * Build 1.2.4: ECG mini-course math + content-contract tests.
 * EcgMath is the pure measurement core behind the calipers, the
 * guided reader and the QTc helper — every number a student sees is
 * computed there, so it is pinned here off-device. The parse test
 * pins the ecg.json field names the publishing pipeline must emit.
 */
class EcgMathTest {

    @Test
    fun deltaMsAtFs100() {
        // At 100 Hz one sample is 10 ms.
        assertEquals(1000.0, EcgMath.deltaMs(0, 100, 100), 0.001)
        assertEquals(800.0, EcgMath.deltaMs(20, 100, 100), 0.001)
        // Order does not matter (absolute distance).
        assertEquals(800.0, EcgMath.deltaMs(100, 20, 100), 0.001)
        // 500 Hz acquisition: 250 samples = 500 ms.
        assertEquals(500.0, EcgMath.deltaMs(0, 250, 500), 0.001)
        assertEquals(0.0, EcgMath.deltaMs(10, 10, 100), 0.001)
        assertEquals(0.0, EcgMath.deltaMs(0, 100, 0), 0.001)
    }

    @Test
    fun heartRateFromRrSamples() {
        // RR of 80 samples at 100 Hz = 800 ms → 75 bpm.
        assertEquals(75.0, EcgMath.heartRateFromRrSamples(80, 100), 0.001)
        assertEquals(60.0, EcgMath.heartRateFromRrSamples(100, 100), 0.001)
        assertEquals(120.0, EcgMath.heartRateFromRrSamples(50, 100), 0.001)
        assertEquals(0.0, EcgMath.heartRateFromRrSamples(0, 100), 0.001)
    }

    @Test
    fun heartRateFromRrMs() {
        assertEquals(75.0, EcgMath.heartRateFromRrMs(800.0), 0.001)
        assertEquals(60.0, EcgMath.heartRateFromRrMs(1000.0), 0.001)
        assertEquals(0.0, EcgMath.heartRateFromRrMs(0.0), 0.001)
        // The paper 300-method agrees: 4 large squares → 75 bpm.
        assertEquals(75.0, EcgMath.heartRateFromLargeSquares(4.0), 0.001)
    }

    @Test
    fun qtcBazett() {
        // RR 1000 ms → sqrt(1.0) = 1 → QTc == QT.
        assertEquals(400.0, EcgMath.qtcBazettMs(400.0, 1000.0), 0.001)
        // RR 640 ms → sqrt(0.64) = 0.8 → QTc = 500.
        assertEquals(500.0, EcgMath.qtcBazettMs(400.0, 640.0), 0.001)
        // RR 250 ms (HR 240) → sqrt(0.25) = 0.5 → QTc doubles.
        assertEquals(600.0, EcgMath.qtcBazettMs(300.0, 250.0), 0.001)
        assertEquals(0.0, EcgMath.qtcBazettMs(0.0, 1000.0), 0.001)
        assertEquals(0.0, EcgMath.qtcBazettMs(400.0, 0.0), 0.001)
    }

    @Test
    fun paperGeometry() {
        // 25 mm/s at 100 Hz: each sample spans 0.25 mm.
        assertEquals(0.25, EcgMath.mmPerSample(100), 0.0001)
        // 10 mm/mV: 1000 µV (1 mV) deflects exactly 10 mm.
        assertEquals(10.0, EcgMath.mmForMicrovolts(1000), 0.0001)
        assertEquals(2.5, EcgMath.mmForMicrovolts(250), 0.0001)
    }

    @Test
    fun snapToPeakFindsR() {
        val samples = listOf(10, 20, 15, 30, 900, 40, 20, 10, 5, 0)
        assertEquals(4, EcgMath.snapToPeak(samples, hintIndex = 2, windowSamples = 4))
        assertEquals(0, EcgMath.snapToPeak(emptyList(), hintIndex = 0))
    }

    @Test
    fun ecgEnvelopeContractParses() {
        val json = """
            {
              "version": 1,
              "method": [{"step": 1, "title": "Rate", "ar": "المعدل", "source": "AHA"}],
              "patterns": [{
                "id": "norm", "ar": "طبيعي", "en": "NORM", "category": "الأساسيات",
                "level": 1, "hooks": ["h1"], "pitfalls": ["p1"],
                "criteria": [{"ar": "c1", "source": "AHA"}], "sourceLine": "AHA"
              }],
              "strips": [{
                "id": "s1", "patternId": "norm", "hr": 72, "axis": 60,
                "age": 34, "sex": "M", "fs": 100,
                "leads": ["I", "II"],
                "samples": {"I": [1, 2, 3], "II": [4, 5, 6]},
                "guided": {"rhythmAr": "جيبي", "axisAr": "طبيعي",
                            "findingsAr": ["f1"], "conclusionAr": "طبيعي"}
              }],
              "leadMap": [{"leads": ["II", "III", "aVF"], "wallAr": "سفلي", "arteryAr": "RCA"}]
            }
        """.trimIndent()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val env = moshi.adapter(EcgEnvelope::class.java).fromJson(json)
        assertTrue(env != null)
        assertEquals(1, env!!.method?.size)
        assertEquals("NORM", env.patterns?.first()?.en)
        val strip = env.strips!!.first()
        assertEquals(72, strip.hr)
        assertEquals(100, strip.sampleRate())
        assertEquals("II", strip.rhythmLead())
        assertEquals(listOf(4, 5, 6), strip.leadSamples("II"))
        assertEquals("طبيعي", strip.guided?.conclusionAr)
        assertEquals("سفلي", env.leadMap?.first()?.wallAr)
    }
}
