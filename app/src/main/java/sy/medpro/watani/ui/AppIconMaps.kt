package sy.medpro.watani.ui

import sy.medpro.watani.R
import sy.medpro.watani.ui.viewmodel.AppTab

/**
 * Direction-2 icon set (Nov 2026, doctor-approved): custom two-tone
 * (maroon / dusty rose) drawings per specialty, produced as assets in
 * `apk-icons-assets/` (map.json is the matching table) and merged here
 * at 192px in drawable-nodpi. These maps are the ONLY wiring: callers
 * fall back to their previous vector icon whenever a name/title has no
 * entry, so unmapped content renders exactly as before.
 */
object SubjectIcons {
    private val byName: Map<String, Int> = mapOf(
        "الأذنية" to R.drawable.ic_subject_ent,
        "الأطفال 1" to R.drawable.ic_subject_pediatrics_1,
        "الأطفال 2" to R.drawable.ic_subject_pediatrics_2,
        "التوليد" to R.drawable.ic_subject_obstetrics,
        "الجراحة البولية" to R.drawable.ic_subject_urology_surgery,
        "الجراحة التجميلية" to R.drawable.ic_subject_plastic_surgery,
        "الجراحة العامة" to R.drawable.ic_subject_general_surgery,
        "الجراحة العصبية" to R.drawable.ic_subject_neurosurgery,
        "الجراحة العظمية" to R.drawable.ic_subject_orthopedic_surgery,
        "الجلدية" to R.drawable.ic_subject_dermatology,
        "الخمجية" to R.drawable.ic_subject_infectious,
        "الدموية" to R.drawable.ic_subject_hematology,
        "الرثوية" to R.drawable.ic_subject_rheumatology,
        "الصدرية" to R.drawable.ic_subject_pulmonology,
        "الطب النفسي" to R.drawable.ic_subject_psychiatry,
        "العصبية" to R.drawable.ic_subject_neurology,
        "العينية" to R.drawable.ic_subject_ophthalmology,
        "الغدية والسكري" to R.drawable.ic_subject_endocrinology,
        "القلبية" to R.drawable.ic_subject_cardiology,
        "الكلية والشوارد" to R.drawable.ic_subject_nephrology,
        "النسائية" to R.drawable.ic_subject_gynecology,
        "الهضمية والكبد" to R.drawable.ic_subject_gastroenterology,
        "جراحة الأطفال" to R.drawable.ic_subject_pediatric_surgery,
        "جراحة الأورام" to R.drawable.ic_subject_surgical_oncology,
        "جراحة الأوعية" to R.drawable.ic_subject_vascular_surgery,
        "جراحة الصدر" to R.drawable.ic_subject_thoracic_surgery,
        "جراحة القلب" to R.drawable.ic_subject_cardiac_surgery
    )

    fun forName(name: String): Int? = byName[name.trim()]
}

object DrawerIcons {
    private val byTitle: Map<String, Int> = mapOf(
        "الملف الشخصي" to R.drawable.ic_drawer_profile,
        "الاشتراك وتفعيل الحساب" to R.drawable.ic_drawer_activation,
        "المراجعة الذكية Anki" to R.drawable.ic_drawer_anki_review,
        "دليل الدراسة" to R.drawable.ic_drawer_study_guide,
        "سجل أخطائي والاختبارات" to R.drawable.ic_drawer_mistakes_log,
        "المريض الافتراضي" to R.drawable.ic_drawer_virtual_patient,
        "نقاط الضعف" to R.drawable.ic_drawer_weaknesses,
        "مؤقت التركيز" to R.drawable.ic_drawer_focus_timer,
        "الإنجازات والأوسمة" to R.drawable.ic_drawer_achievements,
        "البحث الشامل" to R.drawable.ic_drawer_search,
        "المفضلة" to R.drawable.ic_drawer_favorites,
        "ملاحظاتي" to R.drawable.ic_drawer_my_notes,
        "سجلّ حالاتي" to R.drawable.ic_drawer_case_log,
        "إعدادات وسجل الإشعارات" to R.drawable.ic_drawer_settings,
        "التواصل" to R.drawable.ic_drawer_contact,
        "محطات OSCE" to R.drawable.ic_drawer_osce,
        "القلب والإصغاء" to R.drawable.ic_drawer_heart_auscultation,
        "غرفة الطوارئ" to R.drawable.ic_drawer_emergency_room,
        "المختبرات والحاسبات" to R.drawable.ic_drawer_labs_calculators,
        "تسجيل الخروج" to R.drawable.ic_drawer_logout
    )

    fun forTitle(title: String): Int? = byTitle[title]
}

object TabIcons {
    fun forTab(tab: AppTab): Int = when (tab) {
        AppTab.HOME -> R.drawable.ic_tab_home
        // UNIVERSITIES / UWORLD are bank sub-states shown under the
        // bank tab, never as their own bottom-bar items.
        AppTab.BANK, AppTab.UNIVERSITIES, AppTab.UWORLD -> R.drawable.ic_tab_bank
        AppTab.CASES -> R.drawable.ic_tab_patient
        AppTab.LIBRARY -> R.drawable.ic_tab_library
        AppTab.LEADERBOARD -> R.drawable.ic_tab_leaderboard
        AppTab.TOOLS -> R.drawable.ic_tab_tools
    }
}
