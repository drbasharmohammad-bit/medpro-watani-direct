package sy.medpro.watani.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * موفّرو عائلة الزجاج v4 (بناء 1.2.51) — التصاميم التسعة الجديدة.
 * كل موفّر غلاف رفيع حول [Widgets] بنمط الموفّرين القائمين منذ
 * 1.2.25 حرفيًا: التحديث المشترك يبني العرض بحجمه الحقيقي.
 * خريطة التصاميم (جدول تقرير widget-glass-v4):
 * 1 عدّاد 4×2 · 2 جاهزية 4×4 · 3 هدف 2×2 · 4 سلسلة 2×2 ·
 * 5 تابع 4×1 · 6 زوادة اليوم 4×2 · 7 أزرار 4×1 · 8 تركيز 2×2 ·
 * 9 عدّاد مصغّر 2×1.
 */
class CountdownWideWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_COUNTDOWN_WIDE)
    }
}

class HeroWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_HERO)
    }
}

class GoalWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_GOAL)
    }
}

class StreakWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_STREAK)
    }
}

class ContinueWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_CONTINUE)
    }
}

class TipWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_TIP)
    }
}

class QuickActionsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_ACTIONS)
    }
}

class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_FOCUS)
    }
}

class MiniCountdownWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_MINI)
    }
}
