package sy.medpro.watani.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * موفّرو عائلة الزجاج v4 (بناء 1.2.51) — التصاميم التسعة الجديدة.
 * كل موفّر غلاف رفيع حول [Widgets] بنمط الموفّرين القائمين منذ
 * 1.2.25 حرفيًا: التحديث المشترك يبني العرض بحجمه الحقيقي.
 * خريطة التصاميم (جدول تقرير widget-glass-v4):
 * 1 عدّاد 4×2 · 2 جاهزية 4×4 · 3 هدف 2×2 · 4 سلسلة 2×2 ·
 * 5 تابع 4×1 · 6 زوادة اليوم 4×2 · 7 أزرار 4×1 · 8 تركيز 2×2 ·
 * 9 عدّاد مصغّر 2×1.
 *
 * إصلاح زجاج 1.2.52: كل موفّر يعالج الآن تغيّر خيارات المقاس
 * (onAppWidgetOptionsChanged) فيعيد بناء وجهه المخبوز بالمقاس
 * الجديد فورًا — بـ1.2.51 لم يكن أي موفّر يعالجها، فبقي الوجه
 * المخبوز بالمقاس الأول ممدّدًا/مقصوصًا بعد أي تغيير حجم من
 * المشغّل حتى التحديث الدوري التالي.
 */
abstract class GlassWidgetProvider(private val kind: Int) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, kind)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        Widgets.updateProvider(context, appWidgetManager, intArrayOf(appWidgetId), kind)
    }
}

class CountdownWideWidgetProvider : GlassWidgetProvider(Widgets.KIND_COUNTDOWN_WIDE)

class HeroWidgetProvider : GlassWidgetProvider(Widgets.KIND_HERO)

class GoalWidgetProvider : GlassWidgetProvider(Widgets.KIND_GOAL)

class StreakWidgetProvider : GlassWidgetProvider(Widgets.KIND_STREAK)

class ContinueWidgetProvider : GlassWidgetProvider(Widgets.KIND_CONTINUE)

class TipWidgetProvider : GlassWidgetProvider(Widgets.KIND_TIP)

class QuickActionsWidgetProvider : GlassWidgetProvider(Widgets.KIND_ACTIONS)

class FocusWidgetProvider : GlassWidgetProvider(Widgets.KIND_FOCUS)

class MiniCountdownWidgetProvider : GlassWidgetProvider(Widgets.KIND_MINI)
