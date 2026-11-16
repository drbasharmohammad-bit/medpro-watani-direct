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

    /** كل أسئلة البنوك المنزّلة الصالحة (مادة ← سؤال) بترتيب الملفات. */
    fun loadPool(context: Context): List<Pair<String, QuestionDto>> {
        val dir = File(context.applicationContext.filesDir, "sections_cache")
        val list = mutableListOf<Pair<String, QuestionDto>>()
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".json") && !file.name.contains("cases") &&
                    !file.name.contains("guide") && !file.name.contains("osce")
                ) {
                    try {
                        val text = SectionCacheCrypto.readText(context, file)
                            ?: throw IllegalStateException("section cache unreadable")
                        val moshi = Moshi.Builder()
                            .add(KotlinJsonAdapterFactory()).build()
                        val env = moshi.adapter(SubjectEnvelope::class.java).fromJson(text)
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
            }
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
