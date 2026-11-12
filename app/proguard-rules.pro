# Project-specific ProGuard/R8 rules.
#
# Phase-2 hardening (Nov 2026): R8 is now ENABLED for release
# (isMinifyEnabled + isShrinkResources in build.gradle.kts). The keep
# set below covers every reflection-bound surface this app actually
# uses — verified by grep over the source tree, not by habit:
#
#  - Moshi runs through BOTH codegen adapters and the reflective
#    KotlinJsonAdapterFactory (WataniRepository, WorkerActivationApi
#    neighbours, ExplanationRepository, AnkiRepository): every DTO
#    package is kept whole, with names, so reflective adapters resolve
#    exactly as in debug builds.
#  - Retrofit's WataniApi interface lives in the data package (kept).
#  - Room is NOT a dependency of this app (checked: no room runtime in
#    build.gradle.kts / libs.versions.toml) — no Room rules needed.
#  - Firebase / GMS / Credential Manager classes are kept: they are
#    entered via manifests, reflection and Play Services plumbing.
#  - EncryptedSharedPreferences / security-crypto and OkHttp keep
#    their own consumer rules; the dontwarn lines below cover the
#    optional platform classes OkHttp probes for reflectively.

# ── App DTO / model packages (Moshi reflective surface) ──
-keep class sy.medpro.watani.data.** { *; }
-keep class sy.medpro.watani.ui.anki.** { *; }
-keep class sy.medpro.watani.ui.explanation.** { *; }
-keep class sy.medpro.watani.ui.logbook.** { *; }
-keep class sy.medpro.watani.ui.viewmodel.** { *; }

# Entry points referenced from the manifest / framework plumbing.
-keep class sy.medpro.watani.MainActivity { *; }
-keep class sy.medpro.watani.ui.push.** { *; }

# ── Moshi ──
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class kotlin.Metadata { *; }
-dontwarn com.squareup.moshi.**

# ── Retrofit / OkHttp / Okio ──
-keep interface sy.medpro.watani.data.WataniApi { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions, InnerClasses, EnclosingMethod, *Annotation*

# ── Firebase / Google Play Services / Credentials ──
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Media3 (library-shipped rules cover most; keep the app entry) ──
-dontwarn androidx.media3.**

# Gson is pulled transitively by some Google libraries and used
# reflectively there.
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
