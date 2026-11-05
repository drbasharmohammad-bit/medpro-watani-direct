package sy.medpro.watani.ui.logbook

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * سجلّ الحالات الشخصي (Logbook) — نموذج الإدخال الواحد.
 *
 * بيانات تعليمية مجهّلة بقرار تصميم: لا يوجد حقل لاسم مريض أو رقم ملف أو أي
 * معرّف شخصي أصلًا، فالواجهة لا تستطيع تخزين ما لا حقل له.
 *
 * [syncedUpdatedAt] و[deleted] حالتان محليتان للمزامنة فقط: ‏[deleted] قبر
 * محلي بانتظار الرفع، و[syncedUpdatedAt] آخر updatedAt أكّد الخادم قبوله —
 * الإدخال «معلّق الرفع» متى كان updatedAt أكبر منها. لا يدخل أيٌّ منهما
 * حمولة `data` المرسلة للخادم (يُرسل الحذف كعلم بجسم الطلب نفسه).
 */
data class LogbookEntry(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** تاريخ مشاهدة الحالة فعليًا (يوم المناوبة) — قد يسبق createdAt عند الإدخال المتأخر. */
    val seenDate: Long = System.currentTimeMillis(),
    val specialty: String = "",
    val ward: String = "",
    val chiefComplaint: String = "",
    val keyFindings: String = "",
    val investigations: String = "",
    val diagnosis: String = "",
    val management: String = "",
    val lessons: String = "",
    val tags: List<String> = emptyList(),
    val deleted: Boolean = false,
    val syncedUpdatedAt: Long = 0L
) {
    /** هل يحمل الإدخال أي محتوى يستحق الحفظ/العرض؟ (قاعدة الحفظ الوحيدة) */
    fun hasContent(): Boolean =
        listOf(ward, chiefComplaint, keyFindings, investigations, diagnosis, management, lessons)
            .any { it.isNotBlank() } || tags.isNotEmpty()

    fun isPendingSync(): Boolean = updatedAt > syncedUpdatedAt

    /** حمولة البيانات كما تُخزَّن سحابيًا داخل عمود data — بلا أي حالة محلية. */
    fun toDataJson(): JSONObject = JSONObject().apply {
        put("createdAt", createdAt)
        put("seenDate", seenDate)
        put("specialty", specialty)
        put("ward", ward)
        put("chiefComplaint", chiefComplaint)
        put("keyFindings", keyFindings)
        put("investigations", investigations)
        put("diagnosis", diagnosis)
        put("management", management)
        put("lessons", lessons)
        put("tags", JSONArray(tags))
    }

    /** التمثيل المحلي الكامل داخل entries_json — يشمل حالتي المزامنة المحليتين. */
    fun toLocalJson(): JSONObject = toDataJson().apply {
        put("id", id)
        put("updatedAt", updatedAt)
        put("deleted", deleted)
        put("syncedUpdatedAt", syncedUpdatedAt)
    }

    companion object {
        fun fromDataJson(
            id: String,
            data: JSONObject,
            updatedAt: Long,
            deleted: Boolean = false,
            syncedUpdatedAt: Long = 0L
        ): LogbookEntry {
            val tagsArr = data.optJSONArray("tags")
            return LogbookEntry(
                id = id,
                createdAt = data.optLong("createdAt", updatedAt),
                updatedAt = updatedAt,
                seenDate = data.optLong("seenDate", data.optLong("createdAt", updatedAt)),
                specialty = data.optString("specialty", ""),
                ward = data.optString("ward", ""),
                chiefComplaint = data.optString("chiefComplaint", ""),
                keyFindings = data.optString("keyFindings", ""),
                investigations = data.optString("investigations", ""),
                diagnosis = data.optString("diagnosis", ""),
                management = data.optString("management", ""),
                lessons = data.optString("lessons", ""),
                tags = if (tagsArr == null) emptyList() else buildList {
                    for (i in 0 until tagsArr.length()) {
                        val t = tagsArr.optString(i, "").trim()
                        if (t.isNotEmpty()) add(t)
                    }
                },
                deleted = deleted,
                syncedUpdatedAt = syncedUpdatedAt
            )
        }

        fun fromLocalJson(obj: JSONObject): LogbookEntry = fromDataJson(
            id = obj.optString("id", ""),
            data = obj,
            updatedAt = obj.optLong("updatedAt", obj.optLong("createdAt", 0L)),
            deleted = obj.optBoolean("deleted", false),
            syncedUpdatedAt = obj.optLong("syncedUpdatedAt", 0L)
        )
    }
}

/** الاختصاصات الأشيع بالتدريب — شرائح بلمسة واحدة بالمحّرر وقوالب الإضافة السريعة. */
val LOGBOOK_SPECIALTIES: List<String> = listOf(
    "داخلية",
    "جراحة",
    "أطفال",
    "نسائية وتوليد",
    "إسعافية",
    "قلبية",
    "عصبية",
    "عظمية",
    "بولية",
    "هضمية",
    "صدرية",
    "عيون",
    "أذن وحنجرة",
    "جلدية",
    "نفسية",
    "أخرى"
)
