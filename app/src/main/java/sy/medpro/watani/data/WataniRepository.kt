package sy.medpro.watani.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets

class WataniRepository(private val context: Context) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // A2: Remove body-level HttpLoggingInterceptor from shared OkHttp client (Level.NONE)
    // Phase-2 hardening: SPKI pinning for the content/gateway hosts.
    private val client = sy.medpro.watani.security.NetworkPinning.apply(
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // Cache audit fix F2 (1.2.45): an OVERALL call deadline.
            // Connect/read timeouts alone let a connection that
            // trickles one byte at a time hang a call for minutes
            // (the First Aid unit first-open spinner the doctor
            // photographed) — the cap turns any such hang into a
            // settled, retryable failure. Section downloads resume
            // via Range, so a capped call never loses real progress.
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            })
    ).build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(WataniApi.BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api = retrofit.create(WataniApi::class.java)

    private val prefs = context.getSharedPreferences("watani_downloaded_sections", Context.MODE_PRIVATE)
    private val _downloadedSectionsFlow = MutableStateFlow<List<DownloadedSectionEntity>>(loadDownloadedSections())
    val downloadedSectionsFlow: Flow<List<DownloadedSectionEntity>> = _downloadedSectionsFlow.asStateFlow()

    // Parsed-envelope cache (1.2.27): getSubjectEnvelope used to
    // re-read, re-decrypt and re-parse the whole shard on EVERY
    // call — entering «دورة كاملة» swept all 27 shards twice
    // (~110MB of JSON) before the quiz could start. Entries are
    // stamped with the registry hash of the bytes they were parsed
    // from, so a re-download (new hash) invalidates automatically;
    // saveDownloadedSection also evicts explicitly.
    private val subjectEnvelopeCache = EnvelopeCache<SubjectEnvelope>()

    // Parsed years-envelope cache (cache audit fix F5, 1.2.45):
    // getYearsEnvelope used to re-read, re-decrypt and re-parse the
    // whole years file on EVERY call — every university subject
    // open and every year-quiz open paid the full parse of a file
    // up to megabytes. Same registry-hash stamping as the subject
    // cache: a re-download swaps the registry hash and the stale
    // parse can never be served.
    private val yearsEnvelopeCache = EnvelopeCache<YearsEnvelope>()

    // Parsed standalone-dawra records cache (cache audit fix F5,
    // same shape as the years cache): getDawraSessionRecords
    // re-parsed the whole session file per call.
    private val dawraRecordsCache = EnvelopeCache<List<DawraSessionQuestionDto>>()

    // Parsed-concepts cache (1.2.31): getConcepts used to re-read,
    // re-decrypt and re-parse the whole pack on EVERY call, and the
    // concept-unit screen calls it on every entry — returning from
    // a bank question re-parsed ~5MB before the page could render.
    // Same registry-hash guard as the envelope cache.
    // Audit fix (21 Nov 2026, item Z / N+1 sweep N1-01): the cache
    // used to be an INSTANCE field — but the concepts screens build
    // a brand-new WataniRepository on every composition effect, so
    // the cache was empty on every Library entry and the whole
    // 11.9MB pack was re-read, re-decrypted and re-parsed before
    // the «ادرس مع ميد برو» card could render, while the videos
    // card (held in LibraryViewModel state) appeared instantly.
    // The cache now lives in the companion object (process-wide,
    // EnvelopeCache is fully synchronized), so the pack is parsed
    // ONCE per process and served from memory everywhere after.
    private val conceptsEnvelopeCache get() = sharedConceptsEnvelopeCache

    // Question-id index (1.2.31): qid -> section path of its FIRST
    // occurrence in registry order (topics before sessions inside
    // an envelope — the exact scan order the old findQuestionById
    // used). Built lazily from the parsed envelopes (which the
    // build itself caches), then guarded by a fingerprint of the
    // downloaded-sections registry: any download/wipe changes the
    // fingerprint and the index rebuilds. Before this index, every
    // lookup rescanned — and on a cold envelope cache re-parsed —
    // every downloaded shard from the top, once per self-test
    // question on the unit screen and again on the open click:
    // the «اذهب إلى مصدر السؤال يطول» freeze.
    @Volatile
    private var questionIdIndex: Map<String, String>? = null

    @Volatile
    private var questionIdIndexFingerprint: String? = null

    private fun loadDownloadedSections(): List<DownloadedSectionEntity> {
        val allEntries = prefs.all
        val list = mutableListOf<DownloadedSectionEntity>()
        for ((path, value) in allEntries) {
            if (value is String) {
                val parts = value.split("|")
                if (parts.size >= 4) {
                    list.add(
                        DownloadedSectionEntity(
                            sectionPath = path,
                            version = parts[0],
                            hash = parts[1],
                            count = parts[2].toIntOrNull() ?: 0,
                            downloadedAt = parts[3].toLongOrNull() ?: 0L
                        )
                    )
                }
            }
        }
        return list
    }

    private fun saveDownloadedSection(entity: DownloadedSectionEntity) {
        val value = "${entity.version}|${entity.hash}|${entity.count}|${entity.downloadedAt}"
        prefs.edit().putString(entity.sectionPath, value).apply()
        _downloadedSectionsFlow.value = loadDownloadedSections()
        // Fresh bytes on disk: any envelope parsed from the previous
        // download of this section is stale (1.2.27 cache).
        subjectEnvelopeCache.evict(entity.sectionPath)
        yearsEnvelopeCache.evict(entity.sectionPath)
        dawraRecordsCache.evict(entity.sectionPath)
        conceptsEnvelopeCache.evict(entity.sectionPath)
        // The registry fingerprint guard would catch this anyway;
        // dropping eagerly keeps a stale id->path map from ever
        // being served between the save and the next lookup.
        questionIdIndex = null
        questionIdIndexFingerprint = null
    }

    /** Reloads the downloaded-sections flow from prefs (after an
     * external wipe, e.g. sign-out or deactivation from another screen). */
    fun refreshDownloadedSections() {
        _downloadedSectionsFlow.value = loadDownloadedSections()
        // An external wipe may have removed section files behind
        // the registry's back — never serve a parsed envelope for
        // content whose on-disk state was just reset (1.2.27).
        subjectEnvelopeCache.clear()
        conceptsEnvelopeCache.clear()
        questionIdIndex = null
        questionIdIndexFingerprint = null
    }

    /**
     * Gateway 401/403 handler (M3/M4): clears the activation, wipes
     * restricted downloads and returns the typed revocation failure.
     * Callers must surface [ActivationRevokedException] unwrapped (the
     * MainActivity prompt event fires from [WorkerActivationApi] itself).
     */
    private fun gatewayAuthFailure(context: Context): ActivationRevokedException {
        WorkerActivationApi.notifyActivationLost(context)
        refreshDownloadedSections()
        return ActivationRevokedException("انتهى التفعيل على هذا الجهاز — أعد إدخال كود التفعيل.")
    }

    /**
     * Offline-activation fix (Nov 2026, doctor's order): the manifest is
     * a FREE path (fetched directly from Pages, never through the
     * gateway), so a copy is cached on disk. An activated account must
     * keep seeing its sections offline — without a cache, every cold
     * start offline failed the fetch and HomeScreen showed only a
     * "connection error" wall even though the sections were downloaded.
     * On any fetch failure the cached manifest is served instead; only
     * when there is no cache at all does the failure reach the UI (and
     * MainViewModel then falls back to the downloaded-sections registry).
     * The cache never grants access by itself: restricted tabs and quiz
     * entry still re-check the local activation, and restricted fetches
     * still go through the gateway.
     */
    private fun manifestCacheFile(): File = File(context.filesDir, MANIFEST_CACHE_FILENAME)

    companion object {
        /** Disk file of the manifest cache (see manifestCacheFile()).
         * Deleted by [RestrictedInventory.wipeRestrictedDownloads] on a
         * proven revocation: a stale copy would keep listing restricted
         * sections after the activation ended. */
        const val MANIFEST_CACHE_FILENAME = "manifest_cache.json"

        /** Overall deadline of one HTTP call on the shared client
         * (cache audit fix F2, 1.2.45 — see the client builder). */
        private const val CALL_TIMEOUT_SECONDS = 120L

        /** Each half of a pair download's progress range (F4). */
        private const val PAIR_PROGRESS_HALF = 0.5f

        /** Process-wide parsed-concepts cache (see the field note in
         * the class body — audit item Z, 21 Nov 2026). */
        private val sharedConceptsEnvelopeCache = EnvelopeCache<ConceptsEnvelope>()

        /** Prefs file of the persisted session-count index
         * (1.2.48, performance item 3 — see [sessionCountsIndexEntry]). */
        const val SESSION_COUNTS_PREFS = "watani_session_counts_index"

        /** Process-wide parsed-pack cache (1.2.48, performance list
         * item 1 — search indexes instead of full scans): the small
         * section packs (cases, ddx, osce, ecg, library…) used to be
         * re-read, re-decrypted and re-parsed on EVERY getter call,
         * and screens build a fresh repository per effect and call a
         * pack getter up to three times per entry. Same idiom as the
         * First Aid cache: entries are stamped with the section
         * file's own (mtime, length), so a re-download rewrites the
         * file and the stale parse drops itself. */
        private val sharedPackEnvelopeCache = EnvelopeCache<Any>()

        // ── First Aid load-path repair (1.2.43, doctor's device
        // report: section jank + «الواجهة الثابتة تكون عند الطالب من
        // تحميل التطبيق») ──────────────────────────────────────────
        // Before 1.2.43 every First Aid getter AWAITED a full manifest
        // network fetch inline before returning even a warm cached
        // copy (refreshSectionIfStale → fetchManifest, no TTL), the
        // section host awaited four such loads sequentially behind a
        // spinner, tab switches destroyed the composition and repeated
        // the whole chain, and nothing was parsed-cached in memory.
        // The pieces below implement the mandated shape: cache-first
        // with INSTANT return, refresh in the background (throttled),
        // a process-wide parsed cache stamped by the cache file's own
        // (mtime, length) so a refreshed file invalidates itself, a
        // 60-second TTL on the manifest used by refresh checks, and
        // bundled STRUCTURE seeds for the two step indexes.
        private val sharedFirstAidCache = EnvelopeCache<Any>()

        private val firstAidRefreshScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
        )

        private val firstAidRefreshStamps =
            java.util.concurrent.ConcurrentHashMap<String, Long>()

        private const val FIRST_AID_REFRESH_MIN_INTERVAL_MS = 5 * 60 * 1000L

        /** Bundled seeds: ONLY the two step indexes — the section's
         * navigation skeleton (system names, unit titles, counts).
         * No questions, no explanations, no unit content: those keep
         * flowing through the normal download path (the doctor's
         * standing rule against embedding valuable content in the
         * APK stands). The seed installs into the encrypted section
         * cache on first use and is then silently refreshed like any
         * cached copy, so a fresh install shows the section's full
         * structure instantly, offline. */
        private val FIRST_AID_SEED_ASSETS: Map<String, String> = mapOf(
            "firstaid/index_step1.json" to "firstaid/index_step1.json",
            "firstaid/index_step2.json" to "firstaid/index_step2.json"
        )

        /** Manifest snapshot reused by refresh checks for 60 s
         * (1.2.43): collapses the burst of manifest fetches that
         * every section open used to fire (one per getter) into at
         * most one per minute. Refresh checks are best-effort and
         * hash-compare against this snapshot; explicit downloads keep
         * using [fetchManifest] directly. */
        @Volatile
        private var refreshManifestCache: ManifestResponse? = null

        @Volatile
        private var refreshManifestCacheAt: Long = 0L

        private const val REFRESH_MANIFEST_TTL_MS = 60_000L

        // ── Concepts pack summary (audit item Z, 21 Nov 2026) ──
        // The doctor's mechanism for «ادرس مع ميد برو»: the pack is a
        // PERMANENT on-device cache — downloaded once through the
        // app's protected channel, stored ENCRYPTED in sections_cache
        // (SectionCacheCrypto, like every section; deliberately NOT
        // bundled in the APK — the doctor's 15 Nov 2026 rule), shown
        // instantly at every open, fully offline with no expiry, and
        // silently refreshed in the background when the network is
        // available. What was missing for "instantly" was a
        // renderable summary: the entry card needs only two numbers
        // (topics, subjects), and those are now persisted at
        // download/parse time so the card renders on the FIRST frame
        // from this tiny record while the full pack parse proceeds
        // in the background.
        private const val CONCEPTS_SUMMARY_PREFS = "watani_concepts_summary"
        private const val KEY_SUMMARY_HASH = "hash"
        private const val KEY_SUMMARY_UNITS = "unit_count"
        private const val KEY_SUMMARY_SUBJECTS = "subject_count"

        /** The persisted summary of the INSTALLED concepts pack, or
         * null when no pack is installed (or the summary was never
         * written). Synchronous and cheap: two SharedPreferences
         * reads, cross-checked against the downloaded-sections
         * registry so a wiped pack never leaves a phantom card. */
        fun conceptsInstalledSummary(context: Context): ConceptsPackSummary? {
            return try {
                val registry = context.getSharedPreferences(
                    "watani_downloaded_sections", Context.MODE_PRIVATE
                )
                val prefs = context.getSharedPreferences(
                    CONCEPTS_SUMMARY_PREFS, Context.MODE_PRIVATE
                )
                val units = prefs.getInt(KEY_SUMMARY_UNITS, 0)
                val subjects = prefs.getInt(KEY_SUMMARY_SUBJECTS, 0)
                val hash = prefs.getString(KEY_SUMMARY_HASH, null)
                if (registry.contains("concepts.json") && hash != null && units > 0) {
                    ConceptsPackSummary(hash = hash, unitCount = units, subjectCount = subjects)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        /** Persists the summary for the pack parsed under [hash]. */
        fun persistConceptsSummary(context: Context, hash: String, unitCount: Int, subjectCount: Int) {
            try {
                context.getSharedPreferences(CONCEPTS_SUMMARY_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SUMMARY_HASH, hash)
                    .putInt(KEY_SUMMARY_UNITS, unitCount)
                    .putInt(KEY_SUMMARY_SUBJECTS, subjectCount)
                    .apply()
            } catch (_: Exception) {
            }
        }

        /** The question-bank families swept by
         * [refreshInstalledBankSections] (build 1.2.21; dawrat_sessions/
         * added 1.2.32 so an installed standalone dawra file receives
         * content corrections silently like every other bank file).
         * Single-file packs (concepts.json and friends) are NOT bank
         * sections and keep their own refresh paths. */
        private val BANK_SECTION_PREFIXES =
            listOf("questions/", "questions_en/", "dawrat_jami3at/", "uworld/", "dawrat_sessions/")

        /**
         * Total questions across the downloaded question-bank sections
         * (build 1.2.23, smart daily goal + widget). Reads the same
         * `watani_downloaded_sections` registry [loadDownloadedSections]
         * parses (value format `version|hash|count|downloadedAt`) —
         * usable from processes/components that hold no repository
         * instance (widget, reminder receiver). Zero when no bank
         * section is downloaded.
         */
        fun downloadedBankQuestionTotal(context: Context): Int {
            // The total counts each bank question ONCE: the standalone
            // dawra files (dawrat_sessions/, 1.2.32) re-carry questions
            // the shards already count, so they stay OUT of this sum
            // even though the refresh sweep covers them.
            val totalPrefixes =
                listOf("questions/", "questions_en/", "dawrat_jami3at/", "uworld/")
            return try {
                val registry = context.applicationContext
                    .getSharedPreferences("watani_downloaded_sections", Context.MODE_PRIVATE)
                var total = 0
                for ((path, value) in registry.all) {
                    if (value !is String) continue
                    if (totalPrefixes.none { path.startsWith(it) }) continue
                    val parts = value.split("|")
                    if (parts.size >= 4) total += parts[2].toIntOrNull() ?: 0
                }
                total
            } catch (_: Exception) {
                0
            }
        }
    }

    private fun saveManifestCache(manifest: ManifestResponse) {
        try {
            // Phase-2b hardening: the disk copy lists restricted
            // sections and is already wiped on revocation
            // (RestrictedInventory) — it is now also stored ENCRYPTED
            // through the central point, after serialization of the
            // already-fetched (verified) manifest above.
            SectionCacheCrypto.writeText(
                context, manifestCacheFile(),
                moshi.adapter(ManifestResponse::class.java).toJson(manifest)
            )
        } catch (_: Exception) {
            // Best-effort: a failed cache write must never fail the fetch.
        }
    }

    private fun loadManifestCache(): ManifestResponse? {
        return try {
            val file = manifestCacheFile()
            if (!file.exists()) return null
            // Phase-2b: central decrypting read — a legacy plaintext
            // manifest_cache.json is migrated to encrypted form here.
            val text = SectionCacheCrypto.readText(context, file) ?: return null
            moshi.adapter(ManifestResponse::class.java).fromJson(text)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The on-disk manifest cache, synchronously (cache audit fix
     * F1, 1.2.45): MainViewModel seeds its section lists from this
     * at construction, so a cold start renders the sections from
     * the complete signed manifest already on the device instead
     * of sitting behind the «جاري جلب الفهرس» spinner until the
     * network answers — the fetch then runs as a background
     * refresh whose success replaces the seed and whose failure
     * leaves it standing. Null when no cache was ever written.
     */
    fun cachedManifest(): ManifestResponse? = loadManifestCache()

    suspend fun fetchManifest(): Result<ManifestResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getManifest()
            if (response.isSuccessful && response.body() != null) {
                val manifest = response.body()!!
                saveManifestCache(manifest)
                Result.success(manifest)
            } else {
                loadManifestCache()?.let { return@withContext Result.success(it) }
                Result.failure(Exception("تعذر جلب الفهرس — تحقق من الاتصال وحاول مجددًا."))
            }
        } catch (e: Exception) {
            loadManifestCache()?.let { return@withContext Result.success(it) }
            Result.failure(Exception("تعذر الاتصال بخادم الفهرس — تحقق من الشبكة وحاول مجددًا.", e))
        }
    }

    private fun getSectionFile(path: String): File {
        val dir = File(context.filesDir, "sections_cache")
        if (!dir.exists()) dir.mkdirs()
        val sanitized = path.replace('/', '_').replace('\\', '_')
        return File(dir, "$sanitized.json")
    }

    /**
     * Silent best-effort refresh of an already-downloaded section
     * (build 1.2.6): the self-loading pack screens call this AFTER a
     * local copy loaded successfully, so a republished pack reaches
     * the device without the user revisiting the sections manager.
     *
     * Contract: returns true only when a newer file was downloaded
     * and installed (the caller then re-reads the pack from disk).
     * ANY failure — offline, manifest fetch failure, stale cached
     * manifest, download/verification failure — returns false and
     * leaves the local copy untouched; this function never throws,
     * so callers can invoke it inside their existing load flow with
     * no error surfacing and no UI flicker.
     */
    /** The manifest for refresh checks: a process-wide snapshot is
     * reused for [REFRESH_MANIFEST_TTL_MS] before a fresh fetch
     * (1.2.43 — see the companion note). Failure serves the snapshot
     * when one exists, else null. */
    private suspend fun fetchManifestForRefresh(): ManifestResponse? {
        val now = System.currentTimeMillis()
        val snapshot = refreshManifestCache
        val fresh = if (snapshot != null && now - refreshManifestCacheAt < REFRESH_MANIFEST_TTL_MS) {
            null
        } else {
            fetchManifest().getOrNull()
        }
        if (fresh != null) {
            refreshManifestCache = fresh
            refreshManifestCacheAt = now
        }
        return fresh ?: snapshot
    }

    suspend fun refreshSectionIfStale(path: String): Boolean {
        return try {
            val manifest = fetchManifestForRefresh() ?: return false
            val entry = manifest.files?.get(path) ?: return false
            val entity = _downloadedSectionsFlow.value
                .firstOrNull { it.sectionPath == path }
            if (!sectionNeedsRefresh(entity, entry)) return false
            downloadSection(path, entry).isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Bank auto-update (build 1.2.21): the self-loading packs refresh
     * themselves on open via [refreshSectionIfStale], but the question
     * banks never called it from any screen — an installed bank only
     * updated when the student noticed the «تحميل» button return in
     * the section list and pressed it. This applies the SAME stale
     * check ([sectionNeedsRefresh] against a freshly fetched manifest)
     * to every ALREADY-INSTALLED section of the bank families
     * (questions/, questions_en/, dawrat_jami3at/, uworld/,
     * dawrat_sessions/) and
     * re-downloads the stale ones through the standard [downloadSection]
     * path — atomic as always: size + family hash + real count are
     * verified before install, and any failure keeps the working copy.
     *
     * Contract mirrors [refreshSectionIfStale]: best-effort and silent.
     * Sections that were never installed are NEVER downloaded here
     * (no entity -> skipped), the concepts pack is deliberately not
     * part of this sweep (it has its own explicit Library download
     * card), paths in [excludePaths] (a manual download in flight on
     * the same file) are skipped, and any failure leaves the local
     * copies untouched. Returns true when at least one section was
     * actually updated.
     */
    suspend fun refreshInstalledBankSections(excludePaths: Set<String> = emptySet()): Boolean {
        return try {
            val manifest = fetchManifest().getOrNull() ?: return false
            val files = manifest.files ?: return false
            // Snapshot first: downloadSection rewrites the downloaded
            // registry as each section lands.
            val installed = _downloadedSectionsFlow.value.filter { entity ->
                entity.sectionPath !in excludePaths &&
                    BANK_SECTION_PREFIXES.any { entity.sectionPath.startsWith(it) }
            }
            val toRefresh = installed.mapNotNull { entity ->
                val entry = files[entity.sectionPath] ?: return@mapNotNull null
                if (sectionNeedsRefresh(entity, entry)) entity to entry else null
            }
            // Bounded parallelism (1.2.48, performance item 9): the
            // per-section downloads are independent; the registry
            // save reloads the full prefs state after each section
            // lands, so concurrent saves stay consistent.
            val results = ParallelFetch.mapBounded(
                toRefresh, ParallelFetch.DOWNLOAD_CONCURRENCY
            ) { (entity, entry) ->
                downloadSection(entity.sectionPath, entry).isSuccess
            }
            results.any { it }
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Phase-2 hardening (Nov 2026): encrypted sections_cache.
    // THE central read/write point for section files — every getter
    // below reads through [readSectionText] and every save goes
    // through [writeSectionBytes]/[writeSectionText], which store
    // AES-GCM ciphertext (see SectionCacheCrypto). Legacy plaintext
    // files are migrated to encrypted form on first read there.
    // ------------------------------------------------------------------

    private fun readSectionText(path: String): String? =
        SectionCacheCrypto.readText(context, getSectionFile(path))

    private fun writeSectionBytes(path: String, bytes: ByteArray): Boolean =
        SectionCacheCrypto.writeBytes(context, getSectionFile(path), bytes)

    private fun writeSectionText(path: String, text: String): Boolean =
        SectionCacheCrypto.writeText(context, getSectionFile(path), text)

    suspend fun downloadSection(
        path: String,
        entry: ManifestFileEntry,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        val staged = stageSectionDownload(path, entry, onProgress)
            .getOrElse { return@withContext Result.failure(it) }
        installStagedSectionDownload(staged)
    }

    /**
     * Pair-atomic download of a university subject and its «حسب
     * السنوات» twin (cache audit fix F4, 1.2.45). Before this, the
     * pair was downloaded as two sequential single downloads from
     * the view model: a twin failure left the NEW subject file
     * installed beside the OLD years file (version skew inside the
     * pair), and a first download that died between the two left a
     * subject file installed that no surface may present (the
     * pair-up-to-date verdict hides it — no data loss, but the
     * atomicity of بند 4 was incomplete).
     *
     * Both files are now STAGED first — fetched to their temp
     * files and fully verified (size + family hash + real count
     * via [SectionVerification]) — and only when BOTH verifications
     * pass is either file written and registered. Any failure
     * before the writes deletes both temps and leaves the working
     * pair exactly as it was. Progress is reported across the pair
     * (subject half, then twin half).
     */
    suspend fun downloadSectionPair(
        path: String,
        entry: ManifestFileEntry,
        twinPath: String,
        twinEntry: ManifestFileEntry,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        // The pair's temp files (the staging discipline of
        // stageSectionDownload): any failure BEFORE the installs
        // deletes both, so no half-staged pair ever survives.
        val subjectFile = getSectionFile(path)
        val subjectTemp = File(subjectFile.parentFile, "${subjectFile.name}.tmp")
        val twinFile = getSectionFile(twinPath)
        val twinTemp = File(twinFile.parentFile, "${twinFile.name}.tmp")
        // Bounded parallelism (1.2.48, performance item 9): the two
        // stagings are independent network fetches, so they now run
        // concurrently instead of one after the other. The atomic
        // install below is untouched — both files are still written
        // and registered only after BOTH verifications pass, and any
        // staging failure deletes every temp of the pair.
        val stagedResults = coroutineScope {
            val subjectDeferred = async {
                stageSectionDownload(path, entry) { p ->
                    onProgress(p * PAIR_PROGRESS_HALF)
                }
            }
            val twinDeferred = async {
                stageSectionDownload(twinPath, twinEntry) { p ->
                    onProgress(PAIR_PROGRESS_HALF + p * PAIR_PROGRESS_HALF)
                }
            }
            subjectDeferred.await() to twinDeferred.await()
        }
        val stagedSubject = stagedResults.first.getOrElse {
            subjectTemp.delete()
            twinTemp.delete()
            stagedResults.second.getOrNull()?.tempFile?.delete()
            return@withContext Result.failure(it)
        }
        val stagedTwin = stagedResults.second.getOrElse {
            stagedSubject.tempFile.delete()
            twinTemp.delete()
            return@withContext Result.failure(it)
        }
        val installed = installStagedSectionDownload(stagedSubject)
        if (installed.isFailure) {
            stagedTwin.tempFile.delete()
            return@withContext installed
        }
        installStagedSectionDownload(stagedTwin)
    }

    /** One fully downloaded + verified section file, staged in its
     * temp file and ready to install (see [stageSectionDownload]).
     * [computedHash] and [actualCount] are derived from the staged
     * plaintext during verification — the registry never stores a
     * verbatim copy of the manifest's declared values (F-01). */
    private class StagedSectionDownload(
        val path: String,
        val entry: ManifestFileEntry,
        val tempFile: File,
        val bytes: ByteArray,
        val computedHash: String,
        val actualCount: Int
    )

    /**
     * The fetch + verify half of a section download: device gate,
     * gateway/Pages fetch into the temp file (Range-resumed), then
     * F-01/F-02 verification of the staged plaintext (manifest size
     * + family hash, then the real parsed count via
     * [SectionVerification]). NOTHING is written to the section
     * cache and nothing is registered here — that is
     * [installStagedSectionDownload], so [downloadSectionPair] can
     * stage two files and install them only when both verify.
     *
     * Temp-file discipline matches the historical single download:
     * a verification failure deletes the temp (its bytes are proven
     * wrong); a network/auth failure KEEPS it, so a later attempt
     * resumes via Range instead of restarting.
     */
    private suspend fun stageSectionDownload(
        path: String,
        entry: ManifestFileEntry,
        onProgress: (Float) -> Unit = {}
    ): Result<StagedSectionDownload> = withContext(Dispatchers.IO) {
        try {
            val file = getSectionFile(path)
            val tempFile = File(file.parentFile, "${file.name}.tmp")

            fetchSectionToTemp(path, entry, tempFile, onProgress)
                .getOrElse { return@withContext Result.failure(it) }

            // Phase-2 hardening (Nov 2026) — verification order is
            // UNCHANGED, only the final persisted form changed:
            // F-01/F-02 verification still runs on the PLAINTEXT bytes
            // (the downloaded temp file), BEFORE anything is saved;
            // the section is then stored ENCRYPTED (SectionCacheCrypto)
            // and the plaintext temp is deleted, so no plaintext
            // question JSON ever persists in sections_cache. The old
            // working copy is replaced only by the checked encrypted
            // write (F-07 spirit): a failed verification or a failed
            // encryption leaves the previously installed copy untouched.
            val installedBytes = tempFile.readBytes()
            val mismatch = SectionVerification.manifestMismatch(
                path, installedBytes, entry.size, entry.hash
            )
            if (mismatch != null) {
                tempFile.delete()
                return@withContext Result.failure(Exception(mismatch))
            }
            val computedHash = SectionVerification.manifestHashHex(path, installedBytes)

            // F-02/F-03/F-04: content verification (pure core in
            // SectionVerification): real parse + honest declared-count
            // comparison, no coerced counts anywhere.
            val jsonString = String(installedBytes, Charsets.UTF_8)
            val actualCount = try {
                SectionVerification.verifySectionCount(path, jsonString, moshi)
            } catch (e: IllegalStateException) {
                tempFile.delete()
                return@withContext Result.failure(Exception(e.message))
            }

            Result.success(
                StagedSectionDownload(
                    path = path,
                    entry = entry,
                    tempFile = tempFile,
                    bytes = installedBytes,
                    computedHash = computedHash,
                    actualCount = actualCount
                )
            )
        } catch (e: Exception) {
            // Typed revocation passes through unwrapped (M3): callers
            // distinguish it from ordinary download failures.
            if (e is ActivationRevokedException) {
                Result.failure(e)
            } else {
                Result.failure(Exception("تعذر التحميل — تحقق من اتصال الإنترنت وحاول مجددًا.", e))
            }
        }
    }

    /**
     * The fetch half of staging: device gate, gateway/Pages request
     * (Range-resumed against the temp file), auth/status checks and
     * the body copy into [tempFile]. Failures are the historical
     * download failures, unchanged; exceptions propagate to the
     * caller's catch (which wraps them, M3).
     */
    private fun fetchSectionToTemp(
        path: String,
        entry: ManifestFileEntry,
        tempFile: File,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        // M8: device-integrity gate at the restricted-download entry.
        // Blocked devices never reach the gateway (and never get a
        // partial file to keep). 1.2.47: the gate guards content that
        // requires activation — the free-bank subjects are free
        // content and download without it, like any free section.
        val gateFailure = if (WorkerActivationApi.requiresActivation(path)) {
            val gate = sy.medpro.watani.ui.security.RestrictedContentGate.check(context)
            if (gate.allowed) null else Exception(gate.userMessage)
        } else {
            null
        }
        // THE fetch-layer switch point (Team 1): restricted paths
        // (questions/, dawrat/, dawrat_jami3at/, uworld/,
        // explanations/, books/, library.json) are fetched via the
        // Worker gateway with the activation token; free paths keep
        // fetching directly from Pages, unchanged. Null = restricted
        // but no usable token -> activation prompt, never a direct fetch.
        val baseRequest = if (gateFailure == null) {
            WorkerActivationApi.contentRequest(context, path)
        } else {
            null
        }
        return when {
            gateFailure != null -> Result.failure(gateFailure)
            baseRequest == null -> Result.failure(
                Exception("هذا القسم يحتاج كود تفعيل — فعّل حسابك من نافذة التفعيل أولاً.")
            )
            else -> executeSectionFetch(path, entry, tempFile, baseRequest, onProgress)
        }
    }

    /** Executes the built section request into [tempFile]:
     * Range resume, the 416 stale-range restart (detector fix b5:
     * a temp longer than a shrunk origin file restarts from zero
     * instead of looping on the same bad Range — the gateway now
     * passes 416 through instead of masking it as 502), then the
     * auth/status checks and the body copy. F-13: the response is
     * closed on EVERY exit path (use{}), including the early
     * 401/403 and HTTP-failure branches — an interrupted download
     * on a weak network must not leak connections or leave the
     * resume temp file unflushed. */
    private fun executeSectionFetch(
        path: String,
        entry: ManifestFileEntry,
        tempFile: File,
        baseRequest: okhttp3.Request,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
        val requestBuilder = baseRequest.newBuilder()
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }
        var resp = client.newCall(requestBuilder.build()).execute()
        if (resp.code == 416) {
            if (tempFile.exists()) tempFile.delete()
            resp.close()
            resp = client.newCall(baseRequest).execute()
        }
        return resp.use {
            when {
                // M3: no generic HTTP message, no cached fallback —
                // activation is lost (cleared + wiped + prompt event).
                // 1.2.47: that semantics belongs to requests that
                // carried an ACTIVATION token. A free-bank download
                // by a student with no activation token carried a
                // Firebase ID token instead — its 401/403 means the
                // sign-in session needs a refresh, never an
                // activation loss (no wipe, no revocation prompt).
                resp.code == 401 || resp.code == 403 ->
                    if (WorkerActivationApi.requiresActivation(path) ||
                        WorkerActivationApi.isActivated(context)
                    ) {
                        Result.failure(gatewayAuthFailure(context))
                    } else {
                        Result.failure(
                            Exception("انتهت جلسة تسجيل الدخول — أعد تسجيل الدخول ثم حاول مجددًا.")
                        )
                    }
                !resp.isSuccessful && resp.code != 206 -> Result.failure(
                    Exception("فشل التحميل — تأكد من اتصال الإنترنت أو صحة الرابط.")
                )
                else -> copySectionBodyToTemp(resp, tempFile, existingBytes, entry, onProgress)
            }
        }
    }

    /** Streams a section response body into [tempFile], resuming
     * from [existingBytes] when the server answered 206 (A1: a 200
     * answer discards the partial temp and restarts from zero). */
    private fun copySectionBodyToTemp(
        resp: okhttp3.Response,
        tempFile: File,
        existingBytes: Long,
        entry: ManifestFileEntry,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        // A1: Resume safety - if server answered HTTP 200 instead of 206 with full body, discard partial temp file
        val isPartial = (resp.code == 206)
        val actualExistingBytes = if (isPartial) existingBytes else {
            if (tempFile.exists()) tempFile.delete()
            0L
        }

        val body = resp.body ?: return Result.failure(Exception("استجابة فارغة من الخادم."))
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0) contentLength + actualExistingBytes else (entry.size ?: 1L)

        body.byteStream().use { inputStream ->
            java.io.FileOutputStream(tempFile, actualExistingBytes > 0).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesCopied = actualExistingBytes
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    bytesCopied += read
                    val progress = (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    onProgress(progress)
                }
                outputStream.flush()
            }
        }
        return Result.success(Unit)
    }

    /**
     * The install half of a section download: the staged, verified
     * plaintext is stored ENCRYPTED through the central write point,
     * the temp is deleted, and the section is registered with the
     * hash/count computed during verification. A failed write
     * deletes the temp and leaves the previously installed copy
     * untouched.
     */
    private fun installStagedSectionDownload(staged: StagedSectionDownload): Result<Int> {
        if (!writeSectionBytes(staged.path, staged.bytes)) {
            staged.tempFile.delete()
            return Result.failure(Exception("تعذر حفظ ملف القسم على الجهاز — أعد المحاولة."))
        }
        staged.tempFile.delete()

        // F-01: the registry stores the hash COMPUTED from the
        // verified plaintext (never a verbatim copy of entry.hash),
        // so isSectionUpToDate compares two independently derived
        // values and a swapped/corrupt file cannot look current.
        val entity = DownloadedSectionEntity(
            sectionPath = staged.path,
            version = staged.entry.version ?: "1.0",
            hash = staged.computedHash,
            count = staged.actualCount,
            downloadedAt = System.currentTimeMillis()
        )
        saveDownloadedSection(entity)
        // Gentle lease: a successful restricted download is a
        // successful server verification.
        if (WorkerActivationApi.requiresActivation(staged.path)) {
            WorkerActivationApi.noteServerVerified(context)
        }
        return Result.success(staged.actualCount)
    }

    suspend fun getSubjectEnvelope(path: String): SubjectEnvelope? = withContext(Dispatchers.IO) {
        try {
            // Gentle lease: an expired lease makes downloaded
            // restricted sections behave as unavailable (free paths
            // are never gated here). The lease check stays FIRST —
            // a cached envelope is never served past it (1.2.27).
            // 1.2.47: the free-bank subjects are free paths for this
            // gate too — their downloads never expire behind a lease.
            if (WorkerActivationApi.requiresActivation(path) &&
                WorkerActivationApi.isRestrictedLeaseExpired(context)
            ) return@withContext null
            // Cache (1.2.27): served only when the registry holds
            // this path AND the entry was parsed from bytes with
            // the registry's current hash — a re-download swaps
            // the hash and the stale parse is dropped inside get().
            val registryHash = _downloadedSectionsFlow.value
                .firstOrNull { it.sectionPath == path }?.hash
            if (registryHash != null) {
                subjectEnvelopeCache.get(path, registryHash)?.let { return@withContext it }
            }
            val jsonString = readSectionText(path) ?: return@withContext null
            val parsed = moshi.adapter(SubjectEnvelope::class.java).fromJson(jsonString)
            if (parsed != null && registryHash != null) {
                subjectEnvelopeCache.put(path, registryHash, jsonString.length, parsed)
                // Build the light session-count index from the same
                // parse (1.2.48, performance item 3), so the «دورة
                // كاملة» list never has to parse this body again
                // just to count its sessions.
                persistSessionCountsIndex(path, registryHash, parsed)
            }
            parsed
        } catch (e: Exception) {
            null
        }
    }

    private val sessionCountsPrefs by lazy {
        context.getSharedPreferences(SESSION_COUNTS_PREFS, Context.MODE_PRIVATE)
    }

    /** The persisted light session-count index for the shard at
     * [path] — subject name + per-session question counts, in shard
     * order — or null when no entry was built from the bytes the
     * downloaded-sections registry currently holds for the path
     * (never downloaded, hash moved on, or the entry is unreadable).
     * Cheap and synchronous: two prefs reads, no file I/O, no parse
     * of the shard body. */
    fun sessionCountsIndexEntry(path: String): SessionCountsIndexEntry? {
        val registryHash = _downloadedSectionsFlow.value
            .firstOrNull { it.sectionPath == path }?.hash ?: return null
        return try {
            val raw = sessionCountsPrefs.getString(path, null) ?: return null
            if (raw.substringBefore('\n') != registryHash) return null
            moshi.adapter(SessionCountsIndexEntry::class.java)
                .fromJson(raw.substringAfter('\n'))
        } catch (e: Exception) {
            null
        }
    }

    /** Builds/persists the session-count index for a freshly parsed
     * subject [envelope], stamped with the registry [registryHash]
     * of the bytes it was parsed from. Best-effort: a failure here
     * only means the next list build falls back to the envelope. */
    private fun persistSessionCountsIndex(
        path: String,
        registryHash: String,
        envelope: SubjectEnvelope
    ) {
        try {
            val entry = SessionCountsIndexEntry(
                subjectName = envelope.subjectName,
                counts = SectionIndex.sessionCounts(envelope)
            )
            val json = moshi.adapter(SessionCountsIndexEntry::class.java).toJson(entry)
            sessionCountsPrefs.edit().putString(path, "$registryHash\n$json").apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Standalone dawra file records (build 1.2.32): the downloaded
     * `dawrat_sessions/<session>.json` section parsed as unified
     * question records. Same gentle-lease gate as [getSubjectEnvelope]
     * (the path is restricted, so an expired lease makes it behave as
     * unavailable and the caller falls back to the shard assembly);
     * null when the file is not downloaded or does not parse.
     */
    suspend fun getDawraSessionRecords(path: String): List<DawraSessionQuestionDto>? =
        withContext(Dispatchers.IO) {
            try {
                if (WorkerActivationApi.requiresActivation(path) &&
                    WorkerActivationApi.isRestrictedLeaseExpired(context)
                ) return@withContext null
                // Parse cache (F5, 1.2.45): same registry-hash guard
                // as getSubjectEnvelope — served only when parsed
                // from bytes with the registry's current hash.
                val registryHash = _downloadedSectionsFlow.value
                    .firstOrNull { it.sectionPath == path }?.hash
                if (registryHash != null) {
                    dawraRecordsCache.get(path, registryHash)?.let { return@withContext it }
                }
                val jsonString = readSectionText(path) ?: return@withContext null
                val listType = com.squareup.moshi.Types.newParameterizedType(
                    List::class.java, DawraSessionQuestionDto::class.java
                )
                val parsed = moshi.adapter<List<DawraSessionQuestionDto>>(listType)
                    .fromJson(jsonString)
                if (parsed != null && registryHash != null) {
                    dawraRecordsCache.put(path, registryHash, jsonString.length, parsed)
                }
                parsed
            } catch (e: Exception) {
                null
            }
        }

    /**
     * «حسب السنوات» لمواد الجامعات: غلاف ملف السنوات المستقل
     * (`dawrat_jami3at/<uni>_years/<subject>.json`). مرآة
     * [getSubjectEnvelope]: بوابة الإيجار المنتهي أولًا للمسارات
     * المقيدة (مسارات الجامعات مقيدة، فالغلاف المحفوظ لا يُقدَّم
     * أبدًا بعد انتهاء الإيجار), ثم القراءة عبر [readSectionText]
     * (الكاش المشفّر) وتحليل Moshi. null عند غياب الملف أو فساد
     * تحليله — الشاشة تعرض عندها قائمة فارغة ولا تنهار.
     */
    suspend fun getYearsEnvelope(path: String): YearsEnvelope? = withContext(Dispatchers.IO) {
        try {
            if (WorkerActivationApi.requiresActivation(path) &&
                WorkerActivationApi.isRestrictedLeaseExpired(context)
            ) return@withContext null
            // Parse cache (cache audit fix F5, 1.2.45): mirror of
            // getSubjectEnvelope — served only when the registry
            // holds this path AND the entry was parsed from bytes
            // with the registry's current hash; a re-download swaps
            // the hash and the stale parse is dropped inside get().
            val registryHash = _downloadedSectionsFlow.value
                .firstOrNull { it.sectionPath == path }?.hash
            if (registryHash != null) {
                yearsEnvelopeCache.get(path, registryHash)?.let { return@withContext it }
            }
            val jsonString = readSectionText(path) ?: return@withContext null
            val parsed = moshi.adapter(YearsEnvelope::class.java).fromJson(jsonString)
            if (parsed != null && registryHash != null) {
                yearsEnvelopeCache.put(path, registryHash, jsonString.length, parsed)
            }
            parsed
        } catch (e: Exception) {
            null
        }
    }

    // ── Parsed-pack cache plumbing (1.2.48, performance item 1) ──
    /** (mtime, length) stamp of a section file — the same
     * self-invalidating stamp the First Aid cache uses: any
     * re-download rewrites the file, changes the stamp and drops
     * the stale parse inside [EnvelopeCache.get]. */
    private fun packFileStamp(path: String): String? {
        val file = getSectionFile(path)
        return if (file.exists()) "${file.lastModified()}:${file.length()}" else null
    }

    /** Read + parse a small section pack through the process-wide
     * parse cache ([sharedPackEnvelopeCache]): a repeat call for
     * unchanged bytes returns the parsed value with no disk read,
     * no decrypt and no parse. Null when the file is absent or does
     * not parse — a null is never cached. Semantics are otherwise
     * identical to the pre-1.2.48 direct read+parse getters. */
    private suspend fun <T> getCachedPack(path: String, parse: (String) -> T?): T? =
        withContext(Dispatchers.IO) {
            try {
                val stamp = packFileStamp(path)
                if (stamp != null) {
                    @Suppress("UNCHECKED_CAST")
                    val hit = sharedPackEnvelopeCache.get(path, stamp) as? T
                    if (hit != null) return@withContext hit
                }
                val text = readSectionText(path) ?: return@withContext null
                val parsed = parse(text) ?: return@withContext null
                packFileStamp(path)?.let {
                    sharedPackEnvelopeCache.put(path, it, text.length, parsed as Any)
                }
                parsed
            } catch (e: Exception) {
                null
            }
        }

    suspend fun getCasesEnvelope(): CasesEnvelope? =
        getCachedPack("cases.json") { moshi.adapter(CasesEnvelope::class.java).fromJson(it) }

    // الحالات الألمانية (نقل 9 نوفمبر): نفس غلاف الحالات تمامًا، ملف مستقل.
    suspend fun getCasesDeEnvelope(): CasesEnvelope? =
        getCachedPack("cases_de.json") { moshi.adapter(CasesEnvelope::class.java).fromJson(it) }

    suspend fun getGuideEnvelope(): GuideEnvelope? =
        getCachedPack("guide.json") { moshi.adapter(GuideEnvelope::class.java).fromJson(it) }

    suspend fun getOsceEnvelope(): OsceEnvelope? =
        getCachedPack("osce.json") { moshi.adapter(OsceEnvelope::class.java).fromJson(it) }

    suspend fun getAuscultationEnvelope(): AuscultationEnvelope? = getCachedPack("auscultation.json") {
        moshi.adapter(AuscultationEnvelope::class.java).fromJson(it)
    }

    /**
     * ECG mini-course pack (1.2.4) — mirrors [getAuscultationEnvelope]
     * exactly: the downloaded "ecg.json" section file from
     * sections_cache, parsed leniently; null when the pack has not been
     * downloaded yet (the course screen then pulls it through the
     * manifest + downloadSection path, like the auscultation trainer).
     */
    suspend fun getEcgEnvelope(): EcgEnvelope? =
        // Encrypted sections_cache (phase-2 hardening): the cached
        // path still reads through the central crypto point.
        getCachedPack("ecg.json") { moshi.adapter(EcgEnvelope::class.java).fromJson(it) }

    // ---------------- First Aid - Med Pro (firstaid/, 1.2.36) ----------------
    // FREE family: files live on the public origin and flow through the
    // ordinary manifest + downloadSection path (never the gateway, never
    // a restricted list). Every getter is cache-first via readSectionText;
    // a cached copy is silently refreshed through refreshSectionIfStale
    // (hash-compare, no-op when current), and a missing copy is pulled
    // once through fetchManifest + downloadSection. Null on any failure —
    // the section screen owns the retry/empty states.

    /** Background stale-refresh for a First Aid path (1.2.43):
     * fire-and-forget on the process scope, throttled per path to
     * one attempt per [FIRST_AID_REFRESH_MIN_INTERVAL_MS] so tab
     * re-entries and unit opens never storm the network. A refresh
     * that installs a newer file changes the cache file's stamp, so
     * the in-memory parsed entry invalidates itself on next read. */
    private fun scheduleFirstAidRefresh(path: String) {
        val now = System.currentTimeMillis()
        val last = firstAidRefreshStamps[path] ?: 0L
        if (now - last < FIRST_AID_REFRESH_MIN_INTERVAL_MS) return
        firstAidRefreshStamps[path] = now
        firstAidRefreshScope.launch {
            try {
                refreshSectionIfStale(path)
            } catch (_: Exception) {
            }
        }
    }

    /** Installs the bundled structure seed for [path] (the two step
     * indexes only — see FIRST_AID_SEED_ASSETS) into the encrypted
     * section cache. Only when NO cache file exists yet: a real
     * downloaded copy is never overwritten by the older seed. */
    private fun seedFirstAidSection(path: String): Boolean {
        val asset = FIRST_AID_SEED_ASSETS[path]
        return asset != null && !getSectionFile(path).exists() && try {
            val text = context.assets.open(asset)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            writeSectionText(path, text)
        } catch (e: Exception) {
            false
        }
    }

    private fun firstAidFileStamp(path: String): String? {
        val file = getSectionFile(path)
        return if (file.exists()) "${file.lastModified()}:${file.length()}" else null
    }

    private suspend fun <T> getFirstAidSection(path: String, clazz: Class<T>): T? =
        withContext(Dispatchers.IO) {
            try {
                // 1.2.43 cache-first contract (doctor's order): a
                // parsed in-memory copy or the on-disk cache returns
                // IMMEDIATELY — the stale-refresh runs in the
                // background and never blocks the first draw again.
                val stamp = firstAidFileStamp(path)
                if (stamp != null) {
                    @Suppress("UNCHECKED_CAST")
                    val hit = sharedFirstAidCache.get(path, stamp) as? T
                    if (hit != null) {
                        scheduleFirstAidRefresh(path)
                        return@withContext hit
                    }
                }
                var text = readSectionText(path)
                if (text == null && seedFirstAidSection(path)) {
                    text = readSectionText(path)
                }
                val parsed = text?.let { moshi.adapter(clazz).fromJson(it) }
                if (parsed != null) {
                    firstAidFileStamp(path)?.let {
                        sharedFirstAidCache.put(path, it, text.length, parsed as Any)
                    }
                    scheduleFirstAidRefresh(path)
                    return@withContext parsed
                }
                // Nothing cached and no seed: the one genuine
                // network path — pull the file once (extracted
                // helper; this getter sits at the complexity gate).
                fetchFirstAidSectionFresh(path, clazz)
            } catch (e: Exception) {
                null
            }
        }

    /** The network tail of [getFirstAidSection]: manifest → download
     * → parse → memory-cache. Null on any failure. */
    private suspend fun <T> fetchFirstAidSectionFresh(path: String, clazz: Class<T>): T? {
        // Cache audit fix F2 (1.2.45, بند 6): the section screen
        // fires four of these loads in parallel at first open; each
        // used to fetch the manifest independently. The refresh
        // snapshot (60-second TTL) collapses the burst into one
        // shared fetch, exactly like every other refresh check.
        val entry = fetchManifestForRefresh()?.files?.get(path)
        val freshText = if (entry != null && downloadSection(path, entry).isSuccess) {
            readSectionText(path)
        } else {
            null
        }
        val fresh = freshText?.let { moshi.adapter(clazz).fromJson(it) }
        if (fresh != null) {
            firstAidFileStamp(path)?.let {
                sharedFirstAidCache.put(path, it, freshText?.length ?: 0, fresh as Any)
            }
        }
        return fresh
    }

    /** Step index (1 or 2): systems in book order + per-unit summaries
     * + booklet catalog. Path: "firstaid/index_step<step>.json". */
    suspend fun getFirstAidIndex(step: Int): FirstAidIndexDto? =
        getFirstAidSection("firstaid/index_step$step.json", FirstAidIndexDto::class.java)

    /** One unified unit package. Path: "firstaid/units<step>/<id>.json". */
    suspend fun getFirstAidUnit(step: Int, id: String): FirstAidUnitPackageDto? =
        getFirstAidSection("firstaid/units$step/$id.json", FirstAidUnitPackageDto::class.java)

    /** True when the unit package already sits in the section cache —
     * its getter then returns with no network wait at all. */
    fun isFirstAidUnitCached(step: Int, id: String): Boolean =
        getSectionFile("firstaid/units$step/$id.json").exists()

    /** Background warm-up of a step's unit packages (1.2.45, the
     * doctor's professional-cache standard): the moment the section's
     * indexes resolve, every not-yet-cached package of the step is
     * pulled silently on the refresh scope (bounded-parallel since
     * 1.2.48), so the first open of a unit is a cache hit instead of
     * a network fetch behind a spinner. Best-effort and silent by
     * contract: a failed package is simply planned again on the next
     * section entry, and the unit's own open remains the fallback fetch. */
    fun prefetchFirstAidUnits(step: Int, ids: List<String>) {
        val missing = FirstAidPrefetchRules.missing(ids) { isFirstAidUnitCached(step, it) }
        if (missing.isEmpty()) return
        firstAidRefreshScope.launch {
            // Bounded parallelism (1.2.48, performance item 9): the
            // packages are independent files, so the warm-up pulls a
            // few at a time instead of one by one — the first open of
            // a unit becomes a cache hit sooner on slow networks.
            ParallelFetch.mapBounded(missing, ParallelFetch.DOWNLOAD_CONCURRENCY) { id ->
                try {
                    getFirstAidUnit(step, id)
                } catch (_: Exception) {
                    // Silent: the open-path fetch stays the fallback.
                }
            }
        }
    }

    /** The integration layer: 157 topic links + integration sessions. */
    suspend fun getFirstAidIntegration(): FirstAidIntegrationDto? =
        getFirstAidSection("firstaid/integration.json", FirstAidIntegrationDto::class.java)

    /** The five printed Step-2 practice tests (499 questions as printed). */
    suspend fun getFirstAidBanks(): FirstAidBanksDto? =
        getFirstAidSection("firstaid/banks_step2.json", FirstAidBanksDto::class.java)

    /** Local file of a First Aid booklet PDF (library-download pattern:
     * a plain filesDir asset, downloaded on first open, then read by
     * the existing PdfViewerScreen). [relativePath] is the index's
     * booklet "file" value prefixed with "firstaid/", e.g.
     * "firstaid/booklets2/FirstAid-Step2-CARD-Booklet.pdf". */
    fun getFirstAidBookletFile(relativePath: String): File {
        val dir = File(context.filesDir, "firstaid_booklets")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, relativePath.replace('/', '_'))
    }

    suspend fun downloadFirstAidBooklet(
        relativePath: String,
        expectedSize: Long = 0L,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getFirstAidBookletFile(relativePath)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        // PDF magic check (1.2.37 hardening): a real booklet starts
        // with the five bytes "%PDF-". Applied to the cached copy
        // AND to every fresh download before it is installed — a
        // right-size-but-wrong-content file (HTML error page, a
        // truncated transfer) must never reach the reader.
        fun hasPdfHeader(f: File): Boolean = try {
            if (!f.exists() || f.length() < 5) {
                false
            } else {
                f.inputStream().use { input ->
                    val head = ByteArray(5)
                    var off = 0
                    while (off < head.size) {
                        val r = input.read(head, off, head.size - off)
                        if (r < 0) break
                        off += r
                    }
                    off == head.size && String(head, Charsets.US_ASCII) == "%PDF-"
                }
            }
        } catch (_: Exception) {
            false
        }
        try {
            if (targetFile.exists() && targetFile.length() > 0 &&
                (expectedSize <= 0 || targetFile.length() == expectedSize)
            ) {
                if (hasPdfHeader(targetFile)) {
                    return@withContext Result.success(targetFile)
                }
                // Cached copy has the right size but is not a PDF:
                // drop it and fall through to a fresh download.
                targetFile.delete()
            }
            val request = okhttp3.Request.Builder()
                .url("${WataniApi.BASE_URL}$relativePath")
                .build()
            val resp = client.newCall(request).execute()
            if (!resp.isSuccessful) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("فشل تحميل الكتيّب (HTTP ${resp.code})"))
            }
            val body = resp.body ?: return@withContext Result.failure(Exception("استجابة كتيّب فارغة"))
            body.byteStream().use { inputStream ->
                java.io.FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    val total = body.contentLength().coerceAtLeast(1L)
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        downloaded += read
                        onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            // Post-transfer verification (1.2.37): expected size
            // (when the index/manifest provides one) AND the PDF
            // magic header. Any mismatch deletes the temp file and
            // fails with a descriptive IOException — the corrupt
            // bytes are never installed under the booklet's name.
            if (expectedSize > 0 && tempFile.length() != expectedSize) {
                val actualSize = tempFile.length()
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(
                    java.io.IOException(
                        "حجم الكتيّب غير مطابق: المتوقع $expectedSize بايت والواصل فعليًا $actualSize بايت — حُذفت النسخة الناقصة."
                    )
                )
            }
            if (!hasPdfHeader(tempFile)) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(
                    java.io.IOException(
                        "الملف المحمّل ليس كتيّب PDF صالحًا: ترويسة %PDF- مفقودة من أول الملف — حُذفت النسخة التالفة."
                    )
                )
            }
            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(Exception("تعذر تحميل الكتيّب — تحقق من الاتصال وحاول مجددًا.", e))
        }
    }

    /** Local file of one Step-1 First Aid media figure (disk asset on
     * the public origin, auscultation-clip pattern). The pack's media
     * [file] value is "media/<id>.png" relative to the unit; on the
     * origin the figure lives at "firstaid/media/<UNIT>/<id>.png". */
    fun getFirstAidMediaFile(unitId: String, mediaFile: String): File {
        val dir = File(context.filesDir, "firstaid_media")
        if (!dir.exists()) dir.mkdirs()
        val base = mediaFile.substringAfterLast('/')
        return File(dir, "${unitId}_$base")
    }

    /** Downloads one Step-1 media figure to its local file (no-op
     * success when already present). Figures download WITH their unit:
     * the unit player pulls every media entry of the open unit, then
     * the reference renderer shows each figure at its in-text marker. */
    suspend fun downloadFirstAidMedia(unitId: String, mediaFile: String): Result<File> =
        withContext(Dispatchers.IO) {
            val targetFile = getFirstAidMediaFile(unitId, mediaFile)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            try {
                if (targetFile.exists() && targetFile.length() > 0) {
                    return@withContext Result.success(targetFile)
                }
                val base = mediaFile.substringAfterLast('/')
                val request = okhttp3.Request.Builder()
                    .url("${WataniApi.BASE_URL}firstaid/media/$unitId/$base")
                    .build()
                val resp = client.newCall(request).execute()
                if (!resp.isSuccessful) {
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext Result.failure(Exception("فشل تحميل الشكل (HTTP ${resp.code})"))
                }
                val body = resp.body ?: return@withContext Result.failure(Exception("استجابة شكل فارغة"))
                body.byteStream().use { inputStream ->
                    java.io.FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (tempFile.exists()) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                }
                Result.success(targetFile)
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                Result.failure(Exception("تعذر تحميل الشكل — تحقق من الاتصال.", e))
            }
        }

    /**
     * Bundled fallback (assets/ecg_sample.json): a small sample pack so
     * the ECG course opens and teaches even before the pipeline's real
     * ecg.json is published to the manifest. Used ONLY when neither the
     * downloaded pack nor a manifest download is available — the server
     * pack always wins when present.
     */
    suspend fun getBundledEcgEnvelope(): EcgEnvelope? = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("ecg_sample.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            moshi.adapter(EcgEnvelope::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getDdxEnvelope(): DdxEnvelope? =
        getCachedPack("ddx.json") { moshi.adapter(DdxEnvelope::class.java).fromJson(it) }

    suspend fun getReverseEnvelope(): ReverseEnvelope? =
        getCachedPack("reverse.json") { moshi.adapter(ReverseEnvelope::class.java).fromJson(it) }

    /**
     * OSCE procedures pack (build 1.2.6): the downloaded
     * "osce_procedures.json" section file from sections_cache — a BARE
     * ARRAY of procedures (see SkillsModels), parsed leniently; null
     * when the pack has not been downloaded yet (the procedures wing
     * then pulls it through the manifest + downloadSection path, like
     * the OSCE stations screen).
     */
    suspend fun getOsceProcedures(): List<ProcedureDto>? =
        // Encrypted sections_cache (phase-2 hardening): the cached
        // path still reads through the central crypto point.
        getCachedPack("osce_procedures.json") {
            val listType = com.squareup.moshi.Types.newParameterizedType(
                List::class.java, ProcedureDto::class.java
            )
            moshi.adapter<List<ProcedureDto>>(listType).fromJson(it)
        }

    /**
     * Bundled fallback (assets/osce_procedures_sample.json): one full
     * sample procedure so the procedures wing opens and teaches even
     * before the pipeline's real pack is published to the manifest.
     * Used ONLY when neither the downloaded pack nor a manifest
     * download is available — the server pack always wins when present.
     */
    suspend fun getBundledOsceProcedures(): List<ProcedureDto>? = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("osce_procedures_sample.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val listType = com.squareup.moshi.Types.newParameterizedType(
                List::class.java, ProcedureDto::class.java
            )
            moshi.adapter<List<ProcedureDto>>(listType).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Dosing drills pack (build 1.2.6): the downloaded
     * "dosing_drills.json" section file from sections_cache — a BARE
     * ARRAY of drills (see SkillsModels); null when not downloaded yet
     * (the drills screen then pulls it through the manifest +
     * downloadSection path).
     */
    suspend fun getDosingDrills(): List<DosingDrillDto>? =
        // Encrypted sections_cache (phase-2 hardening): the cached
        // path still reads through the central crypto point.
        getCachedPack("dosing_drills.json") {
            val listType = com.squareup.moshi.Types.newParameterizedType(
                List::class.java, DosingDrillDto::class.java
            )
            moshi.adapter<List<DosingDrillDto>>(listType).fromJson(it)
        }

    /**
     * Bundled fallback (assets/dosing_drills_sample.json): two sample
     * drills (adult + pediatric) used ONLY when neither the downloaded
     * pack nor a manifest download is available.
     */
    suspend fun getBundledDosingDrills(): List<DosingDrillDto>? = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("dosing_drills_sample.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val listType = com.squareup.moshi.Types.newParameterizedType(
                List::class.java, DosingDrillDto::class.java
            )
            moshi.adapter<List<DosingDrillDto>>(listType).fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * «ادرس مع ميد برو» concepts pack (build 1.2.13): the downloaded
     * "concepts.json" section file from sections_cache — an envelope
     * of concept units (see ConceptsModels); null when not downloaded
     * yet (the calling screens then pull it through the manifest +
     * downloadSection path, exactly like dosing_drills.json). There
     * is deliberately NO bundled fallback: the Library section stays
     * hidden until a real pack with units exists on the device.
     */
    suspend fun getConcepts(): ConceptsEnvelope? = withContext(Dispatchers.IO) {
        try {
            // Cache (1.2.31): served only when the entry was parsed
            // from bytes with the registry's current hash for
            // concepts.json — a republished pack swaps the hash and
            // the stale parse is dropped inside get(). Without a
            // registry entry there is nothing to guard against, so
            // the pack is read fresh exactly as before.
            val registryHash = _downloadedSectionsFlow.value
                .firstOrNull { it.sectionPath == "concepts.json" }?.hash
            if (registryHash != null) {
                conceptsEnvelopeCache.get("concepts.json", registryHash)
                    ?.let { return@withContext it }
            }
            // Encrypted sections_cache (phase-2 hardening): read through
            // the central crypto point like every other section getter.
            val jsonString = readSectionText("concepts.json") ?: return@withContext null
            val parsed = moshi.adapter(ConceptsEnvelope::class.java).fromJson(jsonString)
            if (parsed != null && registryHash != null) {
                conceptsEnvelopeCache.put("concepts.json", registryHash, jsonString.length, parsed)
                // Item Z: persist the renderable summary (same
                // filters the UI applies: non-blank id for topics;
                // non-blank subject+topic for the subject count) so
                // the Library card renders instantly next launch.
                val allUnits = parsed.units.orEmpty()
                val unitCount = allUnits.count { !it.id.isNullOrBlank() }
                val subjectCount = allUnits
                    .filter { !it.subject.isNullOrBlank() && !it.topic.isNullOrBlank() }
                    .map { it.subject!!.trim() }
                    .distinct()
                    .size
                persistConceptsSummary(context, registryHash, unitCount, subjectCount)
            }
            parsed
        } catch (e: Exception) {
            null
        }
    }

    /**
     * «ادرس مع ميد برو» server status (build 1.2.17, doctor's order):
     * what the manifest currently offers for concepts.json vs what is
     * on the device — (server unit count, updateAvailable). Downloads
     * NOTHING; the Library uses it to show the explicit download card
     * (pack absent) and the «new topics» badge (pack stale) so content
     * additions stay a server-only publish the student pulls on
     * demand, never a new APK. Any failure (offline, no manifest
     * entry) returns (null, false) and the UI falls back to the
     * on-device pack alone.
     */
    suspend fun conceptsServerStatus(): Pair<Int?, Boolean> =
        conceptsServerStatusDetailed().let { it.serverCount to it.updateAvailable }

    /**
     * Detailed variant of [conceptsServerStatus] (concepts fix,
     * build 1.2.21): the old Pair could not distinguish «the server
     * was checked and offers a pack» from «the check itself failed»
     * (offline with no cached manifest, fetch error) — both came
     * back (null, false), and the Library hid the whole section in
     * the second case. [ConceptsServerStatus.known] is true ONLY
     * when a manifest (live or the cached fallback inside
     * [fetchManifest]) was actually read AND carries a concepts.json
     * entry; the UI shows the explicit «تعذّر التحقق» card whenever
     * it is false, so the section never disappears silently again.
     */
    suspend fun conceptsServerStatusDetailed(): ConceptsServerStatus =
        withContext(Dispatchers.IO) {
            try {
                val manifest = fetchManifest().getOrNull()
                    ?: return@withContext ConceptsServerStatus(known = false)
                val entry = manifest.files?.get("concepts.json")
                    ?: return@withContext ConceptsServerStatus(known = false)
                val entity = _downloadedSectionsFlow.value
                    .firstOrNull { it.sectionPath == "concepts.json" }
                ConceptsServerStatus(
                    known = true,
                    serverCount = entry.count,
                    updateAvailable = sectionNeedsRefresh(entity, entry)
                )
            } catch (e: Exception) {
                ConceptsServerStatus(known = false)
            }
        }

    /**
     * Explicit concepts-pack download/update for the Library card
     * button (build 1.2.17): resolves the current manifest entry and
     * runs the standard section download (verification included).
     * Returns true only when the new pack is installed.
     */
    suspend fun downloadConceptsPack(onProgress: (Float) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val manifest = fetchManifest().getOrNull() ?: return@withContext false
                val entry = manifest.files?.get("concepts.json") ?: return@withContext false
                val ok = downloadSection("concepts.json", entry, onProgress).isSuccess
                if (ok) {
                    // Item Z: warm the process-wide parse cache and
                    // persist the renderable summary NOW, so the card
                    // the student returns to is already instant.
                    try { getConcepts() } catch (_: Exception) { }
                }
                ok
            } catch (e: Exception) {
                false
            }
        }

    /** The question-id index for the CURRENT downloaded set,
     * rebuilt only when the registry fingerprint moved (1.2.31).
     * Ids are collected topics-first then sessions, per envelope in
     * registry order — the old linear scan's exact first-hit order,
     * so an id living in two places resolves to the same record it
     * always did. Building parses each envelope once through
     * [getSubjectEnvelope], which caches the parse for later. */
    private suspend fun questionIndex(): Map<String, String> {
        val fingerprint = _downloadedSectionsFlow.value
            .joinToString(";") { "${it.sectionPath}|${it.hash}" }
        val cached = questionIdIndex
        if (cached != null && questionIdIndexFingerprint == fingerprint) return cached
        val entries = mutableListOf<Pair<String, List<String>>>()
        for (entity in _downloadedSectionsFlow.value) {
            val path = entity.sectionPath
            // Only subject-envelope families can hold bank
            // questions; the pack files (cases.json, ecg.json, …)
            // parse to empty envelopes and are skipped by shape.
            if (!path.contains('/')) continue
            val envelope = getSubjectEnvelope(path) ?: continue
            val ids = mutableListOf<String>()
            for (topic in envelope.topics.orEmpty()) {
                for (q in topic.questions.orEmpty()) {
                    q.id?.trim()?.takeIf { it.isNotEmpty() }?.let { ids.add(it) }
                }
            }
            for (session in envelope.sessions.orEmpty()) {
                for (q in session.questions.orEmpty()) {
                    q.id?.trim()?.takeIf { it.isNotEmpty() }?.let { ids.add(it) }
                }
            }
            entries.add(path to ids)
        }
        val built = buildQuestionIdIndex(entries)
        questionIdIndex = built
        questionIdIndexFingerprint = fingerprint
        return built
    }

    /**
     * Finds bank questions by their ids across the DOWNLOADED
     * subject envelopes (topics and sessions), for the concepts
     * self-test «افتح السؤال في البنك» button. Returns, per found
     * id, the question plus a display title (its topic/session
     * name, else the envelope's subject name); ids not on the
     * device are simply absent — the caller then hides the button
     * entirely. Restricted-envelope lease rules apply through
     * [getSubjectEnvelope] itself.
     *
     * Build 1.2.31: resolution goes through the question-id index —
     * after its one build, a lookup loads only the envelope(s)
     * actually holding the wanted ids (normally already cached),
     * instead of rescanning every shard per question.
     */
    suspend fun findQuestionsByIds(
        qids: Collection<String>
    ): Map<String, Pair<QuestionDto, String>> = withContext(Dispatchers.IO) {
        val wanted = qids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) return@withContext emptyMap()
        val index = questionIndex()
        val result = mutableMapOf<String, Pair<QuestionDto, String>>()
        val byPath = wanted.groupBy { index[it] }
        for ((path, ids) in byPath) {
            if (path == null) continue
            val envelope = getSubjectEnvelope(path) ?: continue
            val idSet = ids.toSet()
            for (topic in envelope.topics.orEmpty()) {
                for (q in topic.questions.orEmpty()) {
                    val id = q.id?.trim()
                    if (id != null && id in idSet && id !in result) {
                        result[id] = q to (topic.name ?: envelope.subjectName ?: "")
                    }
                }
            }
            for (session in envelope.sessions.orEmpty()) {
                for (q in session.questions.orEmpty()) {
                    val id = q.id?.trim()
                    if (id != null && id in idSet && id !in result) {
                        result[id] = q to (session.name ?: envelope.subjectName ?: "")
                    }
                }
            }
        }
        result
    }

    /** Single-id convenience over [findQuestionsByIds] — same
     * record, same title, same null-when-absent contract as the
     * pre-1.2.31 linear scan. */
    suspend fun findQuestionById(qid: String): Pair<QuestionDto, String>? =
        findQuestionsByIds(listOf(qid))[qid.trim()]

    suspend fun getAuscultationArEnvelope(): AuscultationArEnvelope? = getCachedPack("auscultation_ar.json") {
        moshi.adapter(AuscultationArEnvelope::class.java).fromJson(it)
    }

    suspend fun downloadAuscultationAr(): Result<AuscultationArEnvelope> = withContext(Dispatchers.IO) {
        val file = getSectionFile("auscultation_ar.json")
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            val fileUrl = "${WataniApi.BASE_URL}auscultation_ar.json"
            val resp = client.newCall(okhttp3.Request.Builder().url(fileUrl).build()).execute()
            if (!resp.isSuccessful) {
                // Auscultation AR layer (Nov 2026): the Arabic
                // description file is an OPTIONAL overlay that is
                // currently absent server-side (404). Its absence is
                // fully silent — no failure is surfaced to the user;
                // the caller treats any non-success as "no Arabic
                // overlay" and the audio material + cache layer are
                // untouched. (Result.failure is kept as the internal
                // signal only; the screen never displays it.)
                resp.close()
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("auscultation_ar unavailable (HTTP ${resp.code})"))
            }
            val body = resp.body
                ?: return@withContext Result.failure(Exception("استجابة فارغة من الخادم."))
            body.byteStream().use { inputStream ->
                java.io.FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val jsonString = tempFile.readText(Charsets.UTF_8)
            val parsed = moshi.adapter(AuscultationArEnvelope::class.java).fromJson(jsonString)
            val clips = parsed?.clips
            if (parsed == null || clips.isNullOrEmpty() || parsed.count != clips.size) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("محتوى ملف الوصف العربي غير مطابق"))
            }

            // Phase-2 hardening: validated plaintext is stored
            // encrypted through the central section write point.
            if (!writeSectionText("auscultation_ar.json", jsonString)) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("تعذر حفظ ملف الوصف العربي"))
            }
            if (tempFile.exists()) tempFile.delete()
            Result.success(parsed)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(Exception("تعذر تحميل الوصف العربي — تحقق من الاتصال وحاول مجددًا.", e))
        }
    }

    fun getAuscultationClipFile(fileName: String): File {
        val dir = File(context.filesDir, "auscultation_clips")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName.replace('/', '_'))
    }

    suspend fun downloadAuscultationClip(fileName: String, expectedSize: Long = 0L, onProgress: (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getAuscultationClipFile(fileName)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            if (targetFile.exists() && targetFile.length() > 0 && (expectedSize <= 0 || targetFile.length() == expectedSize)) {
                return@withContext Result.success(targetFile)
            }
            val fileUrl = "${WataniApi.BASE_URL}$fileName"
            val request = okhttp3.Request.Builder().url(fileUrl).build()
            val resp = client.newCall(request).execute()
            if (!resp.isSuccessful) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("فشل تحميل ملف الصوت (HTTP ${resp.code})"))
            }
            val body = resp.body ?: return@withContext Result.failure(Exception("استجابة صوتية فارغة"))
            val inputStream = body.byteStream()
            val outputStream = java.io.FileOutputStream(tempFile)
            val buffer = ByteArray(8192)
            var read: Int
            var downloaded = 0L
            val total = body.contentLength().coerceAtLeast(1L)
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                downloaded += read
                onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
            outputStream.close()
            inputStream.close()
            if (expectedSize > 0 && tempFile.length() != expectedSize) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("حجم الملف غير مطابق للمتوقع"))
            }
            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            if (targetFile.exists()) targetFile.delete()
            Result.failure(Exception("تعذر تحميل الصوت — تحقق من الاتصال وحاول مجددًا.", e))
        }
    }

    // ---------------- Library (library.json + books/videos) ----------------

    private val libraryAdapter get() = moshi.adapter(LibraryEnvelope::class.java)

    suspend fun getLibraryEnvelope(): LibraryEnvelope? {
        // Gentle lease: library.json is a restricted file — the
        // lease check stays FIRST, before any cached value (1.2.27
        // rule, same as getSubjectEnvelope).
        val leaseExpired = try {
            WorkerActivationApi.isRestrictedLeaseExpired(context)
        } catch (e: Exception) {
            return null
        }
        if (leaseExpired) return null
        // Books never surface in-app (see LibraryEnvelope.videosOnly):
        // the raw parse is what the 1.2.48 pack cache holds, and the
        // videosOnly projection is applied per call as before.
        return getCachedPack("library.json") { libraryAdapter.fromJson(it) }?.videosOnly()
    }

    suspend fun fetchLibrary(retries: Int = 3): Result<LibraryEnvelope?> = withContext(Dispatchers.IO) {
        // Cache audit fix F3 (1.2.45): freshness short-circuit —
        // before this, EVERY library entry re-downloaded the whole
        // index even when the copy on disk was already the
        // manifest's current one. The stored copy's hash (computed
        // from its own bytes with the family algorithm) is compared
        // with the manifest entry through the same TTL snapshot the
        // refresh checks use; a match serves the disk copy with no
        // download at all. Any doubt (no snapshot, no stored copy,
        // no declared hash) falls through to the full fetch below.
        val snapshotEntry = fetchManifestForRefresh()?.files?.get("library.json")
        if (snapshotEntry != null) {
            val storedText = readSectionText("library.json")
            val storedHash = storedText?.let {
                SectionVerification.manifestHashHex("library.json", it.toByteArray(Charsets.UTF_8))
            }
            if (storedHash != null &&
                storedHash.equals(snapshotEntry.hash?.trim().orEmpty(), ignoreCase = true)
            ) {
                getLibraryEnvelope()?.let { return@withContext Result.success(it) }
            }
        }
        // library.json is a restricted path (contract): via the Worker
        // gateway with the activation token. Null = not activated.
        val libraryRequest = WorkerActivationApi.contentRequest(context, "library.json")
            ?: return@withContext Result.failure(Exception("المكتبة تحتاج كود تفعيل — فعّل حسابك أولاً."))
        var lastError: Exception? = null
        for (attempt in 1..retries) {
            try {
                val resp = client.newCall(libraryRequest).execute()
                if (resp.code == 404) {
                    return@withContext Result.success(null)
                }
                if (resp.code == 401 || resp.code == 403) {
                    // M3: revoked activation — no retry, no cached
                    // fallback, prompt event fires from the gateway path.
                    return@withContext Result.failure(gatewayAuthFailure(context))
                }
                if (!resp.isSuccessful) {
                    lastError = Exception("فشل جلب المكتبة — تأكد من اتصال الإنترنت.")
                    if (attempt < retries) {
                        Thread.sleep(700L * attempt)
                        continue
                    }
                    break
                }
                val body = resp.body ?: run {
                    lastError = Exception("استجابة فارغة من الخادم.")
                    null
                } ?: continue
                val text = body.string()
                val parsed = libraryAdapter.fromJson(text)
                    ?: throw Exception("ملف المكتبة غير صالح")
                // Phase-2 hardening: the index is verified (parsed)
                // on plaintext above, then stored ENCRYPTED through
                // the central section write point. F-07 spirit kept:
                // a failed save surfaces as a failure, never as
                // success, and the old copy stays as fallback.
                if (!writeSectionText("library.json", text)) {
                    throw Exception("تعذر حفظ ملف المكتبة على الجهاز")
                }
                WorkerActivationApi.noteServerVerified(context)
                // Books never surface in-app (see LibraryEnvelope.videosOnly);
                // the stored copy stays complete — only the returned
                // envelope is filtered.
                return@withContext Result.success(parsed.videosOnly())
            } catch (e: Exception) {
                lastError = e
                if (attempt < retries) Thread.sleep(700L * attempt)
            }
        }
        // Offline fallback: if we have a cached copy, serve it rather than failing.
        val cached = getLibraryEnvelope()
        if (cached != null) return@withContext Result.success(cached)
        Result.failure(Exception("تعذر تحميل المكتبة — تحقق من الاتصال وحاول مجددًا.", lastError))
    }

    fun getLibraryFile(item: LibraryItem): File {
        val dir = File(context.filesDir, "library_files_plain")
        if (!dir.exists()) dir.mkdirs()
        val rawName = item.id ?: item.fileUrl?.substringAfterLast('/') ?: "item"
        val safe = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = item.fileUrl?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 && it.none { c -> !c.isLetterOrDigit() } }
        val name = if (ext != null && !safe.endsWith(".$ext")) "$safe.$ext" else safe
        return File(dir, name)
    }

    /** Plain side-car SRT file for a video, next to its plain file.
     * Paired strictly by the same item (same base name), never by guesswork:
     * the SRT belongs to this item's id/file only. Stored in the app's
     * private filesDir (not external storage) and deleted with the item.
     * LEGACY layout for HLS items (pre-phase-3): new HLS downloads keep
     * the SRT encrypted inside the encrypted tree (see
     * [getLibraryEncryptedSubtitleFile]); this path remains for the
     * non-HLS flow and for migration/deletion of legacy copies. */
    fun getLibrarySubtitleFile(item: LibraryItem): File {
        val plain = getLibraryFile(item)
        return File(plain.parentFile, "${plain.nameWithoutExtension}.srt")
    }

    private fun libraryItemSafeName(item: LibraryItem): String {
        val rawName = item.id ?: item.fileUrl?.substringAfterLast('/')?.substringBefore('?') ?: "item"
        return rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    /** Encrypted SRT side-car of an HLS item, inside its encrypted tree:
     * same file name as the legacy plain side-car, but the CONTENT is in
     * [sy.medpro.watani.security.ContentEncryption] format (phase-3 task
     * decision: SRT side-cars are encrypted too, closing the documented
     * U3 gap for HLS items). */
    fun getLibraryEncryptedSubtitleFile(item: LibraryItem): File =
        File(getLibraryHlsEncDir(item), "${libraryItemSafeName(item)}.srt")

    /** Cache temp the encrypted SRT is decrypted into at presentation
     * time only. Name carries the legacy `decrypt_temp_` prefix, so the
     * app-close sweep (TempDecryptedFiles) collects it even after a
     * process kill; [closeLibraryItem] deletes it deterministically. */
    private fun getLibrarySubtitleTempFile(item: LibraryItem): File {
        val file = File(context.cacheDir, "decrypt_temp_${libraryItemSafeName(item)}_srt.srt")
        sy.medpro.watani.ui.security.TempDecryptedFiles.track(file)
        return file
    }

    fun getLibrarySubtitleFileIfExists(item: LibraryItem): File? {
        if (isHlsItem(item)) {
            val enc = getLibraryEncryptedSubtitleFile(item)
            if (enc.exists()) {
                // Decrypted ONLY when presented to the player (§4.2
                // principle, same as the playlists): a small text file
                // in cacheDir, never a plaintext copy in filesDir.
                val bytes = sy.medpro.watani.security.ContentEncryption
                    .decryptFileToBytes(enc) ?: return null
                val temp = getLibrarySubtitleTempFile(item)
                return try {
                    temp.writeBytes(bytes)
                    temp
                } catch (_: Exception) {
                    null
                }
            }
        }
        val f = getLibrarySubtitleFile(item)
        return if (f.exists()) f else null
    }

    /** HLS items: fileUrl ends with .m3u8. The offline copy is the whole
     * playlist tree (master + variants + segments) in one directory, so a
     * local master playlist plays offline with its relative segments. */
    fun isHlsItem(item: LibraryItem): Boolean =
        item.fileUrl?.substringBefore('?')?.endsWith(".m3u8", ignoreCase = true) == true

    /** LEGACY plain HLS tree dir (pre-phase-3 downloads). Kept only so
     * migration and deletion can find old trees; nothing new is written
     * here for HLS items. */
    fun getLibraryHlsDir(item: LibraryItem): File {
        val dir = File(context.filesDir, "library_files_plain")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, libraryItemSafeName(item))
    }

    fun getLibraryHlsMasterFile(item: LibraryItem): File {
        val name = item.fileUrl?.substringAfterLast('/')?.substringBefore('?')?.takeIf { it.isNotBlank() } ?: "master.m3u8"
        return File(getLibraryHlsDir(item), name)
    }

    /** Phase-3 (§4.2): the encrypted HLS tree root for an item —
     * `filesDir/library_hls_enc/<itemId>/`, same relative file names as
     * the origin tree, every file's content in ContentEncryption format,
     * plus a small `hls_meta.json` (names + PLAINTEXT sizes) that is the
     * integrity basis replacing the old whole-tree byte-sum check. */
    fun getLibraryHlsEncDir(item: LibraryItem): File {
        val root = File(context.filesDir, "library_hls_enc")
        if (!root.exists()) root.mkdirs()
        return File(root, libraryItemSafeName(item))
    }

    fun getLibraryHlsEncMasterFile(item: LibraryItem): File {
        val name = item.fileUrl?.substringAfterLast('/')?.substringBefore('?')?.takeIf { it.isNotBlank() } ?: "master.m3u8"
        return File(getLibraryHlsEncDir(item), name)
    }

    private fun hlsMetaFile(dir: File): File = File(dir, "hls_meta.json")

    /** Reads `hls_meta.json`: file name -> plaintext size. Null when the
     * meta file is missing or unparseable (tree treated as absent). */
    private fun readHlsMeta(dir: File): Map<String, Long>? {
        val meta = hlsMetaFile(dir)
        if (!meta.exists()) return null
        return try {
            val filesJson = org.json.JSONObject(meta.readText(Charsets.UTF_8))
                .getJSONObject("files")
            val out = mutableMapOf<String, Long>()
            val keys = filesJson.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = filesJson.getLong(k)
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun writeHlsMeta(dir: File, files: Map<String, Long>): Boolean {
        return try {
            val filesJson = org.json.JSONObject()
            var total = 0L
            for ((name, size) in files) {
                filesJson.put(name, size)
                total += size
            }
            val root = org.json.JSONObject()
                .put("plainTotal", total)
                .put("files", filesJson)
            hlsMetaFile(dir).writeText(root.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    /** §4.3 downloaded check for the encrypted tree: meta present, the
     * master listed, and every listed file present at exactly
     * plaintext size + 28 (12-byte IV + 16-byte GCM tag). A segment that
     * fails the GCM tag later, at open, fails playback explicitly and
     * the tree is purged there — the same level the book flow uses
     * (size at rest, header/tag verified at open). */
    fun isHlsEncTreeValid(item: LibraryItem): Boolean {
        val dir = getLibraryHlsEncDir(item)
        if (!dir.exists()) return false
        val meta = readHlsMeta(dir) ?: return false
        val masterName = getLibraryHlsEncMasterFile(item).name
        if (!meta.containsKey(masterName)) return false
        return meta.all { (name, plainSize) ->
            val f = File(dir, name)
            f.exists() && f.length() == plainSize + 28L
        }
    }

    private fun hlsDirTotalBytes(dir: File): Long =
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun isHlsItemDownloaded(item: LibraryItem): Boolean {
        if (isHlsEncTreeValid(item)) return true
        // Legacy plain tree, still downloadable-looking until the
        // scheduled migration (§4.4) re-encrypts or invalidates it.
        val dir = getLibraryHlsDir(item)
        if (!dir.exists() || !getLibraryHlsMasterFile(item).exists()) return false
        val expected = item.size ?: 0L
        return expected <= 0L || hlsDirTotalBytes(dir) == expected
    }

    /**
     * Phase-3 §4.4 migration of ONE legacy plain HLS tree, modelled on
     * [migrateAnyPlainLibraryFile]: the plain tree is first verified
     * complete by the old rule (whole-tree byte sum == item.size), then
     * re-encrypted file by file into a temp dir, the meta is written,
     * the temp dir is renamed into place atomically, the plain SRT
     * side-car is encrypted into the tree, and only then is the plain
     * tree deleted. On ANY failure both copies are dropped and the item
     * shows as not downloaded (§4.4) — never a half-encrypted tree.
     * Runs on IO (called from LibraryViewModel.loadLibrary and lazily
     * from [openLibraryItem]); NEVER from a UI state check.
     */
    fun migratePlainHlsTree(item: LibraryItem): Boolean {
        if (!isHlsItem(item)) return false
        val plainDir = getLibraryHlsDir(item)
        val plainSrt = getLibrarySubtitleFile(item)
        if (isHlsEncTreeValid(item)) {
            // Already migrated earlier; drop any plain leftover.
            if (plainDir.exists()) plainDir.deleteRecursively()
            if (plainSrt.exists()) plainSrt.delete()
            return true
        }
        if (!plainDir.exists() || !getLibraryHlsMasterFile(item).exists()) return false
        val expected = item.size ?: 0L
        if (expected > 0 && hlsDirTotalBytes(plainDir) != expected) {
            // Incomplete/corrupt legacy tree: §4.4 — invalidate, do not bless.
            plainDir.deleteRecursively()
            if (plainSrt.exists()) plainSrt.delete()
            return false
        }
        val encDir = getLibraryHlsEncDir(item)
        val tmpDir = File(encDir.parentFile, encDir.name + ".migtmp")
        fun failMigration(): Boolean {
            tmpDir.deleteRecursively()
            encDir.deleteRecursively()
            plainDir.deleteRecursively()
            if (plainSrt.exists()) plainSrt.delete()
            return false
        }
        try {
            if (tmpDir.exists()) tmpDir.deleteRecursively()
            tmpDir.mkdirs()
            val meta = mutableMapOf<String, Long>()
            val plainFiles = plainDir.walkTopDown().filter { it.isFile }.toList()
            for (plainFile in plainFiles) {
                val target = File(tmpDir, plainFile.name)
                if (!sy.medpro.watani.security.ContentEncryption
                        .encryptFile(context, plainFile, target)
                ) return failMigration()
                meta[plainFile.name] = plainFile.length()
            }
            if (plainSrt.exists()) {
                val srtTarget = File(tmpDir, plainSrt.name)
                if (!sy.medpro.watani.security.ContentEncryption
                        .encryptFile(context, plainSrt, srtTarget)
                ) return failMigration()
            }
            if (!writeHlsMeta(tmpDir, meta)) return failMigration()
            if (encDir.exists()) encDir.deleteRecursively()
            if (!tmpDir.renameTo(encDir)) return failMigration()
        } catch (_: Exception) {
            return failMigration()
        }
        plainDir.deleteRecursively()
        if (plainSrt.exists()) plainSrt.delete()
        return isHlsEncTreeValid(item)
    }

    /** Scheduled §4.4 migration sweep for every HLS item in the index.
     * Called once from LibraryViewModel.loadLibrary on IO — heavy disk
     * work that must never run inside a UI downloaded-state check. */
    suspend fun migratePlainHlsTrees(items: List<LibraryItem>) = withContext(Dispatchers.IO) {
        for (item in items) {
            try {
                migratePlainHlsTree(item)
            } catch (_: Exception) {
                // One bad tree must not stop the rest of the sweep.
            }
        }
    }

    private fun resolvePlaylistUrl(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return java.net.URL(java.net.URL(baseUrl), ref).toString()
    }

    private fun playlistFileName(url: String): String =
        url.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: "file"

    /** Downloads an HLS tree: master + variant playlists + all segments,
     * preserving relative names. No Range resume in this version (fresh
     * temp dir per attempt); the PLAINTEXT byte total must equal
     * item.size, exactly like the single-file flow.
     *
     * Phase-3 (§4.2): every fetched file is verified as plaintext and
     * then stored ENCRYPTED — segments land in a `.part` plaintext file
     * inside the tree's temp dir only for the moments between fetch and
     * [sy.medpro.watani.security.ContentEncryption.encryptFile], which
     * are deleted immediately; playlists are encrypted from memory. The
     * finished tree (temp dir + `hls_meta.json`) is renamed into
     * `library_hls_enc/<itemId>/` atomically, so a half-encrypted tree
     * never appears as downloaded. No plaintext segment persists. */
    private suspend fun downloadHlsItem(
        item: LibraryItem,
        onProgress: (Float) -> Unit
    ): LibraryDownloadResult {
        val url = item.fileUrl ?: return LibraryDownloadResult.fail("رابط الملف غير متوفر في محتوى المكتبة")
        if (isHlsEncTreeValid(item)) {
            return LibraryDownloadResult.ok(getLibraryHlsEncMasterFile(item))
        }
        // A legacy plain tree still on disk: migrate it instead of
        // re-downloading (§4.4); a failed migration invalidates it and
        // the fresh download below proceeds from zero.
        if (getLibraryHlsDir(item).exists()) {
            migratePlainHlsTree(item)
            if (isHlsEncTreeValid(item)) {
                return LibraryDownloadResult.ok(getLibraryHlsEncMasterFile(item))
            }
        }
        val encDir = getLibraryHlsEncDir(item)
        val tmpDir = File(encDir.parentFile, encDir.name + ".dltmp")
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        if (encDir.exists()) encDir.deleteRecursively()
        tmpDir.mkdirs()
        var downloaded = 0L
        val total = item.size ?: 0L
        val meta = mutableMapOf<String, Long>()
        fun fail(msg: String): LibraryDownloadResult {
            tmpDir.deleteRecursively()
            return LibraryDownloadResult.fail(msg)
        }
        // Guards the shared [meta]/[downloaded] bookkeeping now that
        // segments fetch in parallel (1.2.48, performance item 9):
        // the network fetch and the encryption stay outside the lock
        // (each segment owns its files), only the three bookkeeping
        // lines are serialized.
        val bookkeepingLock = Any()
        /** Fetches one segment to a transient `.part`, encrypts it to
         * its final name inside the temp tree, deletes the `.part`. */
        fun fetchSegmentEncrypted(targetUrl: String, fileName: String): Boolean {
            val request = try {
                WorkerActivationApi.contentRequestForUrl(context, targetUrl)
            } catch (_: IllegalArgumentException) {
                return false
            } ?: return false
            val partFile = File(tmpDir, "$fileName.part")
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return false
                    val body = resp.body ?: return false
                    partFile.parentFile?.mkdirs()
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(partFile).use { output -> input.copyTo(output) }
                    }
                }
            } catch (_: Exception) {
                partFile.delete()
                return false
            }
            val plainSize = partFile.length()
            val ok = sy.medpro.watani.security.ContentEncryption
                .encryptFile(context, partFile, File(tmpDir, fileName))
            partFile.delete()
            if (!ok) return false
            synchronized(bookkeepingLock) {
                meta[fileName] = plainSize
                downloaded += plainSize
                if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 0.9f))
            }
            return true
        }
        /** Fetches every segment of one playlist with bounded
         * parallelism (1.2.48, performance item 9 — a video is dozens
         * of independent segment files; the pre-1.2.48 chain fetched
         * them one by one). False when any segment failed; the whole
         * batch settles before the caller acts on the verdict, so a
         * failure never races the temp-tree cleanup in [fail]. */
        suspend fun fetchSegmentsParallel(playlistUrl: String, playlistText: String): Boolean {
            val segUrls = HlsPlaylistRewrite.mediaReferences(playlistText)
                .map { ref -> resolvePlaylistUrl(playlistUrl, ref) }
            if (segUrls.isEmpty()) return true
            val results = ParallelFetch.mapBounded(
                segUrls, ParallelFetch.SEGMENT_CONCURRENCY
            ) { segUrl ->
                fetchSegmentEncrypted(segUrl, playlistFileName(segUrl))
            }
            return results.all { it }
        }
        /** Encrypts playlist text (already held in memory) into the tree. */
        fun storePlaylistEncrypted(text: String, fileName: String): Boolean {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (!sy.medpro.watani.security.ContentEncryption
                    .encryptBytesToFile(bytes, File(tmpDir, fileName))
            ) return false
            meta[fileName] = bytes.size.toLong()
            downloaded += bytes.size
            return true
        }
        fun readText(targetUrl: String): String? {
            val request = try {
                WorkerActivationApi.contentRequestForUrl(context, targetUrl)
            } catch (_: IllegalArgumentException) {
                return null
            } ?: return null
            return try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    resp.body?.string()
                }
            } catch (_: Exception) {
                null
            }
        }
        val masterText = readText(url)
            ?: return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
        if (!storePlaylistEncrypted(masterText, playlistFileName(url))) {
            return fail("فشل تشفير الملف")
        }
        val variantUrls = mutableListOf<String>()
        val masterLines = masterText.lines()
        for (i in masterLines.indices) {
            if (masterLines[i].startsWith("#EXT-X-STREAM-INF")) {
                val next = masterLines.getOrNull(i + 1)?.trim().orEmpty()
                if (next.isNotEmpty() && !next.startsWith("#")) {
                    variantUrls.add(resolvePlaylistUrl(url, next))
                }
            }
        }
        val playlistsToScan = if (variantUrls.isEmpty()) listOf(url to masterText) else emptyList()
        for (variantUrl in variantUrls) {
            val variantText = readText(variantUrl)
                ?: return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
            if (!storePlaylistEncrypted(variantText, playlistFileName(variantUrl))) {
                return fail("فشل تشفير الملف")
            }
            if (!fetchSegmentsParallel(variantUrl, variantText)) {
                return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
            }
        }
        for ((playlistUrl, playlistText) in playlistsToScan) {
            if (!fetchSegmentsParallel(playlistUrl, playlistText)) {
                return fail("فشل التحميل — تأكد من اتصال الإنترنت.")
            }
        }
        // Integrity, §4.3: plaintext byte total (recorded per file in
        // the meta) must equal item.size — the on-disk ciphertext total
        // is larger by 28 bytes per file and is never compared here.
        if (total > 0 && downloaded != total) {
            return fail("حجم الملف غير مطابق للمتوقع")
        }
        if (!writeHlsMeta(tmpDir, meta)) {
            return fail("تعذر حفظ ملف المكتبة على الجهاز")
        }
        if (!tmpDir.renameTo(encDir)) {
            return fail("تعذر حفظ الملف على الجهاز")
        }
        downloadLibrarySubtitle(item)
        onProgress(1f)
        return LibraryDownloadResult.ok(getLibraryHlsEncMasterFile(item))
    }

    /** Best-effort download of the item's SRT side-car. Never fails the
     * video download: a missing SRT only means subtitles are unavailable
     * for that video until the next download attempt.
     *
     * M10: subtitles go through the same switch point
     * ([WorkerActivationApi.contentRequestForUrl]), never a raw link —
     * so a restricted subtitle URL is gateway-checked like any other
     * restricted file, and a malformed (double-encoded) URL is rejected
     * instead of fetched. */
    private fun downloadLibrarySubtitle(item: LibraryItem) {
        val url = item.subtitleUrl?.takeIf { it.isNotBlank() } ?: return
        try {
            val request = try {
                WorkerActivationApi.contentRequestForUrl(context, url)
            } catch (_: IllegalArgumentException) {
                return
            } ?: return
            if (isHlsItem(item)) {
                // Phase-3: an HLS item's SRT is stored ENCRYPTED inside
                // its encrypted tree. It is fetched to a transient temp,
                // encrypted into place, and the temp is deleted — no
                // plaintext SRT persists in filesDir.
                val target = getLibraryEncryptedSubtitleFile(item)
                target.parentFile?.mkdirs()
                val tmp = java.io.File(target.parentFile, "${target.name}.tmp")
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return
                    val body = resp.body ?: return
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    sy.medpro.watani.security.ContentEncryption
                        .encryptFile(context, tmp, target)
                    tmp.delete()
                }
                return
            }
            val target = getLibrarySubtitleFile(item)
            // Detector fix (b9): write to a temp file and rename — a
            // direct write interrupted mid-stream left a partial SRT
            // that later counted as "existing".
            val tmp = java.io.File(target.parentFile, "${target.name}.tmp")
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return
                val body = resp.body ?: return
                body.byteStream().use { input ->
                    java.io.FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                tmp.renameTo(target)
            }
        } catch (_: Exception) {
            // SRT download failure is non-fatal by design.
        }
    }

    fun getLibraryEncryptedFile(item: LibraryItem): File {
        val dir = sy.medpro.watani.security.ContentEncryption.getEncryptedDir(context)
        val rawName = item.id ?: item.fileUrl?.substringAfterLast('/') ?: "item"
        val safe = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_").replace(".", "_")
        return File(dir, "$safe.enc")
    }

    private fun getLibraryTempFile(target: File): File = File(target.parentFile, "${target.name}.part")

    private fun isItemDownloadedAndEncrypted(item: LibraryItem): Boolean {
        val encFile = getLibraryEncryptedFile(item)
        if (!encFile.exists()) return false
        // Encrypted-aware existence check: length >= 28 (12 IV + 16 tag)
        // and matches plaintext size + 28 when plaintext size is declared.
        // Hash check is not valid for ciphertext (integrity was verified on plaintext before encryption at download).
        val len = encFile.length()
        if (len < 28) return false
        val expectedSize = item.size ?: 0L
        if (expectedSize > 0 && len != expectedSize + 28) return false
        return true
    }

    private fun migrateAnyPlainLibraryFile(item: LibraryItem): Boolean {
        val plainFile = getLibraryFile(item)
        if (!plainFile.exists()) return false
        // Never bless a stale/corrupt plain copy: an unverified plain file
        // becomes "downloaded but broken" after migration. Drop it so the
        // user re-downloads a clean copy instead of being stuck.
        if (verifyLibraryFile(plainFile, item) != null) {
            plainFile.delete()
            return false
        }
        if (isPdfItem(item) && !hasPdfHeader(plainFile)) {
            plainFile.delete()
            return false
        }
        val encryptedFile = getLibraryEncryptedFile(item)
        return sy.medpro.watani.security.ContentEncryption.encryptFile(context, plainFile, encryptedFile).also {
            if (it) plainFile.delete()
        }
    }

    private fun expectedHashFormat(hash: String?): String? {
        if (hash.isNullOrBlank()) return null
        val h = hash.trim().lowercase()
        return when {
            h.matches(Regex("^[a-f0-9]{64}$")) -> "sha256"
            h.matches(Regex("^[a-f0-9]{40}$")) -> "sha1"
            h.matches(Regex("^[a-f0-9]{32}$")) -> "md5"
            else -> null
        }
    }

    private fun hashFile(file: File, algo: String): String {
        val digest = java.security.MessageDigest.getInstance(algo)
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read: Int
            while (input.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Verifies an existing file against library.json. Returns null when verified, otherwise a reason. */
    private fun verifyLibraryFile(file: File, item: LibraryItem): String? {
        if (!file.exists()) return "الملف غير موجود"
        val expectedSize = item.size ?: 0L
        if (expectedSize > 0 && file.length() != expectedSize) return "حجم الملف غير مطابق"
        val algo = expectedHashFormat(item.hash)
        if (algo != null) {
            val actual = hashFile(file, algo)
            if (!actual.equals(item.hash!!.trim(), ignoreCase = true)) return "بصمة الملف غير مطابقة"
        }
        return null
    }

    /** A downloaded book must start with the PDF magic. Gateway/error
     * bytes that happen to match the declared size must never be blessed
     * as a downloaded book — they only surface later as a corrupt open. */
    private fun hasPdfHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 5) return false
        return try {
            val head = ByteArray(5)
            java.io.FileInputStream(file).use { it.read(head) == 5 } && head.decodeToString() == "%PDF-"
        } catch (_: Exception) {
            false
        }
    }

    private fun isPdfItem(item: LibraryItem): Boolean =
        item.fileUrl?.substringBefore('?')?.endsWith(".pdf", ignoreCase = true) == true

    fun hashVerificationFor(item: LibraryItem): HashVerification {
        if (item.hash.isNullOrBlank()) return HashVerification.SIZE_ONLY
        return if (expectedHashFormat(item.hash) != null) HashVerification.VERIFIED else HashVerification.UNKNOWN_FORMAT
    }

    fun isLibraryItemDownloaded(item: LibraryItem): Boolean {
        if (isHlsItem(item)) return isHlsItemDownloaded(item)
        if (isItemDownloadedAndEncrypted(item)) return true
        // Migration succeeds → returns encrypted state result directly
        if (migrateAnyPlainLibraryFile(item)) return isItemDownloadedAndEncrypted(item)
        val file = getLibraryFile(item)
        return file.exists() && verifyLibraryFile(file, item) == null
    }

    fun listDownloadedLibraryItems(items: List<LibraryItem>): List<LibraryItem> =
        items.filter { it.fileUrl != null && isLibraryItemDownloaded(it) }

    fun deleteLibraryItem(item: LibraryItem): Boolean {
        val encFile = getLibraryEncryptedFile(item)
        val plainFile = getLibraryFile(item)
        val plainTemp = getLibraryTempFile(plainFile)
        if (plainTemp.exists()) plainTemp.delete()
        plainFile.delete()
        encFile.delete()
        getLibrarySubtitleFile(item).delete()
        // Playback-session temps first: their names are derived from
        // the tree's meta, which is about to be deleted.
        deleteHlsSessionTemps(item)
        // Legacy plain HLS tree + the phase-3 encrypted tree (same
        // recursive delete — the encrypted layout changes only file
        // contents, never the tree shape, so every existing deletion
        // path keeps working on it).
        val hlsDir = getLibraryHlsDir(item)
        if (hlsDir.exists()) hlsDir.deleteRecursively()
        val hlsEncDir = getLibraryHlsEncDir(item)
        if (hlsEncDir.exists()) hlsEncDir.deleteRecursively()
        val srtTemp = getLibrarySubtitleTempFile(item)
        if (srtTemp.exists()) srtTemp.delete()
        return !plainFile.exists() && !encFile.exists() && !hlsDir.exists() && !hlsEncDir.exists()
    }

    suspend fun downloadLibraryItem(
        item: LibraryItem,
        onProgress: (Float) -> Unit = {}
    ): LibraryDownloadResult = withContext(Dispatchers.IO) {
        val integrityResult = sy.medpro.watani.security.DeviceIntegrityCheck.checkDeviceIntegrity(context)
        if (integrityResult is sy.medpro.watani.security.DeviceIntegrityCheck.IntegrityResult.Compromised) {
            return@withContext LibraryDownloadResult.fail(sy.medpro.watani.security.DeviceIntegrityCheck.getBlockMessage(integrityResult))
        }

        val url = item.fileUrl
            ?: return@withContext LibraryDownloadResult.fail("رابط الملف غير متوفر في محتوى المكتبة")

        if (isHlsItem(item)) {
            return@withContext downloadHlsItem(item, onProgress)
        }

        if (isItemDownloadedAndEncrypted(item)) {
            return@withContext LibraryDownloadResult.ok(getLibraryEncryptedFile(item))
        }

        val plainTarget = getLibraryFile(item)
        val plainTemp = getLibraryTempFile(plainTarget)

        var attempt = 0
        while (attempt < 2) {
            attempt++
            try {
                val existingBytes = if (plainTemp.exists()) plainTemp.length() else 0L
                // Same switch point for absolute file URLs: Pages /v1/
                // URLs under a restricted prefix (e.g. books/) go via the
                // Worker gateway; other hosts stay direct, unchanged.
                val itemRequest = try {
                    WorkerActivationApi.contentRequestForUrl(context, url)
                } catch (_: IllegalArgumentException) {
                    return@withContext LibraryDownloadResult.fail("رابط الملف غير صالح")
                } ?: return@withContext LibraryDownloadResult.fail("هذا الملف يحتاج كود تفعيل — فعّل حسابك أولاً.")
                val builder = itemRequest.newBuilder()
                if (existingBytes > 0) builder.header("Range", "bytes=$existingBytes-")
                client.newCall(builder.build()).execute().use { resp ->
                    if (resp.code == 401 || resp.code == 403) {
                        // M3: revoked activation — the prompt itself
                        // fires from the activationLost event; the
                        // failure text stands alone, no HTTP generic.
                        WorkerActivationApi.notifyActivationLost(context)
                        refreshDownloadedSections()
                        return@withContext LibraryDownloadResult.fail(
                            "انتهى التفعيل على هذا الجهاز — أعد إدخال كود التفعيل."
                        )
                    }
                    if (resp.code == 416) {
                        // Detector fix (b5): stale Range — drop the temp
                        // and let the attempt loop retry from zero.
                        plainTemp.delete()
                        if (attempt < 2) continue
                        return@withContext LibraryDownloadResult.fail("تعذر استئناف التحميل — أعد المحاولة.")
                    }
                    if (!resp.isSuccessful && resp.code != 206) {
                        return@withContext LibraryDownloadResult.fail("فشل التحميل — تأكد من اتصال الإنترنت.")
                    }
                    val isPartial = resp.code == 206
                    val startBytes = if (isPartial) existingBytes else {
                        if (plainTemp.exists()) plainTemp.delete()
                        0L
                    }
                    val body = resp.body
                        ?: return@withContext LibraryDownloadResult.fail("استجابة فارغة من الخادم.")
                    val contentLength = body.contentLength()
                    val total = when {
                        contentLength > 0 -> contentLength + startBytes
                        (item.size ?: 0L) > 0 -> item.size!! + startBytes
                        else -> 0L
                    }
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(plainTemp, startBytes > 0).use { output ->
                            val buf = ByteArray(16384)
                            var copied = startBytes
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                copied += read
                                if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 0.9f))
                            }
                            output.flush()
                        }
                    }
                }

                if (item.size != null && item.size > 0 && plainTemp.length() != item.size) {
                    plainTemp.delete()
                    if (attempt < 2) continue
                    return@withContext LibraryDownloadResult.fail("حجم الملف غير مطابق للمتوقع")
                }

                if (plainTarget.exists()) plainTarget.delete()
                if (!plainTemp.renameTo(plainTarget)) {
                    plainTemp.delete()
                    return@withContext LibraryDownloadResult.fail("تعذر حفظ الملف على الجهاز")
                }

                val reason = verifyLibraryFile(plainTarget, item)
                if (reason != null) {
                    plainTarget.delete()
                    if (attempt < 2) continue
                    return@withContext LibraryDownloadResult.fail("الملف تالف ($reason) — أعد المحاولة")
                }

                if (isPdfItem(item) && !hasPdfHeader(plainTarget)) {
                    plainTarget.delete()
                    if (attempt < 2) continue
                    return@withContext LibraryDownloadResult.fail("الملف المحمّل ليس PDF سليمًا — أعد المحاولة")
                }

                onProgress(0.95f)
                val encFile = getLibraryEncryptedFile(item)
                if (!sy.medpro.watani.security.ContentEncryption.encryptFile(context, plainTarget, encFile) { p ->
                        onProgress(0.95f + p * 0.04f)
                    }) {
                    plainTarget.delete()
                    encFile.delete()
                    return@withContext LibraryDownloadResult.fail("فشل تشفير الملف")
                }

                plainTarget.delete()
                // Side-car SRT travels with the video; its failure never
                // fails the video download (see downloadLibrarySubtitle).
                downloadLibrarySubtitle(item)
                onProgress(1f)
                return@withContext LibraryDownloadResult.ok(encFile)
            } catch (e: Exception) {
                if (attempt >= 2) {
                    // Book-download fix: KEEP the partial temp file on a
                    // network failure so the next tap resumes via Range
                    // (the gateway now serves ranges from cache) instead
                    // of restarting a 20MB book from zero every time.
                    return@withContext LibraryDownloadResult.fail(
                        "تعذر التحميل — تحقق من اتصال الإنترنت وحاول مجددًا (سيُستأنف من حيث توقف)."
                    )
                }
            }
        }
        LibraryDownloadResult.fail("تعذر إكمال التحميل")
    }

    private fun getDecryptTempFile(item: LibraryItem): File {
        val rawName = item.id ?: item.fileUrl?.substringAfterLast('/') ?: "item"
        val safe = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(context.cacheDir, "decrypt_temp_$safe")
    }

    // ------------------------------------------------------------------
    // Phase-3 (§4.2): HLS playback session.
    //
    // openLibraryItem for an HLS item no longer returns a stored file:
    // the three playlists (master + variants, each < a few KB) are
    // decrypted IN MEMORY ONLY, rewritten, and written as small temp
    // playlists in cacheDir (registered in TempDecryptedFiles, so the
    // app-close sweep collects them even after a process kill):
    //  - master: variant references become the bare temp file names;
    //  - variants: segment references become watani-enc:/// URIs that
    //    EncryptedHlsDataSource decrypts in memory while streaming.
    // Segments are NEVER written decrypted anywhere. The returned File
    // keeps the exact signature/call chain the UI already uses.
    // ------------------------------------------------------------------

    /** Deterministic session temp playlist for [fileName] of [item]. */
    private fun getHlsSessionTempFile(item: LibraryItem, fileName: String): File {
        val file = sy.medpro.watani.ui.security.TempDecryptedFiles.newTempFile(
            context, "hls_${libraryItemSafeName(item)}_$fileName"
        )
        return file
    }

    /** Every session temp this item could have produced (master +
    * variants listed in its meta). Used by close/delete. */
    private fun hlsSessionTempFiles(item: LibraryItem): List<File> {
        val names = mutableSetOf<String>()
        names += getLibraryHlsEncMasterFile(item).name
        readHlsMeta(getLibraryHlsEncDir(item))?.keys
            ?.filter { it.endsWith(".m3u8", ignoreCase = true) }
            ?.let { names += it }
        return names.map { getHlsSessionTempFile(item, it) }
    }

    private fun deleteHlsSessionTemps(item: LibraryItem) {
        for (file in hlsSessionTempFiles(item)) {
            sy.medpro.watani.ui.security.TempDecryptedFiles.delete(file)
        }
    }

    /** Builds the playback session for an encrypted HLS tree. Returns
     * the temp master playlist file, or null on any failure — including
     * a segment-independent failure here (playlist GCM failure): the
     * caller then purges the tree so the next tap re-downloads, the
     * same open-time integrity behaviour as the book flow. */
    private fun buildHlsPlaybackSession(item: LibraryItem): File? {
        val encDir = getLibraryHlsEncDir(item)
        val masterEnc = getLibraryHlsEncMasterFile(item)
        val masterBytes = sy.medpro.watani.security.ContentEncryption
            .decryptFileToBytes(masterEnc) ?: return null
        val masterText = String(masterBytes, Charsets.UTF_8)
        deleteHlsSessionTemps(item)
        // Decrypt + rewrite each variant playlist referenced by the master.
        val variantNames = HlsPlaylistRewrite.mediaReferences(masterText)
            .map { HlsPlaylistRewrite.fileNameOf(it) }
            .filter { it.endsWith(".m3u8", ignoreCase = true) }
        val itemKey = libraryItemSafeName(item)
        for (variantName in variantNames) {
            val variantBytes = sy.medpro.watani.security.ContentEncryption
                .decryptFileToBytes(File(encDir, variantName)) ?: return null
            val rewritten = HlsPlaylistRewrite.rewriteVariant(
                String(variantBytes, Charsets.UTF_8), itemKey
            )
            val temp = getHlsSessionTempFile(item, variantName)
            try {
                temp.writeText(rewritten, Charsets.UTF_8)
            } catch (_: Exception) {
                return null
            }
        }
        // A master that itself carries segments (single-playlist tree):
        // rewrite its segment references the same way.
        val rewrittenMaster = if (variantNames.isEmpty()) {
            HlsPlaylistRewrite.rewriteVariant(masterText, itemKey)
        } else {
            HlsPlaylistRewrite.rewriteMaster(masterText)
        }
        val masterTemp = getHlsSessionTempFile(item, masterEnc.name)
        return try {
            masterTemp.writeText(rewrittenMaster, Charsets.UTF_8)
            masterTemp
        } catch (_: Exception) {
            null
        }
    }

    // Serializes opens: two concurrent opens of the same book used to
    // decrypt into the same cache temp path and interleave, producing a
    // corrupt plaintext that the reader then rejected as "not in PDF
    // format".
    private val openMutex = Mutex()

    suspend fun openLibraryItem(item: LibraryItem): Result<File> = withContext(Dispatchers.IO) {
        openMutex.withLock {
        if (!isLibraryItemDownloaded(item)) {
            return@withContext Result.failure(Exception("العنصر غير محمّل"))
        }

        if (isHlsItem(item)) {
            // Phase-3 (§4.2/§4.4): a legacy plain tree still on disk is
            // migrated on this first open after the update if the
            // scheduled sweep has not reached it yet.
            if (!isHlsEncTreeValid(item) && getLibraryHlsDir(item).exists()) {
                migratePlainHlsTree(item)
            }
            if (!isHlsEncTreeValid(item)) {
                return@withContext Result.failure(Exception("تعذر العثور على الملف المحمّل"))
            }
            val sessionMaster = buildHlsPlaybackSession(item)
            if (sessionMaster == null || !sessionMaster.exists()) {
                // Playlist integrity failed at open: purge the tree so
                // the next tap re-downloads (book-flow behaviour).
                deleteLibraryItem(item)
                return@withContext Result.failure(
                    Exception("النسخة المحفوظة تالفة — حُذفت، أعد تحميل الفيديو")
                )
            }
            return@withContext Result.success(sessionMaster)
        }

        val encFile = getLibraryEncryptedFile(item)
        if (!encFile.exists()) {
            return@withContext Result.failure(Exception("تعذر العثور على الملف المحمّل"))
        }

        val tempFile = getDecryptTempFile(item)
        if (tempFile.exists()) tempFile.delete()

        val success = sy.medpro.watani.security.ContentEncryption.decryptFile(context, encFile, tempFile)
        if (!success || !tempFile.exists()) {
            tempFile.delete()
            return@withContext Result.failure(Exception("تعذر فتح الملف المحمّل — أعد تحميله"))
        }

        // Open-time integrity gate (Nov 2026, doctor's order): a stored
        // copy is trusted by size alone, so a damaged-but-right-sized
        // copy used to reach the reader as "file not in PDF format or
        // corrupted" again and again. A decrypted copy that fails the
        // size or the PDF-header check is purged together with its
        // stored copy, so the next tap cleanly re-downloads instead of
        // reopening the same corruption.
        val expectedSize = item.size ?: 0L
        val badDecrypted = (expectedSize > 0 && tempFile.length() != expectedSize) ||
            (isPdfItem(item) && !hasPdfHeader(tempFile))
        if (badDecrypted) {
            tempFile.delete()
            encFile.delete()
            return@withContext Result.failure(Exception("النسخة المحفوظة تالفة — حُذفت، أعد تحميل الكتاب"))
        }

        Result.success(tempFile)
        }
    }

    fun closeLibraryItem(item: LibraryItem) {
        val tempFile = getDecryptTempFile(item)
        if (tempFile.exists()) tempFile.delete()
        if (isHlsItem(item)) {
            // Phase-3 (§4.2): closing an HLS reader deletes its session
            // playlist temps and the decrypted SRT temp — after close,
            // no plaintext of the item remains anywhere (segments were
            // never written at all).
            deleteHlsSessionTemps(item)
            val srtTemp = getLibrarySubtitleTempFile(item)
            if (srtTemp.exists()) srtTemp.delete()
        }
    }
}

/**
 * Pure first-wins fold for the question-id index (build 1.2.31):
 * [entries] are (section path -> its question ids in scan order)
 * in registry order; an id keeps the FIRST path that claims it —
 * exactly where the old per-lookup linear scan would have found
 * it. Pure so the rule is unit-testable off-device.
 */
internal fun buildQuestionIdIndex(
    entries: List<Pair<String, List<String>>>
): Map<String, String> {
    val index = LinkedHashMap<String, String>()
    for ((path, ids) in entries) {
        for (id in ids) {
            if (!index.containsKey(id)) index[id] = path
        }
    }
    return index
}
