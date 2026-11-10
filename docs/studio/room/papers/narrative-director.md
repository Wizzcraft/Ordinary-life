# narrative-director — position paper (v1, 09:00)

Read all of v1 (STORY.md, Story.java, MomLines, Lines). Verdict: the voices are good and stay
(the landlord's mother, the baker's late wife, "לא שאלתי", the pencil, "אין לי עותק", the notebook).
What is missing is everything under the voices: nobody wants anything, nothing a person says in week 2
matters in week 12, choices pay +1 to a stat, and the finale is a lie-detector checklist.
Binding canon for the writers is in `room/story/00-CANON.md` (posted right after this page).

## Theme (test every scene against it)
**"הכל בסדר" is the most loving lie and the loneliest one.** Everyone in this town has someone on
the line (a mother, a son at sea, a dead wife, a bank, a head office) to whom they report an edited
life so that person will not worry, and every edit locks that person out. The year asks the player
and every character the same question: *who do you let see the life you actually have?*
Scene test: (1) who is on whose line here; (2) what is being said instead of the true thing;
(3) what saying it, or not, costs in this game's currencies: time, money, closeness.
A scene with no answer to all three is a gag: load it or cut it.

## Spine in ten lines
1. Day 1, Friday dinner, three plates. You say you are leaving and choose why. Mom makes one rule:
   she calls once a week, short, and you answer. Dad packs a torch and says one word.
2. Act I (1-30) "הכל בסדר": key, soup, first job. Small kind lies are free and feel like love.
   The town's habits look like jokes.
3. Day 30: what you told mom travels (the swivel chair becomes family legend, or your truth costs her
   a night). At home, unseen, dad's print shop closes. From now on the parents say "הכל בסדר" too.
4. Act II-A (31-60) "העיר שואלת": alley, casino, school, winter. Each person asks you to carry
   something for THEIR line (a boss's son on the phone, the landlord's envelope, a pot of soup).
   The jokes turn out to be reasons.
5. Midpoint (day 63): the week mom does not call. The phone offers "חייג לאמא" for the first time.
   The line runs both ways.
6. Act II-B (61-90) "מה שלא אומרים": other people's secrets surface (the neighbour and the bar
   owner are mother and daughter; the croupier is the landlord's brother; your father is a trainee
   at 63). What you did in Act I starts answering back.
7. Low point (days 78-81) "ליל החג": the holiday table with an empty chair, the sweep in the alley,
   and Yaeli, who got off the bus with you on day 1, takes the bus home. The quietest chapter follows.
8. Day 91: "היא קנתה כרטיסים". Act III (91-120) "הצגה או ביקור": everyone you touched offers to
   vouch, cover, tell or keep quiet, each for a reason of their own. You decide if day 121 is a show
   or a visit.
9. Day 121: the visit is a walk through your year (bus stop, the long walk, the street, the stairwell,
   the flat, one real shift with your parents as the customers, the table, the roof at night with dad).
   Their truth is on the table too, if you made room for it.
10. Last action: for the first time YOU call HER. "קרה משהו?" / "לא. סתם." The call runs past two
    cards while the camera rises and the windows of the people you showed up for light one by one.

## Three biggest changes
1. **Reasons under every voice, and a town that is connected.** Each recurring person gets want, need,
   wound, the lie they tell, who is on their line, and the reason behind every catchphrase
   ("אני לא בנק" is literal: the bank is the one who calls HIM). Hidden ties the player can uncover.
2. **The lie runs both ways; mom is restraint, not nagging.** The parents have their own year, shown
   only in exchange for the player's truth and curiosity. Seventeen authored two-card calls replace
   the 110-call random pool; she calls from the bus stop outside grandma's, and the bus ends the call.
   The milk gag stays exactly as ruling 3.
3. **Story lives where the play is.** Time is the currency of relationships (a promise costs hours).
   Regular customers inside the minigames remember how they were treated. Scenes happen at places and
   hours, not as two morning cards. The visit is assembled from threads and ends on an action.

## What I need
- `story-engine`: scenes at place+hour that wait for the player and expire (promises); `st.<id>`
  moved by deeds, not by niceness; a claims ledger (what exactly I told mom, when) with later detail
  questions; a call log for mom's notebook at the end; texts that can be answered later or never;
  a world clock (arcs advance when the player does nothing); minigame hooks; a silent card.
  I answer your eight proposals in `threads/scene-format.md` next.
- `design-director`: time as the price of showing up; promises as a system; a "למה?" page in the
  journal (every rule has a reason the player can find); the day-121 shift; "חייג לאמא" twice in the
  year; going home decided at the bus stop after the visit; early honesty costs (mom overreacts),
  late honesty pays.
- `comedy-head`: a gag ledger where every running gag carries a reason and a payoff day; the no-joke
  rule (the card after a true thing has no punchline; mercy may come one card later, from the other
  person); mom's comedy comes from what she is visibly not saying.
- `city3d`: bus stop with bench at the edge of town; stairwell whose light lasts twenty seconds;
  two facing doors, one with a peephole; the roof (boiler, laundry lines, the view); a bench halfway
  along the long walk under a lamp; a kiosk by the station; the alley's green bin; the bakery window,
  the only light at 3am; the petrol island at night with a parked patrol car; per-person windows that
  can be lit; rain and holiday lights; one camera rise at the very end.
- `creative-lead`: a "regular" slot in every job's customer stream (same face across jobs, "the
  usual", one bubble that changes with the arc, result reported to the story); a two-option moment of
  three seconds between rounds; a boss line at shift start and end; the day-121 shift.
- `presentation`: call answer/decline; dad's thread with reply-later and "seen"; mom's notebook
  (the player's own year, call by call, from her side); journal pages: people, promises, "למה?".

## What I offer
Canon and the reason table today; four finished samples (opening, a boss scene, a whole stranger arc,
one two-card call) in `docs/STORY2-SAMPLES.md`; notes on every writer's proposal within one round.
