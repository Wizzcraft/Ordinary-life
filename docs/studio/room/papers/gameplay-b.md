# gameplay-b — position paper (v1, 09:08)

Owns `PestMini` + `PestSim` (exterminator, job 7) and the casino finishing pass (Cards, Blackjack,
Poker, Roulette, Slots + CardsCheck, CasinoCheck). Branch `worktree-agent-a4806462bae30bc45`.
This is the first version: the verdict on the exterminator draft and what I am building instead.
The casino section comes when I get there.

## What I found in the exterminator draft (measured, not felt)
The draft passes the lab (`LAB OK`, skill table 0.09 / 1.27 / 2.00, 40 s a shift) and @originality
ruled it `convention`. It still fails the brief, and its own simulation says why
(`gameplay-b/pest-draft-lab/pestsim-draft.txt`, 60 shifts per strategy, score 0..2):

| strategy | score |
|---|---|
| the author's expert build order, no slipper | 0.25 |
| slipper only, nothing built | 0.21 |
| **random cards on random spots + a decent slipper thumb** | **1.71** |
| "save for the best towers" + slipper | 1.39 |
| the bot at skill 0.9 | 1.85 |

1. **Placement is not a decision.** Random building scores within 0.14 of an expert and above
   every thought-out build order. The towers alone stop nothing (0.25). It is a whack-a-bug game
   wearing a tower-defense costume.
2. **The waves cannot be read.** The only forecast is a number ("מזיקים: 15") and a 1.4 s countdown.
3. **Too many systems for 40 seconds.** Coins, 4 towers x 3 levels, 5 health classes, per-pest damage
   multipliers, prices on cards: nothing a thumb can weigh in a 1.4 s build window. And against
   @creative-lead's creed: health bars float over every pest and coin numbers fly everywhere.
4. Good and kept: the night kitchen, the crumb trail, the cake, the pests' faces, the cat on its
   cushion, mom's slipper as the thumb's verb, the virtual board (same balance at 560 and 760), the
   per-round board snapshot that survives the app being closed.

## What I am building (the three changes)
Family: tower defense. Nearest relative: the fixed-lane, build-pad kind. Two rules it lacks:
1. **Traps hold, they do not shoot: capacity instead of damage.** A glue board holds three crawlers
   and you see them stuck on it; a cheese box holds one mouse; the blue lamp keeps moths circling;
   the cat chases one runner at a time. No health, no coins, no numbers: you count. Nothing dies
   (caught, shooed, or sent flying by the slipper), so it is never gross.
2. **You play the hand the van deals.** No shop. Each wave the van hands you one trap before the
   lights go out and one more mid-wave; you only decide WHERE (drop it on one of five floor tiles;
   same kind on the same tile stacks). Two holes feed two crumb trails that meet at one junction
   tile: the junction serves both trails but is the last line, the side tiles are safe but serve one
   side. Every hole shows who is queuing inside it before the wave (eyes, kind, count).
3. **Four waves teach four pests** (ants in file, the runner, the mouse, moths from the window) and
   the last wave brings a strutting pigeon instead of a crowned boss. The cake is twelve slices for
   the whole night: 0 lost in a wave = PERFECT, 3 lost = that wave is a MISS and ends.
Proof I owe: in `PestSim`, right tiles must beat random tiles by a wide margin at the same thumb.

## What I need
- @creative-lead: (a) object now if capacity + a dealt hand is the wrong toy; (b) rank modes, my
  proposal: rank 0 "where do they come from" (one hole per wave, dealt hand), rank 1 "what gets the
  junction" (both holes every wave, pick one of two traps), rank 2 to be agreed; I read
  `shift.rank` defensively as you assumed; (c) guest slot for a night job with nobody in the room:
  I propose the regular's runaway hamster among the mice (catch it in the box, never slipper it).
- @w-night: the exterminator's people. Hooks I am leaving: a one-line note from the client on the
  wall at the first wave (restaurant: Rina, bakery: Nissim; `PestMini.NOTES`), and the cat, which
  can be Mazal's Shoko on loan if you and @w-building want that.
- @tech-director: nothing yet; I will post branch + commit when `PestMini` is final.

## What I offer
`PestSim` as a pattern for "does the decision matter" (fixed strategies against the same thumb), and
a second opinion on any Mini's skill table.
