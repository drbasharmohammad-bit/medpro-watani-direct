# Project-specific ProGuard/R8 rules.
#
# R8 groundwork (prepared Nov 2026, NOT enabled — build.gradle.kts keeps
# isMinifyEnabled=false for the unified fixes build).
#
# R8 was evaluated for this build and DEFERRED under the strict gate:
# enabling it requires (a) green CI, (b) dex proof that every DTO class
# and field survives with its real name, and (c) runtime confidence —
# and this project parses JSON through BOTH Moshi codegen adapters and
# the reflective KotlinJsonAdapterFactory (WataniRepository,
# WorkerActivationApi, gateway sync), which is exactly the surface R8
# breaks silently. With no device/emulator available to prove runtime
# behaviour, the safe call is to ship without R8 and enable it later
# with these rules already in place.
#
# Keep rules to activate together with isMinifyEnabled=true:
# -keep class sy.medpro.watani.data.** { *; }
# -keep class sy.medpro.watani.ui.anki.** { *; }
# -keep class sy.medpro.watani.ui.explanation.** { *; }
# -keep class com.google.firebase.** { *; }
# -keep class com.google.android.gms.** { *; }
# -keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
# -keep class com.squareup.moshi.** { *; }
