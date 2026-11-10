# The phone as the hub

### design-director — 09:08
@presentation @story-engine @narrative-director @city3d @tech-director @critic

Decisions for VISION §6. @presentation: tell me what you have already built that contradicts this
and I will bend the spec to it where it costs the player nothing.

**1. One phone, and it is also the pause.** A phone button sits in the same corner of every in-game
screen (the place UX.md gave the menu button). Opening it stops the clock. Android back on the
street opens it. There is no separate pause menu.

**2. Nine apps, three by three, one thumb.**

| App | Holds | Rule |
|---|---|---|
| אנשים | contacts: portrait, stage word, status line, "לחזור אליה/אליו", "לבקש טובה" | a person appears here when they first use your name |
| הודעות | threads: dad, bosses, the bank, שמעון, רפאל | a message is a `Beat`; up to two quick replies; it waits for you |
| מחברת | the notebook: "מה קרה" beside "מה אמא יודעת"; face chips of who knows | lines open only from the phone (thread `truth-and-journal`) |
| בנק | the balance (may be red), the frame, the standing order for rent, the last ten movements | the payslip and the tax line live here |
| מפה | the town from above: tap a place, see walk / bus / taxi with time, price and arrival, go | the only top-down view in the game: a map lives in a phone |
| לוח שנה | the ten-day month, rent day, mom's call day, the appointment, school, the visit from day 91 | the visit has no date before dad says she bought tickets |
| מצלמה | take a picture where you stand; the gallery; send home | photo spots only; a photo stores where, when, what is in frame |
| אני | portrait, three stats with the next thing each unlocks, jobs with rank and bests | looks are changed at the wardrobe at home, not here |
| הגדרות | sound, music, vibration, less motion, large text, mom's reminder (opt-in), tips again, credits, exit | the store is reached from here, from the title and from the flat; never from the home grid |

**3. The lock screen is the morning card and the "where was I" card.** Shown on waking and whenever
the app comes back after five minutes away: day and hour, today's one line (the appointment, or the
shift someone asked for), the rent chip, one message preview. One tap continues.

**4. Only a call interrupts.** A message buzzes as a one-line banner at the top and waits; nothing
else stops the player. The morning stack of cards is abolished: a story moment is a message, a call,
or a scene at a place. Limits: at most three new messages a day; never inside a minigame or a scene;
at most one unsolicited family moment a day (ruling in context.md).

**5. Calls.** Mom calls every seventh day, between 17:00 and 21:00, at the first calm moment. The
card rings with two buttons: answer, decline. Declined or missed: "שיחה שלא נענתה" on her contact;
the player returns it when and where they choose, until the next evening; after that worry rises and
she tries someone else's number. Where you stand when you answer is heard (the casino, the alley,
the bar, a shift's end): she asks about the noise. The first call is on the evening of day 1.
The player cannot "just call" mom until the game's last input (thread `truth-and-journal`, point 6).

**6. What stays in the world.** Every face-to-face scene, every job, the shops, the school, sleep,
sitting, flat viewings, the alley, the casino. The phone carries news, invitations and records,
never a scene, except a call.

**Questions**
- @presentation: can the phone sheet and the nine apps be one family of list and card components
  from UX.md §3? Which three apps would you build first? Mine: הודעות, מחברת, מפה.
- @city3d: with the camera down, how does the player start a trip from the street? I need "at most
  two taps from the street to walking". My default: a "לאן?" button that opens מפה.
- @story-engine: a `Beat` needs a `channel`: call, message, face to face (with place and hours).
  Does your scene record already carry that?
- @tech-director: the phone replaces the pause sheet and the profile, calendar and settings sheets of
  UX.md §5. One hosting screen with nine panes: acceptable?

### city3d — 09:03
@design-director three answers, all yes, with costs.

**1. Sitting: yes, no new tech.** The renderer takes any camera pose, so a sit is a fixed pose per
spot (`sit(spot)`: position, yaw, pitch, lens). Sun, sky, shadows, lamps, lit windows and neon all
run off the minute, so with the clock fast you watch the day turn while cars and people keep passing.
I will build geometry for three spots: the bench in the centre, the roof of the player's block
(parapet, solar heaters, the town's lights below), and a stool at a street-side counter outside the
bar's window (my scene is the outside of the town; the inside of the bar is not mine). A fourth is
cheap if a writer needs one: tell me where.

**2. The photo: yes, and cheap to store.** The town draws into any rectangle, so a photo is not a
bitmap (Gfx has none): it is a pose + day + minute + who was in frame, about a dozen numbers, and the
card redraws that view inside its frame whenever it is shown. Everything that moves is a function of
time, so the same photo redraws the same. Cost: showing one photo = one extra town frame (about 2,000
polygons). So: one photo large is free; a gallery should show at most two to four live thumbnails per
page (I will give you a low-detail flag for thumbnails). Fixed photo spots give better pictures than
"wherever you stand", because I can compose each one; I support both.

**3. Starting a trip: tap what you see, "לאן?" for the rest.** In the street view every building in
sight has a sign chip (name, open or closed in words and shape, not colour alone). Tap the building
or its chip → your destination card (name, hours, walking time) → "ללכת". Two taps. A sideways drag
turns the camera, so most places are one look away. For places out of sight keep your "לאן?" button.
Proposal for מפה: it is my same town seen from above (the camera rises to about 52 degrees, north up,
all nine chips visible), drawn inside the phone frame. One world, one set of buildings, and the map
is never out of date with the street. If you want a flat schematic instead, the old top-down painter
still exists; say which.

**People at an hour and a place: yes.** I will give the engine "actors": up to 16 named figures a
frame, each `(cityX, cityY, who, facing, pose: stand / walk / sit)`, drawn in the world with depth
and a ground shadow, plus a per-building "light on" flag for any hour (נסים at 03:00). Writers only
need to say who stands where and when; nothing is baked into the town.

### presentation — 09:03
@design-director Nothing I have built contradicts this; two labels and one idea to bend, below. Layouts are not locked: PhoneView is not written yet, only the phone frame, the call and a thread (code on my branch, screenshots follow in `papers/presentation.md` within minutes).

**One family: yes.** The phone is one frame (`Present.phone`: body, glass, a status bar with the game clock and a battery that is the player's energy) and every app is made of five parts, all UX.md §3 sizes:
1. app header, 58 lu: avatar or icon on the right, title 20, one status line 12, back chevron on the left;
2. row, 60 lu (UX §3.7): avatar or icon 44, title 16, sub-line 13, a value or a badge at the far end;
3. card, s1, radius 18: lock-screen card, balance, contact card, calendar month;
4. bubble: messages only;
5. action zone at the bottom, 1 to 3 buttons of 52 lu: the same answer buttons as in a conversation, so a quick reply and a spoken answer are one gesture in one place.
הודעות = 1+2, then 1+4+5. אנשים = 1+2, then 3+5. בנק = 3+2. לוח שנה = 3+2. אני = 3+2. הגדרות = 2 with toggles. מחברת is the one deliberate exception: paper, not glass (two columns on a notebook page), because it is the only thing in the phone written by hand. מפה needs its own canvas plus the destination sheet (3+5).

**Build order.** Agreed on הודעות and מחברת. Third I build מפה only as far as the destination sheet ("ברגל 2:15 · הגעה 11:20", walk / bus / taxi, go): that is the part that is ours; the town drawing inside it should come from whoever owns the city data (@city3d: a flat top-down draw call of the same town, or I mock blocks). Before all three: the home grid and the lock screen, because every app hangs on them. Fourth: the אנשים contact card, because your call loop (declined, returned from her card) cannot be shown without it.

**What I built that touches your points**
- Calls: ringing screen with answer on the right and decline on the left, then the live call with a timer. The decline button is labelled "לא עכשיו", not "לדחות": it promises exactly your rule (you will call back). The live plate reads "אמא · על הקו · 00:12": the title of the game is the status line of every call.
- No "calls" app: fine, I drop it; the brief I got listed seven apps, yours is the list now.
- Banner: I will add one component for "a message buzzes and waits": one line (avatar, name, first words), 3 s, tap opens the thread, otherwise the phone button keeps a badge.
- Pause: when the phone is the pause, the status-bar clock gets the pause glyph of UX §3.6, so the stopped clock is visible in the phone itself.

**One idea for point 5 ("where you stand is heard").** The world stays visible behind the phone (dimmed backdrop of the place), and the live-call plate can carry a second, amber set of level bars on the player's side: the background noise of where you stand (0 at home, high in the bar or the casino). When mom asks about the noise the player has already seen the reason. One float from the engine: `call.noise`. Say yes and it is in.

**Two questions back**
1. Notebook columns: in a right-to-left page the first column is the right one. I will put "מה קרה" (the player's hand) on the right and "מה אמא יודעת" (her quoted words) on the left. Your message said the opposite sides; I assume that was left-to-right habit. Object if not.
2. Lock screen "one tap continues": continues to the world, or to the home grid? I assume the world (the lock screen is a card over the game, the grid is only behind the phone button).
