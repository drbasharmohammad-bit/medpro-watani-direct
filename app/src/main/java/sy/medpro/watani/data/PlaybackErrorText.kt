package sy.medpro.watani.data

/**
 * نص خطأ تشغيل الفيديو الودّي (بناء 1.2.31).
 *
 * قبل هذا كان المشغّل يعرض `PlaybackException.message` الخام —
 * نص تقني إنجليزي لا يعني الطالب بشيء، وبلا أي فعل ممكن. الآن
 * تُترجم شيفرة الخطأ (media3 `PlaybackException.errorCode` —
 * القيم أعداد ثابتة موثقة) إلى جملة عربية تصف السبب بفئته:
 * - 2001–2004: شبكة/HTTP (NETWORK_CONNECTION_FAILED/TIMEOUT,
 *   INVALID_HTTP_CONTENT_TYPE, BAD_HTTP_STATUS)
 * - 2005–2008: وصول لملف (FILE_NOT_FOUND, NO_PERMISSION,
 *   CLEARTEXT_NOT_PERMITTED, READ_POSITION_OUT_OF_RANGE)
 * - 3000–3999: فكّ تشفير/محرّك (decoder + audio-track families)
 * - غيرها: جملة عامة.
 *
 * دالة نقية على عدد صحيح — قابلة للاختبار خارج الجهاز.
 */
fun playbackErrorMessage(errorCode: Int): String = when (errorCode) {
    2001, 2002, 2003, 2004 ->
        "تعذّر الاتصال بخادم الفيديو — تحقق من الإنترنت ثم أعد المحاولة."
    2005, 2006, 2007, 2008 ->
        "تعذّر الوصول إلى ملف الفيديو على هذا الجهاز."
    in 3000..3999 ->
        "حدثت مشكلة أثناء فكّ تشفير الفيديو على هذا الجهاز."
    else ->
        "حدث خطأ أثناء تشغيل الفيديو."
}
