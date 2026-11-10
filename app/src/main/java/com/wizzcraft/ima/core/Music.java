package com.wizzcraft.ima.core;

import java.util.ArrayList;

/**
 * The game's music: four looping tracks, composed as readable data and synthesized here in code.
 * No audio files, no libraries, and no dependency on the effects synth.
 *
 * <pre>
 * ====================================================================================================
 *  HOW TO READ THE SCORE
 * ====================================================================================================
 *  Melodic parts (bass, melody, bell) are written note by note, bars separated by '|':
 *      A4q.   = the A above middle C, dotted quarter      (C4 is middle C, A4 = 440 Hz)
 *      Bb4e   = B flat, eighth;   C#5s = C sharp, sixteenth;   rq = quarter rest
 *      w h q e s = whole, half, quarter, eighth, sixteenth;  '.' after the length = dotted
 *      '!' after a note = accent, '~' = soft (ghost); otherwise a normal, medium dynamic.
 *  Every bar is checked to add up exactly to the meter, so a typo fails loudly instead of drifting.
 *
 *  Chord parts are two lines: the progression as chord symbols ("C7sus4:h C7:h" splits a bar), and
 *  a rhythm grid of equal steps per bar:  X accent, x normal, o soft, '-' keep holding, '.' silence.
 *  The exact notes of every chord symbol are in VOICINGS below (the bass part supplies the root).
 *
 *  Drum parts are grids of 16th notes (12 per bar in 3/4): X accent, x normal, o soft, '.' rest.
 *  A grid with fewer bars than the track repeats; several bars are separated by '|'.
 *
 * ====================================================================================================
 *  THE FOUR TRACKS (all in F major or its relative minor, so they share one key centre with the SFX)
 * ====================================================================================================
 *  0  THE CITY BY DAY      F major, 98 BPM, 4/4, 8 bars (19.6 s)
 *     | Fmaj9 | Am7 | Bbmaj7 | C7sus4 C7 | Dm7 | Gm7 | Bbm6 | C7sus4 C7 |
 *     Form: two 4-bar phrases on the same motif (rest, two rising eighths, a long leaning note):
 *     bars 1-4 ask (they end hanging on C), bars 5-8 answer, reach the high F, and sigh through the
 *     borrowed minor iv (Bbm6: the D-flat is the wistful note) before C7 turns back to the top.
 *     An inner voice of the chords slides D - Db - C over bars 6-8. Comping is a soft 3+3+2 lilt;
 *     a music box answers the melody only where it holds a long note (bars 2, 4, 6, 8).
 *
 *  1  THE CITY AT NIGHT    F major, 76 BPM, 3/4, 8 bars (18.9 s)
 *     | Fmaj9 | Dm9 | Bbmaj7 | C7sus4 | Fmaj9 | Am7 | Bbm6 | C7sus4 C7 |
 *     The day motif re-barred as a slow waltz: the same rising A-C-D, now with room around it.
 *     Bar 5 leaps to the high G (the loneliest moment), bar 7 is the same Db-to-C sigh as the day.
 *     A warm pad replaces the comping; the bass just holds roots; a longer echo; brushes on 2 and 3.
 *
 *  2  WORK                 F major, 112 BPM, 4/4, 8 bars (17.1 s)
 *     | Fmaj7 | Gm7 | Am7 | Gm7 | Fmaj7 | Gm7 | Bbmaj7 | C7sus4 |
 *     A neutral, busy-but-quiet groove for any minigame: a plucked arpeggio grouped 3+3+2 over
 *     straight 4/4 (it moves forward without a backbeat), a staccato bass with chromatic approach
 *     notes into each chord, a shaker only, and two short whistled phrases (bars 2-3 and 6-7).
 *     No snare, no rim, no echo: the baker's rhythm game owns the beat.
 *
 *  3  TENSION              D minor, 138 BPM, 4/4, 8 bars (13.9 s)
 *     | Dm | Dm | Bb/D | A7/C# | Dm | Gm/D | Bb/D Eb/D | A7/C# |
 *     Comic-tense chase: an octave-bouncing bass over a D pedal, off-beat staccato stabs, and a
 *     sneaky staccato "clarinet" line that creeps chromatically (A-Bb-B-C, then F-E-Eb-D). The
 *     Eb/D in bar 7 is the "uh-oh"; bar 8 has a small tom fill back to the top.
 *
 * ====================================================================================================
 *  INSTRUMENTS, in building blocks (oscillator / noise -> filter -> amplifier, shaped by envelopes and LFOs)
 * ====================================================================================================
 *  BASS      saw osc -> 2-pole low-pass (cutoff ~260 Hz + a 120 ms envelope sweep, scaled by velocity)
 *            -> amp ADSR 4 ms / 250 ms / 0.55 / 70 ms. Played in octave 3 so phones can carry it.
 *  KEYS      2 triangle oscs detuned 5 cents -> low-pass 800 Hz + a 300 ms envelope sweep
 *            -> amp ADSR 5 ms / 0.9 s / 0.25 / 0.3 s; LFO 4.2 Hz tremolo, 12 %. A soft electric piano.
 *  PAD       2 saw oscs detuned +-7 cents -> low-pass 650 Hz, LFO 0.15 Hz moves the cutoff +-250 Hz
 *            -> amp ADSR 0.4 s / - / 1.0 / 0.9 s.
 *  FLUTE     triangle osc + quiet sine an octave up + breath noise (with a 30 ms "chiff")
 *            -> low-pass 2.2 kHz -> amp ADSR 45 ms / 0.3 s / 0.8 / 0.18 s; LFO 5.2 Hz vibrato that fades in.
 *  BELL      sine osc + 3rd harmonic sine -> no filter -> amp: 2 ms attack, exponential decay
 *            (0.45 s for the fundamental, 0.1 s for the overtone). A music box.
 *  PLUCK     35 % pulse osc -> low-pass 400 Hz + a fast 60 ms envelope sweep -> amp 2 ms / 220 ms decay.
 *  CLARINET  square osc -> low-pass 1.3 kHz + 80 ms envelope sweep -> amp 10 ms / 150 ms / 0.75 / 50 ms;
 *            LFO 5.5 Hz vibrato fading in. The square's odd harmonics are the comic woodwind.
 *  SHAKER    white noise -> band-pass 4.2 kHz -> low-pass 5.5 kHz -> amp 3 ms attack, 30 ms decay.
 *  KNOCK     sine osc with a pitch envelope (165 Hz plus a 25 ms drop from ~360 Hz) + its 2nd harmonic
 *            -> amp 110 ms decay. A soft kick tuned above the phone speaker's floor.
 *  RIM       820 Hz sine + noise band-passed at 1.9 kHz -> amp 20-30 ms decay.
 *  BRUSH     white noise -> band-pass 2.4 kHz (wide) -> amp 12 ms attack, 100 ms decay.
 *  TOM_HI/LO sine osc 260 / 200 Hz with a pitch drop -> amp 160 ms decay.
 *  Echo: a feedback delay (with a low-pass inside the loop) on a send bus.
 *  Master: 2-pole high-pass at 140 Hz (phone speakers cannot use the lows anyway) and 2-pole
 *  low-pass at 5.2 kHz, then a fixed loudness with the peak held at or under -10 dBFS.
 *
 *  Seamless loops: every note and every echo is written into a circular buffer, so a tail that
 *  runs past the end of the loop lands at its start. The delay and the master filters are run
 *  around the loop until they settle, so their state at the seam is the steady state.
 * </pre>
 */
public final class Music {

    // ---------------------------------------------------------------- key and tempo (tune here)

    /** Semitones added to every note of every track, to follow the sound effects' key. */
    public static final int TRANSPOSE = 0;

    public static final String KEY_DAY = "F major";
    public static final String KEY_NIGHT = "F major";
    public static final String KEY_WORK = "F major";
    public static final String KEY_TENSION = "D minor";

    public static final double BPM_DAY = 98;
    public static final double BPM_NIGHT = 76;
    public static final double BPM_WORK = 112;
    public static final double BPM_TENSION = 138;

    public static final int DAY = 0, NIGHT = 1, WORK = 2, TENSION = 3;
    public static final int TRACKS = 4;

    /** Peak ceiling of every rendered loop, in dBFS: the music sits quietly under the effects. */
    static final double PEAK_DB = -10;
    static final double MAX_SECONDS = 20;

    // ---------------------------------------------------------------- chord voicings

    /** Chord symbol, then its exact notes (middle register; the bass part plays the root). */
    static final String[][] VOICINGS = {
            {"Fmaj9", "A3 C4 E4 G4"},
            {"Fmaj7", "A3 C4 E4"},
            {"Am7", "G3 C4 E4"},
            {"Bbmaj7", "A3 D4 F4"},
            {"C7sus4", "Bb3 C4 F4"},
            {"C7", "Bb3 C4 E4"},
            {"Dm7", "A3 C4 F4"},
            {"Dm9", "F3 A3 C4 E4"},
            {"Gm7", "Bb3 D4 F4"},
            {"Bbm6", "Bb3 Db4 G4"},
            // tension (D minor), voiced a little higher so the staccato stabs stay clear of the bass
            {"Dm", "D4 F4 A4"},
            {"Gm/D", "D4 G4 Bb4"},
            {"Bb/D", "D4 F4 Bb4"},
            {"Eb/D", "Eb4 G4 Bb4"},
            {"A7/C#", "C#4 E4 G4"},
    };

    // ---------------------------------------------------------------- instruments

    static final int BASS = 0, KEYS = 1, PAD = 2, FLUTE = 3, BELL = 4, PLUCK = 5, CLARINET = 6,
            SHAKER = 7, KNOCK = 8, RIM = 9, BRUSH = 10, TOM_HI = 11, TOM_LO = 12;
    static final String[] VOICE_NAMES = {"bass", "keys", "pad", "flute", "bell", "pluck", "clarinet",
            "shaker", "knock", "rim", "brush", "tom hi", "tom lo"};

    // ---------------------------------------------------------------- the score

    static final Track[] SCORE = {

            // ======================================================== 0: THE CITY BY DAY
            new Track("city by day", BPM_DAY, 4, 8, 0.75, 0.30, -23,
                    mel(FLUTE, 1.00, 0.22, 1.0,
                            "rq A4e C5e D5q. E5e | C5q A4q G4h | rq Bb4e D5e F5q. D5e | C5h. rq |"
                          + "rq A4e C5e D5q. F5e | E5q. D5e Bb4h | rq Bb4e Db5e C5q. Bb4e | C5q. Bb4e G4h"),
                    mel(BELL, 0.48, 0.45, 1.0,
                            "rw | rh rq E5e~ C5e~ | rw | rh. G5e~ E5e~ |"
                          + "rw | rh rq D5e~ F5e~ | rw | rh. E5e~ G5e~"),
                    comp(KEYS, 0.50, 0.0, 1.0,
                            "Fmaj9 | Am7 | Bbmaj7 | C7sus4:h C7:h | Dm7 | Gm7 | Bbm6 | C7sus4:h C7:h",
                            "x--o--o-"),
                    mel(BASS, 0.74, 0.0, 1.0,
                            "F3q. F3e~ C4q G3q | A3q. A3e~ E3q G3q | Bb3q. Bb3e~ F3q A3q | C4q. C4e~ G3q E3q |"
                          + "D3q. D3e~ A3q F3q | G3q. G3e~ D3q F3q | Bb3q. Bb3e~ F3q Db4q | C4q. C4e~ G3q E3q"),
                    drum(SHAKER, 0.45, "o.x.o.x.o.x.o.x."),
                    drum(KNOCK, 0.56, "x.....o.........|x.....o.........|x.....o.........|x.....o.....o.o."),
                    drum(RIM, 0.38, "............o...")),

            // ======================================================== 1: THE CITY AT NIGHT
            new Track("city at night", BPM_NIGHT, 3, 8, 1.0, 0.42, -25,
                    mel(FLUTE, 1.00, 0.35, 0.8,
                            "A4e C5e D5h | C5h rq | Bb4e D5e F5h | D5q. C5e Bb4q |"
                          + "A4e C5e G5h | E5q. D5e C5q | Db5h C5q | G4h rq"),
                    mel(BELL, 0.58, 0.55, 1.0,
                            "rh. | rh A5e~ F5e~ | rh. | rh. | rh. | rh. | rh. | rh E5e~ G5e~"),
                    comp(PAD, 0.38, 0.10, 1.0,
                            "Fmaj9 | Dm9 | Bbmaj7 | C7sus4 | Fmaj9 | Am7 | Bbm6 | C7sus4:h C7:q",
                            "x--|x--|x--|x--|x--|x--|x--|x-o"),
                    mel(BASS, 0.98, 0.0, 0.8,
                            "F3h.~ | D3h~ A3q~ | Bb3h.~ | C4h.~ | F3h.~ | A3h~ C4q~ | Bb3h.~ | C4h~ E3q~"),
                    drum(BRUSH, 0.90, "....o...o..."),
                    drum(KNOCK, 0.49, "o...........")),

            // ======================================================== 2: WORK
            new Track("work", BPM_WORK, 4, 8, 0.0, 0.0, -24,
                    mel(PLUCK, 0.37, 0.0, 1.0,
                            "A4e! C5e E5e A4e! C5e E5e G4e! C5e | Bb4e! D5e F5e Bb4e! D5e F5e A4e! D5e |"
                          + "G4e! C5e E5e G4e! C5e E5e A4e! C5e | F4e! Bb4e D5e F4e! Bb4e D5e A4e! D5e |"
                          + "A4e! C5e E5e A4e! C5e E5e G4e! C5e | Bb4e! D5e F5e Bb4e! D5e F5e A4e! D5e |"
                          + "A4e! D5e F5e A4e! D5e F5e C5e! D5e | Bb4e! C5e F5e Bb4e! C5e F5e G4e! C5e"),
                    mel(FLUTE, 0.70, 0.0, 0.9,
                            "rw | rq Bb4e D5e C5q A4q | G4h rh | rw |"
                          + "rw | rq Bb4e D5e F5q D5q | C5h rh | rw"),
                    mel(BASS, 0.58, 0.0, 1.0,
                            "F3e! re F3e~ C4e! re F3e~ A3e! F#3e~ | G3e! re G3e~ D4e! re G3e~ Bb3e! G#3e~ |"
                          + "A3e! re A3e~ E3e! re A3e~ C4e! Ab3e~ | G3e! re G3e~ D4e! re G3e~ F3e! E3e~ |"
                          + "F3e! re F3e~ C4e! re F3e~ A3e! F#3e~ | G3e! re G3e~ D4e! re G3e~ F3e! A3e~ |"
                          + "Bb3e! re Bb3e~ F3e! re Bb3e~ D4e! B3e~ | C4e! re C4e~ G3e! re Bb3e~ G3e! E3e~"),
                    drum(SHAKER, 0.47, "x.o.o.x.o.o.x.o."),
                    drum(KNOCK, 0.40, "o...............")),

            // ======================================================== 3: TENSION
            new Track("tension", BPM_TENSION, 4, 8, 0.0, 0.0, -22,
                    mel(CLARINET, 0.80, 0.0, 1.0,
                            "D5e rs D5s F5e rs E5s D5e C#5e D5q | A4e A4e Bb4e B4e C5q rq |"
                          + "D5e rs D5s F5e rs E5s D5e C5e Bb4q | A4e C#5e E5e G5e F5e E5e C#5q |"
                          + "D5e rs D5s F5e rs E5s D5e C#5e D5q | F5e F5e E5e Eb5e D5q rq |"
                          + "D5e rs D5s F5e rs D5s Eb5e rs Eb5s G5e Eb5e | E5e rs E5s C#5e rs A4s Bb4e B4e C#5q"),
                    comp(PLUCK, 0.34, 0.0, 1.0,
                            "Dm | Dm | Bb/D | A7/C# | Dm | Gm/D | Bb/D:h Eb/D:h | A7/C#",
                            ".o.x.o.x"),
                    mel(BASS, 0.60, 0.0, 1.0,
                            "D3e! A3e D4e A3e D3e! A3e D4e A3e | D3e! A3e D4e A3e D3e! A3e D4e A3e |"
                          + "D3e! F3e Bb3e F3e D3e! F3e Bb3e F3e | C#3e! E3e A3e E3e C#3e! E3e A3e E3e |"
                          + "D3e! A3e D4e A3e D3e! A3e D4e A3e | D3e! G3e Bb3e G3e D3e! G3e Bb3e G3e |"
                          + "D3e! F3e Bb3e F3e D3e! G3e Eb4e G3e | C#3e! E3e A3e E3e C#3e! E3e A3e E3e"),
                    drum(SHAKER, 0.29, "xoxoxoxoxoxoxoxo"),
                    drum(KNOCK, 0.42, "X.......X.......|X.......X.......|X.......X.......|X.......X.....x."),
                    drum(RIM, 0.32, "....x.......x...|....x.......x...|....x.......x...|....x..........."),
                    drum(TOM_HI, 0.30, "................|................|................|........x.x....."),
                    drum(TOM_LO, 0.30, "................|................|................|............x.x.")),
    };

    // ---------------------------------------------------------------- public API

    /** Renders one track as a seamless mono 16-bit loop: play it with looping on, no crossfade needed. */
    public static short[] render(int trackId, int sampleRate) {
        float[] f = renderFloat(trackId, sampleRate);
        short[] out = new short[f.length];
        for (int i = 0; i < f.length; i++) {
            int v = (int) Math.round(f[i] * 32767.0);
            out[i] = (short) Math.max(-32767, Math.min(32767, v));
        }
        return out;
    }

    /** Tempo of a track in beats per minute. */
    public static double bpm(int trackId) {
        return SCORE[trackId].bpm;
    }

    /** The loop length in samples at a given rate (what {@link #render} returns). */
    public static int loopLength(int trackId, int sampleRate) {
        Track t = SCORE[trackId];
        return (int) Math.round(t.bars * t.beatsPerBar * 60.0 / t.bpm * sampleRate);
    }

    // ---------------------------------------------------------------- rendering

    static float[] renderFloat(int trackId, int sampleRate) {
        return renderFloat(trackId, sampleRate, -1, true);
    }

    /**
     * Renders a track as floats. With onlyPart >= 0 just that part is rendered (a stem, used by the
     * desktop dump to check the balance); with normalize false the mix keeps its raw level.
     */
    static float[] renderFloat(int trackId, int sampleRate, int onlyPart, boolean normalize) {
        if (trackId < 0 || trackId >= TRACKS) throw new IllegalArgumentException("no track " + trackId);
        Track t = SCORE[trackId];
        int len = loopLength(trackId, sampleRate);
        if (len > MAX_SECONDS * sampleRate + 0.5) throw new IllegalStateException(t.name + " is longer than 20 s");
        Sink sink = new Sink(len, sampleRate);
        double samplesPerBeat = 60.0 / t.bpm * sampleRate;

        for (int p = 0; p < t.parts.length; p++) {
            if (onlyPart >= 0 && p != onlyPart) continue;
            Part part = t.parts[p];
            ArrayList<Ev> evs = part.events(t);
            for (int i = 0; i < evs.size(); i++) {
                Ev e = evs.get(i);
                int start = (int) Math.round(e.beat * samplesPerBeat);
                double legato = part.voice == PLUCK || part.voice == CLARINET ? 0.85 : 0.94;
                int gate = Math.max(1, (int) Math.round(e.dur * samplesPerBeat * legato));
                double f = e.midi < 0 ? 0 : 440.0 * Math.pow(2, (e.midi + TRANSPOSE - 69) / 12.0);
                long seed = (trackId * 1000003L + p * 7919L + i) * 2654435761L;
                play(part.voice, sink, start, gate, f, e.vel, part.gain * e.vel, part.send * part.gain * e.vel,
                        part.tone, seed);
            }
        }

        float[] mix = new float[len];
        for (int i = 0; i < len; i++) mix[i] = sink.dry[i];
        if (t.delayBeats > 0) {
            float[] echo = circularDelay(sink.wet, (int) Math.round(t.delayBeats * samplesPerBeat),
                    t.delayFeedback, 2500, sampleRate);
            for (int i = 0; i < len; i++) mix[i] += echo[i];
        }
        circularBiquad(mix, true, 140, sampleRate);
        circularBiquad(mix, false, 5200, sampleRate);

        if (normalize) {
            double peak = 1e-9, sum = 0;
            for (float v : mix) {
                peak = Math.max(peak, Math.abs(v));
                sum += v * (double) v;
            }
            double rms = Math.sqrt(sum / len) + 1e-12;
            double gain = Math.min(Math.pow(10, t.rmsDb / 20) / rms, Math.pow(10, PEAK_DB / 20) / peak);
            for (int i = 0; i < len; i++) mix[i] *= (float) gain;
        }
        return mix;
    }

    /** Feedback echo run around the loop three times, so the echoes that cross the seam are present. */
    private static float[] circularDelay(float[] in, int delay, double feedback, double damp, int sr) {
        int len = in.length;
        float[] out = new float[len];
        float[] line = new float[Math.max(1, delay)];
        double c = 1 - Math.exp(-2 * Math.PI * damp / sr), lp = 0;
        int pos = 0;
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < len; i++) {
                double y = line[pos];
                lp += c * (y - lp);
                line[pos] = (float) (in[i] + feedback * lp);
                pos = (pos + 1) % line.length;
                if (pass == 2) out[i] = (float) y;
            }
        }
        return out;
    }

    /** 2-pole Butterworth high- or low-pass, run once to settle and again to produce the loop. */
    private static void circularBiquad(float[] x, boolean highPass, double fc, int sr) {
        double w = 2 * Math.PI * fc / sr, cs = Math.cos(w), al = Math.sin(w) / (2 * Math.sqrt(0.5));
        double b0, b1, b2, a0 = 1 + al, a1 = -2 * cs, a2 = 1 - al;
        if (highPass) {
            b0 = (1 + cs) / 2; b1 = -(1 + cs); b2 = (1 + cs) / 2;
        } else {
            b0 = (1 - cs) / 2; b1 = 1 - cs; b2 = (1 - cs) / 2;
        }
        b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        float[] src = x.clone();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < src.length; i++) {
                double in = src[i];
                double y = b0 * in + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
                x2 = x1; x1 = in; y2 = y1; y1 = y;
                if (pass == 1) x[i] = (float) y;
            }
        }
    }

    // ---------------------------------------------------------------- voices

    /** A circular mix bus: writes past the end of the loop wrap to its start. */
    private static final class Sink {
        final float[] dry, wet;
        final int len, sr;

        Sink(int len, int sr) {
            this.len = len;
            this.sr = sr;
            dry = new float[len];
            wet = new float[len];
        }

        void add(int start, int i, double s, double amp, double send) {
            int k = (start + i) % len;
            dry[k] += (float) (s * amp);
            wet[k] += (float) (s * send);
        }
    }

    private static void play(int voice, Sink o, int start, int gate, double f, double vel, double amp,
                             double send, double tone, long seed) {
        int sr = o.sr;
        switch (voice) {
            case BASS: {
                Env env = new Env(0.004, 0.25, 0.55, 0.07, gate, sr);
                Svf lp = new Svf(0.9);
                double ph = 0, dt = f / sr, keyTrack = Math.sqrt(f / 180.0);
                for (int i = 0; i < env.len; i++) {
                    double t = (double) i / sr;
                    if ((i & 15) == 0) lp.tune(tone * keyTrack * (260 + 1500 * vel * Math.exp(-t / 0.12)), sr);
                    double s = saw(ph, dt);
                    ph = wrap(ph + dt);
                    o.add(start, i, lp.lp(s) * env.at(i) * 0.9, amp, send);
                }
                break;
            }
            case KEYS: {
                Env env = new Env(0.005, 0.9, 0.25, 0.3, gate, sr);
                Svf lp = new Svf(0.7);
                double p1 = 0, p2 = 0, d1 = f / sr, d2 = d1 * Math.pow(2, 5 / 1200.0);
                for (int i = 0; i < env.len; i++) {
                    double t = (double) i / sr;
                    if ((i & 15) == 0) lp.tune(tone * (800 + 2400 * Math.exp(-t / 0.3)), sr);
                    double s = 0.5 * (tri(p1) + tri(p2));
                    p1 = wrap(p1 + d1);
                    p2 = wrap(p2 + d2);
                    double trem = 1 - 0.12 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 4.2 * t));
                    o.add(start, i, lp.lp(s) * env.at(i) * trem, amp, send);
                }
                break;
            }
            case PAD: {
                Env env = new Env(0.4, 1.0, 1.0, 0.9, gate, sr);
                Svf lp = new Svf(0.8);
                double p1 = 0.0, p2 = 0.37, d1 = f / sr * Math.pow(2, 7 / 1200.0), d2 = f / sr * Math.pow(2, -7 / 1200.0);
                for (int i = 0; i < env.len; i++) {
                    double t = (double) i / sr;
                    if ((i & 15) == 0) lp.tune(tone * (650 + 250 * Math.sin(2 * Math.PI * 0.15 * t)), sr);
                    double s = 0.5 * (saw(p1, d1) + saw(p2, d2));
                    p1 = wrap(p1 + d1);
                    p2 = wrap(p2 + d2);
                    o.add(start, i, lp.lp(s) * env.at(i), amp, send);
                }
                break;
            }
            case FLUTE: {
                Env env = new Env(0.045, 0.3, 0.8, 0.18, gate, sr);
                Svf lp = new Svf(0.7);
                lp.tune(tone * 2200, sr);
                Noise n = new Noise(seed);
                double ph = 0, ph2 = 0;
                for (int i = 0; i < env.len; i++) {
                    double t = (double) i / sr;
                    double vibDepth = 0.004 * clamp01((t - 0.15) / 0.35);
                    double fi = f * (1 + vibDepth * Math.sin(2 * Math.PI * 5.2 * t));
                    double s = tri(ph) + 0.12 * Math.sin(2 * Math.PI * ph2)
                            + n.next() * (0.04 + 0.25 * Math.exp(-t / 0.03));
                    ph = wrap(ph + fi / sr);
                    ph2 = wrap(ph2 + 2 * fi / sr);
                    o.add(start, i, lp.lp(s) * env.at(i) * 0.8, amp, send);
                }
                break;
            }
            case BELL: {
                int len = gate + (int) (2.2 * sr);
                for (int i = 0; i < len; i++) {
                    double t = (double) i / sr;
                    double a = clamp01(t / 0.002) * endFade(i, len, sr);
                    double s = Math.sin(2 * Math.PI * f * t) * Math.exp(-t / 0.45)
                            + 0.18 * Math.sin(2 * Math.PI * 3 * f * t) * Math.exp(-t / 0.10);
                    o.add(start, i, s * a, amp, send);
                }
                break;
            }
            case PLUCK: {
                Env env = new Env(0.002, 0.22, 0.0, 0.06, gate, sr);
                Svf lp = new Svf(1.0);
                double ph = 0, dt = f / sr;
                for (int i = 0; i < env.len; i++) {
                    double t = (double) i / sr;
                    if ((i & 15) == 0) lp.tune(tone * (400 + 3200 * vel * Math.exp(-t / 0.06)), sr);
                    double s = pulse(ph, dt, 0.35);
                    ph = wrap(ph + dt);
                    o.add(start, i, lp.lp(s) * env.at(i), amp, send);
                }
                break;
            }
            case CLARINET: {
                Env env = new Env(0.01, 0.15, 0.75, 0.05, gate, sr);
                Svf lp = new Svf(0.7);
                double ph = 0;
                for (int i = 0; i < env.len; i++) {
                    double t = (double) i / sr;
                    if ((i & 15) == 0) lp.tune(tone * (1300 + 900 * Math.exp(-t / 0.08)), sr);
                    double fi = f * (1 + 0.003 * clamp01((t - 0.12) / 0.2) * Math.sin(2 * Math.PI * 5.5 * t));
                    double dt = fi / sr;
                    double s = pulse(ph, dt, 0.5);
                    ph = wrap(ph + dt);
                    o.add(start, i, lp.lp(s) * env.at(i) * 0.7, amp, send);
                }
                break;
            }
            case SHAKER: {
                int len = (int) (0.2 * sr);
                Noise n = new Noise(seed);
                Svf bp = new Svf(1.3), lp = new Svf(0.7);
                bp.tune(4200, sr);
                lp.tune(5500, sr);
                for (int i = 0; i < len; i++) {
                    double t = (double) i / sr;
                    double a = clamp01(t / 0.003) * Math.exp(-t / 0.03) * endFade(i, len, sr);
                    o.add(start, i, lp.lp(bp.bp(n.next())) * a * 2.0, amp, send);
                }
                break;
            }
            case KNOCK: {
                int len = (int) (0.6 * sr);
                double ph = 0;
                for (int i = 0; i < len; i++) {
                    double t = (double) i / sr;
                    double fi = 165 * (1 + 1.2 * Math.exp(-t / 0.025));
                    ph = wrap(ph + fi / sr);
                    double a = clamp01(t / 0.001) * Math.exp(-t / 0.11) * endFade(i, len, sr);
                    double s = Math.sin(2 * Math.PI * ph) + 0.3 * Math.sin(4 * Math.PI * ph);
                    o.add(start, i, s * a, amp, send);
                }
                break;
            }
            case RIM: {
                int len = (int) (0.2 * sr);
                Noise n = new Noise(seed);
                Svf bp = new Svf(4);
                bp.tune(1900, sr);
                for (int i = 0; i < len; i++) {
                    double t = (double) i / sr;
                    double a = clamp01(t / 0.0005) * endFade(i, len, sr);
                    double s = 0.6 * Math.sin(2 * Math.PI * 820 * t) * Math.exp(-t / 0.02)
                            + 2.5 * bp.bp(n.next()) * Math.exp(-t / 0.03);
                    o.add(start, i, s * a, amp, send);
                }
                break;
            }
            case BRUSH: {
                int len = (int) (0.5 * sr);
                Noise n = new Noise(seed);
                Svf bp = new Svf(0.7);
                bp.tune(2400, sr);
                for (int i = 0; i < len; i++) {
                    double t = (double) i / sr;
                    double a = clamp01(t / 0.012) * Math.exp(-t / 0.10) * endFade(i, len, sr);
                    o.add(start, i, bp.bp(n.next()) * a * 1.5, amp, send);
                }
                break;
            }
            case TOM_HI:
            case TOM_LO: {
                int len = (int) (0.8 * sr);
                double f0 = voice == TOM_HI ? 260 : 200, ph = 0;
                for (int i = 0; i < len; i++) {
                    double t = (double) i / sr;
                    ph = wrap(ph + f0 * (1 + 0.5 * Math.exp(-t / 0.04)) / sr);
                    double a = clamp01(t / 0.001) * Math.exp(-t / 0.16) * endFade(i, len, sr);
                    o.add(start, i, (Math.sin(2 * Math.PI * ph) + 0.25 * Math.sin(4 * Math.PI * ph)) * a, amp, send);
                }
                break;
            }
            default:
                throw new IllegalStateException("no voice " + voice);
        }
    }

    /** ADSR: linear attack, exponential decay to sustain, exponential release that fades fully to zero. */
    private static final class Env {
        final int len, gate, a;
        final double d, s, r, gateLevel;

        Env(double att, double dec, double sus, double rel, int gate, int sr) {
            this.gate = gate;
            a = Math.max(1, (int) (att * sr));
            d = dec * sr;
            s = sus;
            r = rel * sr;
            len = gate + (int) (6 * r) + 1;
            gateLevel = held(gate);
        }

        private double held(int i) {
            if (i < a) return (double) i / a;
            return s + (1 - s) * Math.exp(-(i - a) / d);
        }

        double at(int i) {
            if (i < gate) return held(i);
            double k = i - gate, tail = len - gate;
            return gateLevel * Math.exp(-k / r) * (1 - k / tail);
        }
    }

    /** Topology-preserving state-variable filter (stable under fast cutoff sweeps). */
    private static final class Svf {
        final double k;
        double a1, a2, a3, ic1, ic2, bpOut;

        Svf(double q) {
            k = 1 / q;
        }

        void tune(double fc, int sr) {
            fc = Math.max(20, Math.min(fc, 0.45 * sr));
            double g = Math.tan(Math.PI * fc / sr);
            a1 = 1 / (1 + g * (g + k));
            a2 = g * a1;
            a3 = g * a2;
        }

        double lp(double x) {
            double v3 = x - ic2, v1 = a1 * ic1 + a2 * v3, v2 = ic2 + a2 * ic1 + a3 * v3;
            ic1 = 2 * v1 - ic1;
            ic2 = 2 * v2 - ic2;
            bpOut = v1;
            return v2;
        }

        double bp(double x) {
            lp(x);
            return bpOut;
        }
    }

    /** Deterministic white noise, so every render of a track is identical. */
    private static final class Noise {
        long s;

        Noise(long seed) {
            s = seed ^ 0x9E3779B97F4A7C15L;
        }

        double next() {
            s = s * 6364136223846793005L + 1442695040888963407L;
            return ((s >>> 40) / (double) (1L << 24)) * 2 - 1;
        }
    }

    private static double wrap(double p) {
        return p >= 1 ? p - Math.floor(p) : p;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** Last 5 ms of a fixed-length hit fades to zero, so no hit ever ends on a step. */
    private static double endFade(int i, int len, int sr) {
        int f = sr / 200;
        return Math.min(1.0, (len - 1 - i) / (double) f);
    }

    private static double tri(double p) {
        return 4 * Math.abs(p - 0.5) - 1;
    }

    /** Band-limited (polyBLEP) saw: keeps aliasing, and so harshness, down at 22 kHz. */
    private static double saw(double p, double dt) {
        return 2 * p - 1 - blep(p, dt);
    }

    private static double pulse(double p, double dt, double width) {
        double v = p < width ? 1 : -1;
        return v + blep(p, dt) - blep(wrap(p - width + 1), dt) - (2 * width - 1);
    }

    private static double blep(double t, double dt) {
        if (t < dt) {
            t /= dt;
            return t + t - t * t - 1;
        } else if (t > 1 - dt) {
            t = (t - 1) / dt;
            return t * t + t + t + 1;
        }
        return 0;
    }

    // ---------------------------------------------------------------- score data types and parsing

    static final class Track {
        final String name;
        final double bpm, delayBeats, delayFeedback, rmsDb;
        final int beatsPerBar, bars;
        final Part[] parts;

        Track(String name, double bpm, int beatsPerBar, int bars, double delayBeats, double delayFeedback,
              double rmsDb, Part... parts) {
            this.name = name;
            this.bpm = bpm;
            this.beatsPerBar = beatsPerBar;
            this.bars = bars;
            this.delayBeats = delayBeats;
            this.delayFeedback = delayFeedback;
            this.rmsDb = rmsDb;
            this.parts = parts;
        }
    }

    private static final int MELODY = 0, CHORDS = 1, DRUMS = 2;

    static final class Part {
        final int kind, voice;
        final double gain, send, tone;
        final String notes, grid;

        Part(int kind, int voice, double gain, double send, double tone, String notes, String grid) {
            this.kind = kind;
            this.voice = voice;
            this.gain = gain;
            this.send = send;
            this.tone = tone;
            this.notes = notes;
            this.grid = grid;
        }

        String label() {
            return VOICE_NAMES[voice];
        }

        ArrayList<Ev> events(Track t) {
            ArrayList<Ev> out = new ArrayList<>();
            if (kind == MELODY) parseMelody(t, notes, out);
            else if (kind == CHORDS) parseComp(t, notes, grid, out);
            else parseDrums(t, grid, out);
            return out;
        }
    }

    static final class Ev {
        final double beat, dur, vel;
        final int midi;

        Ev(double beat, double dur, int midi, double vel) {
            this.beat = beat;
            this.dur = dur;
            this.midi = midi;
            this.vel = vel;
        }
    }

    private static Part mel(int voice, double gain, double send, double tone, String notes) {
        return new Part(MELODY, voice, gain, send, tone, notes, null);
    }

    private static Part comp(int voice, double gain, double send, double tone, String chords, String grid) {
        return new Part(CHORDS, voice, gain, send, tone, chords, grid);
    }

    private static Part drum(int voice, double gain, String grid) {
        return new Part(DRUMS, voice, gain, 0, 1, null, grid);
    }

    private static String[] bars(String s, Track t, String what) {
        String[] b = s.split("\\|");
        if (b.length != t.bars) throw new IllegalStateException(t.name + " " + what + ": " + b.length
                + " bars, expected " + t.bars);
        return b;
    }

    private static void parseMelody(Track t, String score, ArrayList<Ev> out) {
        String[] bars = bars(score, t, "melody");
        for (int b = 0; b < bars.length; b++) {
            double pos = 0;
            for (String tok : bars[b].trim().split("\\s+")) {
                int[] cursor = {0};
                int midi = tok.charAt(0) == 'r' ? -1 : pitch(tok, cursor);
                if (midi < 0) cursor[0] = 1;
                double vel = velocity(tok);
                double dur = duration(tok, cursor[0], t.name + " bar " + (b + 1));
                if (midi >= 0) out.add(new Ev(b * t.beatsPerBar + pos, dur, midi, vel));
                pos += dur;
            }
            checkBar(t, b, pos);
        }
    }

    private static void parseComp(Track t, String chords, String grid, ArrayList<Ev> out) {
        String[] bars = bars(chords, t, "chords");
        String[] grids = grid.split("\\|");
        for (int b = 0; b < bars.length; b++) {
            // the chord timeline of this bar
            ArrayList<double[]> spans = new ArrayList<>();
            ArrayList<String> names = new ArrayList<>();
            double pos = 0;
            for (String tok : bars[b].trim().split("\\s+")) {
                int colon = tok.indexOf(':');
                String name = colon < 0 ? tok : tok.substring(0, colon);
                double dur = colon < 0 ? t.beatsPerBar : duration(tok, colon + 1, t.name + " bar " + (b + 1));
                spans.add(new double[]{pos, pos + dur});
                names.add(name);
                pos += dur;
            }
            checkBar(t, b, pos);

            String g = grids[b % grids.length].trim();
            double step = (double) t.beatsPerBar / g.length();
            for (int s = 0; s < g.length(); s++) {
                double vel = gridVelocity(g.charAt(s));
                if (vel <= 0) continue;
                int e = s + 1;
                while (e < g.length() && g.charAt(e) == '-') e++;
                double at = s * step;
                String name = names.get(names.size() - 1);
                for (int k = 0; k < spans.size(); k++) if (at >= spans.get(k)[0] && at < spans.get(k)[1]) name = names.get(k);
                for (int midi : voicing(name)) out.add(new Ev(b * t.beatsPerBar + at, (e - s) * step, midi, vel));
            }
        }
    }

    private static void parseDrums(Track t, String grid, ArrayList<Ev> out) {
        String[] grids = grid.split("\\|");
        for (int b = 0; b < t.bars; b++) {
            String g = grids[b % grids.length].trim();
            double step = (double) t.beatsPerBar / g.length();
            for (int s = 0; s < g.length(); s++) {
                double vel = gridVelocity(g.charAt(s));
                if (vel > 0) out.add(new Ev(b * t.beatsPerBar + s * step, step, -1, vel));
            }
        }
    }

    private static void checkBar(Track t, int b, double pos) {
        if (Math.abs(pos - t.beatsPerBar) > 1e-9)
            throw new IllegalStateException(t.name + " bar " + (b + 1) + " has " + pos + " beats, expected " + t.beatsPerBar);
    }

    private static double gridVelocity(char c) {
        switch (c) {
            case 'X': return 1.0;
            case 'x': return 0.75;
            case 'o': return 0.45;
            case '.': case '-': return 0;
            default: throw new IllegalStateException("bad grid symbol '" + c + "'");
        }
    }

    private static double velocity(String tok) {
        if (tok.endsWith("!")) return 1.0;
        if (tok.endsWith("~")) return 0.55;
        return 0.78;
    }

    /** Length letter (w h q e s) at index i, optional dot; in quarter-note beats. */
    private static double duration(String tok, int i, String where) {
        if (i >= tok.length()) throw new IllegalStateException(where + ": no length in '" + tok + "'");
        double d;
        switch (tok.charAt(i)) {
            case 'w': d = 4; break;
            case 'h': d = 2; break;
            case 'q': d = 1; break;
            case 'e': d = 0.5; break;
            case 's': d = 0.25; break;
            default: throw new IllegalStateException(where + ": bad length in '" + tok + "'");
        }
        if (i + 1 < tok.length() && tok.charAt(i + 1) == '.') d *= 1.5;
        return d;
    }

    /** Note name like "C#5" or "Bb3" to MIDI number (A4 = 69); leaves cursor[0] after the octave. */
    private static int pitch(String tok, int[] cursor) {
        int pc;
        switch (tok.charAt(0)) {
            case 'C': pc = 0; break;
            case 'D': pc = 2; break;
            case 'E': pc = 4; break;
            case 'F': pc = 5; break;
            case 'G': pc = 7; break;
            case 'A': pc = 9; break;
            case 'B': pc = 11; break;
            default: throw new IllegalStateException("bad note '" + tok + "'");
        }
        int i = 1;
        if (i < tok.length() && tok.charAt(i) == '#') { pc++; i++; }
        else if (i < tok.length() && tok.charAt(i) == 'b') { pc--; i++; }
        if (i >= tok.length() || !Character.isDigit(tok.charAt(i))) throw new IllegalStateException("no octave in '" + tok + "'");
        int oct = tok.charAt(i) - '0';
        cursor[0] = i + 1;
        return 12 * (oct + 1) + pc;
    }

    private static int[] voicing(String chord) {
        for (String[] v : VOICINGS) {
            if (v[0].equals(chord)) {
                String[] ns = v[1].split(" ");
                int[] m = new int[ns.length];
                for (int i = 0; i < ns.length; i++) m[i] = pitch(ns[i], new int[1]);
                return m;
            }
        }
        throw new IllegalStateException("no voicing for chord " + chord);
    }

    private Music() {
    }
}
