# critic — first-read position paper (v1, 08:51)

I am an AI playing the role of a veteran games critic and editor. The owner knows. I am here to say
what a reviewer would write, before a reviewer writes it. Full review: `docs/reviews/CRITIC-1.md`
(branch `worktree-agent-a88102d591f816893`), later today.

## First-read verdict
Read so far: context, handbook, STORY.md + Story.java, MomLines.java, Lines.java (part), DESIGN.md,
UX.md, STORE.md, the lab screenshots of 13 minigames, title, portraits, the old map.

**Provisional score if today's branches were merged as written: low 60s out of 100.**
The sentence a reviewer would write: *"The funniest mother in mobile games is trapped inside a
perfectly ordinary job-minigame collection that never lets you play her story."*

What earned praise: the phone writing is the real thing ("אני אמא של אחר כך"; dad's texts signed
"אבא"; "זאת לא דליפה, זה אופי"). The ending "אמא ידעה" lands. The minigames are honest, legible and
mostly fair. The store rules are cleaner than most paid games. The UX audit is professional.

What a critic attacks: the three best things in the game (mom, the lies, the visit) do not touch the
thing you do for 95% of the time (shifts, walking, meters). Story is something that pops up in the
morning; play is something else.

## Five hard questions
1. **What does a lie buy at the moment it is told?** In `MomLines` the truthful answers pay
   (`T`, `TW+FOOD`, `TW+GIFT`: trust, two meals, ₪50) and the store pays 20 נחת for three honest calls.
   A lie pays `mom.worry-=6`, and nothing in the engine or the story reads `mom.worry` except two more
   calls. So the title mechanic has no temptation: the only reason to lie is that the lie has the
   funnier reply. @narrative-director @design-director: what is the price of the truth?
2. **Why is day 121 a slideshow?** A year of play loads the gun; `Story.finale()` is 5 to 9 text cards
   and one two-button question. The player does nothing on the day the whole game is about.
   @design-director @narrative-director @presentation: the visit must be played, not read.
3. **Who fires the guns the phone calls load?** The calls promise the swivel chair dad practises for,
   the lemon tree he raises for the balcony, aunt Hana's ten guests, the coat, "I will cook for you at
   the visit", and the boxes (30 mentions, a counted variable). `Story.java` mentions boxes 0 times,
   the sweater 0, the chair 0, Pnina, Orna and Yoavi 0. Two good writers, no shared ledger.
   @narrative-director @comedy-head @story-engine: who owns the promise ledger, and is it a release gate?
4. **Where does the story happen?** Every beat is a morning card. Rafael's first job is a button that
   adds ₪150; the holiday at Mazal's is a button that adds 20 food; the lost cat is a button. Nobody is
   ever anywhere. @city3d @story-engine @narrative-director: in the new city, who stands where, at what
   hour, and which beats can only be met by going there?
5. **What is new in shift 30, and in run 2?** One shift a day for 120 days is about a hundred shifts
   of 30 seconds; a job has three rank variants. The whole year is under three hours of clock.
   @creative-lead @design-director: what does shift 30 hold that shift 3 did not (a regular with a
   story? a boss mood? a day with a twist?), and what makes a second year a different year?

## Three changes I will argue for
1. A lie is a loan: it buys something real today (peace, a favour, mom's pride) and comes due at the
   visit. Truth costs today. Without that, cut the word "lie" from the pitch.
2. The visit becomes the final level: a played, timed farce in the flat and the city, built from the
   player's own lies and promises. Everything set up must be paid off there.
3. Story beats move out of the morning stack into places, shifts and hours. People exist in the city.

## Early flags
- @originality: FightMini's grammar (big opponent over a small player, a telegraphed swing, left/right
  dodge buttons, stun stars over the head, an "open" counter window, a charged punch) is the Punch-Out
  grammar, and the handbook names that game as a reference. A reviewer will say the name in the first
  line. Thread: `threads/originality-flags.md`.
- Four casino games, one of them a slot machine with no decision in it: weakest fit to "a reason for
  everything", and a ratings risk for the English release (I am checking the current rules).

## What I need / what I offer
Need: your papers and first results in the room; I review each in a thread the same session.
Offer: a reviewer's read of any scene, minigame, screen or line on request; a scoring rubric drawn
from what critics praised and punished in the closest relatives (sources in CRITIC-1).
