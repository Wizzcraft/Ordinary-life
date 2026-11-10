package com.wizzcraft.ima.core;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.util.Arrays;

/**
 * Renders every music track to a WAV and measures it, since nobody here can listen.
 *
 * Usage: java -cp OUT com.wizzcraft.ima.core.MusicDump outDir [-stems]
 * Prints one line per track and "MUSIC OK"; exits with status 1 on NaN, clipping, a peak above
 * -9 dBFS, a loop longer than 20 s, a DC offset, or a click at the loop seam.
 * With -stems it also prints the level of every part, to check the balance of the arrangement.
 */
public final class MusicDump {
    static final int SR = 22050;
    static final String[] NAMES = {"0_city_day", "1_city_night", "2_work", "3_tension"};

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "tools/out/music");
        boolean stems = Arrays.asList(args).contains("-stems");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        if (Music.TRACKS != NAMES.length) fail("TRACKS is " + Music.TRACKS + ", expected " + NAMES.length);

        boolean ok = true;
        System.out.printf("%-13s %6s %5s %5s %8s %8s %9s %7s %7s %7s %7s%n", "track", "sec", "bars", "bpm",
                "peak dB", "rms dB", "DC", "seam", "step", "<150Hz", ">6kHz");
        for (int t = 0; t < Music.TRACKS; t++) {
            Music.Track tr = Music.SCORE[t];
            float[] f = Music.renderFloat(t, SR);
            short[] s = Music.render(t, SR);
            String problems = "";

            int n = f.length;
            double peak = 0, sum = 0, dc = 0;
            int clipped = 0;
            for (int i = 0; i < n; i++) {
                if (Float.isNaN(f[i]) || Float.isInfinite(f[i])) problems += " NaN@" + i;
                peak = Math.max(peak, Math.abs(f[i]));
                sum += f[i] * (double) f[i];
                dc += f[i];
                if (Math.abs(s[i]) >= 32767 || Math.abs(f[i]) >= 1) clipped++;
            }
            dc /= n;
            double rms = Math.sqrt(sum / n);
            double sec = (double) n / SR;
            double bars = sec * tr.bpm / 60.0 / tr.beatsPerBar;

            // Loop seam: the step from the last sample back to the first, against the ordinary steps.
            double[] steps = new double[n - 1];
            double stepSq = 0;
            for (int i = 1; i < n; i++) {
                steps[i - 1] = Math.abs(f[i] - f[i - 1]);
                stepSq += steps[i - 1] * steps[i - 1];
            }
            double rmsStep = Math.sqrt(stepSq / (n - 1));
            double seam = Math.abs(f[0] - f[n - 1]);
            double[] sorted = steps.clone();
            Arrays.sort(sorted);
            double p999 = sorted[(int) (0.999 * (sorted.length - 1))];
            // Levels just before and after the seam (a gap or a doubled downbeat shows up here).
            double tailRms = rmsOf(f, n - SR / 20, n), headRms = rmsOf(f, 0, SR / 20);

            double[] bands = bands(f, SR);

            if (clipped > 0) problems += " clipping(" + clipped + ")";
            if (20 * Math.log10(peak) > -9) problems += " peak above -9 dBFS";
            if (sec > 20.0001) problems += " longer than 20 s";
            if (Math.abs(dc) > 1e-3) problems += " DC offset";
            if (seam > 3 * rmsStep) problems += " seam click";

            System.out.printf("%-13s %6.2f %5.2f %5.0f %8.2f %8.2f %9.1e %7.4f %7.4f %6.1f%% %6.2f%%%n",
                    NAMES[t], sec, bars, tr.bpm, 20 * Math.log10(peak), 20 * Math.log10(rms), dc,
                    seam, rmsStep, 100 * bands[0], 100 * bands[1]);
            System.out.printf("%13s seam/typical step %.2f (max allowed 3), 99.9th pct step %.4f, "
                    + "level 50 ms before/after seam %.1f / %.1f dB%n", "", seam / rmsStep, p999,
                    20 * Math.log10(tailRms + 1e-12), 20 * Math.log10(headRms + 1e-12));
            if (stems) {
                float[] mix = Music.renderFloat(t, SR, -1, false);
                double mixRms = rmsOf(mix, 0, mix.length);
                for (int p = 0; p < tr.parts.length; p++) {
                    float[] st = Music.renderFloat(t, SR, p, false);
                    double[] b = bands(st, SR);
                    System.out.printf("%13s   %-9s %6.1f dB vs mix   <150Hz %5.1f%%  >6kHz %5.2f%%%n", "",
                            tr.parts[p].label(), 20 * Math.log10(rmsOf(st, 0, st.length) / mixRms),
                            100 * b[0], 100 * b[1]);
                }
            }
            if (!problems.isEmpty()) {
                System.out.println("  FAIL " + NAMES[t] + ":" + problems);
                ok = false;
            }
            writeWav(new File(dir, NAMES[t] + ".wav"), s, SR);
        }
        System.out.println("transpose " + Music.TRANSPOSE + " semitones; keys: day " + Music.KEY_DAY + ", night "
                + Music.KEY_NIGHT + ", work " + Music.KEY_WORK + ", tension " + Music.KEY_TENSION);
        if (!ok) fail("music checks failed");
        System.out.println("WAVs in " + dir.getPath());
        System.out.println("MUSIC OK");
    }

    static void fail(String why) {
        System.out.println("MUSIC FAIL: " + why);
        System.exit(1);
    }

    static double rmsOf(float[] x, int from, int to) {
        double s = 0;
        for (int i = from; i < to; i++) s += x[i] * (double) x[i];
        return Math.sqrt(s / Math.max(1, to - from));
    }

    /**
     * Share of energy below 150 Hz and above 6 kHz. The loop is periodic, so frames wrap around the
     * end instead of being cut off; each frame is Hann-windowed.
     */
    static double[] bands(float[] x, int sr) {
        int size = 4096, hop = 2048, n = x.length;
        double lo = 0, hi = 0, all = 0;
        double[] re = new double[size], im = new double[size];
        for (int start = 0; start < n; start += hop) {
            for (int i = 0; i < size; i++) {
                double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / size);
                re[i] = x[(start + i) % n] * w;
                im[i] = 0;
            }
            fft(re, im);
            for (int k = 1; k < size / 2; k++) {
                double p = re[k] * re[k] + im[k] * im[k], hz = (double) k * sr / size;
                all += p;
                if (hz < 150) lo += p;
                if (hz > 6000) hi += p;
            }
        }
        return new double[]{lo / Math.max(all, 1e-30), hi / Math.max(all, 1e-30)};
    }

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
            double ang = -2 * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
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

    static void writeWav(File file, short[] s, int sr) throws IOException {
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            int data = s.length * 2;
            o.writeBytes("RIFF");
            o.writeInt(Integer.reverseBytes(36 + data));
            o.writeBytes("WAVEfmt ");
            o.writeInt(Integer.reverseBytes(16));
            o.writeShort(Short.reverseBytes((short) 1));
            o.writeShort(Short.reverseBytes((short) 1));
            o.writeInt(Integer.reverseBytes(sr));
            o.writeInt(Integer.reverseBytes(sr * 2));
            o.writeShort(Short.reverseBytes((short) 2));
            o.writeShort(Short.reverseBytes((short) 16));
            o.writeBytes("data");
            o.writeInt(Integer.reverseBytes(data));
            for (short v : s) o.writeShort(Short.reverseBytes(v));
        }
    }

    private MusicDump() {
    }
}
