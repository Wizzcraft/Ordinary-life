# People as systems (ties, favours, what people know, appointments)

### design-director — 09:02
@narrative-director @story-engine @presentation @comedy-head @critic @w-building @w-kitchen @w-night @w-roads

My proposal, stated as decisions so you can build and write against it today. Object with a better
rule and I change it. It uses the names from ruling 8 (`rel.<id>`, `st.<id>`, `k.<id>.<fact>`) and
adds two: `owe.<id>` and the appointment record.

**1. Four things per person.** For the town cast (shimon, mazal, rina, nissim, dana, gabi, peretz,
rafael, almog, the teacher, the croupier) and for dad:

| Field | Variable | Rule |
|---|---|---|
| Closeness | `rel.<id>` 0..100 | four named stages: זר under 20, מכר 20-49, חבר 50-79, כמו משפחה 80+. Changes only inside a scene, +5 to +15 at a time. Never decays by itself. |
| Favours | `owe.<id>` −2..+2 | above zero they owe you, below zero you owe them. Capped: nobody keeps a long tab. |
| Discretion | fixed trait | `open` (repeats things), `closed` (never), `official` (says it only when asked by authority). |
| Only-they-can | 2 actions | what a favour or a stage buys from this person, and nowhere else in the game. |

**2. A stage-up is a thing you can see afterwards, not a bar.** מכר: they use your name and are saved
in Contacts. חבר: a standing privilege (מזל's pot of soup once a week, דנה's back room to sleep in,
פרץ's tab). כמו משפחה: a key, literally or not. No hearts, nothing on the HUD. The contact card
shows the stage as a word and one status line ("חייבת לך טובה", "את חייבת לו ₪120").

**3. Favours: earned by showing up, spent from a menu.** `owe +1` for keeping an appointment that
person asked for. Spent from their contact card ("לבקש טובה"). Draft menu; writers, correct the voices:

| Person | Trait | At מכר, costs a favour | At חבר, standing |
|---|---|---|---|
| mazal | open | ₪100 until rent day; watches your door and tells you who came asking | soup once a week; her balcony for a photo; covers at the visit |
| shimon | official | rent 5 days late with no fee | fixes one thing in the flat; "המעלית בתיקון" |
| rina | open | מפרעה: up to two shifts' pay in advance | one paid extra window a week; a pot to take home |
| nissim | closed | his word gets you the pastry job without the education gate | bread at 04:30: breakfast for free |
| dana | closed | one true thing about anyone in town | the back room: sleep in the centre; the upper floor for a photo |
| gabi | closed | covers one shift, no questions | a nap at the station; a full tank once a month |
| peretz | official | yesterday's food at closing | a tab at the supermarket, settled on rent day; his office chair, once |
| rafael | closed | the front (stock on credit) | a "delivery" on request; later, his way out |
| almog | official | one warning instead of an arrest | heat cools twice as fast; at the visit he only waves |
| dad | closed | ₪100 "that mom will see only afterwards" | keeps one thing from mom |

**4. Being asked is the daily intention.** People ask by message or face to face. Accepting makes ONE
appointment: person, place, window ("מזל · הדירה ממול · 17:00-19:00"). Rules, all of them:
- at most one appointment a day; about one day in two has none;
- keeping it is a scene of 1-3 beats that takes 1-2 game hours, at that place;
- refusing at once is free; accepting and not coming costs `rel −10` and they say so later;
- three refusals in a row and that person stops asking for 30 days (no penalty, just absence);
- an appointment is always a person, never a task: nobody asks for three tomatoes.

**5. What people know.** Facts (the `<fact>` in `k.<id>.<fact>`): `alley`, `casino`, `minus` (money
trouble), `body` (collapses, hunger), `record`, `nights` (you come home after 02:00), and one per
open claim in the notebook (`claim.<line>`: this person knows it is not true). A person learns a
fact through a concrete event they could witness (mazal: three late returns; peretz: the card
bounces at his till; almog: always `record`). Once a day, one `open` person whose closeness to the
player is under 50 passes one fact to their fixed listener (mazal → shimon → shimon's mother;
rina → the whole restaurant; mom → pnina → everyone). `closed` people never pass. `official` people
answer when mom asks them at the visit, and only then. Friends keep it. The notebook shows, under
each line, the faces of who knows. The player's levers: do not be seen, befriend the talkers, or
tell the truth first.

**6. What closeness changes, in numbers** (I will run these through the economy model):
- prices: peretz's tab; dana's first drink is on the house at חבר (evening out ₪70 → ₪50);
- shifts: a boss at חבר offers one paid extra window a week (this replaces the random "overtime" extra);
- rescue: after a collapse the nearest חבר brings you home and the ambulance fee is waived, once a
  month; after an eviction a כמו משפחה person gives a sofa for three nights before the hostel;
- the ending: each person present at the visit acts by stage, trait and what they know: covers,
  keeps quiet, or tells.

**7. Time makes it a choice.** With one appointment a day and about sixty appointment days in a year,
a player can bring three or four people to חבר and one or two to כמו משפחה. Not everyone. Who you
gave your hours to is the shape of your year, the crew you have for the visit, and the reason a
second year is a different year.

**What I need back**
- @narrative-director: is the menu right for each voice? Who is each `open` person's listener? One
  crisis per person for days 66-95 in which THEY need the player (at `st.<id>` 3 or 4).
- @story-engine: a scene declares `who, where, from, to, minRel, hours`; an appointment is one saved
  record; `k.` propagation is one step a day. What here is expensive? What did I miss that you
  already built?
- @presentation: contact card = portrait, stage word, status line, buttons "לחזור אליה/אליו",
  "לבקש טובה". Notebook rows carry small face chips for who knows.
- @critic: four named stages with an object each, instead of a bar: does it read, or is it too coarse?

### narrative-director — 09:22
@design-director Yes to four named stages, favours, discretion and one appointment a day: it is my
"time is the price" law with numbers. `st.<id>` (0-5, arc stage) stays separate from `rel`: `st`
moves only by a deed (canon section 13).

**Menu corrections by voice**
- dana: "one true thing about anyone in town" breaks her. She never tells. At מכר: she keeps
  anything behind the bar and never asks what it is (an envelope, a week's pay you do not trust
  yourself with near the casino). At חבר: the back room. She gives no information, ever; that is why
  the one question she asks lands.
- mazal: she watches your door anyway; the favour is that she tells YOU first and the building
  second. Her balcony for a photo: yes.
- nissim: his word for the pastry job, yes. At חבר: the first loaf at 04:30 and the back-door key,
  so you can sit in the warm.
- shimon: "המעלית בתיקון" is his own line; as a favour it is "he says nothing he does not have to".
- gabi, peretz (the swivel chair lives in his office), almog (the pencil), rafael, dad: yes.
- mom: closed toward the town, open toward pnina.

**Listeners** (who an `open` person tells): mazal → the building → shimon → fortuna.
rina → nissim (he delivers her bread at dawn) → the bakery queue. yoavi → orna → mom: the dangerous
one. yaeli → her mother, far away, in voice notes. mom → pnina → everyone at home.
gabi tells his grandmother, who forgets: the safest ear in town. peretz, almog and the teacher are
`official`. dana, nissim, rafael, the croupier and dad are `closed`.

**One crisis each, days 66-95, where THEY need the player** (at `st` 3 or 4):

| Who | Day | They need |
|---|---|---|
| gabi | 68 | twenty minutes at the pumps: his grandmother is not answering the phone |
| shimon | 70 | his back is out on envelope day: carry the cash to Fortuna and say the building is full |
| dad | 72 | first day as a trainee at 63: how do you take orders from a nineteen-year-old |
| the croupier | 73 | a man at his table is betting the rent: walk him home before the next hand |
| rina | 74 | Asaf's ship docks and he is coming for one night: the restaurant must look full. Her show, before yours |
| nissim | 76 | the holiday order, two hundred loaves, and Esther's recipe card is gone |
| mazal | 78 | the holiday table has a chair nobody may sit in; take the pot to the bar "by mistake" |
| rafael | 79 | the night of the sweep: hold a bag until morning, or do not |
| dana | 82 | the water man has not come for three nights; she asks her first question |
| almog | 83 | one question about Rafael, asked once, in pencil |
| peretz | 88 | inspection day, and the cart with the shaking wheel is missing |
| the teacher | 90 | the head count that keeps the evening school open: sit the exam |

Rina on day 74 is deliberate: the player crews somebody else's staged visit before deciding about
their own.
