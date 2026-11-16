package sy.medpro.watani.notify

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sy.medpro.watani.MainActivity
import sy.medpro.watani.R
import sy.medpro.watani.data.FocusBarMath
import sy.medpro.watani.data.FocusPhase
import sy.medpro.watani.data.FocusTimerEngine
import sy.medpro.watani.data.FocusTimerState
import sy.medpro.watani.data.ReminderSettings
import sy.medpro.watani.data.WidgetQuickAction
import sy.medpro.watani.ui.widget.WidgetRouter

/**
 * شريط جلسة التركيز الحي على شاشة القفل (بناء 1.2.30).
 *
 * خدمة أمامية (نوع SpecialUse بتصريح أندرويد 14) تُبقي جلسة
 * البومودورو حية أثناء عملها: إشعار مستمر بعدّاد تنازلي حي
 * (Chronometer يعرضه النظام نفسه، فلا تحديث كل ثانية) وزرّي
 * «إيقاف مؤقت/استئناف» و«إنهاء» يعملان على [FocusTimerEngine]
 * نفسه عبر [FocusActionReceiver] — فلا منطق عدّ ثانيًا أبدًا.
 *
 * دورة الحياة: تبدأ من واجهة التطبيق (بدء/استئناف جلسة) عبر
 * [sync] حين يكون مفتاح الميزة مفعّلًا، وتراقب حالة المحرك:
 * عند خمول الجلسة الكامل توقف نفسها وتزيل الشريط. أثناء التوقف
 * المؤقت تبقى الخدمة قائمة (الشريط يعرض «متوقف مؤقتًا») فلا
 * تحتاج أي إعادة بدء من الخلفية.
 */
class FocusSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastRenderKey: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        StudyNotifications.ensureChannels(this)
        scope.launch {
            FocusTimerEngine.state.collect { state ->
                if (state.isIdle) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val key = renderKey(state)
                    if (key != lastRenderKey) {
                        lastRenderKey = key
                        startForegroundCompat(buildNotification(state))
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = FocusTimerEngine.state.value
        if (state.isIdle) {
            stopSelf()
            return START_NOT_STICKY
        }
        lastRenderKey = renderKey(state)
        startForegroundCompat(buildNotification(state))
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    /** مفتاح إعادة الرسم: لا يُعاد بناء الإشعار مع كل ثانية أثناء
     * الجريان (الكرونومتر يعرض العدّ)، بل عند تغيّر المرحلة أو
     * حالة التشغيل أو المادة — وعند التوقف تهمّ الثواني المتبقية. */
    private fun renderKey(state: FocusTimerState): String {
        val tail = if (state.isRunning) "run" else "paused:${state.timeLeftSec}"
        return "${state.phase}|${state.isRunning}|${state.subject}|$tail"
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                StudyNotifications.ID_FOCUS_LIVE, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(StudyNotifications.ID_FOCUS_LIVE, notification)
        }
    }

    private fun buildNotification(state: FocusTimerState): Notification {
        val builder = NotificationCompat.Builder(this, StudyNotifications.CHANNEL_FOCUS_LIVE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(FocusBarMath.titleFor(state.phase, state.subject))
            .setContentIntent(focusContentIntent())
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, FocusBarMath.toggleLabel(state.isRunning), actionIntent(ACTION_TOGGLE, REQ_TOGGLE))
            .addAction(0, "إنهاء", actionIntent(ACTION_FINISH, REQ_FINISH))
        if (state.isRunning) {
            builder
                .setContentText(
                    if (state.phase == FocusPhase.BREAK) "استرخِ — الاستراحة جزء من الخطة."
                    else "التركيز جارٍ — العدّاد يعمل."
                )
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + state.timeLeftSec * 1000L)
        } else {
            builder
                .setContentText(FocusBarMath.pausedLine(state.timeLeftSec))
                .setUsesChronometer(false)
                .setShowWhen(false)
        }
        return builder.build()
    }

    private fun focusContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(WidgetRouter.EXTRA_ROUTE, WidgetQuickAction.FOCUS.route)
        }
        return PendingIntent.getActivity(
            this, REQ_CONTENT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, FocusActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_TOGGLE = "sy.medpro.watani.action.FOCUS_TOGGLE"
        const val ACTION_FINISH = "sy.medpro.watani.action.FOCUS_FINISH"

        private const val REQ_CONTENT = 8200
        private const val REQ_TOGGLE = 8201
        private const val REQ_FINISH = 8202

        /** هل الخدمة قائمة في هذه العملية؟ (علم محلي — الخدمة
         * والمحرك يعيشان بعملية واحدة دائمًا). */
        @Volatile
        var running: Boolean = false
            private set

        /**
         * مزامنة الخدمة مع الإعدادات وحالة المحرك: تُستدعى بعد كل
         * تحكم بالجلسة ومن شاشة الإعدادات. البدء يحدث فقط حين توجد
         * جلسة نشطة فعلًا والخدمة ليست قائمة — وبدايات الجلسات
         * أصلها من الواجهة (مقدمة التطبيق) فلا بدء من الخلفية.
         * أي فشل هنا صامت عمدًا: الجلسة تعمل داخل التطبيق كما كانت
         * قبل هذه الميزة مهما حدث للشريط.
         */
        fun sync(context: Context) {
            try {
                val appContext = context.applicationContext
                val enabled = ReminderSettings.config(appContext).focusBarEnabled
                val active = !FocusTimerEngine.state.value.isIdle
                val intent = Intent(appContext, FocusSessionService::class.java)
                if (enabled && active) {
                    if (!running) ContextCompat.startForegroundService(appContext, intent)
                } else {
                    appContext.stopService(intent)
                }
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * أزرار شريط التركيز على شاشة القفل: تبديل التشغيل وإنهاء
 * الجلسة على [FocusTimerEngine] نفسه. إجراءات على جلسة خاملة
 * (شريط قديم) تُتجاهل — التبديل على خامل كان سيبدأ جلسة جديدة.
 */
class FocusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        if (FocusTimerEngine.state.value.isIdle) return
        when (intent?.action) {
            FocusSessionService.ACTION_TOGGLE -> FocusTimerEngine.toggle(appContext)
            FocusSessionService.ACTION_FINISH -> FocusTimerEngine.finishSession(appContext)
        }
        FocusSessionService.sync(appContext)
    }
}
