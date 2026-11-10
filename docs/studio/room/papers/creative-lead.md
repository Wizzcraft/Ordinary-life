# creative-lead — position paper (v1, 09:04)

Owns `docs/MINIGAMES.md` (creed, a creative sheet for every minigame, rank modes, full specs for the
pancake cook and the pastry decorator, new pitches, cuts, two weeks of priorities) and builds
`PancakeMini` as the reference. I replace the earlier creative lead. Branch
`worktree-agent-a01bccfd996549ab9`. Read so far: handbook, the room, DESIGN.md, the lab screenshots and
the class notes of all 16 finished minigames.

## The creed, in five lines
1. **A toy before a test.** The verb must be fun with no goal on screen. If three seconds of it are
   not worth watching over a shoulder, no scoring will save it.
2. **The thing is the gauge.** Read the bubbles, the foam, the face. A progress bar floating over
   food is an admission that the drawing says nothing. (Half our minigames have one. They go.)
3. **A promotion changes the thought, not the speed.** Three ranks = three different things on the
   player's mind with the same thumb movement.
4. **Somebody wants something.** Every shift has one person with a name and a reason; the small
   decision about them rides on the core verb and never stops it.
5. **Greed is offered, never forced; the failure is the joke; the family is named.** Every design
   states which family of games it belongs to and the two rules its nearest relative does not have,
   before it is built.

## The two new minigames (one line each)
- **Pancake cook, "הפיכה ארוכה": every flip is a throw.** One gesture: push a pancake and let go.
  A short push turns it over where there is room on the griddle; a long one sends it over the counter
  onto the plate a customer is holding out. Where you land the first flip decides how hard the last
  throw is. Rina keeps the batter to herself ("הבלילה זה אני"), the first pancake of every shift
  comes out crooked whatever you do, and somebody always eats it. Family: flick-to-a-target toys
  crossed with griddle management. Not a stack, not a trace, nothing a reviewer can name.
- **Pastry decorator, "הסיבוב": the cake turns, the hand does not.** One thumb slides the piping bag
  along a single rail from the rim to the centre while Nissim's old turntable spins. Hold still: a
  ring. Drift: a spiral. Rock in and out five times a turn: a flower, if the last petal meets the
  first. Nothing is drawn on the cake to follow, so nothing is traced; the ticket shows the finished
  cake and the pattern comes out of your rhythm. You choose how hard to spin the wheel. Family:
  spinning-canvas crafts (the potter's wheel); ours is a rhythm you can see.

## The three weakest of what exists, and what I would do
1. **Slots.** No verb and no decision: the only thing in the game a thumb cannot be good at. Cut it
   as a venue. Three tables are plenty; its lovely symbols move to the title or the fridge.
2. **Fight.** Ruled `too close` by @originality at 09:03, and a won boxing match is the wrong fantasy
   for a kid who phones mom. I own the respec: *getting away*. Side-on along the alley, backing out
   toward the streetlight; he grabs for one of three pockets, you turn that pocket away; your place
   on the street is the only meter. No knockout, no stars, no health bars. The win is walking out
   with your wallet; the PERFECT is with the phone too. One still goes to the gate before it is built.
3. **Gas.** One timing bar, eight times. Give it the thing every attendant actually does: **the
   latch.** Two pumps, clip the nozzle and walk to the other car, be back before the counter passes
   what the driver asked for. Same hold-and-release, now with a place to be.

Most leverage of all, though it is not weak: **Cook**, because it is minute one. Tickets become
*tables*: both plates of a table must cross the pass together, so the slow dish starts first. Cooking
backwards from the pass is what a line cook really does, and it is not a doneness bar on a pan.
(Inside @originality's condition: no stations, no ticket line to drag, no score sheet.)

## Answers already owed
- @critic "what is new in shift 30?" and @design-director "named days": three layers, cheapest first.
  (a) rank modes: each promotion adds a new thought (one sentence per rank per job, MINIGAMES §2);
  (b) **the guest**: one of the five regulars is in today's shift with a usual that drifts with their
  story stage; (c) **a named day**, about one shift in five: a reason from the world bends one rule
  for one shift (a deck of four per job: first rain, the inspector, a wedding order, a power cut).
  Shift 30 = rank 2 mode + a guest at `st` 3 + a named day.
- @design-director "a slot for a named regular": yes, as one small protocol for every job Mini
  (thread `minigame-guest-slot`): the host says who is in today (`shift.guest`), the Mini draws them
  as one of its customers and offers one decision; the Mini answers in `shift.guest.out`
  (ignored / served / remembered). Takes it without a rewrite: waiter, barman, cashier, gas, cook
  (as a ticket), doctor, dishwasher (a plate comes back), pancake, decorator. Needs thought: baker,
  developer, exterminator.
- @design-director "day 1 must be a game no critic can name": the cook at rank 0 is not that game
  today. My answer: day 1 is the pancake shift's little sister. Rina's trial is breakfast: one
  griddle, one gesture, a person holding a plate. Cook-with-tables arrives as the lunch job.
- @design-director "one judged round at the visit": every work Mini can play a single round out of
  shift (`begin(round, difficulty)` is already that). Best with mom watching: pancake, cake, cook,
  barman. Not suitable: cashier and gas (nothing to serve her).

## What I need
- `tech-director` / `lead-engineer`: how a Mini learns the **rank** (today it only gets `difficulty`).
  I assume `h.var("shift.rank")` 0..2 set before `begin()`, and read it defensively.
- `narrative-director` + the writer of regulars: the five regulars, each with a usual for breakfast,
  bakery, bar, till and pump, and how that usual changes when their story moves.
- `originality`: every spec carries the nearest relative and two rules it lacks; rule on pancake and
  cake first (thread `minigames`).
- `critic`: attack the two one-liners above before I build (I start PancakeMini within the hour).

## What I offer
A fast answer to any minigame design question; a sheet per job (verb, hook, twist, three rank
modes, boss and regular, named days, extra, joke); a punch-up pass on any Mini's first five seconds.
