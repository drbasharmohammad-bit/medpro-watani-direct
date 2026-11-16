package sy.medpro.watani.ui.widget

import sy.medpro.watani.R

/**
 * مكتبة خلفيات ويدجت v3: صور معالم سورية معالجة (قصّ مربع
 * مركّز على المعلم، 1200×1200) كموارد drawable — تُضبط
 * بمرجعها في RemoteViews فلا تكلّف الـBinder شيئًا عند أي
 * تحديث. الترتيب هنا هو ترتيب التدوير اليومي الحتمي.
 *
 * المصادر والتراخيص موثّقة في
 * ~/workspace/widget-v3-mockups/src/SOURCES.txt وفي سجل
 * البناء WIDGET_V3.md.
 */
internal data class Landmark(val name: String, val drawableRes: Int)

internal object LandmarkLibrary {

    val all: List<Landmark> = listOf(
        Landmark("الجامع الأموي — دمشق", R.drawable.widget_bg_umayyad),
        Landmark("قلعة حلب", R.drawable.widget_bg_aleppo),
        Landmark("تدمر", R.drawable.widget_bg_palmyra),
        Landmark("نواعير حماة", R.drawable.widget_bg_hama),
        Landmark("قلعة الحصن", R.drawable.widget_bg_krak),
        Landmark("مدرج بصرى", R.drawable.widget_bg_bosra),
        Landmark("دمشق من جبل قاسيون", R.drawable.widget_bg_damascus_qasioun),
        Landmark("قصر العظم — دمشق", R.drawable.widget_bg_azem_palace),
        Landmark("التكية السليمانية — دمشق", R.drawable.widget_bg_takiyya),
        Landmark("باب توما — دمشق القديمة", R.drawable.widget_bg_bab_touma),
        Landmark("قلعة دمشق", R.drawable.widget_bg_damascus_citadel),
        Landmark("أفاميا", R.drawable.widget_bg_apamea),
        Landmark("معلولا", R.drawable.widget_bg_maaloula),
        Landmark("صيدنايا", R.drawable.widget_bg_saidnaya),
        Landmark("قلعة جعبر", R.drawable.widget_bg_jabar_castle),
        Landmark("قلعة المرقب", R.drawable.widget_bg_margat),
        Landmark("قلعة صلاح الدين", R.drawable.widget_bg_saladin_castle),
        Landmark("جزيرة أرواد", R.drawable.widget_bg_arwad),
        Landmark("أوغاريت — رأس شمرا", R.drawable.widget_bg_ugarit),
        Landmark("الجسر المعلق — دير الزور", R.drawable.widget_bg_deir_ezzor_bridge),
        Landmark("أسواق حلب القديمة", R.drawable.widget_bg_aleppo_souk),
        Landmark("كنيسة القديس سمعان", R.drawable.widget_bg_saint_simeon),
        Landmark("سرجيلا — المدن المنسية", R.drawable.widget_bg_serjilla),
        Landmark("دورا أوروبوس", R.drawable.widget_bg_dura_europos),
        Landmark("الرصافة", R.drawable.widget_bg_rusafa),
        Landmark("قلعة شيزر", R.drawable.widget_bg_shaizar),
        Landmark("قلعة مصياف", R.drawable.widget_bg_masyaf),
        Landmark("جامع خالد بن الوليد — حمص", R.drawable.widget_bg_khalid_mosque),
        Landmark("كاتدرائية طرطوس", R.drawable.widget_bg_tartus),
        Landmark("برج صافيتا", R.drawable.widget_bg_safita)
    )
}
