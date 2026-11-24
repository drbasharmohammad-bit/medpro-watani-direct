package sy.medpro.watani.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.theme.DarkMaroonSurfaceVariant
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.NotoSansArabicFontFamily
import sy.medpro.watani.ui.theme.RoseSurfaceVariant
import sy.medpro.watani.ui.theme.wataniDarkTheme

/**
 * Shared Premium loading skeleton (build 1.2.48, performance list
 * item 10a — perceived performance). Generalizes the First Aid unit
 * skeleton of 1.2.45 (FaUnitLoadingSkeleton) to the app's remaining
 * genuine loading gates: instead of a lone spinner on an empty
 * screen, the student sees the shape of the list that is arriving —
 * card rows in the Premium identity (clean card, light shadow,
 * ivory/maroon track blocks) with a small spinner and caption.
 *
 * Professional-cache rule (23 Nov 2026) is preserved by the CALL
 * SITES, not by this composable: every gate that uses the skeleton
 * is already conditioned on the content being absent (empty
 * manifest / null envelope / unresolved unit), so cached content
 * still renders instantly with no skeleton flash, and the skeleton
 * shows only for content that is genuinely not on the device yet.
 *
 * Static by design, exactly like the First Aid model: no shimmer
 * animation competing with real content rendering on slow devices.
 */

/** Track-block colour of skeleton placeholders, per theme. */
@Composable
private fun skeletonTrack(): Color =
    if (wataniDarkTheme()) DarkMaroonSurfaceVariant else RoseSurfaceVariant

@Composable
fun PremiumListSkeleton(
    caption: String,
    modifier: Modifier = Modifier,
    rows: Int = SKELETON_DEFAULT_ROWS
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth(SKELETON_TITLE_FRACTION).height(16.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(skeletonTrack())
        )
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(rows) { PremiumSkeletonRow() }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = MaroonPrimary, modifier = Modifier.size(19.dp), strokeWidth = 2.2.dp
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                caption,
                fontFamily = NotoSansArabicFontFamily, fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun PremiumSkeletonRow() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(skeletonTrack())
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(SKELETON_ROW_TITLE_FRACTION).height(13.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(skeletonTrack())
                )
                Box(
                    modifier = Modifier.fillMaxWidth(SKELETON_ROW_SUBTITLE_FRACTION).height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(skeletonTrack())
                )
            }
        }
    }
}

// Skeleton proportions as named constants (mirrors the First Aid
// model's named-constant style from the 1.2.45 detekt pass).
private const val SKELETON_DEFAULT_ROWS = 4
private const val SKELETON_TITLE_FRACTION = 0.45f
private const val SKELETON_ROW_TITLE_FRACTION = 0.55f
private const val SKELETON_ROW_SUBTITLE_FRACTION = 0.8f
