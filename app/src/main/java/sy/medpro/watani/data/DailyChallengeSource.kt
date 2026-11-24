package sy.medpro.watani.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/**
 * مصدر سؤال التحدي اليومي الموحّد (بناء 1.2.30).
 *
 * تحميل مجمّع الأسئلة من كاش الأقسام المنزّلة واختيار سؤال اليوم
 * كانا يعيشان داخل شاشة «التحدي اليومي» وحدها. صارا هنا مصدرًا
 * واحدًا يقرأه سطحان: الشاشة نفسها، وإشعار «سؤال اليوم صباحًا»
 * على شاشة القفل — فيتطابق السؤال المعروض بالإشعار مع سؤال
 * الشاشة في اليوم نفسه دائمًا (نفس المجمّع ونفس صيغة الاختيار
 * في [DailyQuestionPick]).
 */
object DailyChallengeSource {

    // One Moshi for every file (1.2.31): the pool sweep used to
    // build a fresh Moshi instance PER FILE — pure waste on top of
    // the decrypt+parse it exists to do.
    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }
    private val envelopeAdapter by lazy { moshi.adapter(SubjectEnvelope::class.java) }

    /**
     * Top-level pack files in sections_cache that are NOT subject
     * envelopes and can never contribute a bank question (1.2.31).
     * Before this skip, the sweep read, decrypted and parse-attempted
     * every one of them — the ~5MB concepts pack included — on every
     * pool load, for zero questions. Names are the section paths;
     * on disk they appear sanitized via WataniRepository's
     * getSectionFile ("concepts.json" -> "concepts.json.json").
     * The older contains-filters (cases/guide/osce) stay as a
     * second net for any pack not listed here.
     */
    private val NON_ENVELOPE_PACKS = setOf(
        "auscultation.json", "auscultation_ar.json", "cases.json",
        "cases_de.json", "concepts.json", "ddx.json",
        "dosing_drills.json", "ecg.json", "guide.json",
        "library.json", "osce.json", "osce_procedures.json",
        "reverse.json"
    )

    // Pool cache (1.2.48, performance list item 1 — indexes instead
    // of full scans): loadPool used to sweep, decrypt and parse
    // EVERY downloaded shard on every call (the drawer screen and
    // the morning reminder both call it). The pool is now cached
    // against a signature of the candidate files themselves
    // (name|length|mtime per file, in name order): any download,
    // refresh or wipe changes a file's stamp and the pool rebuilds;
    // unchanged bytes return the cached pool with zero disk reads
    // beyond the directory listing.
    @Volatile
    private var cachedPoolSignature: String? = null

    @Volatile
    private var cachedPool: List<Pair<String, QuestionDto>>? = null

    private val poolLock = Any()

    /** The candidate shard files of the sweep, in stable order —
     * the exact filter [loadPool] has always applied. */
    private fun candidateFiles(dir: File): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()?.filter { file ->
            file.name.endsWith(".json") && !file.name.contains("cases") &&
                !file.name.contains("guide") && !file.name.contains("osce") &&
                file.name.removeSuffix(".json") !in NON_ENVELOPE_PACKS
        }?.sortedBy { it.name } ?: emptyList()
    }

    /** Signature of a candidate-file set: any content change,
     * addition or removal changes it. Pure — unit-testable. */
    fun poolSignature(files: List<Triple<String, Long, Long>>): String =
        files.joinToString(";") { (name, length, mtime) -> "$name|$length|$mtime" }

    /** كل أسئلة البنوك المنزّلة الصالحة (مادة ← سؤال) بترتيب الملفات. */
    fun loadPool(context: Context): List<Pair<String, QuestionDto>> {
        val dir = File(context.applicationContext.filesDir, "sections_cache")
        val files = candidateFiles(dir)
        val signature = poolSignature(
            files.map { Triple(it.name, it.length(), it.lastModified()) }
        )
        synchronized(poolLock) {
            val hit = cachedPool
            if (hit != null && cachedPoolSignature == signature) return hit
        }
        val list = mutableListOf<Pair<String, QuestionDto>>()
        files.forEach { file ->
            try {
                val text = SectionCacheCrypto.readText(context, file)
                    ?: throw IllegalStateException("section cache unreadable")
                val env = envelopeAdapter.fromJson(text)
                val subjName = env?.subjectName ?: file.name.substringBefore(".json")
                env?.topics?.forEach { topic ->
                    topic.questions?.forEach { q ->
                        if (!q.text.isNullOrBlank() && !q.options.isNullOrEmpty()) {
                            list.add(Pair(subjName, q))
                        }
                    }
                }
                env?.sessions?.forEach { session ->
                    session.questions?.forEach { q ->
                        if (!q.text.isNullOrBlank() && !q.options.isNullOrEmpty()) {
                            list.add(Pair(subjName, q))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        synchronized(poolLock) {
            cachedPool = list
            cachedPoolSignature = signature
        }
        return list
    }

    /** سؤال اليوم من مجمّع جاهز، أو null عند فراغه. */
    fun pickForDay(
        pool: List<Pair<String, QuestionDto>>,
        nowMillis: Long
    ): Pair<String, QuestionDto>? {
        val idx = DailyQuestionPick.indexFor(nowMillis, pool.size)
        return if (idx < 0) null else pool[idx]
    }

    /** سؤال اليوم مباشرة من كاش الجهاز (تحميل + اختيار). */
    fun pickToday(context: Context, nowMillis: Long = System.currentTimeMillis()): Pair<String, QuestionDto>? =
        pickForDay(loadPool(context), nowMillis)
}
