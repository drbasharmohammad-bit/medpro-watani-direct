#!/bin/sh
# Bidi proof runner (build 1.2.55): invoked as ONE line from
# build-final.yml's emulator-bidi job (the emulator runner mangles
# multi-line shell loops in its `script:` input). Runs ONLY the
# BidiProofTest instrumentation class, then pulls the proof
# screenshots the test wrote into the app's external files dir.
# Retry discipline mirrors the reader runner: transient Gradle
# resolution flakes must not masquerade as a display verdict.
set +e
code=1
attempt=1
while [ "$attempt" -le 3 ]; do
  gradle :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.additionalTestOutputDir=/storage/emulated/0/Android/data/sy.medpro.watani.test/files \
    -Pandroid.testInstrumentationRunnerArguments.class=sy.medpro.watani.bidi.BidiProofTest \
    --console=plain --max-workers=2
  code=$?
  if [ "$code" = "0" ]; then break; fi
  echo "connectedDebugAndroidTest attempt $attempt failed (exit $code); retrying in 20s..."
  sleep 20
  attempt=$((attempt + 1))
done
mkdir -p bidi_screenshots
adb exec-out run-as sy.medpro.watani cat /storage/emulated/0/Android/data/sy.medpro.watani/files/bidi_1_header.png > bidi_screenshots/bidi_1_header.png 2>/dev/null || true
adb exec-out run-as sy.medpro.watani cat /storage/emulated/0/Android/data/sy.medpro.watani/files/bidi_2_quiz.png > bidi_screenshots/bidi_2_quiz.png 2>/dev/null || true
adb exec-out run-as sy.medpro.watani cat /storage/emulated/0/Android/data/sy.medpro.watani/files/bidi_3_watani.png > bidi_screenshots/bidi_3_watani.png 2>/dev/null || true
{
      echo "== ls Android/data =="
      adb shell ls /sdcard/Android/data/ 2>&1
      echo "== ls test files dir =="
      adb shell ls -la /sdcard/Android/data/sy.medpro.watani.test/files/ 2>&1
      echo "== ls target files dir =="
      adb shell ls -la /sdcard/Android/data/sy.medpro.watani/files/ 2>&1
      echo "== ProofShoot logcat lines =="
      adb logcat -d -s ProofShoot 2>&1
    } > discovery-bidi.txt 2>&1 || true
adb logcat -d -v threadtime > logcat-bidi.txt 2>/dev/null || true
exit $code
