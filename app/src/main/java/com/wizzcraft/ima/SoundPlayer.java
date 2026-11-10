package com.wizzcraft.ima;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.SoundPool;
import android.util.Log;

import com.wizzcraft.ima.core.Game;
import com.wizzcraft.ima.core.Music;
import com.wizzcraft.ima.core.Sfx;
import com.wizzcraft.ima.core.Synth;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Plays the game's synthesized audio. Effects: rendered once by {@link Synth} to WAV files in the cache
 * directory, then played through a {@link SoundPool}. Music: a looping {@link AudioTrack} in streaming
 * mode fed by one thread that owns it (see {@link #musicLoop()} for why).
 *
 * <p>Audio must never take the game down: every failure is caught, logged and turns into silence.
 * Effects requested before loading has finished are dropped.
 */
public final class SoundPlayer {
    private static final String TAG = "ImaSound";
    private static final int SAMPLE_RATE = 44100;
    private static final int MAX_STREAMS = 6;
    private static final float SFX_VOLUME = 1.0f;
    /** Music sits under the effects; the composer's mix decides the rest. */
    private static final float MUSIC_VOLUME = 0.7f;
    private static final float MUSIC_FADE_S = 0.4f;
    /** Write unit for the music thread, about 23 ms. */
    private static final int CHUNK = 1024;

    private final Context ctx;
    private final Object lock = new Object();
    private int[] soundIds = new int[Sfx.COUNT];              // SoundPool sample ids, 0 = none; guarded by lock
    private final java.util.HashSet<Integer> loaded = new java.util.HashSet<>();   // guarded by lock
    private volatile SoundPool pool;
    private volatile boolean released, paused;
    private volatile int musicWanted = -1;
    private volatile boolean musicBroken;
    private volatile File cacheDir;                            // set by the loader once the stamp is checked
    private int rng = 0x2545F491;                              // frame() thread only

    public SoundPlayer(Context ctx) {
        Context app = null;
        try {
            app = ctx.getApplicationContext();
        } catch (RuntimeException e) {
            Log.w(TAG, "no application context", e);
        }
        this.ctx = app != null ? app : ctx;
        try {
            Thread loader = new Thread(this::loadEffects, "ima-sfx-loader");
            loader.setDaemon(true);
            loader.start();
            Thread music = new Thread(this::musicLoop, "ima-music");
            music.setDaemon(true);
            music.start();
        } catch (Throwable t) {
            Log.w(TAG, "audio threads did not start; staying silent", t);
        }
    }

    /** Once per frame: plays the queued effects and follows the wanted music track. */
    public void frame(Game game) {
        if (game == null) return;
        try {
            int s;
            int guard = 0;
            while ((s = game.pollSound()) >= 0 && guard++ < 64) play(s);
            int m = game.musicTrack();
            if (m != musicWanted) {
                musicWanted = m;
                wake();
            }
        } catch (Throwable t) {
            Log.w(TAG, "frame", t);
        }
    }

    public void pause() {
        paused = true;
        SoundPool p = pool;
        if (p != null) {
            try {
                p.autoPause();
            } catch (Throwable t) {
                Log.w(TAG, "autoPause", t);
            }
        }
        wake();
    }

    public void resume() {
        paused = false;
        SoundPool p = pool;
        if (p != null) {
            try {
                p.autoResume();
            } catch (Throwable t) {
                Log.w(TAG, "autoResume", t);
            }
        }
        wake();
    }

    /** Frees everything. The music thread releases its own AudioTrack on its way out. */
    public void release() {
        SoundPool p;
        synchronized (lock) {
            released = true;
            p = pool;
            pool = null;
            lock.notifyAll();
        }
        if (p != null) {
            try {
                p.release();
            } catch (Throwable t) {
                Log.w(TAG, "SoundPool.release", t);
            }
        }
    }

    // ------------------------------------------------------------------------------------- effects

    private void play(int id) {
        SoundPool p = pool;
        if (p == null || paused || id < 0 || id >= Sfx.COUNT) return;
        int sample;
        synchronized (lock) {
            sample = soundIds[id];
            if (sample == 0 || !loaded.contains(sample)) return;
        }
        // Noise-based sounds get a small random rate change so repeats (a pour every 0.3 s, a run of
        // punches) do not sound like a machine. Tonal sounds stay in tune with the key.
        float rate = 1f;
        if (id == Sfx.POUR || id == Sfx.PUNCH || id == Sfx.WHOOSH || id == Sfx.SIZZLE || id == Sfx.EAT) {
            rng ^= rng << 13; rng ^= rng >>> 17; rng ^= rng << 5;
            rate = 0.95f + 0.1f * ((rng >>> 8) & 0xFFFF) / 65535f;
        }
        p.play(sample, SFX_VOLUME, SFX_VOLUME, 1, 0, rate);
    }

    /** Background thread: render (or reuse) the WAVs and load them into a fresh SoundPool. */
    private void loadEffects() {
        try {
            File dir = prepareCacheDir();
            cacheDir = dir;
            String[] paths = new String[Sfx.COUNT];
            for (int id = 0; id < Sfx.COUNT && !released; id++) {
                File f = new File(dir, "sfx_" + id + ".wav");
                if (dir == null || !f.isFile() || f.length() <= 44) {
                    short[] pcm = Synth.render(id, SAMPLE_RATE);
                    if (dir == null) continue;
                    writeAtomically(f, Synth.wav(pcm, SAMPLE_RATE));
                }
                paths[id] = f.getAbsolutePath();
            }
            if (released) return;

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            SoundPool p = new SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(attrs).build();
            // The callback can arrive (on the main looper) before load() has returned its id to us,
            // so it only records which sample ids are playable; play() looks the id up.
            p.setOnLoadCompleteListener((sp, sampleId, status) -> {
                if (status == 0) {
                    synchronized (lock) {
                        loaded.add(sampleId);
                    }
                } else {
                    Log.w(TAG, "sample " + sampleId + " failed to load: " + status);
                }
            });
            int[] ids = new int[Sfx.COUNT];
            for (int id = 0; id < Sfx.COUNT && !released; id++) {
                ids[id] = paths[id] == null ? 0 : p.load(paths[id], 1);
            }
            synchronized (lock) {
                if (released) {
                    p.release();
                    return;
                }
                soundIds = ids;
                pool = p;
            }
        } catch (Throwable t) {
            Log.w(TAG, "effects unavailable; staying silent", t);
        }
    }

    // --------------------------------------------------------------------------------------- music

    /**
     * The music thread. It alone creates, feeds, pauses and releases the AudioTrack, so no AudioTrack
     * call ever races another thread (release() during a write is a known native crash on some
     * devices). It writes non-blocking and sleeps briefly when the buffer is full, so it never hangs
     * and notices pause, release and track changes within a few milliseconds.
     *
     * <p>Why a streamed AudioTrack: the loop is sample-accurate (we wrap the index ourselves, no decoder
     * gap as with MediaPlayer looping), there is no size limit (SoundPool refuses samples over about
     * 1 MB decoded, and a static AudioTrack must allocate the whole track in shared memory up front),
     * and fades between tracks are plain multiplication, so changing from day to night never clicks.
     */
    private void musicLoop() {
        AudioTrack track = null;
        boolean playing = false;
        short[] pcm = null;
        int current = -1, pos = 0, pendingOff = 0, pendingLen = 0, failures = 0;
        float gain = 0f;
        final float step = 1f / (MUSIC_FADE_S * SAMPLE_RATE);
        short[] chunk = new short[CHUNK];
        try {
            while (!released && !musicBroken) {
                if (paused) {
                    if (playing) {
                        track.pause();
                        playing = false;
                    }
                    sleep(100);
                    continue;
                }
                int want = musicWanted;
                // Switch only once the old track has faded out and its last chunk is written.
                if (want != current && (pcm == null || (gain <= 0f && pendingLen == 0))) {
                    current = want;
                    pcm = want >= 0 ? loadMusic(want) : null;
                    pos = 0;
                    gain = 0f;
                    pendingLen = 0;
                    continue;   // loading can take a while: re-read the flags before playing
                }
                if (pcm == null) {
                    if (playing) {
                        track.pause();
                        track.flush();
                        playing = false;
                    }
                    sleep(100);
                    continue;
                }
                if (track == null) {
                    track = createTrack();
                    if (track == null) {
                        if (++failures >= 3) musicBroken = true;
                        sleep(1000);
                        continue;
                    }
                }
                if (pendingLen == 0) {
                    float target = want == current ? 1f : 0f;
                    int len = pcm.length;
                    for (int i = 0; i < CHUNK; i++) {
                        if (gain < target) gain = Math.min(target, gain + step);
                        else if (gain > target) gain = Math.max(target, gain - step);
                        chunk[i] = (short) (pcm[pos] * gain * MUSIC_VOLUME);
                        if (++pos >= len) pos = 0;
                    }
                    pendingOff = 0;
                    pendingLen = CHUNK;
                }
                int n = track.write(chunk, pendingOff, pendingLen, AudioTrack.WRITE_NON_BLOCKING);
                if (n < 0) {
                    // Dead track (audio server restarted, device changed): rebuild it from scratch.
                    Log.w(TAG, "AudioTrack.write returned " + n + "; recreating the track");
                    releaseTrack(track);
                    track = null;
                    playing = false;
                    if (++failures >= 5) musicBroken = true;
                    sleep(500);
                    continue;
                }
                pendingOff += n;
                pendingLen -= n;
                if (!playing) {
                    track.play();
                    playing = true;
                }
                if (n == 0) sleep(10);
            }
        } catch (Throwable t) {
            Log.w(TAG, "music stopped; staying silent", t);
        } finally {
            releaseTrack(track);
        }
    }

    private AudioTrack createTrack() {
        try {
            int min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            // At least 300 ms, so the 10 ms naps of the writer never starve it.
            int bytes = Math.max(min > 0 ? min * 2 : 0, SAMPLE_RATE * 2 * 3 / 10);
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            if (t.getState() != AudioTrack.STATE_INITIALIZED) {
                t.release();
                Log.w(TAG, "AudioTrack did not initialize");
                return null;
            }
            return t;
        } catch (Throwable e) {
            Log.w(TAG, "AudioTrack unavailable", e);
            return null;
        }
    }

    private static void releaseTrack(AudioTrack t) {
        if (t == null) return;
        try {
            t.pause();
            t.flush();
        } catch (Throwable ignored) {
            // releasing anyway
        }
        try {
            t.release();
        } catch (Throwable e) {
            Log.w(TAG, "AudioTrack.release", e);
        }
    }

    /** A music track's PCM: from the cache if this build already rendered it, else rendered now. */
    private short[] loadMusic(int trackId) {
        File dir = cacheDir;
        File f = dir == null ? null : new File(dir, "music_" + trackId + ".wav");
        try {
            if (f != null && f.isFile() && f.length() > 44) {
                short[] pcm = readWavPcm(f);
                if (pcm != null && pcm.length > 0) return pcm;
            }
        } catch (Throwable t) {
            Log.w(TAG, "music cache unreadable; rendering", t);
        }
        try {
            short[] pcm = Music.render(trackId, SAMPLE_RATE);
            if (pcm == null || pcm.length == 0) return null;
            if (f != null) {
                try {
                    writeAtomically(f, Synth.wav(pcm, SAMPLE_RATE));
                } catch (Throwable t) {
                    Log.w(TAG, "music cache not written", t);
                }
            }
            return pcm;
        } catch (Throwable t) {
            Log.w(TAG, "music track " + trackId + " unavailable", t);
            return null;
        }
    }

    // --------------------------------------------------------------------------------------- cache

    /**
     * The cache folder for this exact build. Keyed on versionCode and also on the install time:
     * a locally built APK always has versionCode 1, and a patch tweak must not play stale sounds.
     * Returns null if the cache cannot be used (effects are then skipped, never a crash).
     */
    private File prepareCacheDir() {
        try {
            File dir = new File(ctx.getCacheDir(), "audio");
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            String stamp = "1";
            try {
                PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                stamp = pi.getLongVersionCode() + ":" + pi.lastUpdateTime + ":" + SAMPLE_RATE;
            } catch (Throwable t) {
                stamp = "unknown:" + System.currentTimeMillis();   // forces a fresh render
            }
            File stampFile = new File(dir, "stamp.txt");
            String old = null;
            try {
                if (stampFile.isFile()) old = new String(readAll(stampFile), "UTF-8").trim();
            } catch (IOException e) {
                old = null;
            }
            if (!stamp.equals(old)) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (!f.delete()) Log.w(TAG, "could not delete " + f);
                    }
                }
                writeAtomically(stampFile, stamp.getBytes("UTF-8"));
            }
            return dir;
        } catch (Throwable t) {
            Log.w(TAG, "audio cache unavailable", t);
            return null;
        }
    }

    private static void writeAtomically(File f, byte[] data) throws IOException {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(f)) {
            if (!f.delete() || !tmp.renameTo(f)) throw new IOException("rename failed: " + f);
        }
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream((int) Math.min(Integer.MAX_VALUE, f.length()));
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    /** PCM of a WAV this class wrote (Synth.wav: 44-byte header, 16-bit mono little-endian). */
    private static short[] readWavPcm(File f) throws IOException {
        byte[] b = readAll(f);
        if (b.length <= 44 || b[0] != 'R' || b[1] != 'I' || b[2] != 'F' || b[3] != 'F') return null;
        int n = (b.length - 44) / 2;
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) pcm[i] = (short) ((b[44 + 2 * i] & 0xFF) | (b[45 + 2 * i] << 8));
        return pcm;
    }

    // ------------------------------------------------------------------------------------- threads

    private void sleep(long ms) throws InterruptedException {
        synchronized (lock) {
            if (!released) lock.wait(ms);
        }
    }

    private void wake() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }
}
