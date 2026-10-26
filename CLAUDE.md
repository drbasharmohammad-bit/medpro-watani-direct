# Med Pro - Watani — instructions for Claude Code

## Project
- Native Kotlin + Jetpack Compose app, package `sy.medpro.watani`,
  Firebase project `national-exam-8ecba`, Google sign-in required.
- Content lives on the content network (`watani-content.pages.dev/v1`),
  never bundled as files in Firestore and never invented in code.

## Build — READ THIS FIRST
- DO NOT run local Gradle builds in this VM. The Gradle daemon starts
  but the Java client never reaches it over loopback (proven with
  isolated loopback tests; Gradle 8.7 and 9.1.0 fail identically).
- The build path is GitHub Actions: `.github/workflows/build-debug.yml`
  (Gradle 9.3.1 downloaded on the runner, debug APK, no secrets).
  Verify a change by pushing and reading the Actions run log, never by
  claiming a local build succeeded.

## Hard rules (Dr. Bashar's standing orders)
- NO invented content: no hard-coded links, numbers, questions, points,
  fake names or statistics in code. An honest "no data / not working"
  state is acceptable; a faked one is not.
- Medical images stay in English, in their exact original places.
  Never generate realistic medical images (CT/MRI/fundus/dermatology).
  Generatable precisely: waves, curves, numbers, text (ECG, spirometry,
  CTG, ABG, labs, calculators).
- Auscultation trainer = verified real recordings only, no synthesised
  substitute. Content is distributed over the content network, offline
  after download — never bundled in the APK.
- RTL Arabic throughout. App identity: maroon #7A1F2B, background
  #FBF3F4, gold #D4AF37. Never change the app icon (maroon square,
  white M, gold +).
- Every feature must be wired from EVERY entry point (home dashboard,
  side drawer, progress screen). A screen that exists but has no
  routing case is a bug, not a feature. Verify wiring with grep from
  each entry point before claiming done.
- No partial delivery claims. A task is done only when: code is in
  place, wiring is verified, the Actions build succeeds, and the APK
  is dex-checked. Report failures immediately and plainly.
- Never print, commit, or paste secrets, tokens, or API keys.

## Work order (current scope)
1. Side-drawer wiring for all 28 items (diagnosed: drawer `when` block
   lacks cases for OSCE stations, ABG lab, ECG trainer, night-shift
   simulator, lab-values lab, expanded clinical calculators,
   virtual patient, sign-out — they fall through to ProfileScreen).
2. Arabic descriptions for the 48 auscultation clips
   (`watani-v3/auscultation-pack/auscultation_ar.json`, already prepared).
3. Anki flashcards: decide + add the doctor-created cards.
4. Library tab (6th bottom tab, Books | Videos, driven by `library.json`).
5. Emergency room: single-player, brand-new scenarios, national-exam
   level, live vitals responding to decisions, offline after download.
6. Cosmetic pass (Thmanyah font, colours, real dark-maroon night mode).
7. Carry over audit/cleanup fixes from the CLEANUP source.
