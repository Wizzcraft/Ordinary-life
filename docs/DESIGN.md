# אמא על הקו: systems and economy

Owner: systems and economy designer. Every number lives in `core/Tuning.java`. The model
`tools/balance/model.py` reads that file directly, so a change there changes the model too. Run
`python3 tools/balance/model.py` (200 runs per strategy, about 3 minutes; `--only trader --verbose`
prints one run day by day).

## 1. The problem and the target

Today a bot that works every day ends month one with about ₪3,500 after rent. Three things cause it:
the employer feeds you **by the hour** (food and water go *up* during a shift), tap water is free, and
₪30/h × 8 h is far more than a ₪900 rent needs. Nothing costs anything, so no week is a decision.

The target curve, and what the model shows (medians of 200 runs, cash after that month's rent):

| Target | Model result |
|---|---|
| Honest unskilled newcomer only just makes the first rent | `unskilled` ends month 1 with **₪190**; 8% of runs miss the first rent |
| Competent worker saves ₪300–600 a month early on | `steady` (+5% from optional extras) saves 369 / 62 (bike) / 402 / 625 / 700 in months 1–5 |
| Month 4: mid flat **or** school, not both | `steady` has ₪1,483 by month 4 after buying the bike. Moving costs ₪1,450 up front, the first half of developer school ₪1,200 |
| Professions pay 3–4× an entry job by month 8 | developer at month 8 earns **3.7×** the cook's rank-0 hourly rate (3.1× on day one, ranks do the rest) |
| Crime ~3× faster, about 1 arrest in 3 months for a greedy player | greedy alley: **2.1×** per hour counting the wait for 22:00, ~10× per hour actually in the alley, **2×** per month. Arrests 0.25 per active month (21% of all months) |
| The casino loses on average | gambler: casino net **−₪3,745** median over the year (mean −2,205); 18% end the year ahead |
| Sinks all year | rent rises in month 7, weddings in months 3/7/10, income tax for professionals, monthly bills, mishaps, the parents' visit |

## 2. Core loops

- **Day** (72 s real time): sleep → travel → one shift → shop → travel → sleep. From the far flat, on
  foot, a shift day leaves about 2 free hours. Everything else (school, the alley, the casino,
  an evening out) has to come from that time, which is why transport and the better flats matter.
- **Month** (10 days): rent + bills on day 11, 21, … The rent line on the HUD is the heartbeat.
- **Year** (12 months): ranks (months 1–4) → the big choice of flat, school or vehicle (month 4) →
  profession or barman career (months 6–8) → the parents' visit (month 12). Sinks scale up so the
  late months still need you to work.

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
| Collapse | ₪250 ambulance (becomes debt if unpaid), wake at 08:00 with 50 | it hurts but cannot spiral (§11) |

**No fourth need.** The model gives a mood meter nothing to do: every strategy that matters
already trades food, sleep and money, and the HUD already has three meters at 360 wide (pillar 4:
learnable in five seconds). What a mood meter would add (reasons to go out, late-game spending) is
covered better by **evenings out as an action**: ₪70 and 3 hours at the bar, +2.5 charisma, feeds
you. Charisma gates the barman job and the lawyer path and raises tips, so going out pays off as a
career move instead of a chore. Mom can ask whether you have friends. `MOOD_NEED = false`.

## 4. Work

All shifts are 8 game hours (the `Mini` contract). Pay per shift = hourly × rank × 8 × the average
round multiplier. XP per shift = 4 + 8 × average round score (0..2), +0.5% per education point.

| Job | ₪/h r0 | Start window | Needs | Trains | Energy/h | Tips | Why |
|---|---|---|---|---|---|---|---|
| Cook | 19 | 08:00–16:00 | none | fitness | 6 | no | the baseline and the reference "entry job" |
| Waiter | 17 | 10:00–16:00 | cha 15 | charisma | 7 | yes | below the cook on paper, ahead of him with charisma 40+ |
| Baker | 21 | 04:30–06:00 | fit 15 | fitness | 7 | no | best untipped pay, but the far flat means a 02:15 alarm and no bus |
| Barman | 22 | 19:00–21:00 | cha 30 | charisma | 6 | yes | the best non-degree career (2.2× entry by month 8); night shift leaves days free for school |
| Gas attendant | 16 | any hour | none | fitness | 7 | no | lowest pay, always available, 1.5 h from the far flat: the fallback |
| Cashier | 18 | 07:00–14:00 | none | charisma | 4 | no | the easy-on-energy job: the one to combine with studying |
| Dishwasher (restaurant) | 15 | 08:00–18:00 | none | fitness | 6 | no | the lowest rung: wide window, no questions asked, the safety net after a bad month |
| Exterminator (night call; van at the gas station) | 24 | 21:00–23:00 | fit 25 | fitness | 6 | no | best untipped rate for nights: competes with the alley for the same hours, honestly |
| Pancake cook (restaurant, breakfast) | 20 | 05:30–07:00 | fit 10 | charisma | 6 | no | a cook's pay +1 for an early alarm; frees the afternoon for school |
| Pastry decorator (bakery) | 22 | 08:00–12:00 | edu 25 | charisma | 4 | no | the reward for a few evening courses: good pay, easy on energy |

**One core minigame per job.** The core minigame earns the whole wage. Optional extras inside a
job (side modes, events, bonus orders) are never required, and all of them together add at most
`EXTRAS_DAILY_BONUS_CAP` = **+10%** of that day's shift pay. That keeps a player who ignores them
on the curve above, and keeps a min-maxer from breaking it. The model gives the steady worker and
the hopper +5% and the expert a capped +10%.

| Constant | Value | Why |
|---|---|---|
| MISS / OK / PERFECT multiplier | 0.4 / 1.0 / 1.3 | skill 0 ≈ 0.68 of OK pay, skill 0.6 ≈ 1.01, skill 1 ≈ 1.22: skill matters, but less than showing up |
| Rank pay | ×1.0 / ×1.2 / ×1.4 | rank 2 is the cook's ceiling (₪26.6/h); beyond that you need tips or a degree |
| Rank XP | 0 / 100 / 350 | competent: rank 1 on day 7, rank 2 around day 22. Unskilled: day 10 and day 35. Ranks are the reason months 2–4 get easier |
| Tips | (0.10 + 0.004 × cha) of base pay × round multiplier | cha 80 gives +42%: a waiter at cha 60 out-earns a cook |
| Stat gain per shift | 1.2 × (1 − stat/120) | the first 50 points take about 50 shifts, the last 30 much longer |
| Job switching | XP is kept per job (`xp.<id>`) | changing jobs costs only rank 0 at the new place, never what you earned |

## 5. Stats

| Stat | Grows by | Gates | Improves |
|---|---|---|---|
| Fitness (start 10) | fitness jobs, 0.08/h walking, 0.2/h cycling | baker 15, doctor 40 | −25% tiredness at 100, +0.2% per point to escape a chase |
| Charisma (start 10) | charisma jobs, evening out +2.5 | waiter 15, barman 30, lawyer 30 | tips, +0.1% per point on alley prices |
| Education (start 15) | evening course ₪50/2 h/+2.5 (17:00–20:00), degree sessions | developer 30, lawyer 40, doctor 50 | +0.5% job XP per point |

## 6. Homes

| Tier | Rent | Deposit | Bills | Sleep/h | Walk to centre | Listing appears | Landlord wants |
|---|---|---|---|---|---|---|---|
| Far (today's) | 900 | 0 (mom paid) | 100 | 10 | 135 min | start | — |
| Mid | 1,200 | 1,200 | 150 | 12 | 80 min | month 3 | any job at rank 1 |
| Central | 2,400 | 2,400 | 220 | 14 | 30 min | month 6 | a profession |

Moving costs ₪250. All rents rise 10% from month 7. Why: the mid flat costs about ₪350 a month
more and saves about 2 hours a day plus an hour of sleep. For a plain cook that is a wash (steady's
savings drop to ~₪100–200 a month after moving); for anyone who uses the time (courses, evenings,
the bar) it pays. The central flat is a professional's reward and a professional's sink.
Proposed spots: mid flat in the empty block west of the gas station (door on road y=330); central
flat near the restaurant/market corner. Placement belongs to the integrators.

## 7. Transport

| Mode | Price | Speed | Per trip | Upkeep/month | Notes |
|---|---|---|---|---|---|
| On foot | — | ×1 | — | — | trains fitness |
| Bus | — | ×3 | ₪6 | — | +15 min average wait, 06:00–24:00 only: useless for baker and barman hours |
| Bicycle | ₪800 once | ×2 | — | ₪30 | any hour, as tiring as walking, trains fitness (0.2/h) |
| Scooter | ₪3,000 once | ×4 | ₪5 fuel (₪50 tank = 10 trips, gas station) | ₪120 | the professional's vehicle; fuel ties it to the gas station |
| Taxi | — | ×4 | ₪40 | — | +5 min wait: the night-time emergency button |

A far-flat commute on foot is about 4.5 h a day; by bus about 2 h for ₪12. The bicycle pays for
itself in time within about a month; the model's steady worker buys it in month 2–3.

## 8. School and professions

| Path | Needs to enrol | Tuition (two halves) | Sessions (4 h, 08:00–14:00) | Grants | Licence | ₪/h r0 | Upkeep/month | Record |
|---|---|---|---|---|---|---|---|---|
| Developer | edu 30 | 2,400 | 12 | +20 edu | 0 | 58 (3.1× cook) | 120 | allowed |
| Lawyer | edu 40, cha 30 | 3,400 | 16 | +25 edu | 600 | 64 (3.4×) | 250 | closed |
| Doctor | edu 50, fit 40 | 4,400 | 20 | +30 edu | 800 | 72 (3.8×) | 250 | closed |

Half the tuition on enrolment, half at the middle session. What cannot be paid of the second half
becomes a student loan (ordinary debt). Professionals are salaried: round multipliers 0.7 / 1.0 /
1.15, shift starts 07:00–11:00, same ranks. Model: developer enrols in month 4–5, graduates in
month 7, earns 3.7× by month 8. The lawyer graduates in month 9–12 (58% of runs within the year).
The doctor is the hardest path: a choice made in month 2, not month 6.

## 9. Prices

| Item | Price | Why |
|---|---|---|
| Start money | ₪300 | down from 400: it is the newcomer's whole margin over the first rent |
| Bag meal / bottle / eating out | 18 / 5 / 45 | food runs ₪250–330 a month for a worker: real, not crushing |
| Bag | 4 meals + 4 bottles; backpack ₪250 → 6 each, goods 12 → 20 | the backpack is the alley's first upgrade |
| Monthly bills | 100 / 150 / 220 | rent and bills arrive together on the rent line |
| Income tax | 35% of the month's wages over ₪3,000 | only professionals (and a top barman) pay it. The late-game sink that matters most |

## 10. The alley (22:00–05:00)

Four goods: knock-off perfume (base ₪100, band 0.5–1.6×, σ 0.15), fake tickets (220, 0.4–1.9×, 0.25),
phones that "fell off a truck" (600, 0.55–1.6×, 0.20), and "סחורה" (400, 0.3–2.4×, 0.35). Each
night, log price = 0.5 × yesterday + Normal(0, σ), pulled back to base and clamped to the band.
There is a 6% chance a night that one good crashes (½) or booms (×2). Guidance for whoever builds
the venue: show yesterday's price as an arrow, keep the volatile good the most tempting, never let
a price move while the screen is open.

| Constant | Value | Why |
|---|---|---|
| The front | the guy fronts up to ₪2,000 of stock at 20%, due in 5 days | without it, a cook's ₪300 of free cash makes ₪50 a night. With it, greed is possible and dangerous |
| Heat per deal | 5 + 1 per ₪500 | volume and size both show |
| Heat decay per day | 2 + 25% of current heat | heat settles at a level set by how often you deal, instead of piling up |
| Police on leaving | 3% + 1.4 × (heat/100)² | heat 15 ≈ 6%, heat 40 ≈ 25%, heat 80 ≈ 93% |
| Escape (chase minigame) | 35% + 35% × skill + 0.2% × fitness (max 90%) | being good at it matters; empty pockets mean a warning, not an arrest |
| Scuffle | 4% when carrying ≥ ₪500; lose half the goods and 25 energy | the risk that has nothing to do with the police |
| Arrest | fine ₪1,000 then ₪2,000; goods confiscated; 1 then 3 days in a cell; record = 1 | the record permanently closes doctor and lawyer. Developer stays open as the way back |

Greedy (seven nights in ten, sells at +15%, uses the front) vs careful (three nights in ten, keeps
the job, only fat margins): from month 4, greedy earns about ₪4,000–4,500 a month against the steady cook's
₪2,090 (before extras), and is arrested in 21% of months. The year ends with a median of ₪1,481 but a 10th
percentile of **−₪11,436** (debt). Careful earns ₪13,000 a year on the side with 0.29 arrests.

## 11. The casino (20:00–04:00)

Table bet ₪10–200 (VIP ₪500 after ₪5,000 wagered). The cage sells at most ₪500 of chips a night
(₪1,000 VIP), so one visit can never ruin you. Return to player: blackjack 98.5% for perfect play
(93% clueless), roulette 97.3%, slots 92% with rare big wins, poker 95%. A first visit includes
₪50 of match-play chips; a free drink every visit. Hooks are honest: early wins happen because
variance is real, the slots' rare 50× win is loud, and the VIP table is a status to chase. No
rigged streaks. Model gambler (two nights in five, 15% of chips per bet): −₪3,745 median over
the year, 18% end ahead, nobody evicted by the casino alone because the night cap holds.

## 12. Failure and recovery

| Rule | Value | Why |
|---|---|---|
| Missed rent | debt carries over + ₪100 fine | as today |
| Second miss in a row | mom pays ₪1,000 once a year (trust −15, worry +20); otherwise eviction | the first fall is caught, and it costs story |
| Eviction | hostel, ₪50 a night (or debt), sleep 9/h, 90 min from the centre; keep everything | **no game over for money**: the year always reaches the visit |
| Back to a flat | far flat again for ₪450 deposit + the rent | the `comeback` strategy is back in a flat between months 5 and 8 and ends the year with ₪3,591 |
| Debt | fines and the ambulance never make cash negative: they become debt, 5% a month, 30% of each wage garnished | cash stays for food, so one bad week cannot become a starvation loop |
| Mom's food box | 2 meals when food < 40, no meals in the bag and no money; at most every 3 days | the old weekly box came too late; the model showed death spirals without this |

## 13. Sinks through the year

Rent +10% (month 7) · weddings ₪250 in months 3, 7 and 10 · mom's birthday ₪150 (month 5) · about
one mishap a month (₪40–160, skipped when broke) · bills · vehicle upkeep · professional upkeep and
tax · the visit (₪400 of food, ₪1,500 of furniture if you can). A professional in the central flat
still saves ~₪1,700 a month: the late game is comfortable, not a victory lap. More sinks would push
the visit into a budget question (open question 4).

## 14. Model results (200 runs each; cash after rent; m12 = day 120)

| Strategy | m1 | m2 | m4 | m8 | m12 | p10 m12 | Missed a rent | Evicted | Collapses/yr | Income m8 |
|---|---|---|---|---|---|---|---|---|---|---|
| careless newcomer | 0 | −230 | −1,652 | −2,497 | −2,291 | −7,650 | 100% | 100% | 27 | 1,317 |
| comeback (careless 2 months, then disciplined) | 0 | −167 | 1,012 | 2,096 | 3,591 | 2,825 | 100% | 96% | 5 | 1,913 |
| honest unskilled | 190 | 23 | 195 | 656 | 745 | 436 | 29% | 0% | 0 | 1,581 |
| steady worker (cook, bike, mid flat, +5% extras) | 669 | 615 | 1,483 | 1,362 | 1,862 | 1,331 | 0% | 0% | 0 | 2,184 |
| expert cook (skill 1.0, extras capped at +10%) | 1,138 | 1,107 | 1,673 | 5,031 | 6,330 | 5,866 | 0% | 0% | 0 | 2,762 |
| job-hopper (best rate it qualifies for, +5% extras) | 377 | 430 | 594 | 3,526 | 4,535 | 3,913 | 0% | 0% | 0 | 2,759 |
| student, developer | 531 | 331 | 835 | 2,094 | 5,835 | 4,436 | 26% | 0% | 0 | 5,625 |
| student, lawyer | 388 | 0 | 637 | 544 | 650 | 0 | 34% | 0% | 0 | 2,064 |
| greedy alley trader | 602 | 0 | 0 | 0 | 1,481 | −11,436 | 80% | 57% | 9 | 4,156 |
| careful alley trader | 572 | 759 | 758 | 1,160 | 3,749 | 539 | 12% | 2% | 5.7 | 1,945 |
| gambler | 221 | 203 | 772 | 753 | 1,294 | 203 | 10% | 0% | 0 | 1,580 |

Cash leaves out goods held and assets bought (bike, deposits). The full output (savings and income
per month, progress, spending, per-hour pay) is printed by the model.

## 15. Risks

1. **The model's player is a script.** It plans a day, not a week, and its alley and casino
   behaviour is crude. The collapses in the trader rows come from its scheduling (alley nights
   after long walks), not from the numbers. Real players will be smarter at trading and worse at
   eating.
2. **Skill drift.** Real players get better over the year; the model keeps skill fixed. The
   unskilled curve is the floor, not a typical player.
3. **The far flat on foot is tight.** A day of work, 4.5 h of walking and 9 h of sleep leaves about
   two hours. That is the intended scarcity, but if the minigames run long, players will feel
   trapped until they buy the bike. Watch the first-month completion rate.
4. **The front is a loan shark.** It is what makes greedy trading possible. Its tone has to stay
   PG-13 and consequence-driven (heat, debt, the guy's friends), never glamorous.
5. **Lawyer and doctor are late.** The lawyer arrives in months 9–12 and the doctor later still.
   That is fine for replay, but a player who picks the doctor in month 6 cannot finish it.
6. **Tax needs a payslip moment** (mom will have opinions). Without UI it will feel like a bug.
7. Not modelled: the doctor path, the scooter, the taxi, the front's 5-day deadline and its
   scuffle ×3, the VIP table, mom's trust and worry.
8. **Fitness gates are weak.** Walking alone takes fitness past 25 in month 2, so the hopper jumps to
   exterminator (₪24/h) and skips the barman route. If the barman is meant to be the top non-degree
   career, halve `FIT_PER_WALK_HOUR` or raise the exterminator gate to 40.

## 16. Open questions for the director

1. **Greedy crime ends the year at a median of +₪1.5k with a 10% chance of −₪11k or worse.** Is that
   the moral you want, or should greed pay more on average (lower `POLICE_K`, bigger `FRONT_MAX`)?
2. **No game over for money**: eviction sends you to the hostel, and the year always reaches the
   visit. Today's "you went back to mom" ending would become a choice, not a punishment.
3. **The front**: keep "the guy fronts stock" as the way into the alley, or only allow trading with
   your own savings? Without it, the alley is not worth visiting before month 5.
4. Income tax as the professional's sink: fine for the tone, or should it be an in-world cost
   (a car, helping a sibling, a bigger visit)?
5. Should the bus have a ₪150 monthly pass? It would make the bus strictly better than walking
   from day one.
6. Doctor: keep it as the long, hard path, or shorten it to 16 sessions?
