package com.wizzcraft.ima.core;

/**
 * A conversation on screen. One view, five kinds, one grammar:
 *
 * <pre>
 *   FACE     somebody stands in front of you, in a place          (portrait over a backdrop, name plate, card)
 *   CALL     the phone rings; you answer or you do not; a timer    (mom on the line)
 *   TEXTS    a thread of short messages with time stamps           (dad)
 *   LETTER   a note or a letter on paper                           (the landlord's note on the door)
 *   THOUGHT  your own voice, nobody else in the frame
 * </pre>
 *
 * In all of them: what is said appears at reading speed with a soft tick and completes on a tap;
 * the answers (two or three) sit at the bottom under the thumb, show a pressed state and commit on
 * release; what the player answered stays on screen as a trace while the other side reacts.
 *
 * The view owns no story logic. The dialog system feeds it ({@link #open}, {@link #say},
 * {@link #ask}, {@link #incoming} ...) and hears back through {@link Out}. It draws only through Gfx
 * and allocates nothing while drawing: text is wrapped when it is set, not when it is drawn.
 * Anatomy, numbers and integration notes: docs/PRESENTATION.md.
 */
final class TalkView {
    static final int FACE = 0, CALL = 1, TEXTS = 2, LETTER = 3, THOUGHT = 4;

    /** How the speaker feels while saying the line: an emote by the head plus a small movement. */
    static final int CALM = 0, WARM = 1, CROSS = 2, WORRIED = 3, SURPRISED = 4, THINKING = 5, SAD = 6;

    /** Call phases. Every other kind is always LIVE. */
    static final int RINGING = 0, LIVE = 1, ENDED = 2;

    /** What the view tells the dialog system. */
    interface Out {
        /** Play a sound (an {@link Sfx} id). */
        void sfx(int id);

        /** The player committed answer 0..2, or -1: "continue" on a line without answers. */
        void answer(int index);

        /** A ringing call was accepted (true) or declined (false). */
        void call(boolean accepted);

        /** The leave animation has finished; the view may be dropped. */
        void closed();
    }

    private static final Out NOBODY = new Out() {
        @Override
        public void sfx(int id) {
        }

        @Override
        public void answer(int index) {
        }

        @Override
        public void call(boolean accepted) {
        }

        @Override
        public void closed() {
        }
    };

    // ---- touch ids ----
    private static final int T_ANSWER = 0, T_ACCEPT = 4, T_DECLINE = 5, T_BACK = 6, T_CARD = 7;
    private static final float W = Present.W;

    // ---- what is on stage ----
    private Out out = NOBODY;
    private int kind = FACE, phase = LIVE, mood = CALM;
    private String who = "", name = "", role = "", place = "street";
    private int accent = Present.GOLD;
    private boolean drawWorld = true, canDecline = true;
    private float night = 1;
    private String clock = "19:42";
    private float battery = 0.7f;

    // ---- time ----
    private float t, phaseT, lineT, answersT = -1, leaveT = -1, reactT = 9, moodT = 9, callSeconds, endedT;
    private boolean closedSent;

    // ---- the line being said ----
    private String text = "";
    private final Para para = new Para();
    private int shown, ticked;
    private boolean typed = true;
    private float lineDelay;

    // ---- answers and the trace ----
    private final String[] opt = new String[3];
    private int nOpt, picked = -1;
    private float pickT = 9;
    private String trace;
    private boolean traceFresh;
    private float traceT = 9;
    private final Para tracePara = new Para();

    // ---- letter ----
    private String heading = "", signature = "";
    private final Para signPara = new Para();

    // ---- thread ----
    private static final int MAX_MSG = 24;
    private static final int M_IN = 0, M_OUT = 1, M_DAY = 2;

    private static final class Msg {
        int type;
        String text, stamp;
        final Para para = new Para();
        float w, h, stampW, born;
        boolean stampInline, laid;
    }

    private final Msg[] msgs = new Msg[MAX_MSG];
    private int nMsg;
    private String pendingText, pendingStamp;
    private float typingT = -1, typingFor, threadH, threadTarget;
    private String idleStatus = "", typingStatus = "", habit;
    private boolean threadSnap = true;

    private final Taps taps = new Taps();

    TalkView() {
        for (int i = 0; i < MAX_MSG; i++) msgs[i] = new Msg();
    }

    // =============================================================================================
    // Feeding the view
    // =============================================================================================

    void listen(Out o) {
        out = o == null ? NOBODY : o;
    }

    /**
     * Starts a conversation.
     *
     * @param kind  FACE, CALL, TEXTS, LETTER or THOUGHT
     * @param who   portrait id of the other side (a PersonArt cast name); ignored for THOUGHT
     * @param name  the name on the plate ("אמא", "רינה"); for LETTER the heading ("פתק על הדלת")
     * @param role  one or two words under the name ("בעלת המסעדה"), may be empty
     * @param place backdrop id (see {@link Backdrop#PLACES}): where the player is standing
     */
    void open(int kind, String who, String name, String role, String place) {
        this.kind = kind;
        this.who = who == null ? "" : who;
        this.name = name == null ? "" : name;
        this.role = role == null ? "" : role;
        this.place = place == null ? "street" : place;
        accent = Present.accentOf(this.who);
        phase = LIVE;
        mood = CALM;
        t = phaseT = lineT = 0;
        answersT = -1;
        leaveT = -1;
        reactT = moodT = pickT = traceT = 9;
        callSeconds = endedT = 0;
        closedSent = false;
        text = "";
        shown = ticked = 0;
        lineDelay = kind == CALL || kind == TEXTS ? Motion.PHONE_UP : Motion.ENTER;
        nOpt = 0;
        picked = -1;
        trace = null;
        traceFresh = false;
        heading = this.name;
        signature = "";
        nMsg = 0;
        habit = null;
        pendingText = null;
        typingT = -1;
        threadH = 0;
        threadSnap = true;
        taps.cancel();
    }

    /** CALL only: start with the incoming-call screen. Without this the call is already answered. */
    void ring(boolean canDecline) {
        phase = RINGING;
        phaseT = 0;
        this.canDecline = canDecline;
    }

    /** CALL only: the other side hung up, or the story did. Shows "the call ended" and leaves. */
    void hangUp() {
        if (kind != CALL || phase == ENDED) return;
        phase = ENDED;
        phaseT = 0;
        nOpt = 0;
        out.sfx(Sfx.BEEP);
    }

    /** The status bar of the phone: game clock ("19:42") and battery 0..1 (the player's energy). */
    void status(String clock, float battery) {
        this.clock = clock;
        this.battery = battery;
    }

    /** 0 = day, 1 = night, for the backdrop; world = false if the host draws its own screen behind. */
    void world(float night, boolean world) {
        this.night = night;
        drawWorld = world;
    }

    /** The speaker says a line. Any answers on screen go away; call {@link #ask} after it for new ones. */
    void say(String line, int mood) {
        text = line == null ? "" : line;
        this.mood = mood;
        moodT = mood == CALM ? 9 : -lineDelayAfterPick();
        lineT = -lineDelayAfterPick() - (t < lineDelay ? lineDelay - t : 0);
        shown = ticked = 0;
        typed = !Motion.reduce;
        nOpt = picked >= 0 ? nOpt : 0;
        answersT = -1;
        if (traceFresh) traceFresh = false;
        else trace = null;
        if (picked < 0) pickT = 9;
    }

    /** Time the answer needs to become a trace before the reply starts. */
    private float lineDelayAfterPick() {
        return picked >= 0 && pickT < 0.3f ? 0.3f - pickT : 0;
    }

    /** Offers two or three answers (null entries are skipped). They appear when the line is complete. */
    void ask(String a, String b, String c3) {
        nOpt = 0;
        picked = -1;
        pickT = 9;
        if (a != null) opt[nOpt++] = a;
        if (b != null) opt[nOpt++] = b;
        if (c3 != null) opt[nOpt++] = c3;
        answersT = -1;
    }

    void ask(String a, String b) {
        ask(a, b, null);
    }

    /** LETTER only: the line under the text ("שמעון, בעל הבית"). */
    void signedBy(String signature) {
        this.signature = signature == null ? "" : signature;
    }

    // ---- thread ----

    /** TEXTS: what the header shows under the name when idle ("נראה לאחרונה 07:10") and while typing ("מקליד…"). */
    void presence(String idle, String typing) {
        idleStatus = idle == null ? "" : idle;
        typingStatus = typing == null ? "" : typing;
    }

    /**
     * TEXTS: the word this sender ends every message with (dad: "אבא"). The view gives it a line of
     * its own, the way a letter is signed; the writers keep writing it at the end of the sentence.
     */
    void signsWith(String word) {
        habit = word == null || word.isEmpty() ? null : word;
    }

    /** TEXTS: a day separator ("יום 9"). */
    void day(String label) {
        Msg m = push(M_DAY, label, null);
        if (m != null) m.born = threadSnap ? -9 : t;
    }

    /** TEXTS: a message that is already in the thread (history): no typing, no pop. */
    void past(boolean mine, String body, String stamp) {
        Msg m = push(mine ? M_OUT : M_IN, body, stamp);
        if (m != null) m.born = -9;
    }

    /**
     * TEXTS: the other side writes a message now: the typing dots show for a moment (longer for a
     * longer message), then the bubble pops in with a sound. Follow with {@link #ask} for replies.
     */
    void incoming(String body, String stamp) {
        pendingText = body;
        pendingStamp = stamp;
        typingT = -Math.max(lineDelayAfterPick(), t < lineDelay ? lineDelay - t + 0.3f : 0.25f);
        typingFor = Motion.reduce ? 0.3f : Ui.clamp(0.7f + (body == null ? 0 : body.length()) * 0.018f, 0.8f, 1.9f);
        threadSnap = false;
        nOpt = picked >= 0 ? nOpt : 0;
        answersT = -1;
    }

    private Msg push(int type, String body, String stamp) {
        if (nMsg == MAX_MSG) {
            // Drop the oldest: the thread on screen never shows more than a dozen anyway.
            Msg first = msgs[0];
            System.arraycopy(msgs, 1, msgs, 0, MAX_MSG - 1);
            msgs[MAX_MSG - 1] = first;
            nMsg--;
        }
        Msg m = msgs[nMsg++];
        m.type = type;
        String b = body == null ? "" : body;
        if (type == M_IN && habit != null && b.endsWith(" " + habit) && b.length() > habit.length() + 1) {
            b = b.substring(0, b.length() - habit.length() - 1) + "\n" + habit;
        }
        m.text = b;
        m.stamp = stamp;
        m.laid = false;
        m.born = t;
        return m;
    }

    /** Leave: the card sinks, the phone goes back to the pocket. {@link Out#closed} follows. */
    void close() {
        if (leaveT < 0) leaveT = 0;
        taps.cancel();
    }

    // ---- state the host or the bench may ask about ----

    boolean lineDone() {
        return lineT >= 0 && (shown >= para.stops || !typed);
    }

    boolean busy() {
        return !lineDone() || typingT > -5 && pendingText != null || lineDelayAfterPick() > 0;
    }

    int phase() {
        return phase;
    }

    /** Bench only: show an answer as held down. */
    void holdAnswer(int i) {
        taps.hold(T_ANSWER + i);
    }

    /** Bench only: show a call button as held down. */
    void holdAccept() {
        taps.hold(T_ACCEPT);
    }

    // =============================================================================================
    // Time
    // =============================================================================================

    void update(float dt) {
        t += dt;
        phaseT += dt;
        lineT += dt;
        reactT += dt;
        moodT += dt;
        pickT += dt;
        traceT += dt;
        taps.update(dt);
        if (leaveT >= 0) {
            leaveT += dt;
            float dur = kind == CALL || kind == TEXTS ? Motion.PHONE_DOWN : Motion.LEAVE;
            if (leaveT >= dur && !closedSent) {
                closedSent = true;
                out.closed();
            }
            return;
        }
        if (kind == CALL) {
            if (phase == LIVE) callSeconds += dt;
            if (phase == RINGING) {
                // One ring every two seconds; the shell's PHONE sound is the ring itself.
                int before = (int) ((phaseT - dt) / 2f), now = (int) (phaseT / 2f);
                if (phaseT - dt <= 0 || now != before) out.sfx(Sfx.PHONE);
                return;
            }
            if (phase == ENDED) {
                endedT += dt;
                if (endedT > 1.3f) close();
                return;
            }
        }
        if (kind == TEXTS) {
            updateThread(dt);
        } else if (lineT >= 0 && typed && shown < para.stops) {
            int now = para.stopsAt(lineT);
            if (now > shown) {
                // A soft tick every few letters, never on spaces or marks.
                for (int k = shown; k < now; k++) {
                    if (!para.quietStop(k) && ++ticked % Motion.TICK_EVERY == 0) {
                        out.sfx(Sfx.TICK);
                        break;
                    }
                }
                shown = now;
            }
        }
        if (nOpt > 0 && picked < 0 && answersT < 0 && answersReady()) answersT = 0;
        else if (answersT >= 0) answersT += dt;
        if (picked >= 0 && pickT > 0.34f) {
            nOpt = 0;
            picked = -1;
        }
    }

    private boolean answersReady() {
        if (kind == TEXTS) return pendingText == null && typingT < -5;
        return lineDone();
    }

    private void updateThread(float dt) {
        // The thread follows its target height like a list being pushed, not like a switch.
        threadH += (threadTarget - threadH) * (1 - (float) Math.exp(-dt * 13));
        if (Math.abs(threadTarget - threadH) < 0.3f) threadH = threadTarget;
        if (pendingText != null) {
            typingT += dt;
            if (typingT >= typingFor) {
                Msg m = push(M_IN, pendingText, pendingStamp);
                if (m != null) m.born = t;
                pendingText = null;
                typingT = -9;
                out.sfx(Sfx.DING);
            }
        }
    }

    // =============================================================================================
    // Touch
    // =============================================================================================

    void down(float x, float y) {
        if (leaveT >= 0) return;
        taps.down(x, y);
    }

    void move(float x, float y) {
        taps.move(x, y);
    }

    void up(float x, float y) {
        if (leaveT >= 0) return;
        boolean wasBusy = taps.busy();
        int id = taps.up(x, y);
        if (id >= T_ANSWER && id < T_ANSWER + 3) {
            if (answersT >= Motion.LOCK && picked < 0 && id - T_ANSWER < nOpt) pick(id - T_ANSWER);
            return;
        }
        if (id == T_ACCEPT || id == T_DECLINE) {
            if (phase != RINGING || phaseT < Motion.LOCK) return;
            out.sfx(Sfx.TAP);
            if (id == T_ACCEPT) {
                phase = LIVE;
                phaseT = 0;
                lineT = Math.min(lineT, -0.35f);
                out.call(true);
            } else {
                out.call(false);
                close();
            }
            return;
        }
        if (id == T_BACK) {
            out.sfx(Sfx.TAP);
            out.answer(-1);
            return;
        }
        if (wasBusy && id < 0) return;   // a press that was dragged off its button is not a tap on the card
        if (kind == CALL && phase != LIVE) return;
        if (kind == TEXTS) return;
        // A tap anywhere else: finish the line, or go on.
        if (!lineDone()) {
            if (lineT < 0) lineT = 0;
            shown = para.stops;
            typed = false;
        } else if (nOpt == 0 && picked < 0 && lineT > Motion.LOCK) {
            out.sfx(Sfx.TAP);
            out.answer(-1);
        }
    }

    private void pick(int i) {
        picked = i;
        pickT = 0;
        trace = opt[i];
        traceFresh = true;
        traceT = 0;
        reactT = 0;
        out.sfx(Sfx.TAP);
        if (kind == TEXTS) {
            Msg m = push(M_OUT, opt[i], clock);
            if (m != null) m.born = t;
            threadSnap = false;
        }
        out.answer(i);
    }

    /** The app went to the background: drop any press in progress. */
    void pause() {
        taps.cancel();
    }

    // =============================================================================================
    // Drawing
    // =============================================================================================

    /** Draws the conversation into the band top..bottom (top is 0 for a full screen, or the HUD height). */
    void draw(Gfx c, float top, float bottom) {
        taps.begin();
        float h = bottom - top;
        boolean phoneKind = kind == CALL || kind == TEXTS;
        float inP = Motion.in(t, 0, phoneKind ? Motion.PHONE_UP : Motion.ENTER);
        float outP = leaveT < 0 ? 0 : Motion.clamp01(leaveT / (phoneKind ? Motion.PHONE_DOWN : Motion.LEAVE));
        float vis = Motion.outCubic(inP) * (1 - Motion.inCubic(outP));

        if (drawWorld) Backdrop.draw(c, place, top, bottom, t, night);
        switch (kind) {
            case CALL, TEXTS -> {
                c.fillRect(0, top, W, h, Present.alpha(Present.BLACK, 0.55f * vis));
                float drop = Motion.reduce ? 0 : (1 - vis) * h;
                if (Motion.reduce && vis < 0.5f) return;
                if (kind == CALL) drawCall(c, top, bottom, drop);
                else drawTexts(c, top, bottom, drop);
            }
            case LETTER -> drawLetter(c, top, bottom, vis);
            case THOUGHT -> drawThought(c, top, bottom, vis);
            default -> drawFace(c, top, bottom, vis);
        }
    }

    // ---- shared blocks --------------------------------------------------------------------------

    private float answersHeight() {
        return nOpt == 0 ? 0 : nOpt * Present.BTN_H + (nOpt - 1) * Present.G2;
    }

    /**
     * The answers, stacked, last one at {@code bottomY}. Each rises into place one after another when
     * the line is complete; until then only their empty slots show, so the layout never jumps.
     */
    private void drawAnswers(Gfx c, float x, float w, float bottomY, float a) {
        if (nOpt == 0) return;
        float y0 = bottomY - answersHeight();
        for (int i = 0; i < nOpt; i++) {
            float y = y0 + i * (Present.BTN_H + Present.G2);
            if (picked >= 0) {
                // The chosen answer lifts off towards the trace; the others just go.
                float p = Motion.clamp01(pickT / 0.2f);
                if (i == picked) Present.button(c, x, y - Motion.away(1 - p, 10), w, Present.BTN_H, opt[i], Present.ANSWER, 1 - p, a * (1 - p));
                else Present.button(c, x, y, w, Present.BTN_H, opt[i], Present.ANSWER, 0, a * (1 - p) * 0.6f);
                continue;
            }
            if (answersT < 0) {
                c.strokeRoundRect(x + 0.75f, y + 0.75f, w - 1.5f, Present.BTN_H - 1.5f, Present.RADIUS, 1.5f,
                        Present.alpha(Present.S3, 0.45f * a));
                continue;
            }
            float p = Motion.in(answersT, i * Motion.STAGGER, Motion.ENTER);
            float locked = answersT < Motion.LOCK ? 0.75f : 1;
            Present.button(c, x, y + Motion.away(p, Motion.SLIDE), w, Present.BTN_H, opt[i], Present.ANSWER,
                    taps.pressed(T_ANSWER + i), a * Motion.outCubic(p) * locked);
            c.strokeRoundRect(x + 0.75f, y + 0.75f, w - 1.5f, Present.BTN_H - 1.5f, Present.RADIUS, 1.5f,
                    Present.alpha(Present.S3, 0.45f * a * (1 - Motion.outCubic(p))));
            taps.add(T_ANSWER + i, x, y, w, Present.BTN_H);
        }
    }

    /** The little "go on" mark at the end of a finished line that has no answers. */
    private void drawMore(Gfx c, float x, float y, int col) {
        if (!lineDone() || nOpt > 0 || picked >= 0) return;
        float bob = Motion.pulse(t, 1.0f) * 3;
        Present.chevron(c, x, y + bob, 5, 0, 2, col);
    }

    /** The trace of the player's answer: their own small bubble, on their side (the left). */
    private void drawTrace(Gfx c, float x, float bottomY, float maxW, float a) {
        if (trace == null) return;
        tracePara.set(c, trace, maxW - 24, Present.LABEL, false);
        float p = Motion.in(traceT, 0.08f, Motion.ENTER);
        float w = tracePara.width + 24, h = tracePara.height() + 12;
        float y = bottomY - h + Motion.away(p, 18);
        float al = a * Motion.outCubic(p);
        c.fillRoundRect(x, y, w, h, 12, Present.alpha(Present.ME, al));
        Present.tri(c, x + 2, y + h - 10, x + 14, y + h - 2, x - 5, y + h + 3, Present.alpha(Present.ME, al));
        c.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1, h - 1, 12, 1, Present.alpha(Present.ME_EDGE, 0.5f * al));
        tracePara.draw(c, x + w - 12, y + 6 + Present.LABEL * 1.02f, Present.alpha(Present.T1, al), Gfx.RIGHT);
    }

    /** The emote by the speaker's head. (cx, cy) is the centre of its little dark bubble. */
    private void drawMood(Gfx c, float cx, float cy, float s) {
        if (mood == CALM || moodT < 0) return;
        float p = Motion.in(moodT, 0, Motion.EMPHASIS);
        float k = Motion.outBack(p) * s;
        if (k <= 0.01f) return;
        float bob = (float) Math.sin(t * 2.2f) * (Motion.reduce ? 0 : 1.2f);
        cy += bob;
        c.fillCircle(cx + 9 * k, cy + 13 * k, 3 * k, 0xE61A1F28);
        c.fillCircle(cx, cy, 14 * k, 0xE61A1F28);
        switch (mood) {
            case WARM -> Present.heart(c, cx, cy + 0.5f * k, 6.2f * k * (1 + 0.08f * Motion.pulse(t, 1.2f)), 0xFFFF6F91);
            case CROSS -> {
                int col = 0xFFFF7A7A;
                float d = 6.5f * k, g = 2.2f * k;
                c.line(cx - d, cy - g, cx - g, cy - g, 2.2f * k, col);
                c.line(cx - g, cy - g, cx - g, cy - d, 2.2f * k, col);
                c.line(cx + d, cy - g, cx + g, cy - g, 2.2f * k, col);
                c.line(cx + g, cy - g, cx + g, cy - d, 2.2f * k, col);
                c.line(cx - d, cy + g, cx - g, cy + g, 2.2f * k, col);
                c.line(cx - g, cy + g, cx - g, cy + d, 2.2f * k, col);
                c.line(cx + d, cy + g, cx + g, cy + g, 2.2f * k, col);
                c.line(cx + g, cy + g, cx + g, cy + d, 2.2f * k, col);
            }
            case WORRIED -> {
                int col = 0xFF7DC4FF;
                c.fillCircle(cx, cy + 2.5f * k, 4.6f * k, col);
                Present.tri(c, cx - 4.2f * k, cy + 0.8f * k, cx + 4.2f * k, cy + 0.8f * k, cx, cy - 8 * k, col);
                c.fillCircle(cx - 1.6f * k, cy + 3 * k, 1.3f * k, 0xAAFFFFFF);
            }
            case SURPRISED -> {
                c.fillRoundRect(cx - 2 * k, cy - 8 * k, 4 * k, 10 * k, 2 * k, Present.GOLD);
                c.fillCircle(cx, cy + 6 * k, 2.3f * k, Present.GOLD);
            }
            case THINKING -> {
                for (int i = 0; i < 3; i++) {
                    float on = Motion.reduce ? 1 : Motion.clamp01((moodT * 2f - i * 0.5f) % 3f);
                    c.fillCircle(cx + (i - 1) * 6.5f * k, cy, 2.3f * k, Present.alpha(Present.T1, 0.35f + 0.65f * on));
                }
            }
            default -> {
                // SAD: a sigh, a small cloud drifting away.
                float d = Motion.reduce ? 0 : (moodT * 3) % 6;
                c.fillCircle(cx - 3 * k - d * 0.3f, cy + 1 * k, 4 * k, 0xFF9AA4B8);
                c.fillCircle(cx + 2 * k - d * 0.3f, cy - 1 * k, 5 * k, 0xFF9AA4B8);
                c.fillCircle(cx + 6 * k - d * 0.3f, cy + 2 * k, 3 * k, 0xFF9AA4B8);
            }
        }
    }

    /** Vertical movement of the portrait: breathing while talking, the reaction to an answer, the mood. */
    private float portraitLift() {
        float lift = 0;
        boolean talking = lineT >= 0 && typed && shown < para.stops;
        if (talking && !Motion.reduce) lift += (float) Math.sin(t * 13.0f) * 0.7f;
        if (reactT < Motion.REACT) lift += Motion.hump(reactT / Motion.REACT) * 7;
        if (moodT >= 0 && moodT < Motion.REACT) {
            float p = moodT / Motion.REACT;
            if (mood == WARM || mood == SURPRISED) lift += Motion.hump(p) * (mood == SURPRISED ? 9 : 5);
            if (mood == SAD || mood == WORRIED) lift -= Motion.hump(p) * 3;
        }
        return lift;
    }

    private float portraitShift() {
        if (mood == CROSS && moodT >= 0 && moodT < 0.5f) return Motion.wobble(moodT / 0.5f, 1.2f) * 5;
        return 0;
    }

    private void layoutLine(Gfx c, float maxW, float size) {
        para.set(c, text, maxW, size, typed);
        if (!typed) shown = para.stops;
    }

    private int shownStops() {
        return typed ? (lineT < 0 ? 0 : shown) : Integer.MAX_VALUE;
    }

    // ---- FACE -----------------------------------------------------------------------------------

    private void drawFace(Gfx c, float top, float bottom, float vis) {
        float h = bottom - top;
        // Lower the lights where the card will sit.
        Present.vgrad(c, 0, top + h * 0.34f, W, h * 0.66f, 0x00000000 | (Present.S0 & 0xFFFFFF), Present.alpha(Present.S0, 0.96f));
        c.fillRect(0, top, W, h, Present.alpha(Present.BLACK, 0.18f));

        float x = Present.G4, w = W - 2 * Present.G4;
        float size = h < 620 && text.length() > 150 ? Present.BODY : Present.SPEECH;
        layoutLine(c, w - 36, size);
        float ansH = answersHeight();
        float cardH = Math.max(para.height() + 34, 2 * para.lead + 34);
        float cardBottom = bottom - Present.G4 - (ansH > 0 ? ansH + Present.G3 : 0);
        float cardY = cardBottom - cardH + Motion.away(vis, Motion.RISE);
        float room = cardY - top - 20;
        float ps = Ui.clamp(room / 1.02f, 96, 250);
        float px = W / 2 + 34 + portraitShift();
        float py = cardY + 8 - ps * 0.516f - portraitLift() + (1 - vis) * ps * 0.5f;

        // The speaker stands behind the card: the card's top edge cuts the bust.
        c.fillRoundRect(px - ps * 0.42f, cardY - 6, ps * 0.84f, 14, 7, Present.alpha(Present.BLACK, 0.35f * vis));
        PersonArt.portrait(c, who, px, py, ps, t);
        drawMood(c, px - ps * 0.40f, py - ps * 0.36f, Ui.clamp(ps / 190f, 0.75f, 1.1f));

        drawTrace(c, x + 2, cardY - 12, w * 0.5f, vis);

        Present.card(c, x, cardY, w, cardH, Present.CARD_R, Present.S1, vis);
        // Name plate, hooked on the card's top edge at the start side.
        float nameW = c.textWidth(name, Present.BODY);
        float roleW = role.isEmpty() ? 0 : c.textWidth(role, Present.MICRO) + 8;
        float plateW = nameW + roleW + 26, plateX = x + w - 14 - plateW, plateY = cardY - 15;
        c.fillRoundRect(plateX, plateY + 2, plateW, 28, 14, Present.alpha(Present.BLACK, 0.3f * vis));
        c.fillRoundRect(plateX, plateY, plateW, 28, 14, Present.alpha(accent, vis));
        c.text(name, plateX + plateW - 13, plateY + 19.5f, Present.BODY, Present.alpha(Present.ON_GOLD, vis), Gfx.RIGHT);
        if (!role.isEmpty()) {
            c.text(role, plateX + 13, plateY + 18.5f, Present.MICRO, Present.alpha(0xFF2A2210, vis), Gfx.LEFT);
        }
        para.draw(c, x + w - 18, cardY + 22 + size, Present.alpha(Present.T1, vis), Gfx.RIGHT, shownStops());
        drawMore(c, x + 22, cardBottom - 16, Present.alpha(accent, vis));
        taps.add(T_CARD, x, cardY, w, cardH);
        drawAnswers(c, x, w, bottom - Present.G4 + (1 - vis) * 30, vis);
    }

    // ---- CALL -----------------------------------------------------------------------------------

    private static final String[] MMSS = new String[60 * 60];

    private static String mmss(float seconds) {
        int s = Math.max(0, Math.min(MMSS.length - 1, (int) seconds));
        String v = MMSS[s];
        if (v == null) {
            v = (s / 60 < 10 ? "0" : "") + s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
            MMSS[s] = v;
        }
        return v;
    }

    private void drawCall(Gfx c, float top, float bottom, float drop) {
        int tint = Present.mix(Present.S0, accent, 0.30f), deep = Present.mix(Present.S0, accent, 0.05f);
        Present.phone(c, top, bottom + drop, drop, tint, clock, battery);
        float sx = Present.scrX, sy = Present.scrY + Present.STATUS_H, sw = Present.scrW;
        float sb = bottom + drop;
        Present.vgrad(c, sx, sy, sw, sb - sy, tint, deep);
        if (phase == RINGING) drawRinging(c, sx, sy, sw, sb);
        else drawLiveCall(c, sx, sy, sw, sb);
    }

    private void drawRinging(Gfx c, float sx, float sy, float sw, float sb) {
        float h = sb - sy, cx = sx + sw / 2;
        // Caller and plate sit as one group, centred in what is left above the two buttons.
        float free = h - 176 - 40;
        float ps = Ui.clamp(free - 70, 110, 250);
        float pcy = sy + 40 + Math.max(0, (free - ps - 62) * 0.45f) + ps * 0.5f;
        c.text("שיחה נכנסת", cx, sy + 26, Present.SMALL, Present.T2, Gfx.CENTER);
        // Rings going out from the caller, one a second: the call is asking.
        for (int i = 0; i < 3; i++) {
            float p = Motion.ripple(phaseT, 0.45f, i / 3f);
            Present.ring(c, cx, pcy - ps * 0.03f, ps * 0.48f + p * 54, 2.5f, Present.alpha(accent, 0.55f * (1 - p)));
        }
        Present.glow(c, cx, pcy, ps * 0.8f, accent, 0.9f);
        float bob = Motion.reduce ? 0 : (float) Math.sin(phaseT * 2.0f) * 1.5f;
        PersonArt.portrait(c, who, cx, pcy + bob, ps, t);
        // The plate the bust stands on.
        float plateY = pcy + ps * 0.516f - 10, plateW = Math.min(sw - 48, 236);
        Present.card(c, cx - plateW / 2, plateY, plateW, 72, 22, Present.S1, 1);
        c.text(name, cx, plateY + 36, 30, Present.T1, Gfx.CENTER);
        if (!role.isEmpty()) c.text(role, cx, plateY + 58, Present.SMALL, Present.T2, Gfx.CENTER);

        // Accept on the right (the thumb's own side), decline on the left.
        float by = sb - 104, r = 36, ax = cx + 84, dx = cx - 84;
        float lock = phaseT < Motion.LOCK ? 0.6f : 1;
        float pa = taps.pressed(T_ACCEPT), pd = taps.pressed(T_DECLINE);
        float beat = Motion.pulse(phaseT, 0.9f);
        for (int i = 0; i < 2; i++) {
            float p = Motion.ripple(phaseT, 0.9f, i * 0.5f);
            Present.ring(c, ax, by, r + 4 + p * 22, 2, Present.alpha(Present.GOOD, 0.5f * (1 - p)));
        }
        float ra = r * (1 - 0.04f * pa) * (1 + 0.035f * beat);
        c.fillCircle(ax, by + 3, ra, 0x55000000);
        c.fillCircle(ax, by, ra, Present.alpha(Present.mix(Present.ACCEPT, Present.ACCEPT_P, pa), lock));
        float wig = Motion.reduce ? 0 : (float) Math.sin(phaseT * 2 * Math.PI * 2.2f) * 9 * Motion.hump((phaseT % 2f) / 0.9f > 1 ? 0 : (phaseT % 2f) / 0.9f);
        Present.handset(c, ax, by + pa, 15, -135 + wig, Present.WHITE);
        c.text("לענות", ax, by + r + 24, Present.LABEL, Present.T1, Gfx.CENTER);
        taps.add(T_ACCEPT, ax - r, by - r, r * 2, r * 2);
        if (canDecline) {
            float rd = r * (1 - 0.04f * pd);
            c.fillCircle(dx, by + 3, rd, 0x55000000);
            c.fillCircle(dx, by, rd, Present.alpha(Present.mix(Present.DECLINE, Present.DECLINE_P, pd), lock));
            Present.handset(c, dx, by - 4 + pd, 15, 0, Present.WHITE);
            c.text("לא עכשיו", dx, by + r + 24, Present.LABEL, Present.T1, Gfx.CENTER);
            taps.add(T_DECLINE, dx - r, by - r, r * 2, r * 2);
        }
    }

    private void drawLiveCall(Gfx c, float sx, float sy, float sw, float sb) {
        float cx = sx + sw / 2, x = sx + 14, w = sw - 28;
        float h = sb - sy;
        float size = h < 600 && text.length() > 150 ? Present.BODY : Present.SPEECH;
        layoutLine(c, w - 36, size);
        float ansH = answersHeight();
        float cardH = Math.max(para.height() + 34, 2 * para.lead + 34);
        float cardBottom = sb - Present.G4 - (ansH > 0 ? ansH + Present.G3 : 0);
        float cardY = cardBottom - cardH;
        boolean ended = phase == ENDED;
        float appear = Motion.outCubic(Motion.in(phaseT, 0.05f, Motion.ENTER));
        if (ended) appear = 1 - Motion.inCubic(Motion.in(phaseT, 0, Motion.LEAVE));

        // The caller: bust on a plate that carries the name, "on the line" and the timer.
        float plateH = 46, traceRoom = trace != null ? 40 : 14;
        float room = cardY - traceRoom - sy;
        float ps = Ui.clamp(room - plateH - 14, 84, 236);
        float pcy = sy + 8 + Math.max(0, (room - ps - plateH - 8) * 0.5f) + ps * 0.5f;
        float talk = lineT >= 0 && typed && shown < para.stops ? 1 : 0;
        Present.glow(c, cx, pcy, ps * 0.75f, accent, 0.5f + 0.25f * talk * Motion.pulse(t, 1.6f));
        PersonArt.portrait(c, who, cx + portraitShift(), pcy - portraitLift(), ps, t);
        if (ended) c.fillCircle(cx, pcy - ps * 0.03f, ps * 0.49f, 0x88000000);
        drawMood(c, cx - ps * 0.44f, pcy - ps * 0.34f, Ui.clamp(ps / 170f, 0.8f, 1.1f));

        float plateY = pcy + ps * 0.516f - 12, plateW = Math.min(w - 40, 232);
        Present.card(c, cx - plateW / 2, plateY, plateW, plateH, 23, Present.S1, 1);
        float rx = cx + plateW / 2 - 18;
        c.text(name, rx, plateY + 29, Present.H2, Present.T1, Gfx.RIGHT);
        float nameW = c.textWidth(name, Present.H2);
        String state = ended ? "השיחה הסתיימה" : "על הקו";
        c.text(state, rx - nameW - 8, plateY + 28, Present.SMALL, ended ? Present.T2 : Present.GOOD, Gfx.RIGHT);
        // Timer at the far (left) end with the voice bars beside it.
        float lx = cx - plateW / 2 + 16;
        c.text(mmss(callSeconds), lx, plateY + 28, Present.SMALL, Present.T1, Gfx.LEFT);
        float tw = c.textWidth("00:00", Present.SMALL);
        for (int i = 0; i < 4; i++) {
            float lvl = ended ? 0.12f : 0.18f + talk * 0.82f * (0.35f + 0.65f * Motion.pulse(t + i * 0.37f, 1.3f + i * 0.31f));
            float bh = 3 + lvl * 13;
            c.fillRoundRect(lx + tw + 8 + i * 5, plateY + 23 - bh / 2, 3, bh, 1.5f, ended ? Present.T3 : Present.GOOD);
        }

        drawTrace(c, x + 2, cardY - 10, w * 0.62f, appear);
        Present.card(c, x, cardY + Motion.away(appear, Motion.RISE), w, cardH, Present.CARD_R, Present.S1, appear);
        para.draw(c, x + w - 18, cardY + 22 + size + Motion.away(appear, Motion.RISE), Present.alpha(Present.T1, appear),
                Gfx.RIGHT, shownStops());
        drawMore(c, x + 22, cardBottom - 16, Present.alpha(accent, appear));
        taps.add(T_CARD, x, cardY, w, cardH);
        drawAnswers(c, x, w, sb - Present.G4, appear);
    }

    // ---- TEXTS ----------------------------------------------------------------------------------

    private static final float BUBBLE_TEXT = 16, BUBBLE_R = 16, HEADER_H = 58;

    private void layoutMsg(Gfx c, Msg m, float maxW) {
        if (m.laid) return;
        m.laid = true;
        if (m.type == M_DAY) {
            m.w = c.textWidth(m.text, Present.MICRO) + 24;
            m.h = 24;
            return;
        }
        m.para.set(c, m.text, maxW - 24, BUBBLE_TEXT, false);
        m.para.lead = BUBBLE_TEXT * 1.42f;
        m.stampW = m.stamp == null ? 0 : c.textWidth(m.stamp, Present.MICRO) + (m.type == M_OUT ? 16 : 0);
        float lastW = m.para.n == 0 ? 0 : m.para.lineW[m.para.n - 1];
        float inner = m.para.width;
        m.stampInline = m.stampW > 0 && lastW + 14 + m.stampW <= maxW - 24;
        if (m.stampInline) inner = Math.max(inner, lastW + 14 + m.stampW);
        else inner = Math.max(inner, m.stampW);
        m.w = Math.max(56, inner + 24);
        m.h = m.para.height() + 14 + (m.stampInline || m.stampW == 0 ? 0 : 16);
    }

    private void drawTexts(Gfx c, float top, float bottom, float drop) {
        Present.phone(c, top, bottom + drop, drop, Present.S1, clock, battery);
        float sx = Present.scrX, sy = Present.scrY + Present.STATUS_H, sw = Present.scrW, sb = bottom + drop;

        // Reply zone height first: the thread ends where it begins.
        float ansH = answersHeight();
        float zoneH = (ansH > 0 ? ansH : Present.BTN_H) + Present.G3 + Present.G4;
        float threadTop = sy + HEADER_H, threadBottom = sb - zoneH;
        c.fillRect(sx, threadTop, sw, sb - threadTop, Present.S0);

        // Lay out and measure the thread.
        float maxW = sw * 0.76f, total = 10;
        for (int i = 0; i < nMsg; i++) {
            Msg m = msgs[i];
            layoutMsg(c, m, maxW);
            total += m.h + gapBefore(i);
        }
        boolean dots = pendingText != null && typingT >= 0;
        if (dots) total += 38 + 6;
        // The thread grows smoothly: new things come up from behind the reply zone.
        threadTarget = total;
        if (threadSnap || Motion.reduce) threadH = total;
        threadSnap = false;

        float y = threadBottom - threadH + 10;
        for (int i = 0; i < nMsg; i++) {
            Msg m = msgs[i];
            y += gapBefore(i);
            if (y + m.h > threadTop && y < sb) drawMsg(c, m, i, sx, sw, y, threadTop);
            y += m.h;
        }
        if (dots) drawTypingDots(c, sx + sw - 14 - 58, y + 6);

        // Header over the thread (it hides whatever scrolled up), then the reply zone.
        c.fillRect(sx, sy, sw, HEADER_H, Present.S1);
        c.fillRect(sx, sy + HEADER_H - 1, sw, 1, Present.S2);
        float ax = sx + sw - 16 - 22, ay = sy + HEADER_H / 2 - 2;
        Present.avatar(c, who, ax, ay, 44, t, Present.mix(Present.S0, accent, 0.45f), Present.S1, 0);
        c.text(name, ax - 32, ay - 1, Present.H2, Present.T1, Gfx.RIGHT);
        boolean isTyping = pendingText != null && typingT >= 0;
        c.text(isTyping ? typingStatus : idleStatus, ax - 32, ay + 17, Present.MICRO, isTyping ? Present.GOOD : Present.T2, Gfx.RIGHT);
        Present.chevron(c, sx + 26, ay, 6, 1, 2.2f, Present.T2);

        c.fillRect(sx, threadBottom, sw, sb - threadBottom + 40, Present.S1);
        c.fillRect(sx, threadBottom, sw, 1, Present.S2);
        float bx = sx + 14, bw = sw - 28;
        if (nOpt > 0) {
            drawAnswers(c, bx, bw, sb - Present.G4, 1);
        } else {
            Present.button(c, bx, sb - Present.G4 - Present.BTN_H, bw, Present.BTN_H, "חזרה", Present.QUIET, taps.pressed(T_BACK), 1);
            taps.add(T_BACK, bx, sb - Present.G4 - Present.BTN_H, bw, Present.BTN_H);
        }
        // A back target on the header chevron as well, for the hand that is up there anyway.
        if (nOpt == 0) taps.add(T_BACK + 2, sx, sy, 56, HEADER_H);
    }

    private float gapBefore(int i) {
        if (i == 0) return 0;
        Msg m = msgs[i], prev = msgs[i - 1];
        if (m.type == M_DAY || prev.type == M_DAY) return 12;
        return m.type == prev.type ? 4 : 12;
    }

    private void drawMsg(Gfx c, Msg m, int index, float sx, float sw, float y, float threadTop) {
        float age = t - m.born;
        float p = Motion.in(age, 0, Motion.ENTER);
        float a = m.born < -1 ? 1 : Motion.outCubic(p);
        if (m.type == M_DAY) {
            if (y < threadTop - 20) return;
            float x = sx + sw / 2 - m.w / 2;
            c.fillRoundRect(x, y, m.w, m.h, 12, Present.alpha(Present.S1, a));
            c.text(m.text, sx + sw / 2, y + 16.5f, Present.MICRO, Present.alpha(Present.T2, a), Gfx.CENTER);
            return;
        }
        boolean mine = m.type == M_OUT;
        boolean lastOfGroup = index == nMsg - 1 || msgs[index + 1].type != m.type;
        float k = m.born < -1 ? 1 : 0.86f + 0.14f * Motion.outBack(p);
        float w = m.w * k, h = m.h * k;
        // The bubble grows out of its own corner: right for them, left for the player.
        float x = mine ? sx + 14 : sx + sw - 14 - w;
        float by = y + (m.h - h);
        int fill = Present.alpha(mine ? Present.ME : Present.THEM, a);
        // An old bubble leaving under the header is cut there: nothing may reach the status bar.
        float cut = Math.max(by, threadTop - 24);
        c.fillRoundRect(x, cut, w, by + h - cut, BUBBLE_R, fill);
        if (lastOfGroup) {
            if (mine) Present.tri(c, x + 1, by + h - 12, x + 14, by + h - 1, x - 6, by + h + 2, fill);
            else Present.tri(c, x + w - 1, by + h - 12, x + w - 14, by + h - 1, x + w + 6, by + h + 2, fill);
        }
        if (k < 0.97f) return;
        float tx = x + w - 12, ty = by + 7 + BUBBLE_TEXT * 1.02f;
        int ink = Present.alpha(Present.T1, a);
        for (int i = 0; i < m.para.n; i++) {
            float ly = ty + i * m.para.lead;
            if (ly > threadTop - 30) c.text(m.para.line[i], tx, ly, BUBBLE_TEXT, ink, Gfx.RIGHT);
        }
        if (m.stamp != null) {
            float sy2 = m.stampInline ? ty + (m.para.n - 1) * m.para.lead : by + h - 8;
            int sc = Present.alpha(mine ? 0xFFC5CCE0 : Present.T2, a);
            float sxl = x + 12;
            if (mine) {
                // One tick when sent, two a moment later: delivered.
                Present.ticks(c, sxl, sy2 - 4, m.born < -1 || age > 0.9f, sc);
                sxl += 15;
            }
            c.text(m.stamp, sxl, sy2, Present.MICRO, sc, Gfx.LEFT);
        }
    }

    private void drawTypingDots(Gfx c, float x, float y) {
        float p = Motion.in(typingT, 0, Motion.ENTER);
        float a = Motion.outCubic(p);
        int fill = Present.alpha(Present.THEM, a);
        c.fillRoundRect(x, y, 58, 36, BUBBLE_R, fill);
        Present.tri(c, x + 57, y + 24, x + 44, y + 35, x + 64, y + 38, fill);
        for (int i = 0; i < 3; i++) {
            float up = Motion.reduce ? 0 : Math.max(0, (float) Math.sin((typingT * 1.4f - i * 0.18f) * Math.PI * 2)) * 4;
            c.fillCircle(x + 41 - i * 12, y + 19 - up, 3.2f, Present.alpha(Present.T2, a));
        }
    }

    // ---- LETTER ---------------------------------------------------------------------------------

    private void drawLetter(Gfx c, float top, float bottom, float vis) {
        float h = bottom - top;
        c.fillRect(0, top, W, h, Present.alpha(Present.BLACK, 0.62f * vis));
        float x = 26, w = W - 52;
        float size = Present.SPEECH;
        layoutLine(c, w - 44, size);
        signPara.set(c, signature, w - 44, Present.BODY, false);
        float ansH = answersHeight();
        float foot = Present.G4 + (ansH > 0 ? ansH : Present.BTN_H) + Present.G5;
        float paperH = 58 + para.height() + (signature.isEmpty() ? 0 : 20 + signPara.height()) + 26;
        float py = top + Math.max(28, (h - foot - paperH) * 0.45f) + Motion.away(vis, 40);

        // The sheet: a shadow, the paper, a fold across the middle, a strip of tape.
        Present.shadow(c, x, py, w, paperH, 3, 1.4f * vis);
        Present.quad(c, x, py, x + w, py + 2, x + w - 1, py + paperH, x + 2, py + paperH - 2, Present.alpha(Present.PAPER, vis));
        float fold = py + paperH * 0.52f;
        Present.quad(c, x + 1, fold, x + w - 1, fold + 1, x + w - 1, py + paperH, x + 2, py + paperH - 2,
                Present.alpha(Present.PAPER_SHADE, 0.55f * vis));
        c.fillRect(x + 1, fold, w - 2, 1, Present.alpha(0xFF000000, 0.10f * vis));
        Present.quad(c, W / 2 - 30, py - 9, W / 2 + 28, py - 7, W / 2 + 30, py + 11, W / 2 - 28, py + 9,
                Present.alpha(0xFFE9DDA8, 0.80f * vis));
        // Heading: what kind of paper this is, small, with a rule under it.
        float rx = x + w - 22;
        c.text(heading, rx, py + 34, Present.SMALL, Present.alpha(Present.PAPER_DIM, vis), Gfx.RIGHT);
        c.fillRect(x + 22, py + 42, w - 44, 1, Present.alpha(Present.PAPER_RULE, vis));
        para.draw(c, rx, py + 52 + size, Present.alpha(Present.PAPER_INK, vis), Gfx.RIGHT, shownStops());
        if (!signature.isEmpty() && lineDone()) {
            // Hebrew letters are signed at the end of the line: the left.
            float sy = py + 58 + para.height() + 20;
            signPara.draw(c, x + 22, sy + 12, Present.alpha(Present.PAPER_INK, vis), Gfx.LEFT);
            c.line(x + 22, sy + 19, x + 22 + signPara.width, sy + 17, 1.2f, Present.alpha(Present.PAPER_INK, 0.55f * vis));
        }
        taps.add(T_CARD, x, py, w, paperH);
        float bx = Present.G4, bw = W - 2 * Present.G4;
        if (nOpt > 0) {
            drawAnswers(c, bx, bw, bottom - Present.G4, vis);
        } else if (lineDone()) {
            float a = Motion.outCubic(Motion.in(lineT - para.duration(), 0.1f, Motion.ENTER)) * vis;
            if (!typed) a = vis;
            Present.button(c, bx, bottom - Present.G4 - Present.BTN_H, bw, Present.BTN_H, "הבנתי", Present.SECONDARY,
                    taps.pressed(T_BACK), a);
            taps.add(T_BACK, bx, bottom - Present.G4 - Present.BTN_H, bw, Present.BTN_H);
        }
    }

    // ---- THOUGHT --------------------------------------------------------------------------------

    private void drawThought(Gfx c, float top, float bottom, float vis) {
        float h = bottom - top;
        // The world goes quiet: dark at the edges, a little light left in the middle.
        c.fillRect(0, top, W, h, Present.alpha(Present.BLACK, 0.58f * vis));
        Present.vgrad(c, 0, top, W, h * 0.3f, Present.alpha(Present.BLACK, 0.5f * vis), 0x00000000);
        Present.vgrad(c, 0, bottom - h * 0.4f, W, h * 0.4f, 0x00000000, Present.alpha(Present.BLACK, 0.6f * vis));
        float size = 18;
        layoutLine(c, W - 88, size);
        float ansH = answersHeight();
        float foot = Present.G4 + (ansH > 0 ? ansH + Present.G5 : 40);
        float blockH = para.height();
        float cy = top + (h - foot) * 0.5f;
        float y0 = cy - blockH / 2;
        // Thought bubbles rise from where the player is (below, a little to the left) to the words.
        for (int i = 0; i < 3; i++) {
            float p = Motion.outBack(Motion.in(t, 0.05f + i * 0.09f, Motion.EMPHASIS));
            float r = (3.5f + i * 2.6f) * p;
            float bob = Motion.reduce ? 0 : (float) Math.sin(t * 1.3f + i) * 1.5f;
            c.fillCircle(W / 2 - 40 + i * 12, y0 + blockH + 62 - i * 17 + bob, r, Present.alpha(Present.T1, 0.20f * vis));
        }
        c.fillRoundRect(28, y0 - 26, W - 56, blockH + 50, 26, Present.alpha(Present.T1, 0.07f * vis));
        para.draw(c, W / 2, y0 + size, Present.alpha(Present.T1, vis), Gfx.CENTER, shownStops());
        drawMore(c, W / 2, y0 + blockH + 8, Present.alpha(Present.T2, vis));
        taps.add(T_CARD, 0, top, W, h - foot);
        drawAnswers(c, Present.G4, W - 2 * Present.G4, bottom - Present.G4, vis);
    }
}
