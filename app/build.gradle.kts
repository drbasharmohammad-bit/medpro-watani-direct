import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "sy.medpro.watani"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "sy.medpro.watani"
    minSdk = 24
    targetSdk = 36
    // Unified fixes build (Nov 2026): a real, ascending versionCode
    // (74273, the build number after 74272) so this build installs as
    // an update over every previous one, and a meaningful versionName.
    versionCode = 74276
    versionName = "1.2.2"

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
      // No R8 yet: obfuscation + shrinking without a device-proven rule
      // set risks breaking Moshi/Room/Firebase reflection at runtime.
      // The release variant differs from debug in debuggable=false and
      // the file name — that is what makes it "final", not shrinking.
      isMinifyEnabled = false
      isShrinkResources = false
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  lint {
    // Unified fixes package (Nov 2026): the 54 pre-existing lint
    // ERRORS (48 NewApi java.time-on-minSdk-24, 5 media3 UnsafeOptIn,
    // 1 WrongConstant — all in code that predates this build) are
    // frozen in lint-baseline.xml. Any NEW lint error fails the CI
    // lint step; warnings never fail it.
    baseline = file("lint-baseline.xml")
  }
  compileOptions {
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
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  // App Check debug provider is a DEBUG-only aid: it must never ship
  // in a release build (unified fixes package, Nov 2026).
  debugImplementation(libs.firebase.appcheck.debug)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.messaging)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.exoplayer.hls)
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
