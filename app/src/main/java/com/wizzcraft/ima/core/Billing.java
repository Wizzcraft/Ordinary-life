package com.wizzcraft.ima.core;

/**
 * Real-money purchases, implemented by the Android shell (Google Play Billing once the game is
 * published there). The game core only ever talks to this interface, so it runs unchanged on the
 * desktop and in test mode. Only skus of {@link Catalog} items with a real-money price go through it;
 * items priced in נחת are bought inside the core and never reach Billing.
 *
 * Threading: implementations must deliver every callback on the game thread (post it back to the
 * view), never from inside {@link #buy} or {@link #restore} on the caller's stack except in test mode.
 *
 * Ordering: the core grants and saves inside {@link Callback#done}; only after that call returns may
 * the shell acknowledge (non-consumables) or consume (packs) the purchase with Play. A crash before
 * that point leaves the purchase unacknowledged, and the next {@link #restore} delivers it again, so
 * the core must make granting the same purchase token twice harmless (it records {@code store.tok.*}).
 */
public interface Billing {
    /** Result codes for {@link Callback#done}. */
    int OK = 0, CANCELLED = 1, PENDING = 2, FAILED = 3, ALREADY_OWNED = 4, UNAVAILABLE = 5,
            RESTORED = 6, RESTORE_DONE = 7;

    interface Callback {
        /**
         * @param sku   the item, or null with {@link #RESTORE_DONE}
         * @param code  one of the result codes
         * @param token a purchase token that identifies this purchase (the test mode invents one); null
         *              unless code is OK or RESTORED
         */
        void done(String sku, int code, String token);
    }

    /**
     * True when nothing is charged: purchases are granted at once and the store must show the test
     * banner. Never true in a build published on Google Play.
     */
    boolean testMode();

    /** False until the billing service is connected (and always false offline without a cache). */
    boolean ready();

    /** The price to show for a real-money sku, localised by the store (e.g. "₪12.90"); null if unknown. */
    String price(String sku);

    /** Opens the platform purchase sheet for one sku; the result arrives once through {@code cb}. */
    void buy(String sku, Callback cb);

    /**
     * Reports every purchase the platform knows that the player still owns or that was never
     * acknowledged: one RESTORED per purchase, then a single RESTORE_DONE (or FAILED / UNAVAILABLE).
     */
    void restore(Callback cb);

    /** The billing used until Play Billing exists: grants instantly, charges nothing, and says so. */
    final class Test implements Billing {
        private int next;

        @Override
        public boolean testMode() {
            return true;
        }

        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public String price(String sku) {
            Catalog.Item it = Catalog.find(sku);
            if (it == null || !it.realMoney()) return null;
            return "₪" + it.agorot / 100 + "." + String.format("%02d", it.agorot % 100);
        }

        @Override
        public void buy(String sku, Callback cb) {
            Catalog.Item it = Catalog.find(sku);
            if (it == null || !it.realMoney() || !it.available) {
                cb.done(sku, UNAVAILABLE, null);
                return;
            }
            // Unique across launches, so the core's double-grant guard never swallows a new test purchase.
            cb.done(sku, OK, "test." + System.currentTimeMillis() + "." + (++next));
        }

        /** Test purchases live only in the save, so there is nothing for the platform to restore. */
        @Override
        public void restore(Callback cb) {
            cb.done(null, RESTORE_DONE, null);
        }
    }
}
