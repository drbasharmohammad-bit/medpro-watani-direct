package sy.medpro.watani.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sy.medpro.watani.data.TipsContract
import sy.medpro.watani.data.TipsRepository
import sy.medpro.watani.data.WidgetGlassMath
import sy.medpro.watani.data.WidgetGlassStyle
import sy.medpro.watani.data.WidgetSettingsStore
import sy.medpro.watani.data.WidgetTipMath
import sy.medpro.watani.ui.widget.WidgetDestination
import sy.medpro.watani.ui.widget.WidgetRouter
import java.io.File

/**
 * Layer 3 — glass widget family v4 (build 1.2.51), the parts that
 * cross real storage and routing:
 *  1. A tips pack seeded into the app's REAL tips cache directory
 *     is read back through the production [TipsRepository] and the
 *     daily-pick math yields «زوادة اليوم» from it; with no pack
 *     at all the pick is null (the widget's empty state).
 *  2. The glass settings round-trip through the real store: a
 *     style change persists, and a below-minimum opacity stored
 *     raw comes back raised to the documented 45 floor.
 *  3. Widget routes for the new family resolve: the tip widget
 *     opens «زوادة الطالب» on its subject, the continue widget
 *     resolves to the Continue destination.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1600dp")
class WidgetGlassIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IntegrationTestSupport.ensureFirebaseApp(context)
        IntegrationTestSupport.clearLocalState(context)
        File(context.filesDir, "tips_cache").deleteRecursively()
    }

    @Test
    fun dailyTipComesFromTheRealCachedPack() {
        val subject = "القلبية"
        val dir = File(context.filesDir, "tips_cache").apply { mkdirs() }
        File(dir, "$subject.json").writeText(
            """
            {"subject":"$subject","version":1,"updated_at":"2026-11-24T00:00:00Z",
             "sections":{"most_common":[
               {"id":"t1","text":"الذبحة المستقرة تُعالج أولًا بتعديل نمط الحياة",
                "author":"ليان","created_at":"2026-11-24"},
               {"id":"t2","text":"نصيحة بلا اسم صاحب",
                "author":null,"created_at":"2026-11-24"}
             ]}}
            """.trimIndent(),
            Charsets.UTF_8
        )
        val packs = TipsContract.SUBJECTS.mapNotNull { s ->
            TipsRepository.cachedPack(context, s)?.let { s to it }
        }.toMap()
        assertEquals(1, packs.size)
        val day0 = WidgetTipMath.pickDaily(packs, 0)
        assertEquals("الذبحة المستقرة تُعالج أولًا بتعديل نمط الحياة", day0?.text)
        assertEquals("من زوادة الطالب — ليان", day0?.let { WidgetTipMath.authorLine(it) })
        val day1 = WidgetTipMath.pickDaily(packs, 1)
        assertEquals("نصيحة بلا اسم صاحب", day1?.text)
        assertNull(day1?.let { WidgetTipMath.authorLine(it) })
    }

    @Test
    fun dailyTipIsNullWithNoPacksAtAll() {
        val packs = TipsContract.SUBJECTS.mapNotNull { s ->
            TipsRepository.cachedPack(context, s)?.let { s to it }
        }.toMap()
        assertTrue(packs.isEmpty())
        assertNull(WidgetTipMath.pickDaily(packs, 7))
    }

    @Test
    fun glassSettingsRoundTripAndOpacityFloor() {
        val saved = WidgetSettingsStore.load(context).copy(
            glassStyle = WidgetGlassStyle.LIGHT,
            glassAlphaPercent = 80,
            showTip = false
        )
        WidgetSettingsStore.save(context, saved)
        val loaded = WidgetSettingsStore.load(context)
        assertEquals(WidgetGlassStyle.LIGHT, loaded.glassStyle)
        assertEquals(80, loaded.glassAlphaPercent)
        assertEquals(false, loaded.showTip)

        // A raw below-minimum value in storage (older build, hand
        // edit) is raised to the documented floor on read.
        context.getSharedPreferences("watani_widget_settings_v3", Context.MODE_PRIVATE)
            .edit().putInt("glass_alpha_percent", 10).commit()
        assertEquals(
            WidgetGlassMath.GLASS_ALPHA_MIN,
            WidgetSettingsStore.load(context).glassAlphaPercent
        )
    }

    @Test
    fun newFamilyRoutesResolve() {
        assertEquals(
            WidgetDestination.Tips("القلبية"),
            WidgetRouter.destinationFor(WidgetRouter.tipsRoute("القلبية"))
        )
        assertEquals(
            WidgetDestination.Tips(null),
            WidgetRouter.destinationFor(WidgetRouter.ROUTE_TIPS)
        )
        assertEquals(
            WidgetDestination.Continue,
            WidgetRouter.destinationFor(WidgetRouter.ROUTE_CONTINUE)
        )
    }
}
