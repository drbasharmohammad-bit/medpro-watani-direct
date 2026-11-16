package sy.medpro.watani.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * الويدجت الصغير (1.2.24): عدّاد الامتحان فقط — أيام + كرونومتر
 * حي بالساعات والدقائق والثواني يعدّ ذاتيًا في المشغّل.
 * العرض المشترك في [Widgets].
 */
class CountdownWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_COUNTDOWN)
    }
}
