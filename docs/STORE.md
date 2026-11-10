# The store (החנות) — monetisation design

Brief: real money that helps you progress, real reasons to buy, not pay-to-win. Data: `core/Catalog.java`;
platform hook: `core/Billing.java`. Until the game is on Google Play with Play Billing, the store ships in
a clearly labelled **test mode** (`Billing.Test`): granted instantly, nothing charged. **The deal:** money buys looks, jokes and a little comfort; it never buys shekels, stats,
ranks, trust or a way out of a lie. Everything that touches play can be earned in one playthrough.

## 1. Hard rules (non-negotiable; a change that breaks one is a bug)
1. Nothing sells in-game shekels, energy, needs or time without a hard cap. No item grants `money`, ever.
2. No loot boxes, gacha, mystery items or anything randomised. The player knows exactly what they get.
3. No premium-only power. Every item that affects play is priced in נחת and is earnable by playing.
4. The casino and the alley are sealed off: נחת can never be spent there and never won there; no earning
   milestone is triggered by a casino or alley event; real money can never fund either (nothing sold
   gives shekels, and shekels and נחת never convert into each other in either direction).
5. No pressure: no countdown timers, no "limited time", no fake or struck-through prices, no guilt
   prompts, no daily-login streaks, no red badges on the store button, no pop-ups that interrupt play.
6. Every price is shown in full on the card before the first tap. Real-money prices come from Play.
7. Every purchase goes through a confirmation that states the price and the balance after it.
8. Restore purchases is always available. Granting the same purchase twice must be harmless.
9. When the player already has enough נחת for everything left in the store, the store says so and stops
   offering packs. A shortfall shows how to earn the rest, never a "buy נחת" button.
10. Mom's alternative lines, voices and ringtones are flavour only: same choices, same effects.

## 2. The currency: נחת
*Nachat* is the pride parents get from their children: the one currency mom has always dealt in. Stored in
`gems`; written after the number, same in singular and plural ("20 נחת"). Icon id `nachat`: a gold coin
with a small heart cut out. Tagline in the store header: **נחת: המטבע היחיד שאמא מבינה בו.**
Toast on earning: **+20 נחת · {reason}** (e.g. "+30 נחת · חודש בלי חובות").

| id (`earn.<id>`) | Hebrew name | נחת | max per playthrough | typical | thorough |
|---|---|---|---|---|---|
| welcome | מתנת פרידה מאמא | 30 | 1 | 30 | 30 |
| first_shift | משמרת ראשונה | 10 | 1 | 10 | 10 |
| month_clean | חודש בלי חובות (rent paid, no debt at month end) | 30 | 12 | 300 | 360 |
| promo1 | קידום ראשון בעבודה (rank 1 in a job) | 20 | 6 | 40 | 80 |
| promo2 | ניהול משמרת (rank 2 in a job) | 30 | 6 | 30 | 60 |
| honest3 | שלוש שיחות בלי שקר (3 calls in a row, no new lie) | 20 | 6 | 60 | 100 |
| stat50 / stat80 | חצי הדרך / כמעט מושלם (a stat reaches 50 / 80) | 15 / 25 | 3 each | 55 | 95 |
| course / profession | סיום קורס / עבודה במקצוע | 30 / 50 | 3 / 1 | 30 | 110 |
| explorer | כל העיר בכף היד (every building visited) | 20 | 1 | 20 | 20 |
| visit / visit_honest | שרדת את הביקור / ביקור בלי אף שקר | 80 / 50 | 1 / 1 | 80 | 130 |
| **total** | | | ceiling 1230 | **655** | **995** |

"Typical" = two jobs, misses rent twice, lies a bit, one course. "Thorough" = clean rent all year, mostly
honest, four jobs promoted, a profession: 5.5 and 8.3 נחת per game day (`<scratchpad>/store/curve.py`).
נחת, `own.*` and equipped cosmetics survive a new game; `earn.*` resets, so every playthrough earns again.

## 3. Real money
Prices in shekels, VAT included, from the Play price tiers. Packs are consumable; the supporter pack is a
one-time, non-consumable purchase. There is deliberately no pack above ₪24.90: the whole cosmetic
collection costs 1,590 נחת, so a bigger pack would only invite overspending.

| sku | Hebrew name | Hebrew description | price | grants | נחת per ₪ |
|---|---|---|---|---|---|
| pack.small | צנצנת נחת | צנצנת עם 60 נחת | ₪4.90 | 60 נחת | 12.2 |
| pack.medium | קופסת נחת | קופסה עם 170 נחת | ₪12.90 | 170 נחת | 13.2 |
| pack.large | ארגז נחת | ארגז עם 360 נחת | ₪24.90 | 360 נחת | 14.5 |
| supporter | חבילת תומכים | מגנט תודה למקרר ועוד 500 נחת. תודה שתמכת! | ₪29.90 once | 500 נחת + magnet | 16.7 |

The supporter pack is the best value on purpose: 500 נחת covers all three conveniences plus a few looks.
Its only exclusive is a thank-you magnet on the flat's fridge (`own.supporter`), which unlocks nothing.
Card line: **אין פה שום דבר שנעול. זה פשוט תודה.**

## 4. Catalogue (first collection; room for more in every section)
Days = game days to earn the item from zero at the typical / thorough rate. Effect on purchase is always
`own.<sku>=1` unless stated. Every cosmetic is bought once (cap 1).

**Looks** (`look.*`; skus to be matched to the character artist's ids). Two are free starters.
| sku | name | description | נחת | days |
|---|---|---|---|---|
| look.basic | בגדי יום יום | מה שנכנס למזוודה ביום שעברת לעיר | free | – |
| look.sweater | הסוודר שאמא סרגה | חם, קצת עקום, ואסור להוריד כשהיא מתקשרת | free | – |
| look.hoodie | קפוצ'ון עירוני | הקפוצ'ון שכל העיר לובשת, רק בצבע שלך | 40 | 8 / 5 |
| look.shirt | חולצה מכופתרת | לראיונות עבודה ולחתונות של אחרים | 50 | 10 / 7 |
| look.tracksuit | אימונית | נראית כמו כושר גם בלי לעשות כושר | 40 | 8 / 5 |
| look.summer | קיץ עירוני | מכנסיים קצרים, כפכפים, ובלי שום התנצלות | 50 | 10 / 7 |
| look.retro | שנות השמונים | צבעים זוהרים, כריות כתפיים וביטחון עצמי מלא | 60 | 11 / 8 |
| look.apron | סינר של שף | קישוט בלבד. לא מבשל במקומך, לצערנו | 60 | 11 / 8 |
| look.evening | בגדי ערב | למקרה שמישהו יזמין אותך למקום עם מפות לבנות | 80 | 15 / 10 |
| look.pajamas | פיג'מה עם עננים | יוצאים ככה לסופר? יוצאים ככה לסופר. | 70 | 13 / 9 |

**Accessories** (`acc.*`): acc.cap כובע מצחייה "שומר מהשמש ומשיחות עם שכנים" 20 · acc.shades משקפי שמש
"שאף אחד לא יראה שלא ישנת" 25 · acc.pins תרמיל עם סיכות "קישוט בלבד. לא מוסיף מקום בתיק" 30 · acc.scarf
צעיף ארוך "ארוך מספיק לכרוך פעמיים ועוד להשאיר זנב" 25 · acc.phones אוזניות גדולות "את העיר לא שומעים.
את אמא, תמיד." 35 · acc.watch השעון של סבא "מאחר בשתי דקות כבר ארבעים שנה" 35 · acc.pendant שרשרת עם
תליון "מתנה ממישהו. לא שואלים ממי." 35 · acc.umbrella מטרייה צבעונית "גם כשאין גשם. במיוחד כשאין גשם." 30.
Days: 20 → 4/3, 25 → 5/4, 30 → 6/4, 35 → 7/5.

**Decor for the flat** (`decor.*`): decor.plant עציץ שעדיין חי "בינתיים. צריך להשקות אותו מדי פעם" 25 ·
decor.poster פוסטר של ים "חלון לנוף שאין לדירה הזאת" 30 · decor.sofa ספה מיד שנייה "רק כמה כתמים. כמעט
לא רואים." 70 · decor.lights שרשרת נורות "הופכת חור בקיר לדירת סטודיו" 40 · decor.rug שטיח כמעט פרסי "פרסי
לגמרי, אם לא מסתכלים על התווית" 45 · decor.photos תמונות על המקרר "המשפחה מסתכלת עליך כל בוקר. באהבה." 35.

**City colour themes** (`theme.*`, recolour the map palette): theme.sunset עיר בשקיעה "כל העיר בכתום ורוד,
כל היום" 80 · theme.winter חורף בעיר "שמיים אפורים, שלוליות ומטריות ברחוב" 80 · theme.neon עיר של לילה
"שלטי ניאון וחלונות דולקים עד הבוקר" 100 (15/10, 15/10, 19/13 days).

**Mom** (why buy: her calls are the heart of the game, and these make them funnier):
- Ringtones, synthesised in code, original tunes: ring.drama צלצול דרמטי "כאילו כל שיחה היא סוף עונה" 20 ·
  ring.lullaby שיר ערש "מנגינה רכה, כמו פעם, רק עם רטט" 20 · ring.march מארש חגיגי "כל שיחה מאמא היא
  אירוע ממלכתי" 25 · ring.rooster תרנגול "קוקוריקו בכל שיחה. אמא לא צריכה לדעת." 25.
- Voice styles (her speech blips and text rhythm): voice.whisper אמא לוחשת "כל שיחה נשמעת כמו סוד מדינה"
  40 · voice.opera אמא באופרה "כל משפט שלה הוא אריה" 50 · voice.fast אמא על מהיר "אותן דאגות, פי שניים מהר" 40.
- One-liner sets (her greetings and sign-offs only, not story beats): lines.grandma אמא בגרסת סבתא "אותה
  אמא, רק שואלת אם אכלת כל שתי דקות" 60 · lines.coach אמא המאמנת "כל שיחה נשמעת כמו נאום לפני גמר" 60 ·
  lines.sage אמא הפילוסופית "דאגות עם ציטוטים שהיא המציאה לבד" 60. About 12 lines per set, both genders.

**Convenience** (the only items that touch play; together 330 נחת):
| sku | name | description | נחת | days | cap | what it does |
|---|---|---|---|---|---|---|
| conv.bag | כיס נוסף בתיק | מקום לעוד ארוחה או בקבוק. אחד, וזהו. | 60 | 11 / 8 | one slot, once | `BAG_MAX` 4 → 5 |
| conv.care | חבילה מאמא | פעם בשבוע מחכות בבית שתי ארוחות ושני בקבוקים | 120 | 22 / 15 | 1 per 7 game days | `meals+=2;bottles+=2` (₪76) |
| conv.taxi | כרטיסיית מוניות | שלוש נסיעות חינם בחודש. מה שלא נוצל, לא נצבר. | 150 | 28 / 19 | 3 per game month | one free ride saves ~2 game hours |

Care package: appears in the flat the day after mom's weekly call; collected at home into the bag; what
does not fit waits there; an uncollected package is replaced, never stacked. Toast: **הגיעה חבילה מאמא.
יש גם פתק: "תאכל/י משהו חם."** (gendered: `תאכל` / `תאכלי`). Taxi: offered on the map as "מונית (נשארו 2
החודש)"; unused rides vanish at rent day. Neither ever gives shekels, energy or stats.

**Content, later** (`available=false`; shown as an announcement with no buy button, sold only once playable):
content.port שכונת הנמל "שכונה חדשה עם עבודות חדשות ודירה ליד הים" 300 · content.vet מקצוע חדש: וטרינריה
"לימודים, עבודה חדשה ומשחק משלה" 300 · content.year2 פרק נוסף: השנה השנייה "ההורים חזרו הביתה. עכשיו סבתא
באה לבקר." 300 (55 / 37 days each). Priced in נחת so free players get them too.
Totals: cosmetics 1,590 (looks 450, accessories 235, decor 245, themes 260, mom 400); convenience 330.

## 5. Honest math
**Free player.** Buying conveniences as soon as affordable: typical owns the bag on day 10, the care
package on day 38, the taxi card on day 63; thorough on days 8, 21, 45. After the conveniences, one
playthrough leaves 325 (typical) to 665 (thorough) נחת: 20% to 42% of the cosmetic collection. Since נחת
and ownership carry over, a second playthrough brings a typical player to about 60%, a thorough one to
nearly all of it.

**Paying player** = buys the supporter pack (₪29.90) on day 1: all three conveniences at once, 170 נחת left.
Value model: care ₪76 per 7 days; taxi 0.6 game hours a day (worth about ₪18 if spent working); bag about
₪3 a day. Gap = what the payer had that the free player did not, accumulated (vs a thorough free player:
~₪310 / ₪960 / ₪1,030).

| after | free (typical) owns | payer owns | payer's extra, cumulative | in context |
|---|---|---|---|---|
| 10 days | bag (day 10), 10 נחת spare | all 3 + 170 נחת of looks | ~₪320 and ~7 game hours | a third of one rent (₪900); ~13% of early wages |
| 40 days | bag, care (day 38) | same | ~₪1,150 and ~25 hours | ~1.3 rents; ~10% of wages earned so far |
| 120 days | all 3 (day 63) | same | ~₪1,550 and ~38 hours, then frozen | <2 rents over a year; ~5% of a year's wages |

**Why this is comfort and style, not victory.** The year is won on whether mom's visit goes well: lies that
hold, trust, rent paid, needs kept up. The store sells nothing that touches lies, trust, stats, ranks or
wages. The gap is a few meals and a few saved walks; it stops growing on day 63, and one good or bad week
of play (or one night in the casino) moves more money than the whole year's gap. A payer who plays badly
still loses; a free player who plays well never needs the store. The biggest relative gap is the first ten
days, which is why the cheapest convenience is the bag and why `welcome` gives 30 נחת at once.

## 6. The store screen (spec for the engineer)
**Entered from** the title screen (a secondary button "חנות" below the new-game buttons) and the flat's building menu ("חנות", last
item). Time is stopped while it is open (it is a menu). **Never shown or linked** from: the map HUD, any
shift or minigame, the casino, the alley, mom's calls and every dialog, rent day, eviction, collapse,
ambulance, arrest, the end screen (no "pay to continue", ever). No notifications, no pop-ups, no badges.

**Layout** (360 wide, 16 gutter, `Ui` palette): header with "חנות", the tagline, the balance chip
"{n} נחת" with the coin icon, and the exit button "יציאה". In test mode an orange band under the header:
**מצב ניסיון: שום כסף לא מחויב, הכול ניתן מיד.** Section chips (scroll sideways): בשבילי (looks,
accessories) · לדירה ולעיר (decor, themes) · אמא (ringtones, voices, lines) · נוחות · נחת (how to earn,
packs, supporter, restore) · בקרוב. Cards in two columns, 156×176, touch target is the whole card.

**Card**: icon drawn from `Item.icon` (polygon icon id; looks show the character wearing it), title 14,
description 12 (two lines max), price row: "40 נחת" or the Play price ("₪12.90"), or "חינם". State chip:
"שלך" (owned) · "בשימוש" (equipped) · "חסרים עוד 15 נחת" (dimmed, informational) · conveniences add their
cap: "נוצלו 2 מתוך 3 החודש". Tapping opens a detail sheet: big preview, full text, price, "אפשר להרוויח
נחת במשחק", for נחת items also the reference "בערך ₪3.30 בכסף אמיתי" (small pack's rate), and one primary button:
"לקנות ב־40 נחת" / "ללבוש" / "להשתמש" / "להוריד" (unequip) / disabled "חסרים עוד 15 נחת".
A shortfall links only to the earning list ("איך מרוויחים נחת"), never to packs.

**Purchase with נחת**: confirm dialog **"לקנות את {name} ב־{price} נחת? יישארו לך {n} נחת."** buttons
"כן, לקנות" / "ביטול" → deduct, set `own.<sku>`, apply `effect`, save, `Sfx.COIN` + burst, toast
**"{name} שלך!"**, button becomes "ללבוש" / "להשתמש".

**Real money, test mode**: confirm **"מצב ניסיון: לא יחויב שום כסף. לקבל את {name}?"** → `Billing.Test`
answers OK at once → grant, save, toast **"התקבל במצב ניסיון, בלי תשלום."** Test grants also add to
`store.test_gems` / set `store.test_sup`. The first launch with real billing removes leftover test נחת
(never below zero) and the test magnet, keeps everything earned, and says once: **"הקניות ממצב הניסיון
הוסרו. כל מה שהרווחת במשחק נשאר."**

**Real money, real mode**: confirm **"לקנות את {name} ב־{price} דרך גוגל פליי?"** → `Billing.buy` → Play's
sheet. Results: OK → grant, record `store.tok.<hash of token>`, save, then the shell acknowledges/consumes;
toast **"תודה! {n} נחת נוספו."** (supporter: **"תודה שתמכת במשחק!"**). CANCELLED → back to the card,
nothing said. PENDING → **"התשלום ממתין לאישור. הנחת יגיעו כשהוא יאושר."** FAILED → **"הקנייה לא הצליחה
ולא חויבת. אפשר לנסות שוב."** ALREADY_OWNED → run restore. Restore button at the bottom of נחת:
"שחזור קניות" → **"שוחזרו הקניות שלך."** or **"לא נמצאו קניות לשחזור."**

**Empty and error states**: billing not ready or offline → the packs block reads **"אי אפשר לקנות בכסף
כרגע. כל השאר עובד גם בלי אינטרנט."** (earning and נחת purchases are fully offline). A section fully owned
→ **"הכול כבר שלך. דברים חדשים יגיעו בעדכונים."** Enough נחת for everything left → packs hidden, text
**"יש לך מספיק נחת לכל מה שבחנות. אין צורך לקנות עוד."** Coming-soon card: chip **"בקרוב"**, no price button.

**Variables**: account-level (kept on new game): `gems`, `own.<sku>`, `look.*`, `store.eq.<category>`
(equipped theme, ringtone, voice, lines, decor), `store.tok.*`, `store.test_gems`, `store.test_sup`.
Per playthrough (reset on new game): `earn.<id>` counts, `store.care_day`, `store.care_waiting`,
`store.taxi_used`. The engine's new-game code must keep the first group.

## 7. Risks: notes from a non-lawyer, for the owner to check (not legal advice)
- **Play Billing is mandatory** on Google Play for digital goods and in-game currency (a few regional
  alternatives exist). No links to outside payment. Purchases must be acknowledged within 3 days or Play
  refunds them. A sideloaded APK must stay in test mode; never take payment any other way.
- **Simulated gambling and crime themes** affect the IARC content rating (answer the questionnaire
  honestly about simulated gambling, crime and drug references even unnamed) and the store listing, which
  will also show "contains in-app purchases". Some regions and boards are strict about simulated gambling
  in games with purchases; our wall between נחת and the casino is the main mitigation, keep it testable.
- **Virtual currencies and consumer rules**: Israeli consumer protection (cancellation and disclosure
  for distance sales and digital content), EU/UK guidance on in-game currencies (show real prices, no
  misleading offers) if distributed there. Check refund obligations; Play's refund flow helps.
- **Minors**: purchases by minors may need guardian consent or be reversible. Do not target children
  in Play's target-audience settings (that would trigger the Families policy); the store must not
  address or market to kids.
- **Privacy policy** (Play requires one, plus the Data safety form): what is collected (the game: nothing;
  saves stay on the phone), what Google processes for purchases, purchase tokens kept locally, no ads, no
  analytics, no accounts, a contact address, how to delete data. Also check tax and a merchant profile.

## 8. Decisions for the director
1. נחת and owned items carry over between playthroughs, and earning repeats each playthrough.
2. Strength of conveniences: is a weekly ₪76 package and 3 taxi rides a month the right ceiling?
3. Content packs priced in נחת (earnable), no pack above ₪24.90, the magnet as the sole exclusive.
