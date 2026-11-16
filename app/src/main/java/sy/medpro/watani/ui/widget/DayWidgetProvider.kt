package sy.medpro.watani.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * الويدجت المتوسط (1.2.24): عدّاد الامتحان الحي + تقدم أسئلة
 * اليوم مقابل هدف الطالب نفسه + أهدافه النصية لليوم.
 * العرض المشترك في [Widgets].
 */
class DayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_DAY)
    }
}
