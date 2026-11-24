package sy.medpro.watani.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * «زوادة الطالب» repository (build 1.2.51) — two halves:
 *
 * **Published packs (reading).** A subject's pack is a plain content
 * file (`tips/<subject>.json` on the content host, registered in the
 * manifest with a sha256[:16] hash + size). Packs are PUBLIC study
 * content — students' published tips — so they cache as plain files
 * under `filesDir/tips_cache/` (not in the encrypted sections cache,
 * whose family verification does not know the tips family). Rules:
 * - Cache-first: a cached pack renders instantly; opening a subject
 *   whose pack is missing downloads it; a manifest hash that differs
 *   from the cached one triggers a SILENT background refresh.
 * - A missing pack (not yet published for that subject) is a normal
 *   EMPTY section — never an error.
 * - Downloads verify size + sha256[:16] against the manifest entry
 *   when one is available; a failed verification keeps the old copy.
 * - Bank integration: [WataniRepository.downloadSection] calls
 *   [ensurePackForBankDownload] after a national-bank subject shard
 *   lands, so a full subject download brings its tips pack with it.
 *
 * **Outbox (writing).** Per-account SharedPreferences store (the
 * repo's persistence pattern — Logbook/ProfileNameStore; no DataStore
 * exists in this codebase) feeding a [TipsSyncEngine] over the
 * worker gateway. Flush triggers: at submit, at app start
 * ([onAppStart]), on connectivity return (MainActivity's network
 * callback), and whenever the tips screens open.
 */
@Suppress("TooManyFunctions")
object TipsRepository {

    private const val PREF_PACK_HASHES = "watani_tips_packs"
    private const val PREF_CONFIG = "watani_tips_config"
    private const val KEY_WRITING_MODE = "tips_writing"
    private const val OUTBOX_PREF_BASE = "watani_tips_outbox"
    private const val KEY_OUTBOX = "outbox_json"

    private val client: OkHttpClient = sy.medpro.watani.security.NetworkPinning.apply(
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
    ).build()

    // ---------------------------------------------------------------
    // Published packs
    // ---------------------------------------------------------------

    private fun packDir(context: Context): File =
        File(context.filesDir, "tips_cache").apply { if (!exists()) mkdirs() }

    private fun packFile(context: Context, subject: String): File =
        File(packDir(context), subject.trim() + ".json")

    /** The cached pack, synchronously — the screens' first paint. */
    fun cachedPack(context: Context, subject: String): TipsPack? {
        val file = packFile(context, subject)
        if (!file.exists()) return null
        return try {
            TipsPack.parse(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    fun hasCachedPack(context: Context, subject: String): Boolean =
        packFile(context, subject).exists()

    /** Cached tip counts per subject (home list) — cache only, no I/O
     * beyond the small pack files themselves. */
    fun cachedCounts(context: Context): Map<String, Int> =
        TipsContract.SUBJECTS.mapNotNull { subject ->
            cachedPack(context, subject)?.let { subject to it.totalCount }
        }.toMap()

    private fun storedPackHash(context: Context, subject: String): String? =
        context.getSharedPreferences(PREF_PACK_HASHES, Context.MODE_PRIVATE)
            .getString(subject, null)

    private fun storePackHash(context: Context, subject: String, hash: String) {
        context.getSharedPreferences(PREF_PACK_HASHES, Context.MODE_PRIVATE)
            .edit().putString(subject, hash).apply()
    }

    /** sha256 hex, first 16 chars — the manifest's tips-family hash
     * format (the shared contract). */
    fun sha256Prefix16(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    /** Pure freshness verdict: refresh when there is no cached file,
     * or when a manifest hash exists and differs from the stored
     * one. A null entry (offline / pack unpublished) never forces a
     * refresh of an existing cache. */
    fun packNeedsDownload(hasFile: Boolean, storedHash: String?, entry: ManifestFileEntry?): Boolean {
        if (!hasFile) return true
        val manifestHash = entry?.hash?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return !manifestHash.equals(storedHash?.trim(), ignoreCase = true)
    }

    /**
     * Ensure [subject]'s pack is on the device and current against
     * [entry] (the FRESH manifest entry when the caller has one).
     * Downloads at most once per hash change. Returns the pack to
     * display (cached copy on any failure), or null when no pack
     * exists anywhere yet — a normal empty section.
     */
    suspend fun ensurePack(
        context: Context,
        subject: String,
        entry: ManifestFileEntry?
    ): TipsPack? = withContext(Dispatchers.IO) {
        val file = packFile(context, subject)
        val needs = packNeedsDownload(file.exists(), storedPackHash(context, subject), entry)
        if (needs) {
            try {
                val url = WataniApi.BASE_URL + TipsContract.packPath(subject)
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && verifyAgainstEntry(bytes, entry)) {
                            val tmp = File(file.parentFile, file.name + ".tmp")
                            tmp.writeBytes(bytes)
                            tmp.renameTo(file)
                            val hash = entry?.hash?.trim()?.takeIf { it.isNotEmpty() }
                                ?: sha256Prefix16(bytes)
                            storePackHash(context, subject, hash)
                        }
                    }
                }
            } catch (_: Exception) {
                // Offline / host failure: the cached copy (if any) stands.
            }
        }
        cachedPack(context, subject)
    }

    private fun verifyAgainstEntry(bytes: ByteArray, entry: ManifestFileEntry?): Boolean {
        if (entry == null) return bytes.isNotEmpty()
        val expectedSize = entry.size
        if (expectedSize != null && expectedSize > 0 && bytes.size.toLong() != expectedSize) return false
        val expectedHash = entry.hash?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        return sha256Prefix16(bytes).equals(expectedHash, ignoreCase = true)
    }

    /** Bank-download hook: after a national-bank subject shard is
     * downloaded in full, bring its tips pack along (best-effort,
     * silent — a tips failure never touches the bank result). */
    fun ensurePackForBankDownload(context: Context, subject: String, entry: ManifestFileEntry?) {
        if (!TipsContract.isTipsSubject(subject)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ensurePack(context, subject, entry)
            } catch (_: Exception) {
            }
        }
    }

    /** Fresh manifest entry for a subject's pack, via the content
     * repository's manifest (its disk-cached manifest serves
     * offline). Null when unavailable. */
    suspend fun freshPackEntry(context: Context, subject: String): ManifestFileEntry? {
        return try {
            WataniRepository(context.applicationContext).fetchManifest().getOrNull()
                ?.files?.get(TipsContract.packPath(subject))
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------
    // Outbox + sync engine (per account)
    // ---------------------------------------------------------------

    private class SharedPrefsOutboxStore(
        private val context: Context,
        private val uid: String
    ) : TipsOutboxStore {
        private fun prefs() = context.applicationContext
            .getSharedPreferences("${OUTBOX_PREF_BASE}_$uid", Context.MODE_PRIVATE)

        override fun load(): List<OutboxTip> =
            TipsOutboxLogic.deserialize(prefs().getString(KEY_OUTBOX, null))

        override fun save(entries: List<OutboxTip>) {
            prefs().edit().putString(KEY_OUTBOX, TipsOutboxLogic.serialize(entries)).apply()
        }
    }

    @Volatile
    private var engine: TipsSyncEngine? = null

    @Volatile
    private var engineUid: String? = null

    /** The engine for the CURRENT account; switching accounts
     * rebinds the store (another account's outbox is never read). */
    @Synchronized
    fun engine(context: Context): TipsSyncEngine? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        val existing = engine
        if (existing != null && engineUid == user.uid) return existing
        val created = TipsSyncEngine(
            SharedPrefsOutboxStore(context.applicationContext, user.uid),
            WorkerTipsGateway()
        )
        engine = created
        engineUid = user.uid
        return created
    }

    fun mineFlow(context: Context): StateFlow<List<OutboxTip>>? = engine(context)?.mine

    /** App-start / connectivity-return trigger: flush + refresh,
     * fully silent. Safe to call signed-out (no-op). */
    fun onAppStart(context: Context) {
        val eng = engine(context) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                eng.syncNow()
            } catch (_: Exception) {
            }
        }
    }

    fun syncInBackground(context: Context) = onAppStart(context)

    // ---------------------------------------------------------------
    // Writing gate (GET /v1/config → tipsWriting)
    // ---------------------------------------------------------------

    fun cachedWritingMode(context: Context): String? =
        context.getSharedPreferences(PREF_CONFIG, Context.MODE_PRIVATE)
            .getString(KEY_WRITING_MODE, null)

    suspend fun refreshWritingMode(context: Context): String? {
        val mode = try {
            WorkerTipsGateway().fetchTipsWriting()
        } catch (_: Exception) {
            null
        }
        if (mode != null) {
            context.getSharedPreferences(PREF_CONFIG, Context.MODE_PRIVATE)
                .edit().putString(KEY_WRITING_MODE, mode).apply()
        }
        return mode ?: cachedWritingMode(context)
    }

    /** Whether THIS student may write right now (reading is never
     * gated). Uses the cached mode — instant, offline-safe. */
    fun canWrite(context: Context): Boolean =
        tipsWritingAllowed(
            cachedWritingMode(context),
            WorkerActivationApi.isActivated(context)
        )
}
