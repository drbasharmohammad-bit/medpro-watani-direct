package sy.medpro.watani.data

import org.json.JSONObject

/**
 * Build 1.2.53 — section switches (the doctor's «مرونة» order,
 * 27 Nov 2026): every major section carries a remote switch the
 * doctor flips from the admin panel, effective at the student's
 * next app open with a connection — no app update. Three states:
 *
 *  - [SwitchState.OPEN]      — open to every signed-in student
 *    (the section's own internal gates, if any, still apply).
 *  - [SwitchState.ACTIVATED] — entry requires a live activation.
 *  - [SwitchState.LOCKED]    — closed for everyone, activated or
 *    not (already-downloaded content on the device is NEVER
 *    confiscated: the lock gates section ENTRY and new downloads,
 *    exactly like the standing activation rules).
 *
 * The gateway serves the map as `sectionSwitches` inside
 * GET /v1/config (app_config key `section_switches` on the
 * Worker). [DEFAULTS] reproduce the behaviour of build 1.2.52
 * EXACTLY, key by key, so publishing the field — and shipping
 * this reader — changes nothing until the doctor flips a switch
 * himself:
 *
 *  - BANKS open: since 1.2.47 every signed-in student enters the
 *    banks; the three free subjects are open inside and every
 *    other subject gates itself at the quiz/download layers.
 *  - CASES open: entry is open; the section's own gate (first
 *    cases free, the rest activated) lives inside it.
 *  - FIRSTAID open: entry is open; first units free, the rest
 *    activated — the section's own gate.
 *  - VIDEOS activated: the videos live inside the library, which
 *    has required activation at the tab level all along.
 *  - UWORLD activated: the tab has required activation all along
 *    (locked in full for the non-activated).
 *  - LIBRARY activated: the tab has required activation all along.
 *  - TIPS open: «زوادة الطالب» is open to every signed-in student
 *    (writing is governed separately by `tipsWriting`).
 *
 * «دورة كاملة» (dawrat_sessions) is deliberately NOT a switch:
 * its hard lock is code (FullDawraLock, the doctor's 1.2.49
 * order) and stays exactly as it is.
 */
enum class SectionKey(val configName: String, val title: String) {
    BANKS("banks", "بنوك الأسئلة"),
    CASES("cases", "الحالات"),
    FIRSTAID("firstaid", "First Aid"),
    VIDEOS("videos", "الفيديوهات"),
    UWORLD("uworld", "UWorld"),
    LIBRARY("library", "المكتبة"),
    TIPS("tips", "زوادة الطالب");

    companion object {
        fun fromConfigName(name: String): SectionKey? =
            values().firstOrNull { it.configName == name.trim().lowercase() }
    }
}

enum class SwitchState(val configValue: String) {
    OPEN("open"),
    ACTIVATED("activated"),
    LOCKED("locked");

    companion object {
        fun fromConfigValue(value: String?): SwitchState? =
            values().firstOrNull { it.configValue == value?.trim()?.lowercase() }
    }
}

object SectionSwitches {

    /** The 1.2.52 behaviour, key by key (see the file header). */
    val DEFAULTS: Map<SectionKey, SwitchState> = mapOf(
        SectionKey.BANKS to SwitchState.OPEN,
        SectionKey.CASES to SwitchState.OPEN,
        SectionKey.FIRSTAID to SwitchState.OPEN,
        SectionKey.VIDEOS to SwitchState.ACTIVATED,
        SectionKey.UWORLD to SwitchState.ACTIVATED,
        SectionKey.LIBRARY to SwitchState.ACTIVATED,
        SectionKey.TIPS to SwitchState.OPEN
    )

    /**
     * Merges a gateway `sectionSwitches` object over [DEFAULTS]:
     * a known key with a valid value wins; anything else (unknown
     * key, invalid value, non-string) leaves the default in place.
     * Always returns a complete map.
     */
    fun parse(config: JSONObject?): Map<SectionKey, SwitchState> {
        if (config == null) return DEFAULTS
        val merged = DEFAULTS.toMutableMap()
        val names = config.keys()
        while (names.hasNext()) {
            val name = names.next()
            val key = SectionKey.fromConfigName(name) ?: continue
            val state = SwitchState.fromConfigValue(config.optString(name, "")) ?: continue
            merged[key] = state
        }
        return merged
    }

    fun stateOf(map: Map<SectionKey, SwitchState>, key: SectionKey): SwitchState =
        map[key] ?: DEFAULTS.getValue(key)

    enum class EntryDecision { ALLOW, NEEDS_ACTIVATION, LOCKED }

    /** Pure entry decision for one section (unit-tested matrix). */
    fun entryDecision(state: SwitchState, isActivated: Boolean): EntryDecision = when (state) {
        SwitchState.OPEN -> EntryDecision.ALLOW
        SwitchState.ACTIVATED -> if (isActivated) EntryDecision.ALLOW else EntryDecision.NEEDS_ACTIVATION
        SwitchState.LOCKED -> EntryDecision.LOCKED
    }

    /** The message a locked section shows instead of opening. */
    const val LOCKED_MESSAGE = "هذا القسم مقفل حاليًا"
}
