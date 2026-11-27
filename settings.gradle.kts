pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    // Google-hosted Maven Central mirror FIRST: it serves artifacts
    // directly, while repo.maven.apache.org intermittently redirects
    // some jars (e.g. kotlin-compiler-embeddable) to GitHub Releases,
    // whose downloads have been returning 504 (Nov 2026 CI outages).
    maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
    mavenCentral()
    gradlePluginPortal()
  }
}

// Build 1.2.54: the foojay-resolver-convention settings plugin
// was REMOVED. Nothing in this build requests a Java toolchain
// (no languageVersion/toolchain spec in any build script — the
// JDK is the one setup-java installs and Gradle itself runs on),
// so the plugin never provisioned anything; but resolving it from
// the Gradle Plugin Portal flaked repeatedly on 2026-11-27 (runs
// 45994691391 / 45996839756 / 45998922484 died in settings
// evaluation before any compilation), masquerading as build
// failures. Removing it deletes that failure class entirely.

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
    mavenCentral()
  }
}

rootProject.name = "Med Pro - Watani"

include(":app")

