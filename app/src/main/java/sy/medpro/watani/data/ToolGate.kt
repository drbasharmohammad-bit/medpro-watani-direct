package sy.medpro.watani.data

/**
 * Tools activation gate (doctor's order 27 Nov 2026): every tool in
 * the Tools section grants [DEFAULT_FREE_LIMIT] free uses per student
 * (per tool, counted separately), and beyond that it is for activated
 * students only — the same philosophy as the cases gate (10 free).
 * The limit is adjustable from the gateway config (`toolsFreeLimit`)
 * without an app update.
 *
 * What counts as ONE use is defined per tool in [ToolDef.usageKind]:
 * - COMPLETION: a completed result (a finished calculation, a
 *   started focus session, a finished case/station, a completed
 *   review session, a saved logbook entry, a started exam) — opening
 *   the tool without completing consumes nothing.
 * - ENTRY: the tool is a pure reference/lookup, so opening it IS the
 *   completed use.
 *
 * Offline rule (binding): an activated student is never blocked —
 * activation is a persisted local fact, so [decide] takes the local
 * activation state and needs no network. Free counts are local-first
 * and sync through the user doc as a monotonic map (never decreases).
 */
enum class ToolUsageKind { COMPLETION, ENTRY }

data class ToolDef(
    val id: String,
    /** The drawer/tile title this tool routes by. */
    val title: String,
    val usageKind: ToolUsageKind,
    /** Arabic definition of one use, for the build record. */
    val usageDefinition: String
)

object ToolGate {
    const val DEFAULT_FREE_LIMIT = 10

    val TOOLS: List<ToolDef> = listOf(
        ToolDef("ddx_trainer", "مدرّب التشخيص التفريقي", ToolUsageKind.COMPLETION, "بدء حالة تدريبية واحدة (فتحها من قائمة المدرّب)"),
        ToolDef("flip_it", "اقلبها", ToolUsageKind.COMPLETION, "إنهاء جولة لعب واحدة حتى ظهور نتيجتها"),
        ToolDef("german_cases", "الحالات الألمانية", ToolUsageKind.COMPLETION, "إنهاء حالة ألمانية واحدة حتى كشف تشخيصها وشرحها"),
        ToolDef("emergency_room", "غرفة الطوارئ", ToolUsageKind.COMPLETION, "بدء حالة مناوبة إسعافية واحدة"),
        ToolDef("exam_simulator", "محاكاة الامتحان", ToolUsageKind.COMPLETION, "بدء امتحان محاكاة واحد مولّد"),
        ToolDef("osce", "محطات OSCE", ToolUsageKind.COMPLETION, "بدء محطة OSCE واحدة (فتحها من قائمة المحطات)"),
        ToolDef("dosing_trainer", "مدرب الجرعات", ToolUsageKind.COMPLETION, "إنهاء جلسة تدريب جرعات واحدة حتى نتيجتها"),
        ToolDef("ecg_course", "القلب والإصغاء", ToolUsageKind.COMPLETION, "فتح شريط تخطيط/وحدة تدريب واحدة من الكورس"),
        ToolDef("calculators", "الحاسبات الطبية", ToolUsageKind.COMPLETION, "حساب منجز واحد يظهر نتيجته"),
        ToolDef("labs_abg", "المختبرات وغازات الدم", ToolUsageKind.ENTRY, "فتح المرجع (الاطلاع هو الاستخدام المكتمل لمرجع سريع)"),
        ToolDef("algorithms", "خوارزميات التدبير", ToolUsageKind.ENTRY, "فتح المرجع (الاطلاع هو الاستخدام المكتمل لمرجع سريع)"),
        ToolDef("first_lines", "الخطوط العلاجية الأولى", ToolUsageKind.ENTRY, "فتح المرجع (الاطلاع هو الاستخدام المكتمل لمرجع سريع)"),
        ToolDef("syndromes", "موسوعة المتلازمات", ToolUsageKind.ENTRY, "فتح المرجع (الاطلاع هو الاستخدام المكتمل لمرجع سريع)"),
        ToolDef("flashcards", "المراجعة الذكية Anki", ToolUsageKind.COMPLETION, "إنهاء جلسة مراجعة واحدة (بطاقة واحدة على الأقل)"),
        ToolDef("mistakes_bank", "بنك أخطائي", ToolUsageKind.COMPLETION, "إنهاء جلسة مراجعة أخطاء واحدة"),
        ToolDef("study_guide", "دليل الدراسة", ToolUsageKind.ENTRY, "فتح الدليل (الاطلاع هو الاستخدام)"),
        ToolDef("study_planner", "مخطط جدول الدراسة", ToolUsageKind.ENTRY, "فتح المخطط (الاطلاع هو الاستخدام)"),
        ToolDef("history", "سجل أخطائي والاختبارات", ToolUsageKind.ENTRY, "فتح السجل (الاطلاع هو الاستخدام)"),
        ToolDef("logbook", "سجلّ حالاتي", ToolUsageKind.COMPLETION, "حفظ قيد حالة جديد واحد"),
        ToolDef("focus_timer", "مؤقت التركيز", ToolUsageKind.COMPLETION, "بدء جلسة تركيز واحدة")
    )

    fun byTitle(title: String): ToolDef? = TOOLS.firstOrNull { it.title == title }
    fun byId(id: String): ToolDef? = TOOLS.firstOrNull { it.id == id }

    enum class Access { ALLOW, LOCKED }

    data class Decision(val access: Access, val remainingFree: Int)

    /** Pure gate decision. Activated always passes (offline-safe). */
    fun decide(usedCount: Int, freeLimit: Int, activated: Boolean): Decision {
        if (activated) return Decision(Access.ALLOW, Int.MAX_VALUE)
        val remaining = (freeLimit - usedCount).coerceAtLeast(0)
        return Decision(if (remaining > 0) Access.ALLOW else Access.LOCKED, remaining)
    }
}
