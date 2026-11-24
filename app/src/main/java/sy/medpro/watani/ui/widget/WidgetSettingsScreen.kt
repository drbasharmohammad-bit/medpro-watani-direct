package sy.medpro.watani.ui.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import sy.medpro.watani.data.CountdownMath
import sy.medpro.watani.data.DailyGoalEngine
import sy.medpro.watani.data.WidgetBackgroundMath
import sy.medpro.watani.data.WidgetGlassMath
import sy.medpro.watani.data.WidgetGlassStyle
import sy.medpro.watani.data.WidgetPreviewSize
import sy.medpro.watani.data.WidgetBgMode
import sy.medpro.watani.data.WidgetCountdownStyle
import sy.medpro.watani.data.WidgetQuickAction
import sy.medpro.watani.data.WidgetSettings
import sy.medpro.watani.data.WidgetSettingsMath
import sy.medpro.watani.data.WidgetSettingsStore
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.ThmanyahFontFamily
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.RoseOutline
import java.time.LocalDate

/**
 * شاشة «إعدادات الويدجت» (بناء 1.2.25 — كما بنموذج الإعدادات
 * المعتمد): خلفية بمعالم سورية (تدوير يومي/ثابت/سادة) مع
 * مصغّرات الاختيار، منزلق تعتيم، مفاتيح الوحدات الظاهرة، نمط
 * العدّاد، واختيار الأزرار السريعة.
 *
 * كل تغيير يُحفظ ويُطبَّق فورًا على الويدجتات الموضوعة
 * (Widgets.updateAll) — بلا زر حفظ، فالطالب يرى أثر اختياره
 * على شاشته الرئيسية مباشرة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(WidgetSettingsStore.settings(context)) }
    var dimDraft by remember { mutableStateOf(settings.dimPercent.toFloat()) }

    fun apply(next: WidgetSettings) {
        settings = next
        WidgetSettingsStore.save(context, next)
        Widgets.updateAll(context)
    }

    val currentIndex = WidgetBackgroundMath.pickIndex(
        settings, LocalDate.now().toEpochDay(), LandmarkLibrary.all.size
    )

    DrawerScreenScaffold(title = "إعدادات الويدجت", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "خصّص ويدجت الشاشة الرئيسية: الخلفية والوحدات والأزرار — كل تغيير يظهر على ويدجتك فورًا.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            SettingsCard(title = "الزجاج والمعاينة (جديد)") {
            // ── عائلة الزجاج v4 (1.2.51): معاينة حية + النمط
            // الزجاجي + الشفافية + مفاتيح المحتوى — كما بنموذج
            // settings.png المعتمد حرفيًا. ─────────────────────
            GlassPreviewCard(settings)

            Spacer(Modifier.height(10.dp))
            Text(
                "حجم المعاينة",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaroonPrimary
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WidgetPreviewSize.entries.forEach { size ->
                    FilterChip(
                        selected = settings.previewSize == size,
                        onClick = { apply(settings.copy(previewSize = size)) },
                        label = {
                            Text(
                                size.label,
                                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaroonPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "النمط الزجاجي",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaroonPrimary
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassStyleThumb(
                    spec = GlassThumbSpec(
                        style = WidgetGlassStyle.DARK,
                        label = "عنابي داكن",
                        photoIndex = settings.fixedLandmarkIndex,
                        alphaPercent = settings.glassAlphaPercent
                    ),
                    selected = settings.glassStyle == WidgetGlassStyle.DARK,
                    modifier = Modifier.weight(1f)
                ) { apply(settings.copy(glassStyle = WidgetGlassStyle.DARK)) }
                GlassStyleThumb(
                    spec = GlassThumbSpec(
                        style = WidgetGlassStyle.LIGHT,
                        label = "عاجي فاتح",
                        photoIndex = settings.fixedLandmarkIndex,
                        alphaPercent = settings.glassAlphaPercent
                    ),
                    selected = settings.glassStyle == WidgetGlassStyle.LIGHT,
                    modifier = Modifier.weight(1f)
                ) { apply(settings.copy(glassStyle = WidgetGlassStyle.LIGHT)) }
                GlassStyleThumb(
                    spec = GlassThumbSpec(
                        style = WidgetGlassStyle.CLEAR,
                        label = "شفاف",
                        photoIndex = settings.fixedLandmarkIndex,
                        alphaPercent = settings.glassAlphaPercent
                    ),
                    selected = settings.glassStyle == WidgetGlassStyle.CLEAR,
                    modifier = Modifier.weight(1f)
                ) { apply(settings.copy(glassStyle = WidgetGlassStyle.CLEAR)) }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "شفافية الخلفية: ${settings.glassAlphaPercent}٪",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaroonPrimary
            )
            Slider(
                value = settings.glassAlphaPercent.toFloat(),
                onValueChange = { v ->
                    apply(settings.copy(glassAlphaPercent = v.toInt()))
                },
                valueRange = WidgetGlassMath.GLASS_ALPHA_MIN.toFloat()..WidgetGlassMath.GLASS_ALPHA_MAX.toFloat(),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = MaroonPrimary,
                    activeTrackColor = MaroonPrimary
                )
            )
            Text(
                "الحد الأدنى ${WidgetGlassMath.GLASS_ALPHA_MIN}٪ ثابت: دونه يضعف تباين النص فوق صور المعالم الفاتحة.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "المحتوى الظاهر",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaroonPrimary
            )
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                label = "العدّاد بالثواني",
                checked = settings.countdownStyle == WidgetCountdownStyle.DAYS_AND_TIME
            ) { on ->
                apply(
                    settings.copy(
                        countdownStyle = if (on) {
                            WidgetCountdownStyle.DAYS_AND_TIME
                        } else {
                            WidgetCountdownStyle.DAYS_ONLY
                        }
                    )
                )
            }
            SwitchRow(
                label = "هدف اليوم والسلسلة",
                checked = settings.showGoals
            ) { on -> apply(settings.copy(showGoals = on)) }
            SwitchRow(
                label = "زوادة اليوم",
                checked = settings.showTip
            ) { on -> apply(settings.copy(showTip = on)) }
            SwitchRow(
                label = "الأزرار السريعة",
                checked = settings.showButtons
            ) { on -> apply(settings.copy(showButtons = on)) }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    val provider = ComponentName(context, CountdownWideWidgetProvider::class.java)
                    val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            manager.requestPinAppWidget(provider, null, null)
                        } catch (_: Exception) {
                            false
                        }
                    } else {
                        false
                    }
                    Toast.makeText(
                        context,
                        if (ok) "اتبع خطوات اللانشر لإضافة الويدجت" else "أضِف الويدجت يدويًا من قائمة الويدجتات",
                        Toast.LENGTH_LONG
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaroonPrimary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    "إضافة إلى الشاشة الرئيسية",
                        fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Text(
                "أو يدويًا: اضغط مطولًا على الشاشة الرئيسية ← الويدجتات ← Med Pro ← اختر التصميم.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(18.dp))

            }

            SettingsCard(title = "خلفية الويدجت") {
                RadioRow(
                    label = "تدوير المعالم تلقائيًا (صورة جديدة كل يوم)",
                    selected = settings.bgMode == WidgetBgMode.ROTATE,
                    onClick = { apply(settings.copy(bgMode = WidgetBgMode.ROTATE)) }
                )
                RadioRow(
                    label = "معلم ثابت أختاره",
                    selected = settings.bgMode == WidgetBgMode.FIXED,
                    onClick = { apply(settings.copy(bgMode = WidgetBgMode.FIXED)) }
                )
                RadioRow(
                    label = "عنابي سادة (بلا صورة)",
                    selected = settings.bgMode == WidgetBgMode.PLAIN,
                    onClick = { apply(settings.copy(bgMode = WidgetBgMode.PLAIN)) }
                )

                if (settings.bgMode == WidgetBgMode.FIXED) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        itemsIndexed(LandmarkLibrary.all) { index, landmark ->
                            val chosen = settings.fixedLandmarkIndex == index
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    apply(settings.copy(fixedLandmarkIndex = index))
                                }
                            ) {
                                Box {
                                    Image(
                                        painter = painterResource(landmark.drawableRes),
                                        contentDescription = landmark.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .then(
                                                if (chosen) {
                                                    Modifier.border(
                                                        2.dp, Color(0xFFD4AF37),
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    )
                                    if (chosen) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFFD4AF37),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    landmark.name,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (currentIndex != null) {
                        "المعلم المعروض حاليًا: ${LandmarkLibrary.all[currentIndex].name}"
                    } else {
                        "المعلم المعروض حاليًا: عنابي سادة"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تعتيم الخلفية", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${dimDraft.toInt()}%",
                        fontSize = 13.sp,
                        color = MaroonPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = dimDraft,
                    onValueChange = { dimDraft = it },
                    onValueChangeFinished = {
                        apply(settings.copy(dimPercent = dimDraft.toInt()))
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "تعتيم أعلى = نص أوضح فوق الصورة، وتعتيم أخف = صورة أبرز.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            SettingsCard(title = "الوحدات الظاهرة") {
                SwitchRow("العدّ التنازلي", settings.showCountdown) {
                    apply(settings.copy(showCountdown = it))
                }
                SwitchRow("تقدم الأسئلة", settings.showQuestions) {
                    apply(settings.copy(showQuestions = it))
                }
                SwitchRow("أهداف اليوم", settings.showGoals) {
                    apply(settings.copy(showGoals = it))
                }
                SwitchRow("دقائق التركيز", settings.showFocus) {
                    apply(settings.copy(showFocus = it))
                }
                SwitchRow("الأزرار السريعة", settings.showButtons) {
                    apply(settings.copy(showButtons = it))
                }
            }

            SettingsCard(title = "نمط العدّاد") {
                RadioRow(
                    label = "أيام وساعة (أيام + باقي اليوم بالساعة والدقيقة والثانية)",
                    selected = settings.countdownStyle == WidgetCountdownStyle.DAYS_AND_TIME,
                    onClick = { apply(settings.copy(countdownStyle = WidgetCountdownStyle.DAYS_AND_TIME)) }
                )
                RadioRow(
                    label = "أيام فقط",
                    selected = settings.countdownStyle == WidgetCountdownStyle.DAYS_ONLY,
                    onClick = { apply(settings.copy(countdownStyle = WidgetCountdownStyle.DAYS_ONLY)) }
                )
                RadioRow(
                    label = "رقمي كامل (إجمالي الساعات المتبقية بعدّاد واحد)",
                    selected = settings.countdownStyle == WidgetCountdownStyle.FULL_DIGITAL,
                    onClick = { apply(settings.copy(countdownStyle = WidgetCountdownStyle.FULL_DIGITAL)) }
                )
            }

            SettingsCard(title = "الأزرار السريعة") {
                Text(
                    "اختر حتى 4 أزرار تظهر على الويدجت — كل زر يفتح شاشته داخل التطبيق مباشرة. ترتيب الظهور هو ترتيب اختيارك.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetQuickAction.entries.forEach { action ->
                        val selected = action in settings.quickActions
                        FilterChip(
                            selected = selected,
                            onClick = {
                                apply(
                                    settings.copy(
                                        quickActions = WidgetSettingsMath.toggleQuickAction(
                                            settings.quickActions, action
                                        )
                                    )
                                )
                            },
                            label = { Text(shortLabel(action), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaroonPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                if (settings.quickActions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "الترتيب الحالي: " + settings.quickActions.joinToString(" ← ") { shortLabel(it) },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun shortLabel(action: WidgetQuickAction): String = when (action) {
    WidgetQuickAction.FOCUS -> "تركيز"
    WidgetQuickAction.MISTAKES -> "أخطائي"
    WidgetQuickAction.QUESTIONS -> "أسئلة"
    WidgetQuickAction.LIBRARY -> "المكتبة"
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, RoseOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaroonPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaroonPrimary)
        )
    }
}

/** ثوابت شاشة الزجاج الرقمية (أحجام صور المعاينة/المصغّرات،
 * لمعة، وألوان الصبغة كقيم ARGB مسمّاة). */
@Suppress("MagicNumber") // the ARGB literals ARE the glass design tokens (repo pattern: LibraryDesign)
private object GlassSettingsTokens {
    const val PREVIEW_PHOTO_WIDTH: Int = 800
    const val PREVIEW_PHOTO_HEIGHT: Int = 460
    const val PREVIEW_PHOTO_RADIUS: Float = 60f
    const val THUMB_PHOTO_WIDTH: Int = 300
    const val THUMB_PHOTO_HEIGHT: Int = 200
    const val THUMB_PHOTO_RADIUS: Float = 30f
    const val SHEEN_END_Y: Float = 120f
    const val SHEEN_ALPHA: Float = 0.13f
    const val ALPHA_BASE: Float = 255f
    const val MILLIS_PER_SECOND: Long = 1000L
    const val SECONDS_PER_HOUR: Long = 3600L
    const val SECONDS_PER_MINUTE: Long = 60L
    val IVORY_ARGB: Int = 0xFFFAF6F0.toInt()
    val BURGUNDY_DARK_ARGB: Int = 0xFF2E0812.toInt()
    val BURGUNDY_ARGB: Int = 0xFF5E1224.toInt()
    val GRADIENT_TOP_ARGB: Int = 0xFF7A2438.toInt()
    val EDGE_ARGB: Int = 0x73FFEECC.toInt()
    val GOLD_ARGB: Int = 0xFFC2A059.toInt()
    const val BORDER_UNSELECTED_ARGB: Int = 0x33FFFFFF

    /** لون صبغة الزجاج لكل نمط (يطابق WidgetGlass). */
    fun tintArgb(style: WidgetGlassStyle): Int = when (style) {
        WidgetGlassStyle.LIGHT -> IVORY_ARGB
        WidgetGlassStyle.CLEAR -> BURGUNDY_DARK_ARGB
        WidgetGlassStyle.DARK -> BURGUNDY_ARGB
    }
}


/**
 * معاينة الزجاج الحية (1.2.51 — نموذج settings.png): صورة المعلم
 * المختار + صبغة النمط والشفافية الحالية + محتوى عدّاد حقيقي،
 * تتحدث مع كل تغيير بالإعدادات. حبة التسمية أسفلها: «هكذا ستبدو
 * على شاشتك الرئيسية».
 */
@Composable
private fun GlassPreviewCard(settings: WidgetSettings) {
    val context = LocalContext.current
    val photo = remember(settings.bgMode, settings.fixedLandmarkIndex) {
        val index = WidgetBackgroundMath.pickIndex(
            settings, LocalDate.now().toEpochDay(), LandmarkLibrary.all.size
        )
        if (index == null) {
            null
        } else {
            WidgetPhotos.rounded(
                context, LandmarkLibrary.all[index].drawableRes,
                GlassSettingsTokens.PREVIEW_PHOTO_WIDTH,
                GlassSettingsTokens.PREVIEW_PHOTO_HEIGHT,
                GlassSettingsTokens.PREVIEW_PHOTO_RADIUS
            )
        }
    }
    val parts = remember {
        val target = DailyGoalEngine.examTargetOrDefault(context)
        CountdownMath.split(target - System.currentTimeMillis())
    }
    val timeText = remember(parts) {
        val totalSec = (parts.withinDayMillis / GlassSettingsTokens.MILLIS_PER_SECOND).coerceAtLeast(0)
        "%02d:%02d:%02d".format(
            totalSec / GlassSettingsTokens.SECONDS_PER_HOUR,
            (totalSec % GlassSettingsTokens.SECONDS_PER_HOUR) / GlassSettingsTokens.SECONDS_PER_MINUTE,
            totalSec % GlassSettingsTokens.SECONDS_PER_MINUTE
        )
    }
    Column {
        GlassPreviewSurface(settings = settings, photo = photo, days = parts.days, timeText = timeText)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .background(MaroonPrimary)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                "هكذا ستبدو على شاشتك الرئيسية",
                fontSize = 11.sp,
                color = Color.White
            )
        }
    }
}

/** سطح المعاينة نفسه: صورة + صبغة + لمعة + محتوى العدّاد. */
@Composable
private fun GlassPreviewSurface(
    settings: WidgetSettings,
    photo: android.graphics.Bitmap?,
    days: Long,
    timeText: String
) {
    val shape = RoundedCornerShape(20.dp)
    val palette = GlassPalette.forStyle(settings.glassStyle)
    val tintAlpha = WidgetGlassMath.effectiveTintAlpha(
        settings.glassStyle, settings.glassAlphaPercent
    ) / GlassSettingsTokens.ALPHA_BASE
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(settings.previewSize.aspect)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(GlassSettingsTokens.GRADIENT_TOP_ARGB),
                        Color(GlassSettingsTokens.BURGUNDY_DARK_ARGB)
                    )
                )
            )
            .border(1.dp, Color(GlassSettingsTokens.EDGE_ARGB), shape)
    ) {
        if (photo != null) {
            Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // صبغة الزجاج + لمعة علوية كما بالوصفة المعتمدة.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(GlassSettingsTokens.tintArgb(settings.glassStyle)).copy(alpha = tintAlpha))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = GlassSettingsTokens.SHEEN_ALPHA),
                            Color.Transparent
                        ),
                        endY = GlassSettingsTokens.SHEEN_END_Y
                    )
                )
        )
        GlassPreviewContent(palette = palette, days = days, timeText = timeText)
    }
}

/** محتوى العدّاد داخل سطح المعاينة (عنوان + أيام + وقت). */
@Composable
private fun GlassPreviewContent(palette: GlassPalette, days: Long, timeText: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text(
            "العد التنازلي للامتحان الوطني",
            fontFamily = ThmanyahFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color(palette.heading)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$days",
                fontFamily = ThmanyahFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = Color(palette.primary)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "يومًا",
                fontFamily = ThmanyahFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(palette.secondary)
            )
        }
        Text(
            timeText,
            fontFamily = ThmanyahFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(palette.timeAccent)
        )
    }
}

/** مواصفة مصغّرة نمط (قيمة مجمّعة — نمط الريبو للبارامترات). */
private data class GlassThumbSpec(
    val style: WidgetGlassStyle,
    val label: String,
    val photoIndex: Int,
    val alphaPercent: Int
)

/** مصغّرة نمط زجاجي مرسومة فعليًا (صورة + صبغة) — تُختار بحدّ ذهبي. */
@Composable
private fun GlassStyleThumb(
    spec: GlassThumbSpec,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val photo = remember(spec.photoIndex) {
        WidgetPhotos.rounded(
            context, LandmarkLibrary.all[spec.photoIndex].drawableRes,
            GlassSettingsTokens.THUMB_PHOTO_WIDTH,
            GlassSettingsTokens.THUMB_PHOTO_HEIGHT,
            GlassSettingsTokens.THUMB_PHOTO_RADIUS
        )
    }
    val alpha = WidgetGlassMath.effectiveTintAlpha(spec.style, spec.alphaPercent) /
        GlassSettingsTokens.ALPHA_BASE
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(shape)
                .background(Color(GlassSettingsTokens.BURGUNDY_DARK_ARGB))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) Color(GlassSettingsTokens.GOLD_ARGB) else Color(GlassSettingsTokens.BORDER_UNSELECTED_ARGB),
                    shape
                )
                .clickable { onClick() }
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(GlassSettingsTokens.tintArgb(spec.style)).copy(alpha = alpha))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            spec.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                MaroonPrimary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
        )
    }
}
