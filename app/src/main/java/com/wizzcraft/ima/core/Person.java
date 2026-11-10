package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Somebody in the player's year, as data: how they are named on a card, how discreet they are, where
 * they can be found, and what they say when they remember something about the player.
 *
 * What changes during a year lives in variables, never here: {@code rel.<key>} (closeness 0..100),
 * {@code st.<key>} (the stage of their own story, 0..5), {@code owe.<key>} (favours, -2..+2) and
 * {@code k.<key>.<fact>} (the day they learned a fact about the player).
 */
final class Person {
    /** Discretion: an open person repeats things to their listener, a closed one never, an official one only when asked by authority. */
    static final int OPEN = 0, CLOSED = 1, OFFICIAL = 2;

    /** Variable-safe id from the director's list of names ("mazal"). */
    final String key;
    /** What others call them ("מזל"): the {who:mazal} placeholder. */
    final String name;
    /** The title of their cards ("מזל, השכנה"). */
    final String title;
    /** Stable 24-bit code of the key, for variables that have to hold "who". */
    final int code;
    /** Key of their portrait in the character artist's PersonArt; empty until one exists. */
    String portrait = "";
    /** Mom, dad and the family at home: a scene of theirs that reaches out is a "family moment". */
    boolean family;
    /** The player's own voice and the narrator are not people: no tie, no thread, no knowledge. */
    boolean voice;
    int trait = CLOSED;
    /** Who an open person tells. */
    Person listener;
    int startRel;
    /** Where they can be met: place id, from hour, to hour (wraps midnight), triples. */
    final List<int[]> haunts = new ArrayList<>();
    /** fact id -> the line they say when they remember it (gender markup allowed). */
    final Map<String, String> recalls = new LinkedHashMap<>();
    /** Greeting by tie (0..3), for a regular who walks into a shift. */
    final String[] greets = new String[4];

    Person(String key, String name, String title) {
        this.key = key;
        this.name = name;
        this.title = title;
        this.code = Scene.code(key);
    }

    Person portrait(String artKey) {
        portrait = artKey;
        return this;
    }

    Person family() {
        family = true;
        return this;
    }

    Person open(Person tells) {
        trait = OPEN;
        listener = tells;
        return this;
    }

    Person closed() {
        trait = CLOSED;
        return this;
    }

    Person official() {
        trait = OFFICIAL;
        return this;
    }

    Person startsAt(int rel) {
        startRel = rel;
        return this;
    }

    /** They can be found at this place between these hours (to may be smaller than from: past midnight). */
    Person at(Script.Place place, int fromHour, int toHour) {
        haunts.add(new int[]{place.id, fromHour, toHour});
        return this;
    }

    /** What they say when they remember this fact about the player; the newest fact they know wins. */
    Person recalls(String fact, String line) {
        recalls.put(fact, line);
        return this;
    }

    /** How they greet the player at a tie (0 stranger .. 3 family); lower ties fall back to the nearest one below. */
    Person greets(int tie, String line) {
        if (tie >= 0 && tie < greets.length) greets[tie] = line;
        return this;
    }

    boolean foundAt(int place, int hour) {
        for (int[] h : haunts) {
            if (h[0] != place) continue;
            if (h[1] == h[2] || (h[1] < h[2] ? hour >= h[1] && hour < h[2] : hour >= h[1] || hour < h[2])) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return key;
    }
}
