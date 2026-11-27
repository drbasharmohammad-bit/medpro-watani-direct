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
import sy.medpro.watani.data.WidgetBackgroundMath
import sy.medpro.watani.data.WidgetBgMode
import sy.medpro.watani.data.WidgetCountdownStyle
import sy.medpro.watani.data.WidgetQuickAction
import sy.medpro.watani.data.WidgetSettings
import sy.medpro.watani.data.WidgetSettingsMath
import sy.medpro.watani.data.WidgetSettingsStore
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
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
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
