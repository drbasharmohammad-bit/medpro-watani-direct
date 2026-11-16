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
