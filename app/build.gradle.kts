import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  // Phase-2 hardening (Nov 2026): Crashlytics symbol/mapping upload
  // wiring for release builds (firebase-crashlytics via the BoM).
  alias(libs.plugins.crashlytics)
  // Full-audit phase 0 (21 Nov 2026): detekt static analysis gate
  // (baseline-frozen legacy findings; NEW findings fail CI) and
  // JaCoCo unit-test coverage measurement (baseline announced, no
  // imposed target in the first round — audit program §0).
  alias(libs.plugins.detekt)
  id("jacoco")
}

android {
  namespace = "sy.medpro.watani"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "sy.medpro.watani"
    minSdk = 24
    targetSdk = 36
    // 1.2.19 (Nov 2026): everything in 1.2.18 (Android 7 crash fix via
    // core library desugaring + firebase-ai removal) PLUS the in-app
    // Privacy Policy / Terms / Medical Disclaimer screen (approved
    // POLICY_TEXT.md bundled as an asset; consent links under the
    // sign-in button open it at their section; permanent drawer item).
    // Ascending versionCode so it installs as an update over 1.2.22.
    // 1.2.23 (Nov 2026): real phone notifications (explicit permission,
    // daily study reminder + mistakes-due alert, boot-resilient),
    // home-screen widget (exam countdown + daily goal + focus minutes),
    // smart daily goal card, and the upgraded immersive focus timer
    // (modes, subject tagging, light stats).
    // 1.2.24 (Nov 2026): widget family redesign (doctor's ruling after
    // testing 1.2.23 on his phone) — three sizes (countdown-only /
    // day / large resizable), live per-second countdown via a
    // self-ticking Chronometer, and student-set daily goals only:
    // his own question target plus free-text day goals edited in the
    // «أهدافي اليوم» home card and mirrored to the widgets instantly;
    // the auto goal survives only as an optional dialog suggestion.
    // 1.2.25 (Nov 2026): widget v3 built exactly on the doctor's
    // approved visual mockups — Syrian-landmark photo backgrounds
    // (30 landmarks, deterministic daily rotation or the student's
    // fixed pick, blended under the approved burgundy gradient),
    // unit-labeled chronometer (ساعة/دقيقة/ثانية), genuinely
    // different content per size, a rich in-app widget-settings
    // screen, and quick-action buttons that each open their OWN
    // intended screen in the app (focus / mistakes bank / questions
    // tab / library) via per-button routed PendingIntents.
    // 1.2.26 (Nov 2026): «دورة كاملة» — a third path at the top of
    // بنك الوطني (beside the per-subject paths, whose own
    // «حسب المواضيع | حسب السنوات» toggle is untouched): one named
    // dawra assembled at runtime from every downloaded subject
    // shard, in the official 27-subject order with in-session
    // subject separators, explicit coverage (missing subjects are
    // listed with the same download action, never hidden), and the
    // sitting runs on the existing quiz engine — same card,
    // explanation/الزبدة, mistakes bank and vault recording — with
    // an end card showing the total plus the per-subject split.
    // 1.2.30 (Nov 2026): the five lock-screen features the doctor
    // approved from the prepared mocks — (1) a permanent silent
    // countdown card (exam countdown + daily-goal progress) on the
    // lock screen, (2) the question of the day as a morning
    // lock-screen notification whose tap opens the daily-challenge
    // screen (wired into navigation at last), (3) a live focus
    // session bar (foreground service + countdown chronometer +
    // pause/finish actions) so a pomodoro survives app closure,
    // (4) the mistakes-due alert on its own independent time with
    // a «ابدأ المراجعة» action that opens the review session
    // directly, and (5) a strong full-screen study alarm page over
    // the lock screen with start/snooze, falling back to the plain
    // reminder when the full-screen permission is absent. All five
    // are opt-in and OFF by default, managed from the existing
    // «الإشعارات والتذكير» screen; sign-in/consent (1.2.29) and
    // every other existing behavior are untouched.
    versionCode = 74320
    versionName = "1.2.46"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Stable signing key (30 Oct 2026): CI restores ONE keystore from the
  // WATANI_* GitHub secrets and signs every build with it, so all builds
  // share one signature — updates install over the old version and the
  // Firebase SHA-1 never changes again. Without the env vars (local
  // builds) it falls back to the classic debug credentials. The old
  // per-run fresh keystore is gone: it made every build a "different
  // app" (fresh install + new fingerprint every time).
  signingConfigs {
    // Release uses the SAME restored keystore as debug (one stable
    // signature for every build, so the Firebase SHA-1 never changes and
    // updates install over the old version). The old my-upload-key.jks /
    // "upload" alias never existed on CI — a release built against it
    // would fail, or worse, sign with a different key.
    create("release") {
      storeFile = file("${rootDir}/debug.keystore")
      storeType = System.getenv("WATANI_KEYSTORE_TYPE") ?: "JKS"
      storePassword = System.getenv("WATANI_KEYSTORE_PASSWORD") ?: "android"
      keyAlias = System.getenv("WATANI_KEY_ALIAS") ?: "watani"
      keyPassword = System.getenv("WATANI_KEY_PASSWORD") ?: "android"
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storeType = System.getenv("WATANI_KEYSTORE_TYPE") ?: "JKS"
      storePassword = System.getenv("WATANI_KEYSTORE_PASSWORD") ?: "android"
      keyAlias = System.getenv("WATANI_KEY_ALIAS") ?: "androiddebugkey"
      keyPassword = System.getenv("WATANI_KEY_PASSWORD") ?: "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // Phase-2 hardening (Nov 2026): R8 enabled with the keep-rule
      // set in proguard-rules.pro, which keeps every reflection-bound
      // surface this app actually uses (all Moshi DTO packages,
      // Retrofit interface, Firebase/GMS, Kotlin metadata). The rules
      // were prepared in Nov 2026 for exactly this switch; see the
      // header of proguard-rules.pro for the retained surfaces and
      // the phase-2 report for the verification status.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  lint {
    // 1.2.18 (Nov 2026): the 48 NewApi java.time-on-minSdk-24 errors
    // are FIXED AT THE ROOT by core library desugaring (below), so
    // their entries were removed from lint-baseline.xml — lint now
    // verifies the desugared java.time paths for real. The baseline
    // freezes only the known UnsafeOptInUsageError detector blind
    // spots around media3 opt-in markers (7 entries, all in
    // VideoPlayerScreen.kt — the @OptIn annotation itself, the
    // same-module DataSource Factory calls, and since 1.2.31 the
    // 5-retry load-error policy, whose usages ARE covered by the
    // composable's @OptIn and compile cleanly) plus 1 WrongConstant.
    // Any NEW lint error fails the CI lint step; warnings never
    // fail it.
    baseline = file("lint-baseline.xml")
  }
  compileOptions {
    // Core library desugaring (1.2.18): java.time & friends are used
    // on core paths (FocusTimerEngine, OfflineStudyVault,
    // WorkerActivationApi, home dashboard...) while minSdk is 24 —
    // without desugaring those classes do not exist on Android 7
    // (API 24/25) and the app dies with NoClassDefFoundError.
    // Desugaring rewrites them to the bundled j$.* backport, so
    // API 24/25 run the same code as newer devices. minSdk stays 24.
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.converter.moshi)
  // Core library desugaring runtime (1.2.18) — pairs with
  // isCoreLibraryDesugaringEnabled above; ships the j$.* backport of
  // java.time etc. inside the APK for API 24/25 devices.
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  // firebase-ai was removed in 1.2.18: the in-app assistant is frozen
  // and the SDK had zero call sites in the source (verified by search)
  // — it only added APK weight and surface.
  implementation(libs.firebase.appcheck.recaptcha)
  // App Check debug provider is a DEBUG-only aid: it must never ship
  // in a release build (unified fixes package, Nov 2026).
  debugImplementation(libs.firebase.appcheck.debug)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.messaging)
  // Phase-2 hardening (Nov 2026): Crashlytics (version via the BoM)
  // and EncryptedSharedPreferences for the activation token store.
  implementation(libs.firebase.crashlytics)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.exoplayer.hls)
  // PDF text layer only (build 1.2.37): word boxes, search and the
  // book outline for the internal reader. Page RENDERING stays on
  // android.graphics.pdf.PdfRenderer — PDFBox is never asked to draw.
  implementation(libs.pdfbox.android)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

// ---------------------------------------------------------------------------
// Full-audit phase 0 (21 Nov 2026): detekt gate + JaCoCo measurement.
//
// detekt runs with the default rule set on top of the built-in config
// (buildUponDefaultConfig). Every finding that exists today is frozen
// in config/detekt/baseline.xml (generated once by the
// detekt-baseline workflow); any NEW finding fails the CI detekt step,
// mirroring the lint-baseline pattern this project already uses.
// ---------------------------------------------------------------------------
detekt {
  buildUponDefaultConfig = true
  parallel = true
  baseline = file("$rootDir/config/detekt/baseline.xml")
  source.setFrom("src/main/java", "src/test/java")
  reports {
    html.required.set(true)
    xml.required.set(true)
    txt.required.set(true)
  }
}

// JaCoCo coverage over the CI unit-test run. Measurement only in this
// round: the baseline percentage is announced in the audit execution
// log; no coverage target is imposed until the audit's phase 2.
tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn(tasks.named("testDebugUnitTest"))
  reports {
    xml.required.set(true)
    html.required.set(true)
  }
  // AGP 9's built-in Kotlin moves class output between releases, so
  // instead of guessing directories: every .class under the build
  // dir (minus generated/resource classes) and every .exec the test
  // run produced. Two earlier exact-path wirings produced empty
  // reports (CI runs 44900023891 / 44901131830 — audit phase 0); the
  // doFirst diagnostic prints what was actually found.
  classDirectories.setFrom(
    fileTree(layout.buildDirectory.get().asFile) {
      include("**/*.class")
      exclude(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*_Factory.*", "**/*_MembersInjector.*", "**/Dagger*.*",
        "**/res/**", "**/tmp/**/mockable*"
      )
    }
  )
  sourceDirectories.setFrom(files("src/main/java"))
  executionData.setFrom(
    fileTree(layout.buildDirectory.get().asFile) { include("**/*.exec") }
  )
  doFirst {
    println("JaCoCo audit diagnostic: exec files = ${executionData.files}")
    println("JaCoCo audit diagnostic: class files found = ${classDirectories.files.size}")
  }
}
