package sy.medpro.watani.ui.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sy.medpro.watani.ui.theme.NotoKufiFontFamily
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonDark
import sy.medpro.watani.ui.theme.MaroonInk
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.TajawalFontFamily
import sy.medpro.watani.ui.theme.TextOnDark
import sy.medpro.watani.ui.theme.ThmanyahFontFamily

/**
 * Brand intro — every cold start (build 1.2.21, doctor's order:
 * the welcome video is deleted for good and replaced by an intro
 * drawn entirely in code, no video file, no sound).
 *
 * Same slot and contract as the old video splash: MainActivity
 * shows it in the `showSplash` branch — after the mandatory-update
 * gate and before [sy.medpro.watani.ui.auth.SignInGate] — and it
 * advances exclusively through [onDone], exactly once.
 *
 * Presentation is the luxury sign-in gate's own family, value for
 * value (see SignInGate): the deep-maroon vertical gradient
 * (MaroonInk → MaroonDark → MaroonPrimary), the muted-copper radial
 * halo behind the official app icon (rasterized from the installed
 * application icon, exactly as the gate does), the «وطني» wordmark,
 * the «MED PRO» copper tracking line, the official slogan and the
 * hairline divider with its centre diamond.
 *
 * Timing: staggered entrance (tween 550ms, delays 0/140/220ms,
 * alpha + vertical rise only — cheap on weak devices), then a
 * short hold; [onDone] fires 1.8s after first composition, inside
 * the 1.6–2.2s budget. A tap anywhere skips at once. The finish
 * guard mirrors the old splash: [onDone] runs at most once, so
 * the student can never be stuck here. No startup work is added:
 * no sleeps, no network, no disk waits.
 */
@Composable
fun BrandIntroScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var doneCalled by remember { mutableStateOf(false) }
    fun finish() {
        if (!doneCalled) {
            doneCalled = true
            onDone()
        }
    }

    // Entrance choreography (same helper shape as SignInGate): one
    // trigger, staggered fades/rises; the same effect owns the
    // timed finish so the total stay stays in the 1.6–2.2s budget.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shown = true
        delay(1800)
        finish()
    }
    fun entrance(delayMs: Int, riseFraction: Float) = fadeIn(
        animationSpec = tween(durationMillis = 550, delayMillis = delayMs)
    ) + slideInVertically(
        animationSpec = tween(durationMillis = 550, delayMillis = delayMs)
    ) { (it * riseFraction).toInt() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to MaroonInk,
                    0.48f to MaroonDark,
                    1.0f to MaroonPrimary
                )
            )
            .clickable { finish() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ---------- Logo with its copper halo ----------
            AnimatedVisibility(visible = shown, enter = entrance(0, 0.22f)) {
                // The official app logo (the launcher icon itself —
                // the fixed white M + gold plus on maroon).
                // Rasterized from the installed application icon so
                // adaptive-icon resources render correctly;
                // painterResource on a mipmap can resolve to the
                // anydpi XML. (Same technique as SignInGate.)
                val appIconBitmap = remember(context) {
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
                Box(contentAlignment = Alignment.Center) {
                    // Muted-copper halo behind the logo (brand
                    // accent only — never a yellow UI surface).
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        GoldAccent.copy(alpha = 0.20f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = "شعار وطني",
                            modifier = Modifier
                                .size(112.dp)
                                .shadow(18.dp, RoundedCornerShape(28.dp))
                                .clip(RoundedCornerShape(28.dp))
                                .border(
                                    1.dp,
                                    GoldAccent.copy(alpha = 0.55f),
                                    RoundedCornerShape(28.dp)
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .shadow(18.dp, RoundedCornerShape(28.dp))
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaroonInk)
                                .border(
                                    1.dp,
                                    GoldAccent.copy(alpha = 0.55f),
                                    RoundedCornerShape(28.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "M",
                                color = Color.White,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            // ---------- Wordmark ----------
            AnimatedVisibility(visible = shown, enter = entrance(140, 0.22f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "وطني",
                        fontFamily = ThmanyahFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        color = TextOnDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "MED PRO",
                        style = TextStyle(
                            fontFamily = NotoKufiFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 4.5.sp,
                            color = GoldAccent
                        )
                    )
                }
            }
            // ---------- Slogan + diamond divider ----------
            AnimatedVisibility(visible = shown, enter = entrance(220, 0.22f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "نجاحك في الوطني يبدأ من هنا",
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        color = TextOnDark.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    // Hairline divider with a centre diamond.
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .width(74.dp)
                                .height(1.5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            GoldAccent,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .rotate(45f)
                                .background(GoldAccent)
                        )
                    }
                }
            }
        }
    }
}
