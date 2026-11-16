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
import sy.medpro.watani.data.CountdownCardMath
import sy.medpro.watani.data.DailyGoalEngine
import sy.medpro.watani.data.ExamDateText
import sy.medpro.watani.data.FullScreenAlarmMath
import sy.medpro.watani.data.ReminderSettings
import sy.medpro.watani.ui.notify.StudyAlarmActivity
import sy.medpro.watani.ui.widget.WidgetRouter

/**
 * قنوات ونشر إشعارات الدراسة المحلية (بناء 1.2.23).
 *
 * إشعارات نظام حقيقية مجدولة محليًا (تذكير الدراسة، مستحقّات بنك
 * الأخطاء، اكتمال جلسة التركيز) — منفصلة عن دفع FCM الخارجي
 * (WataniMessagingService) وعن أي قائمة داخل التطبيق. لا يُنشر أي
 * إشعار هنا ما لم تكن إشعارات التطبيق مفعّلة من النظام (إذن
 * POST_NOTIFICATIONS على أندرويد 13+).
 *
 * توسيع 1.2.30 (ميزات شاشة القفل): بطاقة عدّاد دائمة صامتة،
 * إشعار سؤال اليوم بربط عميق لشاشة التحدي، زر «ابدأ المراجعة»
 * بتنبيه الأخطاء بربط عميق لجلسة المراجعة، ومنبّه دراسة بملء
 * الشاشة يتراجع للإشعار العادي عند غياب إذنه.
 */
object StudyNotifications {
    const val CHANNEL_REMINDERS = "watani_reminders"
    const val CHANNEL_REVIEW = "watani_review"
    const val CHANNEL_FOCUS = "watani_focus"
    const val CHANNEL_COUNTDOWN = "watani_countdown"
    const val CHANNEL_FOCUS_LIVE = "watani_focus_live"
    const val CHANNEL_ALARM = "watani_alarm"

    private const val ID_STUDY = 4101
    private const val ID_MISTAKES = 4102
    private const val ID_FOCUS = 4103
    private const val ID_BREAK = 4104
    const val ID_FOCUS_LIVE = 4105
    private const val ID_COUNTDOWN = 4106
    private const val ID_QUESTION = 4107

    private const val REQ_CHALLENGE = 8101
    private const val REQ_MISTAKES_REVIEW = 8102
    private const val REQ_ALARM = 8103

    /** True when a posted notification can actually reach the student
     * (system-level notifications on + runtime permission on 13+). */
    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context.applicationContext).areNotificationsEnabled()

    /** بوابة النشر الكاملة: إشعارات مفعّلة من النظام + إذن التشغيل
     * الصريح على أندرويد 13+ (فحصان منفصلان بقدْر ما يتطلبه النظام). */
    private fun canPostNow(context: Context): Boolean {
        if (!canPost(context)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context.applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return true
    }

    /**
     * إذن «إشعارات ملء الشاشة» متاح فعليًا؟ قبل أندرويد 14 كان
     * يُمنح بالتصريح في المانيفست؛ من أندرويد 14 صار قرارًا بيد
     * الطالب من إعدادات النظام ويفحصه النظام هنا.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.canUseFullScreenIntent()
    }

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
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_COUNTDOWN, "بطاقة العدّاد", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS_LIVE, "جلسة التركيز الحية", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "منبّه الدراسة", NotificationManager.IMPORTANCE_HIGH)
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

    /** قصديّة نشاط تحمل توجيهًا داخليًا (نفس عقد WidgetRouter الذي
     * تستهلكه MainActivity مرة واحدة بعد الدخول). */
    private fun routedActivityIntent(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(WidgetRouter.EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
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
        if (!canPostNow(context)) return
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

    private fun studyReminderBody(goal: Int, doneToday: Int, hasBank: Boolean): String = when {
        !hasBank -> "حان وقت جلسة دراستك اليومية. افتح التطبيق وتابع تحضيرك للامتحان الوطني."
        goal <= 0 -> "حان وقت دراستك اليومية — واصل مراجعتك وحافظ على إيقاعك."
        doneToday >= goal -> "أنجزت هدف اليوم كاملًا ($goal سؤالًا). تذكيرك اليومي: حافظ على هذا الإيقاع."
        else -> "هدف اليوم: $goal سؤالًا — أنجزت $doneToday حتى الآن. جلسة قصيرة الآن تُبقي خطتك على المسار."
    }

    fun postStudyReminder(context: Context, goal: Int, doneToday: Int, hasBank: Boolean) {
        post(context, CHANNEL_REMINDERS, ID_STUDY, "تذكير الدراسة اليومي", studyReminderBody(goal, doneToday, hasBank))
    }

    /**
     * نشر تذكير الدراسة بالسطح الذي اختاره الطالب: صفحة منبّه
     * بملء الشاشة إن فعّل المنبّه القوي وكان إذنه متاحًا، وإلا
     * الإشعار العادي نفسه (قرار [FullScreenAlarmMath] الصافي).
     */
    fun postStudyReminderSmart(context: Context, goal: Int, doneToday: Int, hasBank: Boolean) {
        val config = ReminderSettings.config(context)
        if (FullScreenAlarmMath.useFullScreen(
                strongEnabled = config.strongAlarmEnabled,
                studyEnabled = config.studyEnabled,
                canUseFullScreenIntent = canUseFullScreenIntent(context)
            )
        ) {
            postStudyAlarmFullScreen(context, goal, doneToday, hasBank)
        } else {
            postStudyReminder(context, goal, doneToday, hasBank)
        }
    }

    /** إشعار عالٍ بملء الشاشة يفتح [StudyAlarmActivity] فوق القفل. */
    private fun postStudyAlarmFullScreen(context: Context, goal: Int, doneToday: Int, hasBank: Boolean) {
        if (!canPostNow(context)) return
        ensureChannels(context)
        val body = studyReminderBody(goal, doneToday, hasBank)
        val alarmIntent = Intent(context.applicationContext, StudyAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, REQ_ALARM, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context.applicationContext, CHANNEL_ALARM)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("حان وقت الدراسة")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context.applicationContext).notify(ID_STUDY, notification)
    }

    /**
     * تنبيه الأخطاء المستحقة (1.2.30): بوقته المستقل الآن، وبزر
     * «ابدأ المراجعة» — الزر والنقر على الإشعار معًا يفتحان
     * «بنك أخطائي» ويبدآن جلسة المراجعة مباشرة عبر التوجيه.
     */
    fun postMistakesDue(context: Context, dueCount: Int) {
        if (dueCount <= 0) return
        if (!canPostNow(context)) return
        ensureChannels(context)
        val body = "لديك $dueCount سؤالًا من بنك أخطائك حان وقت مراجعته. راجعه الآن قبل أن يتراكم."
        val reviewIntent = routedActivityIntent(context, WidgetRouter.ROUTE_MISTAKES_REVIEW, REQ_MISTAKES_REVIEW)
        val notification = NotificationCompat.Builder(context.applicationContext, CHANNEL_REVIEW)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("مراجعة أخطاء مستحقة")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(reviewIntent)
            .addAction(0, "ابدأ المراجعة", reviewIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context.applicationContext).notify(ID_MISTAKES, notification)
    }

    /** سؤال اليوم صباحًا (1.2.30): نص السؤال ومادته، والنقر يفتح شاشة التحدي اليومي نفسها. */
    fun postDailyQuestion(context: Context, subject: String, questionText: String) {
        if (!canPostNow(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context.applicationContext, CHANNEL_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("سؤال اليوم — $subject")
            .setContentText(questionText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(questionText))
            .setContentIntent(routedActivityIntent(context, WidgetRouter.ROUTE_CHALLENGE, REQ_CHALLENGE))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context.applicationContext).notify(ID_QUESTION, notification)
    }

    /**
     * بطاقة العدّاد الدائمة (1.2.30): إشعار مستمر صامت بقناة
     * منخفضة — عدّاد الامتحان وشريط تقدم هدف اليوم من لقطة
     * [DailyGoalEngine] نفسها التي تغذي الويدجت، فلا يختلفان أبدًا.
     */
    fun postCountdownCard(context: Context) {
        if (!canPostNow(context)) return
        ensureChannels(context)
        val snapshot = DailyGoalEngine.snapshot(context)
        val target = snapshot.examTargetMillis ?: DailyGoalEngine.defaultExamTargetMillis()
        val title = CountdownCardMath.titleFor(snapshot.daysLeft)
        val body = CountdownCardMath.bodyFor(
            goal = snapshot.goal,
            doneToday = snapshot.doneToday,
            hasBank = snapshot.hasBank,
            examDateText = ExamDateText.formatMillis(target)
        )
        val builder = NotificationCompat.Builder(context.applicationContext, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (snapshot.goal > 0) {
            builder.setProgress(snapshot.goal, snapshot.doneToday.coerceIn(0, snapshot.goal), false)
        }
        NotificationManagerCompat.from(context.applicationContext)
            .notify(ID_COUNTDOWN, builder.build())
    }

    fun cancelCountdownCard(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(ID_COUNTDOWN)
    }

    /** تحديث البطاقة من الإعدادات الحالية: تُنشر إن كانت مفعّلة وتُلغى إن عُطّلت. */
    fun refreshCountdownCard(context: Context) {
        if (ReminderSettings.config(context).countdownCardEnabled) {
            postCountdownCard(context)
        } else {
            cancelCountdownCard(context)
        }
    }

    /** إزالة إشعار تذكير الدراسة (تستدعيه صفحة المنبّه بملء الشاشة عند أي زر فيها). */
    fun dismissStudyReminder(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(ID_STUDY)
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
