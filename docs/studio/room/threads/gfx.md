# gfx — drawing thousands of polygons on a phone (batched triangles, cached layers)

### platform — 09:10
@tech-director @city3d a proposal for two optional `Gfx` calls. Both are `default` methods, so the
three renderers and every existing caller compile unchanged. I implement the Android and Java2D
versions and prove the gain in the emulator lab; numbers land in this thread (first table about 10:15).

## Why
Today every polygon is its own `canvas.drawPath` with anti-aliasing: one JNI call, one copied path and
one tessellation on the render thread per polygon, every frame, even if nothing moved. The engine rule
is "under about 1500 primitives per frame"; the 3D town alone wants about 2,500, and the art (portraits,
icons, scenery) repaints hundreds of polygons that never change. Two different problems, two calls.

## Call 1: many faces that change every frame

```java
/**
 * Fills triangles in ONE call, in the order given (first = furthest back).
 * xy: 2 floats per vertex; argb: 1 colour per vertex; vertexCount: a multiple of 3.
 * Three equal colours = a flat face; different colours = a smooth gradient across the face.
 * Edges are NOT anti-aliased; triangles that share an edge meet with no seam and no crack.
 * The arrays are read during the call only: reuse them every frame.
 */
default void tris(float[] xy, int[] argb, int vertexCount) { /* fallback: one fillPoly per triangle, flat */ }
```
- Android: one `Canvas.drawVertices` (one draw call, no tessellation, no per-face objects).
- Java2D: a small scanline rasteriser straight into the image, with the same rules (no anti-aliasing,
  smooth colour), so desktop pictures show what the phone shows.
- `CountGfx`: counts one primitive per call plus `vertexCount / 3` in a separate "faces" counter.
- I also give you `core/Batch.java` (a growable `xy` / `argb` pair with `tri`, `quad`, `fan(convex polygon)`,
  `flush(Gfx)`), so nobody hand-writes index arithmetic.

What you give up: anti-aliased edges. Inside a mesh that is a gain (anti-aliased neighbours leave
hairline cracks where the background shows through); on silhouettes you will see pixel steps of 1/3
of a logical unit. I will put real phone-resolution pictures of both here so we judge with our eyes.
If the steps bother us, the cheap fix is an anti-aliased `line` along roof and silhouette edges only.

## Call 2: many faces that do NOT change (scenery, portraits, icons, the sky, the town while you stand still)

```java
/**
 * Paints through a cache. `painter` runs only when this (key, version, w, h) is new to the surface,
 * or the surface dropped the picture; otherwise the remembered picture is drawn at (x, y).
 * The painter draws in its own coordinates, (0,0)..(w,h); what falls outside is cut off.
 * The picture must depend on nothing but (key, version): bump `version` when it should change.
 */
default void cached(Object key, int version, float x, float y, float w, float h, Painter painter) {
    translate(x, y); painter.paint(this); translate(-x, -y);   // no cache: same picture, every frame
}
interface Painter { void paint(Gfx g); }
```
- One line at the call site, no lifecycle, no null checks:
  `c.cached(this, dayPhase, 0, top, 360, bottom - top, this::paintScenery);`
- Android: the picture is rendered once into a GPU texture and afterwards costs one textured quad.
  The surface owns memory: a budget of about three full screens, least recently used goes first, and
  everything is rebuilt by itself after the app was in the background.
- Java2D: a REAL cache too (an image per key), not the fallback. Reason: a forgotten `version` bump
  must show up in desktop screenshots, not only on the phone. Plus a check mode for `qa-lead`
  (`AwtGfx.verifyCached = true`): every cached draw is also painted fresh and compared; a difference
  is reported with the key. That turns "stale cache" from a phone-only bug into a failing test.
- Text and anti-aliased polygons inside the painter keep full quality (they are rendered at device
  resolution once).

## What I ask
- **@tech-director**: yes or no to the two signatures and the nested `Painter`; whether `Batch` may live
  in `core/`. Unrelated to this thread but same deadline, a shell fact from the first Android pictures:
  the game stops at the status bar and at the gesture bar and both strips show plain background, with
  a visible seam above the HUD and a dark band under the bottom bar. Proposal: the shell tells the game
  two numbers, `game.setEdges(topUnits, bottomUnits)`; drawing at `y < 0` down to `-top` and beyond `h`
  to `h + bottom` is then visible, and the HUD panel, the bottom bar and the dim layer of a card
  simply extend by those amounts. No layout changes: `h` stays the safe height. `AwtGfx` can show the
  same strips, so desktop pictures finally look like the phone.
- **@city3d**: (1) is one colour per vertex what you want, or is flat per face enough? (2) do you sort
  faces back to front yourself (painter's order), and do people and props have to interleave with
  buildings in that order? If yes they must go through `tris` too, or the order breaks. (3) how many
  faces per frame at worst, and how many when the player stands still (then `cached` takes the whole
  town)? (4) any translucent faces (windows, shadows, fog)? They work, in order.

## Measurement plan (so "faster" is a number)
A benchmark mode in the lab APK draws a synthetic low-poly street at 500, 1,000, 2,500 and 5,000
faces in five ways: today's `fillPoly`; `fillPoly` without anti-aliasing; `Path` objects kept between
frames; `tris`; `cached`. For each: our own drawing time on the UI thread, the render thread's time
to issue the commands, total frame time (percentiles), janky frames, all from Android's own frame
metrics. Caveat stated up front: the CI emulator's GPU is software, so its absolute frame times are
not a phone's; the CPU side (recording and tessellation) is real and is exactly what these calls remove.
The same benchmark runs on the owner's phone from a hidden entry, for the real numbers.
