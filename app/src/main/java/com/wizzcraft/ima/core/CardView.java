package com.wizzcraft.ima.core;

import java.util.List;

/**
 * How the message that is up is painted. The engine owns the queue, the answers and their effects;
 * a view only draws the card over the frozen screen and says where its buttons are.
 * The default is {@link PanelCards}; the presentation layer replaces it with one line:
 * {@code Game.cards = new MyCards();}
 */
interface CardView {
    /**
     * Paints the card and adds one Btn per answer to btns, with id = the index of the answer.
     * A card without answers gets exactly one button with id 0 ("continue"). h is the logical
     * screen height; the dim layer should also cover g.edgeTop above 0 and g.edgeBottom below h.
     */
    void draw(Gfx c, Game g, Card card, float h, List<Btn> btns);

    /** The message on screen, read-only for the view. */
    final class Card {
        /** The beat's id (a style key for the presentation layer); empty for plain engine messages. */
        final String id;
        final String speaker, text;
        /** The answers; empty for a plain message. */
        final String[] options;
        /** True while the phone is ringing: the two answers are "pick up" and "not now". */
        final boolean call;

        Card(String id, String speaker, String text, String[] options, boolean call) {
            this.id = id;
            this.speaker = speaker;
            this.text = text;
            this.options = options;
            this.call = call;
        }
    }
}
