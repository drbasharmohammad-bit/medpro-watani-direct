package sy.medpro.watani.data

/**
 * منطق ميزات شاشة القفل الصافي (بناء 1.2.30) — بلا أي اعتماد على
 * أندرويد، فيُختبر وحدويًا وتُبنى عليه الإشعارات والخدمات:
 * نصوص بطاقة العدّاد الدائمة، اختيار سؤال اليوم، صياغة شريط
 * جلسة التركيز، حساب التأجيل، وقرار منبّه ملء الشاشة.
 */

/** نصوص بطاقة العدّاد الدائمة على شاشة القفل (الميزة 1). */
object CountdownCardMath {

    /**
     * عنوان البطاقة من عدد الأيام المتبقية على الامتحان (أيام
     * كاملة كما يحسبها [DailyGoalEngine] — صفر يعني يوم الامتحان
     * نفسه، وسالب يعني أن الموعد مرّ).
     */
    fun titleFor(daysLeft: Int): String = when {
        daysLeft < 0 -> "مرّ موعد الامتحان الوطني"
        daysLeft == 0 -> "الامتحان الوطني اليوم — بالتوفيق"
        daysLeft == 1 -> "بقي يوم واحد على الامتحان الوطني"
        daysLeft == 2 -> "بقي يومان على الامتحان الوطني"
        daysLeft in 3..10 -> "بقي $daysLeft أيام على الامتحان الوطني"
        else -> "بقي $daysLeft يومًا على الامتحان الوطني"
    }

    /** جسم البطاقة: تاريخ الامتحان + حال هدف اليوم. */
    fun bodyFor(goal: Int, doneToday: Int, hasBank: Boolean, examDateText: String): String {
        val datePart = "الامتحان: $examDateText"
        val goalPart = when {
            !hasBank -> "حمّل بنوك الأسئلة وابدأ تحضيرك اليومي."
            goal <= 0 -> "حدّد هدفك اليومي من التطبيق ليظهر تقدمك هنا."
            doneToday >= goal -> "أنجزت هدف اليوم كاملًا ($goal سؤالًا) — أحسنت."
            else -> "هدف اليوم: $goal سؤالًا — أنجزت $doneToday حتى الآن."
        }
        return "$datePart • $goalPart"
    }
}

/** اختيار سؤال اليوم (الميزة 2) — نفس صيغة شاشة التحدي اليومي. */
object DailyQuestionPick {

    const val DAY_MS: Long = 1000L * 60 * 60 * 24

    /**
     * فهرس سؤال اليوم داخل مجمّع الأسئلة: رقم اليوم منذ الإيبوك
     * modulo حجم المجمّع (حتمي لليوم نفسه على كل الأسطح). ‎-1‎ عند
     * مجمّع فارغ.
     */
    fun indexFor(nowMillis: Long, poolSize: Int): Int {
        if (poolSize <= 0) return -1
        val day = (nowMillis / DAY_MS).toInt()
        val idx = day % poolSize
        return if (idx < 0) idx + poolSize else idx
    }
}

/** صياغات شريط جلسة التركيز الحي على القفل (الميزة 3). */
object FocusBarMath {

    /** عدّاد «دقائق:ثوانٍ» — الدقائق إجمالية (قد تتجاوز 59). */
    fun formatClock(totalSec: Int): String {
        val safe = totalSec.coerceAtLeast(0)
        return String.format("%02d:%02d", safe / 60, safe % 60)
    }

    /** عنوان الشريط حسب مرحلة الجلسة ومادتها. */
    fun titleFor(phase: FocusPhase, subject: String?): String = when (phase) {
        FocusPhase.FOCUS -> subject?.takeIf { it.isNotBlank() }
            ?.let { "جلسة تركيز — $it" } ?: "جلسة تركيز"
        FocusPhase.BREAK -> "استراحة"
    }

    /** نص زر التبديل بالشريط: إيقاف مؤقت أثناء الجريان، استئناف عند الوقوف. */
    fun toggleLabel(isRunning: Boolean): String = if (isRunning) "إيقاف مؤقت" else "استئناف"

    /** سطر الحالة تحت العنوان عندما لا يجري العدّ (متوقف مؤقتًا). */
    fun pausedLine(timeLeftSec: Int): String = "متبقّي ${formatClock(timeLeftSec)} — متوقف مؤقتًا"
}

/** تأجيل منبّه الدراسة (الميزة 5). */
object SnoozeMath {
    const val SNOOZE_MINUTES: Int = 10
    const val SNOOZE_MS: Long = SNOOZE_MINUTES * 60_000L

    fun triggerMillis(nowMillis: Long): Long = nowMillis + SNOOZE_MS
}

/** قرار سطح منبّه الدراسة (الميزة 5): ملء شاشة أو إشعار عادي. */
object FullScreenAlarmMath {

    /**
     * ملء الشاشة لا يُستخدم إلا إذا فعّل الطالب المنبّه القوي
     * وتذكيرَ الدراسة معًا وكان إذن ملء الشاشة متاحًا فعليًا —
     * وغياب أي شرط يعني التراجع الصامت للإشعار العادي.
     */
    fun useFullScreen(strongEnabled: Boolean, studyEnabled: Boolean, canUseFullScreenIntent: Boolean): Boolean =
        strongEnabled && studyEnabled && canUseFullScreenIntent
}
