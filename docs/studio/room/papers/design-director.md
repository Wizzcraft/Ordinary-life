# design-director — position paper (v1, 08:55)

Owns `docs/VISION.md` (the design bible v2) and the feature map. Read so far: TEAM, DESIGN + Tuning,
UX, STORE, STORY + Story.java, MomLines, the engine on `next`, the first-session screenshots.

## What I found
The parts are good. The game between them is thin. A day today is: tap work, wait, play 30 seconds,
tap the market, tap home, sleep. The economy paper says it plainly: "sleep, travel, one shift, shop,
travel, sleep; about 2 free hours". The story arrives as cards on waking, whatever the player did.
A building is a menu with two verbs and nobody in it.

**The biggest weakness: the game has no middle.** Days 1-10 teach survival. Days 96-120 are a
countdown. In between the model's steady worker never misses a rent and saves ₪300-700 a month, so
survival is solved by day 30, and the one unsolved question (mom) is answered once a week by one tap
out of three. There is nothing the player is *trying to do* on day 47. The title mechanic, the lie, is
three flags that pay off 3 real hours later. No day has an intention, and no person needs the player
at a place and a time.

## The three changes that matter most
1. **A lie is a promise: the truth ledger is the quest log.** Every claim made to mom is one line in a
   journal with two columns, "מה קרה" beside "מה אמא יודעת". Each line can be made true (rank, flat,
   savings: the game's long goals, chosen by the player's own mouth), confessed (costs worry now), or
   covered by a person who owes you. Claims are tested three times before day 121: proof requests on
   the phone, one guest from home around day 60, and the countdown. Honesty has a real price that is
   not a sermon: a worried mom steps *into* the city (phones the landlord, recruits the neighbour,
   sends boxes that must come back). Lying buys independence now and debt on day 121.
2. **People are places and hours.** Story leaves the morning mail and moves into town. About ten
   people each have a tie (stranger / acquaintance / friend / family), a favour ledger in both
   directions, a discretion trait (who repeats what to whom), and one or two things only they can do
   for you. The phone invites; the scene happens where the person is and costs hours. Each day gets
   at most one "today" line (someone expects you between two hours), competing with the shift, the
   shop and sleep. Ties change prices, shifts, rescue after failure, and who covers at the visit.
3. **Five phases, each adds a verb and removes a comfort; the phone is the hub; waiting is sitting
   somewhere.** Arrival 1-10, grind 11-35, temptation 36-65, turning 66-95, countdown 96-120, each
   with one new system and one new pressure (list in VISION §2). The phone holds calls, messages,
   bank, map, contacts, journal, settings; the world holds every scene and every job. "הרץ זמן"
   becomes a place: the bench, the roof, the bar stool, each with its own people passing.

## Decisions I am taking now (so nobody waits on me; object in the threads)
- No fourth meter. No reputation bar on the HUD. Ties show as how people greet you and as one status
  line per contact in the phone.
- Money: one balance that may go red. The bank overdraft ("מינוס", with a frame that grows with rank)
  replaces the abstract debt number for small shocks; garnish stays for fines beyond the frame.
- The player can phone mom. Calling first is a mechanic (once a week it replaces her call, on better
  terms). The last input of the game is the player dialling her.
- Mom's fridge is the meta screen: endings, achievements, the supporter magnet. Carries over.
- Free-play shifts with personal bests: yes, after the visit and from the title; no economy effect.
- Regulars who cross between jobs: yes, but five of them, deep, not twenty.

## What I need, by role
- `narrative-director`: (a) the cast list with one "only they can" favour each and a discretion
  trait; (b) which claims mom can extract (I propose 8, concrete and checkable, see thread
  `truth-and-journal`); (c) the guest from home on about day 60: who? (d) do you accept scenes that
  happen at a place and hour instead of on waking?
- `story-engine`: can a scene declare place, hour window, tie gate and cost in hours? I will specify
  the data in thread `people-as-systems`; tell me what is cheap and what is not.
- `presentation`: the phone as one sheet with seven apps; I need the journal's two-column row and
  the "today" line on the lock screen. Thread `phone-hub`.
- `creative-lead`: a slot in every job Mini for a named regular (portrait, a preference to learn, a
  line). Which Minis can take it without a rewrite? Rank modes: I need one sentence per rank per job.
- `comedy-head`: the bank as an SMS-only voice; the box-return gag as a mechanic (boxes owed).
- `city3d`: the roof and the bench as sit-down views of the town (camera low, time passing).
- `critic`: attack change 1 first. If the ledger is not fun on day 47, nothing else saves the middle.
- `economy designer`: the overdraft frame and interest; I will run the model on a copy and post numbers.
- `lead-engineer`: nothing now. The feature map (VISION §16) is your hand-off, with tests.

## What I offer
A reason table (every rule and its in-world reason), the phase plan with what is new in each, the
first ten minutes minute by minute, and a CUT list. Ask me "is X in or out" and get a yes or no.
