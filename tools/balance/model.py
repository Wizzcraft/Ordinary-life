#!/usr/bin/env python3
"""Year-long balance model for "אמא על הקו".

Reads every number from app/src/main/java/com/wizzcraft/ima/core/Tuning.java (so the model and the
game cannot drift apart), plays the 120-day year for a set of player strategies many times with
different seeds, and prints money, needs and progress curves.

    python3 tools/balance/model.py              # summary + monthly curves, 200 runs per strategy (~3 min)
    python3 tools/balance/model.py --runs 1000  # tighter percentiles
    python3 tools/balance/model.py --only trader --verbose   # one strategy, day-by-day log of run 0

Standard library only. The simulation is an abstraction of the real game: a day is a list of tasks
(travel, shift, study, alley, ...) executed on a clock in chunks of at most one game hour, with the
real need rates, opening windows and walking distances (the City.path algorithm on today's map).
Minigame skill is reduced to one number per strategy, mapped onto the team's lab targets
(skill 0 -> average round score 0.5, 0.6 -> 1.25, 1.0 -> 1.75).
"""
import argparse
import math
import os
import random
import re
import statistics
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
TUNING = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'java', 'com', 'wizzcraft', 'ima', 'core',
                      'Tuning.java')


# ---------------------------------------------------------------- Tuning.java parser
def load_tuning(path):
    src = open(path, encoding='utf-8').read()
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    env = {}
    for m in re.finditer(r'static\s+final\s+(?:int|float|double|boolean)(?:\[\])*\s+(.*?);', src, re.S):
        body = m.group(1)
        # "A = 1, B = 2" declares several constants; split on commas outside braces.
        parts, depth, cur = [], 0, ''
        for ch in body:
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
            if ch == ',' and depth == 0:
                parts.append(cur)
                cur = ''
            else:
                cur += ch
        parts.append(cur)
        for part in parts:
            name, expr = part.split('=', 1)
            expr = re.sub(r'(\d+(?:\.\d+)?)[fFdD]\b', r'\1', expr)
            expr = expr.replace('{', '[').replace('}', ']').replace('true', 'True').replace('false', 'False')
            env[name.strip()] = eval(expr, {'__builtins__': {}}, dict(env))
    return env


T = type('T', (), load_tuning(TUNING))

# ---------------------------------------------------------------- map (today's City)
DOORS = {
    'centre': (130, 190),  # restaurant
    'market': (230, 190),
    'bakery': (90, 190),
    'bar': (270, 190),
    'school': (110, 60),
    'casino': (250, 60),
    'gas': (250, 330),
    'alley': (250, 470),
    'home0': (110, 470),
}
ROADS_V = (40, 180, 320)


def path_len(a, b):
    (px, py), (tx, ty) = a, b
    if py == ty:
        return abs(px - tx)
    best = min(ROADS_V, key=lambda xv: abs(px - xv) + abs(tx - xv))
    return abs(px - best) + abs(tx - best) + abs(py - ty)


def walk_min(home, a, b):
    """Walking minutes between two places. Homes other than today's flat are placed by their
    distance to the centre (Tuning.HOME_TO_CENTRE); the hostel is HOSTEL_TO_CENTRE away."""
    def resolve(x):
        if x == 'home':
            return 'home%d' % home
        return x
    a, b = resolve(a), resolve(b)
    if a == b:
        return 0.0
    for x, y in ((a, b), (b, a)):
        if x in ('home1', 'home2', 'home-1'):
            base = T.HOSTEL_TO_CENTRE if x == 'home-1' else T.HOME_TO_CENTRE[int(x[4:])]
            if y == 'centre':
                return base
            return base + path_len(DOORS['centre'], DOORS[y]) / T.WALK_SPEED * 0.6
    return path_len(DOORS[a], DOORS[b]) / T.WALK_SPEED


# Where each job's shift happens (the exterminator's van leaves from the gas station).
JOB_PLACE = ['centre', 'centre', 'bakery', 'bar', 'gas', 'market', 'centre', 'gas', 'centre', 'bakery']
FOOT, BUS, BIKE, SCOOTER, TAXI = range(5)


def in_window(tod, frm, to):
    if frm == to:
        return True
    if frm < to:
        return frm <= tod < to
    return tod >= frm or tod < to


def score_dist(skill):
    """Round result probabilities for an effective skill, matching the lab targets."""
    s = max(0.0, min(2.0, 0.5 + 1.25 * skill))
    pp = s * s / 4
    po = s - 2 * pp
    return 1 - po - pp, po, pp


class Collapse(Exception):
    pass


# ---------------------------------------------------------------- player
class P:
    def __init__(self, strat, seed):
        self.s = strat
        self.r = random.Random(seed)
        self.money = T.START_MONEY
        self.food, self.water, self.energy = 70.0, 70.0, 85.0
        self.fit, self.cha, self.edu = T.START_FIT, T.START_CHA, T.START_EDU
        self.meals, self.bottles, self.bag = 1, 1, T.BAG_MAX
        self.home, self.loc = 0, 'home'
        self.owned = {FOOT}
        self.fuel = 0
        self.job = None
        self.xp = [0.0] * len(T.JOB_PAY)
        self.pro, self.pro_xp = None, 0.0
        self.school, self.sessions = None, 0
        self.heat, self.record, self.arrests = 0.0, 0, 0
        self.goods = [0] * 4
        self.goods_cost = [0.0] * 4
        self.prices_x = [0.0] * 4
        self.prices = list(T.GOOD_BASE)
        self.rent_missed, self.debt_due = 0, 0
        self.bailout = False
        self.wagered = 0
        self.casino_visits = 0
        self.clock = 9 * 60.0
        self.day = 1
        self.lost_until = 0
        self.last_mom = 0
        self.last_box = -99
        self.debt = 0.0
        self.front = 0.0
        self.hours = dict(job=0.0, alley=0.0)
        self.first_miss = False
        self.favours, self.trust, self.gems = 0, 0.0, 0
        self.x_value = self.x_month = 0.0
        self.core_month = 0.0
        self.month_taxable = 0.0
        self.log = []
        self.ev = dict(collapse=0, arrest=0, scuffle=0, missed=0, evict=0, bailout=0, shifts=0,
                       sessions=0, courses=0, outs=0, alley=0, casino=0, chase=0)
        self.inc = dict(wage=0, tips=0, pro=0, alley=0, casino=0, extras=0)
        self.spend = dict(rent=0, bills=0, food=0, transport=0, school=0, fines=0, sinks=0, out=0, ambulance=0,
                          move=0, hostel=0, tax=0)
        self.monthly = []
        self.month_income = 0

    # ---- helpers
    @property
    def tod(self):
        return self.clock % 1440

    def rank(self, xp):
        return 2 if xp >= T.RANK_XP[2] else 1 if xp >= T.RANK_XP[1] else 0

    def rent_now(self, tier):
        r = T.HOME_RENT[tier]
        month = (self.day - 1) // T.DAYS_PER_MONTH + 1
        if month >= T.RENT_RAISE_MONTH:
            r = int(round(r * (1 + T.RENT_RAISE)))
        return r

    def pay(self, amount, cat, debt=False):
        """Spend money. With debt=True (fines, ambulance) what cannot be paid becomes debt."""
        self.spend[cat] += amount
        if debt and amount > self.money:
            self.debt += amount - max(0, self.money)
            self.money = min(self.money, 0) if self.money < 0 else 0
            return
        self.money -= amount

    def earn(self, amount, cat):
        self.inc[cat] += amount
        self.month_income += amount
        if cat in ('wage', 'tips', 'pro'):
            self.month_taxable += amount
            self.core_month += amount
        if self.debt > 0 and cat in ('wage', 'tips', 'pro'):
            g = min(self.debt, amount * T.DEBT_GARNISH)
            self.debt -= g
            amount -= g
        self.money += amount
        return
        self.inc[cat] += amount
        self.month_income += amount
        if cat in ('wage', 'tips', 'pro'):
            self.month_taxable += amount
            self.core_month += amount

    # ---- needs
    def eat_drink(self):
        careful = self.s.careful
        lim_f = 45 if careful else 20
        lim_w = 45 if careful else 20
        if self.food < lim_f and self.meals > 0:
            self.meals -= 1
            self.food = min(100, self.food + T.MEAL_FOOD)
        if self.water < lim_w:
            if self.loc == 'home':
                self.water = min(100, self.water + T.TAP_WATER)
            elif self.bottles > 0:
                self.bottles -= 1
                self.water = min(100, self.water + T.BOTTLE_WATER)
        if self.food < 10 and self.loc == 'centre' and self.money >= T.DINE_PRICE and 8 * 60 <= self.tod:
            self.pay(T.DINE_PRICE, 'food')
            self.food = min(100, self.food + T.DINE_FOOD)
            self.water = min(100, self.water + T.DINE_WATER)

    def advance(self, minutes, act, energy_rate=None, sleep_rate=None):
        while minutes > 1e-6:
            step = min(60.0, minutes, 1440 - self.tod)
            if step < 1e-6:
                step = min(60.0, minutes)
            if act != T.A_SLEEP:
                self.eat_drink()
            h = step / 60
            fr, wr = T.FOOD_RATE[act], T.WATER_RATE[act]
            if act == T.A_SLEEP:
                er = -sleep_rate
            else:
                er = (energy_rate if energy_rate is not None else T.ENERGY_RATE[act]) * (1 - T.FIT_ENERGY_SAVING * self.fit)
                if self.food <= 0 or self.water <= 0:
                    er *= T.STARVING_ENERGY_MULT
            self.food = max(0.0, self.food - fr * h)
            self.water = max(0.0, self.water - wr * h)
            self.energy = max(0.0, min(100.0, self.energy - er * h))
            before = self.clock
            self.clock += step
            minutes -= step
            if int(self.clock // 1440) != int(before // 1440):
                self.new_day()
            if self.energy <= 0 and act != T.A_SLEEP:
                raise Collapse()

    def travel(self, dest, mode=None):
        if self.loc == dest:
            return
        walk = walk_min(self.home, self.loc, dest)
        if mode is None:
            mode = self.s.mode(self)
        if mode == BUS and not (T.BUS_FROM <= self.tod < T.BUS_TO):
            mode = FOOT
        if mode == SCOOTER:
            if self.fuel <= 0:
                if self.money >= T.FUEL_TANK_PRICE:
                    self.pay(T.FUEL_TANK_PRICE, 'transport')
                    self.fuel = T.FUEL_TANK_LEGS
                else:
                    mode = FOOT
            if mode == SCOOTER:
                self.fuel -= 1
        elif T.TR_PER_LEG[mode]:
            if self.money < T.TR_PER_LEG[mode]:
                mode = FOOT
            else:
                self.pay(T.TR_PER_LEG[mode], 'transport')
        mins = walk / T.TR_SPEED[mode] + T.TR_WAIT[mode]
        if mode == FOOT:
            self.fit = min(100, self.fit + T.FIT_PER_WALK_HOUR * mins / 60)
        elif mode == BIKE:
            self.fit = min(100, self.fit + T.FIT_PER_BIKE_HOUR * mins / 60)
        self.advance(mins, T.TR_ACTIVITY[mode])
        self.loc = dest

    def wait_for(self, frm, to, max_wait=6 * 60):
        if in_window(self.tod, frm, to):
            return True
        wait = (frm - self.tod) % 1440
        if wait > max_wait:
            return False
        self.advance(wait, T.A_IDLE)
        return True

    # ---- day changes
    def new_day(self):
        self.day = int(self.clock // 1440) + 1
        d = self.day
        self.heat = max(0.0, self.heat * (1 - T.HEAT_DECAY_SHARE) - T.HEAT_DECAY_DAY)
        self.update_prices()
        if (d - 1) % T.DAYS_PER_MONTH == 0 and d > 1:
            self.month_end()
        if self.r.random() < T.MISHAPS_PER_MONTH / T.DAYS_PER_MONTH:
            cost = self.r.randint(T.MISHAP_MIN, T.MISHAP_MAX)
            if self.money >= cost + 100:  # the dentist can wait when there is nothing to pay with
                self.pay(cost, 'sinks')

    def month_end(self):
        month = (self.day - 1) // T.DAYS_PER_MONTH  # the month that just ended
        nxt = month + 1
        due = 0
        if self.home >= 0:
            due = self.rent_now(self.home) + T.HOME_BILLS[self.home] + self.debt_due
        upkeep = sum(T.TR_UPKEEP[m] for m in self.owned)
        if self.pro is not None:
            upkeep += T.PRO_UPKEEP[self.pro]
        self.pay(upkeep, 'transport', debt=True)
        self.debt *= 1 + T.DEBT_INTEREST_MONTH
        tax = max(0.0, self.month_taxable - T.TAX_FREE_MONTH) * T.TAX_RATE
        self.month_taxable = 0
        if tax:
            self.pay(tax, 'tax', debt=True)
        while self.home >= 0 and self.money < due and self.favours > 0:
            self.favours -= 1  # a favour buys a week's grace: worth FAVOUR_RENT_GRACE off the rent
            due -= T.FAVOUR_RENT_GRACE
        if self.home >= 0:
            if self.money >= due:
                self.pay(due, 'rent')
                self.debt_due = 0
                self.rent_missed = 0
            else:
                paid = max(0, int(self.money))
                self.pay(paid, 'rent')
                self.debt_due = due - paid + T.RENT_FINE
                self.rent_missed += 1
                self.ev['missed'] += 1
                if month == 1:
                    self.first_miss = True
                if self.rent_missed >= T.RENT_EVICT_AFTER:
                    if not self.bailout:
                        self.bailout = True
                        self.ev['bailout'] += 1
                        self.money += T.MOM_BAILOUT
                        pay = min(self.debt_due, max(0, int(self.money)))
                        self.pay(pay, 'rent')
                        self.debt_due -= pay
                        if self.debt_due <= 0:
                            self.rent_missed = 0
                    if self.rent_missed >= T.RENT_EVICT_AFTER:
                        self.ev['evict'] += 1
                        self.home = -1
                        self.debt_due = 0
                        self.rent_missed = 0
        # Social sinks are paid only from what there is (a broke guest brings a smaller cheque).
        if nxt in T.WEDDING_MONTHS:
            self.pay(min(T.WEDDING_GIFT, max(0, self.money)), 'sinks')
        if nxt == T.MOM_BIRTHDAY_MONTH:
            self.pay(min(T.MOM_BIRTHDAY_GIFT, max(0, self.money)), 'sinks')
        if nxt == T.VISIT_PREP_MONTH and self.s.visit_prep and self.money > T.VISIT_PREP_FOOD + 500:
            self.pay(T.VISIT_PREP_FOOD, 'sinks')
            if self.money > T.VISIT_PREP_FURNITURE + 2500:  # a sofa the parents can sit on
                self.pay(T.VISIT_PREP_FURNITURE, 'sinks')
        self.monthly.append(dict(month=month, money=self.money - self.debt, debt=self.debt, income=self.month_income, energy=self.energy,
                                 home=self.home, rank=self.best_rank(), edu=self.edu, cha=self.cha, fit=self.fit,
                                 pro=self.pro, sessions=self.sessions, heat=self.heat, record=self.record,
                                 arrests=self.arrests, hourly=self.hourly_now(),
                                 xshare=self.x_month / self.core_month if self.core_month else 0))
        self.month_income = 0
        self.x_month = self.core_month = 0.0

    def best_rank(self):
        if self.pro is not None:
            return 3 + self.rank(self.pro_xp)
        return self.rank(self.xp[self.job]) if self.job is not None else 0

    def hourly_now(self):
        """Expected pay per hour on shift right now (OK rounds), tips included."""
        if self.pro is not None:
            return T.PRO_PAY[self.pro] * T.RANK_PAY[self.rank(self.pro_xp)]
        if self.job is None:
            return 0
        j = self.job
        base = T.JOB_PAY[j] * T.RANK_PAY[self.rank(self.xp[j])]
        if T.JOB_TIPS[j]:
            base *= 1 + T.TIP_BASE + self.cha * T.TIP_PER_CHA
        return base

    def update_prices(self):
        for g in range(4):
            x = (1 - T.GOOD_REVERT) * self.prices_x[g] + self.r.gauss(0, T.GOOD_VOL[g])
            self.prices_x[g] = x
            p = T.GOOD_BASE[g] * math.exp(x)
            if self.r.random() < T.GOOD_EVENT / 4:
                p *= 0.5 if self.r.random() < 0.5 else 2.0
            self.prices[g] = max(T.GOOD_BASE[g] * T.GOOD_LO[g], min(T.GOOD_BASE[g] * T.GOOD_HI[g], p))

    # ---- tasks
    def shift(self, pro=False):
        c0 = self.clock
        ok = self._shift(pro)
        if ok:  # the commute there counts; the way home is counted by the next leg's owner
            self.hours['job'] += (self.clock - c0) / 60 + walk_min(self.home, 'home', 'centre') / T.TR_SPEED[self.s.mode(self)] / 60
        return ok

    def _shift(self, pro=False):
        j = self.job
        frm, to = (T.PRO_FROM, T.PRO_TO) if pro else (T.JOB_FROM[j], T.JOB_TO[j])
        self.travel('centre' if pro else JOB_PLACE[j])
        if not self.wait_for(frm, to, 16 * 60):
            return False
        if self.energy < T.MIN_SHIFT_ENERGY:
            return False
        self.food = min(100, self.food + T.STAFF_MEAL_FOOD)
        self.water = min(100, self.water + T.STAFF_MEAL_WATER)
        xp = self.pro_xp if pro else self.xp[j]
        rank = self.rank(xp)
        rounds, total, score = 8, 0.0, 0
        mult = T.PRO_RESULT_MULT if pro else T.RESULT_MULT
        erate = 5.0 if pro else T.JOB_ENERGY[j]
        offers = self.roll_extras() if (self.s.extras and not pro) else []
        early = T.X_AMOUNT[T.X_EARLY] if T.X_EARLY in offers else 0
        for _ in range(rounds):
            eff = self.s.skill - 0.05 * rank - (T.TIRED_DIFFICULTY if self.energy < T.TIRED_ENERGY else 0)
            pm, po, _pp = score_dist(eff)
            u = self.r.random()
            res = 0 if u < pm else 1 if u < pm + po else 2
            score += res
            total += mult[res]
            self.advance((T.SHIFT_HOURS * 60 - early) / rounds, T.A_WORK, energy_rate=erate)
        avg = total / rounds
        hourly = (T.PRO_PAY[self.pro] if pro else T.JOB_PAY[j]) * T.RANK_PAY[rank]
        wage = hourly * T.SHIFT_HOURS * avg
        self.earn(wage, 'pro' if pro else 'wage')
        if offers:
            self.take_extras(offers, wage, hourly, erate, j)
        if not pro and T.JOB_TIPS[j]:
            self.earn(T.JOB_PAY[j] * T.SHIFT_HOURS * avg * (T.TIP_BASE + self.cha * T.TIP_PER_CHA), 'tips')
        gain = (T.XP_BASE + T.XP_PER_SCORE * score / rounds) * (1 + self.edu * T.EDU_XP_BONUS)
        if pro:
            self.pro_xp += gain
        else:
            self.xp[j] += gain
            st = T.JOB_TRAINS[j]
            cur = (self.fit, self.cha, self.edu)[st]
            inc = T.STAT_PER_SHIFT * (1 - cur / 120)
            if st == T.FIT:
                self.fit += inc
            elif st == T.CHA:
                self.cha += inc
        self.ev['shifts'] += 1
        return True

    def roll_extras(self):
        """Which optional extras this shift offers, kept under the daily money-equivalent cap."""
        kinds = [k for k in (T.X_TIP, T.X_EARLY, T.X_MEAL, T.X_ENERGY, T.X_XP, T.X_FAVOUR, T.X_TRUST,
                             T.X_OVERTIME) if self.r.random() < T.X_CHANCE[k]]
        return kinds

    def take_extras(self, offers, wage, hourly, erate, j):
        budget = wage * T.X_DAY_CAP  # money-equivalent cap for the day
        for k in offers:
            amount = T.X_AMOUNT[k]
            value = amount * T.X_VALUE[k]
            if k == T.X_OVERTIME:
                if self.energy < T.OVERTIME_MIN_ENERGY:
                    continue
                # Overtime pays well and costs energy (and so tomorrow's sleep): its value is the
                # premium over normal pay, minus the energy, minus the hours.
                pay = hourly * amount / 60 * T.OVERTIME_PAY
                value = pay - erate * amount / 60 * T.X_VALUE[T.X_ENERGY] - amount * T.X_VALUE[T.X_EARLY]
                if value > budget:
                    continue
                self.advance(amount, T.A_WORK, energy_rate=erate)
                self.earn(pay, 'extras')
            elif value > budget:
                continue
            elif k == T.X_TIP:
                self.earn(amount, 'extras')
            elif k == T.X_MEAL:
                if self.meals < self.bag:
                    self.meals += 1
                else:
                    self.food = min(100, self.food + T.MEAL_FOOD)
            elif k == T.X_ENERGY:
                self.energy = min(100, self.energy + amount)
            elif k == T.X_XP:
                before = self.rank(self.xp[j])
                self.xp[j] += amount
                if self.rank(self.xp[j]) > before:
                    self.gems += T.X_AMOUNT[T.X_GEMS]
            elif k == T.X_FAVOUR:
                self.favours += 1
            elif k == T.X_TRUST:
                self.trust += amount
            budget -= max(0.0, value)
            self.x_value += value
            self.x_month += value

    def shop(self, n):
        if self.money < T.MEAL_PRICE:
            return
        self.travel('market')
        if not self.wait_for(7 * 60, 22 * 60, 60):
            return
        while self.meals < min(n, self.bag) and self.money >= T.MEAL_PRICE + self.s.reserve_food:
            self.pay(T.MEAL_PRICE, 'food')
            self.meals += 1
        while self.bottles < min(max(1, n // 2), self.bag) and self.money >= T.BOTTLE_PRICE:
            self.pay(T.BOTTLE_PRICE, 'food')
            self.bottles += 1
        self.advance(15, T.A_IDLE)

    def course(self):
        if self.money < T.COURSE_PRICE:
            return False
        self.travel('school')
        if not self.wait_for(T.COURSE_FROM, T.COURSE_TO, 3 * 60):
            return False
        self.pay(T.COURSE_PRICE, 'school')
        self.advance(T.COURSE_HOURS * 60, T.A_STUDY)
        self.edu = min(100, self.edu + T.COURSE_EDU)
        self.ev['courses'] += 1
        return True

    def study(self):
        self.travel('school')
        if not self.wait_for(T.PRO_SESSION_FROM, T.PRO_SESSION_TO):
            return False
        self.advance(T.PRO_SESSION_HOURS * 60, T.A_STUDY)
        k = self.school
        self.sessions += 1
        if self.sessions == T.PRO_SESSIONS[k] // 2:
            # Second half: what the cash allows above the rent, the rest as a student loan (debt).
            second = T.PRO_TUITION[k] - T.PRO_TUITION[k] // 2
            cash = min(second, max(0, self.money - self.rent_now(max(0, self.home)) - T.HOME_BILLS[max(0, self.home)] - 150))
            self.pay(cash, 'school')
            self.spend['school'] += second - cash
            self.debt += second - cash
        self.edu = min(100, self.edu + T.PRO_EDU_GRANT[k] / T.PRO_SESSIONS[k])
        self.ev['sessions'] += 1
        if self.sessions >= T.PRO_SESSIONS[k]:
            self.pay(T.PRO_LICENSE[k], 'school')
            self.pro, self.school = k, None
        return True

    def out(self):
        if self.money < T.OUT_PRICE:
            return
        self.travel('bar')
        if not self.wait_for(20 * 60, 2 * 60, 4 * 60):
            return
        self.pay(T.OUT_PRICE, 'out')
        self.food = min(100, self.food + T.OUT_FOOD)
        self.water = min(100, self.water + T.OUT_WATER)
        self.advance(T.OUT_HOURS * 60, T.A_IDLE)
        self.cha = min(100, self.cha + T.OUT_CHA * (1 - self.cha / 120))
        self.ev['outs'] += 1

    def alley(self):
        c0 = self.clock
        self._alley()
        self.hours['alley'] += (self.clock - c0) / 60 + walk_min(self.home, 'home', 'alley') / 60

    def _alley(self):
        self.travel('alley')
        if not self.wait_for(T.ALLEY_FROM, T.ALLEY_TO, 18 * 60):
            return
        self.ev['alley'] += 1
        s = self.s
        cap = T.GOODS_CAP_BACKPACK if self.bag > T.BAG_MAX else T.GOODS_CAP
        adj = self.cha * T.CHA_ALLEY_PRICE
        for g in range(4):  # sell
            if self.goods[g] and (s.fire_sale(self) or self.prices[g] >= T.GOOD_BASE[g] * s.sell_at or
                                  self.prices[g] * self.goods[g] >= self.goods_cost[g] * s.sell_gain):
                v = self.goods[g] * self.prices[g] * (1 + adj)
                self.earn(v - self.goods_cost[g], 'alley')
                self.money += self.goods_cost[g]
                self.goods[g] = 0
                self.goods_cost[g] = 0
                self.heat = min(100, self.heat + T.HEAT_PER_DEAL + T.HEAT_PER_500 * v / 500)
                if self.front > 0:  # pay the guy back first, with his fee
                    owed = self.front * (1 + T.FRONT_FEE)
                    paid = min(owed, self.money)
                    self.money -= paid
                    self.inc['alley'] -= paid - paid / (1 + T.FRONT_FEE)
                    self.front -= paid / (1 + T.FRONT_FEE)
        room = cap - sum(self.goods)
        ratios = sorted(range(4), key=lambda g: self.prices[g] / T.GOOD_BASE[g])
        for g in ratios:  # buy
            price = self.prices[g] * (1 - adj)
            if room <= 0 or self.prices[g] > T.GOOD_BASE[g] * s.buy_at:
                break
            own = max(0.0, self.money - s.reserve(self))
            credit = (T.FRONT_MAX - self.front) if s.use_front else 0
            n = min(room, int((own + credit) // price))
            if n <= 0:
                continue
            from_own = min(own, n * price)
            self.money -= from_own
            self.front += n * price - from_own
            self.goods[g] += n
            self.goods_cost[g] += n * price
            room -= n
            self.heat = min(100, self.heat + T.HEAT_PER_DEAL + T.HEAT_PER_500 * n * price / 500)
        self.advance(90, T.A_IDLE)
        carried = sum(self.goods[g] * self.prices[g] for g in range(4))
        if carried >= T.SCUFFLE_MIN_VALUE and self.r.random() < T.SCUFFLE_CHANCE:
            self.ev['scuffle'] += 1
            if self.r.random() > 0.3 + 0.5 * s.skill:
                for g in range(4):
                    lost = int(round(self.goods[g] * T.SCUFFLE_LOSS))
                    if self.goods[g]:
                        self.goods_cost[g] *= (self.goods[g] - lost) / self.goods[g]
                    self.goods[g] -= lost
                self.energy = max(1, self.energy - T.SCUFFLE_ENERGY)
        p = T.POLICE_BASE + T.POLICE_K * (self.heat / 100) ** 2
        if self.r.random() < p:
            self.ev['chase'] += 1
            esc = min(0.9, T.ESCAPE_BASE + T.ESCAPE_SKILL * s.skill + T.FIT_ESCAPE * self.fit)
            if sum(self.goods) > 0 and self.r.random() > esc:
                self.arrest()

    def arrest(self):
        k = min(self.arrests, 1)
        self.arrests += 1
        self.ev['arrest'] += 1
        self.record = 1
        self.pay(T.ARREST_FINE[k], 'fines', debt=True)
        self.goods = [0] * 4
        self.goods_cost = [0.0] * 4
        self.debt += self.front * (1 + T.FRONT_FEE)  # the guy still wants his money
        self.front = 0
        self.heat = T.ARREST_HEAT_AFTER
        if self.school is not None and T.PRO_CLEAN_RECORD[self.school]:
            self.school = None
        # A night in the cell, released at noon after ARREST_DAYS days.
        release = (int(self.clock // 1440) + T.ARREST_DAYS[k]) * 1440 + 12 * 60
        while self.clock < release:
            self.advance(min(60, release - self.clock), T.A_IDLE, energy_rate=0)
            self.food = max(self.food, 40)
            self.water = max(self.water, 40)
        self.loc = 'centre'

    def casino(self):
        self.travel('casino')
        if not self.wait_for(20 * 60, 4 * 60, 3 * 60):
            return
        self.ev['casino'] += 1
        cap = T.NIGHT_BUYIN_VIP if self.wagered >= T.VIP_AFTER else T.NIGHT_BUYIN
        chips = min(cap, max(0, int(self.money - self.s.reserve(self))))
        if self.casino_visits == 0:
            chips += T.WELCOME_CHIPS
            self.money += T.WELCOME_CHIPS
        self.casino_visits += 1
        self.water = min(100, self.water + T.COMP_WATER)
        start = chips
        tmax = T.VIP_MAX if self.wagered >= T.VIP_AFTER else T.TABLE_MAX
        for hand in range(40):
            if chips < T.TABLE_MIN or chips >= start * 2:
                break
            bet = max(T.TABLE_MIN, min(tmax, chips, int(chips * self.s.bet_frac)))
            game = self.r.choice((0, 1, 2))
            self.wagered += bet
            if game == 2:  # slots: rare big wins, low RTP
                u = self.r.random()
                win = bet * (50 if u < 0.004 else 5 if u < 0.04 else 1.6 if u < 0.38 else 0)
                # scale so the mean return equals the tuned RTP
                win *= T.GAME_RTP[2] / (0.004 * 50 + 0.036 * 5 + 0.34 * 1.6)
                chips += win - bet
            else:
                rtp = T.GAME_RTP[1] if game == 1 else (
                        T.BLACKJACK_RTP_CLUELESS + (T.GAME_RTP[0] - T.BLACKJACK_RTP_CLUELESS) * self.s.skill)
                chips += bet if self.r.random() < rtp / 2 else -bet
            self.advance(3, T.A_IDLE)
        delta = chips - start
        self.money += delta
        self.inc['casino'] += delta
        self.month_income += delta

    def sleep(self, wake_tod, max_h=10.0, min_h=5.0):
        self.travel('home')
        if self.home < 0:
            self.pay(T.HOSTEL_NIGHT, 'hostel', debt=True)
        rate = T.HOSTEL_SLEEP if self.home < 0 else T.HOME_SLEEP[self.home]
        until = (wake_tod - self.tod) % 1440
        hrs = max(min_h, min(max_h, until / 60))
        need = (100 - self.energy) / rate
        hrs = max(min(hrs, need + 0.5), min_h if self.energy < 70 else 0)
        self.water = min(100, self.water + T.TAP_WATER)
        self.advance(hrs * 60, T.A_SLEEP, sleep_rate=rate)
        self.water = min(100, self.water + T.TAP_WATER)

    def rest(self):
        """Go home and sleep until nearly full: what a sensible player does when exhausted."""
        self.travel('home')
        rate = T.HOSTEL_SLEEP if self.home < 0 else T.HOME_SLEEP[self.home]
        if self.home < 0:
            self.pay(T.HOSTEL_NIGHT, 'hostel', debt=True)
        self.water = min(100, self.water + T.TAP_WATER)
        self.advance((95 - self.energy) / rate * 60, T.A_SLEEP, sleep_rate=rate)
        self.water = min(100, self.water + T.TAP_WATER)

    def collapse(self):
        self.ev['collapse'] += 1
        self.pay(T.AMBULANCE, 'ambulance', debt=True)
        self.loc = 'home'
        self.energy = T.COLLAPSE_WAKE_ENERGY
        self.food = max(self.food, 25)
        self.water = max(self.water, 25)
        nxt = (int(self.clock // 1440) + (1 if self.tod >= 8 * 60 else 0)) * 1440 + 8 * 60
        before = self.clock
        self.clock = nxt
        if int(nxt // 1440) != int(before // 1440):
            self.new_day()

    # ---- a whole year
    def run(self, verbose=False):
        end = T.MONTHS * T.DAYS_PER_MONTH * 1440
        while self.clock < end:
            day = int(self.clock // 1440) + 1
            self.day = day
            m0, c0 = self.money, self.clock
            try:
                if day % 7 == 0 and day != self.last_mom and (self.food < 30 or self.money < 40):
                    # Mom's weekly call sends food boxes to a child who sounds hungry or broke.
                    self.last_mom = day
                    self.meals = min(self.bag, self.meals + 2)
                if self.food < 40 and self.meals == 0 and self.money < T.MEAL_PRICE and \
                        day - self.last_box >= T.MOM_BOX_EVERY_DAYS:
                    self.last_box = day
                    self.meals = min(self.bag, self.meals + T.MOM_BOX_MEALS)
                if self.energy < self.s.rest_below:
                    self.rest()
                self.s.day(self)
            except Collapse:
                self.collapse()
            if self.clock - c0 < 60:  # a plan that did nothing still costs an hour
                try:
                    self.advance(60, T.A_IDLE)
                except Collapse:
                    self.collapse()
            if verbose:
                print('day %3d  %-9s ₪%6d (%+5d) debt %5d  food %3d water %3d energy %3d  job %s rank %d  fit %2d cha %2d edu %2d'
                      '  heat %2d  home %d' % (day, self.s.name, self.money, self.money - m0, self.debt, self.food, self.water,
                                               self.energy, self.job, self.best_rank(), self.fit, self.cha, self.edu,
                                               self.heat, self.home))
        return self


# ---------------------------------------------------------------- strategies
class Strategy:
    name = 'base'
    skill = 0.6
    careful = True
    reserve_food = 0
    visit_prep = True
    sell_at, buy_at, bet_frac, sell_gain = 1.15, 0.8, 0.1, 1.3
    use_front = False
    extras = False  # True: takes every optional extra a shift offers
    seed_name = None  # share another strategy's seeds for a paired comparison
    rest_below = 55

    def fire_sale(self, p):
        """Rent is due in two days and the cash is short: sell everything at any price."""
        days_left = T.DAYS_PER_MONTH - ((p.day - 1) % T.DAYS_PER_MONTH)
        rent = p.rent_now(max(0, p.home)) + T.HOME_BILLS[max(0, p.home)]
        return days_left <= 3 and p.money < rent + 100

    def reserve(self, p):
        """Cash to keep aside for rent and food when gambling or trading."""
        days_left = T.DAYS_PER_MONTH - ((p.day - 1) % T.DAYS_PER_MONTH)
        rent = p.rent_now(max(0, p.home)) + T.HOME_BILLS[max(0, p.home)]
        return max(0, rent - days_left * 120) + 150

    def mode(self, p):
        for m in (SCOOTER, BIKE):
            if m in p.owned:
                return m
        return BUS if self.bus else FOOT

    bus = False

    def hire(self, p, j):
        if p.job != j:
            p.job = j

    def housekeeping(self, p):
        # Back from the hostel when there is enough for the deposit and a month's rent.
        if p.home < 0 and p.money >= T.REENTRY_DEPOSIT + T.HOME_RENT[0] + 300:
            p.pay(T.REENTRY_DEPOSIT, 'move')
            p.home = 0

    def maybe_move(self, p, tier, buffer):
        month = (p.day - 1) // T.DAYS_PER_MONTH + 1
        if p.home >= tier or p.home < 0 or month < T.HOME_FROM_MONTH[tier]:
            return
        if tier == 1 and p.best_rank() < T.HOME_MID_NEED_RANK:
            return
        if tier == 2 and p.pro is None:
            return
        need = T.HOME_DEPOSIT[tier] + T.MOVE_COST + buffer
        if p.money >= need:
            p.pay(T.HOME_DEPOSIT[tier] + T.MOVE_COST, 'move')
            p.home = tier

    def work_day(self, p, extra=()):
        if p.meals < 2 and 7 * 60 <= p.tod < 21 * 60 and p.job is not None and p.pro is None and \
                not in_window(p.tod, T.JOB_FROM[p.job], T.JOB_TO[p.job]):
            p.shop(3)  # the shift starts later (bar, bakery): shop on the way
        p.shift(pro=p.pro is not None)
        if p.meals < 2:
            p.shop(3)
        for e in extra:
            e()
        p.sleep(self.wake(p))

    def wake(self, p):
        j = p.job
        frm = T.PRO_FROM if p.pro is not None else T.JOB_FROM[j]
        lead = walk_min(p.home, 'home', 'centre' if p.pro is not None else JOB_PLACE[j]) / T.TR_SPEED[self.mode(p)]
        return (frm + 30 - lead - 20) % 1440

    def day(self, p):
        self.housekeeping(p)
        self.hire(p, 0)
        self.work_day(p)


class Careless(Strategy):
    """Plays badly AND forgets the basics: no shopping list, skips days, walks everywhere."""
    name = 'careless'
    skill = 0.25
    careful = False
    visit_prep = False
    rest_below = 30

    def day(self, p):
        self.housekeeping(p)
        self.hire(p, 0)
        if p.r.random() < 0.25:  # a lost day: oversleeps, wanders, scrolls
            p.advance(5 * 60, T.A_IDLE)
            if p.r.random() < 0.5:
                p.shop(1)
            p.sleep(7 * 60)
            return
        p.shift()
        if p.r.random() < 0.5:
            p.shop(2)
        if p.r.random() < 0.15 and p.money > 200:
            p.out()
        p.sleep(7 * 60, max_h=8)


class Comeback(Careless):
    """Careless for two months (debt, maybe the hostel), then pulls together: shops, sleeps, works."""
    name = 'comeback'

    def day(self, p):
        if p.day <= 2 * T.DAYS_PER_MONTH:
            return Careless.day(self, p)
        self.careful, self.rest_below, self.skill = True, 55, 0.45
        self.housekeeping(p)
        self.hire(p, 0)
        self.work_day(p)


class Unskilled(Strategy):
    """Honest and organised, but bad at the minigames: the newcomer the first month is tuned for."""
    name = 'unskilled'
    skill = 0.2


class Steady(Strategy):
    """Competent cook. Bicycle when it is affordable, mid flat when the landlord says yes."""
    name = 'steady'

    def day(self, p):
        self.housekeeping(p)
        self.hire(p, 0)
        if BIKE not in p.owned and p.money >= T.TR_BUY[BIKE] + 1300:
            p.pay(T.TR_BUY[BIKE], 'transport')
            p.owned.add(BIKE)
        self.maybe_move(p, 1, 1800)
        self.work_day(p)


class SteadyExtras(Steady):
    """The steady worker, same seeds, but takes every optional extra (overtime included)."""
    name = 'steady+x'
    extras = True
    seed_name = b'steady'


class Expert(Steady):
    name = 'expert'
    skill = 1.0


class Hopper(Strategy):
    """Chases the best hourly rate: trains charisma with evenings out, moves cashier -> waiter -> barman."""
    name = 'hopper'
    bus = True

    def best_job(self, p):
        best, bv = 0, -1
        for j in range(len(T.JOB_PAY)):
            st = T.JOB_NEED_STAT[j]
            if st != T.NONE and (p.fit, p.cha, p.edu)[st] < T.JOB_NEED_VALUE[j]:
                continue
            v = T.JOB_PAY[j] * T.RANK_PAY[p.rank(p.xp[j])] * (
                1 + (T.TIP_BASE + p.cha * T.TIP_PER_CHA if T.JOB_TIPS[j] else 0))
            if j in (2, 8):
                v *= 0.9  # a pre-dawn alarm from the far flat
            if v > bv:
                best, bv = j, v
        return best

    def day(self, p):
        self.housekeeping(p)
        p.job = self.best_job(p)
        self.maybe_move(p, 1, 1800)
        extra = []
        if p.job != 3 and p.money > 500 and p.day % 3 == 0:
            extra.append(p.out)
        self.work_day(p, extra)


class Student(Strategy):
    """Cook first, evening courses from month 2, then the developer programme, then the profession."""
    name = 'student'
    target = T.DEVELOPER
    bus = True

    def day(self, p):
        self.housekeeping(p)
        self.hire(p, 0)
        k = self.target
        month = (p.day - 1) // T.DAYS_PER_MONTH + 1
        if p.pro is None and p.school is None and p.edu >= T.PRO_NEED_EDU[k] and (
                T.PRO_NEED_STAT[k] == T.NONE or (p.fit, p.cha, p.edu)[T.PRO_NEED_STAT[k]] >= T.PRO_NEED_VALUE[k]) and (
                not p.record or not T.PRO_CLEAN_RECORD[k]):
            rent = p.rent_now(max(0, p.home)) + T.HOME_BILLS[max(0, p.home)]
            if p.money >= T.PRO_TUITION[k] // 2 + rent + 200:
                p.pay(T.PRO_TUITION[k] // 2, 'school')
                p.school, p.sessions = k, 0
        if p.pro is not None:
            self.maybe_move(p, 2, 3500)
            self.work_day(p)
            return
        if p.school is not None:
            if p.day % 3 != 0:  # two study days in three, a shift after class
                p.study()
                if p.meals < 2:
                    p.shop(3)
                p.shift()
                p.sleep(T.PRO_SESSION_FROM - 60 - walk_min(p.home, 'home', 'school') / T.TR_SPEED[self.mode(p)])
            else:
                self.work_day(p)
            return
        extra = []
        need_edu = p.edu < T.PRO_NEED_EDU[k]
        if month >= 2 and need_edu and p.money > 300 + (0 if month > 2 else 400):
            extra.append(p.course)
        if k == T.LAWYER and p.cha < T.PRO_NEED_VALUE[k] and p.money > 600 and p.day % 3 == 0:
            extra.append(p.out)
        self.work_day(p, extra)


class StudentLawyer(Student):
    name = 'stud-law'
    target = T.LAWYER


class Trader(Strategy):
    """Greedy. Cooks through month 1, then the alley seven nights in ten (no shift on those days:
    the far flat leaves no time for both) and a cook shift on the other three. Keeps the rent in
    cash, trades everything above it plus the guy's front, sells at +15%."""
    name = 'trader'
    sell_at, buy_at, sell_gain = 1.15, 0.85, 1.3
    nights = (0, 1, 3, 4, 6, 7, 9)
    use_front = True
    capital = 600
    started = False

    def reserve(self, p):
        # The rent stays in cash; everything above it is working capital.
        return 150 + p.rent_now(max(0, p.home)) + T.HOME_BILLS[max(0, p.home)]

    def day(self, p):
        self.housekeeping(p)
        self.hire(p, 0)
        free = p.money - self.reserve(p)
        if not self.started:
            self.started = free >= self.capital or (self.use_front and p.day > T.DAYS_PER_MONTH and free >= 0)
        if self.started and p.bag == T.BAG_MAX and free >= T.BACKPACK_PRICE + 800:
            p.pay(T.BACKPACK_PRICE, 'transport')
            p.bag = T.BACKPACK_MAX
        if self.started and p.day % 10 in self.nights and (free >= 200 or sum(p.goods) or self.use_front):
            if p.meals < 2:
                p.shop(4)
            p.alley()
            p.sleep(self.wake(p))
            return
        self.work_day(p)


class CarefulTrader(Trader):
    """Keeps the cook job; three nights in ten in the alley, sells only at a fat margin."""
    name = 'trader-c'
    sell_at, buy_at, sell_gain = 1.25, 0.75, 1.45
    nights = (2, 5, 8)
    use_front = False


class Gambler(Strategy):
    """A steady cook who goes to the casino two nights in five with whatever is above the rent."""
    name = 'gambler'
    bet_frac = 0.15
    bus = True

    def day(self, p):
        self.housekeeping(p)
        self.hire(p, 0)
        extra = [p.casino] if p.day % 5 in (2, 4) else []
        self.work_day(p, extra)


STRATS = [Careless, Comeback, Unskilled, Steady, SteadyExtras, Expert, Hopper, Student, StudentLawyer, Trader, CarefulTrader, Gambler]


# ---------------------------------------------------------------- reporting
def pct(xs, q):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, max(0, int(round(q * (len(xs) - 1)))))]


def fmt(n):
    return ('%d' % round(n)).rjust(6)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--runs', type=int, default=200)
    ap.add_argument('--only')
    ap.add_argument('--verbose', action='store_true')
    ap.add_argument('--seed', type=int, default=7)
    a = ap.parse_args()
    strats = [s for s in STRATS if not a.only or s.name == a.only]
    print('Tuning: %s' % os.path.relpath(TUNING))
    print('%d runs per strategy, %d days, rent every %d days\n' % (a.runs, T.MONTHS * T.DAYS_PER_MONTH,
                                                                    T.DAYS_PER_MONTH))
    results = {}
    for S in strats:
        runs = []
        for i in range(a.runs):
            p = P(S(), a.seed * 100003 + i * 7919 + zlib.crc32(S.seed_name or S.name.encode()) % 1000)
            p.run(verbose=a.verbose and i == 0)
            runs.append(p)
        results[S.name] = runs

    months = T.MONTHS
    print('== SUMMARY (medians unless noted; money after that month\'s rent) ==')
    print('%-10s %6s %6s %6s %6s %6s %6s | %5s %5s %5s %5s %5s %5s | %s' % (
        'strategy', 'm1', 'm2', 'm4', 'm8', 'm12', 'p10m12', 'miss%', 'evic%', 'coll', 'arr/mo', 'pay8', 'x1st',
        'month-1 net'))
    entry = T.JOB_PAY[0]
    for name, runs in results.items():
        def at(m):
            return [r.monthly[m - 1]['money'] if len(r.monthly) >= m else r.money for r in runs]
        miss = 100 * sum(1 for r in runs if r.ev['missed']) / len(runs)
        evic = 100 * sum(1 for r in runs if r.ev['evict']) / len(runs)
        coll = statistics.mean(r.ev['collapse'] for r in runs)
        arr = statistics.mean(r.ev['arrest'] for r in runs) / months
        pay8 = statistics.median(r.monthly[7]['hourly'] for r in runs if len(r.monthly) >= 8)
        first_miss = 100 * sum(1 for r in runs if r.first_miss) / len(runs)
        m1net = statistics.median(r.monthly[0]['money'] for r in runs)
        print('%-10s %s %s %s %s %s %s | %5.0f %5.0f %5.1f %5.2f %5.1f %5.0f | %+d vs rent' % (
            name, fmt(statistics.median(at(1))), fmt(statistics.median(at(2))), fmt(statistics.median(at(4))),
            fmt(statistics.median(at(8))), fmt(statistics.median(at(12))), fmt(pct(at(12), 0.1)), miss, evic, coll,
            arr, pay8 / entry, first_miss, m1net))
    print('\n  m1..m12  cash right after paying rent+bills at the end of that month (m12 = day 120, no rent due)')
    print('  miss%    runs that ever missed a rent   evic% runs evicted at least once   coll  collapses per year')
    print('  arr/mo   arrests per month               pay8  hourly pay at month 8 / cook rank-0 hourly')
    print('  x1st     %% of runs that could not pay the FIRST rent in full\n')

    print('== SAVED PER MONTH (median change in cash, month by month) ==')
    print('%-10s' % 'strategy' + ''.join('%6s' % ('m%d' % m) for m in range(1, months + 1)))
    for name, runs in results.items():
        row = []
        for m in range(1, months + 1):
            d = [(r.monthly[m - 1]['money'] - (r.monthly[m - 2]['money'] if m > 1 else T.START_MONEY))
                 for r in runs if len(r.monthly) >= m]
            row.append(statistics.median(d) if d else 0)
        print('%-10s' % name + ''.join(fmt(v) for v in row))

    print('\n== INCOME PER MONTH (median, wages+tips+profession+alley+casino) ==')
    print('%-10s' % 'strategy' + ''.join('%6s' % ('m%d' % m) for m in range(1, months + 1)))
    for name, runs in results.items():
        print('%-10s' % name + ''.join(
            fmt(statistics.median(r.monthly[m - 1]['income'] for r in runs if len(r.monthly) >= m))
            for m in range(1, months + 1)))

    print('\n== PROGRESS at the end of months 1, 2, 4, 6, 8, 12 (medians) ==')
    print('%-10s %-40s %-28s %-20s %s' % ('strategy', 'rank (3+ = profession rank)', 'edu / cha / fit at m12',
                                         'home tier m4/m8/m12', 'energy m1/m4/m12'))
    for name, runs in results.items():
        def med(m, k):
            vals = [r.monthly[m - 1][k] for r in runs if len(r.monthly) >= m and r.monthly[m - 1][k] is not None]
            return statistics.median(vals) if vals else -1
        ranks = ' '.join('%d' % med(m, 'rank') for m in (1, 2, 4, 6, 8, 12))
        stats = '%2.0f / %2.0f / %2.0f' % (med(12, 'edu'), med(12, 'cha'), med(12, 'fit'))
        homes = '%d/%d/%d' % (med(4, 'home'), med(8, 'home'), med(12, 'home'))
        en = '%2.0f/%2.0f/%2.0f' % (med(1, 'energy'), med(4, 'energy'), med(12, 'energy'))
        prof = sum(1 for r in runs if r.pro is not None) * 100 // len(runs)
        print('%-10s %-40s %-28s %-20s %s%s' % (name, ranks, stats, homes, en,
                                                '   profession reached: %d%%' % prof if prof else ''))

    print('\n== EVENTS and SPENDING per year (means) ==')
    for name, runs in results.items():
        ev = {k: statistics.mean(r.ev[k] for r in runs) for k in runs[0].ev}
        sp = {k: statistics.mean(r.spend[k] for r in runs) for k in runs[0].spend}
        inc = {k: statistics.mean(r.inc[k] for r in runs) for k in runs[0].inc}
        print('%-10s shifts %5.1f  sessions %4.1f courses %4.1f outs %4.1f alley %4.1f casino %4.1f | '
              'collapse %.2f missed %.2f evict %.2f bailout %.2f chase %.2f arrest %.2f scuffle %.2f' % (
                  name, ev['shifts'], ev['sessions'], ev['courses'], ev['outs'], ev['alley'], ev['casino'],
                  ev['collapse'], ev['missed'], ev['evict'], ev['bailout'], ev['chase'], ev['arrest'],
                  ev['scuffle']))
        print('%-10s income: %s' % ('', '  '.join('%s %d' % (k, v) for k, v in inc.items() if v)))
        print('%-10s spend:  %s' % ('', '  '.join('%s %d' % (k, v) for k, v in sp.items() if v)))

    print('\n== EARNINGS PER HOUR OF THE PLAYER\'S TIME (shift or alley visit, travel included, waiting included) ==')
    for name, runs in results.items():
        jh = statistics.mean(r.hours['job'] for r in runs)
        ah = statistics.mean(r.hours['alley'] for r in runs)
        jw = statistics.mean(r.inc['wage'] + r.inc['tips'] + r.inc['pro'] for r in runs)
        aw = statistics.mean(r.inc['alley'] for r in runs)
        line = '%-10s job ₪%5.1f/h over %4.0f h' % (name, jw / jh if jh else 0, jh)
        if ah:
            line += '   alley ₪%5.1f/h over %4.0f h  (x%.1f)' % (aw / ah, ah, (aw / ah) / (jw / jh))
        print(line)

    xs = [n for n in results if results[n][0].s.extras]
    if xs:
        print('\n== OPTIONAL EXTRAS (money-equivalent per month as a share of core pay; band %.0f-%.0f%%) ==' % (
            100 * T.X_MONTH_BAND_MIN, 100 * T.X_MONTH_BAND_MAX))
        for name in xs:
            runs = results[name]
            share = [row['xshare'] for r in runs for row in r.monthly]
            print('%-10s median %.1f%%  p10 %.1f%%  p90 %.1f%%   per year: value ₪%d, extras cash ₪%d, favours unspent %.1f, '
                  'trust +%.1f, gems %.1f' % (name, 100 * statistics.median(share), 100 * pct(share, 0.1),
                                             100 * pct(share, 0.9), statistics.mean(r.x_value for r in runs),
                                             statistics.mean(r.inc['extras'] for r in runs),
                                             statistics.mean(r.favours for r in runs),
                                             statistics.mean(r.trust for r in runs), statistics.mean(r.gems for r in runs)))

    if 'trader' in results:
        runs = results['trader']
        # Share of trader-months that contain at least one arrest.
        per_month = []
        for r in runs:
            prev = 0
            for row in r.monthly:
                per_month.append(1 if row['arrests'] > prev else 0)
                prev = row['arrests']
        print('\ntrader: share of months with an arrest: %.0f%%' % (100 * statistics.mean(per_month)))
    if 'gambler' in results:
        runs = results['gambler']
        net = [r.inc['casino'] for r in runs]
        print('gambler: casino net per year median %d, mean %d, p10 %d, p90 %d; ever up over the year: %.0f%%' % (
            statistics.median(net), statistics.mean(net), pct(net, 0.1), pct(net, 0.9),
            100 * sum(1 for x in net if x > 0) / len(net)))


if __name__ == '__main__':
    main()
