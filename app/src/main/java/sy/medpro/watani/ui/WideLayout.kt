package sy.medpro.watani.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width breakpoints for the tablet pass (build 1.2.5, doctor's
 * correction): adaptation keys on the AVAILABLE WIDTH only — never
 * on orientation. A portrait tablet (width ≥ 600 dp held upright)
 * gets its column grids and governed content width; side panels are
 * reserved for genuinely wide windows (≥ 840 dp), which in practice
 * means landscape tablets or very large portrait ones. Phones in
 * either orientation stay below 600 dp and keep the exact phone
 * layouts. (The landscape chrome rule — bottom bar only, drawer by
 * edge swipe — lives in MainActivity and is untouched by this.)
 */
object WidthBreakpoints {
    /** ≥ this width: two-column grids, governed content widths. */
    val MEDIUM: Dp = 600.dp

    /** ≥ this width: side-by-side panels (strip + step/measurement). */
    val EXPANDED: Dp = 840.dp

    /** Content never stretches past this; it centers instead. */
    val CONTENT_MAX: Dp = 1120.dp

    /** Reading content (lessons, details) caps tighter for line length. */
    val READING_MAX: Dp = 880.dp
}

/** Current window width from the configuration (multi-window aware). */
@Composable
fun currentWindowWidth(): Dp = LocalConfiguration.current.screenWidthDp.dp

@Composable
fun isMediumWidth(): Boolean = currentWindowWidth() >= WidthBreakpoints.MEDIUM

@Composable
fun isExpandedWidth(): Boolean = currentWindowWidth() >= WidthBreakpoints.EXPANDED
