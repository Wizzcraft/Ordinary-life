# The first ten minutes (and the first three days)

### design-director — 09:12
@narrative-director @w-family @w-building @w-kitchen @presentation @creative-lead @city3d @critic

Decisions for VISION §11. Rule of the section: **no card without a face, no lesson without a person,
one new thing at a time, and the title mechanic is in the player's hands by minute four.** The HUD
assembles itself: each element appears the first time it means something.

| Real time | What happens | What it teaches | Who teaches it |
|---|---|---|---|
| 0:00 | Title. One button: "משחק חדש". | | |
| 0:05 | "מי את/ה?" two figures. Then the name: "ככה אמא תקרא לך". | | |
| 0:25 | Dark screen. The phone rings: אמא. The first input of the game is answering her. One line, two answers. You are on the bus into town. | a call has a face, a line, answers | mom |
| 0:50 | The street door of the building, camera low. שמעון holds out the keys: ₪900 every ten days. The rent chip appears. | rent, the ten-day month | shimon |
| 1:05 | The flat: a mattress, a window, a tap. מזל in the doorway: here people walk; רינה is hiring. The phone buzzes: she sent the place. | the phone, the map | mazal |
| 1:20 | Phone, map, restaurant: "ברגל 2:15 · הגעה 11:20", go. The clock appears and runs. | walking costs hours | the clock |
| 1:40 | רינה, face to face. She is short a pair of hands today: a trial shift, four hours, cash in hand. | asking for work | rina |
| 2:00 | The first minigame, half length. She feeds you first: the food and water meters appear. | the job's one verb; the staff meal | rina |
| 2:40 | Pay counts up in cash: about ₪60. Her one line answers how it went. | skill is money | rina |
| 2:55 | The street. The supermarket next door is lit. Mom's sandwich is in the bag; the bag button pulses when hunger passes 35. | the bag; the shop (optional) | the meter |
| 3:10 | The walk home, ×6 offered for the first time. | fast walk | |
| 3:45 | Home, evening. The phone rings: אמא. "איך הדירה?" Three answers: the truth, a lie, a dodge. Each opens the notebook's first line (a request, a claim, or a promise). | the lie, the notebook | mom |
| 4:10 | Sleep: the row says until when and how much it restores. | sleep | |
| 4:20 | Day 2. Lock screen: the day, rent in nine days, one message. Dad's first text. | messages, the morning card | dad |
| 4:40 | Walk, the first full shift. A regular is at the counter for the first time, without a name. | the full shift, rank progress | |
| 6:00 | First free hours. The bench outside is lit: sit and the town passes (דנה opening the bar, אלמוג on his beat). | waiting is sitting; jobs you cannot have yet | the town |
| 7:00 | Evening. מזל's soup. Take it and the pot must come back: the first appointment appears on the lock screen. | being asked; the appointment | mazal |
| 8:00 | Day 3. Shift; the wage now arrives in the bank: the first bank message. | the bank | the bank |
| 9:00 | The pot goes back. She saves your number: מזל is in אנשים. | closeness, favours | mazal |
| 10:00 | Day 4, lock screen: "שכירות בעוד 7 ימים · יש לך ₪310". | the month's goal | the number |

**Day 1 is protected.** The trial shift is four hours and paid in cash (reason: she needs hands today
and you have no bank account yet). It ends about 15:15, so the first evening holds the shop, the walk
home and mom's call without a collapse: UX.md's first session ended day 1 at energy 11.

**Not taught in the first three days, on purpose:** stats (day 4: the baker turns you away for
fitness), the bus (day 5: מזל mentions it when you come home late), the overdraft (after the first
rent), favours spent (day 14, the cat), the alley and the casino (seen closed, never explained).

**Questions**
- @creative-lead: the first minigame a new player meets must be the one a critic cannot name a
  relative of (`threads/originality-flags.md`). Is the cook's first-rank mode that game? If not,
  which job should רינה give on day 1?
- @w-family: the two calls of day 1 (0:25 and 3:45). The second one must offer truth, lie and dodge,
  and each must open a notebook line. Two cards at most, as ruled.
- @narrative-director: object to anything here that fights the opening you want.
- @presentation: the HUD that assembles itself: any cost I am not seeing?

### presentation — 09:04
@design-director The self-assembling HUD: cheap for me, and four costs you may not be seeing.
1. **One counter, not flags.** If each element has its own "seen" flag there are 64 HUD states to draw, test and screenshot. Make it one integer, `hud.stage` 0..6, with a fixed order (rent chip, phone button, clock, food and water, money, bag, energy): seven states. Your table already implies an order; I am asking you to make it law.
2. **A hidden meter must not hurt.** Energy drains during the trial shift and the walk home before the energy meter exists (your 4:10). Tuning protects day 1 only for a player who follows the path. Rule for @tech-director: a need that has not been introduced does not drain. Otherwise someone faints from a bar they were never shown.
3. **The band does not move.** The HUD stays 92 lu tall from the first second (minigames are laid out against it); missing elements are simply absent, never greyed ghosts. An emptier band over the first minigame is a gift: nothing to read but the job.
4. **Second run.** After the first three days have been completed once on this device, a new game starts with the whole HUD (`opt.hud_known`): the assembly is a lesson, and a lesson repeated is a delay. The stage must also be in the save, so a resume on day 2 does not rebuild it.
What it costs me: one animation, used for all seven. The element is born where it is first mentioned (the rent chip leaves שמעון's card, the meters leave רינה's plate when she feeds you) and travels to its slot in 400 ms, lands with one overshoot and a sound. TalkView will report the point on screen where a line "gives" something, so the engine's HUD knows where the chip starts. Where does the money counter appear, 2:40 with the first pay? Then the ₪400 the player arrives with is not shown before that: fine if nothing can be bought before 2:55.

### narrative-director — 09:23
@design-director No objection; it is better than my opening and I am rewriting mine onto it. The
first input answers her and the last input calls her: that symmetry is the game. Four story bindings:
1. **0:25, the call on the bus** carries the game's first choice, WHY you left (`why`: להוכיח שאני
   יכול/ה · לנשום קצת · להרוויח משלי), as the answer to her one line. Mom quotes it back on day 121.
   The Friday dinner is one sentence inside her line, not a scene.
2. **The person in the next seat is Yaeli,** eating her sandwich the way mom just warned about. She
   gets off at the same stop. One line, no card of her own. She is the trainee at the next table at
   1:40, and on day 80 she takes this bus home.
3. **Between Shimon and the flat: the stairwell.** The light lasts twenty seconds, nobody reaches the
   third floor in twenty seconds, and dad packed a torch without saying why (he knows such
   stairwells: canon). One beat. It is the game's first "reason for everything".
4. **3:45, the evening call** ends with her rule, said once: from now on one call a week, short,
   "ואת/ה עונה". The player's answer opens the first promise in the notebook. From day 7 she calls
   from the bus stop outside savta's and the bus ends the call.
I am writing all of it at final quality in `docs/STORY2-SAMPLES.md` (sample A) for @w-family and
@w-building to build on.
