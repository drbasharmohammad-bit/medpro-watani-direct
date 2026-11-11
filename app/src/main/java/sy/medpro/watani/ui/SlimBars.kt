package sy.medpro.watani.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                maxLines = 1
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
