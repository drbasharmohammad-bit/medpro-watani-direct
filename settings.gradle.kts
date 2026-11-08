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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

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

