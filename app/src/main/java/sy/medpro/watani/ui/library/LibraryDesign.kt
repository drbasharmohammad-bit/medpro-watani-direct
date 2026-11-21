package sy.medpro.watani.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily

/**
 * Design tokens + shared components for the enhanced Library UI
 * (build 1.2.37), transcribed from the approved models in
 * ~/workspace/library-ui-design/ (01-subjects / 02-subject /
 * 03-booklet-sheet + style.css). The values are IDENTICAL to the
 * First Aid tokens (ui/firstaid FaColors — same CSS), duplicated
 * here so the Library carries its own design file per the build
 * plan; if one side ever changes, change both.
 *
 *   --maroon #5E1224 · --maroon-deep #42091A · --gold #C2A059
 *   --gold-soft #E9D9B4 · --gold-tint #F7EFDC · --bg #FAF6F0
 *   --muted #8A7A6E · --line #EFE6DA · green #2E7D5B
 *   cards: clean, one soft shadow, no coloured frames
 *   headings: Noto Kufi Arabic · body: Noto Sans Arabic
 *
 * Dark mode follows the Fa* convention: maroon/gold accents stay,
 * page background and card surface follow the app theme.
 */
object LibColors {
    val Maroon = Color(0xFF5E1224)
    val MaroonDeep = Color(0xFF42091A)
    val MaroonMid = Color(0xFF7A2233)
    val Gold = Color(0xFFC2A059)
    val GoldSoft = Color(0xFFE9D9B4)
    val GoldTint = Color(0xFFF7EFDC)
    /** Gold-family text colour on light tints (the CSS chip text). */
    val GoldText = Color(0xFF8A6D2F)
    val Bg = Color(0xFFFAF6F0)
    val Ink = Color(0xFF2B2320)
    val Muted = Color(0xFF8A7A6E)
    val Line = Color(0xFFEFE6DA)
    val Green = Color(0xFF2E7D5B)
    /** Play-circle background for a not-yet-downloaded video (.play). */
    val PlayTint = Color(0xFFF3E9E4)
    /** Chevron tint of the booklet strip (.chev). */
    val Chevron = Color(0xFFC9B894)
    /** Progress-bar label grey of the subject cards (.barlbl). */
    val BarLabel = Color(0xFFB4A696)
    /** Video-link box background of the booklet sheet (.vidlink). */
    val VidLinkBg = Color(0xFFFBF7F1)

    val MaroonBrush = Brush.linearGradient(listOf(MaroonDeep, Maroon))
    val GoldBrush = Brush.linearGradient(listOf(Color(0xFFD8BC7E), Gold))
}

@Composable
fun libBackground(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.background else LibColors.Bg

@Composable
fun libSurface(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White

@Composable
fun libHeading(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurface else LibColors.MaroonDeep

@Composable
fun libInk(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurface else LibColors.Ink

@Composable
fun libMuted(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurfaceVariant else LibColors.Muted

/** The design's clean card (.card/.subj/.vrow): white surface, one
 * soft shadow, no border. Box-based like FaCard (the pinned BOM's
 * clickable Card is experimental). */
@Composable
fun LibCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier
        .shadow(4.dp, shape, clip = false)
        .clip(shape)
        .background(libSurface())
    if (onClick != null) m = m.clickable { onClick() }
    Box(modifier = m) { content() }
}

/** The design's progress bar (.bar): warm track, gold gradient fill
 * anchored to the start side (right in RTL, as in the models). */
@Composable
fun LibGoldBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0xFFF0E7DB))
    ) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(f)
                    .height(height)
                    .clip(RoundedCornerShape(99.dp))
                    .background(LibColors.GoldBrush)
            )
        }
    }
}

/** Pill chip of the design (.chip): [pdfStyle] = gold tint (.chip-pdf),
 * otherwise the rose code tint (.chip-num). */
@Composable
fun LibChip(text: String, pdfStyle: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (pdfStyle) LibColors.GoldTint else LibColors.PlayTint)
            .padding(horizontal = 11.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            color = if (pdfStyle) LibColors.GoldText else LibColors.Maroon
        )
    }
}

/** The design's white header button (.iconbtn): 42dp, radius 14,
 * soft shadow, maroon icon. */
@Composable
fun LibIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .background(libSurface())
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = LibColors.Maroon, modifier = Modifier.size(20.dp))
    }
}

/**
 * The official app logo for the library header (.apphead .logo,
 * 46dp, radius 14): the launcher icon itself (white M + gold plus
 * on maroon), rasterized from the application icon exactly as the
 * drawer header (MainActivity, 1.2.17) and the sign-in gate do —
 * painterResource on the adaptive-icon mipmap can resolve to the
 * anydpi XML, so the bitmap route is the proven one.
 */
@Composable
fun LibAppLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(context) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bmp = android.graphics.Bitmap.createBitmap(
                240, 240, android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, 240, 240)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    Box(
        modifier = modifier
            .size(46.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .background(LibColors.MaroonDeep),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "شعار وطني", modifier = Modifier.size(46.dp))
        }
    }
}

/** The booklet sheet's primary action (.btn.primary): maroon fill,
 * 52dp, radius 16, Kufi bold. [enabled] = false renders the design's
 * disabled state (the «فتح الكتيّب» button while downloading). */
@Composable
fun LibPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(if (enabled) 4.dp else 0.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) LibColors.Maroon else LibColors.Maroon.copy(alpha = 0.45f))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, color = Color.White
        )
    }
}

/** The booklet sheet's ghost action (.btn.ghost): surface fill,
 * 1.5dp line border, maroon Kufi text. */
@Composable
fun LibGhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(libSurface())
            .androidx.compose.foundation.border(
                1.5.dp, LibColors.Line, RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, color = LibColors.Maroon
        )
    }
}

/** Video/booklet size label, same formatting the old list used. */
fun libSizeText(sizeBytes: Long?): String {
    val mb = (sizeBytes ?: 0L) / (1024.0 * 1024.0)
    return if (mb > 0) "%.1f MB".format(mb) else "حجم غير محدد"
}

/** Arabic count word for booklets, per the models: 9 → «كتيّبات»,
 * 19 → «كتيّبًا». Zero is rendered by callers as «بلا كتيّبات». */
fun libBookletCountWord(count: Int): String = when {
    count == 1 -> "كتيّب واحد"
    count == 2 -> "كتيّبان"
    count in 3..10 -> "كتيّبات"
    else -> "كتيّبًا"
}
