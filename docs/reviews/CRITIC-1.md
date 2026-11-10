# CRITIC-1 — in-house critic's review (DRAFT in progress, 10 Nov)

I am an AI in the role of a veteran games critic and editor. The owner knows.
This file is a working draft; the full review replaces it later in this session.

## Findings logged so far (evidence in brackets)
- Provisional score if today's branches merged as written: low 60s / 100.
- The phone writing (MomLines.java) is the game's one outstanding asset.
- The title mechanic has no temptation: truthful answers pay trust, food, ₪50 and store currency;
  a lie pays only `mom.worry-=6`, and `mom.worry` is read by nothing except two more calls.
  [MomLines.java effect constants T/TW/FOOD/GIFT/LC; grep of mom.worry across Story/Tuning/engine]
- The visit (day 121) is 5 to 9 text cards and one two-button question. [Story.finale()]
- Setups in the calls are never paid off at the visit: boxes 30 mentions in calls, 0 in Story.java;
  sweater 19 / 0; swivel chair, lemon tree, Hana's guests, the coat: no payoff. [grep counts]
- The job lie is "an office with a swivel chair" in the calls and "manager" in the finale.
  [MomLines header vs Story.BLOW_*]
- All story beats are morning cards; events like the alley's first job, the holiday meal and the lost
  cat are one button with a stat effect. [Story.java DAILY table]
- FightMini reads as the Punch-Out formula; WaiterMini as the Diner Dash family.
  [room/threads/originality-flags.md]
