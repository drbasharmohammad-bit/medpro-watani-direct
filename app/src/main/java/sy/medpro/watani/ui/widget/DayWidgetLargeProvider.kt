package sy.medpro.watani.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * الويدجت الكبير (1.2.24): كل محتوى المتوسط + أهداف نصية أكثر
 * + دقائق تركيز اليوم، وقابل لتكبير الحجم أفقيًا وعموديًا.
 * العرض المشترك في [Widgets].
 */
class DayWidgetLargeProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateProvider(context, appWidgetManager, appWidgetIds, Widgets.KIND_LARGE)
    }
}
