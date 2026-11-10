# Director's ledger — "אמא על הקו" (repo /home/claude/ordinary-life, working branch `next`, shipped build on `main` = 0.1.3)

Base commit for all worktrees: 9180dee (kit: Mini, Host, MiniLab, CookMini, docs/TEAM.md).
Worktrees live in /home/claude/ordinary-life/.claude/worktrees/agent-<id> on branch worktree-agent-<id>.
Concurrency cap: 20 agents. Never push from agents; director merges into `next`, then `main` when green (push to main triggers the APK build).

## Wave 1 roster (id → role → status)
- ab27b66e40990aa7b  lead engineer (fable, MAIN TREE on next)      running
- aa1cf229f60ec306a  WaiterMini (sonnet)                           running
- a7de99fddf26bef3c  BarMini (sonnet)                              DONE  commit cabb0af  LAB OK, skill table 0.00/0.78/1.08/1.91/2.00, 25-37 s; screenshot looks good
- a1a600103b9990083  GasMini (sonnet)                              running
- adcd10700234af0b9  CashierMini (sonnet)                          running
- a38a57c3812a253d2  FightMini (sonnet)                            running
- ac58e9661f91f0ab3  AlleyMini + AlleySim (opus)                   running
- a2c3cbb7ae182b160  Cards + BlackjackMini + PokerMini (sonnet)    running
- a537a73ad7709f147  RouletteMini + SlotsMini (sonnet)             running
- ae8b96b0b2d21cbf3  DoctorStudyMini + DoctorMini (sonnet)         running
- a56be74398c639ba6  DevStudyMini + DevMini (sonnet)               running
- a9736deb3158b46b4  environment artist: City/MapScreen.draw/CityArt (opus)   running
- a7014298bd5b646aa  character artist: Look/PersonArt/Ui.person (opus)        running
- a46ae1660320ab02d  illustrator: Icons + TitleScreen art (opus)              running
- aac7ac99b419cea2b  story writer: Story.java + docs/STORY.md (opus)          running
- a2545166539c4427e  comedian: MomLines.java (opus)                           running
- a0c196ba8ef3b9cae  city voices: Lines.java (opus)                           running
- a5b12356aca8d210e  economy designer: docs/DESIGN.md + Tuning.java (opus)    running
- a7ca1d74d9ddc3a45  store designer: docs/STORE.md + Catalog + Billing (opus) running
- ac989af9f31fff74c  UX designer: docs/UX.md (opus)                           running
- a582b794691b65080  sound designer: Synth + SoundPlayer + SynthDump (opus)   running (started when bar finished)

## Queue (launch as slots free, in this order)
1. composer: Music.java + MusicDump (opus)
2. cook rework: CookMini.java (sonnet)
3. baker rhythm: BakerMini.java (sonnet)
4. police chase: ChaseMini.java (sonnet)
5. lawyer: LawStudyMini + LawyerMini (opus)

## Wave 2 (after lead + wave 1): integrators, UX implementation, Hebrew editor, QA x2, balance pass
- Mini ids to register: cook, waiter, baker, barman(BarMini), gas, cashier, chase, fight, alley, blackjack, poker, roulette, slots, doctor(+study), lawyer(+study), dev(+study)
- Requests from agents for new Sfx ids / engine features: (collect here)
  - bar: maybe a dedicated "glug"/"bell" sound; tips via h.pay

## Owner decisions / notes
- Owner wants: full build (not minimal), AAA-practice polish, real-money store that is not pay-to-win, more 3D + life, will move the session to Claude Code (teleport) when everything is done, merged and pushed.
- Real payments need Google Play Billing + owner's developer account: store ships in test mode.

## Events
- CashierMini DONE: agent adcd10700234af0b9, branch worktree-agent-adcd10700234af0b9, commit 7d9c02e. LAB OK; skill 0.03/0.28/1.29/1.97/1.99; 32-62 s. Risk: expert too easy for bots; offer rate high; did not run Sim. Screens in scratchpad/cashier/.
- Composer launched (opus) in the freed slot. Queue now: cook, baker, chase, lawyer.
- Store designer (a7ca1d74d9ddc3a45) waiting on its own background work, no report yet.
- Store designer DONE: agent a7ca1d74d9ddc3a45, branch worktree-agent-a7ca1d74d9ddc3a45, commit 3989d5d. docs/STORE.md, Catalog.java, Billing.java. Currency "נחת" (stored in var gems). Free player earns ~655-995 per run; cosmetics total 1590; conveniences: bag slot 60, mom care package 120 (weekly), taxi card 150 (3 rides/month); packs ₪4.90/12.90/24.90, supporter ₪29.90 (500 + fridge magnet). Vars: gems, own.*, look.*, store.eq.*, store.tok.*, earn.*, store.care_day, store.care_waiting, store.taxi_used. Needs: look/accessory skus matched to character artist's ids; store screen + earning hooks (section 6 of STORE.md).
  Director defaults (told owner): carry נחת + owned items across new games; conveniences as proposed; content in נחת, no pack above ₪24.90.
- Cook rework launched (sonnet). Queue now: baker, chase, lawyer.

## PRODUCTION PIPELINE (owner's instruction, 07:42) — run to completion inside this session, max useful parallelism
A. Wave 1 build finishes (lead + specialists).
B. Integration: integrators wire Minis/art/story/store/sound into the engine; UX implementation; Hebrew editor.
C. Debug + tests #1: write diagnostics and run; write tests and run; all bot tests at maximum simulation speed. Fix.
   -> push a green build to main here so the owner gets an APK early.
D. Playtest #1: build a fast playtest driver (text observation + screenshots, policy-driven autoplay, readable diary),
   then 20 simulated players of different ages/demographics/gaming literacy play the WHOLE game at very high speed and
   each writes an honest review (confusions, boredom, delight, where they would quit, ratings). A research lead synthesises.
   (These are AI personas, not real people: say so to the owner.)
E. Fix wave from the feedback (many agents, by area).
F. Debug + tests #2 (same suites, max speed).
G. Playtest #2: the SAME 20 personas play again and compare before/after.
H. Improve what is still weak; final merge to main, APK, then tell the owner it is the moment to teleport to Claude Code
   (give `claude --teleport session_01VJEuTU28he8mrC7MDAw2y5` and the branch name).
Goal for every phase: the game should feel like a AAA-quality experience (polish, feel, clarity, humour, progression).
- Lines writer DONE: agent a0c196ba8ef3b9cae, branch worktree-agent-a0c196ba8ef3b9cae, commit f343494. Lines.java + LinesCheck (LINES OK). 60 street beats, shiftStart 13/job, shiftEnd 9/job, 27 promotion beats (ids ln.promo.<job>.<rank>, rank 0 = hired welcome), closed 4/building, 33 tips.
  Integration notes: effect "" must parse as no-op; engine must clamp food/water/energy; engine sets seen.<id>; options are gender-neutral; month read as calendar month (1 = January) for seasons; thresholds hungry<35, broke<60, flush>=600; jobs 6-8 = doctor/lawyer/dev.
- Baker launched (sonnet). Queue now: chase, lawyer.
- Note: machine is loaded (20 agents); full Sim takes >4 min under load.
- UX designer DONE: agent ac989af9f31fff74c, branch worktree-agent-ac989af9f31fff74c, commit 4a39d15. docs/UX.md (320 lines: audit, design system, onboarding, 30-item change list 10 P0 / 12 P1 / 8 P2) + UxShots.java harness (prints UX OK + three counts that should reach zero: 7 low-contrast pairs, 19 text styles <12, 18 small/crowded buttons). Output in scratchpad/ux/ (pairs/ is easiest).
  Top findings: "coming soon" buildings look playable and waste hours; tap = instant walk with no info/cancel; no pause/menu on screen; ENERGY TRAP (shift allowed at 55, uses 48, walk home ~12 -> collapse); buttons act on touch-down with no pressed state, dialogs share a button position (double-tap skips), new-game wipe too easy; open/closed only by dimming; small text/targets; colour-only meters/results; duplicated verbs; all messages look the same; no sound yet.
  DIRECTOR'S ANSWERS for the UX implementation wave:
   1. Shift minimum energy = shift cost + walk-home cost + 5 (computed), and the shift/destination card shows predicted energy on arrival home.
   2. Clock frozen until the first building tap on day 1 only.
   3. נחת (gems) shown in store, profile and day summary only, not in the HUD.
   4. Leaving a shift early is allowed at round boundaries from the pause menu with a confirm; paid for finished rounds; still counts as today's shift.
   5. Mom's call may be declined: mom.worry +10, mom.trust -5, she calls again next morning.
   6. Energy meter violet (#B79CFF) approved.
   7. Section 7 is built after the merge by two UX engineers: (a) Btn/Ui/HUD/dialog system; (b) destination sheet, onboarding, pause/settings menu.
- Chase launched (sonnet). Queue now: lawyer.
- GasMini DONE: agent a1a600103b9990083, branch worktree-agent-a1a600103b9990083, commit 1970052. LAB OK; 8 rounds; skill 0.38/0.72/1.28/1.85/1.91; 35-38 s. Hold-to-pump, release on amount (graded in time), turbo toggle, windscreen wipe for tips (uses move()), pays tips/charges via h.pay. Title uses "תדלקן/תדלקנית" (Job table says מתדלק/מתדלקת: unify in Hebrew edit). Screens in scratchpad/gas/.
- Lawyer launched (opus). QUEUE EMPTY. All 26 wave-1 agents launched.

## MILESTONES (owner, 07:53): alpha -> beta -> release candidate, zero known bugs, AAA-level optimization, unlimited agents (cap 20 concurrent)
- ALPHA  = end of phase C: everything integrated and playable end to end, tests green, APK on main.
- BETA   = end of phase F: playtest #1 feedback fixed, debug+tests #2 green, APK.
- RC     = end of phase H: playtest #2 confirms, polish, OPTIMIZATION TRACK done, zero known bugs, APK; then teleport hand-off.
- Optimization track (run in phases C, F, H): frame time and per-frame allocations (no garbage in hot paths), primitives per frame budgets,
  redraw only when needed (battery), startup time, memory, save size, APK size, release build type (R8/minify, proper signing notes for the owner),
  Android lifecycle robustness, audio latency; a perf harness with numbers before/after.
- Owner hopes the art is "not polygons anymore": told him honestly it is illustrated vector art drawn in code (not placeholder boxes, not bitmaps, not real 3D);
  show him the city/characters/icons screenshots as soon as the artists report.
- Casino cards DONE WITH GAPS: agent a2c3cbb7ae182b160, branch worktree-agent-a2c3cbb7ae182b160, commit af6fe44. Cards.java, BlackjackMini, PokerMini, CardsCheck. LAB OK both. RTP: blackjack 98.9% (no split), video poker 99.5% (5 coins). Vars casino.bj.last, casino.pk.coins, casino.pk.val.
  GAPS (assign a finisher in phase C): only ~2 polish rounds; possible payout/floater mismatch in poker (a "+₪100" next to an apparent straight flush) not traced; max-bet royal pays ₪16,000 (cap coin values per economy); no final screenshots saved; 300-high blackjack tray tight; Hebrew not proofread.
- Launched (new roles, fill free slots with real work): test infrastructure engineer (sonnet): tools/test runner, MiniSuite (fast headless gate for all Minis), tools/check.sh, CI step. It merged bar/cashier/gas/cards into its own branch.
- NEXT TO LAUNCH when slots free: (1) performance engineer (perf harness: frame time/allocations/primitives per screen and Mini, budgets), (2) casino cards finisher.
- SETTINGS SCREEN requirements (owner asked 07:56; UX.md already specifies a pause/settings menu with sound, music, new game with confirm, credits). Director adds for the UX implementation wave:
  sound on/off + music on/off (vars opt.sfx / opt.music), haptics on/off (opt.haptic; shell vibrates lightly on results if available),
  reduce motion (opt.motion: no screen shake, calmer particles), larger text (opt.bigtext: +2 on the type scale where layout allows),
  left-handed layout (opt.lefty: mirror the primary action side), game pace (opt.pace: normal / relaxed = clock 25% slower), 
  new game with a proper confirm dialog, restore purchases, privacy policy text, credits, version. All persisted in vars and applied live.
- Stop hook keeps flagging the lead's in-flight edits in the main tree: director snapshots them to `next` (WIP commits) before ending a turn.
- Roulette + Slots DONE: agent a537a73ad7709f147, branch worktree-agent-a537a73ad7709f147, commit 3810b35. RouletteMini, SlotsMini, CasinoCheck (CASINO OK). RTP roulette 97.30% all bets; slots 94.25% exact. Vars casino.roulette.chip, casino.slots.bet. Screens in scratchpad/casino/final/. NOT yet backed up to remote (push as wip/casino-machines).
  It ran `pkill -f MiniLab` once (killed other agents' labs at that moment). Machine load ~30.
- DECISION (pause exploit, all venues): pause() must SETTLE the already-decided outcome (roulette/slots: pay the drawn result; blackjack: auto-stand and resolve; poker: draw with current holds), never refund. Casino finisher applies to all four venues + cards polish + poker floater trace + bet caps.
- DECISION (shift replay exploit): one free replay of an interrupted round per shift; later interruptions count that round as MISS. (Tell lead/integrator.)
- Backed up to GitHub as wip/* : bar, cashier, gas, casino-cards, store-design, city-voices, ux-design.
- Launched: Android device-lab engineer (opus) on branch ci-lab from origin/main; allowed to push ONLY ci-lab; emulator workflow + in-app tour + real screenshots.
- QUEUE for free slots: (1) casino finisher (sonnet), (2) performance engineer (opus).

## GAPS LIST ("everything we haven't thought of", owner asked 07:57) — owners in brackets
1. Never run on Android -> emulator device lab in CI [launched]. Other screen sizes / Android 11-16 / OEM fonts: emulator matrix later.
2. Canvas performance of art-heavy scenes: cache static layers into bitmaps (Gfx layer cache), 60 fps cap, redraw only when dirty [perf engineer + lead].
3. Exploits: pause-refund in venues; round replay by backgrounding; save scumming [decisions above].
4. Art direction consistency across 3 artists + 14 minigame scenes: style guide + unification pass [art director review in phase B/C].
5. Photosensitivity: cap flashing (police lights, neon) below 3 Hz, reduce-motion option [UX wave].
6. Crash handling: uncaught-exception handler writes a report + friendly recovery screen + "share report" [integrator, shell].
7. Autosave at each new day / after each shift / venue exit, not only onPause [lead/integrator].
8. Unmerged agent branches exist only on this VM: push every finished branch to wip/* [director, ongoing].
9. Release: AAB + release signing key owned by the owner + Play App Signing; applicationId is permanent (decide before first upload); debug-signed installs cannot upgrade to a release-signed build -> add save export/import [integrator + owner].
10. Play: content rating (simulated gambling, crime, alcohol via the bar) likely Teen/17+; privacy policy; data-safety form; target API policy; store listing assets generated from the game [owner + release agent].
11. Localisation: all strings are Hebrew literals; decide Hebrew-only launch (recommended) vs string tables now [owner decision].
12. No analytics by design -> in-game feedback button (mail intent with anonymous stats) for real-player feedback [integrator].
13. AI-generated code/art: copyright and disclosure questions for a commercial release [owner, non-lawyer flag].
14. Name / trademark check for "אמא על הקו" and the English title [owner].
15. Audio focus (pause music on a real phone call), headphones unplug, silent mode [sound designer follow-up].
16. Gesture-navigation edge conflicts with drags (gas wipe, chase): system gesture exclusion for the play area [integrator, shell].
17. Achievements / statistics screen (also where נחת is earned) and New Game+ [store engineer].
18. Accessibility: TalkBack cannot read a custom canvas (state it); colour-blind, text size, reduce motion, left-handed are planned.
- WaiterMini DONE: agent aa1cf229f60ec306a, branch worktree-agent-aa1cf229f60ec306a, commit dcd2190 (backed up wip/waiter). LAB OK; 4 rounds; skill 0.43/0.83/1.29/1.80/1.89; 31-39 s. Drag plates (uses move), peek costs tips, look-alike dishes, mind changes. Screens scratchpad/waiter/shots{,2}/.
- FightMini DONE: agent a38a57c3812a253d2, branch worktree-agent-a38a57c3812a253d2, commit 5e5a60a (backed up wip/fight). LAB OK; event; skill 0.00/0.67/1.27/1.83/2.00; 25-28 s. Returns MISS/OK/PERFECT only; host applies robbery. Screens scratchpad/fight/.
- Roulette agent a537a73ad7709f147 got stuck re-sending its report in a loop: STOPPED with TaskStop (work is committed 3810b35 and backed up as wip/casino-machines).
- Owner asked (07:58) for a tower-defense style profession/minigame: launched PestMini (exterminator "מדביר/מדבירה", opus) + PestSim. New job id to assign at integration (suggest 9), night call at the restaurant/bakery.
- QUEUE for free slots: (1) casino finisher (sonnet): settle-on-pause for all 4 venues, cards polish, poker floater trace, bet caps; (2) performance engineer (opus).
- Comedian DONE: agent a2545166539c4427e, branch worktree-agent-a2545166539c4427e, commit 368d0a4 (backed up wip/mom-lines). MomLines.java (110 weekly calls, 89 summary remarks, 61 toasts), MomCheck (MOM OK). Every call has 3 options; effects also set seen.<id>=1. Vars: mom.boxes, mom.honest, mom.collapsed, mom.rank, mom.job; lie.job = office job with a swivel chair; lie.money = savings; lie.home = big flat with balcony, sea view, guest room. Confessions reset lie flags to 0 (lie.count never decreases). Engine must start mom.trust / mom.worry at 50 and clamp 0..100. Family: צביקה (dad, one word per call), סבתא רחל, דודה חנה (rumour hub), אורנה מהבריכה + יואבי, פנינה מלמטה, the blue box.
- Owner (08:01): use Fable freely where needed, plenty of budget.
- Launched: casino finisher (opus; merges both casino branches; settle-on-pause, poker payout trace, limits, one family look, polish) and Hebrew editor pass one (fable; merges mom, lines, bar, cashier, gas, waiter, fight; string literals only; docs/GLOSSARY.md).
- Performance engineer HELD until phase C (MiniSuite already measures per-frame time/allocations; full-game + Android rendering strategy needs the integrated engine and device-lab numbers).
- IMPORTANT for integration: merge the Hebrew editor's branch and the casino finisher's branch INSTEAD of the originals they absorbed.
- OWNER DECISIONS (08:02): (a) ambition: "game of the year" quality bar; (b) THE RELEASE WILL BE IN ENGLISH; for now focus on Hebrew.
  => English localisation track (do NOT disturb the Hebrew work now):
     1. After alpha (files stable): localisation engineer adds a string layer (Hebrew stays the source language; whole-sentence patterns with placeholders, no grammar-dependent concatenation; gender only where the language needs it), start/end alignment and mirrored layouts instead of hard-coded right/left, a lint against new raw literals, a language switch in settings.
     2. After the Hebrew is final (post playtest #2): English TRANSCREATION by a comedy writer (not translation: mom's humour, names, idioms and the family must be re-imagined for an English-speaking audience without ethnic stereotype), then an English editor pass, then playtest personas in English.
     3. Rules for everyone from now: no text baked into art (signs must use c.text), no sentence built from fragments that only works in Hebrew word order.
  => Store listing, rating, privacy policy in English as well.
- Story writer DONE: agent aac7ac99b419cea2b, branch worktree-agent-aac7ac99b419cea2b, commit c697dd5 (backed up wip/story). Story.java, docs/STORY.md (Hebrew bible), StoryCheck (STORY OK). Opening 4 beats; 114 morning beats (43 with a choice); finale 5-9 beats, 7 endings (proud, honest-and-modest, farce, crime boss, broke-going-home, mom knew, "the lie came true"). Cast: שמעון (landlord), מזל (neighbour, cat שוקו), bosses רינה, ניסים, דנה, גבי, פרץ, רפאל (alley dealer), השוטר אלמוג, dad by text signed "אבא". Vars story.* (mazal, mazal_loan, mazal_cover, mazal_honest, home, land, land_cover, defer, boss_cover, boss_honest, raf, raf_curious, raf_no, raf_out, raf_cover, cop_promise, dad, dad_money, school, dana).
  ENGINE REQUESTS from the writers (for integrators): gendered answer options (optionsF, or the "[m|f]" markup Lines uses); more placeholders than {name} (job, debt, money); a finale that can branch on the dinner answer; effects that touch rent debt / deferral (story.defer); Facts fields for profession, flat tier, casino, alley, school; beats triggered by time of day / place / delay; speaker -> portrait mapping (character artist's PersonArt.portrait keys); start mom.trust and mom.worry at 50 and clamp; mark seen.<id>; do not call onNewDay on day 121.
- Launched: release engineer + store compliance (opus) on branch release-lab from origin/main; may push ONLY release-lab; docs/RELEASE.md, release build + release.yml, adaptive launcher icon, StoreArt tool, privacy policy + listing drafts (EN + HE).
- OWNER (08:08): wants the game to appeal to women and girls too; suggested pancakes, cakes, dish washing.
  => QUEUE (launch as slots free, sonnet each, same Mini brief pattern):
     (1) DishMini: dishwasher "שוטף/ת כלים" at the restaurant, the classic entry job (no requirements, lowest pay): satisfying scrub-rinse-stack with drag (move), stubborn stains, a fragile glass, rush racks; cozy and tactile.
     (2) PancakeMini: breakfast shift at the restaurant/café: pour, watch the bubbles, flick to flip (swipe up), stack to order with toppings; timing + a greedy tall stack that can topple.
     (3) CakeMini: pastry decorator "קונדיטור/ית" at the bakery: follow an order card: pipe cream along a path (drag accuracy), place decorations symmetrically, write-free; creative, precise, calm; a free-style bonus.
  => Also for wave 2: flat decorating screen (store decor items shown in the home), the neighbour's cat as a recurring cozy presence, and playtest personas must be at least half women across ages 16-70.
  => Director's stance (said to owner): these are cozy/creative genres with broad appeal; what makes the game welcoming is a fully written female lead (both genders everywhere), no talking down, room for self-expression.
- BakerMini DONE: agent a141de2b26b841043, branch worktree-agent-a141de2b26b841043, commit cab6089 (backed up wip/baker). LAB OK; 4 rounds; rhythm at 88/100/112/124 BPM with count-in, 8th-note patterns, syncopation, optional held "fold" flourish; windows PERFECT ±60 ms / OK ±130 ms on accumulated time; skill 0.09/0.40/1.13/1.98/2.00; 36 s. Screens scratchpad/baker/.
- Developer DONE: agent a56be74398c639ba6, branch worktree-agent-a56be74398c639ba6, commit 1b79f0f (backed up wip/developer). DevStudyMini (1 round, 5 "what comes next" puzzles, duck hint) + DevMini (4 rounds, bug hunt on a station line, ship early vs extra test). LAB OK both; skill study 0.00/0.67/1.27/1.87/1.93, shift 0.10/0.66/1.05/1.76/1.82. DevMini reuses static helpers from DevStudyMini (keep both). Screens scratchpad/dev/.
- OWNER (08:12): the pancake pitch was uncreative; wants an especially creative minigame designer who knows browser games. => Launched CREATIVE LEAD FOR MINIGAMES (fable): docs/MINIGAMES.md (creed, full specs for pancakes + cake decorating, creative sheet + 3 rank modes for every minigame, signature ideas like mom calling mid-shift, up to 5 new pitches, cut list, priorities). PancakeMini and CakeMini implementers WAIT for its specs.
- OWNER (08:13): "we do not steal games; this needs control too". => Launched ORIGINALITY AND IP REVIEWER (opus, web search): docs/ORIGINALITY.md (item table, rules, release gate, lawyer list) + tools/originality-check.sh. Second pass later for art/audio/remaining minis.
- DECISION: each promotion changes how a job plays (rank modes) — designs from the creative lead; build in the fix wave after playtest #1 (or earlier if slots are free).
- CookMini rework DONE: agent a1ad0c94787d1102a, branch worktree-agent-a1ad0c94787d1102a, commit 75e145c (backed up wip/cook). LAB OK; 4 rounds; tickets on a rail (egg / schnitzel / steak with cook-time pips and patience), choose which ticket to start, RUSH tickets x2, gas stove scene; skill 0.13/0.63/1.24/1.98/2.00; 33-43 s. REPLACES the reference CookMini in next (merge conflict expected with the lead's tree only if the lead edited CookMini: take the rework). Screens scratchpad/cook/ (peek-* show tickets).
- STATUS 08:20: 15 finished (bar, cashier, gas, waiter, fight, casino cards, casino machines, store design, city voices, UX, comedian, story, baker, developer, cook). Running 20: lead, alley, doctor, env artist, char artist, illustrator, economy, sound, composer, chase, lawyer, test infra, device lab, exterminator, casino finisher, Hebrew editor, release engineer, dishwasher, creative lead, originality reviewer. Waiting on creative lead: pancake + cake implementers.
- OWNER RULE (08:15): not every minigame is mandatory. One CORE minigame per job earns the wage; every extra (second minigame, rank mode, mom calling mid-shift, special events) is the player's choice; all extras together add at most +10% to that day's pay. Sent to the creative lead and the economy designer; integrators must enforce the cap in the pay logic (constant EXTRAS_DAILY_BONUS_CAP = 0.10).
- New jobs to add at integration beyond the original six: dishwasher (entry), exterminator (night, tower defense), pancake cook (breakfast), pastry decorator (bakery); professions doctor / lawyer / developer.
- OWNER CORRECTION (08:19): the "10%" was a metaphor. Extras = a modest, felt-but-not-decisive edge, never mandatory, and the reward must be MORE INTERESTING than a flat percentage: mixed reward types (tips, time, free meal, energy, promotion XP, favours owed by characters, mom's trust, rare cosmetics / נחת on milestones) and real trade-offs. Economy designer sizes the band from the model; creative lead designs the variety. Sent to both. (No hard EXTRAS cap constant; use the designer's band.)
- OWNER (08:19): mom must not nag too much. => Rules for integration: mom's weekly call = 2 cards at most (question + reply), fast to tap through; at most ONE unsolicited mom moment per game day (the day-summary remark counts); "אמא הייתה אומרת" sayings rare (about 15% of eligible moments, never twice a day); a mid-shift call is optional and at most once in about five shifts; declining a call is allowed (cost in trust). Add a settings item "כמה אמא": שקטה / רגילה / דואגת (frequency of the optional mom moments; the weekly call and the story stay). Hebrew editor told to trim mom's lines (opening <= ~140 chars, replies <= ~110).

## 08:20-09:00 — OWNER PIVOT + INTERRUPTION + THE FABLE STUDIO (read this first after any context loss)
- Finished + backed up since 08:20: chase 304d810 (wip/chase), economy 09894f1 (wip/economy: docs/DESIGN.md, Tuning.java, tools/balance/model.py),
  icons+title ec3b165 (wip/icons-title), music 7d3ad63 (wip/music; WAVs scratchpad/audio/music), doctor 9cd054b (wip/doctor),
  characters 7352459 (wip/characters), sound e5e0542 (wip/sound; WAVs scratchpad/audio/sfx).
- OWNER (08:21-08:37), in order:
  * "you did not think of what we had not thought of; take it to the highest level"; "there is a reason for everything"; "have people who examined other games look at it".
  * No "amount of mom" menu. Mom in small doses; once in a while a joke from mom/dad. HIS GAG (canon): mom says dad went to get milk; a week later dad texts he has been looking for milk all over the city for a week. "Am I funnier than you? Be sharp!"
  * Saw the top-down city screenshot: "What is this design? Lower the camera. You said 3D."  => city rebuilt as low-camera real 3D (own software renderer through Gfx).
  * I stretched the milk gag across the year; he REJECTED that: "no no; take the whole story of the game to the depth of Red Dead Redemption. Game of the year." (depth, never content)
  * "Open the wallet, focus on the game and the story, run improvement, do not be afraid to rephrase/replace, only for the better."
  * "Bring 20 Fables who consult among themselves and work together as a dev team as if they had done this for 30 years."
- HAZARD LEARNED: when the owner interrupts (stop) instead of just sending a message, ALL running subagents are killed and cannot be resumed.
  At ~08:31-08:37 this killed 13 agents: lead engineer, alley, env artist, lawyer, test infra, device lab, exterminator, Hebrew editor, casino finisher,
  release engineer, dishwasher, creative lead (no files survived), originality reviewer. I committed their uncommitted work and pushed:
  wip/lawyer adbab1e (drafts), wip/test-infra 3d965b4, wip/pest 2d22f3b (draft), wip/casino-finisher 86aea41 (in progress), wip/dish f342b17 (draft),
  wip/originality 3ad4cdb (tools only), wip/alley c0bac17 (finished, unreviewed), wip/city-2d 01c8d96 (top-down city, superseded by 3D),
  wip/hebrew-editor d787faf (merges only), ci-lab d0d83ba and release-lab 21506bd (already pushed). Engine on `next` = 5a9af0d (compiles; Sim never passed; no report).
  Told the owner (he should send messages without pressing stop). Rule for agents: commit every 10-15 minutes (room ruling 7).
- THE STUDIO (20 Fable agents, all running since ~08:45-08:58). Shared room: scratchpad/room/ (BOARD.md protocol, decisions.md rulings 1-9, ROSTER.md ids,
  papers/, threads/, story/). Context file for newcomers: scratchpad/director/context.md.
  Leads: narrative-director adbbb1411af16b3b1 (docs/STORY2.md + STORY2-SAMPLES.md) · design-director a25967a9b1941f932 (docs/VISION.md, feature map)
         · tech-director a9e486e1c6cdd07fc (MAIN TREE `next`: audit engine, fast Sim ALL OK, Engine section in TEAM.md, integration plan + merges)
         · creative-lead a01bccfd996549ab9 (docs/MINIGAMES.md, then PancakeMini, CakeMini)
  Story: story-engine a7b6af7d55a5e1ce4 (Scene DSL, StoryDirector, Rel, journal data, StoryLint/StorySim/ScenePreview, docs/WRITING.md)
         · comedy-head a30f94ab45a241841 (docs/TONE.md, Gags.java, best20.txt in scratchpad/comedy-head)
         · writers: w-family ae8112be016ea12f3 · w-building ad46bb45b28b11200 · w-kitchen a1fa9d1f4ba6edd92 · w-night ace3dd93bfc9db117
           · w-roads a08d49c477c7a73cb · w-strangers a07ad52820904f3ee  (dossiers -> room/story/<role>.md, scenes -> docs/story/<role>/ on their branches)
  World/screen/play: city3d a6ac8660070cf6144 (first frames -> scratchpad/city3d/first/, will message main; OWNER PICKS THE CAMERA)
         · presentation abb40779032c1d29b (TalkView/PhoneView/JournalView; first shots -> scratchpad/presentation, will message main)
         · gameplay-a a84d47175d2301fd9 (dish, lawyer, alley review) · gameplay-b a4806462bae30bc45 (exterminator TD, casino pass)
  Quality/platform: critic a88102d591f816893 (docs/reviews/CRITIC-1.md; in-house reviewer of papers) · qa-lead a225ebf79cd2d6247 (tools/check.sh, room/threads/bugs.md, docs/QA.md)
         · platform a2cd26b3c05c38534 (may push ONLY ci-lab and release-lab; device lab, release, batching, shell patches)
         · originality aea6789b06a7ddb9e (docs/ORIGINALITY.md, tools/originality-check.sh, docs/reviews/TRUST-1.md)
  NEXT ROTATION when slots free: art director (unify 2D scenes/UI with the 3D town), audio director (adaptive music, voices; owner is a sound teacher: wait for his ear on the WAVs),
         UX engineer (implement UX.md + settings), language editor (Hebrew pass on all new writing + glossary), integration engineers, playtest driver + 20 personas.
- RULINGS recorded in room/decisions.md: pillars (reason for everything; sharp; real 3D), RDR depth-not-content, milk gag = two messages only, licence to replace only for the better,
  economy answers (crime tail risk; money never ends the game -> hostel; the front approved PG-13), no "amount of mom" setting, shared story ids/vars, 20 on one model.
- Settings list change: REMOVE "כמה אמא". Mom rules: weekly call two cards; occasional short family joke; at most one unsolicited family moment per day.
- Art seen by director: title (night skyline, phone logo) good; 9 building illustrations good; 14 portraits good; top-down city rejected by owner.
- TO SEND OWNER: audio (music loops + sfx reel) for his ear; 3D first frames (3 cameras) to choose; presentation first shots; comedy best20; narrative theme/spine when posted.
- 09:10 critic: simulated gambling => PEGI 18, Australia R18+ (legal), Apple 18+, ESRB Teen (thread room/threads/age-rating-casino.md, sources inside). Working assumption = option B
  (Hebrew build keeps the 4 games; international build: casino as place/story, betting off by one build flag). ASKED OWNER; pending his answer. Room ruling 13.
- 09:08 OWNER on casino rating: "leave as is for now; when we publish we'll think about it" (room ruling 14). He also asked: "give me the latest version meanwhile".
- 09:08 SECOND KILL: his message arrived as an interruption and killed ALL 20 Fable agents (~12-22 min into their work). Saved everything:
  branches pushed as team/<role> (city3d 63ea5cd, presentation 4d8cfdd, story-engine 9c64a5d, narrative-director b7689e3 (canon v1.1 + bible draft),
  critic 24bc612, qa-lead 66c230c, gameplay-a f342b17, gameplay-b 2d22f3b, originality 3ad4cdb; comedy-head/creative-lead nothing committed);
  design-director, platform and the six writers had written nothing in the repo (their papers/threads are in scratchpad/room).
  Main tree `next` = ee50aff (tech director mid-work, DOES NOT COMPILE; last green = 4a1c774: Sim gate ALL OK in <3 s + economy merged).
  Room material survives: 11 papers, 12 threads, story/00-CANON.md (theme, laws, family), critic's age-rating thread.
  Installable build is still 0.1.3 (release tag `latest`). NOT relaunching without the owner's explicit go (asked him; options: all 20 / small team to get a build to his phone first / not now).
