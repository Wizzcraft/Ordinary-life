# אמא על הקו — team handbook

Read this fully before you start. It is the contract between everyone working on the game.

## The game
A Hebrew life-sim for Android phones (portrait, one thumb). You move to the big city, rent a cheap flat
far from everything, find work, and try to hold on until your parents visit at the end of the year.
Mom phones every week; you can lie to her, and every lie has to survive the visit.

- Time is the resource: one game hour is 3 real seconds, a day is about 72 seconds, rent is due every
  10 days (a "month"), the year is 12 months. Walking across town costs 2-3 game hours.
- Needs: food, water, energy (0..100). Stats: fitness, charisma, education (0..100).
- Money: jobs with a minigame each and three ranks; later school and professions, a casino, and an alley
  with fast, risky money, police heat and a criminal record.
- Tone: warm, funny, Israeli, self-aware. Never cruel or cynical. PG-13. Crime is tempting, risky and has
  consequences; it is never glorified and substances are never named ("סחורה").
- Look: EVERYTHING is drawn in code with polygons. No image files, no fonts, no audio files, no libraries.
  Everything is original: no characters, logos, jokes, tunes or designs taken from any existing work.

Pillars: (1) every day is a trade-off between time, money and needs; (2) every job has its own verb;
(3) mom's calls give the game its heart and its jokes; (4) learnable in five seconds, deep enough to
master; (5) every meaningful action answers back with motion and sound.

## How we work
- You are in your own git worktree on your own branch. Run `git rev-parse --show-toplevel` first and use
  that path. Never work in `/home/claude/ordinary-life` itself unless your brief says so.
- Commit locally when done (one or a few commits). NEVER push, never touch the remote, never switch
  branches, never run `gh`. Do not install anything (no network installs are available).
- Create NEW files only, unless your brief names existing files you own. Never edit files you don't own:
  several agents are working in parallel and the integrators merge everything afterwards.
- Temp files and anything the director should see (screenshots, WAVs, notes) go under
  `/tmp/claude-0/-home-claude/afbbc77c-f63c-5589-a685-e163ec397744/scratchpad/<your-role>/`
  because a worktree may be deleted once you finish.
- Final report (your last message): branch name and commit hash; what you built; the evidence it works
  (lab or test output); the exact API others need; what you are unsure about; what you would do next.
  Be factual. Report failures and gaps plainly.

## Repository map
- `app/src/main/java/com/wizzcraft/ima/core/` — the whole game, pure Java, no Android imports.
  It draws only through the `Gfx` interface, so it runs on a desktop for testing.
- `app/src/main/java/com/wizzcraft/ima/` — thin Android shell (Activity, View, Canvas-backed Gfx).
  There is no Android SDK on this machine: shell code can only be type-checked against hand-written stubs
  in `<scratchpad>/stubs` and is first run on a real phone. Be conservative there.
- `tools/desktop/com/wizzcraft/ima/core/` — desktop harness in the same package (so it sees
  package-private members): `AwtGfx` (Java2D renderer), `Sim` (plays the whole game), `MiniLab`
  (test bench for one minigame).
- `docs/` — this handbook; `docs/drafts/*.txt` are the director's unfinished engine drafts (reference only).

Build everything and run the full-game check (must print `ALL OK`):

    javac --release 17 -Xlint:all -d tools/out/classes app/src/main/java/com/wizzcraft/ima/core/*.java tools/desktop/com/wizzcraft/ima/core/*.java
    java -Djava.awt.headless=true -cp tools/out/classes com.wizzcraft.ima.core.Sim tools/out

## Drawing: `Gfx`
Logical units. The screen is always 360 wide; the height is 560..900 (760 on the target Pixel).
Colours are 0xAARRGGBB ints; alpha blends.

    fillRect(x, y, w, h, argb)            fillRoundRect(x, y, w, h, r, argb)
    strokeRoundRect(x, y, w, h, r, strokeWidth, argb)
    fillPoly(float[] xy, argb)            // x0,y0,x1,y1,... closed
    fillCircle(cx, cy, r, argb)           line(x1, y1, x2, y2, strokeWidth, argb)
    text(s, x, baselineY, size, argb, align)   // one line; align = Gfx.LEFT / CENTER / RIGHT
    textWidth(s, size)

Text is one line, drawn right-to-left by the platform. For paragraphs use `Ui.paragraph(c, text, cx, y,
maxWidth, size, argb)` (centred, wrapped). Money is written with `Ui.nis(n)`.

`Ui` palette for interface chrome: `BG` (0xFF12161C), `PANEL`, `PANEL2`, `INK` (text), `DIM` (secondary
text), `ACCENT` (gold), `GOOD` (green), `BAD` (red), `BLUE`, `ORANGE`, `DARK_INK`. Illustrations may use
any colours that sit well on the dark background. Helpers: `Ui.in`, `Ui.clamp`, `Ui.bar`, `Ui.darken`,
`Ui.isDark`, `Ui.clock`, `Ui.wrap`, `Ui.person` (the little polygon person). `Btn` is a simple button
(`new Btn(id, x, y, w, h, label, enabled, primary)`, `hit(x, y)`, `draw(c)`).

## Minigames: `Mini` and `Host`
Every playable activity is a `Mini` (read `Mini.java` and `Host.java`, they are short). The engine hosts
it and owns everything around it: intro card, pause, clock, wages, result banner, saving. A Mini never
touches the engine; it talks to the world only through `Host` (player facts, money, persistent
variables, and game feel: `h.text`, `h.burst`, `h.shake`, `h.sfx`).

- One file per Mini: `core/<Name>Mini.java`, `final class <Name>Mini extends Mini`, package-private,
  no-argument constructor, helper types nested inside. `CookMini.java` shows the shape (its tuning is
  being reworked; copy the structure, not the balance).
- `rounds()`: work shift = 8, 4 or 2; event or study session = 1; venue (casino table, market) = 0.
- `draw(c, top, bottom)`: stay inside the play area. It is about 500 tall on the target phone and about
  300 on the smallest screen: lay out relative to `top` and `bottom`, never with absolute y values.
- `bot(r, skill)`: a scripted player that acts only through `down / up / move`. The lab and the balance
  simulations depend on it, so make it honest: skill 0 is clueless, 1 is an expert.
- `pause()`: settle anything in flight fairly. An interrupted round is replayed from `begin()`.
- Venues draw their own exit button labelled "יציאה", move money only through `h.pay` (which refuses
  what the player cannot afford), and keep anything that must outlive the visit in `h.setVar` under
  their own namespace.

Test bench (prints `LAB OK`):

    java -Djava.awt.headless=true -cp tools/out/classes com.wizzcraft.ima.core.MiniLab <Name>Mini <outDir>

It writes screenshots at heights 760 and 560 (LOOK at them with the Read tool: red lines mark the play
area; anything you draw outside is covered by the host), checks that every round ends by itself within
60 seconds with no input, prints the result distribution at five skill levels, and hammers the Mini
with random touches and pauses.

### What a good minigame is here (the bar)
Think of the best Flash-era and mobile-Java games: Papa's Pizzeria, Diner Dash, Cooking Mama, WarioWare,
Tapper, Bartender: The Right Mix, Dope Wars, Punch-Out, Canabalt. Take the craft, never the content.
1. One verb, understood in five seconds without reading; the intro text is a safety net.
2. It escalates across the rounds (speed, count, a new wrinkle), following `difficulty`.
3. There is a decision or a risk, not only reflex: a greedy option that can backfire.
4. Skill shows: PERFECT must feel earned. Target for the lab's skill table (score = average of
   MISS 0 / OK 1 / PERFECT 2): skill 0 at most 0.6, skill 0.6 between 1.0 and 1.4, skill 1.0 at least 1.6.
5. Length: a whole work shift takes 25-45 real seconds (8 rounds of 3-5 s, 4 of 7-11 s, or 2 of 13-20 s).
6. Juice on every meaningful action: a floating word or number, a burst on success, a shake on failure,
   a fitting sound. Idle things breathe; nothing just sits there.
7. It is a little scene, not a spreadsheet: draw the counter, the customer, the pump. Keep it legible:
   touch targets at least 48 units, text at least 12, strong contrast on the dark background.
8. Playable with one thumb in the lower two thirds of the play area. No tiny targets near the edges.
9. Fair: never punish the player for something they could not see coming; a tired player (energy
   below 30) arrives through a higher `difficulty`, so no extra handling is needed.

## Sound ids (`Sfx`)
TAP, COIN, GOOD, PERFECT, BAD, DOOR, PHONE, HIRED, RENT, COLLAPSE, LEVEL, TICK, POUR, SIREN, PUNCH,
WHOOSH, SIZZLE, BEEP, DING, EAT. Use the closest one; ask for new ids in your report rather than
inventing numbers.

## Persistent variables
`h.var(key)` / `h.setVar(key, v)` (floats; unset = 0; keys lowercase `[a-z0-9_.]`). Shared names:
`fit`, `cha`, `edu` (stats 0..100) · `xp.<jobId>`, `hired.<jobId>` · `heat` (0..100), `record` (0/1),
`arrests`, `collapses` · `gems` (premium currency), `own.<sku>` (1 = owned), `look.*` (equipped looks) ·
`opt.sfx`, `opt.music` · `lie.job`, `lie.money`, `lie.home` (1 = told mom that lie), `lie.count`,
`mom.trust`, `mom.worry` (0..100, start 50) · `seen.<beatId>` · `story.*`.
Take your own prefix for anything else (`alley.*`, `casino.*`, `school.*`, ...) and list it in your report.

Job ids: 0 cook, 1 waiter, 2 baker, 3 barman, 4 gas attendant, 5 cashier. Ranks 0..2:
מתלמד / מקצוען / אחראי משמרת.

## Story data: `Beat` and `Facts`
Dialogue is data. A `Beat` is one message from a character with optional answers, per-answer replies and
effects; `Facts` is the read-only snapshot of the player that narrative code receives. Their Javadoc is
the specification (text limits, `{name}`, the effects grammar).

## Hebrew
- All player-facing text is Hebrew: natural, spoken, short. No Latin letters, emoji or nikud.
- The player has a gender: every line addressing them needs both forms (`h.t(m, f)`, or `Beat.m` / `Beat.f`).
- The player has a name: use `{name}` in beats where a character would actually say it.
- Numbers and ₪ inside Hebrew sentences are fine; avoid starting a line with a number.
- Proofread: agreement, spelling, and whether a real person would say it.

## Engineering rules
- Deterministic visuals: no `new Random()` per frame; derive pseudo-random values from indices and time.
- Go easy on per-frame allocation inside loops; stay under about 1500 primitives per frame.
- Never throw on odd input: touches can land anywhere at any time, twice in one frame, before the first draw.
- The app can be closed at any moment and must reopen at the same point; that is the host's job, your
  part is `pause()` and keeping long-lived state in variables.
- Plain, readable Java 17. Comments say why, not what.
