package com.wizzcraft.ima.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The registry of minigames: the engine only ever asks for a Mini by its id, so a Mini that is not
 * registered yet simply is not offered (or, for a job, is stood in for by the cook).
 */
final class Minis {
    private static final Map<String, Supplier<Mini>> ALL = new HashMap<>();

    static {
        // =====================================================================================
        // INTEGRATORS: register every Mini here, one line each. The ids the engine already uses:
        //   jobs    "cook" "waiter" "baker" "barman" "gas" "cashier"      (the mini column of Job.ALL)
        //   events  "chase" "fight"                                        (Crime and Events)
        //   venues  "alley" "blackjack" "roulette" "slots" "poker"         (VENUE_MINI below)
        // =====================================================================================
        register("cook", CookMini::new);
        register("waiter", WaiterMini::new);
        register("baker", BakerMini::new);
        register("barman", BarMini::new);
        register("gas", GasMini::new);
        register("cashier", CashierMini::new);
        register("doctor", DoctorMini::new);
        register("dev", DevMini::new);
        register("study.doctor", DoctorStudyMini::new);
        register("study.dev", DevStudyMini::new);
        register("chase", ChaseMini::new);
        register("fight", FightMini::new);
        register("alley", AlleyMini::new);
        register("blackjack", BlackjackMini::new);
        register("roulette", RouletteMini::new);
        register("slots", SlotsMini::new);
        register("poker", PokerMini::new);
    }

    /** Every Mini of the build in the order the test list shows them: jobs, studies, events, venues. */
    static final String[] ORDER = {"cook", "waiter", "baker", "barman", "gas", "cashier", "doctor", "dev",
            "study.doctor", "study.dev", "chase", "fight", "alley", "blackjack", "roulette", "slots", "poker"};

    /**
     * Where each venue (an open-ended Mini, rounds() == 0) is entered: building id and Mini id.
     * A building's menu offers a venue as soon as its Mini is registered above.
     */
    static final int[] VENUE_AT = {City.ALLEY, City.CASINO, City.CASINO, City.CASINO, City.CASINO};
    static final String[] VENUE_MINI = {"alley", "blackjack", "roulette", "slots", "poker"};

    private Minis() {
    }

    static void register(String id, Supplier<Mini> factory) {
        ALL.put(id, factory);
    }

    /** For the test harness: takes a Mini out of the build. */
    static void unregister(String id) {
        ALL.remove(id);
    }

    static boolean has(String id) {
        return id != null && ALL.containsKey(id);
    }

    /** A new Mini with that id, or null when the id is unknown or the Mini cannot be created. */
    static Mini create(String id) {
        Supplier<Mini> f = id == null ? null : ALL.get(id);
        if (f == null) return null;
        try {
            return f.get();
        } catch (RuntimeException e) {
            // A Mini that fails to start must not take the game down with it.
            return null;
        }
    }

    /**
     * The Mini that plays a job's shifts.
     * INTEGRATORS: until a job's own Mini is registered above, its shifts are played with the cook's
     * Mini. Nothing else needs to change when the real one arrives.
     */
    static Mini forJob(Job job) {
        Mini m = create(job.mini);
        return m != null ? m : create("cook");
    }
}
