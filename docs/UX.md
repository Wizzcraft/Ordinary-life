# אמא על הקו — UX audit and design system

Audience: the engineer who implements this after the merge, and every author of a screen, Mini, venue or dialog. All
sizes are logical units (lu; the screen is 360 wide, 560..900 tall). Evidence: `tools/desktop/.../UxShots.java` plays a
first session at heights 760 and 560 and writes `<scratchpad>/ux/{760,560}/NN-name.png` (+ `.targets.png` overlays,
`.txt` measurements), side-by-side pairs in `ux/pairs/`, and `contrast.txt`, `targets.txt`, `timeline.txt`. Shot ids
below (e.g. `07`) refer to those files. Re-run it after every change in section 7; it prints the three counts that must
reach zero.

## 1. Audit: a first session, screen by screen
Timeline from the run (`timeline.txt`): start 09:00, walk home→restaurant 2:14 h (6.7 real s), hired, shift 11:23→19:23
(energy 73→25), market at 19:57, home at 22:12 with **energy 11**, sleep to 06:12, casino ("soon") cost 3:05 h, collapse
at 08:00 day 3, rent missed day 11, evicted day 21.

- **Title (01, 29)**. The first decision is gender, under a 13 lu grey label "משחק חדש": a newcomer reads two equal gold
  buttons as two different games. Both are primary (two primaries). With a save, a tap on either arms a wipe that is
  shown only as 13 lu red text (29, y≈496) and **never disarms by time**; a second tap at any later moment wipes the
  save. Version text is 10 lu.
- **Name (02, 03, 39)**. Keys 31×46, gaps 4 (tolerable for a keyboard only if taller); "מחק" disabled label 2.73:1; at
  560 "המשך" ends 4 lu above the bottom edge.
- **Opening dialogs (04, 05, 06)**. Three walls of text in a row, all with the same button at exactly x44 y414 w272 h48.
  Buttons act on touch-down, so a double tap skips the rent rules. Mom's call looks like the rules card and like the
  collapse card (33): same gold title, same layout, no speaker, no phone. Bodies are centred, which reads poorly for
  4-line Hebrew paragraphs.
- **Map (07, 08, 09, 25, 31, 46)**. (a) Six of nine buildings are "בקרוב" but look exactly like the working three;
  tapping one costs 2-3 game hours (casino walk 3:05 h, 23). (b) A tap commits instantly: no walking time, arrival time
  or "open when I arrive?" before the clock starts. (c) Closed = darkened colour only: open/closed luminance ratio
  2.5-3.0 per building, 1.76 for the alley, and at night the dusk tint darkens everything so the bar (open) and bakery
  (closed) are told apart by hue alone (25). (d) At 560 the map scales to 0.765: labels 9.6 lu, hit boxes 89×47 (07
  @560). At 760, vertically neighbouring hit boxes overlap by 2 lu. (e) Time runs while the player reads; nothing says
  so except the clock. (f) No pause or menu on screen; the only way out is Android back, which jumps to the title. (g)
  "הרץ זמן" turns gold when on ("עצור"), borrowing the primary style for a toggle. (h) The only guidance is the 12 lu
  status strip at the top. (i) The ~20 lu gold player figure covers labels (25: "מאפייה"). (j) The bottom third of the
  city is empty lots.
- **HUD (all)**. Meter labels and values 11 lu. Bars fill left→right in an RTL UI. Below 20 the bar of any meter turns
  red, so at 46 all three bars are identical red slivers; the label blinks at ~1.5 Hz between grey and red. The rent
  line says "₪1780" while the rent dialog says the debt is "₪880" (36): two numbers, no explanation. Money changes
  (shift pay, ambulance) jump without any motion.
- **Building menus (10, 12, 17, 18, 20, 23, 44, 45)**. The primary action sits at y≈198-248, the hardest reach for one
  thumb; exit and bag at the bottom. Two buttons with the same verb on one screen: "לאכול (1)" (bag) vs "לאכול כאן ·
  ₪45"; at home "לשתות (3)" vs "לשתות מהברז (חינם)". Disabled buttons give no reason ("לאכול כאן" at food ≥90, 17; sleep
  at energy ≥95, 22) and tapping them does nothing. "להתחיל משמרת (8 שעות)" hides the cost: it ends at 19:23 with energy
  ~25, and the walk home costs ~12 more (session arrived home at 11; extras run collapsed on the way, 46→47). Time is
  frozen in menus but nothing says so. "Coming soon" interiors are one grey line (23).
- **Market (18, 19)**: the bag count is buried in a grey sentence. **Toasts (19, 21, 22)**: Fixed at y = h−172: at 560
  it covers the tap-water row and the rent line (22 @560, y 388-422); it survives screen changes and floats over the
  sleep screen (21).
- **Cook shift (13-16, 27; CookGame, to be replaced by CookMini)**. The intro text explains a heat bar the art doesn't
  show; the food colour follows the marker, not the green zone (misleading). Results are colour-only dots
  (gold/green/red; red-green unsafe). Summary shows money but not the 8 hours and the 48 energy it cost. No way to leave
  mid-shift. Pause card says "הרווחת עד עכשיו ₪0" with no progress.
- **Sleep and morning (21, 22)**. No wake-up time, no progress, cannot be cancelled. Morning is a 14 lu toast "בוקר
  טוב"; nothing summarises the night or reminds of the day (rent in 9 days, shift from 08:00).
- **Collapse, rent, end (33, 36, 37)**. Bad news uses the same gold title as mom's love. The ₪150 ambulance isn't shown
  leaving the wallet. End screen: decent, but "משחק חדש" only returns to the title.
- **Resume (27, 28)**. Works and reassures; "המשך · נועה, יום 2" is good copy. Keep it.

## 2. Measurements
WCAG 2.x ratio, measured on the rendered pixels around each glyph (`contrast.txt`) and on the palette.
- **Text failures (<4.5:1)**: disabled label `5B6472` on `1B2028` 2.73 (02 "מחק"; also every disabled row); map labels
  on closed/night buildings: `E9EDF2` on `5D824B` 3.75 (35 "סופר"), on `8B7253` 3.86 (25 "מאפייה"), on `427773` 4.34 (07
  "בר"); closed gas station label 4.49. `DIM` on `PANEL2` is 3.98 (unused yet). Everything else passes (INK on BG 15.4,
  ACCENT on PANEL 10.0, on-gold label 12.0, BAD on BG 6.5).
- **Non-text**: open vs closed buildings 1.76-3.05 (needs a second cue); meter bars vs track ≥7.9 (ok).
- **Text below 12**: 9.6 map labels @560; 10 version; 11 every meter label and value. (12.5 @760.)
- **Targets below 48 or gaps below 8** (`targets.txt`): bag buttons 100×46 (map) and 160×46 (buildings, gap 8); "הרץ
  זמן/עצור" 100×46; keyboard keys 31×46 gap 4, "מחק/רווח/'" 80/164/80×46 gap 4; map hit boxes 89×47 @560. Building rows
  328×50 are fine.
- **Colour alone**: meters (identity lost at low values: all red); open/closed buildings; shift result dots; HUD rent
  warning (red text only); new-game warning (red text only).

## 3. Design system
### 3.1 Type scale (Gfx size, lu). Nothing below 12, ever.
**display 44** game title, shift/venue total · **h1 26** building name, end title, Mini title · **h2 20** dialog, card
and sheet titles · **body 16** dialog bodies and paragraphs (line height ×1.45) · **button 16** every button label ·
**label 14** list sub-lines, toasts, coach marks · **small 13** captions, hints in play areas, chips · **micro 12** HUD
meter values, map labels (fixed size, never multiplied by the map scale). Clock 22, HUD money 18. Paragraphs of 2+ lines
are **right-aligned** (`Ui.paragraph` gets an align argument); one-line titles and button labels are centred.
### 3.2 Spacing
4-lu grid: 4 / 8 / 12 / 16 / 24 / 32. Side margin 16 (content 16..344). Gap between buttons 8, between groups 16, card
padding 16, dialog padding 20. Bottom safe gap 16 below the last button.
### 3.3 Colour roles (replace the `Ui` constants with these names; old names become aliases)
| Role | Hex | Allowed with |
|---|---|---|
| s0 surface | `#12161C` | background of every screen |
| s1 raised | `#1E2530` | HUD, bottom dock, cards, dialogs, sheets |
| s2 control | `#2C3644` | secondary buttons, list rows, keys |
| s3 pressed | `#3A4658` | pressed s2, selected row; no t2 text on it |
| disabled fill | `#222932` | disabled buttons |
| scrim | `#000000` @ 75% | behind dialogs and sheets |
| track | `#0B0E12` | empty part of every bar |
| t1 text | `#E9EDF2` | s0-s3: 15.4 / 13.1 / 10.4 / 8.1 |
| t2 text | `#A3ACB9` (was `8A94A3`) | s0-s2: 7.9 / 6.7 / 5.3 |
| t3 text | `#7D8796` | disabled labels and placeholders only; s0/s1/disabled: 5.0 / 4.2 / 4.0 |
| gold accent | `#FFC857`, pressed `#E0A93A` | primary fill, money, highlight ring; as text on s0-s3 ≥6.2 |
| on-gold | `#1A1300` | text on gold 12.0, on pressed gold 8.7 |
| success | `#5BD68A` | gains, OK, open chip; text on s0-s2 ≥6.6 |
| danger | `#FF7A7A` (was `FF6B6B`) | losses, warnings, MISS, low meter; on s0-s2 ≥4.8 |
| destructive btn | `#C8474A`, pressed `#A93B3E` | label `#FFFFFF` 4.7 / 5.3 |
| info | `#5AA9FF` | neutral notes, links (rare) |
| food | `#FF9F5A` | food meter and food icons only |
| water | `#5AA9FF` | water meter and water icons only |
| energy | `#B79CFF` (was green) | energy meter only; frees green to mean "good" |
| on-light | `#10141A` | labels on light building colours |
Rules: gold means "the thing to do / money"; green and red mean good and bad outcomes and are always paired with an icon
or word. One gold (primary) element per screen besides money.
### 3.4 Buttons
- Sizes: standard 328×52 (full width); half 160×52 (gap 8); dock 104×52; minimum hit area 48×48 even if the drawn shape
  is smaller; two-line button 60 tall (label 16 + sub-line 13 t2). Radius 12.
- Styles: **primary** gold/on-gold; **secondary** s2/t1; **quiet** (exit, cancel) s1 fill + 1.5 lu s3 stroke, t1 label;
  **destructive** `#C8474A`/white. Toggle buttons (fast-forward, sound) are secondary with an "on" state: s3 fill + 2 lu
  gold stroke + icon change; never the primary fill.
- States: pressed = pressed fill, label and icon offset y+1, shape scale 0.97 about its centre (in 80 ms, out 140 ms).
  Disabled = disabled fill, t3 label, and the **reason must be visible** (sub-line or caption in t2) and **tapping it
  shows the reason** as a toast + 4 lu shake of the button + TAP sound at half volume.
- Behaviour: menu, dialog and map buttons commit on **release inside** the bounds (+8 lu slop) and show pressed on down;
  gameplay touches inside a Mini may still act on down. After a dialog or sheet appears, ignore input for 300 ms (the
  button draws in its pressed colour while locked).
- Implementation: `Btn` gets `style` (PRIMARY, SECONDARY, QUIET, DESTRUCTIVE, TOGGLE), `sub` (sub-line), `icon` (name),
  `reason` (for disabled); `Screen` tracks the pressed button between down and up.
### 3.5 Cards, dialogs, sheets
- **Card**: s1, radius 16, padding 16, title h2 right-aligned (icon on its right), body 16 t1.
- **Dialog** (Game.dialog and every Beat): width 328, s1, radius 20, 1.5 lu s2 stroke, padding 20, **anchored to the
  bottom** (bottom edge at h−16), scrim behind. Header row: speaker avatar 40 (circle, right edge), name h2 coloured by
  tone: neutral gold, good news success, bad news danger (collapse, rent missed, arrest), system/tutorial t2. Body 16
  right-aligned, max 7 lines (≈220 chars, as Beat says). Actions stacked full-width 52, gap 8, the **default action at
  the bottom**. Answer options (2-3) are all secondary: the game never nudges toward truth or lie. Money or meter
  effects appear as chips under the body ("−₪150" danger, "+2 ארוחות" success) and animate into the HUD on dismiss.
- **Mom's call** is a special dialog: a 1.2 s incoming-call card (ring, PHONE sound, "אמא" + avatar, pulsing green
  "לענות" button on the right half), then the dialog. Whether a call can be declined is a director question (section 8).
- **Confirm** (destructive): title states the consequence, body states what is lost ("המשחק של נועה, יום 12, יימחק. אי
  אפשר לבטל."), buttons stacked: "ביטול" quiet on top, destructive action at the bottom. Never a "tap again" pattern.
- **Sheet**: bottom-anchored s1 panel, radius 20 at the top corners, max 70% of h; used for the destination card, pause
  menu, bag, profile, calendar. Back / tap on scrim closes it.
- **Toast**: label 14 t1 on `#000000`@92%, radius 17, height 34, centred, bottom edge 16 lu above the dock (or above the
  last button), 1.8 s (+0.04 s per char over 20). Cleared on screen change; never shown under a dialog. Max one; a new
  toast replaces the old.
### 3.6 HUD (stays 92 tall: Minis depend on HUD_H)
Row 1 (baseline 27): right "יום 3" h2-ish 16 t1 + "חודש 1" 13 t2; centre clock 22 t1 with a 16 lu sun/moon icon to its
right; left money 18 gold with coin icon; **menu button** 48×48 at x0 y0 (icon "menu" 20, t2), money starts at x 52. Row
2 (y 38-64): three meter slots 104 wide at x 244 / 130 / 16 (food right, then water, then energy: reading order). Each
slot: icon 16 at the right edge, label 12 t2 next to it, value 12 t1 at the left edge, bar 104×8 at y 56
**filling from the right**. Low (<20): bar keeps its own colour, value and icon turn danger, the slot gets a 1.5 lu
danger outline pulsing 0.4↔1 alpha at 1 Hz (never faster than 3 Hz), the icon shows a "!" badge. Critical (<10): plus
one short haptic pulse every 5 s where haptics exist. Row 3 (baseline 81): the **rent chip** "שכירות ₪900 · עוד 4 ימים"
12 t2 with calendar icon; with debt: "שכירות ₪1780 (כולל חוב ₪880)"; when money < due and ≤3 days: danger text + "!"
icon. Tapping row 3 opens the calendar sheet. When time is frozen (menus, dialogs) the clock shows a small pause glyph
instead of the sun/moon.
### 3.7 Lists (building actions, store, profile)
Row 328×60, s2, radius 12, gap 8. Right to left: icon 24 (centre x 320), title 16 t1 (right edge 300), sub-line 13 t2
below it stating the **consequence** (time, end time, meter change), and at the left a value chip (price 16 gold "₪30",
or "חינם" success). At most one primary row (gold fill) per list: the recommended action. Disabled rows keep their
layout, use t3 title and put the reason in the sub-line in t2. Section headers 13 t2, 24 lu above the group. Lists that
may exceed the space must scroll (drag) with a 16 lu fade at the cut edge; at 560 the building list area is 560−92−72−72
= 324 lu = 4 rows plus one caption line.
### 3.8 Motion (ms; ease-out = cubic 1−(1−t)³ unless stated)
Press in 80 / out 140. Screen change 180 crossfade + 12 lu slide, forward enters from the **left** (RTL), back from the
right. Dialog/sheet in: scrim 150, panel rises 24 lu and fades in 220 (no overshoot); out 140 ease-in. Money and meter
changes count over 400 (600 if Δ>₪500) and the value pulses 1.15→1 over 250, with an Fx.text delta near the HUD ("+₪213"
success, "−₪150" danger). Shift/venue total counts up over 900 with TICK every 80 and COIN at the end. PERFECT: burst 16
gold + PERFECT; promotion: burst 30 + LEVEL + a 1.2 s banner. Coach ring: alpha 0.35↔1, period 1.2 s, sine. Shake: 4
light (disabled tap, small miss), 7 failure, 10 only for collapse/arrest. Every meaningful action also has a sound
(pillar 5): taps TAP, purchases COIN, doors DOOR, hire HIRED, rent RENT, collapse COLLAPSE. Today the main game plays no
sound at all.
### 3.9 Icons (`Icons.draw(c, name, cx, cy, size, tint)`)
Sizes 16 (HUD, chips), 20 (buttons), 24 (rows), 40 (avatars, map badges). Tint with a role colour, ≥3:1 against the
background. Icons **accompany** text everywhere except: the HUD meters after day 3 (labels may then collapse to icon +
value), the result glyphs (star/check/cross, always with the word on the per-round floater), and map badges (lock,
clock, "soon") which always sit beside a word on the destination sheet. Names this spec uses (the illustrator's names
win; map any differences here): `food water energy money coin clock sun moon calendar rent home bag meal bottle tap
sleep walk bus taxi fast pause menu back close lock soon open star check cross phone heart mom alert info gem store
profile settings sound music job school casino bar bakery restaurant market gas alley police`.
### 3.10 Right-to-left rules
1. Start is the right: icons, titles and the first item of any row sit on the right; bars, progress and timers fill and
   drain from the right; "next" moves leftwards; chevrons are mirrored.
2. Paragraphs right-aligned; numbers stay LTR inside them (`Ui.nis`, `Ui.clock`); never start a line with a number
   (handbook rule).
3. Primary action: bottom of the screen, full width; if paired, the primary is the **right** button (the right thumb's
   natural spot), the quiet/cancel one on the left. Destructive confirmations stack instead.
4. Exit / back: quiet button at the bottom-left of a pair, or the left slot of the dock; Android back always does the
   same as the on-screen exit and closes sheets/dialogs first.
5. Equal-weight peers (gender cards, answer options) never use the primary style.

## 4. The first ten minutes (teach by doing, one highlight at a time)
Coach marks are lines of label 14 in a s1 bubble with a pointer, next to the target, plus the gold highlight ring on the
target; nothing else is dimmed and input is never blocked. One at a time, each stored as `seen.coach.<id>`; settings has
"להראות שוב את ההדרכה". Copy needs both genders.
1. **Title**: one primary "משחק חדש" (and "המשך" when a save exists). Gender becomes the first step of the new game: two
   equal cards with the figures, heading "מי את/ה?" and no other text.
2. **Name**, then the phone rings (incoming-call card). Mom says one line (sandwich and bottle in the bag) with two
   answers ("הגעתי, הכל טוב" / "הדירה... קטנה"). The rules card is deleted.
3. **Map, clock frozen** (pause glyph on the clock) until the first building tap. Only the restaurant rings. Coach:
   "המסעדה מחפשת עובדים. לוחצים עליה."
4. **Destination sheet** for the restaurant: "הליכה 2:15 שעות · הגעה ב-11:14". Coach on the clock: "הזמן רץ כשהולכים."
   On "ללכת" the clock starts; for the first walk the clock pulses each game hour and the meters tick visibly. "הרץ זמן"
   is hidden until this first walk ends.
5. **Restaurant**: "לבקש עבודה" is the gold row. Hired → the shift row shows "8 שעות · עד 19:14 · אנרגיה −48 · אוכל
   ושתייה עלינו". Coach on the energy meter when the shift ends: "עבודה מעייפת. הביתה לישון." The home building rings on
   the map.
6. **First meter under 35**: that meter pulses and so does the matching bag button: "יש ארוחה בתיק."
7. **Bag empty**: market rings: "בסופר יש אוכל ומים. הם נכנסים לתיק."
8. **First morning**: a morning card (day, wake time, "המשמרת נפתחת ב-08:00", rent chip highlighted: "שכירות ₪900 בעוד 9
   ימים. יורדת לבד, אם יש כסף."). The calendar is introduced here.
9. **Day 3 or first idle 10 s on the map**: coach on the menu button (pause exists).
10. **Rent minus 3 days with money < due**: danger rent chip + a mom line about rent (narrative team).

## 5. Structure
- **Pause menu** (sheet from the HUD menu button or Android back on the map; time stops): rows פרופיל, לוח שנה, חנות,
  הגדרות, קרדיטים, "למסך הפתיחה", and "להמשיך" primary at the bottom. Settings sheet: toggles צלילים (`opt.sfx`), מוזיקה
  (`opt.music`), רטט, תנועה מופחתת (no shake, no slides), "להראות שוב את ההדרכה", and **משחק חדש** (destructive →
  confirm dialog, 3.5). Credits: one scrolling card.
- **Title**: "המשך · {name}, יום N" primary; "משחק חדש" secondary (confirm if a save exists); settings icon button 48×48
  top-left; version 12 t3 at the bottom.
- **Profile** (sheet with three sections, scroll): stats (fit/cha/edu bars + icons), jobs (rank name, XP bar to next
  rank, shifts worked), mom (trust and worry bars; "מה סיפרת לאמא" list of lies, each with a check, and a risk chip
  "יתגלה בביקור?" when the facts contradict it).
- **Calendar** (sheet; from the rent chip and the menu): the current month as 10 day cells (today ringed), rent day
  marked with the rent icon, mom calls with the phone icon, below it "עוד N ימים לביקור של ההורים" with a 12-month
  strip.
- **Store**: from the menu and title only; never opened automatically, never during a shift, a sheet or a dialog; gem
  balance shown inside the store and profile, not in the HUD (question 3).
- **Map bottom dock** (72 tall, s1): three 104×52 slots right→left: לאכול (count badge), לשתות (count badge), context
  (fast-forward toggle "×6" on the street). With transport, the **destination sheet** (not the dock) holds the choice: a
  segmented row of up to three options, each 96×56 with icon, time and price ("ברגל 2:15 · חינם", "אוטובוס 0:50 · ₪6",
  "מונית 0:20 · ₪35"), the cheapest affordable preselected, arrival time and warnings below, "ללכת"/"לנסוע" primary at
  the bottom right, "ביטול" quiet left. A second tap on the same building = go with the preselected option (experts).

## 6. Rules for the parallel work
**Every Mini (shift, event, study)** — the host draws the frame, the Mini fills the play area:
1. Intro card (host): title h1, `art()` 120 tall that **shows the verb** (a ghost finger doing the first action on
   loop), `howTo()` ≤2 sentences ≤120 chars, body right-aligned, a pay line for shifts ("₪30 לשעה · 8 שעות · עד 19:15"),
   primary "מתחילים" at the bottom, quiet "חזרה" left of it (before the first round only). After 3 completed sessions of
   that Mini the card shrinks to title + art + button.
2. First-time hint inside play (round 0 of the first session only): one line small/t2 at the top of the play area and
   the gold highlight ring on the exact target; gone after the first correct action.
3. Per-round result (host): floater word + glyph at the play-area centre — "מושלם!" gold + star + burst 16 + PERFECT;
   "טוב" success + check + GOOD; "פספוס" danger + cross + shake 6 + BAD. The Mini animates its scene (the dish leaves,
   the cup spills) but draws no result banner of its own.
4. Session end (host): count-up total, pay/tips line, a row of star/check/cross glyphs filling from the right, rank
   progress, and the cost "8 שעות · אנרגיה −48", primary "המשך". Events: outcome card in success or danger tone with
   effect chips ("+₪120", "סיכון משטרה +10").
5. Layout: targets' centres between top+33% and bottom−24 of the play area, ≥48 lu, ≥8 apart, ≥16 from the side edges;
   on-screen info (timer, count) at the play-area top in small/label; timers are 4 lu bars draining from the right,
   turning danger in the last 25%. Text ≥12, colours from 3.3.
6. Exit: pause menu offers "לעזוב את המשמרת" (confirm; paid for finished rounds, no XP) — needs a director decision
   (question 4).
**Every venue (casino tables, alley market)** — one family:
1. Stakes strip at the play-area top, 44 tall, s1: venue name 16 on the right; wallet chip on the left (coin icon +
   money 16 gold, ticking on change); for the alley a heat chip (police icon + bar) next to it.
2. Scene in the middle. Bet controls in the thumb zone: a row of amount chips 64×48 (gap 8, first and smallest on the
   right), the current stake shown above them ("הימור ₪50 · אפשר לזכות ₪100").
3. Bottom row at bottom−68: primary action 196×52 on the right ("לשחק", "לקנות", "למכור"), quiet "יציאה" 124×52 on the
   left — same position in every venue, always enabled except during a ≤2 s resolution. Leaving mid-bet returns the
   stake (handbook `pause()` rule).
4. Win: success floater "+₪100", COIN, burst ≤16; loss: danger floater "−₪50", BAD at half volume, no shake. No
   auto-repeat, no "play again" highlighted after a loss, no flashing jackpot. Crime screens show the risk before the
   commit, in danger text, with the police icon.

## 7. Change list (screen · change · verify)
### P0 — confusing or error-prone today
1. **Map, coming-soon buildings** · draw in s3 `#3A4658` with 45° stripes (`#2C3644`, 6 lu apart), label t2 +
   lock-"בקרוב" chip 12; tap opens the destination sheet with the soon text and no go button; no walk. · UxShots: tap
   casino at 09:00 → `g.at`, `target` unchanged, minute unchanged.
2. **Map, tap commits a walk** · destination sheet (5, dock section): name h2, status chip, "הליכה H:MM · הגעה HH:MM",
   "סגור כשתגיע/י · נפתח ב-20:00" danger when closed on arrival, energy warning (item 7); time frozen while open. · tap
   restaurant → sheet, clock unchanged for 300 frames; "ללכת" starts walk.
3. **No pause / menu** · HUD menu button 48×48 at (0,0); pause sheet (5). · button present on map, building, sleep;
   opening it freezes `g.minute` (extend Sim.menuPause).
4. **Open vs closed by colour only** · closed: darken 0.55 **and** a "סגור" chip (s0@85%, lock 12 + "סגור · 20:00" 12
   t1) over the roof; open: lit door (gold 10×7) and lit windows at night. Labels min 12 t1 on a 70%-s0 pill when the
   building is dark. · contrast.txt has no map label below 4.5; grayscale shot still tells open from closed.
5. **Buttons act on down, no pressed state; stacked dialogs skip** · 3.4 behaviour + 300 ms lock. · double tap on 04
   shows 05 (not 06); pressed fill visible in a shot taken between down and up.
6. **Title new-game wipe** · replace "tap again" with the confirm dialog (3.5); gender moves to its own step. · Sim: a
   save exists, tap "משחק חדש" twice → save intact, dialog up.
7. **Energy trap** · shift row sub-line shows "אנרגיה אחרי: ~25"; destination sheet shows "האנרגיה תיגמר בדרך" in danger
   when projected energy < 5 on arrival (walk cost 5/h); balance question 1. · energy 15 at the restaurant, tap home →
   warning visible.
8. **Small text and targets** · meters 12; map labels fixed 12 (not ×s); map hit boxes ≥52 tall (pad to the block,
   nearest-centre wins on overlap); dock/bag buttons 52 tall; keys 32×50 gap 3 (keyboard exemption from 48 wide). ·
   UxShots summary line prints 0 / 0 / 0.
9. **Disabled without reason** · every disabled row/button gets `reason` (sub-line + toast on tap): "לא רעב/ה עכשיו",
   "כבר ישנת מספיק", "התיק מלא (4)", "אין מספיק כסף". · tap disabled sleep at 99 → toast.
10. **Collapse / rent / eviction look like mom** · tone colours + avatar/icon (3.5); ambulance shown as a "−₪150" chip
    that animates into the wallet. · shots 33, 36 titles render danger.
### P1 — clearly better
11. HUD per 3.6 (icons, 12 lu, bars fill from the right, low = outline not red bar, energy violet, rent chip with debt
    explained). · shot 46 shows three different coloured bars with danger outlines.
12. Building screens per 3.7: header card 64 tall (name h1-ish 22 + hours + open chip), action rows with consequence
    sub-lines ("לישון 8 שעות" / "עד 06:12 · אנרגיה +88"; "תנומה" / "עד 00:12 · +22"), and the dock (bag + "יציאה" quiet
    left). Rename: "ארוחה חמה כאן · ₪45", "מים מהברז · חינם". · 560 shots: no overlaps, list ≤4 rows.
13. Toast per 3.5 (anchored above the dock, cleared on screen change). · 21 shows no toast; 22 @560 toast above y 420.
14. Dialog anatomy per 3.5, bottom-anchored, right-aligned bodies, effect chips, Beat options. · 04-06.
15. Onboarding per 4 (remove the "איך משחקים" dialog). · new UxShots path asserts one highlight at a time.
16. Shift summary: cost line, glyph row, count-up (6.4). Cook intro art shows the real verb. · 16.
17. Sleep screen: "ער/ה ב-06:12" 16 t2 under the clock, a progress arc, cancel ("להתעורר") after 1 s; morning card (4.8)
    replaces the toast. · 21, 22.
18. Name screen: subtitle "ככה אמא תקרא לך" (16 t2), key labels 18, "המשך" primary at y≥ keyboard+24; keep the 12-char
    limit visible as "12/12" when full. · 39.
19. Title per 5 (one primary, settings button, version 12). · 01, 28.
20. Map fast-forward as a toggle (3.4), label "×6" + fast icon; auto-stop already exists, add toast "האטנו: האוכל אוזל"
    naming the meter. · 09, 31.
21. Mom calls: incoming-call card + avatar (3.5). · day-7 call shot (add to UxShots).
22. Map: player marker 1.6× with a t1 outline ring; path line 3.5 lu gold @80%; draw the marker after labels with a 2 lu
    s0 halo so it never hides a name. · 25.
### P2 — polish
23. Motion 3.8 everywhere. 24. Sounds per 3.8 once the shell plays `Sfx`. 25. Reduced-motion setting. 26. Map: use the
    full width at 760, trees/parking in empty lots. 27. End screen stats card (days, shifts, best rank, money, lies) and
    a direct new-game flow. 28. Calendar and profile sheets. 29. Haptics (shell). 30. Night tint ×0.8 (25).

## 8. Not evaluated here, and questions for the director
Not evaluable on a desktop: the Android Hebrew font (DejaVu was used; re-check every wrap on the phone); whether 3 real
seconds per game hour feels calm or frantic; thumb reach on the Pixel and gesture-back vs taps at x<16; sound and
haptics (the main game plays none today); animation smoothness; OLED colour outdoors; real colour-blind users.
Questions: (1) Balance: keep MIN_SHIFT_ENERGY 55 (players then collapse walking home) or raise it to ~65 / make the
restaurant's after-shift meal restore energy? (2) Should the clock be frozen on the map until the first tap of every
session, or only on day 1? (3) Gems in the HUD or only in store/profile? (4) May a player leave a shift early (paid for
finished rounds), and may they decline mom's call (and what does that cost)? (5) Energy colour change from green to
violet: OK for the art team? (6) Who owns `Btn`/`Ui`/`Game.drawHud` after the merge: one engineer for all of section 7,
or each team for its own screens following section 6?
