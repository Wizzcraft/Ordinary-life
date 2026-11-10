package com.wizzcraft.ima.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Screens that the engine does not know by name: each registers how to come back from a save under
 * its {@link Screen#kind()}. That is all a new screen (the phone, the journal) needs in order to
 * survive the app being closed; opening it is {@code g.openMenu(screen)} or {@code g.screen = screen}.
 */
final class Screens {
    private static final Map<String, BiFunction<Game, HashMap<String, String>, Screen>> ALL = new HashMap<>();

    static {
        register("pause", PauseMenu::restore);
    }

    private Screens() {
    }

    /** restore receives the game and every key=value of the save; it reads the keys its save() wrote. */
    static void register(String kind, BiFunction<Game, HashMap<String, String>, Screen> restore) {
        ALL.put(kind, restore);
    }

    /** The saved screen of that kind, or null when the kind is unknown or cannot be restored. */
    static Screen restore(String kind, Game g, HashMap<String, String> m) {
        BiFunction<Game, HashMap<String, String>, Screen> f = kind == null ? null : ALL.get(kind);
        if (f == null) return null;
        try {
            return f.apply(g, m);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
