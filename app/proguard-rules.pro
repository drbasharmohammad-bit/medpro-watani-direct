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

# ── PDF reader marking DTOs (Crashlytics c3b30976, build 1.2.37) ──
# HighlightStrokeDto / PdfAnnotationDto / TextHighlightDto live in
# ui.library — the one Moshi surface NOT covered by the package keeps
# above — and are (de)serialized reflectively by PdfViewerScreen.
# Under R8 their names were obfuscated (the crash's "List<dx1>") and
# adapter creation threw during composition, killing every book open.
# The DTOs now carry @JsonClass(generateAdapter = true) (which also
# matches the @JsonClass keep rule above); these explicit keeps are
# the second layer so neither the DTOs nor their generated adapters
# can be renamed or stripped.
-keep class sy.medpro.watani.ui.library.HighlightStrokeDto { *; }
-keep class sy.medpro.watani.ui.library.PdfAnnotationDto { *; }
-keep class sy.medpro.watani.ui.library.TextHighlightDto { *; }
-keep class sy.medpro.watani.ui.library.HighlightStrokeDtoJsonAdapter { *; }
-keep class sy.medpro.watani.ui.library.PdfAnnotationDtoJsonAdapter { *; }
-keep class sy.medpro.watani.ui.library.TextHighlightDtoJsonAdapter { *; }

# PDFBox Android (build 1.2.37, text layer only): its JPXFilter
# references the optional Gemalto JPEG2000 decoder, which is NOT
# bundled with pdfbox-android and never ships on Android. R8 treats
# the reference as a missing class and fails minifyReleaseWithR8;
# the reference is dead at runtime (no JPX decoding path is used —
# the reader renders with PdfRenderer and only extracts text), so
# the warning is suppressed exactly as R8's missing_rules suggests.
-dontwarn com.gemalto.jp2.JP2Decoder

# ── Release log stripping (full audit 21 Nov 2026, SEC-S9/SEC-C3) ──
# Debug/verbose android.util.Log calls are removed from release
# builds: activation-flow Log.d lines printed raw server error
# strings and sign-in error details to logcat. Warning/error logs
# are kept (Crashlytics breadcrumbs value, no content in them).
-assumenosideeffects class android.util.Log {
    public static int d(java.lang.String, java.lang.String);
    public static int d(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int v(java.lang.String, java.lang.String);
    public static int v(java.lang.String, java.lang.String, java.lang.Throwable);
}
