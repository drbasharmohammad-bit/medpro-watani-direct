package sy.medpro.watani.data

/**
 * «زوادة اليوم» — the daily tip the glass widget family shows
 * (build 1.2.51, design 6 of the approved v4 family).
 *
 * Selection rule (documented choice): a STABLE DAILY PICK — all
 * tips of all downloaded packs are flattened in a fixed order
 * (the 27 subjects in contract order, the six sections in
 * contract order, pack order inside a section), and the day picks
 * `epochDay mod count`. The whole family therefore shows the same
 * tip all day on every device with the same packs, it changes at
 * the midnight widget refresh, and no network or randomness is
 * involved. "Latest approved" was rejected deliberately: pack
 * files carry no per-tip approval timestamp ordering guarantee
 * across subjects, while the daily pick is deterministic and
 * testable.
 *
 * Pure logic — the widget layer only loads the cached packs.
 */
data class WidgetTip(
    val text: String,
    val author: String?,
    val subject: String,
    val sectionId: String
)

object WidgetTipMath {

    /** All tips of [packs] in the fixed flattening order. Packs
     * are keyed by subject name; subjects outside the contract
     * order are appended by name so nothing is silently dropped. */
    fun flatten(packs: Map<String, TipsPack>): List<WidgetTip> {
        val orderedSubjects = TipsContract.SUBJECTS.filter { packs.containsKey(it) } +
            packs.keys.filter { it !in TipsContract.SUBJECTS }.sorted()
        val out = mutableListOf<WidgetTip>()
        for (subject in orderedSubjects) {
            val pack = packs.getValue(subject)
            for (sectionId in TipsContract.SECTION_IDS) {
                for (tip in pack.tipsFor(sectionId)) {
                    out.add(WidgetTip(tip.text, tip.author, subject, sectionId))
                }
            }
        }
        return out
    }

    /** The day's tip, or null when no pack carries any tip (the
     * widget then renders its elegant empty state). */
    fun pickDaily(packs: Map<String, TipsPack>, epochDay: Long): WidgetTip? {
        val all = flatten(packs)
        if (all.isEmpty()) return null
        return all[Math.floorMod(epochDay, all.size.toLong()).toInt()]
    }

    /** The author line under the widget tip: «من زوادة الطالب —
     * الاسم» when the pack carries a name; null (NO line at all)
     * when author is null — the widget spec's explicit rule. */
    fun authorLine(tip: WidgetTip): String? {
        val name = tip.author?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "من زوادة الطالب — $name"
    }
}
