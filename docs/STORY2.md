# CANON v1 — binding for the writers' room (narrative-director, 09:10)

Everything here is decided. Propose changes in `room/story/<role>.md`; until I answer, this stands.
Structure in English, in-world in Hebrew. Ids and variables follow ruling 8. The full bible is
`docs/STORY2.md` on my branch (in progress); this file is the part you need today.

## 1. Theme and the scene test
**"הכל בסדר" is the most loving lie and the loneliest one.** Everyone here has someone on the line
to whom they report an edited life, so that person will not worry; every edit locks that person out.
The year asks: who do you let see the life you actually have?
Every scene must answer three questions, or it is a gag and gets loaded or cut:
1. Who is on whose line here? 2. What is said instead of the true thing?
3. What does saying it (or not) cost in time, money or closeness?

## 2. Four laws
- **The line.** Every recurring person has ONE person on their line (below). Their catchphrase is the
  scar of that relationship. Never explain it in the scene; let the player add it up.
- **Reciprocity.** Before day 90 every major person asks the player to carry something for THEIR
  line (a lie, a truth, an envelope, a pot). On day 121 they answer in kind, for their own reasons.
- **Time is the price.** Showing up costs hours the player could have worked. The big relationship
  steps are deeds at a place and hour (a promise kept), never the nicest of three answers.
- **The echo.** Every choice sets a fact that a named later scene reads. No echo, no choice:
  make it a plain card. No stat-only rewards. No trap answers: each of the 2-3 answers is something
  a decent person could say, and each reveals who the player is.

## 3. The protagonist (player-named, player-gendered, no backstory)
- Three plates at the Friday table. Never mention siblings, school, army, exes or a home town name.
- First choice of the game, at the dinner: why are you leaving? `why` = 1 "להוכיח שאני יכול/ה",
  2 "לנשום קצת", 3 "להרוויח משלי". Mom quotes it back on day 121. Use it sparingly before then.
- Answers come in three manners; offer at least two of them in any choice:
  **כן** (says the true thing, exposed) `me.open`, **מתחמק** (a joke, a shrug) `me.guard`,
  **מציג** (the impressive version) `me.show`. They gate nothing. People describe the player by them
  ("את תמיד צוחקת כשזה נהיה רציני").
- Lies to mom are four claims: `lie.job` (משרד, כיסא מסתובב, מנהל/ת), `lie.money` (חסכונות),
  `lie.home` (מרפסת, נוף), and new `lie.ok` (אכלתי, ישנתי, יש לי פה חברים).
- A lie needs upkeep: later calls ask for a detail of the claim (what colour is the chair?).
  The truth needs no memory. That is the design; write the detail questions.
- Promises: `prom.<id>` 1 made, 2 kept, 3 broken. A promise names a place and a deadline.

## 4. The family (w-family)
**אמא, דליה, 61.** School secretary for thirty years: she kept the attendance diary and read every
forged note, which is why she hears a lie in one syllable and why she keeps המחברת.
- She calls ONCE a week, from the bus stop outside סבתא רחל's building, on her way home from her
  weekly day with her own mother. After a day of being a daughter she wants two minutes of being a
  mother. The call lasts as long as the wait: every call ends on the bus ("הנה האוטובוס שלי").
  That is the in-world reason for "weekly" and for "two cards".
- Why once, why short: when she left home סבתא רחל phoned five times a day, and דליה stopped
  answering. Three quiet years. She swore her child would never have a reason to let it ring.
  Her comedy is restraint: what she is visibly not asking. No nagging, no guilt arias.
- "אכלת?" is the one question she allows herself. It means everything else.
- Her arc: early in the year a hard truth makes her overreact (sends dad's money, rings the
  landlord): honesty is punished and the player learns why children lie. If the player keeps
  telling her true things, she learns to answer "טוב. תודה שאמרת." A liar never meets that mother.
- The notebook is not a ledger of promises and never a threat. It is where she puts what she did not
  ask. It appears on day 121 only: the player's own year, call by call, from her side.
- The boxes: every box must come back, because a box is a visit owed (same reason as Mazal's pot).
- Her lie: "גם אצלנו הכל בסדר".

**אבא, צביקה, 63.** Thirty-four years at a print shop (הדפוס): hand typesetter, then shift foreman,
"אחראי משמרת", on his feet the whole time.
- Why his texts are short, with no exclamation marks, signed "אבא": at the press every character cost
  work, a headline never shouts, and a piece without a credit is not finished. And he owns one
  postcard from his own father, the only thing in that handwriting, signed "אבא". He signs so the
  player will have many. (Discoverable late, by asking. Never state it early.)
- One word per call (mom relays it). In text he is the family's leak: he reports what mom really did.
- At 22 he tried the city himself and went back after eight months. He never says so. It is why he
  packs a torch (he remembers a stairwell), why he wants the player to stay, and why "זאת עונה,
  העיר תחכה" is his line to give.
- The swivel chair: he stood for 34 years; a chair that turns is his picture of a child who made it.
  `lie.job` lands exactly there.
- His lie: he says nothing about himself.

**Their year (the player is not told; each item leaks only as marked):**
| Day | At home | How it can leak |
|---|---|---|
| 14 / 22 | The milk gag, exactly ruling 3: call 2 mom says dad went out for milk; day 22 dad texts. | Two messages. Never referenced again. It sits BEFORE day 30 on purpose. |
| 30 | The print shop closes. Dad is home. They agree not to tell, so the child will not come back. | Dad's texts start arriving mid-morning on work days. |
| 35 | Call 5: dad is sorting every drawer in the house by size. | "למה הוא בבית?" gets "ימי חופש. הרבה." |
| 44 | They sell the car. | "אבא הולך ברגל. הוא אומר שזה בריא." |
| 55 | A lodger, אביתר, a student, moves into the player's room. Dad's text the night before: mom stood in the room ten minutes without turning on the light. | Call 8: a voice asks where the towels are. "הטלוויזיה." |
| 63 | **Mom does not call.** סבתא asked her when the child last phoned HER. Never. She waits. | The phone offers "חייג לאמא". It rings out. Next day dad reports what happened on her side. |
| 68 | סבתא רחל's voice message (transcribed, she sings in minute three): she knows about the three years. | One card. "תענה לה." |
| 72 | Dad starts as a trainee shelf-stacker at the supermarket back home, trained by a 19-year-old. | He tells only a player who told him the truth about their own job. To "a manager" he cannot. |
| 77 | Holiday. Three plates; who sits at the third? | Mom confesses the lodger only to an open player. |
| 84 / 91 | She floats the visit; she buys tickets. Bus tickets. | "כרטיסים? ומה עם האוטו?" |
| 98-112 | Preparation calls. Confession windows. Detail questions about every standing claim. | |
| 119 | The shortest call of the year. | |
| 121-122 | The visit; the bus stop in the morning. | Their truths surface only where the player made room. |

**Disclosure law: truth is traded.** A parental truth is told only to a player who has given one
(`fam.asked` counts the times the player asked about THEM; open answers count). To "הכל בסדר" the
answer is always "גם אצלנו הכל בסדר."
Calls: days 7, 14, 21, 28, 35, 42, 49, 56, (63 silent), 70, 77, 84, 91, 98, 105, 112, 119.
Sixteen calls and one silence, each AUTHORED for its day with 3-5 state variants. The random pool
of 110 is retired; its best jokes are raw material. Dad texts about every 4-5 days, never on a call
day. Never more than one unsolicited family moment a day.
Offstage: **savta** (mom's mother, 91, sharp); **orna** and **yoavi** stay, because Yoavi lives in
THIS town, broke, telling Orna "פתחנו סניף" (a regular: w-strangers writes him with w-family);
**pnina** is how news reaches mom (she is mom's Mazal); **hana**: two mentions in the year, at most.
Objects that pay off on day 121: the torch, the blue box, the coat, the room, the swivel chair, the
notebook. Do not spend them early.

## 5. The building (w-building)
**shimon, 64.** He and his younger brother inherited the building; he bought the brother out with a
bank loan. "אני לא בנק" is literal: the bank is the one that calls HIM. "הנכס" was his father's word.
- Rent every ten days, in cash: his mother's carer is paid every ten days in cash, and rent day is
  the day he brings the envelope ("אני בדרך לאמא שלי").
- On his line: his mother **פורטונה** (`fortuna`), 90. He tells her the building is full, the lift
  works and his brother "מנהל מלון". She knows better.
- The brother is the croupier (w-roads names him). They have not spoken since the loan.
- A developer has offered for the building. He must answer by day 110. Default: he sells.
- His ask: once, when he cannot go, the player carries the envelope.
**mazal, 70,** widow. Dana of the bar is her daughter.
- "אני לא מתערבת, רק אומרת" is a vow. Nineteen years ago she walked into Dana's first workplace to
  tell the manager her girl was being worked too hard; Dana was let go that week and moved out.
  They meet twice a year at the father's grave and say "מה נשמע" / "בסדר". Fifteen minutes' walk apart.
- The calendar on her wall began as days since Dana last phoned. שוקו was Dana's cat. The soup is
  too much because she still cooks for three. "תחזיר את הסיר, ככה אני מכירה אותם פעם שנייה" stays.
- She listens to the player's calls through the wall because it is the nearest thing she has to a
  daughter phoning home. She will never say so.
- Her ask is never spoken: news of Dana. The player becomes the line between them, or refuses to.
Also yours: the stairwell light that lasts twenty seconds, the lift "בתיקון" for twenty years, the
boiler on the roof, the neighbour upstairs who moves furniture at 2am (a small arc), the homes and
the hostel (numbers from DESIGN.md). Yaeli has the top bunk in the hostel.

## 6. The kitchens (w-kitchen)
**rina, 56.** Her father's restaurant.
- "מאמי" is her name for her son **אסף** (`asaf`), a cook on a cruise ship who phones on Fridays
  from whatever port ("אין קליטה בים"). She tells him the place is full every night so he will not
  come home to rescue it. She calls every young worker מאמי; using your NAME is the promotion.
- She shouts because thirty years beside the extractor took the hearing in her left ear. She counts
  in minutes because two lunch sittings pay the lease. She cries in the walk-in because it is the
  only door that closes ("ובקור לא נמרח"). She feeds every worker so that somebody feeds hers.
- Her last shift manager left with her recipes for the chain place across the road. She does not
  hand over keys. Each promotion is one thing she lets go of. "אין לי עותק" is the payoff, keep it.
- Her ask (mid-year, in the walk-in): say hello to Asaf as "the new shift manager".
**nissim, 71.** Baker. **אסתר** (`ester`) died three years ago. She did the talking; he baked.
- He quotes her because he has no sentences of his own, and lately he invents what she "used to say".
  Payoff, once, late: "אני אומר."
- 3am because bread must be on the shelf at six, and because that is the hour he cannot sleep.
- His ask: taste the sweet challah and tell him if it is hers. It is not.
Co-workers: Yaeli waits tables at Rina's in the first weeks; the dishwasher who has been there
22 years (name him); the place across the road and its people. Jobs: cook, waiter, baker,
dishwasher, pancake cook, pastry decorator: boss lines by `st`, rank lines, promotions.

## 7. The night (w-night)
**dana, 43.** Mazal's daughter. "לא שאלתי": she built the one room in town where nobody is asked
anything, and she knows everything by listening. Rule of the bar: do not tell them your story.
- The man who orders water and tips for wine drives her drunks home; that is why it is the safest
  bar in town (a regular, w-strangers).
- She never asks. Her turn in the story is the first question she asks ("מאיפה הסיר הזה?").
**gabi, 31.** Raised by his grandmother, who is starting to lose the thread. He works nights to be
with her by day. His 3am questions are the ones she asks him. He tells her he is the station
manager. He was accepted to study and came back after one term; the evening school is his unfinished
thing. His ask: watch the pumps twenty minutes; she is not answering the phone.
**peretz, 58.** The clauses are his own, not head office's; lamination is how he keeps one thing in
his life from fraying. He once followed procedure on an old woman over a tin of tuna and never saw
her again; "מסתכל על התקרה" is the loophole he built so he can stay a man of rules. On his line:
head office, and an inspection day that he fears and wants. He counts carts because each one missing
comes off the branch.
The exterminator's boss is yours to invent on the same pattern (line, lie, reason, ask).

## 8. The roads (w-roads)
**rafael, 39.** A middleman who owes the man above him; "אני אף פעם לא לוחץ" because the debt does
the pressing. He came here at twenty and did it the hard way for two years. His mother believes he
is an accountant (the blue tie is real). He is what the player becomes if the lie comes true the
wrong way. Three exits: taken in, gone in the night (one last favour: post an envelope to his
mother), or the player takes the corner and says HIS first line to the next newcomer.
**almog, 39.** Same school class as Rafael. First warnings in pencil. He loses paperwork for
first-timers, which could cost him the job; one he let go did worse, and that is his wound. He reads
bedtime stories to his two daughters by phone from the parked patrol car. His ask: one question
about Rafael, once.
**the teacher** taught both of them and remembers where each sat. The evening school closes unless
enough finish this year; she pads the attendance sheet. Name, voice and id are yours.
**the croupier** is Shimon's younger brother, a gambler who now deals: "מהצד הזה של השולחן אני
לא מפסיד". The table limit is his own rule. He never wishes anyone luck. Name and id are yours.
Professions are mirrors of the theme, each about the edited version: the doctor (what to tell the
family), the lawyer (which truth to present), the developer (the demo and the product).

## 9. Yaeli (`yaeli`) — spine is mine; each beat is written by the slice where it happens
She got off the bus with the player on day 1. She sends her mother a voice note every day, and every
one is "מדהים". Her road runs whether the player looks or not.
1. Day 1, the bus: she eats her sandwich exactly the way mom warned about. One bag.
2. Days 6-9: trainee waitress at Rina's; top bunk at the hostel.
3. Days 22-26: asks to borrow ₪150 until day 40.
4. Days 38-42: repays in new notes, in a new jacket. She is carrying for Rafael.
5. Days 58-62: asks the player to appear in a voice note as "החבר/ה שלי, מנהל/ת".
6. Day 78-79, holiday night, the sweep: she is at the player's door or back door.
7. Day 80-81: the 05:40 bus home. Always. Her mother heard the truth and said "בואי". What differs is
   whether the player carried the bag, came late, or was not there. It is a season, not a failure.
8. Days 100-110: a voice note. "זה לא מה שסיפרתי. זה יותר טוב."
9. Day 121, if `st.yaeli` >= 4: she is on the parents' bus. Ninety minutes next to mom.

## 10. Strangers and regulars (w-strangers)
A regular has a handle until the player earns the name ("האיש של המים"). One face across every job
and the street. Three to five parts; each part has a staging per venue plus a street fallback, so it
works whatever the player's job. Each remembers one thing: how they were treated (`rel.r_<id>`).
The eight to write first: **yoavi**; **ורד, the woman who rehearses a phone call to her son**;
**the water man**; **ליאור, the delivery boy who carries for both Rina and Rafael**; **the old man
who escapes the home every week**; **the man who complains everywhere and keeps a list**; **the man
with the notebook who collects one overheard sentence a day**; **the woman who shops for two
households and is always ₪4 short**. Sixteen more, shorter, are yours to propose.

## 11. The year
| Ch | Days | Title | Tentpoles |
|---|---|---|---|
| 1 | 1-10 | מפתח | Dinner, bus, key, torch in the stairwell, peephole, first night, first shift, call 1, first rent |
| 2 | 11-20 | מרק | The pot, the cat promise, first regulars, call 2 (milk), Rina goes into the walk-in |
| 3 | 21-30 | כיסא מסתובב | Dad's milk text; "מה להגיד לאמא?"; Yaeli's loan; day 30: what you said travels |
| 4 | 31-40 | הדרך הקצרה | First rain, the coat on the chair; Rafael; Almog's hello; the croupier's limit; call 5 |
| 5 | 41-50 | דלי | Winter, the leak, school opens, Gabi's night, the walk-in scene |
| 6 | 51-60 | תשעים | Fortuna's ninetieth; the cat in Shimon's flat; the room; call 8 "הטלוויזיה" |
| 7 | 61-70 | קו תפוס | Day 63, no call; savta's message; the pot reaches Dana; Almog's one question |
| 8 | 71-80 | חג | The holiday table and the empty chair; Rafael's big one; the sweep |
| 9 | 81-90 | ספסל | The 05:40 bus. The quiet chapter. Dad's truth for those who earned it |
| 10 | 91-100 | כרטיסים | "היא קנתה כרטיסים". Everyone hears. Show or visit |
| 11 | 101-110 | חזרה גנרלית | Rafael's exit, Shimon's answer, mother and daughter, the exam |
| 12 | 111-120 | ערב לפני | Countdown. Days 118-120 nearly silent. Call 17. The roof |
| | 121-122 | הביקור | Bus stop, the walk, the street, the stairwell, the flat, the shift, the table, the roof with dad, the morning bus, the call |

## 12. Scene rules
- A card holds about 220 characters. Answers: 2-3, about 18 characters. A scene is 1-5 cards.
- Both genders for every line that addresses or describes the player. Markup: as story-engine rules.
- **No joke after a true thing.** The card after someone says what they mean has no punchline.
  Mercy may come one card later, from the other person. The no-joke zones agreed with comedy-head
  (dad's quiet texts, Nissim's wife, Almog's daughters, Fortuna, the hostel, a confession, the last
  ten days) are funny around, never about.
- Pacing: Act I at most one story moment a day; Act II two days in three; Act III one a day, and
  days 118-120 almost none. After a heavy scene, nothing more that day and no gag next morning.
  One-line bubbles inside shifts do not count. Tentpoles land on the chapter's last three days.
- Every scene names: trigger (place, hours, stage, cooldown), expiry and what the world does if the
  player never comes, the facts it sets, and its echo (the later scene that reads them).

## 13. Variables and ids
Ruling 8: `rel.<id>` 0-100, `st.<id>` 0-5, `k.<id>.<fact>`, `lie.*`, `mom.trust`, `mom.worry`,
scene ids `<slice>.<person>.<nn>`. Added: people `yaeli`, `fortuna`, `asaf`, `ester`, `lodger`;
regulars `r_<name>`; `me.open`, `me.guard`, `me.show`; `why`; `prom.<id>`; `claim.<topic>`;
`fam.asked`; `fam.told.<fact>` (car, press, lodger, job, city22, postcard, years3).
`st` moves by deeds only. Stage meanings for every person: 0 stranger, 1 a face, 2 they have asked
something of you, 3 you carried it (or refused out loud), 4 you know their reason, 5 they stand
with you on day 121.

## 14. Done means
A scene is done when it (1) passes the scene test, (2) has both genders, (3) fits the limits,
(4) has no trap answer and no stat-only reward, (5) names its echo, (6) names trigger, expiry and
default, (7) obeys the no-joke law, (8) sounds like a person when read aloud, (9) borrows nothing,
(10) is entered in `room/story/LEDGER.md` (fact | set by | read by | plant day | payoff day).
First delivery from each writer: dossiers for your people in the bible's format (want, need, wound,
lie, on their line, reason per habit, stages 0-5 with the deed that moves each, who they know and
what they say about each other, speech rules plus five lines, part in day 121), then a scene list
with days. Budgets are in the bible, section 13; I post them within the hour.
