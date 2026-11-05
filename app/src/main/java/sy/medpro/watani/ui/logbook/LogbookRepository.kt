package sy.medpro.watani.ui.logbook

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import sy.medpro.watani.data.WorkerActivationApi

enum class LogbookSyncState { IDLE, SYNCING, SYNCED, PENDING, AUTH_ERROR }

data class LogbookSyncInfo(
    val state: LogbookSyncState = LogbookSyncState.IDLE,
    val pending: Int = 0,
    val lastSyncAt: Long = 0L
)

/**
 * مستودع سجلّ الحالات الشخصي — Offline-first بثلاث قواعد لا تُكسر:
 *
 * 1. **الحفظ المحلي الفوري أولًا ودائمًا:** كل إنشاء/تعديل/حذف يُكتب لحظيًا
 *    في ملف prefs خاص بالحساب (`watani_logbook_<uid>`، على نمط
 *    OfflineStudyVault وملاحظاتي) قبل أي تفكير بالشبكة. قتل التطبيق أثناء
 *    الكتابة لا يضيّع شيئًا لأن المحرّر يحفظ كل تغيير فور وقوعه.
 * 2. **العزل بالحساب:** المخزن يُفتح بـuid الحالي حصرًا؛ تبديل الحساب يبدّل
 *    المخزن (switchAccount) ولا يمسّ مخزن غيره، والخروج لا يحذف شيئًا —
 *    بيانات كل طالب تبقى تحت uid-ه بجهازه وتعود عند عودته.
 * 3. **المزامنة انتهازية وصامتة:** الدفع/الجلب عبر WorkerActivationApi
 *    برمز Firebase ID (الخادم يستخرج uid من الرمز المُتحقَّق — العميل لا
 *    يرسل uid أبدًا). الفشل لا يحجب ولا ينبّه: يبقى الإدخال «معلّقًا»
 *    (updatedAt > syncedUpdatedAt) ويُعاد دفعه بالمزامنة التالية.
 *
 * حلّ التعارض: الأحدث بـupdatedAt يفوز (LWW) باتجاهي الدفع والجلب؛ خاسر
 * التعارض عند الدفع (applied=false من الخادم) يقبل نسخة الخادم عند الجلب
 * اللاحق في نفس دورة المزامنة — موثّق بقرار تصميم بالوثيقة المرافقة.
 */
object LogbookRepository {

    private const val PREF_BASE = "watani_logbook"
    private const val KEY_ENTRIES = "entries_json"
    private const val KEY_LAST_PULL = "last_pull_ms"

    private var ownerUid: String? = null
    private var loaded = false
    private val entries = mutableListOf<LogbookEntry>()
    private var lastPullMs: Long = 0L

    private val _entriesFlow = MutableStateFlow<List<LogbookEntry>>(emptyList())
    val entriesFlow: StateFlow<List<LogbookEntry>> = _entriesFlow.asStateFlow()

    private val _syncInfo = MutableStateFlow(LogbookSyncInfo())
    val syncInfo: StateFlow<LogbookSyncInfo> = _syncInfo.asStateFlow()

    private val syncMutex = Mutex()

    private fun prefsName(uid: String) = "${PREF_BASE}_$uid"

    /** يفتح مخزن الحساب المطلوب؛ تبديل الحساب يبدّل المخزن بلا حذف أي شيء. */
    @Synchronized
    fun switchAccount(context: Context, uid: String) {
        if (loaded && ownerUid == uid) return
        ownerUid = uid
        entries.clear()
        lastPullMs = 0L
        val prefs = context.applicationContext.getSharedPreferences(prefsName(uid), Context.MODE_PRIVATE)
        lastPullMs = prefs.getLong(KEY_LAST_PULL, 0L)
        val raw = prefs.getString(KEY_ENTRIES, null)
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val e = LogbookEntry.fromLocalJson(arr.getJSONObject(i))
                    if (e.id.isNotBlank()) entries.add(e)
                }
            } catch (_: Exception) {
                // ملف تالف: نبدأ فارغين محليًا؛ النسخة السحابية تعيد البناء عند الجلب.
            }
        }
        loaded = true
        publish()
    }

    @Synchronized
    private fun persist(context: Context) {
        val uid = ownerUid ?: return
        val arr = JSONArray()
        entries.forEach { arr.put(it.toLocalJson()) }
        context.applicationContext.getSharedPreferences(prefsName(uid), Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, arr.toString())
            .putLong(KEY_LAST_PULL, lastPullMs)
            .apply()
    }

    @Synchronized
    private fun publish() {
        _entriesFlow.value = entries.filter { !it.deleted }
            .sortedWith(compareByDescending<LogbookEntry> { it.seenDate }.thenByDescending { it.updatedAt })
        refreshPendingInfo()
    }

    private fun refreshPendingInfo() {
        val pending = entries.count { it.isPendingSync() }
        val current = _syncInfo.value
        _syncInfo.value = current.copy(
            pending = pending,
            state = when {
                current.state == LogbookSyncState.SYNCING || current.state == LogbookSyncState.AUTH_ERROR -> current.state
                pending == 0 && current.lastSyncAt > 0L -> LogbookSyncState.SYNCED
                pending == 0 -> LogbookSyncState.IDLE
                else -> LogbookSyncState.PENDING
            }
        )
    }

    /** إنشاء فوري محفوظ محليًا من قالب اختصاص — قلب «الكتابة السريعة». */
    @Synchronized
    fun createEntry(context: Context, uid: String, specialty: String): LogbookEntry {
        switchAccount(context, uid)
        val now = System.currentTimeMillis()
        val entry = LogbookEntry(createdAt = now, updatedAt = now, seenDate = now, specialty = specialty)
        entries.add(0, entry)
        persist(context)
        publish()
        return entry
    }

    /** حفظ تعديل (يستدعيه المحرّر عند كل تغيير) — كتابة محلية فورية كاملة. */
    @Synchronized
    fun saveEntry(context: Context, uid: String, entry: LogbookEntry) {
        switchAccount(context, uid)
        val idx = entries.indexOfFirst { it.id == entry.id }
        if (idx < 0) return
        entries[idx] = entry
        persist(context)
        publish()
    }

    /** حذف = قبر محلي (لا إزالة) حتى يؤكد الخادم، حتى يصل الحذف لبقية الأجهزة. */
    @Synchronized
    fun deleteEntry(context: Context, uid: String, id: String) {
        switchAccount(context, uid)
        val idx = entries.indexOfFirst { it.id == id }
        if (idx < 0) return
        entries[idx] = entries[idx].copy(deleted = true, updatedAt = System.currentTimeMillis())
        persist(context)
        publish()
    }

    /**
     * رمي محلي نهائي بلا قبر — مشروع حصرًا لإدخال لم يُزامَن قط
     * (syncedUpdatedAt == 0) وتُرك فارغًا: لا وجود له خادميًا ولا بجهاز آخر،
     * فلا شيء يُعلَّم بالحذف. المستدعي (المحرّر) يتحقق من الشرطين.
     */
    @Synchronized
    fun discardLocal(context: Context, uid: String, id: String) {
        switchAccount(context, uid)
        val target = entries.firstOrNull { it.id == id } ?: return
        if (target.syncedUpdatedAt != 0L) return
        entries.removeAll { it.id == id }
        persist(context)
        publish()
    }

    @Synchronized
    private fun findById(id: String): LogbookEntry? = entries.firstOrNull { it.id == id }

    /**
     * دورة مزامنة كاملة: دفع المعلّق ثم جلب التفاضلي منذ آخر جلب.
     * لا ترمي أبدًا؛ نتيجتها تُقرأ من [syncInfo].
     */
    suspend fun syncNow(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            _syncInfo.value = _syncInfo.value.copy(state = LogbookSyncState.AUTH_ERROR)
            return
        }
        if (!syncMutex.tryLock()) return
        try {
            switchAccount(context, user.uid)
            _syncInfo.value = _syncInfo.value.copy(state = LogbookSyncState.SYNCING)

            // ---------- الدفع ----------
            val pendingSnapshot = synchronized(this) { entries.filter { it.isPendingSync() }.sortedBy { it.updatedAt } }
            var authFailed = false
            for (entry in pendingSnapshot) {
                val body = JSONObject().apply {
                    put("id", entry.id)
                    put("updatedAt", entry.updatedAt)
                    if (entry.deleted) put("deleted", true) else put("data", entry.toDataJson())
                }
                val res = WorkerActivationApi.postToWorker(user, "/v1/logbook", body)
                if (res.code == 401 || res.code == -1) { authFailed = true; break }
                if (res.code != 200) continue // شبكة/خادم: يبقى معلّقًا لدورة قادمة
                val ok = try { JSONObject(res.body ?: "").optBoolean("ok", false) } catch (_: Exception) { false }
                if (!ok) continue
                val applied = try { JSONObject(res.body ?: "").optBoolean("applied", true) } catch (_: Exception) { true }
                synchronized(this) {
                    val current = findById(entry.id)
                    if (current != null && current.updatedAt == entry.updatedAt) {
                        if (applied && entry.deleted) {
                            entries.removeAll { it.id == entry.id } // الحذف تأكد: يُزال القبر
                        } else {
                            // applied: صار متزامنًا. !applied: خسر تعارض LWW — نعلّمه
                            // مدفوعًا حتى لا يدور للأبد، والجلب أدناه يفرض نسخة الخادم.
                            val idx = entries.indexOfFirst { it.id == entry.id }
                            if (idx >= 0) entries[idx] = current.copy(syncedUpdatedAt = entry.updatedAt)
                        }
                        persist(context)
                    }
                }
            }

            // ---------- الجلب التفاضلي ----------
            if (!authFailed) {
                var loops = 0
                while (loops < 10) {
                    loops++
                    val since = synchronized(this) { lastPullMs }
                    val res = WorkerActivationApi.getFromWorker(user, "/v1/logbook?since=$since&limit=200")
                    if (res.code == 401 || res.code == -1) { authFailed = true; break }
                    if (res.code != 200) break
                    val root = try { JSONObject(res.body ?: "") } catch (_: Exception) { break }
                    if (!root.optBoolean("ok", false)) break
                    val arr = root.optJSONArray("entries") ?: break
                    synchronized(this) {
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val id = item.optString("id", "")
                            if (id.isBlank()) continue
                            val remoteUpdated = item.optLong("updatedAt", 0L)
                            if (remoteUpdated > lastPullMs) lastPullMs = remoteUpdated
                            val remoteDeleted = item.optBoolean("deleted", false)
                            val local = findById(id)
                            if (remoteDeleted) {
                                // قبر وارد: يزيل المحلي ما لم يحمل تعديلًا أحدث منه.
                                if (local != null && local.updatedAt <= remoteUpdated) {
                                    entries.removeAll { it.id == id }
                                }
                                continue
                            }
                            val data = item.optJSONObject("data") ?: continue
                            val remote = LogbookEntry.fromDataJson(
                                id = id, data = data, updatedAt = remoteUpdated,
                                deleted = false, syncedUpdatedAt = remoteUpdated
                            )
                            when {
                                local == null -> entries.add(remote)
                                local.deleted -> {
                                    // قبرنا المحلي أقدم من نسخة الخادم: الحذف خسر، تُستعاد النسخة.
                                    if (local.updatedAt < remoteUpdated) {
                                        val idx = entries.indexOfFirst { it.id == id }
                                        if (idx >= 0) entries[idx] = remote
                                    }
                                }
                                remoteUpdated > local.updatedAt -> {
                                    val idx = entries.indexOfFirst { it.id == id }
                                    if (idx >= 0) entries[idx] = remote
                                }
                                // المحلي أحدث أو مساوٍ: يبقى، ودفعه (إن كان معلّقًا) يحسمه.
                            }
                        }
                        persist(context)
                        publish()
                    }
                    if (arr.length() < 200) break
                }
            }

            synchronized(this) { publish() }
            _syncInfo.value = _syncInfo.value.copy(
                state = if (authFailed) LogbookSyncState.AUTH_ERROR else _syncInfo.value.state,
                lastSyncAt = if (authFailed) _syncInfo.value.lastSyncAt else System.currentTimeMillis()
            )
            refreshPendingInfo()
            if (authFailed) _syncInfo.value = _syncInfo.value.copy(state = LogbookSyncState.AUTH_ERROR)
        } finally {
            syncMutex.unlock()
        }
    }
}
