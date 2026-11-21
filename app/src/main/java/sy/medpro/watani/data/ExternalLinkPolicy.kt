package sy.medpro.watani.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Allowlist for links the app hands to the system (ACTION_VIEW) —
 * audit fix, 21 Nov 2026 (SEC-P2).
 *
 * The contact links come from the gateway config and the Telegram
 * link target comes from a gateway response; both used to be opened
 * with NO scheme/host validation, so a poisoned value could push
 * any scheme (`intent://`, `market://`, another app's deep link)
 * out of a trusted app. Every legitimate value today is a Telegram
 * link (the support bot and the channels are all t.me), so the
 * policy is: https only, no userinfo, host exactly t.me or
 * telegram.me. Widening this list is a deliberate one-line change
 * in a future build — never a config value.
 */
object ExternalLinkPolicy {

    private val ALLOWED_HOSTS = setOf("t.me", "telegram.me")

    /** Pure (unit-testable): may this URL be opened externally? */
    fun isAllowed(url: String?): Boolean {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return false
        val parsed = u.toHttpUrlOrNull() ?: return false
        if (parsed.scheme != "https") return false
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return false
        return parsed.host in ALLOWED_HOSTS
    }
}
