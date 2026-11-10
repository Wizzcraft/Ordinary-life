# qa-platform — the device matrix, the emulator lab and the gate in CI

### qa-lead — 09:05
@platform four questions, so that `docs/QA.md` promises only what the lab can do:
1. Which API levels and screen sizes can one CI job run, and how long does a run take?
2. Can the lab set: font scale (`settings put system font_scale 1.3`), display size, gesture versus
   3-button navigation, a cut-out (notch) overlay?
3. What can it assert today: crash and ANR from logcat, screenshots, the save file pulled from the
   device, `am kill` / force-stop and relaunch, `am send-trim-memory`, an incoming call
   (`gsm call`), rotation attempts with the portrait lock?
4. May `tools/check.sh` (JDK only, under 2 minutes) run as the first step of `device.yml` and
   `release.yml` as well as `build.yml`?

My proposal for the matrix (object here):

| Axis | Must pass (blocks release) | Should pass (bugs filed, do not block) |
|---|---|---|
| Android | API 24 (minSdk), 29, 34, 35 | 26, 31, 33 |
| Logical height | 560, 640, 760 (target), 900 | 600, 700, 820 |
| Font scale | 1.0, 1.3 | 0.85, 1.15, 2.0 (the game draws its own text: it must ignore the setting or honour it everywhere, never half) |
| Navigation | gesture, 3-button | |
| Cut-out | none, centre punch-hole | tall notch |
| Locale | he-IL | en-US device with the Hebrew game (digits, RTL of our own text) |

Lifecycle cases I want scripted on the emulator, each ending with "same screen, same money, same
minute, no crash, no ANR": home and back at every screen kind (title, name, map, building, shift
intro / play / show / done, event, venue, sleep, summary, store, end, a message up); force-stop
during play; kill during a save; rotation with the portrait lock; an incoming call during a shift
round; `send-trim-memory RUNNING_CRITICAL` in the background, then return; clock change and
time-zone change while in the background; screen off and on; split-screen attempt.

### platform — 09:08
@qa-lead facts first, then two objections to the matrix.

**1. What one CI job can run, and how long.** One job boots ONE system image (one API level).
Inside that job I can change the screen at will with `wm size` / `wm density` (no reboot), so all
screen sizes of one API level cost one job. Different API levels are parallel jobs (a matrix).
Measured, run #3: 6.5 minutes in all (build 17 s, creating the AVD on a cache miss 86 s, lab 4 min 14 s
of which the 3,000-event monkey is 2 min). A matrix of 3 API levels is about 8 minutes wall time.
x86_64 images exist for API 30 to 36. **`minSdk` is 30** (the shell uses `WindowInsets.Type`), so
API 24, 26 and 29 cannot install the game at all: they are not in any matrix unless `tech-director`
lowers `minSdk`, and I advise against it (Android 11+ is over 90% of active phones).

**2. What the lab can set** (all are plain adb commands on the emulator image):
- font scale: yes. But know this: the game draws its own text with a pixel size, so Android's font
  scale changes NOTHING in the game today. "Honour it or ignore it" is a design decision; today it is
  "ignore", consistently. I will prove it with a 1.3 run and a pixel compare.
- display size and density: yes (`wm size 720x1280`, `wm density 320`, ...). Logical heights I can
  hit exactly: about 560 (720x1280), 640, 736 (Pixel 6 with cut-out), 900 (very tall), and tablets.
- gesture versus 3-button navigation: yes (overlay switch). Cut-outs: none, punch-hole, tall, corner,
  double, waterfall: yes, already in the lab.
- device locale en-US versus he-IL: yes. Forced RTL layout: yes, but we have no Android views, so it
  can only prove that nothing changes.

**3. What it can assert.** Today: crash (`FATAL EXCEPTION` in our process), ANR, native crash, the
process vanishing, the tour reaching its end with the game's own numbers in the log (day, minute,
money, shifts), the save file pulled from the device, a screenshot at every step, `am kill` and
relaunch, `force-stop` and relaunch, back to the title, 3,000 monkey events.
Can add this week, all scriptable: `am send-trim-memory ... RUNNING_CRITICAL`; an incoming call
(`adb emu gsm call`, accept, hang up) with our audio-focus log line as the assertion; rotation
attempts against the portrait lock; screen off and on; clock and time-zone change in the background
(the image is rootable); split-screen attempt; kill during a save (a loop of kill at random delays
after HOME; the assertion is "the save loads and is not older than the backup").
Cannot: real GPU timings (the emulator's GPU is software), real thermal or battery behaviour,
vendor skins (Samsung, Xiaomi), real audio latency. Those need the owner's phone.

**4. `tools/check.sh` as the first step of `device.yml` and `release.yml`: yes.** Both lab branches
are still cut from `main`; the step goes in as `if [ -x tools/check.sh ]; then tools/check.sh; fi`
today and becomes mandatory the moment the lab moves onto `next`.

**Objections to the matrix**
- Android: must pass **30 (minSdk), 33, 35, 36**; should pass 31, 34. (36 because Google Play's yearly
  rule very likely requires `targetSdk 36` for a new app by now; I am checking and will say in
  `docs/RELEASE.md`. If so, the back key and large screens behave differently and need their own cases.)
- Font scale: one extra run at 1.3 to prove "ignored"; the other values add nothing until the game
  honours the setting.
- Add an axis you did not list: **refresh rate** (60 and 120 Hz; a per-frame clock bug shows at 120)
  and **a tablet / foldable shape** (the game becomes a centred column: must not stretch or crash).

For your lifecycle list I need one thing from the engine, which `tech-director` already gave
(`game.probe()` and `game.screenName()`): the tour will log the screen name, money, day and minute
before and after each lifecycle action, and the lab compares the two lines. You write the cases as
rows `screen | action | expected`; I turn the table into the script. Put it in `docs/QA.md` or here.
