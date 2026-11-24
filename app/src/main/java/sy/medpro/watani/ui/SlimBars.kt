package sy.medpro.watani.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.MaroonPrimary

/**
 * Slim chrome (build 1.2.7, doctor's device verdict): the stock Material3
 * TopAppBar is a fixed 64dp block, and every nested sub-screen bar added
 * its own 64dp block under the main one, so two stacked maroon bars ate
 * roughly a third of the screen before any content appeared. These bars
 * keep the exact same identity (same maroon, same icons, same titles and
 * actions) at a fraction of the height:
 *
 *  - [SlimMainBar]: the tab header — one 52dp row (drawer icon + tab
 *    title). The brand slogan no longer lives here; it stays on the
 *    sign-in screen only. This is the ONLY bar that pads for the status
 *    bar, because it is the topmost chrome in the window.
 *  - [SlimSubBar]: every sub-screen bar (back arrow + title + the same
 *    actions the old TopAppBar carried) — one 48dp row, deliberately
 *    WITHOUT any status-bar inset: sub-screens render inside the main
 *    Scaffold's content, under the main bar, so an inset here would be
 *    pure dead space stacked on top of the main bar's own.
 */
object SlimBarHeights {
    val MAIN = 52.dp
    val SUB = 48.dp
}

/** Named metrics for [SlimBarTitle]'s fitting loop (detekt
 * MagicNumber is enforced on new code in this repo). */
object SlimBarTitleMetrics {
    /** Font shrink step while fitting a bar title to its width. */
    const val SHRINK_STEP_SP = 0.5f

    /** Line height as a multiple of the fitted font size — Arabic
     * fonts need the extra room or ascenders/descenders clip. */
    const val LINE_HEIGHT_FACTOR = 1.4f
}

/**
 * Bar title that can NEVER be clipped (build 1.2.49, doctor's device
 * report 24 Nov 2026: the cases screen title «المريض الافتراضي والحالات
 * السريرية (436 حالة)» wrapped to a second line inside the fixed 48dp
 * sub-bar and its bottom was sliced off at the panel edge).
 *
 * Root cause of that whole family: the bars have a FIXED height while
 * the old call sites passed a bare wrapping [Text] — a long title took
 * two lines, the second overflowed the fixed-height row and was cut.
 * The fix is text-side only (the panel height does not change): this
 * title is always ONE line, measured against the real available width
 * and shrunk in 0.5sp steps from [maxFontSize] down to [minFontSize]
 * until it fits; its line height is pinned at 1.4× the fitted size so
 * Arabic ascenders/descenders always have room inside the line box.
 * Ellipsis remains only as a last-resort guard at the floor size.
 */
@Composable
fun SlimBarTitle(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 17.sp,
    minFontSize: TextUnit = 12.sp,
    color: Color = Color.Unspecified
) {
    val measurer = rememberTextMeasurer()
    val baseStyle = LocalTextStyle.current
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = constraints.maxWidth
        val fitted = remember(text, maxWidthPx, baseStyle, maxFontSize, minFontSize, fontScale) {
            var candidate = maxFontSize
            if (maxWidthPx > 0) {
                while (candidate > minFontSize) {
                    val width = measurer.measure(
                        text = AnnotatedString(text),
                        style = baseStyle.copy(fontSize = candidate, fontWeight = FontWeight.Bold),
                        softWrap = false,
                        maxLines = 1
                    ).size.width
                    if (width <= maxWidthPx) break
                    candidate = (candidate.value - SlimBarTitleMetrics.SHRINK_STEP_SP).sp
                }
            }
            candidate
        }
        Text(
            text = text,
            fontSize = fitted,
            lineHeight = (fitted.value * SlimBarTitleMetrics.LINE_HEIGHT_FACTOR).sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SlimMainBar(
    title: String,
    onMenuClick: () -> Unit
) {
    Surface(color = MaroonPrimary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(SlimBarHeights.MAIN)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "القائمة",
                    tint = Color.White
                )
            }
            SlimBarTitle(
                text = title,
                maxFontSize = 18.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun SlimSubBar(
    onBack: () -> Unit,
    containerColor: Color = MaroonPrimary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit
) {
    Surface(color = containerColor) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SlimBarHeights.SUB)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = contentColor
                    )
                }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f)
                ) {
                    title()
                }
                actions()
            }
        }
    }
}
