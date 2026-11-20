package sy.medpro.watani.ui.firstaid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily

/**
 * The approved First Aid design system (build 1.2.36, merge phase 5),
 * transcribed 1:1 from ~/workspace/firstaid-design/style.css — the five
 * approved models (01-home … 05-reference) are the visual contract:
 *
 *   --maroon #5E1224 · --maroon-deep #42091A · --maroon-mid #7A2233
 *   --gold #C2A059 · --gold-soft #E9D9B4 · --gold-tint #F7EFDC
 *   --bg #FAF6F0 · --surface #FFFFFF · --ink #2B2320 · --muted #8A7A6E
 *   --line #EFE6DA · --green #2E7D5B / --green-tint #EAF4EE
 *   --red #B3402E / --red-tint #FAEEEA
 *   cards: radius 22, soft shadow (no thick coloured frames, ever)
 *   headings: Noto Kufi Arabic · body: Noto Sans Arabic
 *
 * Dark mode keeps the same maroon/gold accents (they read on dark);
 * only the page background and card surface follow the app theme so
 * the section never glares against a dark app.
 */
object FaColors {
    val Maroon = Color(0xFF5E1224)
    val MaroonDeep = Color(0xFF42091A)
    val MaroonMid = Color(0xFF7A2233)
    val Gold = Color(0xFFC2A059)
    val GoldSoft = Color(0xFFE9D9B4)
    val GoldTint = Color(0xFFF7EFDC)
    val GoldText = Color(0xFF8A6D2F)
    val GoldDeepText = Color(0xFF6E5518)
    val Bg = Color(0xFFFAF6F0)
    val Ink = Color(0xFF2B2320)
    val Muted = Color(0xFF8A7A6E)
    val Line = Color(0xFFEFE6DA)
    val Green = Color(0xFF2E7D5B)
    val GreenTint = Color(0xFFEAF4EE)
    val Red = Color(0xFFB3402E)
    val RedTint = Color(0xFFFAEEEA)
    val ChipCodeBg = Color(0xFFF3E9E4)
    val HeroTextSoft = Color(0xFFEBD9C8)

    /** Maroon gradient of the hero / primary buttons (150deg in CSS). */
    val MaroonBrush = Brush.linearGradient(listOf(Maroon, MaroonDeep))
    val MaroonMidBrush = Brush.linearGradient(listOf(MaroonMid, Maroon))
    val GoldBrush = Brush.linearGradient(listOf(Color(0xFFD8BC7E), Gold))
}

/** Page background: the design's warm ivory in light mode, the app's
 * own background in dark mode. */
@Composable
fun faBackground(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.background else FaColors.Bg

/** Card surface: white per the design in light mode; theme surface in dark. */
@Composable
fun faSurface(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White

/** Primary ink for headings: maroon-deep in light mode per the design,
 * theme onSurface in dark mode (maroon-deep is unreadable on dark). */
@Composable
fun faHeading(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurface else FaColors.MaroonDeep

/** Body ink. */
@Composable
fun faInk(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurface else FaColors.Ink

/** Secondary text: the design's warm muted in light mode. */
@Composable
fun faMuted(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurfaceVariant else FaColors.Muted

/** The design's clean card: radius 22, one soft shadow, no border.
 * Built on Box (not Material3's clickable Card, experimental in the
 * pinned BOM) — visually identical to the models' .card. */
@Composable
fun FaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    var m = modifier
        .shadow(6.dp, shape, clip = false)
        .clip(shape)
        .background(faSurface())
    if (onClick != null) m = m.clickable { onClick() }
    Box(modifier = m) { content() }
}

/** Small pill chip of the design (.chip): tinted bg + bold 11sp text. */
@Composable
fun FaChip(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text, fontFamily = NotoSansArabicFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 11.sp, color = fg
        )
    }
}

/** The design's 7dp progress bar (.bar): warm track, maroon or gold fill. */
@Composable
fun FaProgressBar(fraction: Float, gold: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0xFFF0E7DB))
    ) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (gold) FaColors.GoldBrush else FaColors.MaroonMidBrush)
            )
        }
    }
}

/** Section screen header (.apphead): white back button with soft
 * shadow, Kufi title in maroon-deep, muted subtitle. */
@Composable
fun FaScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .shadow(4.dp, RoundedCornerShape(13.dp), clip = false)
                    .clip(RoundedCornerShape(13.dp))
                    .background(faSurface())
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = FaColors.Maroon,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 18.5.sp, color = faHeading(), lineHeight = 25.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle, fontFamily = NotoSansArabicFontFamily,
                    fontSize = 12.sp, color = faMuted()
                )
            }
        }
    }
}

/** The design's full-width maroon gradient action button (.next). */
@Composable
fun FaPrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(FaColors.MaroonMidBrush)
            .clickable { onClick() }
            .padding(vertical = 13.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontFamily = NotoKufiFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, color = Color.White
        )
    }
}

/** Centered, width-governed content column (WideLayout contract:
 * reading content caps at READING_MAX and centers on wide windows). */
@Composable
fun FaReadingColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = sy.medpro.watani.ui.WidthBreakpoints.READING_MAX)) {
            content()
        }
    }
}

/** Option letters of the design's quiz model: أ ب ج د هـ و ز ح ط ي. */
val FA_OPTION_LETTERS = listOf("أ", "ب", "ج", "د", "هـ", "و", "ز", "ح", "ط", "ي")

fun faOptionLetter(index: Int): String = FA_OPTION_LETTERS.getOrElse(index) { "${index + 1}" }
