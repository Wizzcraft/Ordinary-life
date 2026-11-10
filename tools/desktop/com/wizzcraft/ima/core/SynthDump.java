package com.wizzcraft.ima.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Renders every sound effect to a WAV file and measures it, since nobody here can listen.
 * Fails loudly (exit code 1) on NaN, clipping, a peak over -3 dBFS, a click at either end, a sound that is
 * too long or too short, silence, DC, too much energy below 200 Hz or above 6 kHz, and a POUR that does
 * not chain smoothly when retriggered every 0.3 s.
 *
 * <pre>java -cp tools/out/classes com.wizzcraft.ima.core.SynthDump &lt;outDir&gt; [sampleRate]</pre>
 */
public final class SynthDump {
    static final float MAX_LOW_SHARE = 0.05f, MAX_HIGH_SHARE = 0.05f;

    private SynthDump() {
    }

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "tools/out/sfx");
        int sr = args.length > 1 ? Integer.parseInt(args[1]) : 44100;
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        int failures = 0;
        StringBuilder problems = new StringBuilder();
        System.out.println("Key: " + Synth.KEY + ", sample rate " + sr + " Hz");
        System.out.printf(Locale.ROOT, "%-3s %-9s %6s %6s %6s %6s %7s %6s %6s %7s %5s %5s%n",
                "id", "name", "len_s", "max_s", "peak", "rms", "target", "dc%", "first", "last", "cent", "<200%");
        System.out.printf(Locale.ROOT, "%-3s %-9s %6s %6s %6s %6s %7s %6s %6s %7s %5s %5s%n",
                "", "", "", "", "dBFS", "dBFS", "dBFS", "", "", "", "Hz", ">6k%");
        float uiMin = 99, uiMax = -99;
        for (int id = 0; id < Sfx.COUNT; id++) {
            Synth.Sound snd = Synth.sound(id);
            if (snd == null) {
                failures++;
                problems.append("id ").append(id).append(": no sound in the patch table\n");
                continue;
            }
            float[] f = Synth.renderFloat(id, sr);
            short[] s = Synth.render(id, sr);
            String name = snd.name;
            Files.write(new File(dir, String.format(Locale.ROOT, "%02d_%s.wav", id, name.toLowerCase(Locale.ROOT)))
                    .toPath(), Synth.wav(s, sr));

            StringBuilder why = new StringBuilder();
            for (float v : f) {
                if (Float.isNaN(v) || Float.isInfinite(v)) { why.append(" NaN/Inf;"); break; }
            }
            double sum = 0;
            int peakS = 0;
            for (short v : s) { sum += v; peakS = Math.max(peakS, Math.abs(v)); }
            float peak = peakS / 32767f;
            float peakDb = db(peak);
            float rms = 0;
            float[] fs = new float[s.length];
            for (int i = 0; i < s.length; i++) fs[i] = s[i] / 32767f;
            rms = Synth.shortTermRms(fs, sr);
            float rmsDb = db(rms);
            float dc = (float) (sum / Math.max(1, s.length) / 32767.0);
            float len = s.length / (float) sr;
            int first = s.length > 0 ? s[0] : 0, last = s.length > 0 ? s[s.length - 1] : 0;
            float[] spec = spectrum(fs, sr);

            if (peakS >= 32767) why.append(" clipping;");
            for (float v : f) if (Math.abs(v) > 1) { why.append(" float overload;"); break; }
            if (peakDb > -3f) why.append(" peak over -3 dBFS;");
            if (peak < 0.03f) why.append(" (nearly) silent;");
            if (len > snd.maxS + 1e-4f) why.append(" too long (max ").append(snd.maxS).append(" s);");
            if (len < 0.04f) why.append(" shorter than 40 ms;");
            if (Math.abs(first) > 16 || Math.abs(last) > 16) why.append(" click: edge sample not near zero;");
            int ms = sr / 1000;
            if (maxAbs(fs, 0, ms) > 0.25f) why.append(" click: loud within the first 1 ms;");
            if (maxAbs(fs, s.length - ms, s.length) > 0.06f) why.append(" click: loud within the last 1 ms;");
            if (Math.abs(dc) > 0.005f) why.append(" DC offset;");
            if (spec[1] > MAX_LOW_SHARE) why.append(" too much energy below 200 Hz;");
            if (spec[2] > MAX_HIGH_SHARE) why.append(" too much energy above 6 kHz;");
            if (snd.maxS <= Synth.UI_MAX_S) {
                uiMin = Math.min(uiMin, rmsDb);
                uiMax = Math.max(uiMax, rmsDb);
            }
            System.out.printf(Locale.ROOT, "%-3d %-9s %6.3f %6.2f %6.1f %6.1f %7.1f %6.3f %6d %7d %5.0f %5.1f %4.1f%s%n",
                    id, name, len, snd.maxS, peakDb, rmsDb, snd.loudDb, dc * 100, first, last, spec[0],
                    spec[1] * 100, spec[2] * 100, rmsDb < snd.loudDb - 0.5f ? "  (peak-limited)" : "");
            if (why.length() > 0) {
                failures++;
                problems.append(name).append(":").append(why).append('\n');
            }
        }
        System.out.printf(Locale.ROOT, "Interface sounds (<= %.2f s): short-term RMS spread %.1f dB (%.1f .. %.1f dBFS)%n",
                Synth.UI_MAX_S, uiMax - uiMin, uiMin, uiMax);

        // POUR is retriggered about every 0.3 s while a bartender pours: overlap copies and check the seam.
        float[] pour = Synth.renderFloat(Sfx.POUR, sr);
        int step = Math.round(0.3f * sr), copies = 8;
        float[] chain = new float[step * (copies - 1) + pour.length];
        for (int c = 0; c < copies; c++) for (int i = 0; i < pour.length; i++) chain[c * step + i] += pour[i];
        int w = Math.round(0.02f * sr);
        float lo = 99, hi = -99, chainPeak = 0;
        for (int st = step; st + w <= step * (copies - 1); st += w / 2) {
            double e = 0;
            for (int i = st; i < st + w; i++) e += chain[i] * chain[i];
            float d = db((float) Math.sqrt(e / w));
            lo = Math.min(lo, d);
            hi = Math.max(hi, d);
        }
        for (float v : chain) chainPeak = Math.max(chainPeak, Math.abs(v));
        // The bubbles make the level flicker by themselves: compare with one copy's own sustained part.
        float slo = 99, shi = -99;
        for (int st = Math.round(0.04f * sr); st + w <= Math.round(0.3f * sr); st += w / 2) {
            double e = 0;
            for (int i = st; i < st + w; i++) e += pour[i] * pour[i];
            float d = db((float) Math.sqrt(e / w));
            slo = Math.min(slo, d);
            shi = Math.max(shi, d);
        }
        System.out.printf(Locale.ROOT, "POUR alone, sustained part: 20 ms RMS %.1f .. %.1f dBFS (ripple %.1f dB)%n",
                slo, shi, shi - slo);
        System.out.printf(Locale.ROOT, "POUR chained every 0.30 s: 20 ms RMS %.1f .. %.1f dBFS (ripple %.1f dB), peak %.1f dBFS%n",
                lo, hi, hi - lo, db(chainPeak));
        if (hi - lo > (shi - slo) + 2f) { failures++; problems.append("POUR: the seams dip or bulge more than 2 dB beyond the bubbles\n"); }
        if (chainPeak > 0.89f) { failures++; problems.append("POUR: chained copies peak above -1 dBFS\n"); }

        if (failures > 0) {
            System.out.print("FAILED:\n" + problems);
            System.exit(1);
        }
        System.out.println("SYNTH OK (" + Sfx.COUNT + " sounds written to " + dir.getPath() + ")");
    }

    static float db(float v) { return (float) (20 * Math.log10(Math.max(1e-9, v))); }

    static float maxAbs(float[] x, int from, int to) {
        float m = 0;
        for (int i = Math.max(0, from); i < Math.min(x.length, to); i++) m = Math.max(m, Math.abs(x[i]));
        return m;
    }

    /** Averaged 2048-point Hann power spectrum: {centroid Hz, share below 200 Hz, share above 6 kHz}. */
    static float[] spectrum(float[] x, int sr) {
        int n = 2048, hop = 512;
        double[] power = new double[n / 2];
        double[] re = new double[n], im = new double[n];
        for (int st = 0; st == 0 || st + n <= x.length; st += hop) {
            for (int i = 0; i < n; i++) {
                double v = st + i < x.length ? x[st + i] : 0;
                re[i] = v * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)));
                im[i] = 0;
            }
            fft(re, im);
            for (int k = 0; k < n / 2; k++) power[k] += re[k] * re[k] + im[k] * im[k];
        }
        double tot = 0, wsum = 0, low = 0, high = 0;
        for (int k = 1; k < n / 2; k++) {
            double f = k * (double) sr / n;
            tot += power[k];
            wsum += f * power[k];
            if (f < 200) low += power[k];
            if (f > 6000) high += power[k];
        }
        if (tot <= 0) return new float[] {0, 0, 0};
        return new float[] {(float) (wsum / tot), (float) (low / tot), (float) (high / tot)};
    }

    /** In-place iterative radix-2 FFT. */
    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j, b = i + j + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }
}
