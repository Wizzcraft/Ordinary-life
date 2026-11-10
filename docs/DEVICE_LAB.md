# Device lab

Every push to the `ci-lab` branch (and every manual "Run workflow" of **device-lab** in the Actions
tab) builds the debug APK, boots a real Android system image in an emulator on GitHub Actions, plays
the game through its screens, screenshots it, and fails if the app crashes, hangs, dies, or cannot
finish the session. Nothing is published except workflow artifacts.

## Parts

| File | What it is |
|---|---|
| `.github/workflows/device.yml` | The workflow: desktop tour, APK build, KVM, cached AVD, the lab, artifact upload. |
| `tools/device/lab.sh` | Everything that happens on the device. Works against any adb device, not only CI. |
| `app/src/main/java/com/wizzcraft/ima/core/Tour.java` | The autopilot: scripted steps, found by Hebrew label / building id. Pure Java. |
| `app/src/main/java/com/wizzcraft/ima/TourDriver.java` | Android side of the autopilot: turns logical points into real `MotionEvent`s, logs to logcat. |
| `tools/desktop/com/wizzcraft/ima/core/TourLab.java` | Runs the same tour on the desktop, frame by frame, with a screenshot per step. |

Emulator: API 35 (Android 15, same edge-to-edge rules as the owner's phone with `targetSdk 35`),
`google_apis`, x86_64, `pixel_6` profile (1080 x 2400, 420 dpi, 3-button navigation), software GPU
(`swiftshader_indirect`). `lab.sh` switches on the system's emulated punch-hole cutout
(`CUTOUT=hole`; also `tall`, `corner`, `none`).

## What a run does (`lab.sh`)

1. Writes `device.txt` (Android version, screen size, density, cutout), installs the APK, clears data.
2. **Tour**: `am start -n com.wizzcraft.ima/.MainActivity --es tour smoke`. While it runs, a screenshot
   is taken whenever a new `IMA-TOUR step` appears in logcat and at least every 3 s. Waits for
   `IMA-TOUR DONE` (timeout `TOUR_TIMEOUT`, 240 s). `IMA-TOUR FAIL` or the process vanishing fails the run.
3. `dumpsys gfxinfo` for the tour (counters are reset right after launch) into `gfxinfo.txt`; the key
   lines go to the summary.
4. **Save and resume**: HOME, copy the save (`run-as ... shared_prefs/save.xml`), app drawer screenshot
   (launcher icon), relaunch (warm, same process), HOME, `am kill` (the process dies like under memory
   pressure), relaunch (cold, from the save), BACK to the title. Screenshot after each.
5. **Monkey**: `monkey -p com.wizzcraft.ima -s 4242 --throttle 20 -v 3000` (seed and count are inputs).
6. **Verdict**: fails on `FATAL EXCEPTION` in our process, `ANR in com.wizzcraft.ima`, a native
   `Fatal signal`, a monkey crash/ANR, the app not running after a phase, or an unfinished tour.

## Reading the results

Open the run, scroll to **Artifacts**, download `device-lab-<run number>`:

- `lab-out/summary.txt` — the verdict, tour time, frame stats, resume, monkey tail (also on the run page).
- `lab-out/shots/NNN_tSSSSs_<step>.png` — order, seconds since the lab started, and the last tour step
  that had been logged. A step is logged *before* its action, so `step-10-restaurant-tap...` shows the
  restaurant menu just before the tap. Fixed names: `tour-done`, `launcher-drawer-icon`, `resume-warm`,
  `resume-cold-new-process`, `back-to-title`, `after-monkey`.
- `lab-out/tour.txt` — every `IMA-TOUR` line (steps, dialogs, dishes, the shell's view/inset/scale line).
- `lab-out/logcat.txt` — full logcat; `problems.txt` — crash/ANR/death lines for our process.
- `lab-out/gfxinfo.txt`, `monkey.txt`, `device.txt`, `save-after-tour.xml`, `am-start-cold.txt`.
- `lab-out/desktop/stepNN.png` — the same tour rendered by the desktop harness (DejaVu font), for
  side-by-side comparison. `app-debug.apk` — the exact APK that was tested.

With `gh`: `gh api repos/Wizzcraft/Ordinary-life/actions/runs/<id>/artifacts`, then
`gh api repos/Wizzcraft/Ordinary-life/actions/artifacts/<artifact id>/zip > lab.zip`.

The frame numbers come from a software-rendered emulator on a shared CI machine. Use them to spot
regressions between runs, not as phone performance.

## Cost

About 8-10 runner minutes per run with a warm AVD cache (build ~2, emulator boot ~1, tour ~1,
resume ~0.5, monkey ~1.5, setup and upload the rest); the first run after the cache key changes
boots twice and takes ~4 minutes more. The real numbers for each run are on its page.

## Tours

### Run one locally (desktop, no emulator)

    javac --release 17 -d tools/out/classes app/src/main/java/com/wizzcraft/ima/core/*.java tools/desktop/com/wizzcraft/ima/core/*.java
    java -Djava.awt.headless=true -cp tools/out/classes com.wizzcraft.ima.core.TourLab smoke tools/out/tour 760

Prints the same `IMA-TOUR` lines and `TOUR OK`. Always get a tour green here before spending CI minutes.

### Run one on a phone

    adb shell am start -n com.wizzcraft.ima/.MainActivity --es tour smoke
    adb logcat -s IMA-TOUR

Warning: the smoke tour starts a new game and confirms over an existing save. Not on a phone whose
game you want to keep.

### Add a step

Steps live in `Tour.smoke()`. A step is a name (logged as `IMA-TOUR step N: <name>`; keep it ASCII
apart from quoted labels, it becomes part of screenshot names) and a `BooleanSupplier` called once per
frame until it returns `true`. Helpers:

- `once(tap, doneWhen)` — taps, then waits for `doneWhen`; taps again only after 3 s without effect.
- `tapLabel("prefix")` — the centre of the first enabled button whose label starts with the prefix.
- `walk(name, City.X, fast)` — walks to a building, optionally pressing 'הרץ זמן'.
- `buy(label, counter)` — taps until the counter goes up.

Dialogs never need a step: whenever one is up, the engine logs it as its own step
(`dialog '<title>'`) and taps its button. Every action is followed by a 0.8 s pause, and a step
that takes more than 90 s fails the tour.

### Add a tour

Add a method like `smoke()` and a name for it in `Tour.create`, run it with TourLab, then start the
workflow by hand with that name in the `tour` input (or change the default in `device.yml`).

## The hook in the shell

Four lines, all marked `DEVICE-LAB HOOK`. `TourDriver.java` and `Tour.java` are new files and carry
over as they are.

`GameView.java`, a field next to the other fields:

    /** DEVICE-LAB HOOK (docs/DEVICE_LAB.md): set only by TourDriver.attach; null in normal play. */
    TourDriver tour;

`GameView.onDraw`, right after `game.update(dt, logicalH);` and before drawing:

    if (tour != null) tour.frame(dt, offsetX, insetTop, scale); // DEVICE-LAB HOOK

`frame` needs the exact transform the view draws with: the x offset and y offset in view pixels and the
logical-to-pixel scale. In a new engine pass whatever plays those roles.

`MainActivity.onCreate`, right after the save is loaded (so the tour sees the loaded game):

    TourDriver.attach(view, getIntent()); // DEVICE-LAB HOOK (docs/DEVICE_LAB.md)

`Tour` reads `Game.dialogs` and `Game.dialogBtn` reflectively, and uses `Screen.btns`, `Btn`,
`City.B`, `MapScreen.BAR_H`, `Game.HUD_H`, `NameScreen.name`, `CookGame.playing()/inZone()`. If the new
engine renames those, `Tour` is the only place to update; TourLab on the desktop shows at once what broke.
