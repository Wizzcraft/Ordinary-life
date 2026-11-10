package com.wizzcraft.ima.core;

/**
 * One scripted scene: when it may fire (the header) and what happens in it (a small program of steps).
 * Writers never build this by hand: they write {@code b.scene(id)...play(steps)} in an Arc file (see
 * {@link Script} and docs/WRITING.md) and the script is compiled into the flat list of {@link Op}s here.
 *
 * A scene is played by {@link StoryDirector} through the ordinary dialog screen, one {@link Beat} at
 * a time. Its position lives in two variables ({@code sc.id}, {@code sc.pc}), and every consequence
 * of a card rides in that card's effects, so a scene survives the app being closed on any card.
 */
final class Scene {
    /** Classes, in order of priority. */
    static final int SPINE = 0, CALL = 1, BEAT = 2, COLOUR = 3;
    static final String[] CLASS_NAME = {"spine", "call", "beat", "colour"};
    /** Channels: face to face at a place, a phone call that interrupts, a message that waits. */
    static final int FACE = 0, PHONE = 1, MESSAGE = 2;
    static final String[] CHANNEL_NAME = {"face", "call", "message"};
    /** Card kinds, for the presentation layer. */
    static final int TALK = 0, ON_PHONE = 1, TEXT = 2, LETTER = 3, THOUGHT = 4, NARRATE = 5, VOICE = 6;
    static final String[] KIND_NAME = {"talk", "call", "text", "letter", "thought", "narrate", "voice"};
    /** Step types. LINE and ASK are cards; everything else is silent. */
    static final int LINE = 1, ASK = 2, FX = 3, IF = 4, JUMP = 5, END = 6, HOURS = 7, REMEMBER = 8, REL = 9,
            STAGE = 10, OWE = 11, TELL = 12, LOG = 13, LEDGER = 14, PLANT = 15, PAYOFF = 16, PROP = 17, EVENT = 18,
            GOTO = 19, INVITE = 20, REFUSE = 21, ENDING = 22, NOTE = 23;
    /** "Any moment the engine asks at": for scenes that are only started by name. */
    static final int MANUAL = -1;

    static final class Op {
        int type;
        Person who;
        int mood, kind;
        /** Raw text for a male and a female player (the same string when the markup covers both). */
        String m, f;
        Cond cond;
        String s, s2;
        int n, n2, target;
        Opt[] opts;
        Alt[] alts;
        /** Code of the registered text this step writes into the journal or a thread; 0 for none. */
        int text;
        /** Source order number of an ASK inside its scene, 1-based: names its picked.* variable. */
        int ask;
    }

    /** One answer of an ASK. */
    static final class Opt {
        String m, f;
        Cond cond;
        int target;
        /** Code of the registered text when the answer is itself a message the player sends. */
        int text;
        /** The branch taken when a waiting message is never answered. Not shown as a button. */
        boolean ignored;
    }

    /** An alternative wording of a LINE, used when its condition holds (first match wins). */
    static final class Alt {
        Cond cond;
        String m, f;
    }

    final String id;
    final int code;
    /** The Arc class that registered it. */
    String source = "";
    String arc = "";
    int kind = BEAT, channel = FACE;
    /** A Narrator moment (0..9), or MANUAL. */
    int moment = 0;
    /** Building id the moment must be about, or -1 for anywhere. */
    int place = -1;
    int dayFrom = 1, dayTo = 9999;
    /** Hours of the day in which it may start; hourTo may be smaller (past midnight). -1 = any hour. */
    int hourFrom = -1, hourTo = -1;
    Cond when;
    /** Event it waits for ("arrested", or a scene's own "s.<name>"), and how fresh the event must be. */
    String onEvent = "";
    int eventWithin = Pacing.EVENT_FRESH_DAYS;
    float chance = 1;
    /** -1: once only. Otherwise repeatable after this many days. */
    int cooldown = -1;
    /** Order inside a class (higher first) for spine and beat; the weight of the draw for calls and colour. */
    int weight = 10;
    boolean heavy, family, urgent, last, nightOwl, background;
    /** Why the simulator may never meet this scene; null when it should. */
    String rare;
    /** The person who asks for something here: the scene stays away while they have stopped asking. */
    Person asks;
    /** The appointment this scene keeps. */
    String date = "";
    /** For a call: which weekly call it is (1 = the first), or 0 for "any week". */
    int week;
    /** Days an unanswered message waits before its ignored branch runs. */
    int expires = Pacing.MESSAGE_EXPIRES_DAYS;
    Op[] ops = new Op[0];
    /** Set when the scene could not be compiled: it is kept out of the game and the lint reports it. */
    String problem;

    Scene(String id) {
        this.id = id;
        this.code = code(id);
    }

    boolean once() {
        return cooldown < 0;
    }

    /** True for scenes the player did not ask for (everything but the weekly call and manual scenes). */
    boolean unsolicited() {
        return kind != CALL && moment != MANUAL && date.isEmpty();
    }

    /**
     * A stable code for an id, 1..16,777,215: small enough to be held exactly by a float variable,
     * and the same in every build, so a save made by one version resumes in the next.
     */
    static int code(String id) {
        int h = 0x811C9DC5;
        for (int i = 0; i < id.length(); i++) {
            h ^= id.charAt(i);
            h *= 0x01000193;
        }
        h = (h ^ (h >>> 24)) & 0xFFFFFF;
        return h == 0 ? 1 : h;
    }

    @Override
    public String toString() {
        return id;
    }
}
