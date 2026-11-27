#!/bin/sh
# Clip proof runner (build 1.2.55): invoked as ONE line from
# build-final.yml's emulator-clip job (the emulator runner mangles
# multi-line shell loops in its `script:` input). Runs ONLY the
# ClipProofTest instrumentation class, then pulls the proof
# screenshots the test wrote into the app's external files dir.
set +e
code=1
attempt=1
while [ "$attempt" -le 3 ]; do
  gradle :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.additionalTestOutputDir=/storage/emulated/0/Android/data/sy.medpro.watani.test/files \
    -Pandroid.testInstrumentationRunnerArguments.class=sy.medpro.watani.clip.ClipBeforeReplicaTest,sy.medpro.watani.clip.ClipProofTest \
    --console=plain --max-workers=2
  code=$?
  if [ "$code" = "0" ]; then break; fi
  echo "connectedDebugAndroidTest attempt $attempt failed (exit $code); retrying in 20s..."
  sleep 20
  attempt=$((attempt + 1))
done
mkdir -p clip_screenshots
adb pull /sdcard/Android/data/sy.medpro.watani.test/files/clip_before.png clip_screenshots/ 2>/dev/null || true
adb pull /sdcard/Android/data/sy.medpro.watani.test/files/clip_after.png clip_screenshots/ 2>/dev/null || true
adb pull /sdcard/Android/data/sy.medpro.watani.test/files/header_after.png clip_screenshots/ 2>/dev/null || true
adb pull /sdcard/Android/data/sy.medpro.watani.test/files/widget_after.png clip_screenshots/ 2>/dev/null || true
adb logcat -d -v threadtime > logcat-clip.txt 2>/dev/null || true
exit $code
