package sy.medpro.watani.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** روابط التواصل الرسمية كما ترد من البوابة (روابط كاملة أو فارغة). */
data class ContactLinks(
    val supportBot: String = DEFAULT_SUPPORT_BOT,
    val wataniChannel: String = DEFAULT_WATANI_CHANNEL,
    val medproChannel: String = DEFAULT_MEDPRO_CHANNEL,
    val instagram: String = ""
) {
    companion object {
        const val DEFAULT_SUPPORT_BOT = "https://t.me/medpro_official_bot"
        const val DEFAULT_WATANI_CHANNEL = "https://t.me/medprowatain"
        const val DEFAULT_MEDPRO_CHANNEL = "https://t.me/medpro_sy_channel"
        val DEFAULTS = ContactLinks()
    }
}

/**
 * مخزن روابط التواصل: يبدأ بآخر قيمة جيدة محفوظة محليًا أو بالافتراضيات،
 * و[refresh] يجلب أحدث قيمة من البوابة (GET /v1/config/contact-links)
 * فيحدّث التدفق والكاش عند النجاح فقط — الفشل يُبقي آخر قيمة جيدة.
 */
object ContactLinksStore {
    private const val PREFS_NAME = "watani_contact_links"
    private const val KEY_JSON = "links_json"

    private val _links = MutableStateFlow(ContactLinks.DEFAULTS)
    val links: StateFlow<ContactLinks> = _links.asStateFlow()
    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null)
        if (!raw.isNullOrBlank()) {
            try {
                val json = JSONObject(raw)
                _links.value = ContactLinks(
                    supportBot = json.optString("supportBot", ContactLinks.DEFAULT_SUPPORT_BOT),
                    wataniChannel = json.optString("wataniChannel", ContactLinks.DEFAULT_WATANI_CHANNEL),
                    medproChannel = json.optString("medproChannel", ContactLinks.DEFAULT_MEDPRO_CHANNEL),
                    instagram = json.optString("instagram", "")
                )
            } catch (_: Exception) {
                // كاش تالف: تبقى الافتراضيات.
            }
        }
    }

    fun refresh(context: Context) {
        init(context)
        val ctx = context.applicationContext
        WorkerActivationApi.fetchContactLinksAsync { result ->
            if (result == null) return@fetchContactLinksAsync
            val updated = ContactLinks(
                supportBot = result["supportBot"] ?: ContactLinks.DEFAULT_SUPPORT_BOT,
                wataniChannel = result["wataniChannel"] ?: ContactLinks.DEFAULT_WATANI_CHANNEL,
                medproChannel = result["medproChannel"] ?: ContactLinks.DEFAULT_MEDPRO_CHANNEL,
                instagram = result["instagram"] ?: ""
            )
            _links.value = updated
            try {
                val json = JSONObject()
                    .put("supportBot", updated.supportBot)
                    .put("wataniChannel", updated.wataniChannel)
                    .put("medproChannel", updated.medproChannel)
                    .put("instagram", updated.instagram)
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_JSON, json.toString()).apply()
            } catch (_: Exception) {
            }
        }
    }
}
