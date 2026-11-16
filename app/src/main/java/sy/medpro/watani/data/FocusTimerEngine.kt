package sy.medpro.watani.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import sy.medpro.watani.notify.FocusSessionService
import sy.medpro.watani.notify.StudyNotifications
import java.time.LocalDate

/** أنماط جلسة التركيز (بناء 1.2.23): كلاسيك 25/5، عميق 50/10، ومخصص. */
enum class FocusMode(val focusMin: Int, val breakMin: Int) {
    CLASSIC(25, 5),
    DEEP(50, 10),
    CUSTOM(25, 5)
}

enum class FocusPhase { FOCUS, BREAK }

enum class FocusEvent { NONE, FOCUS_DONE, BREAK_DONE }

/** حالة محرك مؤقت التركيز المشترك الوحيد بالتطبيق. */
data class FocusTimerState(
    val mode: FocusMode = FocusMode.CLASSIC,
    val phase: FocusPhase = FocusPhase.FOCUS,
    val timeLeftSec: Int = FocusTimerEngine.FULL_SESSION_SEC,
    val phaseTotalSec: Int = FocusTimerEngine.FULL_SESSION_SEC,
    val isRunning: Boolean = false,
    /** مادة الجلسة الحالية/الأخيرة كما اختارها الطالب عند البدء. */
    val subject: String? = null,
    val sessionsToday: Int = 0,
    /** دقائق التركيز المنجزة اليوم / آخر 7 أيام (من سجل المحرك). */
    val minutesToday: Int = 0,
    val minutesWeek: Int = 0,
    /** دقائق اليوم موزعة على المواد (لإحصاء الشاشة الخفيف). */
    val subjectMinutesToday: Map<String, Int> = emptyMap(),
    val keepScreenOn: Boolean = false,
    val customFocusMin: Int = 40,
    val customBreakMin: Int = 8,
    /** آخر حدث اكتمال (لتعرض الشاشة سطرها الختامي) + وقته. */
    val lastEvent: FocusEvent = FocusEvent.NONE,
    val lastEventAt: Long = 0L
) {
    val isIdle: Boolean
        get() = !isRunning && phase == FocusPhase.FOCUS && timeLeftSec == phaseTotalSec
}

/** تجميع إحصاءات التركيز (منطق صافٍ — يُختبر وحدويًا). */
object FocusStatsMath {

    /** مجموع دقائق آخر 7 أيام شاملاً اليوم، من خريطة yyyy-MM-dd ← دقائق. */
    fun weekTotal(minutesByDate: Map<String, Int>, today: LocalDate): Int {
        var total = 0
        for (offset in 0..6) {
            total += minutesByDate[today.minusDays(offset.toLong()).toString()] ?: 0
        }
        return total
    }

    /** إسقاط الأيام الأقدم من نافذة الأسبوع من الخريطة قبل الحفظ. */
    fun pruned(minutesByDate: Map<String, Int>, today: LocalDate): Map<String, Int> {
        val keep = (0..6).map { today.minusDays(it.toLong()).toString() }.toSet()
        return minutesByDate.filterKeys { it in keep }
    }
}

/**
 * محرك مؤقت التركيز (بومودورو) — مصدر الحقيقة الوحيد لحالة المؤقت.
 *
 * الحالة كانت تعيش بـremember محلية داخل FocusTimerScreen فتضيع عند
 * مغادرة الشاشة ولا تراها الرئيسية. الآن تعيش هنا بـStateFlow واحد،
 * وحلقة العدّ الوحيدة بالتطبيق هنا أيضًا — ممنوع أي منطق عدّ ثانٍ
 * بأي شاشة.
 *
 * ترقية 1.2.23: أنماط (كلاسيك/عميق/مخصص)، مرحلتا تركيز واستراحة،
 * وسم مادة عند بدء الجلسة تُسجَّل الجلسة لها، وإحصاءات دقائق محفوظة
 * (اليوم + آخر 7 أيام + توزيع اليوم على المواد). اكتمال التركيز
 * ينشر إشعار نظام عبر StudyNotifications (ضمن إذن الإشعارات) ويرفع
 * حدث FOCUS_DONE لشاشة التركيز. الجلسات المكتملة اليوم تُحفظ
 * بـSharedPreferences وتتصفّر عند تغيّر اليوم كما كانت.
 */
object FocusTimerEngine {
    const val FULL_SESSION_SEC = 25 * 60

    private const val PREFS_NAME = "watani_focus_timer"
    private const val KEY_SESSIONS_DATE = "sessions_date"
    private const val KEY_SESSIONS_COUNT = "sessions_count"
    private const val KEY_MINUTES_JSON = "minutes_by_date_json"
    private const val KEY_SUBJECTS_JSON = "subject_minutes_json"
    private const val KEY_MODE = "mode"
    private const val KEY_CUSTOM_FOCUS = "custom_focus_min"
    private const val KEY_CUSTOM_BREAK = "custom_break_min"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_LAST_SUBJECT = "last_subject"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private var appContext: Context? = null
    private var loadedDate: String? = null
    private var minutesByDate: MutableMap<String, Int> = mutableMapOf()
    private var subjectMinutesToday: MutableMap<String, Int> = mutableMapOf()

    private val _state = MutableStateFlow(FocusTimerState())
    val state: StateFlow<FocusTimerState> = _state.asStateFlow()

    private fun todayString(): String = LocalDate.now().toString()

    private fun focusSec(mode: FocusMode, customFocusMin: Int): Int =
        (if (mode == FocusMode.CUSTOM) customFocusMin else mode.focusMin) * 60

    private fun breakSec(mode: FocusMode, customBreakMin: Int): Int =
        (if (mode == FocusMode.CUSTOM) customBreakMin else mode.breakMin) * 60

    /** تُستدعى من أي قارئ للحالة: تحمل إعدادات المؤقت وإحصاءات اليوم
     * (وتُصفّر عدّادات اليوم عند تغيّره) مرة واحدة لكل يوم. */
    @Synchronized
    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val today = todayString()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val mode = try {
            FocusMode.valueOf(prefs.getString(KEY_MODE, FocusMode.CLASSIC.name) ?: FocusMode.CLASSIC.name)
        } catch (_: Exception) {
            FocusMode.CLASSIC
        }
        val customFocus = prefs.getInt(KEY_CUSTOM_FOCUS, 40).coerceIn(5, 180)
        val customBreak = prefs.getInt(KEY_CUSTOM_BREAK, 8).coerceIn(1, 60)
        val keepOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        val lastSubject = prefs.getString(KEY_LAST_SUBJECT, null)

        if (loadedDate != today) {
            val storedDate = prefs.getString(KEY_SESSIONS_DATE, null)
            val sessions = if (storedDate == today) prefs.getInt(KEY_SESSIONS_COUNT, 0) else 0
            if (storedDate != today) {
                prefs.edit().putString(KEY_SESSIONS_DATE, today).putInt(KEY_SESSIONS_COUNT, 0).apply()
            }
            minutesByDate = parseMinutesMap(prefs.getString(KEY_MINUTES_JSON, null))
            subjectMinutesToday = parseSubjectMap(prefs.getString(KEY_SUBJECTS_JSON, null), today)
            loadedDate = today
            val current = _state.value
            val focusTotal = focusSec(mode, customFocus)
            _state.value = current.copy(
                mode = mode,
                customFocusMin = customFocus,
                customBreakMin = customBreak,
                keepScreenOn = keepOn,
                subject = current.subject ?: lastSubject,
                sessionsToday = sessions,
                minutesToday = minutesByDate[today] ?: 0,
                minutesWeek = FocusStatsMath.weekTotal(minutesByDate, LocalDate.now()),
                subjectMinutesToday = subjectMinutesToday.toMap(),
                // مؤقت خامل يتبع النمط المحفوظ؛ جلسة جارية لا تُلمس.
                timeLeftSec = if (!current.isRunning && current.phase == FocusPhase.FOCUS &&
                    current.timeLeftSec == current.phaseTotalSec
                ) focusTotal else current.timeLeftSec,
                phaseTotalSec = if (!current.isRunning && current.phase == FocusPhase.FOCUS &&
                    current.timeLeftSec == current.phaseTotalSec
                ) focusTotal else current.phaseTotalSec
            )
        } else {
            // نفس اليوم: حدّث الإعدادات الخفيفة فقط إن تغيّرت من شاشة أخرى.
            val current = _state.value
            if (current.mode != mode || current.keepScreenOn != keepOn) {
                _state.value = current.copy(mode = mode, keepScreenOn = keepOn)
            }
        }
    }

    private fun parseMinutesMap(json: String?): MutableMap<String, Int> {
        val out = mutableMapOf<String, Int>()
        if (json.isNullOrBlank()) return out
        try {
            val obj = JSONObject(json)
            obj.keys().forEach { k -> out[k] = obj.optInt(k, 0) }
        } catch (_: Exception) {
        }
        return out
    }

    private fun parseSubjectMap(json: String?, today: String): MutableMap<String, Int> {
        val out = mutableMapOf<String, Int>()
        if (json.isNullOrBlank()) return out
        try {
            val root = JSONObject(json)
            if (root.optString("date", "") != today) return out
            val map = root.optJSONObject("subjects") ?: return out
            map.keys().forEach { k -> out[k] = map.optInt(k, 0) }
        } catch (_: Exception) {
        }
        return out
    }

    private fun persistStats() {
        val ctx = appContext ?: return
        val today = todayString()
        minutesByDate = FocusStatsMath.pruned(minutesByDate, LocalDate.now()).toMutableMap()
        val minutesObj = JSONObject()
        minutesByDate.forEach { (k, v) -> minutesObj.put(k, v) }
        val subjectsObj = JSONObject()
        subjectMinutesToday.forEach { (k, v) -> subjectsObj.put(k, v) }
        val subjectRoot = JSONObject().put("date", today).put("subjects", subjectsObj)
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SESSIONS_DATE, today)
            .putInt(KEY_SESSIONS_COUNT, _state.value.sessionsToday)
            .putString(KEY_MINUTES_JSON, minutesObj.toString())
            .putString(KEY_SUBJECTS_JSON, subjectRoot.toString())
            .apply()
    }

    private fun persistSettings() {
        val ctx = appContext ?: return
        val s = _state.value
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, s.mode.name)
            .putInt(KEY_CUSTOM_FOCUS, s.customFocusMin)
            .putInt(KEY_CUSTOM_BREAK, s.customBreakMin)
            .putBoolean(KEY_KEEP_SCREEN_ON, s.keepScreenOn)
            .putString(KEY_LAST_SUBJECT, s.subject)
            .apply()
    }

    /** تسجيل دقائق تركيز منجزة: لليوم، ولأسبوعه، ولمادة الجلسة. */
    @Synchronized
    private fun recordFocusMinutes(minutes: Int, subject: String?) {
        if (minutes <= 0) return
        val today = todayString()
        if (loadedDate != today) {
            // عبرنا منتصف الليل أثناء الجلسة: ابدأ يومًا جديدًا نظيفًا.
            minutesByDate = FocusStatsMath.pruned(minutesByDate, LocalDate.now()).toMutableMap()
            subjectMinutesToday = mutableMapOf()
            loadedDate = today
            _state.value = _state.value.copy(sessionsToday = 0)
        }
        minutesByDate[today] = (minutesByDate[today] ?: 0) + minutes
        val label = subject?.takeIf { it.isNotBlank() } ?: "دراسة عامة"
        subjectMinutesToday[label] = (subjectMinutesToday[label] ?: 0) + minutes
        _state.value = _state.value.copy(
            minutesToday = minutesByDate[today] ?: 0,
            minutesWeek = FocusStatsMath.weekTotal(minutesByDate, LocalDate.now()),
            subjectMinutesToday = subjectMinutesToday.toMap()
        )
        persistStats()
    }

    // -----------------------------------------------------------------
    // إعدادات النمط
    // -----------------------------------------------------------------

    @Synchronized
    fun setMode(context: Context, mode: FocusMode) {
        init(context)
        val current = _state.value
        if (current.isRunning || current.mode == mode) return
        val total = focusSec(mode, current.customFocusMin)
        _state.value = current.copy(
            mode = mode,
            phase = FocusPhase.FOCUS,
            timeLeftSec = total,
            phaseTotalSec = total,
            isRunning = false
        )
        persistSettings()
    }

    @Synchronized
    fun setCustomDurations(context: Context, focusMin: Int, breakMin: Int) {
        init(context)
        val current = _state.value
        if (current.isRunning) return
        val f = focusMin.coerceIn(5, 180)
        val b = breakMin.coerceIn(1, 60)
        val updated = current.copy(customFocusMin = f, customBreakMin = b)
        _state.value = if (updated.mode == FocusMode.CUSTOM && updated.phase == FocusPhase.FOCUS) {
            val total = f * 60
            updated.copy(timeLeftSec = total, phaseTotalSec = total)
        } else updated
        persistSettings()
    }

    @Synchronized
    fun setKeepScreenOn(context: Context, on: Boolean) {
        init(context)
        _state.value = _state.value.copy(keepScreenOn = on)
        persistSettings()
    }

    // -----------------------------------------------------------------
    // التحكم بالجلسة
    // -----------------------------------------------------------------

    /** بدء جلسة تركيز جديدة بمادة مختارة (من الشاشة الغامرة). */
    @Synchronized
    fun startSession(context: Context, subject: String?) {
        init(context)
        val current = _state.value
        if (current.isRunning) return
        val total = focusSec(current.mode, current.customFocusMin)
        _state.value = current.copy(
            phase = FocusPhase.FOCUS,
            timeLeftSec = total,
            phaseTotalSec = total,
            isRunning = true,
            subject = subject?.takeIf { it.isNotBlank() } ?: current.subject,
            lastEvent = FocusEvent.NONE
        )
        persistSettings()
        startTickLoop()
        // شريط القفل الحي (1.2.30): جلسة بدأت ⇒ الخدمة الأمامية تلحقها إن فعّلها الطالب.
        FocusSessionService.sync(context)
    }

    /** بدء/إيقاف مؤقت — نقطة الدخول الوحيدة لتشغيل العدّ وإيقافه. */
    @Synchronized
    fun toggle(context: Context) {
        init(context)
        val current = _state.value
        if (current.isRunning) {
            tickJob?.cancel()
            tickJob = null
            _state.value = current.copy(isRunning = false)
        } else {
            if (current.timeLeftSec <= 0) {
                val total = focusSec(current.mode, current.customFocusMin)
                _state.value = current.copy(
                    phase = FocusPhase.FOCUS, timeLeftSec = total, phaseTotalSec = total
                )
            }
            _state.value = _state.value.copy(isRunning = true)
            startTickLoop()
        }
        FocusSessionService.sync(context)
    }

    /**
     * إنهاء نظيف بيد الطالب: دقائق التركيز المنقضية فعلًا (إن بلغت
     * دقيقة) تُسجَّل لليوم ولمادة الجلسة، ثم رجوع لخامل كامل. جلسة
     * منتهية مبكرًا لا تُحتسب «جلسة مكتملة» بعدّاد الجلسات.
     */
    @Synchronized
    fun finishSession(context: Context) {
        init(context)
        val current = _state.value
        tickJob?.cancel()
        tickJob = null
        if (current.phase == FocusPhase.FOCUS) {
            val elapsedSec = current.phaseTotalSec - current.timeLeftSec
            if (elapsedSec >= 60) recordFocusMinutes(elapsedSec / 60, current.subject)
        }
        val total = focusSec(current.mode, current.customFocusMin)
        _state.value = _state.value.copy(
            phase = FocusPhase.FOCUS,
            timeLeftSec = total,
            phaseTotalSec = total,
            isRunning = false
        )
        FocusSessionService.sync(context)
    }

    /** تخطي الاستراحة الجارية والعودة لخامل. */
    @Synchronized
    fun skipBreak(context: Context) {
        init(context)
        val current = _state.value
        if (current.phase != FocusPhase.BREAK) return
        tickJob?.cancel()
        tickJob = null
        val total = focusSec(current.mode, current.customFocusMin)
        _state.value = current.copy(
            phase = FocusPhase.FOCUS,
            timeLeftSec = total,
            phaseTotalSec = total,
            isRunning = false
        )
        FocusSessionService.sync(context)
    }

    /** إعادة ضبط: إيقاف والرجوع إلى جلسة كاملة، بلا تسجيل وبلا مساس
     * بعدد جلسات اليوم (السلوك التاريخي). */
    @Synchronized
    fun reset() {
        tickJob?.cancel()
        tickJob = null
        val current = _state.value
        val total = focusSec(current.mode, current.customFocusMin)
        _state.value = current.copy(
            phase = FocusPhase.FOCUS,
            timeLeftSec = total,
            phaseTotalSec = total,
            isRunning = false
        )
        appContext?.let { FocusSessionService.sync(it) }
    }

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                delay(1000L)
                var stopLoop = false
                synchronized(this@FocusTimerEngine) {
                    val current = _state.value
                    if (!current.isRunning) {
                        stopLoop = true
                    } else if (current.timeLeftSec > 1) {
                        _state.value = current.copy(timeLeftSec = current.timeLeftSec - 1)
                    } else {
                        onPhaseCompleteLocked(current)
                        stopLoop = !_state.value.isRunning
                    }
                }
                if (stopLoop) return@launch
            }
        }
    }

    /** انتقال المرحلة عند بلوغ الصفر (يُستدعى داخل القفل). */
    private fun onPhaseCompleteLocked(current: FocusTimerState) {
        val ctx = appContext
        val now = System.currentTimeMillis()
        when (current.phase) {
            FocusPhase.FOCUS -> {
                val focusMinutes = current.phaseTotalSec / 60
                recordFocusMinutes(focusMinutes, current.subject)
                val nextSessions = _state.value.sessionsToday + 1
                val breakTotal = breakSec(current.mode, current.customBreakMin)
                _state.value = _state.value.copy(
                    sessionsToday = nextSessions,
                    phase = FocusPhase.BREAK,
                    timeLeftSec = breakTotal,
                    phaseTotalSec = breakTotal,
                    isRunning = true,
                    lastEvent = FocusEvent.FOCUS_DONE,
                    lastEventAt = now
                )
                persistStats()
                if (ctx != null) {
                    StudyNotifications.postFocusDone(ctx, current.subject)
                    // جلسة اكتملت ⇒ أرقام الهدف/العدّاد تغيّرت: حدّث بطاقة القفل الدائمة.
                    StudyNotifications.refreshCountdownCard(ctx)
                    FocusSessionService.sync(ctx)
                }
            }
            FocusPhase.BREAK -> {
                val total = focusSec(current.mode, current.customFocusMin)
                _state.value = current.copy(
                    phase = FocusPhase.FOCUS,
                    timeLeftSec = total,
                    phaseTotalSec = total,
                    isRunning = false,
                    lastEvent = FocusEvent.BREAK_DONE,
                    lastEventAt = now
                )
                if (ctx != null) {
                    StudyNotifications.postBreakDone(ctx)
                    FocusSessionService.sync(ctx)
                }
            }
        }
    }
}
