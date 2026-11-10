# tech-director — position paper (v1, 09:00)

Owns the engine on `next` (main tree), the architecture, the integration of everyone's work.
Audit done: every file in `core/`, the shell, the harness. The engine was run end to end once.

## The architecture in one page
- **`Game`** is the whole state and the only public class the shell talks to: `update(dt, h)`,
  `draw(Gfx)`, `down / move / up / back`, `pollSound()`, `musicTrack()`, `save() / load()`,
  `onAppPause()`. Saved state = a dozen fields (money, day, minute, needs, bag, rent, position) plus
  **one sorted map of float variables** (`var / setVar / addVar`, keys `[a-z0-9_.]`). Every feature
  keeps its long-lived state in variables; money moves only through `earn / spend / buy`.
- **Screens** (one at a time, `Game.screen`): Title → Name → **Map** (town, walking, the only place
  the clock runs by itself) ⇄ **Building** (a menu; time stands still) → **Shift** / **Venue** /
  **Sleep** → **Summary** → Building; **Event** (chase, fight) after the alley; **Store**; **End**.
  Each screen saves itself under a `kind`, so the app reopens on the same card of the same screen.
- **Messages** sit above any screen in one saved queue: `g.dialog(title, text)` and `g.play(Beat)`.
  A Beat has up to three answers; an answer applies an effects string (`"lie.job=1;mom.trust-=10"`)
  and may queue a reply. Showing a beat sets `seen.<id>`. The world waits while a message is up.
- **Minigames** are `Mini`s behind a registry (`Minis.register("waiter", WaiterMini::new)`). Three
  hosts own everything around a Mini: `ShiftScreen` (rounds 8/4/2: intro card, clock, wages = pay ×
  rank × MISS 0.4 / OK 1.0 / PERFECT 1.3, tips, sent home, XP, promotion), `EventScreen` (1 round;
  consequences in `Events` / `Crime`), `VenueScreen` (0 rounds; real money through `Host.pay`).
  A Mini sees the world only through `Host`. Jobs are rows of the `Job` table.
- **Narrative hooks** the engine calls today: `Story.intro / onNewDay / finale`, `MomLines.weekly /
  summary / toast`, `Lines.street / shiftStart / shiftEnd / promotion / tip / closed`; all receive a
  `Facts` snapshot (which carries a copy of every variable).
- **Save**: one `k=v;` string, version 3, loads v1 and v2 (migration gives stats, mom's meters and
  `hired.0`). Reloading at any frame must reproduce the save byte for byte: the harness checks it.
- **Harness** (`tools/desktop`): `Sim` (25 test groups, bots, a random-touch soak with reloads),
  `MiniLab` (one Mini), `AwtGfx` (pictures), `CountGfx` (no pictures, fast).

## The true state (measured, 08:56)
- Compiles clean with `-Xlint:all`. `Sim` passes 24 groups in 9 s on a quiet machine and **fails in
  the last one** (the soak): the harness itself sets an illegal clock (minute 1484). Never printed
  `ALL OK`. Under load it took the previous lead over four minutes.
- Present and working: variables, books, Host, the three hosts, registry, Job table (6 jobs), stats,
  Fx, sound queue, music track, beats and effects, crime skeleton, summary, store placeholder, v1/v2
  saves, resume from every screen, menus pause the clock.
- **Missing or wrong against the director's decisions** (this is my work list):
  1. Missed rent twice **ends the game** (`Game.end`). Must be eviction → hostel, never game over.
  2. An interrupted shift round is replayed **without limit** (rule: one free replay per shift, then MISS).
  3. Shift needs a fixed 55 energy (rule: shift cost + walk home + 5, predicted energy shown).
  4. No way to leave a shift early; no pause card reachable by the player.
  5. The clock runs from the first frame (rule: frozen until the first building tap on day 1).
  6. No autosave points, no backup slot, no corrupt-save recovery (the shell saves on pause only).
  7. Mom's call cannot be declined; nothing limits family moments to one a day.
  8. Nothing stops a casino or alley Mini from writing `gems`.
  9. `Mini.pause()` is documented as "return the bet" (rule: settle the decided outcome, never refund).
  10. Jobs 6-9 and the three professions have no rows; a job without a Mini silently plays the cook's.
  11. Nothing reads `Tuning.java`; `Story`, `MomLines`, `Lines` on `next` are stubs.
  12. Shell: no `ACTION_MOVE`, no `SoundPlayer`, no autosave. Handbook: no Engine section.

## Plan (in this order; `Sim` green and a commit after every step)
1. **Now → 09:20**: `Sim` to `ALL OK`, fast (pictures off by default, soak sized to the clock budget).
2. **→ 10:00**: the twelve items above, each with its checks in `Sim`.
3. **→ 10:15**: the "Engine" section of `docs/TEAM.md` = the integration surface, with four seams
   so nobody has to edit engine files:
   - **Minis**: one `Minis.register` line, one `Job` row, one `Events` case.
   - **Story**: one `Narrator` object the engine asks (`moment(...)` at every calm point: morning,
     wake, street, arrive, leave, inside, after shift, evening, bedtime, phone; `next(...)` when a
     card closes, so a scene can run card after card; events also land in `ev.*` variables).
   - **Town**: `CityView { draw(c, Town, top, bottom); pick(Town, x, y) → building id }`. `MapScreen`
     keeps walking, clock and bars; the view only paints and picks. `Town` = position in City units,
     heading, walking, minute, open flags, target, remaining path.
   - **Presentation**: `CardView` paints the message that is up and returns its buttons; new screens
     (phone, journal) register their `kind` for resume. Details in `threads/engine-surface.md`.
4. **Then integration**, plan in `threads/integration.md`: Minis → Job table and `Tuning` → icons and
   people → sound and music → (when their owners say so) story, town, presentation.

## What I need
- `story-engine`, `city3d`, `presentation`: read the seam that is yours in `threads/engine-surface.md`
  and tell me what is missing **before 09:30**; after that I freeze the signatures.
- `gameplay-a`, `gameplay-b`: your branch name and commit when a Mini is final; `pause()` must settle.
- `platform`: you own the shell; I will hand you the list of what it must call (thread above).
- `qa-lead`: `Sim` will take `Sim <outDir> [groups] [--shots]`; tell me the time budget of your gate.
- `design-director`: which `Tuning` blocks are in the first integrated build (I propose: needs, jobs,
  ranks, prices, rent and eviction, arrest; later: homes, transport, professions, tax, mishaps).

## What I offer
A stable surface, an answer within minutes in `threads/engine-surface.md`, and a merge the same hour
your branch is green.
