# 24-hour Claude API run plan — Watani (prepared 26 Oct 2026, before key arrival)

## The window
- Incoming: Claude API key, **10M tokens, 24 hours** (per Dr. Bashar, 26 Oct ~14:48).
- The 24h clock matters more than the token count: if activation starts
  the clock, do NOT activate until the doctor confirms he is ready to
  hand over the key and work starts immediately. Record the exact
  activation/expiry time the moment the key arrives.
- Key handling: secure entry ONLY, never in chat, never in files, never
  in logs. If the seller gave a base URL / activation doc, record the
  endpoint (not the key) alongside this plan.

## Minute-zero sequence (no token waste)
1. Secure entry received -> configure `ANTHROPIC_API_KEY` +
   `ANTHROPIC_BASE_URL` (reseller endpoint, if any) for Claude Code only.
2. Tiny test call first: one small, real task (e.g. print + fix one
   drawer routing case in a scratch check, or a minimal code question
   against the repo) and measure balance before/after. This calibrates
   the reseller's counting rule — earlier reseller formula was
   (input + output x 5) x model ratio, so "10M tokens" may NOT mean
   10M raw tokens. If the test shows the key/endpoint does not work
   with Claude Code, stop immediately and report — do not burn the
   window on retries.
3. Only then start Task 1 below.

## Task order (largest risk first, cheapest wins early)
All tasks run in `~/workspace/watani-direct/` under `CLAUDE.md` rules.
Each task ends with MY verification (grep wiring, dex check, counts)
before the next task starts. No task is called done on Claude's report.

1. **Drawer wiring** (small, unblocks device testing of everything else).
   Acceptance: every one of the 28 drawer items routes to its real
   screen; none falls through to ProfileScreen; verified by grep from
   MainActivity drawer handler + Home/Progress entry points.
2. **Auscultation Arabic** (data task, cheap). Acceptance: 48/48 clip
   IDs match the live manifest exactly; no invented diagnosis for
   murmurs (expert description only); lung clips carry the recorded
   patient diagnosis with the explicit "patient diagnosis, not inferred
   from sound" note.
3. **Anki cards** (decision + data). Acceptance: both card kinds the
   doctor named work in-app (Watani-section cards + smart-review cards),
   save + completion verified in code paths.
4. **Library tab** (medium). Acceptance: 6th bottom tab, Books | Videos,
   driven ONLY by `library.json` on Pages; empty state in Arabic;
   offline-first download/resume/progress/integrity per the reader spec;
   zero hard-coded books/subjects/counts in code.
5. **Emergency room** (largest — reserve the biggest token share).
   Acceptance: single-player, brand-new scenarios (none from our banks,
   none of the 436 repeated), national-exam level, live vitals that
   respond to decisions, playable offline after download.
6. **Cosmetic pass** (Thmanyah font, colours, real dark-maroon night
   mode that actually works, error/empty states in Arabic).
7. **Audit/cleanup carry-over** from CLEANUP source + final full check.

## Token guardrails (estimates, not promises)
- 10M tokens is generous for this scope IF tasks are batched and
  bounded. Indicative split only: test 0.1M, drawer 0.5M, auscultation
  0.3M, Anki 0.5M, library 2.5M, ER 4M, polish + audit 1.5M, buffer 0.6M.
- Stop conditions per task: 3 failed verification rounds -> stop that
  task, report exactly what failed, move the remaining budget to the
  next task. Never let one task eat the window.
- No local Gradle builds during the window (loopback bug). Feedback
  loop is: Claude writes -> push -> Actions build -> read run log ->
  fix. Batch changes before each push; do not push after every edit.
- Delivery rule unchanged: one complete, verified APK only, with a
  device checklist. No partial delivery, even if the window is ending.

## What I need from the doctor when the key arrives
- The key itself (secure entry only).
- The seller's endpoint/base URL if one was given (or "Anthropic
  direct" if it is a direct key).
- Activation time / expiry time, so the 24h plan runs backwards from it.
- Push destination (old private repo vs new private repo) — needed
  before the first Actions build, not before the first coding task.
