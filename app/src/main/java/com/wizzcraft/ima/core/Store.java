package com.wizzcraft.ima.core;

/**
 * Where the game keeps its save: named string slots that the shell backs with real storage
 * (SharedPreferences on Android, a map in tests). The game decides when to write and keeps its own
 * backup in a second slot; the shell only stores and returns strings.
 */
public interface Store {
    /** The value of a slot, or null when it was never written. */
    String read(String slot);

    /** Stores the value. Must not throw; a failed write is simply lost. */
    void write(String slot, String value);
}
