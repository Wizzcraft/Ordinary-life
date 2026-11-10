# platform — Android device lab, release, performance on the phone, shell

First version 09:00. I took over the two lab branches (`ci-lab`, `release-lab`); my working copies are
the local branches `platform/ci-lab` and `platform/release-lab`, pushed to the remote under the old names.

## What I found

**The lab works, on the old game.** Both lab branches were cut from `main` (0.1.3), not from `next`.
Device lab run #3 is green: https://github.com/Wizzcraft/ordinary-life/actions/runs/42768465164
Android 15 (API 35), Pixel 6 profile, 1080x2400 @420dpi, emulated punch-hole cutout, gesture navigation.
- The APK installs and starts; a 24-step tour (name, mom, walk, get hired, an 8-dish shift, market, home,
  sleep, morning) finishes in 56 s through real touch events; 29 real screenshots.
- Save and resume: warm resume and a killed-process cold resume both return to the same place
  (cold start 2.6 s on the emulator). 3,000 monkey events: no crash, no ANR in our package.
- Release run #2 is green: R8-shrunk AAB and APK, optional upload-key signing, adaptive icon, `StoreArt`.
  Missing there: `docs/RELEASE.md`, privacy policy, store listing, the owner's to-do list.

**The first pictures of the game on Android** are in
`/tmp/claude-0/-home-claude/afbbc77c-f63c-5589-a685-e163ec397744/scratchpad/platform/runs/run3/shots/`
(side by side with the desktop render: `.../platform/look/pair-stepNN.png`). What differs from desktop:
1. **Font.** Android draws Hebrew in Noto Sans Hebrew and digits in Roboto; the desktop harness uses
   DejaVu Sans. On the phone the same text is 13-16% NARROWER and lighter (HUD date line 214 px vs 247 px;
   the clock 155 vs 185). Paragraphs wrap at different words. Nothing overflows, but every desktop
   screenshot the team reviews shows text bigger and heavier than players will see.
2. **The ₪ sign** on Android is a small, low glyph (about 60% of digit height) beside tall digits;
   on desktop it is full height. Money is on every screen, so this is visible everywhere.
3. **Insets.** Top inset 128 px (43 units) with the cutout, bottom 63 px (21 units) for the gesture bar.
   The game stops at both and the strips are painted in plain background colour, so there is a visible
   seam above the HUD panel and a dark band under the bottom bar. Panels should run to the screen edges.
4. **Colours** match exactly (sampled: background is 0x12161C on both).
5. Launcher icon on `ci-lab` is the old non-adaptive one (Android puts it in a white circle).
6. **Frame times** for these LIGHT screens on the software-GPU emulator: p50 117 ms, 96% janky. That is
   the emulator's software renderer, not a phone, and today's one number per run cannot tell our cost
   from the emulator's. The lab needs per-screen numbers split into "our drawing code" and "the rest".

## The three biggest things I will do
1. **Device lab v2**: frame statistics per tour screen (percentiles, janky frames, our draw time
   separately from the render thread), crash traces, an Android-vs-desktop text metrics table, a matrix
   (API 30/33/35; small, normal, tall and tablet screens; large system font; 3-button navigation),
   a summary on the run page, `docs/DEVICELAB.md`. Then move the lab onto `next`.
2. **Polygon performance**: a benchmark mode in the APK (500 to 5,000 polygons per frame), then a
   batched triangle call and a static-layer cache behind `Gfx`, proven by lab numbers.
   Proposal goes to `threads/gfx.md` within the hour.
3. **Shell and release**: patches for sound and music with audio focus, haptics, crash recovery screen,
   gesture exclusion, backup rules, the opt-in mom notification; listing and privacy drafts (he + en),
   `docs/RELEASE.md`, and the exact list of what only the owner can do.

## What I need
- **@tech-director**: (a) say when `next` builds and plays a first session, and I will merge it into
  `ci-lab` and rewrite the tour for the new engine; (b) a small stable way for the tour to find
  what can be tapped (today: `Screen.btns` labels, plus reflection into `Game.dialogs`, which R8 will
  break in release builds); (c) agreement on the `Gfx` additions; (d) how you want shell patches
  delivered (I assume: one branch, one commit per feature, new files where possible).
- **@city3d**: what a frame of the town is made of: polygon count, convex or not, flat colour per
  polygon, how much is identical from frame to frame, any transparency, do edges need anti-aliasing.
- **@qa-lead**: which lab results gate a release (my proposal: tour passes, zero crashes and ANRs,
  5,000 monkey events, save survives a killed process), and which devices you want in the matrix.
- **@comedy-head**: the away-for-days notification in mom's voice: 4-6 variants, title up to 30
  characters and body up to 80, both genders or gender-free, gentle, no guilt.

## What I offer
- Real Android screenshots of any branch: tell me the branch, about 8 minutes per run.
- Numbers on request: text widths on Android, frame cost of any screen, APK and AAB size.
- `StoreArt`: store icon, feature graphic and 9:16 screenshots drawn by the game's own code.
