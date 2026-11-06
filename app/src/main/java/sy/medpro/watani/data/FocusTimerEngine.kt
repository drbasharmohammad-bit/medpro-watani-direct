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
import java.time.LocalDate

/** حالة محرك مؤقت التركيز المشترك الوحيد بالتطبيق. */
data class FocusTimerState(
    val timeLeftSec: Int = FocusTimerEngine.FULL_SESSION_SEC,
    val isRunning: Boolean = false,
    val sessionsToday: Int = 0
)

/**
 * محرك مؤقت التركيز (بومودورو) — مصدر الحقيقة الوحيد لحالة المؤقت.
 *
 * الحالة كانت تعيش بـremember محلية داخل FocusTimerScreen فتضيع عند
 * مغادرة الشاشة ولا تراها الرئيسية. الآن تعيش هنا بـStateFlow واحد،
 * وحلقة العدّ الوحيدة بالتطبيق هنا أيضًا — ممنوع أي منطق عدّ ثانٍ بأي
 * شاشة. الجلسات المكتملة اليوم تُحفظ بـSharedPreferences وتتصفّر عند
 * تغيّر اليوم.
 */
object FocusTimerEngine {
    const val FULL_SESSION_SEC = 25 * 60

    private const val PREFS_NAME = "watani_focus_timer"
    private const val KEY_SESSIONS_DATE = "sessions_date"
    private const val KEY_SESSIONS_COUNT = "sessions_count"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private var appContext: Context? = null
    private var loadedDate: String? = null

    private val _state = MutableStateFlow(FocusTimerState())
    val state: StateFlow<FocusTimerState> = _state.asStateFlow()

    private fun todayString(): String = LocalDate.now().toString()

    /** تُستدعى من أي قارئ للحالة: تحمل عدد جلسات اليوم (وتُصفّره عند
     * تغيّر اليوم) مرة واحدة لكل يوم، بلا أي عدّ هنا. */
    @Synchronized
    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val today = todayString()
        if (loadedDate == today) return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedDate = prefs.getString(KEY_SESSIONS_DATE, null)
        val count = if (storedDate == today) prefs.getInt(KEY_SESSIONS_COUNT, 0) else 0
        if (storedDate != today) {
            prefs.edit().putString(KEY_SESSIONS_DATE, today).putInt(KEY_SESSIONS_COUNT, 0).apply()
        }
        loadedDate = today
        _state.value = _state.value.copy(sessionsToday = count)
    }

    private fun persistSessions(count: Int) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SESSIONS_DATE, todayString())
            .putInt(KEY_SESSIONS_COUNT, count)
            .apply()
    }

    /** بدء/إيقاف مؤقت — نقطة الدخول الوحيدة لتشغيل العدّ وإيقافه. */
    @Synchronized
    fun toggle(context: Context) {
        init(context)
        if (_state.value.isRunning) {
            tickJob?.cancel()
            tickJob = null
            _state.value = _state.value.copy(isRunning = false)
        } else {
            if (_state.value.timeLeftSec <= 0) {
                _state.value = _state.value.copy(timeLeftSec = FULL_SESSION_SEC)
            }
            _state.value = _state.value.copy(isRunning = true)
            startTickLoop()
        }
    }

    /** إعادة ضبط: إيقاف والرجوع إلى جلسة كاملة، بلا مساس بعدد جلسات اليوم. */
    @Synchronized
    fun reset() {
        tickJob?.cancel()
        tickJob = null
        _state.value = _state.value.copy(timeLeftSec = FULL_SESSION_SEC, isRunning = false)
    }

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                delay(1000L)
                var finished = false
                synchronized(this@FocusTimerEngine) {
                    val current = _state.value
                    if (!current.isRunning) return@launch
                    if (current.timeLeftSec <= 1) {
                        // بلوغ الصفر: جلسة مكتملة — تُحتسب وتُحفظ،
                        // ويتوقف المؤقت ويرجع إلى جلسة كاملة.
                        val nextCount = current.sessionsToday + 1
                        persistSessions(nextCount)
                        _state.value = current.copy(
                            timeLeftSec = FULL_SESSION_SEC,
                            isRunning = false,
                            sessionsToday = nextCount
                        )
                        finished = true
                    } else {
                        _state.value = current.copy(timeLeftSec = current.timeLeftSec - 1)
                    }
                }
                if (finished) return@launch
            }
        }
    }
}
