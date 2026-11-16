package sy.medpro.watani.ui.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sy.medpro.watani.data.WidgetQuickAction
import sy.medpro.watani.ui.viewmodel.AppTab

/**
 * توجيه أزرار الويدجت السريعة (بناء 1.2.25 — شرط الدكتور الصريح
 * 13:42: «الأزرار لما يضغط عليها يروح عالمكان ضمن التطبيق مو
 * يروح للتخبيص»).
 *
 * كل زر سريع يحمل بقصديّته (PendingIntent) قيمة توجيه واحدة
 * بهذا الـextra. MainActivity يقرؤها عند الفتح البارد (onCreate)
 * والفتح الدافئ (onNewIntent — النشاط singleTop) ويسلّمها هنا،
 * وواجهة ما بعد الدخول تستهلكها مرة واحدة فتفتح الوجهة:
 *
 * | التوجيه | الوجهة |
 * |---|---|
 * | focus | شاشة «مؤقت التركيز» (FocusTimerScreen) |
 * | mistakes | شاشة «بنك أخطائي» (MistakesBankScreen) |
 * | questions | تبويب «بنك الأسئلة» عبر requestTab ببوابتيه |
 * | library | تبويب «المكتبة» عبر requestTab ببوابتيه |
 * | widget_settings | شاشة «إعدادات الويدجت» |
 * | daily_challenge | شاشة «التحدي اليومي» (1.2.30 — إشعار سؤال اليوم) |
 * | mistakes_review | «بنك أخطائي» مع بدء جلسة المراجعة (1.2.30) |
 * | (فارغ/مجهول) | الرئيسية — فتح عادي بلا تنقّل |
 *
 * جسم الويدجت نفسه بلا توجيه: يفتح التطبيق على الرئيسية كما
 * اعتاد الطالب.
 */

/** وجهة تنقّل محسومة من قيمة توجيه — منطق نقي مختبَر بالوحدات. */
sealed interface WidgetDestination {
    /** شاشة درج باسمها الحرفي في خريطة تنقّل MainActivity. */
    data class Drawer(val title: String) : WidgetDestination

    /** تبويب رئيسي يُفتح حصرًا عبر requestTab (بواباته كاملة). */
    data class Tab(val tab: AppTab) : WidgetDestination

    /** فتح عادي على الرئيسية بلا أي تنقّل. */
    data object Home : WidgetDestination

    /** «بنك أخطائي» مع بدء جلسة المراجعة تلقائيًا (بناء 1.2.30 —
     * وجهة زر «ابدأ المراجعة» في تنبيه الأخطاء المستحقة). */
    data object MistakesReview : WidgetDestination
}

object WidgetRouter {

    const val EXTRA_ROUTE: String = "sy.medpro.watani.widget.ROUTE"

    const val ROUTE_SETTINGS: String = "widget_settings"

    /** إشعار «سؤال اليوم صباحًا» (1.2.30) — يفتح شاشة التحدي اليومي. */
    const val ROUTE_CHALLENGE: String = "daily_challenge"

    /** زر «ابدأ المراجعة» بتنبيه الأخطاء المستحقة (1.2.30). */
    const val ROUTE_MISTAKES_REVIEW: String = "mistakes_review"

    // أسماء شاشات الدرج الحرفية كما بمفاتيح خريطة التنقّل.
    const val DRAWER_FOCUS: String = "مؤقت التركيز"
    const val DRAWER_MISTAKES: String = "بنك أخطائي"
    const val DRAWER_SETTINGS: String = "إعدادات الويدجت"
    const val DRAWER_CHALLENGE: String = "التحدي اليومي"

    private val _pending = MutableStateFlow<String?>(null)

    /** آخر توجيه وارد لم يُستهلك بعد (يُعاد بثّه لمن يركَّب لاحقًا). */
    val pending: StateFlow<String?> = _pending

    /** تسجيل توجيه وارد من قصديّة النشاط (فتح بارد أو دافئ). */
    fun request(route: String?) {
        if (!route.isNullOrBlank()) {
            _pending.value = route
        }
    }

    /** استهلاك التوجيه بعد تطبيقه حتى لا يتكرر عند إعادة التركيب. */
    fun consume() {
        _pending.value = null
    }

    /** تحويل قيمة التوجيه إلى وجهة تنقّل — نقي وحتمي. */
    fun destinationFor(route: String?): WidgetDestination = when (route) {
        WidgetQuickAction.FOCUS.route -> WidgetDestination.Drawer(DRAWER_FOCUS)
        WidgetQuickAction.MISTAKES.route -> WidgetDestination.Drawer(DRAWER_MISTAKES)
        WidgetQuickAction.QUESTIONS.route -> WidgetDestination.Tab(AppTab.BANK)
        WidgetQuickAction.LIBRARY.route -> WidgetDestination.Tab(AppTab.LIBRARY)
        ROUTE_SETTINGS -> WidgetDestination.Drawer(DRAWER_SETTINGS)
        ROUTE_CHALLENGE -> WidgetDestination.Drawer(DRAWER_CHALLENGE)
        ROUTE_MISTAKES_REVIEW -> WidgetDestination.MistakesReview
        else -> WidgetDestination.Home
    }
}
