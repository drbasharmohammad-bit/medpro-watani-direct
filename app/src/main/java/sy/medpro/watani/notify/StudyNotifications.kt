package sy.medpro.watani.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import sy.medpro.watani.MainActivity
import sy.medpro.watani.R

/**
 * قنوات ونشر إشعارات الدراسة المحلية (بناء 1.2.23).
 *
 * إشعارات نظام حقيقية مجدولة محليًا (تذكير الدراسة، مستحقّات بنك
 * الأخطاء، اكتمال جلسة التركيز) — منفصلة عن دفع FCM الخارجي
 * (WataniMessagingService) وعن أي قائمة داخل التطبيق. لا يُنشر أي
 * إشعار هنا ما لم تكن إشعارات التطبيق مفعّلة من النظام (إذن
 * POST_NOTIFICATIONS على أندرويد 13+).
 */
object StudyNotifications {
    const val CHANNEL_REMINDERS = "watani_reminders"
    const val CHANNEL_REVIEW = "watani_review"
    const val CHANNEL_FOCUS = "watani_focus"

    private const val ID_STUDY = 4101
    private const val ID_MISTAKES = 4102
    private const val ID_FOCUS = 4103
    private const val ID_BREAK = 4104

    /** True when a posted notification can actually reach the student
     * (system-level notifications on + runtime permission on 13+). */
    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context.applicationContext).areNotificationsEnabled()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "تذكيرات الدراسة", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REVIEW, "مراجعة الأخطاء", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS, "مؤقت التركيز", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun post(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (!canPost(context)) return
        // Explicit runtime-permission check before notify() (Android 13+):
        // areNotificationsEnabled() alone does not satisfy the platform's
        // permission contract if the grant state changes between the two.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context.applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context.applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context.applicationContext).notify(notificationId, notification)
    }

    fun postStudyReminder(context: Context, goal: Int, doneToday: Int, hasBank: Boolean) {
        val body = when {
            !hasBank -> "حان وقت جلسة دراستك اليومية. افتح التطبيق وتابع تحضيرك للامتحان الوطني."
            goal <= 0 -> "حان وقت دراستك اليومية — واصل مراجعتك وحافظ على إيقاعك."
            doneToday >= goal -> "أنجزت هدف اليوم كاملًا ($goal سؤالًا). تذكيرك اليومي: حافظ على هذا الإيقاع."
            else -> "هدف اليوم: $goal سؤالًا — أنجزت $doneToday حتى الآن. جلسة قصيرة الآن تُبقي خطتك على المسار."
        }
        post(context, CHANNEL_REMINDERS, ID_STUDY, "تذكير الدراسة اليومي", body)
    }

    fun postMistakesDue(context: Context, dueCount: Int) {
        if (dueCount <= 0) return
        post(
            context, CHANNEL_REVIEW, ID_MISTAKES,
            "مراجعة أخطاء مستحقة",
            "لديك $dueCount سؤالًا من بنك أخطائك حان وقت مراجعته. راجعه الآن قبل أن يتراكم."
        )
    }

    fun postFocusDone(context: Context, subject: String?) {
        val subjectPart = subject?.takeIf { it.isNotBlank() }?.let { " في $it" } ?: ""
        post(
            context, CHANNEL_FOCUS, ID_FOCUS,
            "اكتملت جلسة التركيز",
            "أحسنت — أنهيت جلسة تركيز كاملة$subjectPart. خذ استراحتك، ثم تابع حين تكون جاهزًا."
        )
    }

    fun postBreakDone(context: Context) {
        post(
            context, CHANNEL_FOCUS, ID_BREAK,
            "انتهت الاستراحة",
            "استراحتك انتهت. إن أردت، ابدأ جلسة تركيز جديدة من مؤقت التركيز."
        )
    }
}
