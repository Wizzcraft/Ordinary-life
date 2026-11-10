# Project context for new team members (written by the director, 10 Nov, 08:40)

Read this, then `docs/TEAM.md` in your worktree (the team handbook: rules, drawing API, minigame
contract, Hebrew rules). Your own brief wins where it differs from either.

## The game
"אמא על הקו" (repo name Ordinary-life): a Hebrew life-sim for Android phones, portrait, one thumb.
You move to the big city, rent a cheap flat far from everything, take jobs (each job is a minigame),
study for a profession, are tempted by a casino and by an alley with fast risky money, keep three
meters alive (food, water, energy) and pay rent every 10 days. Mom phones every week; you may lie
to her; on day 121 the parents visit and every lie is tested. A day is about 72 real seconds.

Everything is drawn and synthesized in code: no image files, no fonts, no audio files, no libraries.
The core is pure Java drawing through a small 2D `Gfx` interface, so it runs on a desktop for tests
and screenshots. The Android shell is thin. There is no Android SDK on this machine; the APK is
built by GitHub Actions.

## The owner's bar (his words, compressed)
- "Game of the year" quality. Not a minimal build. Take it to the edge of what is technically possible.
- Everything original: no assets, tunes, jokes, characters or designs from anyone else, and no cloning
  of existing games ("we do not steal games; this needs control too").
- A real-money store that gives a reason to buy but is not pay-to-win.
- Girls and women should enjoy it as much as boys and men.
- Release will be in English later; right now everything is Hebrew.
- NEW today, and these three are now pillars:
  1. "יש סיבה לכל דבר" (there is a reason for everything). Every person, price, rule and joke exists
     for a reason the player can sense or discover. The story is not only mom: customers and bosses
     are people, with wants and reasons.
  2. "תהיה חריף" (be sharp). The humour must have bite: dry, surprising, specific, with running gags
     that are set up in one week and pay off in another. Warm at heart, never bland. The owner's own
     example, now canon: mom says dad went out to get milk; a week later dad texts that he has been
     looking for milk all over the city for a week. Mom herself comes in small doses (no nagging).
  3. Real 3D feeling. The city must not look like a map seen from above. The camera comes down.
     (A specialist is rebuilding the city view as a low-camera 3D scene rendered by our own code.)

## Where things are
Repository: `/home/claude/ordinary-life`. Working branch `next` (the lead engineer is finishing the
engine there: do not touch that directory). The shipped build is on `main` (version 0.1.3, an early
sketch). You work in your own git worktree; your branch was cut from `next`.

Finished work sits on separate branches until the integration wave. Read any file with
`git show <branch>:<path>`; list what a branch added with `git diff --stat next...<branch>`.

| Branch | What it holds |
|---|---|
| `worktree-agent-a7de99fddf26bef3c` | BarMini (barman) |
| `worktree-agent-adcd10700234af0b9` | CashierMini |
| `worktree-agent-a1a600103b9990083` | GasMini (gas attendant) |
| `worktree-agent-aa1cf229f60ec306a` | WaiterMini |
| `worktree-agent-a1ad0c94787d1102a` | CookMini (reworked) |
| `worktree-agent-a141de2b26b841043` | BakerMini (rhythm) |
| `worktree-agent-a56be74398c639ba6` | DevStudyMini, DevMini (developer) |
| `worktree-agent-ae8b96b0b2d21cbf3` | DoctorStudyMini, DoctorMini |
| `worktree-agent-a38a57c3812a253d2` | FightMini (event) |
| `worktree-agent-a7ad0caa3310fafb3` | ChaseMini (police chase event) |
| `worktree-agent-a2c3cbb7ae182b160` | Cards, BlackjackMini, PokerMini |
| `worktree-agent-a537a73ad7709f147` | RouletteMini, SlotsMini |
| `worktree-agent-aac7ac99b419cea2b` | Story.java, `docs/STORY.md` (story bible: arc, cast, endings) |
| `worktree-agent-a2545166539c4427e` | MomLines.java (mom's weekly calls, remarks, toasts) |
| `worktree-agent-a0c196ba8ef3b9cae` | Lines.java (street encounters, bosses' shift lines, promotions, tips) |
| `worktree-agent-ac989af9f31fff74c` | `docs/UX.md` (audit, design system, onboarding, change list) |
| `worktree-agent-a7ca1d74d9ddc3a45` | `docs/STORE.md`, Catalog.java, Billing.java (premium currency "נחת") |
| `worktree-agent-a5b12356aca8d210e` | `docs/DESIGN.md`, Tuning.java, `tools/balance/model.py` (economy) |
| `worktree-agent-a46ae1660320ab02d` | Icons.java (55 icons, 9 building illustrations), title screen art |
| `worktree-agent-a7014298bd5b646aa` | Look.java, PersonArt.java (people, walk cycle, 14 portraits) |
| `worktree-agent-ac4ca674ca232c5a0` | Music.java (4 synthesized loops) |
| `worktree-agent-a582b794691b65080` | Synth.java, SoundPlayer (20 synthesized effects) |

Still being written (ask the director if you need them): the engine on `next`; alley trading;
lawyer; exterminator (tower defense); dishwasher; casino finishing pass; Hebrew editing pass;
`docs/MINIGAMES.md` (creative lead: specs for pancakes and cake decorating, rank modes for every
job); `docs/ORIGINALITY.md`; test runner; Android emulator lab; release engineering.

Screenshots and audio already produced are under
`/tmp/claude-0/-home-claude/afbbc77c-f63c-5589-a685-e163ec397744/scratchpad/` in folders named by
role: `bar cashier gas waiter cook baker dev doctor fight chase casino art-icons art-chars art-city
audio ux store story economy`. Look at pictures with the Read tool.

## Cast (from the story bible)
Mom and dad (dad texts, short, no exclamation marks, signs every message "אבא"); שמעון the landlord;
מזל the neighbour and her cat שוקו; bosses: רינה (restaurant), נסים (bakery), דנה (bar), גבי (night
shift at the gas station), פרץ (supermarket manager); רפאל from the alley; השוטר אלמוג. The
comedian's family: צביקה (dad's name), סבתא רחל, דודה חנה, אורנה מהבריכה and her son יואבי, פנינה
מלמטה, the blue box.

## Decisions already taken (do not reopen without a strong reason)
- One core minigame per job earns the wage; extras are optional with modest, varied rewards.
- Each promotion changes how the job plays. Jobs: cook, waiter, baker, barman, gas attendant,
  cashier, dishwasher, exterminator, pancake cook, pastry decorator; professions doctor, lawyer,
  developer.
- Premium currency can never be used in, or won from, the casino or the alley. No pack above ₪24.90.
- Money never ends the game: eviction leads to a hostel and the year always reaches the visit.
- Mom: the weekly call is two cards at most; between calls only an occasional short joke from mom
  or dad; never more than one unsolicited family moment a day. No "amount of mom" setting.
- No text baked into art; no sentence built from fragments that only work in Hebrew word order.
- Playtesters and reviewers on this project are AI personas, and the owner knows it.

## Working rules (short form of the handbook)
- Run `git rev-parse --show-toplevel` first; work only inside that worktree.
- Create new files only, unless your brief names files you own. Commit locally. Never push, never
  switch branches, never run `gh`, never install anything.
- The machine is shared by about 20 agents: do not start the full-game `Sim` unless your brief asks
  for it (it takes many minutes under load), and never kill processes you did not start.
- Put anything the director should see under the scratchpad folder named in your brief.
- Your last message is your report: branch and commit, what you made, the evidence, what you are
  unsure about, what you would do next. Facts only; say plainly what is missing.

## The studio room (read next)
`/tmp/claude-0/-home-claude/afbbc77c-f63c-5589-a685-e163ec397744/scratchpad/room/`: `BOARD.md` (how
we consult each other), `decisions.md` (the director's rulings, binding), `ROSTER.md` (who owns what
and how to message them), `papers/` and `threads/`. The owner asked for a team of veterans who consult
each other and build the game together, and gave a licence: rewrite and replace without fear, only for
the better. He also set the story ambition: the depth of the great narrative games (he named Red Dead
Redemption as the benchmark for depth, never for content).
