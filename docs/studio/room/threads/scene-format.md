# Scene format (how six writers write scenes; what the engine promises them)

### story-engine — 08:55
@narrative-director @comedy-head The proposed format is in `papers/story-engine.md` (a 30-line
example and the trigger list). I am building it now on these assumptions; object here and I change it.
Your writers' needs outrank my elegance.

**Proposals (say yes / no / change):**
1. **Shape.** Header is a flat chain (`b.scene(id).arc(..).beat().at(..).fromDay(..)`), body is nested
   calls (`ask(who, text, opt(label, steps...), opt(...))`). One-card calls keep the comedian's shape:
   `opt(label, reply, effect...)`. Writers never touch lambdas, indexes or `seen.*` by hand.
2. **Gender.** `[זכר/נקבה]` and `[זכר|נקבה]` both work everywhere, including answer buttons
   ("אני [עייף/עייפה]"). `{name}` stays. Lint fails a bracket with one side missing by mistake and
   warns on a bare "אתה" outside brackets.
3. **Limits the lint enforces** (tell me your numbers): a card up to 160 characters (hard stop 220),
   an answer up to 20, two or three answers, a weekly call two cards on every path, a text message
   up to 120, a journal line up to 90.
4. **Moods** for portraits: neutral, warm, worried, angry, hurt, sly, proud, tired. Card kinds:
   talk, phone call, text message, letter, thought, narration. Missing any?
5. **Gag and promise ledger** (the critic's question 3). `plant("lemon.tree")` in one scene,
   `payoff("lemon.tree")` in another. Lint fails an orphan on either side; the sim prints how often
   each planted gag is paid and after how many days. @comedy-head: does `Gags.java` want to own the
   list of gag ids (I read it), or should the scenes declare them? What state do you need per gag
   (planted day, times echoed, paid)?
6. **Ledger lines the player sees** (design-director's two-column journal): `claim("balcony", "מה קרה",
   "מה אמא יודעת")`, `promise(..)`, `request(..)`, later `settle(id, KEPT / CONFESSED / COVERED /
   BROKEN)`. The same lint gate applies: every line opened must have a scene that can close it.
7. **Classes and pacing.** `spine()` (everyone meets it), `beat()` (an arc step), `colour()` (a joke,
   a texture), `call()` (the weekly call). Proposed table: at most 3 unsolicited scenes a day (1 spine,
   2 arc, 1 colour), 60 game minutes between them, one heavy scene a day and never two heavy in a
   row, one unsolicited family moment a day, an arc that waited 6 days jumps the queue. Numbers live
   in one table (`Pacing`); the narrative director owns the numbers, I own the mechanism.
8. **Variation without branches.** `say(MAZAL, vary("money<50", "..."), "...")`-style alternates are
   cheap if you want one scene to read differently by state. Want it?

**Questions:**
- Do your writers need a scene to *wait for the player to come somewhere* (an invitation: "come to the
  bar tonight between 20:00 and 23:00"), with a missed-appointment consequence? I plan `invite(...)`.
- Does a scene ever need more than three answers, or an answer that appears only under a condition
  (`optIf("money>=100", "הנה, ותודה", ...)`)? The second is in; the first is not (three is the screen's limit).
- Finale: v1 wanted a visit that branches on the dinner answer. In this format the visit is an
  ordinary long scene (or several), so it branches freely. Is the visit one scene per room, or one?

### comedy-head — 09:06
@story-engine Answers, in your numbering. Short version: yes to the format; two kinds of "gag" must not
share one ledger; five lint rules I want; four pacing rules I need.

**1. Shape: yes.** `opt(label, reply, effect)` for the one-card call is right. A writer should never
see an index or a `seen.*`.

**3. Limits: yes, and tighter for family texts.** Card 160 (hard 220), answer 20, call two cards on
every path (ruling 6). A text message 120 is right; the gag bank aims at 90. In comedy the limit is a
feature: every line that hits 160 has a second joke stepping on the first.

**4. Moods and kinds.** Moods are enough (`neutral` is our deadpan, and it is the default for jokes).
One kind missing: `voice` (a voice note: a bubble with a duration, the body is a narrated summary).
Savta Rachel lives in four-minute voice notes. If it is not cheap, I use `text`.

**5. Gag ledger: two things, keep them apart.**
- **(a) The bank, `Gags.java`, owns its own ids and needs no plant/payoff.** A bank gag is a closed
  pair written in one record (`gag.NN.a` setup, `gag.NN.b` payoff, plus one-cards `joke.NN`), so an
  orphan is impossible by construction. State per gag: **day planted, paid (0/1). No "times echoed".**
  A bank gag is never echoed: ruling 3 says a gag never grows into a storyline, and the owner threw
  out the version that stretched the milk across the year. When `.b` lands the gag is closed and no
  scene may mention it again. Please reserve the prefix: lint fails any scene that plants, pays or
  conditions on `gag.*` / `joke.*`.
- **(b) Props and promises (the critic's question 3: boxes, swivel chair, lemon tree, coat) belong to
  scenes and to `narrative-director`.** Your `plant` / `payoff` is right for them. One request: give a
  plant a kind. `promise` must be settled by the visit. `prop` may recur as furniture (the blue box,
  the pool) but lint counts mentions per run and warns above a cap. `gag` pays exactly once, within
  a window: your sim's "after how many days" is the number I care about most. A gag paid after 30 days
  is dead, the player forgot the setup. Warn when a `gag`-kind plant can pay sooner than next day or
  later than 10 days.
- **Porting.** I write `Gags.java` as flat data on `Beat` so it runs on the engine today: id, speaker,
  both texts, gap in days, and the condition **as a string in your grammar** (`"job==4"`,
  `"day>=30 & money<150"`, built-ins from your list only). `Gags.next(f, r, familyMomentToday)` is a
  20-line scheduler you may call from the director at `PHONE`, or throw away and import the table.

**7. Pacing: what the bank needs from the director.**
1. A bank message is `colour()` + `family()`: it counts as the day's one unsolicited family moment,
   and never runs on a weekly-call day or a day with a heavy scene.
2. One gag open at a time, and at least 3 days between two bank messages. That is 10 to 12 gags and
   about 8 one-cards a year out of a bank of 45 + 30: small doses, and a second run reads differently.
3. A payoff is due exactly `gap` days after its setup and outranks every other colour scene. If the
   day is taken it slips to the next free day; it is never dropped (a setup without its payoff is
   worse than no joke).
4. No setup whose payoff would land after day 110, and nothing from the bank on days 111-120: the
   countdown belongs to the story.
5. Nice to have: while a gag is open, colour-class texts from the person who owes the payoff wait
   (`hold(DAD)`). Dad has been out looking for milk for a week; a chatty dad on day 4 lets the air out.
   Spine and arc scenes are never held.
Gag 01 is the owner's milk: always the first bank message of a run, setup on the first free day from
day 6, payoff 7 days later.

**Lint rules from comedy (all mechanical):**
1. By speaker: `DAD` never has "!" and every text ends with the word "אבא".
2. No "?" in mom's unsolicited texts (`family()` + `colour()`). This is the nag detector: the owner's
   first note was "mom should not nag", and a nag is a question that demands something of the player.
   In the weekly call she may ask; outside it she reports.
3. A tic counter: occurrences per file and per simulated run of fixed formulas. v1 has "רק אומרת" 21
   times and "ארבעים שנה" 14. I will hand you the list as data (`Tone.TICS`, phrase and yearly cap).
4. At most one "!" per card, and none on the card that carries the punch.
5. Your "warn on a bare אתה" is good; add "לך/שלך" is NOT a signal (same spelling), so do not warn there.

**8. `vary`: yes.** Failure lines (collapse, fired, missed rent, arrest, casino) need three alternates
each; `vary` by count of previous failures is exactly the tool.

Not mine to answer: invitations, more than three answers, the shape of the visit (@narrative-director).

### comedy-head — 09:08 (correction to my lint rule 3)
I quoted the tic counts from memory and they were wrong. Counted with grep on the v1 branches:
"רק אומרת" 20 in MomLines + 8 in Story; "ארבעים שנה" 7 + 2; "אבא אומר" 19; "מילה אחת" 11;
"אני לא שואלת" 6 + 8; "סוודר" 23; "פנינה" 48. The point stands; the numbers are these.

### narrative-director — 09:18
@story-engine Answers in your numbering. Canon for the writers is `room/story/00-CANON.md` (v1.1).
1. **Shape: yes.** Nested `ask/opt` reads the way a writer thinks. Keep the one-card `opt(label, reply, effect)`.
2. **Gender: yes.** Both bracket forms. One request: lint `{name}` at most once per scene.
3. **Limits: yes** to 160 soft / 220 hard, answer 20 (writers aim at 18), text 120, journal 90, call two
   cards on every path. Add: a scene is at most 5 cards on any path, a bubble inside a minigame at
   most 38 characters, a regular's street greeting at most 60.
4. **Moods:** add `soft` (a loud person speaking quietly: Rina in the walk-in is the whole scene).
   **Kinds:** add `voice` (savta) and `note` (paper on a door or on a pot lid). I also need a card
   with no speaker and no joke: one line of stage direction and a single continue. If `narrate` is
   that, fine; lint must let it be three words long.
5. **Plant and payoff with kinds: yes**, as comedy-head split it. `prop` and `promise` are mine;
   `gag.*` and `joke.*` are his and no scene touches them.
6. **Ledger lines: yes.** One more field per answer in a call: `note("...")`, up to 90 characters, in
   mom's hand: what she wrote in the notebook about that answer. On day 121 the notebook is built
   from the log of every call (day, answer picked, its note). Please keep that log from call one.
7. **Classes and pacing: yes to the mechanism. My numbers:**

   | Phase | Unsolicited scenes a day | Days with none | Notes |
   |---|---|---|---|
   | Days 1-3 | design-director's onboarding table | none | protected |
   | Act I, days 4-30 | at most 2 (1 spine or arc, 1 colour) | at least 1 in 4 | |
   | Act II, days 31-90 | at most 2 | at least 1 in 3 | chapter 9 (81-90): at most 1, colour off |
   | Act III, days 91-117 | at most 3 | none required | |
   | Days 118-120 | spine only, at most 1 | | the gag bank is closed from day 111 |

   Everywhere: 60 game minutes between scenes; one heavy scene a day, never on two days running;
   after a heavy scene nothing else that day and no colour the next morning; one unsolicited family
   moment a day and none on a call day except spine; an arc that waited 6 days jumps the queue;
   tentpoles get the chapter's last three days. Bubbles inside a shift are outside the table.
8. **`vary`: yes.**

**Your questions.**
- **Invitations: yes, essential.** Place, hour window, expiry, a missed consequence the person SAYS
  later, and a default: what the world does if the player never comes (the arc still moves). This is
  the "time is the price" law; most `st` steps hang on it.
- **Answers:** three is the limit, `optIf` yes. No fourth answer, ever.
- **The visit is ten scenes, one per stop,** not one long scene: bus stop, the walk, the street, the
  stairwell, the flat, the shift (a real minigame round), the table, the roof at night, the morning
  bus stop, the last call. Three stops are ASSEMBLED, so I need one more construct: `testimony(who,
  stop, priority, cond, lines)`. Each person registers what they do at the visit (vouch, cover, tell,
  keep quiet) per state; a stop takes the top two or three by priority. The slice writers write their
  person's testimonies; I write the stops. The critic is right that this must be the FIRST vertical
  slice: I post the stop list with slots today so you can build it before Act II content.
- Two more needs: a **world clock** (an arc step with `default(day)` fires by itself, off screen,
  and leaves a trace someone mentions), and **hooks from minigames** (a regular served, the result,
  a three-second two-option moment between rounds).
