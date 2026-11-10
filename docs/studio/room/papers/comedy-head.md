# comedy-head — position paper (v1, 09:12)

Owns: how funny the game is. `docs/TONE.md` (the comedy bible), `Gags.java` (the two-message gag
bank), failure lines, microcopy proposals, punch-up of every writer's scenes.
Read so far: MomLines, Lines, Story + STORY.md (all three in full), the engine's strings, the room.

## What I found
The writing is good and it is not yet sharp. The owner's milk joke is two messages, 12 words, no
joke-word in either. A v1 call card averages 20 words plus a 13-word reply (measured over 109 cards
and 330 replies), and it explains itself ("אבל אני מתרגמת", "רק אומרת", "ספרתי"). Three problems:
1. **Mom's engine is guilt, and guilt is a nag.** The laugh in most v1 calls is paid by the player
   (did you eat, did you take a sweater, you did not call). The owner's first note was "mom should not
   nag". A nag is a question that demands something. Dad's texts in Story never demand, and they are
   the best thing in the script.
2. **Tics.** Counted with grep: "רק אומרת" 20 times in MomLines and 8 in Story, "אבא אומר: '<מילה>'"
   19, "סוודר" 23, "פנינה" 48, "אני לא שואלת" 14 in both files. A formula is funny twice.
3. **Two writers, two casts.** The bakery boss is a cheerful woman at five in the morning in Lines and
   slow Nissim at three in Story; the bar is run by "בעל הבר" in one file and by Dana in the other.
   One voice per character, or nobody has a voice.

## The three changes
1. **Mom stops asking and starts reporting.** Outside the weekly call she never asks the player a
   question (lint: no "?" in her unsolicited texts). She sends postcards from home, dry, with a turn.
   Her comic engine is understatement: she knows more than she says (this is also what the ending
   "אמא ידעה" needs). Sweater, boxes and "you never call" are retired as questions.
2. **A gag is two messages and then it is closed.** Setup from one person, payoff days later from
   another, played straight, no explanation, never echoed (ruling 3). The bank holds 45 pairs and 30
   one-cards; a run shows 10 to 12 pairs and about 8 one-cards, one open at a time.
3. **Sharp is a set of rules, not a mood** (TONE.md): cut until it breaks; the turn is the last word;
   a number beats an adjective; nobody in the game knows they are funny; logic goes one step too far,
   never two; no "!" on a punchline.

## Ten sample gags (setup, days later, payoff). Calibrate on these.
1. אמא: "אבא יצא להביא חלב." · 7 ימים · אבא: "אני מחפש חלב בכל העיר כבר שבוע. אבא" *(the owner's)*
2. אמא: "אבא עלה לגג לכוון את האנטנה. השכן ביקש את הסולם לחמש דקות." · 4 · אבא: "השכן עוד לא החזיר את הסולם. אבא"
3. אמא: "אם הקופסה הכחולה אצלך, אל תגיד לאבא." · 5 · אבא: "הקופסה הכחולה אצלי. אל תגיד לאמא. אבא"
4. אמא: "הבת של פנינה מתקשרת אליה כל יום. כל יום." · 6 · אבא: "פנינה שאלה אותי איך חוסמים מספר. אבא"
5. אמא: "אבא יושב לכתוב לך הודעה. ביקש שלא נפריע." · 2 · אבא: "בסדר. אבא"
6. אמא: "אורנה אומרת שהיא תמיד יוצאת מהבריכה אחרונה. מחר נראה." · 3 · אבא: "אמא ואורנה עדיין בבריכה. אף אחת לא יוצאת ראשונה. הבאתי לאמא כריך. אבא"
7. אמא: "נסעתי לחנה לשלושה ימים. השארתי לאבא קופסה לכל יום, עם פתק." · 2 · אבא: "אכלתי את יום שלישי ביום שני. מה עושים עכשיו. אבא"
8. אבא: "הספר שלי יצא לפנסיה. הלכתי לחדש. אבא" · 3 · אמא: "אבא ישן עם כובע."
9. אמא: "אבא אמר עליך משהו יפה. לא אגיד מה, שלא תתרגל." · 4 · אבא: "אמא אומרת שאמרתי עליך משהו יפה. אני לא זוכר מה. אני עומד מאחורי זה. אבא"
10. אמא: "התקנו לסבתא רחל אפליקציה להזמנת אוכל." · 6 · דודה חנה: "לסבתא מגיע שליח כל יום. היא לא מזמינה אוכל. היא מזמינה שליח. הם שותים תה."

What they share: the setup is not a joke and can be forgotten; the gap is the punchline (he was on
the roof while you worked four shifts); dad is never stupid, he is thorough; nobody is hurt.

## Five rewrites (before → after, why)
1. `mom.justsaying`: "אני לא אומרת כלום. אני רק אומרת שלא התקשרת שבוע. ושבוע זה שבעה ימים. ובכל אחד מהם אני אמא שלך. רק אומרת."
   → "חלמתי שהתקשרת. היה נחמד. לא צריך להתקשר, כבר דיברנו." — same longing, but she lets the player go instead of billing them.
2. `mom.jobless.1`: "נו? מצאת עבודה? יואבי של אורנה, ביום הראשון שלו בעיר, מצא עבודה, דירה וכלב. כלב, {name}. אפילו הכלב מצא אותו."
   → "יואבי של אורנה מצא ביום הראשון בעיר עבודה ודירה. וכלב. הכלב מצא אותו." — the question and "אפילו" go; the turn stands last.
3. Shift end, cook, flawless: "ככה מבשלים! שמישהו יצלם, אף אחד לא יאמין!"
   → רינה: "לא צעקתי עליך כל המשמרת. אל תתרגל." — praise by absence, paid in her own currency.
4. Collapse dialog: "הגוף אמר די. [...] לאכול, לשתות ולישון זה לא מותרות."
   → "התעלפת. האמבולנס עלה ₪150. זאת הנסיעה היחידה שלך השנה שלא הייתה ברגל." — the sermon becomes a joke made of the game's own rule; the lesson survives.
5. Tip: "אמא מתקשרת כל שבוע. מי שאוכל מעט, מקבל ממנה חבילה."
   → "אמא שולחת אוכל למי שמודה שהוא רעב. את הקופסאות היא רוצה בחזרה." — teaches the rule and plants the prop in one line.

## What I need
- **@narrative-director: where must comedy stop?** My proposal, strike or add: (1) the notebook
  belongs to the ending; in calls it stops being a threat prop (v1 uses it 9 times). (2) Dad's quiet
  texts (the coat on the chair, mom in the dark room): no bank message within two days of them.
  (3) Nissim's late wife, Almog's daughters, Shimon's mother, the hostel: people may be funny around
  these, never about them. (4) A confession to mom gets a dry answer and no punchline. (5) Nothing
  from the bank on days 111-120. Also: six of my pairs use Mazal, Shoko and Shimon; they wait for your
  or `w-building`'s yes, and none touches what happened in Shimon's flat for three days.
- **@story-engine**: answered in `threads/scene-format.md` (closed pairs, no echo counter, pacing, lint).
- **@presentation**: show the day stamp between two texts in a thread ("יום 9" above mom's line, "יום 16"
  above dad's). The gap is the punchline and the thread is where the player sees it. A `voice` bubble
  with a duration for savta.
- **@design-director**: the bank as an SMS-only voice: yes, in my microcopy table today. Boxes owed
  as a mechanic: yes, shown as dry bookkeeping in mom's contact line ("3 קופסאות אצלך"), never as her
  nagging; the return is a payoff at the visit.
- **language editor** (when seated): I follow Story.java spelling (נסים, פרץ, שוקו, "עלייך/אלייך" in the
  feminine); tell me where the glossary differs.

## What I offer
Punch-up of any scene inside the hour (send a path); the pre-send checklist (TONE.md §9); `Tone.TICS`
for the lint; three failure lines per moment and gender; a second opinion on any line: "is this
sharp or just long?" gets a yes or no and a rewrite.
