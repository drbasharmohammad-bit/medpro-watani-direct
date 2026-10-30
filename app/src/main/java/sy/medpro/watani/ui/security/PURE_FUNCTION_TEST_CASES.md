# Pure-function test cases — Team 3 device-protection (UNRUN on a JVM)

Status: NOT RUN against the Kotlin code. No kotlinc/java exists in this
environment (`which kotlinc java javac` → all absent, no /usr/lib/jvm),
and local Gradle builds are impossible here; CI builds only on push to
main, which this branch must not do. These vectors are therefore
recorded as DATA for the first build-capable run (JVM unit test or CI),
not as passing tests. A Python mirror of the decision RULES (not the
Kotlin code) was executed during development to check that the vectors
below are self-consistent (expected reason sets match the rules); that
mirror run is not a substitute for running the Kotlin functions.

How to run for real (follow-up): add a JVM unit test that imports
EmulatorCheck / RootCheck / TamperCheck / DebuggerCheck /
RestrictedContentGate / RestrictedFileStore and asserts each row below.

## EmulatorCheck.isEmulator / emulatorReasons

| # | props (lowercased by the function) | expected |
|---|-------------------------------------|----------|
| E1 | real Pixel: fingerprint=google/raven/raven:13/..., model=Pixel 7, manufacturer=Google, brand=google, device=raven, product=raven, hardware=raven | false, reasons=[] |
| E2 | AVD: fingerprint=generic/sdk_gphone64_arm64/..., model=sdk_gphone64_arm64, brand=generic, device=generic, product=sdk_gphone64_arm64, hardware=ranchu, qemu=1 | true, reasons contain fingerprint, model, brand, device, product, hardware, qemu |
| E3 | Genymotion: manufacturer=Genymotion, model=Custom Phone | true, reasons contain manufacturer |
| E4 | empty map {} | false, reasons=[] (missing props must not false-positive) |

## RootCheck.isRooted / rootReasons

| # | existingPaths | buildTags | expected |
|---|---------------|-----------|----------|
| R1 | {} | release-keys | false |
| R2 | {/system/xbin/su} | release-keys | true, reasons=[root_path:/system/xbin/su] |
| R3 | {/data/adb/modules} | release-keys | true (Magisk modules dir) |
| R4 | {} | test-keys | true, reasons=[test_keys] |

## TamperCheck.evaluate (PINNED_SIGNATURE_SHA256 is empty today)

| # | inputs | expected |
|---|--------|----------|
| T1 | expected=sy.medpro.watani, actual=sy.medpro.watani, installer=com.android.vending, debuggable=false, sig=aa.. (64 hex) | blocked=false, warnings=[signature_pin_unconfigured] |
| T2 | actual=com.evil.repack, rest as T1 | blocked=true, reason=package_mismatch |
| T3 | sig=null, rest as T1 | blocked=true, reason=signature_absent |
| T4 | installer=null, rest as T1 | blocked=false, warnings contain installer_unknown_sideload |
| T5 | debuggable=true, treatDebuggableAsBlock=false | blocked=false, warnings contain debuggable_build |
| T6 | debuggable=true, treatDebuggableAsBlock=true | blocked=true, reason=debuggable_build |
| T7 | pinStatusFor(anything) while PIN is empty | UNCONFIGURED (pin can never mismatch today — the honest limit) |

## DebuggerCheck.isDebuggerAttached / parseTracerPid

| # | inputs | expected |
|---|--------|----------|
| D1 | (false, 0) | false |
| D2 | (true, 0) | true |
| D3 | (false, 1234) | true |
| D4 | (false, null) | false |
| D5 | parse "TracerPid:\t0\n" | 0 |
| D6 | parse "TracerPid:\t5678\n" | 5678 |
| D7 | parse text without a TracerPid line | null |

## RestrictedContentGate.evaluate (priority: emulator > rooted > debugger > tamper)

| # | emulator, rooted, debugger, tamper | expected |
|---|-------------------------------------|----------|
| G1 | false, false, false, false | allowed=true |
| G2 | true, false, false, false | EMULATOR |
| G3 | true, true, true, true | EMULATOR (first hit wins) |
| G4 | false, true, false, false | ROOTED |
| G5 | false, false, true, false | DEBUGGER |
| G6 | false, false, false, true | TAMPERED |

## RestrictedFileStore.isRestrictedPath

true: questions/x.json, dawrat/x.json, dawrat_jami3at/damascus/x.json,
uworld/x.json, explanations/shard.json, library.json.
false: cases.json, guide.json, osce.json, auscultation.json,
auscultation_ar.json (free sections stay plaintext by design).
