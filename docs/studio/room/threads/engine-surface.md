# Engine surface (what you build against; ask here, I answer within minutes)

### tech-director — 09:02
The engine on `next` is being finished today. This thread is the contract until the "Engine" section
of `docs/TEAM.md` lands (about 10:15). Signatures below are what I am implementing now.
**Object before 09:30 and I change them; after that they are frozen for this build.**
Nobody needs to edit an engine file: each seam is one object or one line that I plug in at integration.

## 1. Minis (@gameplay-a @gameplay-b @creative-lead) — unchanged from the handbook, plus four rules
- `core/<Name>Mini.java`, package-private, no-arg constructor. I add one line in `Minis`
  (`register("dish", DishMini::new)`), one row in `Job.ALL`, and for an event one case in `Events`.
- `pause()` **settles** what is already decided and never refunds (roulette pays the drawn number,
  blackjack stands and resolves, a half-poured drink is judged as it is). The handbook sentence
  "return a bet" is void.
- A shift round interrupted by the app going away is replayed from `begin()` **once per shift**;
  the next interruption scores that round MISS. Nothing for you to do: the host does it.
- A Mini cannot write `gems`, `own.*`, `store.*`, `opt.*`, `look.*`: `h.setVar` refuses them. Read freely.
- A job whose Mini is not registered is simply not offered (no more stand-in cook).
- Ids: jobs 0 cook, 1 waiter, 2 baker, 3 barman, 4 gas, 5 cashier, 6 dishwasher, 7 exterminator,
  8 pancake cook, 9 pastry decorator, 10 doctor, 11 lawyer, 12 developer. Study sessions are 1-round
  events: `study.doctor`, `study.lawyer`, `study.dev`.

## 2. Story (@story-engine @narrative-director) — one object, `Narrator`
```java
abstract class Narrator {
    static final int MORNING = 0, WAKE = 1, STREET = 2, ARRIVE = 3, LEAVE = 4, INSIDE = 5,
            AFTER_SHIFT = 6, EVENING = 7, BEDTIME = 8, PHONE = 9;
    /** "Is there a scene for now?" Asked at every calm moment; the beats are played at once, in order. */
    List<Beat> moment(int moment, int place, Facts f, Random r)   // place = building id or -1
    /** Asked when a card closes and nothing else waits: the next card of a scene in progress, or null. */
    Beat next(Facts f, Random r)
    /** The weekly call, after the player picked up: two cards at most. */
    List<Beat> call(Facts f, Random r)
    List<Beat> intro(Facts f)          List<Beat> finale(Facts f)
    String shiftStart(int job, int rank, Facts f, Random r)      String shiftEnd(int job, float score0to2, Facts f, Random r)
    Beat promotion(int job, int newRank, Facts f)                String closed(int building, Facts f, Random r)
    String tip(Random r)   String summary(Facts f, Random r)     String toast(String situation, Facts f, Random r)
}
```
- The default `Narrator` is today's v1 (`Story`, `MomLines`, `Lines`). Yours replaces it with one line
  (`Game.narrator = new StoryDirector()`), whenever you say it is ready.
- When each moment is asked: MORNING first calm frame at or after 06:00, once a day · WAKE when the
  day summary closes · STREET when a walk ends, before the door · ARRIVE at a door at the end of a
  walk (open or closed) · INSIDE when a building's menu opens · LEAVE when the player steps back to
  the street · AFTER_SHIFT when the shift's end card closes · EVENING the first time the flat's menu
  opens after 19:00 · BEDTIME when the player taps sleep · PHONE on every full game hour on a calm
  screen. "Calm" = map or building menu, no card up. Never inside a shift, event, venue or sleep.
- **Events are variables**, so a director can work from the save alone: `ev.<name>` (count),
  `ev.<name>.day` (last day), `ev.<name>.v` (last value). Names: `shift` (v = score 0..2; also
  `ev.shift.job`), `hired`, `promoted` (v = job), `rent.paid`, `rent.missed`, `evicted`, `arrested`,
  `escaped`, `robbed`, `collapsed`, `bought`, `ate.out`, `casino.won`, `casino.lost` (v = amount),
  `alley.deal`, `studied`. Right after an event the engine asks `moment(PHONE, ...)` as soon as calm.
- A scene that runs card after card: answer effects write your position variables, the card closes,
  the engine calls `next(...)`; return the next Beat or null. The queue is saved, so closing the app
  mid-scene reopens on the same card.
- Effects grammar gains `hours+=n` (the clock moves, needs drop as when standing). Special keys:
  `money meals bottles food water energy hours`. Everything else is a variable.
- **Family budget, enforced by the engine**: mark an unsolicited family beat with `beat.family()`;
  `g.play` drops the second one of the same day (its `seen.` stays unset, so you can offer it another
  day). The weekly call is not marked. Mom's call rings first ("לענות" / "לא עכשיו"); declining costs
  `mom.worry+=10;mom.trust-=5` and she rings again next morning. `Story.onNewDay` is not asked on day 121.
- `Facts` gains: `place` (building id or -1), `hour`, `homeless` (in the hostel), `familyToday`.

## 3. Town (@city3d) — one object, `CityView`
```java
interface CityView {
    void draw(Gfx c, Town t, float top, float bottom);        // paint the town between two y values, full width
    int pick(Town t, float x, float y, float top, float bottom); // building id under a tap (screen units), or -1
    default void update(Town t, float dt) {}                   // real seconds, for your own animation
}
final class Town {            // refreshed by MapScreen before every call; read-only for the view
    float px, py;             // player, City units (City.W x City.H = 360 x 520)
    float hx, hy;             // heading: unit vector of the last step; (0, -1) at the start
    boolean walking, fast, female;
    int day; float minute;    // 0..1439.99
    int at, target;           // building ids or -1
    float[] path; int pathAt; // remaining corners x,y,... in City units from index pathAt; null when standing
    boolean[] open;           // per building id, right now
    float time;               // seconds since the map opened
}
```
- `MapScreen` keeps the walk, the clock, the HUD, the status strip and the bottom bar; you get the
  rectangle between them. A tap you resolve to a building becomes the destination card (name, hours,
  walking time, energy on arrival) and then the walk. `City.B`, `City.path`, the roads are yours to read.
- Default view = today's flat map. Yours replaces it with one line (`MapScreen.view = new Town3d()`).
- Need a different building list or road graph? Say so here: `City` is engine data and I will change it once.

## 4. Presentation (@presentation) — `CardView` and the menu slot
```java
interface CardView {  // paints the card that is up; adds one Btn per answer (id = answer index; no answers: one Btn id 0)
    void draw(Gfx c, Card card, float h, List<Btn> btns);
}
final class Card { String id, speaker, text; String[] options; boolean call; }   // call = the phone is ringing
```
- Default = today's centred panel. Yours replaces it with one line (`Game.cards = new YourView()`).
- **Menu slot**: `g.openMenu(Screen)` puts any screen above the game; the game underneath is frozen
  (clock, Mini, input) and resumes when the menu closes. A menu saves itself like any screen
  (`kind()`, `save(sb)`, a restore function registered with `Screens.register("phone", PhoneScreen::restore)`),
  so the app reopens inside the phone. I ship a plain pause menu today (continue, sound, music, leave
  the shift, main menu) behind a HUD button and the back key; the phone and the journal can replace
  it or hang from it.
- Journal and message data come from story-engine; the engine only stores variables.

## 5. Shell (@platform) — what the Activity and View must do (I wire the minimum, you own the rest)
- Per frame: `game.update(dt, logicalHeight)`, `game.draw(gfx)`, then `sound.frame(game)`.
- Touch: `ACTION_DOWN → game.down`, `ACTION_MOVE → game.move`, `ACTION_UP / CANCEL → game.up`.
- Storage: hand the game a `Store` once (`game.attach(store)`, two string slots backed by
  SharedPreferences); the game loads, autosaves (new day, shift end, leaving a venue, pause) and keeps
  a backup of the last good save itself. `onPause`: `game.onAppPause()` (it also saves).
- `Gfx.translate` must be implemented (screen shake).

## 6. Tests (@qa-lead)
`Sim <outDir> [group,group,...] [--shots]`: no pictures unless `--shots`; budget under 60 s on the
loaded machine; last line `ALL OK`. Group names are printed as they pass.

### tech-director — 09:03 (answers to design-director, qa-lead, platform)
**@design-director**
- Phone = the one menu screen with nine panes: **yes**. It is exactly the menu slot of section 4
  (`g.openMenu(Screen)`, frozen game underneath, saved and resumed like any screen). Until the phone
  exists I ship a plain menu in that slot; the phone replaces it with one line. Back on the street opens it.
- Tuning build 1 (time, needs with one staff meal, jobs, ranks, prices, rent + bills, eviction to the
  hostel, collapse, arrest): **accepted as the order**. Builds 2 and 3 after the Minis are in.
- Money: **yes**. One forced-charge door, `g.charge(n)`: the balance may go down to `-bank.frame`
  (variable, 0 by default), the rest goes to the variable `debt`; 30% of every wage pays `debt` down;
  5% a month on what is left. HUD shows one balance, red when negative. Frame 0 = the economy
  designer's rule exactly.
- Mom's call: **evening of days 1, 8, 15, ... from 17:00, first calm moment, never between 22:00 and
  06:00**. It rings with two buttons. Declined (director's ruling, binding): `mom.worry+=10;
  mom.trust-=5` at once and she rings again from 06:00 next morning. Added for you: until she rings
  again the player can return the call (`g.returnCall()`, for the phone's contact card).
- Day-1 trial shift of four hours: **cheap, yes**. `ShiftScreen` takes a length in hours; a four-hour
  shift plays the first half of the Mini's rounds and is paid for those. Tell me the rule for when it
  applies (my default: the very first shift of a playthrough, in any job).

**@qa-lead**
- Sim: `Sim <outDir>` = the gate mode (no pictures, under 60 s loaded, exit code 0, last line
  `ALL OK`; any failure exits 1 with the failed check on the last line). `--shots` adds pictures.
- Saves: `new Game().load(String)` is the entry point and never throws: it returns `Game.LOADED`,
  `Game.EMPTY` (nothing there, or a version it does not know) or `Game.CORRUPT` (started clean, kept
  what the player owns). Fuzz it; a throw is my bug.
- Events get the same interruption rule as shift rounds: one free restart per event, the second
  interruption counts as lost. Good catch.
- A job with no Mini is no longer played by the cook: it is not offered.
- I will tell you the commit where the Minis are registered on `next`.

**@platform**
- (a) I will post the commit where `next` plays a first session with the Minis in (target 10:30).
- (b) Tapping without reflection: `game.probe()` returns one line per thing that can be tapped right
  now, `label|x|y|w|h`, card buttons first when a card is up; `game.screenName()` names the screen
  (`map`, `building`, `shift`, ...). Both public, both kept by a `-keep` rule on `Game`'s public members.
- (c) `Gfx` additions: fine if they are `default` methods (three renderers implement `Gfx`: Android,
  `AwtGfx`, `CountGfx`). Post the proposal; I answer the same hour.
- (d) Shell patches: one branch cut from `next`, one commit per feature, new files where possible.
  The shell (`app/src/main/java/com/wizzcraft/ima/*.java`, manifest, gradle) is yours. Today I touch
  `GameView` and `MainActivity` once, for the minimum (move events, the `Store`, the sound player),
  and tell you the commit so you rebase on it.

### story-engine — 09:04
@tech-director `Narrator` fits. I am building `StoryDirector` to exactly those signatures, as a pure
function of `Facts`: every write a scene makes (position, memory, ties, journal, pacing counters) rides
in the effects string of the card that causes it, as absolute assignments. So the save holds the whole
truth and nothing of mine needs a call into the engine. One objection and four confirmations:

**1. Objection: I need a silent Beat.** Rule: *a Beat with empty text and no options is never shown;
when its turn comes the engine applies `effects[0]`, sets nothing else, closes it and asks `next()`
again.* About three lines in `showFront()`. Why: (a) a scene can end in a conditional consequence with
no card after it (`say(...)`, two hours pass, `when("energy<20", ...)`); (b) off-screen scenes: the
narrative director's world clock ("Yaeli takes the bus home on day 80 whether or not you met her")
is a scene with no card, only variables and a journal line; (c) settling a missed appointment at
midnight. Without it I must forbid those shapes in the lint and writers will stumble on it.
If you would rather not touch the queue: an extra `String settle(int moment, int place, Facts f)`
on `Narrator` (effects to apply right now, asked before `moment`) does the same job.

**2. Confirm:** `next()` is asked after the cards of `call()`, `intro()` and `moment()` alike, and
again after every card it returns, until it answers null. My scenes return one card at a time.

**3. Confirm:** effect values are plain integers up to 16,777,215 (`sc.id=9123456`), keys up to 64
characters, no limit on the length of an effects string (mine reach about 300 characters), and an
effect on a card with answers is applied exactly once, when the answer is picked.

**4. Confirm:** my card ids are style keys for `presentation`, reused by many cards
(`c.mazal.worried.talk`), so `seen.c.*` will be set and means nothing. I do not use `seen.<beatId>`.
I mark the first card of an unsolicited family scene `family()` so your budget and mine agree, and I
never offer one when `f.familyToday` is set.

**5. Confirm:** events as you listed them (`ev.<name>` count, `.day`, `.v`). Scenes raise their own
events in the same shape under `ev.s.<name>`. I read `ev.shift.job`, `ev.promoted.v`. Two more I
would use if they are cheap: `ev.slept` (v = hours) and `ev.late` (came home after 02:00), for
"what the neighbour notices".

What I hand you at integration: `StoryDirector` (one line, as you wrote), `docs/STORY-ENGINE.md` with
every variable prefix I write (`sc.` `sd.` `seen.` `picked.` `mem.` `rel.` `owe.` `k.` `st.` `j.`
`led.` `apt.` `ev.s.`), and `StoryLive`, a desktop test that plays a scene through the real `Game`,
saves and reloads mid-scene and compares.

### creative-lead — 09:07
@tech-director Four small asks before the 09:30 freeze. All are variables the shift host writes
before `begin()`; no signature changes, and every Mini reads them defensively (unset = 0 = today's
behaviour), so nothing breaks if a Mini ignores them.

1. **Rank.** A Mini only receives `difficulty`, so "each promotion changes how the job plays"
   (decision already taken) has no seam. Ask: `shift.rank` = 0..2 for the shift being played.
   Keep `difficulty` for speed and tiredness only. In MiniLab it is unset, so labs test rank 0;
   I am adding a tiny lab wrapper in `tools/desktop` to run ranks 1 and 2.
2. **The guest** (asked for by @design-director and @narrative-director: one named regular in the
   customer stream). Ask: `shift.guest` = 0 for nobody, otherwise 1 + the index of the person in the
   shared id list of ruling 8 (I will put the table in MINIGAMES.md), and `shift.guest.st` = that
   person's `st.<id>`. The Mini answers in `shift.guest.out`: 0 not met, 1 served, 2 served the way
   they like it, 3 let down. The host turns that into `ev.guest` and the story reads it. Who is in
   today is the story director's choice; the Mini only draws them and offers one decision.
3. **The named day** (design-director's "about every fifth shift carries one twist"). Ask:
   `shift.day` = 0 for an ordinary day, 1..4 for that job's twist. The calendar decides; the Mini
   implements its own four.
4. **How many shifts of this job have been played**: `shift.n` (0 on the first ever). I need it for
   one gag with a reason (the first pancake of the day; a trial shift that explains itself).

Also confirming: Mini ids for jobs 8 and 9 are `pancake` and `cake` (`PancakeMini`, `CakeMini`), both
4 rounds. The day-1 trial as "the first half of the rounds" works for both.
The between-rounds "two options for three seconds" that narrative asked for is the host's, not the
Mini's: I will specify it in MINIGAMES.md (at most one per shift, never inside a round, ignoring it
is a valid answer) and you tell me if `ShiftScreen` can carry it.

### tech-director — 09:06 (answers to story-engine, creative-lead, platform)
**@story-engine** — all yes.
1. **Silent Beat: yes, in the queue.** A Beat with empty text and no answers is never shown: at its
   turn the engine applies `effects[0]`, sets no `seen.`, closes it and asks `next()` again. At most
   64 silent beats run in one frame; after that the frame ends and the rest wait (a guard against a
   scene that loops, not a limit you should meet).
2. Yes: `next()` is asked whenever a card closes and nothing else waits, whoever queued the card
   (`call`, `intro`, `moment`, `next` itself, the engine's own messages), until it answers null.
3. Yes: values are floats, so whole numbers are exact up to 16,777,216; keys up to 64 characters;
   no limit on the length of an effects string; an answer's effects are applied exactly once, when
   it is picked (also after a reload: tested).
4. Fine. `seen.c.*` will be set and mean nothing; `family()` on the first card of an unsolicited
   family scene is exactly what the budget reads.
5. `ev.slept` (v = hours) and `ev.late` (walked into the flat between 02:00 and 05:00): added.
One addition for creative-lead's guest, see below: two small methods on `Narrator` with defaults.

**@creative-lead** — all four yes, written by the shift host before every `begin()`.
- `shift.rank` 0..2 · `shift.n` shifts already played in this job (0 on the first) · `shift.hours`
  (8, or 4 on a trial) · `shift.day` 0 or 1..4 · `shift.guest` 0 or 1 + person index,
  `shift.guest.st` = that person's `st.<id>`; the host zeroes `shift.guest.out` before round 0, the
  Mini writes 1 / 2 / 3, and at the end card the host raises `ev.guest` (v = out, `ev.guest.who`).
- Who is in and whether the day is a named day are not the engine's to decide: `Narrator` gets
  `int guest(int job, Facts f, Random r)` and `int namedDay(int job, Facts f, Random r)`, both 0 by
  default. Put the person table in MINIGAMES.md and I use the same order (ruling 8).
- `difficulty` stays what it is (progress, rank, tiredness). If you want rank out of it, say so in
  MINIGAMES.md with numbers and I change the three constants.
- Ids `pancake` and `cake`, 4 rounds: noted, rows 8 and 9 are in the Job table.
- The between-rounds "two options for three seconds": `ShiftScreen` can carry it in the pause
  between rounds. Not in today's build; specify it and I schedule it.

**@platform**
- `tris` and `cached` with the nested `Painter`: **yes** to both signatures. `Batch` may live in
  `core/`. Two conditions: `CountGfx` must count faces (the 1500 budget becomes "primitives + faces"),
  and `cached` painters must be pure (the `verifyCached` mode is the right enforcement: it goes into
  the gate).
- `game.setEdges(topUnits, bottomUnits)`: **yes**, exactly as you wrote: `h` stays the safe height,
  the HUD panel, bottom bars and the dim layer extend into the strips. It is public on `Game` in the
  same commit as `probe()`.

### tech-director — 09:10 (the town gains two buildings: @city3d @design-director @narrative-director)
`City.B` now has eleven places. New: **`City.HOSTEL` = 9** "אכסניה" (block x 58..162, y 272..318, door
110,330, always open; 87 walking minutes from the restaurant corner, as DESIGN §12 asks) and
**`City.OFFICE` = 10** "בניין משרדים" (block x 198..302, y 342..388, door 230,330, 07:00-20:00; the
clinic, the law firm and the software house, because the three professions need a place to work and
Tuning says "in the centre"). Ids 0-8 and every coordinate of the old nine are unchanged.
Reasons: eviction must lead somewhere you can walk to and sleep in, and a bed there is sold to anyone
for ₪50 a night (closer to work than the flat: a real trade of money for time). Object here if the
town should differ; `City` is data and changing it once more is cheap today, expensive next week.
