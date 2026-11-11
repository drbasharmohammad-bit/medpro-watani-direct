package sy.medpro.watani.ui.ecg

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.EcgStripDto
import sy.medpro.watani.ui.theme.MaroonDark
import sy.medpro.watani.ui.theme.MaroonLight
import sy.medpro.watani.ui.theme.MaroonPrimary
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Vector ECG rendering for the mini-course (build 1.2.4). Everything
 * is drawn on a Compose Canvas from the strip's numeric samples — no
 * images anywhere. Standard printout geometry: 25 mm/s horizontally,
 * 10 mm/mV vertically, minor grid every 1 mm and major every 5 mm in
 * the app's rose/maroon palette (never yellow).
 */
private val GridMinor = Color(0xFFF3DCDF)
private val GridMajor = Color(0xFFE0ACB2)
private val TraceColor = MaroonDark
private val CaliperA = MaroonPrimary
private val CaliperB = MaroonLight

/** Draws the paper grid for the visible viewport. */
private fun DrawScope.drawEcgGrid(pxPerMm: Float, offsetX: Float) {
    val firstMm = floor(-offsetX / pxPerMm).toInt()
    val lastMm = ceil((size.width - offsetX) / pxPerMm).toInt()
    for (mm in firstMm..lastMm) {
        val x = offsetX + mm * pxPerMm
        val major = mm % 5 == 0
        drawLine(
            color = if (major) GridMajor else GridMinor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = if (major) 1.2f else 0.7f
        )
    }
    val centerY = size.height / 2f
    var mm = 0
    while (mm * pxPerMm <= size.height / 2f) {
        val major = mm % 5 == 0
        val color = if (major) GridMajor else GridMinor
        val w = if (major) 1.2f else 0.7f
        drawLine(color, Offset(0f, centerY - mm * pxPerMm), Offset(size.width, centerY - mm * pxPerMm), w)
        if (mm > 0) {
            drawLine(color, Offset(0f, centerY + mm * pxPerMm), Offset(size.width, centerY + mm * pxPerMm), w)
        }
        mm++
    }
}

/** Draws [samples] starting at [startSample] for [count] samples. */
private fun DrawScope.drawEcgTrace(
    samples: List<Int>,
    startSample: Int,
    count: Int,
    pxPerMm: Float,
    offsetX: Float,
    fs: Int
) {
    if (samples.isEmpty() || count <= 0) return
    val stepPx = (pxPerMm * EcgMath.mmPerSample(fs)).toFloat()
    val centerY = size.height / 2f
    val path = Path()
    var started = false
    val end = minOf(startSample + count, samples.size)
    for (i in startSample until end) {
        val x = offsetX + (i - startSample) * stepPx
        if (x < -stepPx || x > size.width + stepPx) {
            started = false
            continue
        }
        val y = centerY - (EcgMath.mmForMicrovolts(samples[i]) * pxPerMm).toFloat()
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
    }
    drawPath(path = path, color = TraceColor, style = Stroke(width = 1.8f))
}

/**
 * One lead of a strip on standard grid, with pinch-zoom and horizontal
 * pan. [seconds] of samples are shown from the start of the recording.
 */
@Composable
fun EcgStripView(
    samples: List<Int>,
    fs: Int,
    label: String?,
    modifier: Modifier = Modifier,
    seconds: Double = 10.0,
    interactive: Boolean = true
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val basePxPerMm = 4.2f
    val totalSamples = minOf(samples.size, (seconds * fs).toInt())

    Box(modifier = modifier.background(Color(0xFFFFFBFB))) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (interactive) {
                        Modifier.pointerInput(samples, fs) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                val pxPerMm = basePxPerMm * zoom
                                val stepPx = (pxPerMm * EcgMath.mmPerSample(fs)).toFloat()
                                val contentWidth = totalSamples * stepPx
                                val minOffset = minOf(0f, size.width - contentWidth)
                                offsetX = (offsetX + pan.x).coerceIn(minOffset, 0f)
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            val pxPerMm = basePxPerMm * zoom
            drawEcgGrid(pxPerMm, offsetX)
            drawEcgTrace(samples, 0, totalSamples, pxPerMm, offsetX, fs)
        }
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaroonPrimary,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}

/** Convenience: one lead of an [EcgStripDto] as a scrolling rhythm view. */
@Composable
fun EcgRhythmView(
    strip: EcgStripDto,
    lead: String,
    modifier: Modifier = Modifier,
    seconds: Double = 10.0,
    interactive: Boolean = true
) {
    EcgStripView(
        samples = strip.leadSamples(lead),
        fs = strip.sampleRate(),
        label = lead,
        modifier = modifier,
        seconds = seconds,
        interactive = interactive
    )
}

private fun DrawScope.drawStaticLead(
    samples: List<Int>,
    startSample: Int,
    count: Int,
    fs: Int
) {
    // Fit exactly [count] samples into the cell width.
    val secondsShown = if (fs > 0) count.toDouble() / fs else 0.0
    val pxPerMm = if (secondsShown > 0) {
        (size.width / (secondsShown * EcgMath.PAPER_SPEED_MM_PER_S)).toFloat()
    } else {
        4.2f
    }
    drawEcgGrid(pxPerMm, 0f)
    drawEcgTrace(samples, startSample, count, pxPerMm, 0f, fs)
}

@Composable
private fun EcgLeadCell(
    strip: EcgStripDto,
    lead: String,
    startSample: Int,
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color(0xFFFFFBFB))) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawStaticLead(strip.leadSamples(lead), startSample, count, strip.sampleRate())
        }
        Text(
            text = lead,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaroonPrimary,
            modifier = Modifier.padding(3.dp)
        )
    }
}

/**
 * Standard 12-lead print layout: 4 columns × 3 rows of 2.5 s windows
 * (I/aVR/V1/V4, II/aVL/V2/V5, III/aVF/V3/V6) plus a full-width 10 s
 * lead-II rhythm strip at the bottom.
 */
@Composable
fun EcgTwelveLeadView(strip: EcgStripDto, modifier: Modifier = Modifier) {
    val fs = strip.sampleRate()
    val windowSamples = (2.5 * fs).toInt()
    val columns = listOf(
        listOf("I", "aVR", "V1", "V4"),
        listOf("II", "aVL", "V2", "V5"),
        listOf("III", "aVF", "V3", "V6")
    )
    Column(modifier = modifier) {
        columns.forEachIndexed { colIndex, leads ->
            Row(modifier = Modifier.fillMaxWidth()) {
                leads.forEach { lead ->
                    EcgLeadCell(
                        strip = strip,
                        lead = lead,
                        startSample = colIndex * windowSamples,
                        count = windowSamples,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    )
                }
            }
        }
        EcgLeadCell(
            strip = strip,
            lead = strip.rhythmLead(),
            startSample = 0,
            count = minOf(strip.leadSamples(strip.rhythmLead()).size, 10 * fs),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        )
    }
}

/**
 * Rhythm strip with two draggable caliper markers. Markers snap to
 * the nearest peak when a drag ends. [onMeasurement] reports the
 * marker distance in ms and the heart rate IF the markers were read
 * as two consecutive R peaks.
 */
@Composable
fun EcgCalipersView(
    strip: EcgStripDto,
    modifier: Modifier = Modifier,
    onMeasurement: (deltaMs: Double, hrFromRr: Double) -> Unit = { _, _ -> }
) {
    val lead = strip.rhythmLead()
    val samples = strip.leadSamples(lead)
    val fs = strip.sampleRate()
    var markerA by remember(strip.id, lead) { mutableIntStateOf((samples.size * 0.25f).toInt()) }
    var markerB by remember(strip.id, lead) { mutableIntStateOf((samples.size * 0.25f).toInt() + (fs * 0.8f).toInt()) }
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var activeMarker by remember { mutableStateOf(0) } // 0 = A, 1 = B

    val deltaMs = EcgMath.deltaMs(markerA, markerB, fs)
    val hr = EcgMath.heartRateFromRrMs(deltaMs)
    LaunchedEffect(deltaMs) { onMeasurement(deltaMs, hr) }

    Box(modifier = modifier.background(Color(0xFFFFFBFB))) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(strip.id, lead, samples.size) {
                    canvasWidth = size.width.toFloat()
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (samples.isNotEmpty()) {
                                val stepPx = size.width.toFloat() / samples.size
                                val touched = (offset.x / stepPx).toInt()
                                val distA = kotlin.math.abs(touched - markerA)
                                val distB = kotlin.math.abs(touched - markerB)
                                activeMarker = if (distA <= distB) 0 else 1
                            }
                        },
                        onDrag = { change, _ ->
                            if (samples.isNotEmpty()) {
                                val stepPx = size.width.toFloat() / samples.size
                                val idx = (change.position.x / stepPx).toInt()
                                    .coerceIn(0, samples.lastIndex)
                                if (activeMarker == 0) markerA = idx else markerB = idx
                            }
                        },
                        onDragEnd = {
                            if (samples.isNotEmpty()) {
                                if (activeMarker == 0) {
                                    markerA = EcgMath.snapToPeak(samples, markerA)
                                } else {
                                    markerB = EcgMath.snapToPeak(samples, markerB)
                                }
                            }
                        }
                    )
                }
        ) {
            canvasWidth = size.width
            val secondsShown = if (fs > 0) samples.size.toDouble() / fs else 10.0
            val pxPerMm = (size.width / (secondsShown * EcgMath.PAPER_SPEED_MM_PER_S)).toFloat()
            drawEcgGrid(pxPerMm, 0f)
            drawEcgTrace(samples, 0, samples.size, pxPerMm, 0f, fs)
            if (samples.isNotEmpty()) {
                val stepPx = size.width / samples.size
                val xA = markerA * stepPx
                val xB = markerB * stepPx
                drawLine(CaliperA, Offset(xA, 0f), Offset(xA, size.height), strokeWidth = 2.5f)
                drawLine(CaliperB, Offset(xB, 0f), Offset(xB, size.height), strokeWidth = 2.5f)
                drawCircle(CaliperA, radius = 7f, center = Offset(xA, 10f))
                drawCircle(CaliperB, radius = 7f, center = Offset(xB, 10f))
            }
        }
        Text(
            text = "$lead — اسحب الفرجارين إلى قمّتين متتاليتين",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaroonPrimary,
            modifier = Modifier.padding(6.dp)
        )
    }
}
