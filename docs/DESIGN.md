# אמא על הקו: systems and economy

Every number lives in `core/Tuning.java`. `tools/balance/model.py` reads that file directly, so a
change there changes the model too. Run `python3 tools/balance/model.py` (200 runs per strategy,
a few minutes; `--only trader --verbose` prints one run day by day).

## 1. The problem and the target

Today a bot that works every day ends month one with about ₪3,500 after rent. Three things cause it:
the employer feeds you **by the hour** (food and water go *up* during a shift), tap water is free, and
₪30/h × 8 h is far more than a ₪900 rent needs. Nothing costs anything, so no week is a decision.

The target curve, and what the model shows (medians of 200 runs, cash after that month's rent):

| Target | Model result |
|---|---|
| Honest unskilled newcomer only just makes the first rent | `unskilled` ends month 1 with **₪155**; 8% of runs miss the first rent |
| Competent worker saves ₪300–600 a month early on | `steady` saves 311 / 192 / −132 (bike, wedding) / 500 / 691 in months 1–5 |
| Month 4: mid flat **or** school, not both | `steady` has ₪1,129 at month 4 after buying the bike (~₪1,900 without it). Moving costs ₪1,450 up front; the first half of developer school costs ₪1,200 |
| Professions pay 3–4× an entry job by month 8 | developer at month 8 earns **3.7×** the cook's rank-0 hourly rate (3.1× on day one; ranks do the rest) |
| Crime ~3× faster, about 1 arrest in 3 months for a greedy player | greedy alley: **2.2×** per hour counting the wait for 22:00, about 10× per hour spent in the alley, **~2×** per month. Arrests: 0.27 per active month (21% of all months) |
| The casino loses on average | gambler: casino net **−₪3,644** median over the year (mean −1,987); 22% end the year ahead |
| Sinks all year | rent rises in month 7, weddings in months 3/7/10, income tax for professionals, monthly bills, mishaps, the parents' visit |

## 2. Core loops

- **Day** (72 s): sleep → travel → one shift → shop → travel → sleep. From the far flat on foot this leaves
  about 2 free hours. School, the alley, the casino and evenings out all come out of that time.
- **Month** (10 days): rent + bills on days 11, 21, … **Year**: ranks (months 1–4) → flat, school or vehicle
  (month 4) → profession or barman (months 6–8) → the parents' visit (month 12), with sinks all the way.

## 3. Time and needs

| Constant | Value | Why |
|---|---|---|
| Hunger per hour: idle/walk/work/sleep/ride/study | 5 / 5 / 4 / 2 / 4 / 4 | about 1.3 bag meals a day for a worker: a real ₪20–25 daily cost |
| Thirst per hour | 6 / 8 / 5 / 2 / 5 / 5 | walking is thirsty, so long commutes cost bottles |
| Tiredness per hour | idle 3, walk 6, ride 2, study 5; on shift per job 4–7 | a full far-flat shift day uses about 85 energy |
| Sleep restores per hour | far 10, mid 12, central 14, hostel 9 | far flat: 9 h of sleep for a work day. Central: 7 h. That is the flat's real value |
| Staff meal per shift | +30 food, +40 water, once | **replaces the per-hour feeding**, the main cause of today's surplus |
| Meal / bottle / tap / dine out | +45 / +50 / +40 / +70 food +20 water | the tap is only at home (and in the hostel) |
| Starving multiplier | ×3 tiredness | as today |
| Fitness | −0.25% tiredness per point | 100 fitness = 25% less tiring days |
| Tired below 30 energy | +0.3 difficulty | as today, handled by the host |
| Minimum energy to start a shift | 40 | a shift costs 32–56 |
| Collapse | ₪250 ambulance (becomes debt if unpaid), wake at 08:00 with 50 | it hurts but cannot spiral (§12) |

**No fourth need.** A mood meter would have nothing to do in the model: every strategy already
trades food, sleep and money, and the 360-wide HUD has room for three meters (pillar 4). What mood
would add is better done as an **evening out**: ₪70 and 3 hours at the bar, +2.5 charisma, and it
feeds you. Charisma unlocks barman and lawyer and raises tips, so going out is a career move, not
a chore. `MOOD_NEED = false`.

## 4. Work

All shifts are 8 game hours (the `Mini` contract). Pay per shift = hourly × rank × 8 × the average
round multiplier. XP per shift = 4 + 8 × average round score (0..2), +0.5% per education point.

| Job | ₪/h r0 | Start window | Needs | Trains | Energy/h | Tips | Why |
|---|---|---|---|---|---|---|---|
| Cook | 19 | 08:00–16:00 | none | fitness | 6 | no | the baseline and the reference "entry job" |
| Waiter | 17 | 10:00–16:00 | cha 15 | charisma | 7 | yes | below the cook on paper, ahead of him with charisma 40+ |
| Baker | 21 | 04:30–06:00 | fit 15 | fitness | 7 | no | good pay, but from the far flat it means a 02:15 alarm and no bus |
| Barman | 22 | 19:00–21:00 | cha 30 | charisma | 6 | yes | top non-degree pay with high charisma; the night shift leaves days free for school |
| Gas attendant | 16 | any hour | none | fitness | 7 | no | always available, 1.5 h from the far flat: the fallback |
| Cashier | 18 | 07:00–14:00 | none | charisma | 4 | no | easy on energy: the one to combine with studying |
| Dishwasher (restaurant) | 15 | 08:00–18:00 | none | fitness | 6 | no | the lowest rung: wide window, no questions asked, the safety net after a bad month |
| Exterminator (night call; van at the gas station) | 24 | 21:00–23:00 | fit 25 | fitness | 6 | no | the best untipped night rate: competes with the alley for the same hours, honestly |
| Pancake cook (restaurant, breakfast) | 20 | 05:30–07:00 | fit 10 | charisma | 6 | no | a cook's pay +1 for an early alarm; frees the afternoon for school |
| Pastry decorator (bakery) | 22 | 08:00–12:00 | edu 25 | charisma | 4 | no | the reward for a few evening courses: good pay, easy on energy |

| Constant | Value | Why |
|---|---|---|
| MISS / OK / PERFECT multiplier | 0.4 / 1.0 / 1.3 | skill 0 ≈ 0.68 of OK pay, skill 0.6 ≈ 1.01, skill 1 ≈ 1.22: skill matters, but less than showing up |
| Rank pay | ×1.0 / ×1.2 / ×1.4 | rank 2 is the cook's ceiling (₪26.6/h); beyond that you need tips, a better job or a degree |
| Rank XP | 0 / 100 / 350 | competent: rank 1 on day 7, rank 2 around day 23. Unskilled: day 10 and day 33. Ranks are why months 2–4 get easier |
| Tips | (0.10 + 0.004 × cha) of base pay × round multiplier | cha 80 gives +42%: a waiter at cha 60 out-earns a cook |
| Stat gain per shift | 1.2 × (1 − stat/120) | the first 50 points take about 50 shifts, the last 30 much longer |
| Job switching | XP is kept per job (`xp.<id>`) | switching costs only starting at rank 0 at the new place |

### Optional extras

Each job has **one core minigame that earns the wage**. Extras (a regular's special order, a spill
to clean, overtime) are optional. They pay in eight currencies, each with a shekel value
(`X_VALUE`) so they can be balanced against each other:

| Extra | Chance/shift | Amount | ₪ value | Reasoning |
|---|---|---|---|---|
| Cash tip | 50% | ₪8 | 8 | the plain one |
| Leave early | 10% | 60 min | 15 | an hour of the player's time ≈ what a shift pays per hour, commute included |
| Free meal | 20% | 1 meal | 18 | the bag price |
| Energy (coffee from the boss) | 10% | +10 | 15 | an hour of far-flat sleep |
| Promotion XP | 40% | +5 XP | 10 | rank 1 (+20% pay) a little sooner |
| Favour owed by a character | 3% | 1 | 100 | redeem later: cover a missed shift, or ₪100 of rent grace |
| Mom's trust | 10% | +1 | 10 | story currency |
| Premium currency | on a rank-up reached through extras | 1 | 0 | cosmetic only, so it can never buy progress |
| Overtime | 20% (energy ≥ 50) | +2 h at ×1.5 | ≈ 0 net | ₪57 now, paid for in energy and time: worth it right before rent, not every day |

Sizing, from a paired run (`steady` vs `steady+x`, same seeds, §14). (1) A player who skips every
extra still never misses a rent, ranks on schedule and saves ₪200–700 a month. (2) A player who
takes every extra gets **6–12% of the month's core pay** (`X_MONTH_BAND_*`). The model's median is
**7.6%** (p10 5.3%, p90 10.2%), about ₪1,900 of value a year: +₪335 of cash at month 4, +₪670
by year end, the same ranks and no rent difference. You feel it, but it never decides anything. (3) One
day's extras are capped at 25% of that shift's pay (`X_DAY_CAP`), so a lucky day cannot spike.

## 5. Stats

| Stat | Grows by | Gates | Improves |
|---|---|---|---|
| Fitness (start 10) | fitness jobs, 0.08/h walking, 0.2/h cycling | baker 15, exterminator 25, doctor 40 | −25% tiredness at 100, +0.2% per point to escape a chase |
| Charisma (start 10) | charisma jobs, evening out +2.5 | waiter 15, barman 30, lawyer 30 | tips, +0.1% per point on alley prices |
| Education (start 15) | evening course ₪50/2 h/+2.5 (17:00–20:00), degree sessions | pastry 25, developer 30, lawyer 40, doctor 50 | +0.5% job XP per point |

## 6. Homes

| Tier | Rent | Deposit | Bills | Sleep/h | Walk to centre | Listing appears | Landlord wants |
|---|---|---|---|---|---|---|---|
| Far (today's) | 900 | 0 (mom paid) | 100 | 10 | 135 min | start | — |
| Mid | 1,200 | 1,200 | 150 | 12 | 80 min | month 3 | any job at rank 1 |
| Central | 2,400 | 2,400 | 220 | 14 | 30 min | month 6 | a profession |

Moving costs ₪250; all rents rise 10% from month 7. The mid flat costs about ₪350 a month more and
saves about 2 hours a day plus an hour of sleep. For a plain cook that roughly cancels out; for
anyone who uses the time it pays. The central flat is a professional's reward and a professional's
sink. Proposed spots: mid flat in the empty block west of the gas station, central flat near the
restaurant.

## 7. Transport

| Mode | Price | Speed | Per trip | Upkeep/month | Notes |
|---|---|---|---|---|---|
| On foot | — | ×1 | — | — | trains fitness |
| Bus | — | ×3 | ₪6 | — | +15 min average wait, 06:00–24:00 only: useless for baker and barman hours |
| Bicycle | ₪800 once | ×2 | — | ₪30 | any hour, as tiring as walking, trains fitness (0.2/h) |
| Scooter | ₪3,000 once | ×4 | ₪5 fuel (₪50 tank = 10 trips, gas station) | ₪120 | the professional's vehicle; fuel ties it to the gas station |
| Taxi | — | ×4 | ₪40 | — | +5 min wait: the night-time emergency button |

On foot the far-flat commute takes about 4.5 h a day; by bus, about 2 h for ₪12. The bicycle pays
for itself in time within about a month, and the model's steady worker buys it in month 2–3.

## 8. School and professions

| Path | Needs to enrol | Tuition (two halves) | Sessions (4 h, 08:00–14:00) | Grants | Licence | ₪/h r0 | Upkeep/month | Record |
|---|---|---|---|---|---|---|---|---|
| Developer | edu 30 | 2,400 | 12 | +20 edu | 0 | 58 (3.1× cook) | 120 | allowed |
| Lawyer | edu 40, cha 30 | 3,400 | 16 | +25 edu | 600 | 64 (3.4×) | 250 | closed |
| Doctor | edu 50, fit 40 | 4,400 | 20 | +30 edu | 800 | 72 (3.8×) | 250 | closed |

Half the tuition is paid on enrolment and half at the middle session; whatever of the second half
the player cannot pay becomes a student loan (ordinary debt). Professionals are salaried: round
multipliers 0.7 / 1.0 / 1.15, shifts start 07:00–11:00, same ranks. In the model the developer
enrols in month 4–5, graduates in month 7 and earns 3.7× by month 8. The lawyer graduates in
months 9–12 (52% of runs within the year). The doctor is a choice for month 2, not month 6.

## 9. Prices

| Item | Price | Why |
|---|---|---|
| Start money | ₪300 | down from 400: it is the newcomer's whole margin over the first rent |
| Bag meal / bottle / eating out | 18 / 5 / 45 | food runs ₪250–330 a month for a worker: real, not crushing |
| Bag | 4 meals + 4 bottles; backpack ₪250 → 6 each, goods 12 → 20 | the backpack is the alley's first upgrade |
| Monthly bills | 100 / 150 / 220 | rent and bills arrive together on the rent line |
| Income tax | 35% of the month's wages over ₪3,000 | only professionals (and a top barman) pay it. The late-game sink that matters most |

## 10. The alley (22:00–05:00)

Goods: knock-off perfume (base ₪100, band 0.5–1.6×, σ 0.15), fake tickets (220, 0.4–1.9×, 0.25), phones that
"fell off a truck" (600, 0.55–1.6×, 0.20), "סחורה" (400, 0.3–2.4×, 0.35). Each night, log price = 0.5 ×
yesterday's + Normal(0, σ), clamped to the band; 6% chance a night of one good crashing (½) or booming (×2).
Show yesterday's price as an arrow, keep the volatile good the most tempting, and never let a price move while the screen is open.

| Constant | Value | Why |
|---|---|---|
| The front | the guy fronts up to ₪2,000 of stock at 20%, due in 5 days | without it, a cook's ₪300 of free cash makes ₪50 a night. With it, greed is possible and dangerous |
| Heat per deal | 5 + 1 per ₪500 | volume and size both show |
| Heat decay per day | 2 + 25% of current heat | heat settles at a level set by how often you deal, instead of piling up |
| Police on leaving | 3% + 1.4 × (heat/100)² | heat 15 ≈ 6%, heat 40 ≈ 25%, heat 80 ≈ 93% |
| Escape (chase minigame) | 35% + 35% × skill + 0.2% × fitness (max 90%) | skill at the chase matters; with empty pockets a stop is only a warning |
| Scuffle | 4% when carrying ≥ ₪500; lose half the goods and 25 energy | the risk that has nothing to do with the police |
| Arrest | fine ₪1,000 then ₪2,000; goods confiscated; 1 then 3 days in a cell; record = 1 | the record permanently closes doctor and lawyer. Developer stays open as the way back |

Greedy (seven nights in ten, sells at +15%, uses the front) earns about ₪3,600–5,000 a month from
month 4, against the steady cook's ₪2,090, and is arrested in 21% of months. The year ends with a
median of ₪4,004 but a 10th percentile of **−₪12,585** (debt). Careful (three nights in ten, keeps
the job, only fat margins) earns ₪12,700 a year on the side with 0.41 arrests.

## 11. The casino (20:00–04:00)

Table bet ₪10–200 (VIP ₪500 after ₪5,000 wagered). The cage sells at most ₪500 of chips a night (₪1,000
VIP), so one visit can never ruin you. Return to player: blackjack 98.5% for perfect play (93% clueless),
roulette 97.3%, slots 92% with rare 50× wins, poker 95%. The first visit includes ₪50 of match-play chips,
and every visit a free drink. The hooks are honest ones: real variance, a loud rare jackpot and a VIP
table to chase. No streaks are rigged. Model gambler (two nights in five): −₪3,644 median a year, 22% end ahead, nobody evicted.

## 12. Failure and recovery

| Rule | Value | Why |
|---|---|---|
| Missed rent | debt carries over + ₪100 fine | as today |
| Second miss in a row | mom pays ₪1,000 once a year (trust −15, worry +20); otherwise eviction | the first fall is caught, and it costs story |
| Eviction | hostel, ₪50 a night (or debt), sleep 9/h, 90 min from the centre; keep everything | **no game over for money**: the year always reaches the visit |
| Back to a flat | far flat again for ₪450 deposit + the rent | `comeback` is back in a flat by months 5–8 and ends the year with ₪3,474 |
| Debt | fines and the ambulance never make cash negative: they become debt, 5% a month, 30% of each wage garnished | cash stays for food, so one bad week cannot become a starvation loop |
| Mom's food box | 2 meals when food < 40, no meals in the bag and no money; at most every 3 days | the old weekly box came too late; the model showed death spirals without this |

## 13. Sinks through the year

Rent +10% (month 7) · weddings ₪250 in months 3, 7, 10 · mom's birthday ₪150 (month 5) · about one mishap
a month (₪40–160, skipped when broke) · bills · vehicle and professional upkeep · tax · the visit (₪400 of food,
₪1,500 of furniture if you can). A professional in the central flat still saves ~₪1,700 a month: comfortable, not a victory lap.

## 14. Model results (200 runs each; cash after rent; m12 = day 120)

| Strategy | m1 | m2 | m4 | m8 | m12 | p10 m12 | Missed a rent | Evicted | Collapses/yr | Income m8 |
|---|---|---|---|---|---|---|---|---|---|---|
| careless newcomer | 0 | −166 | −1,597 | −2,565 | −2,619 | −6,731 | 100% | 100% | 27 | 1,278 |
| comeback (careless 2 months, then disciplined) | 0 | −145 | 972 | 2,002 | 3,474 | 2,872 | 100% | 94% | 5 | 1,897 |
| honest unskilled | 155 | 15 | 231 | 674 | 732 | 321 | 30% | 0% | 0 | 1,585 |
| steady worker (cook, bike, mid flat; no extras) | 611 | 785 | 1,129 | 812 | 991 | 721 | 0% | 0% | 0 | 2,088 |
| steady + every optional extra | 697 | 693 | 1,464 | 1,247 | 1,658 | 1,239 | 0% | 0% | 0 | 2,136 |
| expert cook (skill 1.0) | 942 | 745 | 983 | 3,317 | 3,710 | 3,298 | 0% | 0% | 0 | 2,503 |
| job-hopper (best rate it qualifies for) | 304 | 311 | 1,753 | 2,835 | 3,743 | 3,214 | 0% | 0% | 0 | 2,617 |
| student, developer | 503 | 334 | 1,038 | 2,157 | 5,633 | 4,502 | 22% | 0% | 0 | 5,568 |
| student, lawyer | 395 | 0 | 625 | 603 | 580 | −42 | 28% | 0% | 0 | 2,064 |
| greedy alley trader | 586 | 0 | 0 | 150 | 4,004 | −12,585 | 70% | 57% | 9 | 3,745 |
| careful alley trader | 610 | 761 | 689 | 1,193 | 4,091 | 505 | 20% | 1% | 5.8 | 1,941 |
| gambler | 223 | 181 | 810 | 919 | 1,216 | 211 | 12% | 0% | 0 | 1,624 |

Cash leaves out goods held and assets (bike, deposits); negative cash means debt. The model also
prints savings and income per month, progress, spending and pay per hour.

## 15. Risks

1. **The model's player is a script.** It plans a day, not a week. Its trader collapses come from its
   own scheduling, not from the numbers. Not modelled: doctor, scooter, taxi, the front's deadline, VIP.
2. **Skill drift.** Real players improve over the year; the unskilled curve is the floor, not the norm.
3. **The far flat on foot is tight** (work, 4.5 h of walking, 9 h of sleep, 2 h left). That is the
   intended scarcity, but watch first-month drop-off if the minigames run long.
4. **The front is a loan shark.** It must stay PG-13 and consequence-driven, never glamorous.
5. **Lawyer and doctor arrive late** (months 9–12 and later). Fine for replay; tell the player early.
6. **Tax needs a payslip moment** (mom will have opinions). Without UI it will feel like a bug.
7. **Fitness gates are weak.** Walking alone takes fitness past 25 in month 2, so the hopper jumps to
   exterminator and skips the barman route. Halve `FIT_PER_WALK_HOUR` or raise that gate to 40.
8. **Extras values are guesses at first.** Retune `X_CHANCE`/`X_AMOUNT` once each Mini exists; the
   model prints the monthly share against `X_MONTH_BAND_*` after every change.

## 16. Open questions for the director

1. **Greedy crime ends the year at a median of +₪4k, but 1 run in 10 ends at −₪12.6k or worse.** Is that
   the moral, or should greed pay less (raise `POLICE_K`) or more?
2. **No game over for money**: eviction sends you to the hostel and the year always reaches the
   visit. Today's "back to mom" ending becomes a choice, not a punishment.
3. **The front**: keep it as the way into the alley, or trading with your own savings only? Without
   it, the alley is not worth visiting before month 5.
4. Income tax as the professional's sink, or an in-world cost (a car, helping a sibling, the visit)?
5. A ₪150 monthly bus pass? It would make the bus strictly better than walking from day one.
6. Doctor: keep it as the long, hard path, or shorten it to 16 sessions?
