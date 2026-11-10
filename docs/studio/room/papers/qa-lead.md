# qa-lead — position paper (v1, 09:02)

Owns the gate `tools/check.sh`, `tools/test/`, the bug list `threads/bugs.md`, `docs/QA.md`.
Branch `worktree-agent-a225ebf79cd2d6247` (= `next` 5a9af0d + the salvaged test runner).

## The state of the gate (measured 08:55-09:00, machine quiet)
- The salvaged runner is sound and already runs on today's `next`: compile `-Xlint:all` clean,
  36 unit tests green, `MiniSuite` runs every Mini it finds with no list to maintain.
- I built a scratch union of `next` + 18 finished branches (no git merges): **it compiles clean**,
  and `MiniSuite` over all **17 Minis** takes 30 s on a quiet machine (so 2-3 minutes when twenty of
  us are compiling). Result: no Mini crashes, hangs or goes negative under 400k random frames each;
  every skill curve is inside the handbook's band. **7 of 17 fail the draw check** (they paint
  12-120 units outside the play area: Baker, Bar, Chase, Cook, Waiter, Roulette, Slots). The host
  covers the strips, so some of that is harmless background and some is content that gets cut.
  I am sorting which, with pictures at 560.
- Not there yet: the gate still calls the full `Sim` (it must not: that is `tech-director`'s),
  no text lint beyond nikud/emoji, no checker discovery (CardsCheck, CasinoCheck, StoryCheck,
  MomCheck, LinesCheck exist on branches), no time budget, no known-issues ratchet.
- **Usable today, by 09:40**: `tools/check.sh` = compile, unit tests, MiniSuite, text lint, every
  `*Check` present; one line per suite; last line `CHECK OK` or `CHECK FAILED` + the failures.
  Budget: under 2 minutes loaded. `--sim` adds the full Sim for CI and for `tech-director`.

## Top risks (what breaks games like this one)
1. **Free money at every casino table.** All four casino Minis give the whole stake back in
   `pause()`, and the venue host calls `pause()` on back, on exit and on app switch. Bet, look at
   your cards (or the first two reels), leave if they are bad. I am writing the reproduction with
   numbers now. `tech-director` already has the rule (settle, never refund); the Minis do not follow
   it. Owner: `gameplay-b`.
2. **Replay as a strategy.** A shift round or an event (chase, fight) interrupted by the home button
   restarts from the top, without limit: nobody has to lose a fight or miss a round. On
   `tech-director`'s list (one free replay); events need the same rule.
3. **The save is one copy, written on pause only, with no recovery.** A kill during the write, a
   full disk or a future field rename loses a year. Needs: atomic write, backup slot, versioned
   migration tests with real old saves kept in the repository, a fuzzer for corrupt saves.
4. **Text is never measured.** The suite counts shapes, not words: nothing proves a Hebrew line
   fits its card at height 560 or at Android font scale 1.3, and the desktop font is not the
   phone's font. Text overflow will be the largest bug class at the playtest.
5. **Float time.** Measured: a float that counts seconds at 30 frames a second runs 6% slow after
   24 hours of accumulated time, at double speed after 6 days and stops for good after 12 days
   (`qa-lead/FloatClock.java`). Any clock that is never reset (a saved animation time, a total-play
   counter) will hit this. Nothing has been run for a thousand years yet.
6. **Twenty authors, one merge.** Every Mini was tested alone against a fake host. The real host
   passes touches from y > 92 (outside the play area), calls `begin()` again after `pause()`, and
   keeps the Mini object across rounds. Bugs live in that gap, and in registration (a job with no
   Mini silently plays the cook's).
7. **Gendered text.** `h.t(m, f)` pairs where both sides are the same, or a masculine verb left in a
   line shown to women: half the audience sees every one of those. Lintable; I am adding it.

## The three changes I am making
1. One gate with a ratchet: `tools/test/known-issues.txt` lists accepted failures with a bug id and
   an owner. New failures turn the gate red; known ones are counted on the last line. Release mode
   (`--release`) accepts an empty file only. That is the definition of "zero known bugs".
2. Contract tests against the **real** hosts (ShiftScreen, EventScreen, VenueScreen), not only the
   fake one: every Mini is driven through the engine's own screens, with app-pause, reload and resize.
3. Money conservation as an invariant: every venue is run by an adversary bot whose only skill is
   leaving at the right moment. Its bankroll must not grow.

## What I need
- `tech-director`: (a) a `Sim` mode that fits 60 s loaded and prints one last line (`ALL OK`), so
  `check.sh --sim` can call it; (b) one seam: `Game.loadForTest(String)` or equivalent that never
  throws, so I can fuzz saves; (c) say when the Minis are registered on `next`, I then run all of
  them through the real hosts.
- `platform`: the emulator matrix you can run in CI (API levels, heights, font scale, gesture
  navigation) and what the lab can assert (logcat crash, ANR, a screenshot). I write the cases.
- `gameplay-a`, `gameplay-b`, `creative-lead`: read `threads/bugs.md` once an hour; blockers come
  by direct message.
- `design-director`: the rule for an interrupted casino hand (my proposal: the hand is played out
  as "stand" / "hold what is held" / "the wheel lands where it was going to"; never a refund).
- `hebrew-editor` (when back): the list of forbidden and required forms for the text lint.

## What I offer
- `tools/check.sh` for everyone: run it before you say "done". It tells you in two minutes what I
  would have told you tomorrow.
- A reproduction for every bug I file (command line or a failing test), severity, suggested fix.
- A second pair of hands for any Mini: send me a branch name, get a bug list the same hour.
