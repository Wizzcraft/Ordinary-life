package com.wizzcraft.ima.core;

/**
 * Runs a device-lab tour on the desktop, frame by frame like GameView does, so a tour can be
 * written and debugged without an emulator. Prints the same IMA-TOUR lines the device logs and
 * saves a screenshot at every step. Prints "TOUR OK" when the tour finishes.
 *
 *     java -Djava.awt.headless=true -cp tools/out/classes com.wizzcraft.ima.core.TourLab smoke tools/out/tour [height]
 */
public final class TourLab {
    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "smoke";
        String out = args.length > 1 ? args[1] : "tools/out/tour";
        float lh = args.length > 2 ? Float.parseFloat(args[2]) : 760;
        new java.io.File(out).mkdirs();
        Game g = new Game();
        AwtGfx small = new AwtGfx(36, (int) (lh / 10), 0.1f);
        String[] lastStep = {""};
        Tour t = Tour.create(g, name, new Tour.Driver() {
            @Override
            public void tap(float x, float y) {
                g.down(x, y);
                g.up(x, y);
            }

            @Override
            public void log(String line) {
                System.out.println(line);
                if (line.startsWith("IMA-TOUR step ")) lastStep[0] = line.substring(14, line.indexOf(':'));
            }
        });
        if (t == null) throw new IllegalArgumentException("no tour named " + name);
        float dt = 1f / 60f;
        String shot = "";
        for (int i = 0; i < 60 * 600 && !t.done() && !t.failed(); i++) {
            g.update(dt, lh);
            t.frame(dt);
            g.draw(small);
            if (!lastStep[0].equals(shot)) {
                shot = lastStep[0];
                AwtGfx big = new AwtGfx(720, (int) (lh * 2), 2f);
                g.draw(big);
                big.save(out + "/step" + (shot.length() < 2 ? "0" : "") + shot + ".png");
            }
        }
        if (!t.done()) throw new AssertionError("tour did not finish");
        System.out.println("TOUR OK");
    }
}
