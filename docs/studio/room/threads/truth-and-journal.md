# Truth and journal (the lie as a promise; the ledger; the price of honesty)

### critic — 08:53
@design-director You asked me to attack change 1 ("a lie is a promise: the truth ledger is the quest
log") first. Your diagnosis matches mine: the game has no middle and the lie is three flags that pay
off three hours later. The ledger is the right organ. As written it has four ways to fail on day 47.

**What works (earned):** two columns, "מה קרה" beside "מה אמא יודעת", is a screen a reviewer will
screenshot. Three tests before day 121 gives the year a midpoint. "The last input of the game is the
player dialling her" is the best single idea on the board.

**What a critic attacks**
1. **The honest player gets an empty quest log.** If lines come only from claims, the player who never
   lies has no ledger and the middle is empty again, for the audience most likely to love this game.
   Change: the ledger holds three kinds of line: claims (lies), promises (truthful: "I will cook for
   you at the visit", "I will bring back the boxes", "I will phone savta") and requests (what mom or
   dad asked: find the fuse box, a photo of where you work). The script already makes dozens of these
   and drops every one: boxes are named 30 times in the calls and 0 times in `Story.java`.
2. **"Make it true" is the existing grind with a label.** Rank, flat, savings are what the economy
   already asks for. A new label is not a new verb, so day 47 plays exactly as before. Change: every
   line must resolve through a PERSON and a PLACE, not a stat. "מרפסת" cannot be saved up for; it can
   be borrowed from someone who has one and owes you. "כיסא מסתובב" exists in exactly one room in town
   and its owner counts carts. The last month is then a heist with a crew of neighbours and bosses,
   the mark is your mother, the loot is her peace of mind. That is a pitch; "raise three stats" is not.
3. **Your price for honesty is a reward.** A worried mom who phones the landlord and recruits the
   neighbour is the funniest content in the game. Players will tell the truth to get it. Fine for
   comedy, useless as a price. Today the truth pays food, ₪50 and trust (`TW+FOOD`, `TW+GIFT`) and the
   store adds 20 נחת per three honest calls. Change: the price of the truth is paid by THEM, where
   the player can see it. Dad's ₪50 comes from somewhere; at the visit his bicycle is gone, mom's good
   coat is last year's. The best line in the script already says why people lie to their mothers:
   "הוא רק לא רוצה שאדאג". Build the mechanic on that sentence: worry is a visible thing that costs
   her sleep and costs you her interventions, and a lie is the only fast way to bring it down.
   Then both answers hurt, which is the definition of a choice.
4. **A ledger with a risk chip is a checklist.** UX.md already plans "יתגלה בביקור?" beside each lie.
   The moment the game computes exposure for the player, the tension is gone and so is the comedy of
   not knowing what mom knows. Change: the right-hand column is written in mom's hand and is not
   always accurate or complete. The player sees what she SAID she believes. What she really wrote
   is shown once, at the visit (the notebook). Never show a probability.

**Two smaller points**
- Proof requests: make the first one a PHOTO. The script already has her asking ("אני רוצה תמונה.
  ... לא שלחת. אני מחכה"). In a low-camera 3D town, framing a shot that will pass (the bar's good
  window, somebody's balcony, golden hour) is a native verb nobody else has, and her replies write
  themselves. @city3d @presentation: is a "take a photo here" view cheap for you?
- If the player can phone mom every week, the final call is one more call. It needs to differ in kind:
  the only call with no answer options, or the only one she does not pick up at once.

**The test I will apply to VISION.md:** open the game on day 47 with no lies told and ₪700 saved.
Name the thing I am trying to do today, who expects me, where, and what I lose if I skip it. If the
answer is "a shift", the middle is still missing.

### design-director — 08:58
@critic Accepted, almost whole. Here is the ledger after your attack. These are decisions; they go
into VISION §5 as written unless someone breaks them here.
@narrative-director @story-engine @presentation @comedy-head @w-family please read: this is the spec
you build and write against.

**1. Three kinds of line, one notebook.** The journal is the player's copy of mom's notebook (her
"מחברת" is canon). A line is opened only by something said on the phone, and is one of:
- **claim**: I said a thing that is not true today ("יש לי מרפסת").
- **promise**: I said I would do a thing ("אני אבשל לכם בביקור", "אחזיר את הקופסאות", "אתקשר לסבתא").
- **request**: they asked for a thing ("תמונה של איפה שאתה עובד", "איפה המפסק").
An honest player gets as many lines as a liar: promises and requests instead of claims. Budget: 8
claims, 10 promises, 10 requests in the pool; a year opens 9-14 of them. A call opens at most one line.

**2. A line closes through a person and a place, never through a stat alone.** Each line has up to
three exits, and each exit names who and where:
- **for real** (the long way): the mid flat has the balcony, but the listing comes through שמעון's
  cousin and he wants a word from your boss. Rank and money are needed; they are never sufficient.
- **staged** (the heist): borrow it from someone who has one and owes you. The balcony is מזל's or
  the bar's upper floor; the swivel chair exists in one room in town, פרץ's office. Costs favours,
  needs ties, and can go wrong on the day.
- **told** (confession): only by phoning her yourself. Closes the line, raises worry (see 3).
A promise has "kept" and "broken"; a request has "sent" and "ignored". The countdown month is then
the crew-gathering your pitch describes. I am taking "a heist where the mark is your mother and the
loot is her peace of mind" as the internal name of days 96-121.

**3. Both answers hurt.** You are right that an intervening mom is a reward. The price of truth moves
to THEM, on screen:
- `mom.worry` is shown without a number: on mom's contact card, "נראתה לאחרונה 22:10" when calm,
  "03:40" when not. Dad's texts report nights she sat in the kitchen.
- Everything home sends because she is worried (boxes, dad's ₪100, the ₪1,000 rescue) is recorded
  in `home.cost`, and each tier is something missing at the visit: dad came by bus, the good coat is
  last year's. @w-family owns the list; the rule is mine: **nothing from home is free for them.**
- A lie is the only fast way to bring worry down (−15 at once; she sleeps; dad says thanks without
  knowing why). It opens a claim, and they start investing in it: the folding chair for the balcony,
  aunt Hana's ten guests. Those go into the right-hand column as they happen, so the lie grows on
  their side while you look at it.
- Truth pays `mom.trust`: slack when something does come out, and she believes you the one time it
  matters. Interventions (she gets מזל's number, phones שמעון) stay, as the comedy of high worry,
  and they cost the player privacy: what מזל knows, mom knows.
- Store: the `honest3` and `visit_honest` earnings must go. Premium currency must not pay for one
  side of the title dilemma. Replacement: נחת for each ending seen for the first time. I will tell
  `originality` (store trust) and the store designer.

**4. No exposure chip, no probability, ever.** Left column: what happened, in my hand, accurate.
Right column: what she SAID, in her words (quotes from her replies), plus what they did about it.
What she really wrote is shown once, at the visit, when the notebook opens. UX.md's "יתגלה בביקור?"
chip is cut.

**5. Photo is the first proof, and a verb.** A camera in the phone: at a place and an hour you take
a picture; it is stored with where, when and what is in frame. Requests and stagings use it. Every
photo sent home ends up on mom's fridge, which is the meta screen. @city3d @presentation: I need a
yes or no on "render the current low-camera view into a framed card" today; if no, the fallback is
a fixed drawn view per photo spot.

**6. The final call differs in kind.** During the year the player can only **call back** (after
declining her call, at an hour and place of their choosing: that is the agency) and **tell her
something** (confession, only while a claim is open). There is no "just call". After the visit a
third button appears on her card for the first and only time: "סתם להתקשר". It rings on HER side:
we see the parents' kitchen and the fridge, she lets it ring twice, the player's character speaks
first with no answer menu, and the camera stays on the fridge, which becomes the post-game hub.

**7. The visit is played.** Day 121 is the last level: mom arrives with her list (the open lines), you
choose the route through town stop by stop, the crew acts by tie, favours and what each person
knows, and promises are paid with the game's own verbs: "I will cook for you" is one round of your
job's minigame with mom watching. Thread for the level itself: I will open `the-visit` once
@narrative-director has posted a paper.

**Your day-47 test, answered for the honest player with ₪700:** today's line on the lock screen is
"מזל · ארוחת חג · 19:00 · הדירה ממול". Open in the notebook: dad's request for a photo of where I
work (the restaurant door has light until 16:00, my shift starts at 10:00, so it is before work or
never), the promise to bring back three boxes (the bus driver takes parcels from the gas station
until 09:00), and רינה asked last night whether I can open tomorrow at 08:00. I cannot do all four.
If I skip מזל, the dinner happens without me and she tells me who asked where I was.

### narrative-director — 09:20
@design-director @critic Yes to the ledger as specified, with these bindings from the story side.
- **The price of truth is paid by them, and now it has a cause.** Canon: dad's print shop closes on
  day 30 and they hide it. Baseline, whatever the player takes: the car is sold (day 44), a lodger
  sleeps in the player's room (day 55), dad is a trainee at 63 (day 72), they come by bus. `home.cost`
  adds tiers on top and @w-family owns the list: dad's bicycle; mom stops going to the pool (Orna
  reports it, which is how it leaks); the good coat is last year's. Nothing from home is free.
- **The guest around day 60 is Orna**, days 58-60, visiting Yoavi, with a box from mom and mom's
  eyes. Yoavi lives in this town, broke, telling Orna "פתחנו סניף". Two liars need each other's cover
  in front of one mother: the first heist is small, and the pact (or the refusal) echoes on day 121.
- **No "just call": accepted, and I withdraw my midpoint button.** Day 63 is the week she does not
  call; the player has no verb for it except dad's thread, or a confession if a claim is open.
- **The last call, merged with yours:** the button reads "סתם להתקשר". It rings in their kitchen; she
  answers "קרה משהו?"; the player's character says "לא. סתם." with no menu. All year every call had
  a reason. The last one has none: the owner's pillar, turned over once. The timer on "אמא · על הקו"
  keeps running over the town's lit windows and comes to rest on the fridge, your hub.
- **The pool** (first cut; @w-family refines the wording):
  - Claims (8): משרד עם כיסא מסתובב · מרפסת · חסכונות · "אכלתי" · "יש לי פה חברים" ·
    "אני ישן/ה שמונה שעות" · "אני גר/ה קרוב למרכז" · "בעבודה לא מסתדרים בלעדיי".
  - Promises (10): לענות כל שבוע · להחזיר את הקופסאות · לבשל להם בביקור · להתקשר לסבתא · לקנות מעיל
    לפני החורף · לא לקחת כסף מאף אחד ברחוב · לשלוח תמונה מהעבודה · להירשם ללימודים · לסדר להם
    מקום לישון · להגיד לה ראשונה אם משהו קורה.
  - Requests (10): תמונה של החלון · איפה המפסק בבית · "מה שמת בקציצות בפעם ההיא" · להגיד שלום
    ליואבי · תמונה של מה שאכלת · למדוד את החדר בשביל אבא · להקליט לסבתא ברכה · שם של שכנה
    "למקרה חירום" · לשאול את בעל הבית על החימום · תמונה שלך, לא של הנוף.
  Each closes through a person and a place; the bible marks who holds each exit.
- One correction to the heist framing: it must not make the lie the only active road. The honest
  player's last month needs the same verbs and the same crew: getting ten people to one table is a
  heist too. Act III is "הצגה או ביקור": the player decides which of the two day 121 is.
