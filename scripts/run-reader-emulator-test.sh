#!/bin/sh
# Reader device gate runner (build 1.2.54): invoked as ONE line
# from build-final.yml's emulator job because the emulator
# runner mangles multi-line shell loops embedded in its
# `script:` input (run 45995690147 died with a sh syntax error
# before Gradle even started). Retry discipline mirrors the
# build job: transient plugin/dependency resolution flakes
# (run 45994691391, foojay-resolver) must not masquerade as a
# reader verdict. Logcat + tombstones are captured after the
# final attempt either way — on a native crash they ARE the
# diagnosis.
set +e
code=1
attempt=1
while [ "$attempt" -le 3 ]; do
  gradle :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=sy.medpro.watani.reader.PdfiumReaderOpenTest \
    --console=plain --max-workers=2
  code=$?
  if [ "$code" = "0" ]; then break; fi
  echo "connectedDebugAndroidTest attempt $attempt failed (exit $code); retrying in 20s..."
  sleep 20
  attempt=$((attempt + 1))
done
adb logcat -d -v threadtime > logcat-full.txt 2>/dev/null || true
adb shell "ls /data/tombstones 2>/dev/null" || true
mkdir -p tombstones
adb pull /data/tombstones tombstones/ 2>/dev/null || true
exit $code
