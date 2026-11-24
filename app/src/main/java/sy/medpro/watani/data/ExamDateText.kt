package sy.medpro.watani.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * صياغة تاريخ الامتحان نصًا عربيًا شرقيًا كما بالنماذج المعتمدة
 * («الثلاثاء 22 كانون الأول 2026»).
 *
 * الجداول ثابتة بالكود عمدًا: أسماء الشهور بصيغ النظام تختلف بين
 * الأجهزة واللغات (ديسمبر/كانون الأول)، والنموذج المعتمد يستخدم
 * الأسماء الشرقية — فالصياغة حتمية هنا على كل جهاز. منطق نقي
 * (java.time فقط) ومختبَر بالوحدات.
 */
object ExamDateText {

    /** كانون الثاني .. كانون الأول (الفهرس = الشهر − 1). */
    val MONTHS: List<String> = listOf(
        "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
        "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"
    )

    /** الاثنين .. الأحد (الفهرس = DayOfWeek.value − 1). */
    val WEEKDAYS: List<String> = listOf(
        "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"
    )

    /** «الثلاثاء 22 كانون الأول 2026» من تاريخ محلي. */
    fun format(date: LocalDate): String {
        val weekday = WEEKDAYS[date.dayOfWeek.value - 1]
        val month = MONTHS[date.monthValue - 1]
        return "$weekday ${date.dayOfMonth} $month ${date.year}"
    }

    /** نفس الصياغة من طابع ميلي ثانية بمنطقة الجهاز الزمنية. */
    fun formatMillis(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        format(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())
}

/**
 * عدّاد الامتحان — نظام أرقام واحد (إصلاح 1.2.51 من صورة الدكتور:
 * خانة «27» لاتينية وباقي الخانات مشرقية بخلافها).
 *
 * كل خانات العدّاد (بلاطة يوم الامتحان + اليوم/الساعة/الدقيقة/
 * الثانية + سطر التاريخ داخل البطاقة نفسها) تمرّ عبر هاتين
 * الدالتين حصرًا، فلا يخرج رقم لاتيني واحد من أي مسار: الأرقام
 * المشرقية (٠١٢٣٤٥٦٧٨٩) هي نمط البطاقة الوحيد. منطق نقي مختبَر
 * بالوحدات.
 */
object CountdownDigits {

    private const val EASTERN = "٠١٢٣٤٥٦٧٨٩"

    /** أي نص: أرقامه اللاتينية تُستبدل بالمشرقية، وباقيه كما هو. */
    fun eastern(text: String): String = buildString(text.length) {
        for (ch in text) {
            append(if (ch in '0'..'9') EASTERN[ch - '0'] else ch)
        }
    }

    /** قيمة خانة عدّاد: مشرقية، ومبطّنة بصفر مشرقي لعرض [pad]. */
    fun unit(value: Long, pad: Int = 0): String {
        val raw = value.toString()
        val padded = if (raw.length >= pad) raw else "0".repeat(pad - raw.length) + raw
        return eastern(padded)
    }
}
