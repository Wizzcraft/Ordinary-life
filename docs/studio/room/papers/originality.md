# originality — position paper (v1, 09:01)

Originality and player-trust counsel. I am an AI in that role, and I am not a lawyer: I produce the
facts, the studio rulings and the questions a real lawyer needs. Owns `docs/ORIGINALITY.md`,
`tools/originality-check.sh` (release gate) and `docs/reviews/TRUST-1.md`
(branch `worktree-agent-aea6789b06a7ddb9e`). Rulings so far: `threads/originality-flags.md`.

## The three biggest risks I see so far
1. **The casino sits in the same app as a real-money store, and that decides who may play the game.**
   The store design itself is clean (נחת never enters the casino or the alley; no random items; no
   timers). The problem is the rating, not the wallet. Four playable casino games are "simulated
   gambling" to every ratings board: PEGI has rated any new game that teaches or glamorises
   traditional gambling **18** since 2020 (a single optional blackjack scene was enough for one
   adventure game); Australia has made simulated gambling a mandatory **R 18+** since September 2024.
   Google Play takes the rating from the IARC questionnaire, and a wrong answer there is a policy
   violation. So today's design means: adults only in Europe and Australia, and no teenagers in a game
   whose owner wants girls and boys to enjoy it. This is a decision for the owner, not a detail:
   keep the casino as it is and accept 18; or reshape it so the player never operates a real gambling
   game (the casino as a place and a story, with our own invented games of nerve, not blackjack,
   poker, roulette and slots); or cut it. I will bring the exact rules and options to
   @design-director in TRUST-1 today. @critic already called the casino the weakest fit to "a reason
   for everything"; the two findings point the same way.
2. **One-source designs.** The handbook lists nine famous games as craft references, and where a
   minigame followed one of them closely a reviewer will say that name in the first line. FightMini
   is `too close` to the boxing game and must be respecced. LawyerMini and AlleyMini carry one
   signature each from their nearest relative (the evidence shout; the coat with pockets) and pass
   once those are replaced. Everything else I have read so far (bar, cook, waiter, cashier, gas,
   baker, dishwasher, developer, doctor, exterminator, chase) is convention or our own. The story,
   mom's lines and the art look original on a first read; the full item table comes in the document.
3. **The name and what we can protect.** A first search found no game, app, show or book called
   "אמא על הקו" (search continues; a trademark register check must be done by a person). The English
   working titles are weak: "Ordinary Life" is already used by other small games and is too generic
   to protect. Separately, the game is written with AI assistance: how much of it the owner can own
   and enforce, and what the stores require him to disclose, are open questions for a lawyer. And one
   trust rule follows from it: our playtesters and critics are AI personas, so nothing they say may
   ever appear in a store listing or an advert as a player's or a reviewer's words.

## The rules, in ten lines (work inside these from now)
1. Mechanics and genre conventions are free. Expression is not: names, characters, drawings, tunes,
   sentences, levels, exact screen layouts. Learn how a game works; never reuse what it looks,
   sounds or reads like.
2. The pitch test: describe your design in one sentence without another title. If you cannot, it is
   one game with our skin. Change it.
3. Two of our own: at least two rules the nearest existing game does not have, which change how you play.
4. The thumbnail test: a still at 90 pixels wide must not be taken for another game.
5. No real names in anything a player or a store visitor reads: no games, studios, brands, shops,
   songs, shows, celebrities, politicians. Invent them, and make the invention a joke of ours.
6. Words: every line is written for this game. No quoting or paraphrasing of film, television,
   stand-up, song lyrics, books, memes with a known author, or advertising slogans. A common folk
   saying is fine; a comedian's bit is not.
7. Sound: every tune and effect is composed here from nothing. No melody "in the style of" a named
   piece, no famous jingle, ringtone or console sound, not even four notes of one.
8. Pictures: drawn here from nothing. No tracing, no "like the one in". No letters inside drawings.
9. Say where an idea came from. Every spec names its nearest existing work and its two differences;
   every reference a brief mentions goes into `tools/originality/blocklist.txt`.
10. Player trust: nothing random is sold; nothing sold is needed; no timers, streaks or nagging; real
    prices always visible; the casino and the alley never touch the store; no words in a listing
    that a real person did not say. When in doubt, ask me before you build it, not after.

## What I need
- @creative-lead: nearest game + two differences in every spec in MINIGAMES.md; the FightMini respec.
- @narrative-director, writers: tell me when STORY2 and scene files land; I read every one for
  borrowed setting, structure, scenes and lines (the benchmark the owner named is for depth only).
- @design-director: a decision path on the casino (TRUST-1, thread to follow).
- everyone: when a brief or a memory of yours names an existing work, tell me. That is not a
  confession; it is how the blocklist stays honest.

## What I offer
A same-hour ruling on any design, line, picture or tune: `convention`, `shared mechanic` (with
conditions), `too close` (with the change), or `copied`. Ask in `threads/originality-flags.md`.
