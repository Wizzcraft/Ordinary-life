# story-engine — position paper (v1, 08:55)

Owns: `Scene`, the script DSL, triggers, `StoryDirector` (scheduler and pacing table), people and
relationships as data, "who knows what", journal / messages / ledger data, placeholders, and the
writer tools `StoryLint`, `StorySim`, `ScenePreview`. New files only, beside the lead's engine.

## What I found
- v1 is three private formats: `Story` (a table of lambdas, max two cards a morning), `MomLines`
  (weighted pick, `[זכר/נקבה]` markup), `Lines` (`[זכר|נקבה]` markup). 230 good beats, none can see the
  others, every beat is one card, nothing happens at a place or an hour, and a lambda cannot be linted.
- The engine can already carry scenes without a change to its screen: a `Beat` answer applies an
  effects string, so a scene keeps its position in two variables and each answer writes the next
  position. The dialog queue is saved, so closing the app mid-scene resumes on the same card.
- The critic's question 3 (boxes named 30 times in calls, 0 in the story) is a tooling failure.
  It becomes a lint gate: nothing may be planted without a payoff, and no payoff without a plant.

## The three changes I am making
1. **One scene format for all six writers**, compiled to the existing `Beat`. Conditions are short
   strings (`"money<100 & day>=15 & !seen.mazal.loan"`), not lambdas, so the tools can read them.
2. **A director instead of a morning stack.** The engine asks "is there a scene for now?" at ten
   moments; the director answers by priority and by one pacing table, from the save alone.
3. **Memory as data**: ties per person (level and stage), who knows what, remembered choices, a ledger
   of claims / promises / requests and of planted gags, a journal and message threads.

## The format, as a writer writes it (v1 text, ported)
```java
final class ArcMazal extends Script {
    static void register(StoryBook b) {
        b.arc("mazal", "מזל, שוקו והסיר");

        b.scene("mazal.shoko_gone").arc("mazal").beat()
            .at(EVENING, HOME).fromDay(53).after("mazal.cat_sitting", 20)
            .play(
                ask(MAZAL, WORRIED, "שוקו נעלם. שלושה ימים. הוא אף פעם לא נעלם יותר מיומיים, יש לו כבוד. [תעזור/תעזרי] לי לחפש?",
                    opt("יוצאים לחפש",
                        say(MAZAL, "[תקרא/תקראי] לו בקול רך. בקול רגיל הוא נעלב."),
                        hours(2), fx("energy-=10; fit+=1"), closer(MAZAL, 2),
                        remember("searched_shoko"), plant("shoko.thanks"),
                        journal("חיפשתי את שוקו עם מזל עד חצות.")),
                    opt("אין לי זמן",
                        say(MAZAL, HURT, "בסדר. אני אחפש לבד. עם פנס. ועם רגשות אשם, שלך."),
                        apart(MAZAL, 1))));

        b.scene("mazal.shoko_back").arc("mazal").beat()
            .at(MORNING).after("mazal.shoko_gone", 3)
            .play(
                say(MAZAL, "שוקו חזר. הוא היה אצל שמעון שלושה ימים. אף אחד מהם לא מוכן לדבר על זה."),
                when("remember.searched_shoko",
                    say(MAZAL, WARM, "הוא השאיר לך משהו על השטיח. אצלו זה הכי גבוה שיש."),
                    payoff("shoko.thanks"), tell(MAZAL, "kind_to_cat"))
                .otherwise(
                    think("שוקו מסתכל עליי מהמדרגות. הוא זוכר מי חיפש ומי לא.")));
    }
}
```
Short form for a one-card call, as the comedian wrote them: `opt("אין מרפסת", "אין מרפסת. אז מה יש? חלון?", claimOff("balcony"))`.
Cards: `say` talk, `call` phone, `text` message (kept in the thread), `letter`, `think`, `narrate`.
Text: `[זכר/נקבה]` or `[זכר|נקבה]` anywhere, in answers too. Placeholders: `{name} {job} {rank} {boss}
{money} {rent} {debt} {day} {daysleft} {lie}` and `{n:var}` / `{nis:var}` for any number.

## Triggers (all combine)
- **Moment** (the engine asks): `MORNING`, `WAKE`, `STREET`, `ARRIVE place`, `LEAVE place`, `INSIDE
  place`, `AFTER_SHIFT`, `EVENING` (home), `BEDTIME`, `PHONE` (any calm minute).
- **Time**: `days(a,b)`, `fromDay`, `onDay`, `phase(n)`, `hours(a,b)` (wraps midnight), `every(7)`.
- **Events**: shift done (job, result), hired, promoted, rent paid / missed, arrested, got away,
  robbed, collapsed, bought, ate out, casino won / lost, alley deal, studied; a scene can raise its own.
  "The first time of anything" is a once-only scene on that event.
- **State**: any variable; built-ins `money food water energy fit cha edu heat record job rank shifts
  rent.debt rent.missed day hour phase`; `tie.mazal>=friend`, `knows.mazal.<fact>`, `seen.<scene>`,
  `picked.<scene>=2`, `remember.<flag>`, `ledger.<line>=open`.
- **Delay**: `after("scene", days)`, `afterEvent("arrest", days)`. **Chance**: `chance(0.3)`, fixed per
  save and day. **Frequency**: once (default), `repeat(coolDownDays)`.
- **Class**: `spine()` > `beat()` (arc) > `colour()`; `call()` for the weekly call; `heavy()`,
  `family()`, `urgent()`. Cost: `hours(n)` inside the script.

## What I need
- @narrative-director, @comedy-head: read the example as your writers would. Where would they stumble?
  Specific asks in `threads/scene-format.md` (answer shape, moods, limits, gag ledger).
- @design-director: your `people-as-systems` data. Place, hour window, tie gate and hour cost are all
  cheap (they are in the example). A favour ledger and a discretion trait are data on `Person`;
  "today" lines are an appointment table. Send the fields, I will fit them.
- @presentation: each card arrives with a style key in the `Beat` id (`who.mood.kind.place`); I give
  you `StoryCard.of(id)`. Tell me what else a card must carry.

## What I offer
`StoryLint` (ids, both genders, lengths, no Latin, every variable written is read and the reverse,
reachability, dead ends, orphan plants and payoffs), `StorySim` (thousands of simulated years by
player policy: scenes per day by phase, longest silence, arcs finished, never seen, blocked by
pacing), `ScenePreview` (any scene as text and as PNG cards), `docs/WRITING.md`.
