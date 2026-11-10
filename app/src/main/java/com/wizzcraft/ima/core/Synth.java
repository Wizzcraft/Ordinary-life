package com.wizzcraft.ima.core;

/**
 * The game's sound-effect synthesizer: every sound is built here from a tiny subtractive voice.
 * No samples, no libraries. Pure Java, so the desktop tools can render and measure the same sounds the
 * phone plays.
 *
 * <h2>How to tweak the patch table (for a synthesis teacher)</h2>
 * Each sound effect in {@link #sound(int)} is one to four {@link Note}s. A note plays one {@link Patch} at a
 * pitch, at a start time, held for a gate length, at a gain. A patch is one classic voice, signal flowing
 * left to right:
 * <pre>
 *   OSCILLATOR 1 ──┐
 *   OSCILLATOR 2 ──┼──► FILTER (LP/BP/HP, resonant) ──► AMPLIFIER ──► out
 *   NOISE SOURCE ──┘        ▲                              ▲
 *                           │                              │
 *     MOD ENVELOPE ─────────┴── pitch / cutoff     AMP ENVELOPE (ADSR)
 *     LFO ──────────────────┴── pitch / cutoff / amplitude
 * </pre>
 * The builder methods, in the order the table uses them:
 * <pre>
 *   .osc(wave, level)                    oscillator 1: SINE, TRIANGLE, SAW, PULSE; level 0..1
 *   .pw(width)                           pulse width 0..1 (0.5 = square), oscillators 1 and 2
 *   .osc2(wave, semitones, level)        oscillator 2, tuned relative to 1. Small fractions detune
 *                                        (0.1 = 10 cents chorus); odd values make bell partials
 *                                        (+15.86 = ratio 2.5, +17.58 = ratio 2.76)
 *   .noise(level)                        white-noise source, 0..1
 *   .filter(mode, cutoffHz, resonance)   LOWPASS, BANDPASS, HIGHPASS or OFF; 12 dB/oct state-variable;
 *                                        resonance 0..1 (0 = flat, 1 = Q of about 16, nearly singing)
 *   .amp(attackS, decayS, sustain, releaseS)   amplifier ADSR. Sustain is a level 0..1. Decay and
 *                                        release are exponential curves that land exactly on their
 *                                        target at the stated time, so nothing clicks.
 *   .modEnv(attackS, decayS, sustain, releaseS)  a second ADSR, output 0..1, routed by:
 *   .modToPitch(semitones)  .modToCutoff(octaves)   (negative = downward)
 *   .lfo(wave, rateHz, fadeInS)          SINE, TRIANGLE, SAW, SQUARE or SAMPLE_HOLD (random steps);
 *                                        restarts with every note; fadeInS = delayed vibrato
 *   .lfoToPitch(semitones) .lfoToCutoff(octaves) .lfoToAmp(depth 0..1)  (bipolar, +/- the amount)
 * </pre>
 * A note's pitch is a MIDI number written as a name: {@code n("C6")} (C4 = middle C, A4 = 440 Hz).
 * The note is held for its gate length, then the amp envelope releases. With sustain 0 the sound is a
 * pluck that has died away by attack + decay, so keep the gate at least that long.
 *
 * <p>Two things happen after the voices, the same for every sound, so you do not have to think about
 * them: (1) a fixed "phone speaker" band, a 12 dB/oct high-pass at 180 Hz and a 24 dB/oct low-pass at 6.5 kHz;
 * (2) the level. Each sound has a loudness target in dB (short-term RMS over the loudest 50 ms); the
 * whole sound is scaled to hit it, then pulled down if its peak would pass -3 dBFS. So note gains only
 * balance the notes inside one sound; to make a sound louder or softer, change its target.
 *
 * <p>Key: every pitched sound is in <b>C major</b> (A4 = 440 Hz, equal temperament), mostly on the
 * notes of the C major triad and G, so effects heard one after another, or over the music, agree.
 * The sad sounds (BAD, RENT) lean towards A minor, the relative minor, without leaving the key.
 */
public final class Synth {

    /** The key every pitched effect is written in, for the composer. */
    public static final String KEY = "C major (A4 = 440 Hz, equal temperament)";

    // Oscillator waves.
    public static final int SINE = 0, TRIANGLE = 1, SAW = 2, PULSE = 3;
    // Extra LFO waves (SQUARE is a 50% pulse).
    public static final int SQUARE = 3, SAMPLE_HOLD = 4;
    // Filter modes.
    public static final int OFF = 0, LOWPASS = 1, BANDPASS = 2, HIGHPASS = 3;

    // Fixed output stage (see the class comment).
    static final float BUS_HIGHPASS_HZ = 180f, BUS_LOWPASS_HZ = 6500f;
    static final float PEAK_LIMIT = 0.68f;          // -3.35 dBFS, a little headroom under -3 dBFS
    static final float TAIL_S = 0.025f;             // lets the bus filters ring out before the end
    static final float FADE_IN_S = 0.002f, FADE_OUT_S = 0.004f;

    // Loudness targets in dBFS (short-term RMS). Interface sounds share one level on purpose.
    static final float UI = -17f, EVENT = -15f;
    // Length limits from the design brief.
    static final float UI_MAX_S = 0.25f, EVENT_MAX_S = 1.2f;

    private Synth() {
    }

    // ------------------------------------------------------------------------------------------ data

    /** One voice. Plain data: every field is public and named with its unit. */
    public static final class Patch {
        // OSCILLATORS
        public int wave = TRIANGLE;
        public float oscLevel = 0f;           // 0..1
        public float pulseWidth = 0.5f;       // 0..1, PULSE only
        public int wave2 = SINE;
        public float osc2Semitones = 0f;      // relative to oscillator 1
        public float osc2Level = 0f;          // 0..1
        // NOISE SOURCE
        public float noiseLevel = 0f;         // 0..1, white
        // FILTER
        public int filterMode = OFF;
        public float cutoffHz = 20000f;
        public float resonance = 0f;          // 0..1
        // AMPLIFIER + its envelope
        public float ampAttackS = 0.002f, ampDecayS = 0.1f, ampSustain = 1f, ampReleaseS = 0.05f;
        // MODULATION ENVELOPE (0..1) and where it goes
        public float modAttackS = 0.001f, modDecayS = 0.1f, modSustain = 0f, modReleaseS = 0.05f;
        public float modToPitchSemitones = 0f, modToCutoffOctaves = 0f;
        // LFO (-1..+1) and where it goes
        public int lfoWave = SINE;
        public float lfoRateHz = 5f, lfoFadeInS = 0f;
        public float lfoToPitchSemitones = 0f, lfoToCutoffOctaves = 0f, lfoToAmp = 0f;

        public Patch osc(int wave, float level) { this.wave = wave; oscLevel = level; return this; }
        public Patch pw(float width) { pulseWidth = width; return this; }
        public Patch osc2(int wave, float semitones, float level) {
            wave2 = wave; osc2Semitones = semitones; osc2Level = level; return this;
        }
        public Patch noise(float level) { noiseLevel = level; return this; }
        public Patch filter(int mode, float cutoffHz, float resonance) {
            filterMode = mode; this.cutoffHz = cutoffHz; this.resonance = resonance; return this;
        }
        public Patch amp(float attackS, float decayS, float sustain, float releaseS) {
            ampAttackS = attackS; ampDecayS = decayS; ampSustain = sustain; ampReleaseS = releaseS; return this;
        }
        public Patch modEnv(float attackS, float decayS, float sustain, float releaseS) {
            modAttackS = attackS; modDecayS = decayS; modSustain = sustain; modReleaseS = releaseS; return this;
        }
        public Patch modToPitch(float semitones) { modToPitchSemitones = semitones; return this; }
        public Patch modToCutoff(float octaves) { modToCutoffOctaves = octaves; return this; }
        public Patch lfo(int wave, float rateHz, float fadeInS) {
            lfoWave = wave; lfoRateHz = rateHz; lfoFadeInS = fadeInS; return this;
        }
        public Patch lfoToPitch(float semitones) { lfoToPitchSemitones = semitones; return this; }
        public Patch lfoToCutoff(float octaves) { lfoToCutoffOctaves = octaves; return this; }
        public Patch lfoToAmp(float depth) { lfoToAmp = depth; return this; }
    }

    /** One played note: a patch, when it starts, its pitch (MIDI), how long the gate is held, its gain. */
    public static final class Note {
        public final Patch patch;
        public final float startS, pitch, gateS, gain;

        public Note(Patch patch, float startS, float pitch, float gateS, float gain) {
            this.patch = patch; this.startS = startS; this.pitch = pitch; this.gateS = gateS; this.gain = gain;
        }

        float endS() { return startS + gateS + Math.max(0.002f, patch.ampReleaseS); }
    }

    /** One sound effect: its notes, its loudness target (dBFS short-term RMS) and its length limit. */
    static final class Sound {
        final String name;
        final float loudDb, maxS;
        final Note[] notes;

        Sound(String name, float loudDb, float maxS, Note... notes) {
            this.name = name; this.loudDb = loudDb; this.maxS = maxS; this.notes = notes;
        }
    }

    /** MIDI number from a name such as "C6", "F#4" or "Bb5" (C4 = 60, A4 = 69). */
    public static float n(String name) {
        int i = "C D EF G A B".indexOf(Character.toUpperCase(name.charAt(0)));
        int p = 1, semi = i;
        if (name.length() > 2 && name.charAt(1) == '#') { semi++; p = 2; }
        else if (name.length() > 2 && name.charAt(1) == 'b') { semi--; p = 2; }
        return 12 * (Integer.parseInt(name.substring(p)) + 1) + semi;
    }

    private static Patch p() { return new Patch(); }

    private static Note note(Patch patch, float startS, String pitch, float gateS, float gain) {
        return new Note(patch, startS, n(pitch), gateS, gain);
    }

    // ----------------------------------------------------------------------------------- patch table

    /** The patch table: one sound per {@link Sfx} id. */
    static Sound sound(int id) {
        switch (id) {
            case Sfx.TAP: {
                // A woody "tock": triangle with a 7-semitone pitch flick down, a breath of noise for the edge.
                Patch tock = p().osc(TRIANGLE, 1).noise(0.12f).filter(LOWPASS, 3000, 0.1f)
                        .amp(0.001f, 0.045f, 0, 0.015f)
                        .modEnv(0.0005f, 0.02f, 0, 0.01f).modToPitch(7);
                return new Sound("TAP", UI, UI_MAX_S, note(tock, 0, "G5", 0.05f, 1));
            }
            case Sfx.TICK: {
                // A clock tick: band-passed noise click with a faint high C ping. Softer than TAP: it repeats.
                Patch tick = p().osc(SINE, 0.5f).noise(0.9f).filter(BANDPASS, 3200, 0.55f)
                        .amp(0.002f, 0.024f, 0, 0.01f);
                return new Sound("TICK", -21, UI_MAX_S, note(tick, 0, "C7", 0.027f, 1));
            }
            case Sfx.COIN: {
                // Two bright pulse notes up a fifth, C6 then G6, with a sine an octave up for sparkle.
                Patch coin = p().osc(PULSE, 1).pw(0.25f).osc2(SINE, 12, 0.3f).filter(LOWPASS, 4000, 0.15f)
                        .amp(0.001f, 0.15f, 0.25f, 0.05f);
                return new Sound("COIN", UI, UI_MAX_S,
                        note(coin, 0, "C6", 0.055f, 0.8f),
                        note(coin, 0.06f, "G6", 0.09f, 1));
            }
            case Sfx.GOOD: {
                // A mellow "yes": soft triangle, G5 up a fourth to C6, landing on the tonic.
                Patch good = p().osc(TRIANGLE, 1).osc2(SINE, 12, 0.25f).filter(LOWPASS, 3500, 0.1f)
                        .amp(0.003f, 0.12f, 0.4f, 0.05f);
                return new Sound("GOOD", UI, UI_MAX_S,
                        note(good, 0, "G5", 0.06f, 0.85f),
                        note(good, 0.07f, "C6", 0.09f, 1));
            }
            case Sfx.PERFECT: {
                // Sparkle: a fast pulse arpeggio C6 E6 G6 C7, plucked, with a detuned saw for shimmer.
                Patch spark = p().osc(PULSE, 1).pw(0.3f).osc2(SAW, 0.08f, 0.5f).filter(LOWPASS, 5000, 0.15f)
                        .amp(0.001f, 0.25f, 0, 0.05f);
                return new Sound("PERFECT", UI + 1, 0.6f,
                        note(spark, 0, "C6", 0.25f, 0.8f),
                        note(spark, 0.045f, "E6", 0.25f, 0.85f),
                        note(spark, 0.09f, "G6", 0.25f, 0.9f),
                        note(spark, 0.135f, "C7", 0.3f, 1));
            }
            case Sfx.BAD: {
                // A gentle "uh-uh": dull square D5 then B4 (a minor third down, towards A minor),
                // the second note sagging a whole tone onto A4 as if shrugging. Glides here ride the mod
                // envelope's decay down to sustain 0, so the release has nothing left to bend back up.
                Patch uh = p().osc(PULSE, 1).filter(LOWPASS, 1400, 0.25f).amp(0.003f, 0.1f, 0.6f, 0.05f);
                Patch sag = p().osc(PULSE, 1).filter(LOWPASS, 1300, 0.25f).amp(0.003f, 0.1f, 0.6f, 0.05f)
                        .modEnv(0.0005f, 0.12f, 0, 0.05f).modToPitch(2);
                return new Sound("BAD", UI, UI_MAX_S,
                        note(uh, 0, "D5", 0.07f, 0.9f),
                        note(sag, 0.08f, "A4", 0.09f, 1));        // starts on B4 (A4 + 2)
            }
            case Sfx.DOOR: {
                // Latch click, a short rising creak, the door thumping into its frame, the latch again.
                Patch click = p().noise(1).filter(BANDPASS, 3000, 0.5f).amp(0.0008f, 0.018f, 0, 0.005f);
                // The creak is a very low saw (a train of stick-slip pulses) through a ringing band-pass;
                // its pitch rising is the hinge speeding up, the random LFO is the wood catching.
                Patch creak = p().osc(SAW, 1).filter(BANDPASS, 1100, 0.7f).amp(0.04f, 0.05f, 0.7f, 0.08f)
                        .modEnv(0.25f, 0.01f, 1, 0.08f).modToPitch(7)
                        .lfo(SAMPLE_HOLD, 35, 0).lfoToAmp(0.6f).lfoToPitch(0.4f);
                Patch thump = p().osc(TRIANGLE, 1).noise(0.5f).filter(LOWPASS, 700, 0.2f)
                        .amp(0.001f, 0.1f, 0, 0.03f).modEnv(0.0005f, 0.04f, 0, 0.01f).modToPitch(10);
                return new Sound("DOOR", EVENT, EVENT_MAX_S,
                        note(click, 0, "C6", 0.02f, 0.6f),
                        note(creak, 0.03f, "A1", 0.25f, 0.8f),
                        note(thump, 0.3f, "G4", 0.1f, 1),
                        note(click, 0.36f, "C6", 0.02f, 0.5f));
            }
            case Sfx.PHONE: {
                // Mom calling: two short bursts of a trill. A square LFO flips the pitch +/-1.5 semitones
                // around E6+1.5, so the voice alternates exactly between G6 and E6, sixteen times a second.
                Patch ring = p().osc(TRIANGLE, 1).osc2(SINE, 12, 0.3f).filter(LOWPASS, 3500, 0.1f)
                        .amp(0.005f, 0.05f, 0.9f, 0.04f)
                        .lfo(SQUARE, 16, 0).lfoToPitch(1.5f);
                float trill = n("E6") + 1.5f;
                return new Sound("PHONE", EVENT, EVENT_MAX_S,
                        new Note(ring, 0, trill, 0.32f, 1),
                        new Note(ring, 0.46f, trill, 0.32f, 1));
            }
            case Sfx.HIRED: {
                // "Ta-daa": a square-ish brass pickup on G5, then a C major chord (C6 E6 G6) that swells
                // open (mod envelope on cutoff) with a vibrato fading in.
                Patch brass = p().osc(PULSE, 1).pw(0.35f).osc2(SAW, 0.1f, 0.6f).filter(LOWPASS, 900, 0.15f)
                        .amp(0.01f, 0.2f, 0.75f, 0.2f)
                        .modEnv(0.04f, 0.25f, 0.5f, 0.2f).modToCutoff(2.2f)
                        .lfo(SINE, 5.5f, 0.25f).lfoToPitch(0.12f);
                return new Sound("HIRED", EVENT, EVENT_MAX_S,
                        note(brass, 0, "G5", 0.09f, 0.8f),
                        note(brass, 0.12f, "C6", 0.38f, 0.75f),
                        note(brass, 0.12f, "E6", 0.38f, 0.6f),
                        note(brass, 0.12f, "G6", 0.38f, 0.55f));
            }
            case Sfx.RENT: {
                // The register: a heavy drawer thunk and a "cha" of noise, a metallic "ching" (two sines
                // at the inharmonic ratio 2.5), then a sad sigh gliding C5 down to A4.
                Patch thunk = p().osc(SINE, 1).filter(LOWPASS, 1500, 0)
                        .amp(0.001f, 0.08f, 0, 0.02f).modEnv(0.0005f, 0.04f, 0, 0.01f).modToPitch(5);
                Patch cha = p().noise(1).filter(BANDPASS, 1800, 0.35f).amp(0.001f, 0.06f, 0, 0.02f)
                        .modEnv(0.0005f, 0.05f, 0, 0.01f).modToCutoff(1);
                Patch ching = p().osc(SINE, 1).osc2(SINE, 15.86f, 0.45f).amp(0.001f, 0.45f, 0, 0.05f);
                Patch sigh = p().osc(TRIANGLE, 1).osc2(SAW, 0.12f, 0.35f).filter(LOWPASS, 1200, 0.2f)
                        .amp(0.03f, 0.1f, 0.8f, 0.18f)
                        .modEnv(0.0005f, 0.4f, 0, 0.2f).modToPitch(3)
                        .lfo(SINE, 5, 0.2f).lfoToPitch(0.1f);
                return new Sound("RENT", EVENT, EVENT_MAX_S,
                        note(thunk, 0, "G4", 0.08f, 0.8f),
                        note(cha, 0, "C6", 0.06f, 0.9f),
                        note(ching, 0.07f, "E6", 0.45f, 1),
                        note(sigh, 0.32f, "A4", 0.45f, 0.7f));    // starts on C5 (A4 + 3)
            }
            case Sfx.COLLAPSE: {
                // Fainting: a woozy detuned saw sliding an octave down from G5 to G4 (the mod envelope's slow
                // decay carries pitch and cutoff down together, fast at first, then sinking), with a vibrato
                // wobble; then a soft bump.
                Patch slide = p().osc(SAW, 1).osc2(SAW, 0.1f, 0.5f).filter(LOWPASS, 800, 0.3f)
                        .amp(0.02f, 0.1f, 0.85f, 0.15f)
                        .modEnv(0.0005f, 0.9f, 0, 0.15f).modToPitch(12).modToCutoff(1)
                        .lfo(SINE, 7, 0.2f).lfoToPitch(0.35f);
                Patch bump = p().osc(SINE, 1).noise(0.4f).filter(LOWPASS, 900, 0.1f)
                        .amp(0.001f, 0.12f, 0, 0.03f).modEnv(0.0005f, 0.06f, 0, 0.01f).modToPitch(12);
                return new Sound("COLLAPSE", EVENT, EVENT_MAX_S,
                        note(slide, 0, "G4", 0.75f, 1),         // starts on G5 (G4 + 12)
                        note(bump, 0.78f, "G4", 0.12f, 0.9f));
            }
            case Sfx.LEVEL: {
                // Promotion fanfare: a brass triplet G5 C6 E6 climbing into a held open fifth
                // (oscillator 2 a fifth below G6 gives C6+G6), with delayed vibrato.
                Patch brass = p().osc(SAW, 1).osc2(SAW, 0.1f, 0.7f).filter(LOWPASS, 900, 0.15f)
                        .amp(0.01f, 0.2f, 0.75f, 0.2f)
                        .modEnv(0.04f, 0.25f, 0.5f, 0.2f).modToCutoff(2.2f);
                Patch hold = p().osc(SAW, 1).osc2(SAW, -7, 0.8f).filter(LOWPASS, 900, 0.15f)
                        .amp(0.015f, 0.25f, 0.8f, 0.25f)
                        .modEnv(0.06f, 0.35f, 0.55f, 0.25f).modToCutoff(2.4f)
                        .lfo(SINE, 5.5f, 0.2f).lfoToPitch(0.15f);
                return new Sound("LEVEL", EVENT, EVENT_MAX_S,
                        note(brass, 0, "G5", 0.09f, 0.8f),
                        note(brass, 0.11f, "C6", 0.09f, 0.85f),
                        note(brass, 0.22f, "E6", 0.09f, 0.9f),
                        note(hold, 0.33f, "G6", 0.5f, 1));
            }
            case Sfx.POUR: {
                // Liquid: noise through a resonant band-pass whose cutoff jumps randomly (sample & hold LFO)
                // twenty-two times a second: each jump is one bubble. Retriggered every ~0.3 s, so the attack
                // is short and the release long enough (0.2 s) for consecutive copies to cross-fade.
                Patch pour = p().noise(1).filter(BANDPASS, 900, 0.75f).amp(0.03f, 0.05f, 1, 0.2f)
                        .lfo(SAMPLE_HOLD, 22, 0).lfoToCutoff(0.9f);
                return new Sound("POUR", -19, EVENT_MAX_S, note(pour, 0, "C5", 0.3f, 1));
            }
            case Sfx.SIREN: {
                // One police whoop: triangle plus a little saw, the mod envelope sweeping the pitch up an
                // octave from C5 to C6 in 0.22 s and back down over 0.4 s.
                Patch siren = p().osc(TRIANGLE, 1).osc2(SAW, 0, 0.35f).filter(LOWPASS, 2500, 0.2f)
                        .amp(0.02f, 0.1f, 0.9f, 0.1f)
                        .modEnv(0.22f, 0.4f, 0, 0.05f).modToPitch(12);
                return new Sound("SIREN", EVENT, EVENT_MAX_S, note(siren, 0, "C5", 0.62f, 1));
            }
            case Sfx.PUNCH: {
                // Slapstick "bop": two sines an octave apart dropping fast from D6 onto C5 (the upper one carries it
                // on a phone speaker), plus a short band of noise (the slap).
                Patch bop = p().osc(SINE, 1).osc2(SINE, 12, 0.6f).filter(LOWPASS, 2500, 0)
                        .amp(0.001f, 0.09f, 0, 0.02f).modEnv(0.0005f, 0.05f, 0, 0.01f).modToPitch(14);
                Patch slap = p().noise(1).filter(BANDPASS, 1200, 0.3f).amp(0.001f, 0.035f, 0, 0.01f);
                return new Sound("PUNCH", UI - 1, UI_MAX_S,
                        note(bop, 0, "C5", 0.09f, 1),
                        note(slap, 0, "C5", 0.035f, 0.5f));
            }
            case Sfx.WHOOSH: {
                // Air: noise through a band-pass swept up two octaves and back by the mod envelope,
                // the amp envelope swelling with it.
                Patch whoosh = p().noise(1).filter(BANDPASS, 700, 0.5f).amp(0.08f, 0.12f, 0, 0.02f)
                        .modEnv(0.1f, 0.11f, 0, 0.02f).modToCutoff(2f);
                return new Sound("WHOOSH", UI, UI_MAX_S, note(whoosh, 0, "C5", 0.2f, 1));
            }
            case Sfx.SIZZLE: {
                // A pan: band-passed noise high up (3 kHz), crackled by a fast sample & hold LFO on the amplifier.
                Patch sizzle = p().noise(1).filter(BANDPASS, 3000, 0.3f).amp(0.08f, 0.1f, 0.8f, 0.25f)
                        .lfo(SAMPLE_HOLD, 45, 0).lfoToAmp(0.7f);
                return new Sound("SIZZLE", -18, EVENT_MAX_S, note(sizzle, 0, "C5", 0.55f, 1));
            }
            case Sfx.BEEP: {
                // Till scanner: a steady square on G6 with its edges filtered off, organ-like envelope.
                Patch beep = p().osc(PULSE, 1).filter(LOWPASS, 2800, 0).amp(0.002f, 0.01f, 1, 0.012f);
                return new Sound("BEEP", -19, UI_MAX_S, note(beep, 0, "G6", 0.1f, 1));
            }
            case Sfx.DING: {
                // Service bell: G6 sine plus an inharmonic partial (ratio 2.76) ringing out over 0.8 s, a gentle
                // amplitude LFO for the beating of a real bell, and a tiny noise strike.
                Patch bell = p().osc(SINE, 1).osc2(SINE, 17.58f, 0.35f).amp(0.001f, 0.8f, 0, 0.05f)
                        .lfo(SINE, 6, 0.05f).lfoToAmp(0.15f);
                Patch strike = p().noise(1).filter(HIGHPASS, 4000, 0).amp(0.0005f, 0.012f, 0, 0.004f);
                return new Sound("DING", UI, EVENT_MAX_S,
                        note(bell, 0, "G6", 0.8f, 1),
                        note(strike, 0, "C6", 0.012f, 0.3f));
            }
            case Sfx.EAT: {
                // Two bites: band-passed noise chopped by a fast random LFO on amplitude and cutoff (crunch).
                Patch bite = p().noise(1).filter(BANDPASS, 1500, 0.4f).amp(0.004f, 0.06f, 0, 0.02f)
                        .lfo(SAMPLE_HOLD, 70, 0).lfoToAmp(0.8f).lfoToCutoff(0.5f);
                Patch bite2 = p().noise(1).filter(BANDPASS, 1200, 0.4f).amp(0.004f, 0.06f, 0, 0.02f)
                        .lfo(SAMPLE_HOLD, 70, 0).lfoToAmp(0.8f).lfoToCutoff(0.5f);
                return new Sound("EAT", UI, UI_MAX_S,
                        note(bite, 0, "C5", 0.065f, 1),
                        note(bite2, 0.11f, "C5", 0.065f, 0.8f));
            }
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------------------------ public API

    /** Renders one effect as 16-bit mono PCM. Unknown ids give a short silence; never throws on odd input. */
    public static short[] render(int sfxId, int sampleRate) {
        float[] f = renderFloat(sfxId, sampleRate);
        short[] out = new short[f.length];
        for (int i = 0; i < f.length; i++) {
            float v = f[i];
            if (!(v == v) || Float.isInfinite(v)) v = 0;   // defensive: NaN would be cast to 0 silently anyway
            int s = Math.round(v * 32767f);
            out[i] = (short) Math.max(-32767, Math.min(32767, s));
        }
        return out;
    }

    /** A 16-bit mono PCM WAV file (44-byte header) holding {@code pcm}. */
    public static byte[] wav(short[] pcm, int sampleRate) {
        int n = pcm == null ? 0 : pcm.length;
        int dataBytes = n * 2;
        byte[] b = new byte[44 + dataBytes];
        putAscii(b, 0, "RIFF");
        putInt(b, 4, 36 + dataBytes);
        putAscii(b, 8, "WAVE");
        putAscii(b, 12, "fmt ");
        putInt(b, 16, 16);                 // fmt chunk size
        putShort(b, 20, 1);                // PCM
        putShort(b, 22, 1);                // mono
        putInt(b, 24, sampleRate);
        putInt(b, 28, sampleRate * 2);     // byte rate
        putShort(b, 32, 2);                // block align
        putShort(b, 34, 16);               // bits per sample
        putAscii(b, 36, "data");
        putInt(b, 40, dataBytes);
        for (int i = 0; i < n; i++) putShort(b, 44 + 2 * i, pcm[i]);
        return b;
    }

    private static void putAscii(byte[] b, int o, String s) {
        for (int i = 0; i < 4; i++) b[o + i] = (byte) s.charAt(i);
    }

    private static void putInt(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24);
    }

    private static void putShort(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8);
    }

    // -------------------------------------------------------------------------------------- the voice

    /** Float render (-1..1) of one effect, after the output stage. Package-private for the dump tool. */
    static float[] renderFloat(int sfxId, int sampleRate) {
        int sr = Math.max(8000, Math.min(192000, sampleRate));
        Sound snd = sound(sfxId);
        if (snd == null) return new float[sr / 100];
        float end = 0;
        for (Note nt : snd.notes) end = Math.max(end, nt.endS());
        int len = (int) Math.ceil((end + TAIL_S) * sr);
        float[] mix = new float[len];
        for (int k = 0; k < snd.notes.length; k++) renderNote(snd.notes[k], mix, sr, sfxId * 7919 + k * 104729 + 1);
        outputStage(mix, sr, snd.loudDb);
        return mix;
    }

    /** Adds one note into {@code mix}. */
    private static void renderNote(Note nt, float[] mix, int sr, int seed) {
        Patch p = nt.patch;
        float aA = Math.max(0.001f, p.ampAttackS), aD = Math.max(0.001f, p.ampDecayS);
        float aR = Math.max(0.002f, p.ampReleaseS), aS = clamp01(p.ampSustain);
        float mA = Math.max(0.0005f, p.modAttackS), mD = Math.max(0.001f, p.modDecayS);
        float mR = Math.max(0.001f, p.modReleaseS), mS = clamp01(p.modSustain);
        float gate = Math.max(0.001f, nt.gateS);
        int start = Math.round(nt.startS * sr);
        int count = (int) Math.ceil((gate + aR) * sr);
        float k = 2f - 2f * clamp01(p.resonance) * 0.97f;   // SVF damping: 2 = flat, 0.06 = Q ~16
        float pw = Math.max(0.02f, Math.min(0.98f, p.pulseWidth));
        double ph1 = 0, ph2 = 0, lfoPh = 0;
        float ic1 = 0, ic2 = 0;
        int rng = seed == 0 ? 1 : seed;
        float sh = 0, shSmooth = 0;
        long shStep = -1;
        float shCoef = (float) (1 - Math.exp(-1.0 / (0.0015 * sr)));   // ~1.5 ms slew on the random steps
        float ratio2 = (float) Math.pow(2, p.osc2Semitones / 12.0);
        for (int i = 0; i < count; i++) {
            int idx = start + i;
            if (idx < 0) continue;
            if (idx >= mix.length) break;
            float t = i / (float) sr;
            float amp = env(t, gate, aA, aD, aS, aR);
            float mod = env(t, gate, mA, mD, mS, mR);

            // LFO
            double lp = lfoPh;
            lfoPh += p.lfoRateHz / sr;
            float lfo;
            switch (p.lfoWave) {
                case TRIANGLE: lfo = tri(lp - Math.floor(lp)); break;
                case SAW: lfo = (float) (2 * (lp - Math.floor(lp)) - 1); break;
                case SQUARE: lfo = (lp - Math.floor(lp)) < 0.5 ? 1f : -1f; break;
                case SAMPLE_HOLD: {
                    long step = (long) Math.floor(lp);
                    if (step != shStep) {
                        shStep = step;
                        sh = hash(seed * 31 + (int) step);
                        if (i == 0) shSmooth = sh;
                    }
                    shSmooth += (sh - shSmooth) * shCoef;
                    lfo = shSmooth;
                    break;
                }
                default: lfo = (float) Math.sin(2 * Math.PI * lp);
            }
            if (p.lfoFadeInS > 0) lfo *= Math.min(1f, t / p.lfoFadeInS);

            // OSCILLATORS + NOISE
            float pitch = nt.pitch + mod * p.modToPitchSemitones + lfo * p.lfoToPitchSemitones;
            float f1 = (float) (440.0 * Math.pow(2, (pitch - 69) / 12.0));
            float dt1 = Math.min(0.45f, f1 / sr), dt2 = Math.min(0.45f, f1 * ratio2 / sr);
            float x = 0;
            if (p.oscLevel != 0) x += p.oscLevel * wave(p.wave, ph1, dt1, pw);
            if (p.osc2Level != 0) x += p.osc2Level * wave(p.wave2, ph2, dt2, pw);
            ph1 += dt1; if (ph1 >= 1) ph1 -= 1;
            ph2 += dt2; if (ph2 >= 1) ph2 -= 1;
            if (p.noiseLevel != 0) {
                rng ^= rng << 13; rng ^= rng >>> 17; rng ^= rng << 5;
                x += p.noiseLevel * (rng * (1f / 2147483648f));
            }

            // FILTER (topology-preserving state-variable filter: stable under fast cutoff sweeps)
            if (p.filterMode != OFF) {
                double fc = p.cutoffHz * Math.pow(2, mod * p.modToCutoffOctaves + lfo * p.lfoToCutoffOctaves);
                fc = Math.max(20, Math.min(0.45 * sr, fc));
                float g = (float) Math.tan(Math.PI * fc / sr);
                float a1 = 1f / (1f + g * (g + k)), a2 = g * a1, a3 = g * a2;
                float v3 = x - ic2;
                float v1 = a1 * ic1 + a2 * v3;
                float v2 = ic2 + a2 * ic1 + a3 * v3;
                ic1 = 2 * v1 - ic1;
                ic2 = 2 * v2 - ic2;
                x = p.filterMode == LOWPASS ? v2 : p.filterMode == BANDPASS ? v1 : x - k * v1 - v2;
            }

            // AMPLIFIER
            float tremolo = 1f - clamp01(p.lfoToAmp) * (0.5f - 0.5f * lfo);
            mix[idx] += x * amp * tremolo * nt.gain;
        }
    }

    /**
     * ADSR level at time t for a gate of the given length. Stateless. Attack is linear from exactly 0;
     * decay and release are exponential curves rescaled to arrive exactly at their target.
     */
    static float env(float t, float gate, float a, float d, float s, float r) {
        if (t >= gate) {
            float atGate = env(gate * 0.99999f, gate, a, d, s, r);
            float x = (t - gate) / r;
            return x >= 1 ? 0 : atGate * fall(x);
        }
        if (t < a) return t / a;
        float x = (t - a) / d;
        return x >= 1 ? s : s + (1 - s) * fall(x);
    }

    /** 1 at x = 0 down to exactly 0 at x = 1, shaped like an RC discharge to -40 dB. */
    private static float fall(float x) {
        return (float) ((Math.exp(-4.6 * x) - 0.01) / 0.99);
    }

    private static float wave(int w, double ph, float dt, float pw) {
        switch (w) {
            case SINE: return (float) Math.sin(2 * Math.PI * ph);
            case SAW: return (float) (2 * ph - 1) - blep(ph, dt);
            case PULSE: {
                double ph2 = ph - pw; if (ph2 < 0) ph2 += 1;
                return (ph < pw ? 1f : -1f) + blep(ph, dt) - blep(ph2, dt);
            }
            default: return tri(ph);
        }
    }

    /** Triangle starting at 0 and rising, so a note's first sample is silent. */
    private static float tri(double ph) {
        double q = ph + 0.25; q -= Math.floor(q);
        return (float) (1 - 4 * Math.abs(q - 0.5));
    }

    /** PolyBLEP correction: rounds off the saw and pulse edges so they do not alias into hiss. */
    private static float blep(double t, float dt) {
        if (t < dt) { double x = t / dt; return (float) (x + x - x * x - 1); }
        if (t > 1 - dt) { double x = (t - 1) / dt; return (float) (x * x + x + x + 1); }
        return 0;
    }

    private static float hash(int v) {
        int h = v * 0x9E3779B1;
        h ^= h >>> 15; h *= 0x85EBCA77; h ^= h >>> 13; h *= 0xC2B2AE3D; h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x800000 - 1f;
    }

    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    // ---------------------------------------------------------------------------------- output stage

    /** Phone-speaker band, loudness to target, peak ceiling, edge fades. In place. */
    private static void outputStage(float[] x, int sr, float loudDb) {
        svf(x, sr, BUS_HIGHPASS_HZ, HIGHPASS);
        svf(x, sr, BUS_LOWPASS_HZ, LOWPASS);
        svf(x, sr, BUS_LOWPASS_HZ, LOWPASS);   // twice: 24 dB/oct, so noise sounds stay out of the fizz band
        float rms = shortTermRms(x, sr), peak = 0;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        // A NaN falls through and stays NaN on purpose, so SynthDump catches it; render() sanitizes.
        if (rms <= 1e-9f || peak <= 1e-9f) {
            java.util.Arrays.fill(x, 0);
            return;
        }
        float gain = (float) Math.pow(10, loudDb / 20.0) / rms;
        if (peak * gain > PEAK_LIMIT) gain = PEAK_LIMIT / peak;
        int fi = Math.max(1, Math.round(FADE_IN_S * sr)), fo = Math.max(1, Math.round(FADE_OUT_S * sr));
        for (int i = 0; i < x.length; i++) {
            float g = gain;
            if (i < fi) g *= 0.5f - 0.5f * (float) Math.cos(Math.PI * i / fi);
            int j = x.length - 1 - i;
            if (j < fo) g *= 0.5f - 0.5f * (float) Math.cos(Math.PI * j / fo);
            x[i] *= g;
        }
    }

    /** Butterworth (Q = 0.707) two-pole high- or low-pass, in place. */
    private static void svf(float[] x, int sr, float fc, int mode) {
        float g = (float) Math.tan(Math.PI * Math.min(fc, 0.45f * sr) / sr), k = 1.41421356f;
        float a1 = 1f / (1f + g * (g + k)), a2 = g * a1, a3 = g * a2, ic1 = 0, ic2 = 0;
        for (int i = 0; i < x.length; i++) {
            float v0 = x[i], v3 = v0 - ic2;
            float v1 = a1 * ic1 + a2 * v3, v2 = ic2 + a2 * ic1 + a3 * v3;
            ic1 = 2 * v1 - ic1;
            ic2 = 2 * v2 - ic2;
            x[i] = mode == LOWPASS ? v2 : v0 - k * v1 - v2;
        }
    }

    /** RMS over the loudest 50 ms window (hop 5 ms); the whole sound if it is shorter. */
    static float shortTermRms(float[] x, int sr) {
        int w = Math.max(1, Math.min(x.length, Math.round(0.05f * sr)));
        int hop = Math.max(1, Math.round(0.005f * sr));
        double best = 0;
        for (int s = 0; s + w <= x.length; s += hop) {
            double sum = 0;
            for (int i = s; i < s + w; i++) sum += x[i] * x[i];
            best = Math.max(best, sum / w);
        }
        return (float) Math.sqrt(best);
    }
}
