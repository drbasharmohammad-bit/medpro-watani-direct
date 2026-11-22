package sy.medpro.watani.integration

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File

/**
 * Layer-3 (integration / end-to-end) test support — the third layer
 * of the AI-code review methodology adopted 22 Nov 2026 (see
 * docs/RELEASE_CHECKLIST.md): static analysis, unit tests, then
 * end-to-end flow tests. This suite drives the REAL composables,
 * the REAL repository and the REAL encrypted stores, with the
 * network-shaped parts replaced by offline doubles:
 *
 *  - Content host: section fixtures seeded into the app's own
 *    sections_cache, in the exact on-disk format a pre-hardening
 *    install would hold (plaintext JSON with an old mtime), so the
 *    production read path — including its legacy migration to
 *    encrypted form — is what parses them. No live fetch happens:
 *    every screen under test is cache-first and the fixtures are
 *    never stale-flagged inside a test.
 *  - Firebase: a dummy FirebaseApp (test project values only), so
 *    FirebaseAuth.getInstance() resolves locally with NO signed-in
 *    user — the signed-out state is itself a production state the
 *    gates must handle. No real student token is ever used.
 *  - Gateway: never contacted. The activation journey is covered
 *    up to the fail-closed boundary the production code guarantees
 *    offline (see ActivationJourneyIntegrationTest).
 */
object IntegrationTestSupport {

    /** Fixture files are stamped with an epoch mtime so the
     * production legacy-plaintext window (SectionCacheCrypto,
     * SEC-S8 bound) accepts them exactly as it accepts a genuine
     * pre-hardening cache file; the first production read migrates
     * each file to encrypted form, and later reads decrypt it. */
    private const val FIXTURE_MTIME_MS = 1_000L

    private val PREFS_TO_CLEAR = listOf(
        "watani_activation",
        "privacy_consent",
        "watani_settings",
        "section_cache_crypto",
        "firstaid_progress",
        "firstaid_srs"
    )

    /** Initialises a dummy Firebase app when the process has none,
     * so FirebaseAuth calls in production code resolve locally.
     * Idempotent; never signs anyone in. */
    fun ensureFirebaseApp(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("integration-test-api-key")
                .setApplicationId("1:123456789012:android:0123456789abcdef01234567")
                .setProjectId("watani-integration-tests")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
    }

    /** Wipes the local state the flows under test read: the
     * activation store, the consent store, the app settings, the
     * section-cache crypto store, and every cached section file.
     * Belt and braces: the in-memory clear is paired with deleting
     * the shared_prefs files themselves, because the production
     * activation store is an EncryptedSharedPreferences whose
     * instance is cached per process — a plain-instance clear was
     * observed in CI not to reset it inside a shared Robolectric
     * sandbox. */
    fun clearLocalState(context: Context) {
        for (name in PREFS_TO_CLEAR) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
        val prefsDir = File(context.dataDir, "shared_prefs")
        prefsDir.listFiles()?.forEach { file ->
            if (PREFS_TO_CLEAR.any { file.name.startsWith(it) }) file.delete()
        }
        File(context.filesDir, "sections_cache").deleteRecursively()
    }

    /** Seeds one content section into the app's own cache, at the
     * exact path the repository computes for [path]. */
    fun seedSection(context: Context, path: String, json: String) {
        val dir = File(context.filesDir, "sections_cache")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, path.replace('/', '_') + ".json")
        file.writeText(json, Charsets.UTF_8)
        file.setLastModified(FIXTURE_MTIME_MS)
    }

    // -----------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------

    const val DAWRA_PATH = "dawrat_sessions/دورة اختبار التكامل.json"
    const val DAWRA_Q1_CORRECT_OPTION = "خيار قلبية صحيح"
    const val DAWRA_Q3_WRONG_OPTION = "خيار هضمية خاطئ"

    /**
     * A four-question full-dawra session file: two cardiology
     * questions then two gastroenterology ones, in the published
     * session-file shape (id / subject / question / options /
     * correct_index). Correct indices: Q1 -> 0, Q2 -> 1, Q3 -> 2,
     * Q4 -> 0.
     */
    fun dawraSessionJson(): String = """
        [
          {"id":"TST-Q1","subject":"القلبية","session":"دورة اختبار التكامل",
           "question":"سؤال قلبية أول لاختبار التكامل",
           "options":["خيار قلبية صحيح","خيار قلبية خاطئ أ","خيار قلبية خاطئ ب","خيار قلبية خاطئ ج"],
           "correct_index":0,
           "explanation":{"why_correct":"لأنه الجواب الصحيح","summary":"خلاصة"}},
          {"id":"TST-Q2","subject":"القلبية","session":"دورة اختبار التكامل",
           "question":"سؤال قلبية ثانٍ لاختبار التكامل",
           "options":["خيار خاطئ","خيار قلبية ثانٍ صحيح","خيار خاطئ آخر","خيار خاطئ أخير"],
           "correct_index":1},
          {"id":"TST-Q3","subject":"الهضمية والكبد","session":"دورة اختبار التكامل",
           "question":"سؤال هضمية أول لاختبار التكامل",
           "options":["خيار هضمية خاطئ","خيار هضمية خاطئ آخر","خيار هضمية صحيح","خيار هضمية خاطئ أخير"],
           "correct_index":2},
          {"id":"TST-Q4","subject":"الهضمية والكبد","session":"دورة اختبار التكامل",
           "question":"سؤال هضمية ثانٍ لاختبار التكامل",
           "options":["خيار هضمية ثانٍ صحيح","خيار خاطئ","خيار خاطئ آخر","خيار خاطئ أخير"],
           "correct_index":0}
        ]
    """.trimIndent()

    const val FA_STEP1_LOCKED_UNIT_TITLE = "وحدة أساسيات 4"
    const val FA_STEP1_FREE_UNIT_TITLE = "وحدة أساسيات 1"
    const val FA_SESSION_TITLE = "جلسة تكامل اختبارية"
    const val ACTIVATION_DIALOG_TITLE = "اشتراك وطني الكامل"

    /** Step 1 index fixture: five path units in one system. The
     * production free tier is the first three, so units 4 and 5 are
     * the locked ones for a non-activated student. */
    fun firstAidIndexStep1Json(): String = """
        {"version":"test","step":1,"count":5,
         "systems":[{"code":"SYS1","sectionAr":"نظام الاختبار","sectionEn":"Test System",
                     "units":["FA1-U1","FA1-U2","FA1-U3","FA1-U4","FA1-U5"]}],
         "units":[
           {"id":"FA1-U1","code":"U1","sectionAr":"نظام الاختبار","titleAr":"وحدة أساسيات 1","order":1,"kind":"unit","quizCount":5,"flashcardCount":2},
           {"id":"FA1-U2","code":"U2","sectionAr":"نظام الاختبار","titleAr":"وحدة أساسيات 2","order":2,"kind":"unit","quizCount":5,"flashcardCount":2},
           {"id":"FA1-U3","code":"U3","sectionAr":"نظام الاختبار","titleAr":"وحدة أساسيات 3","order":3,"kind":"unit","quizCount":5,"flashcardCount":2},
           {"id":"FA1-U4","code":"U4","sectionAr":"نظام الاختبار","titleAr":"وحدة أساسيات 4","order":4,"kind":"unit","quizCount":5,"flashcardCount":2},
           {"id":"FA1-U5","code":"U5","sectionAr":"نظام الاختبار","titleAr":"وحدة أساسيات 5","order":5,"kind":"unit","quizCount":5,"flashcardCount":2}
         ],
         "booklets":[]}
    """.trimIndent()

    /** Step 2 index fixture: four path units plus one integration
     * session (kind = "integration") named in the integration
     * fixture's session list. */
    fun firstAidIndexStep2Json(): String = """
        {"version":"test","step":2,"count":5,
         "systems":[{"code":"SYS2","sectionAr":"نظام الاختبار السريري","sectionEn":"Clinical Test System",
                     "units":["FA2-U1","FA2-U2","FA2-U3","FA2-U4"]}],
         "units":[
           {"id":"FA2-U1","code":"V1","sectionAr":"نظام الاختبار السريري","titleAr":"وحدة سريرية 1","order":1,"kind":"unit","quizCount":5,"flashcardCount":2},
           {"id":"FA2-U2","code":"V2","sectionAr":"نظام الاختبار السريري","titleAr":"وحدة سريرية 2","order":2,"kind":"unit","quizCount":5,"flashcardCount":2},
           {"id":"FA2-U3","code":"V3","sectionAr":"نظام الاختبار السريري","titleAr":"وحدة سريرية 3","order":3,"kind":"unit","quizCount":5,"flashcardCount":2},
           {"id":"FA2-U4","code":"V4","sectionAr":"نظام الاختبار السريري","titleAr":"وحدة سريرية 4","order":4,"kind":"unit","quizCount":5,"flashcardCount":2},
           {"id":"FA2-INT1","code":"INT1","sectionAr":"نظام الاختبار السريري","titleAr":"جلسة تكامل اختبارية","order":5,"kind":"integration","quizCount":5,"flashcardCount":2}
         ],
         "booklets":[]}
    """.trimIndent()

    /** Integration-layer fixture: no topic links, one Step 2
     * integration session (FA2-INT1). */
    fun firstAidIntegrationJson(): String = """
        {"version":"test","count":0,"links":[],
         "integrationSessions":{"step1":[],"step2":["FA2-INT1"]}}
    """.trimIndent()

    /** Printed-banks fixture: one (empty) printed test; the entry
     * card renders from the envelope alone. */
    fun firstAidBanksJson(): String = """
        {"version":"test","count":3,"keyed":3,"unkeyed":0,
         "tests":[{"test":"اختبار مطبوع 1","sourcePages":"1-3","questions":[]}]}
    """.trimIndent()

    /** Minimal unit package for the free Step 1 unit FA1-U1. */
    fun firstAidUnitPackageJson(): String = """
        {"unit":{"id":"FA1-U1","titleAr":"وحدة أساسيات 1","sectionAr":"نظام الاختبار"},
         "quiz":[],"flashcards":[],"glossary":[]}
    """.trimIndent()

    /** Seeds the complete First Aid fixture set the section screen
     * loads at entry: both step indexes, the integration layer, the
     * printed banks, and the free unit's package. */
    fun seedFirstAidFixtures(context: Context) {
        seedSection(context, "firstaid/index_step1.json", firstAidIndexStep1Json())
        seedSection(context, "firstaid/index_step2.json", firstAidIndexStep2Json())
        seedSection(context, "firstaid/integration.json", firstAidIntegrationJson())
        seedSection(context, "firstaid/banks_step2.json", firstAidBanksJson())
        seedSection(context, "firstaid/units1/FA1-U1.json", firstAidUnitPackageJson())
    }
}
