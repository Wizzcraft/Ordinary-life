package com.wizzcraft.ima.core;

/**
 * One scripted moment: a message from a character, optionally with answers to choose from.
 * Pure data, written by the narrative team and played by the dialog system.
 *
 * Text: {@code m} is shown to a male player, {@code f} to a female player. "{name}" is replaced
 * with the player's name. Keep a message under about 220 characters so it fits one card.
 *
 * Options: none = a plain message with a single "continue" button; otherwise 2-3 short answers
 * (about 18 characters each). {@code repliesM/F[i]} is an optional follow-up message from the same
 * speaker after answer i. {@code effects[i]} is applied when answer i is picked (or effects[0] when
 * the message is shown and there are no options).
 *
 * Effects grammar: assignments separated by ';', each "key=n", "key+=n" or "key-=n", e.g.
 * "lie.job=1;mom.trust-=10;money+=50". Special keys: money, meals, bottles, food, water, energy,
 * hours (the clock moves forward). Every other key is a persistent variable (see Facts.vars).
 *
 * A beat with no text and no options is SILENT: it is never shown; at its turn its effects[0] is
 * applied and the queue moves on. A beat marked {@link #family()} is an unsolicited family moment:
 * the engine plays at most one of those a day and drops the rest.
 */
final class Beat {
    final String id, speaker, m, f;
    final String[] options, repliesM, repliesF, effects;
    /** An unsolicited family moment (mom, dad, the family); see the class comment. */
    final boolean family;

    Beat(String id, String speaker, String m, String f, String[] options, String[] repliesM, String[] repliesF,
         String[] effects) {
        this(id, speaker, m, f, options, repliesM, repliesF, effects, false);
    }

    private Beat(String id, String speaker, String m, String f, String[] options, String[] repliesM, String[] repliesF,
                 String[] effects, boolean family) {
        this.family = family;
        this.id = id;
        this.speaker = speaker;
        this.m = m;
        this.f = f;
        this.options = options == null ? new String[0] : options;
        this.repliesM = repliesM;
        this.repliesF = repliesF;
        this.effects = effects;
    }

    /** A plain message with separate male / female wording. */
    static Beat say(String id, String speaker, String m, String f) {
        return new Beat(id, speaker, m, f, null, null, null, null);
    }

    /** A plain message whose wording fits both genders. */
    static Beat say(String id, String speaker, String text) {
        return new Beat(id, speaker, text, text, null, null, null, null);
    }

    /** The same beat with an effect applied when it is shown (for beats without options). */
    Beat effect(String e) {
        return new Beat(id, speaker, m, f, options, repliesM, repliesF, new String[]{e}, family);
    }

    /** The same beat marked as an unsolicited family moment. */
    Beat family() {
        return new Beat(id, speaker, m, f, options, repliesM, repliesF, effects, true);
    }

    /** A beat that only changes variables: never shown. */
    static Beat silent(String effects) {
        return new Beat("", "", "", "", null, null, null, new String[]{effects}, false);
    }
}
