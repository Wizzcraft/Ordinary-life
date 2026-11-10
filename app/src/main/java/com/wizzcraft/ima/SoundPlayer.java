package com.wizzcraft.ima;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
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
 * mode fed by one thread that owns it (see {@link #musicLoop()} for why); each track is rendered once
 * on a background thread, kept in memory and cached as a WAV for the next start.
 *
 * <p>The sound and music switches (opt.sfx, opt.music) live in the engine: with effects off
 * {@link Game#pollSound()} stays empty, with music off {@link Game#musicTrack()} is -1. This class only
 * follows those two calls. Audio focus is asked for when music first has to play, so a player who
 * switched the music off keeps their own podcast; while a phone call holds the focus everything is silent.
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
    /** Fade in, fade out and the crossfade between two tracks. */
    private static final float MUSIC_FADE_S = 0.6f;
    /** Music level while another app asks us to duck (a navigation prompt, a notification). */
    private static final float MUSIC_DUCK = 0.25f;
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
    private final Object cacheLock = new Object();
    private File cacheDir;                                     // guarded by cacheLock, see audioDir()
    private boolean cacheChecked;                              // guarded by cacheLock
    private final Object renderLock = new Object();
    private final short[][] tracks = new short[Music.TRACKS][];   // rendered loops; guarded by lock
    private final int[] trackState = new int[Music.TRACKS];    // 0 not asked, 1 rendering, 2 ready, 3 failed; guarded by lock
    private int rng = 0x2545F491;                              // frame() thread only

    // Audio focus. The request is made and dropped on the UI thread; the listener only flips flags.
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private boolean focusAsk = true;                           // ask at the next frame that wants music
    private volatile boolean focusHeld;
    private volatile boolean musicHeld;                        // focus is elsewhere: no music
    private volatile boolean sfxHeld;                          // a phone call: no effects either
    private volatile boolean ducked;

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
                if (m >= 0 && musicWanted < 0 && !focusHeld) focusAsk = true;
                musicWanted = m;
                wake();
            }
            if (m >= 0 && focusAsk && !paused) {
                focusAsk = false;
                requestFocus();
                wake();
            }
        } catch (Throwable t) {
            Log.w(TAG, "frame", t);
        }
    }

    public void pause() {
        paused = true;
        abandonFocus();
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
        focusAsk = true;
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
        abandonFocus();
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

    // --------------------------------------------------------------------------------- audio focus

    private static AudioAttributes musicAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
    }

    /** UI thread. If the focus service itself fails we simply play: silence would be the worse fault. */
    private void requestFocus() {
        try {
            if (audio == null) audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return;
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(musicAttributes())
                        .setOnAudioFocusChangeListener(this::onFocusChange)
                        .setAcceptsDelayedFocusGain(true)      // asked during a call: GAIN arrives when it ends
                        .build();
            }
            int r = audio.requestAudioFocus(focusRequest);
            boolean granted = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            focusHeld = granted;
            musicHeld = !granted;
            sfxHeld = r == AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
            ducked = false;
        } catch (Throwable t) {
            Log.w(TAG, "audio focus unavailable; playing without it", t);
            musicHeld = false;
            sfxHeld = false;
            ducked = false;
        }
    }

    private void abandonFocus() {
        try {
            if (audio != null && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        } catch (Throwable t) {
            Log.w(TAG, "abandonAudioFocusRequest", t);
        }
        focusHeld = false;
        musicHeld = false;
        sfxHeld = false;
        ducked = false;
    }

    private void onFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                focusHeld = true;
                musicHeld = false;
                sfxHeld = false;
                ducked = false;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                ducked = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:       // a phone call, an alarm: GAIN follows
                musicHeld = true;
                sfxHeld = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS:                 // another player took over for good:
                focusHeld = false;                             // stay quiet until the app is resumed or
                musicHeld = true;                              // the music is switched on again
                sfxHeld = false;
                ducked = false;
                break;
            default:
                break;
        }
        wake();
    }

    // ------------------------------------------------------------------------------------- effects

    private void play(int id) {
        SoundPool p = pool;
        if (p == null || paused || sfxHeld || id < 0 || id >= Sfx.COUNT) return;
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
            File dir = audioDir();
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
     * and notices pause, release, focus and track changes within a few milliseconds.
     *
     * <p>Why a streamed AudioTrack: the loop is sample-accurate (we wrap the index ourselves, no decoder
     * gap as with MediaPlayer looping), there is no size limit (SoundPool refuses samples over about
     * 1 MB decoded, and a static AudioTrack must allocate the whole track in shared memory up front),
     * and a change of track is a plain mix: the incoming loop fades in while the outgoing one fades out.
     * The outgoing loop keeps playing until the incoming one is rendered, so a change never leaves a hole.
     */
    private void musicLoop() {
        AudioTrack track = null;
        boolean playing = false;
        short[] a = null, b = null;            // a: the current loop, b: the outgoing one during a crossfade
        int aId = -1, aPos = 0, bPos = 0, pendingOff = 0, pendingLen = 0, failures = 0;
        float aGain = 0f, bGain = 0f, duck = 1f;
        final float step = 1f / (MUSIC_FADE_S * SAMPLE_RATE);
        short[] chunk = new short[CHUNK];
        try {
            while (!released && !musicBroken) {
                if (paused || musicHeld) {
                    if (playing) {
                        track.pause();
                        playing = false;
                    }
                    sleep(100);
                    continue;
                }
                int want = musicWanted;
                if (want >= Music.TRACKS) want = -1;
                // One crossfade at a time; a track that is still rendering waits while the old one plays on.
                if (want != aId && b == null) {
                    short[] next = want < 0 ? null : ready(want);
                    if (want >= 0 && next == null && trackFailed(want)) want = -1;
                    if (want < 0 || next != null) {
                        b = a;
                        bPos = aPos;
                        bGain = aGain;
                        a = next;
                        aId = want;
                        aPos = 0;
                        aGain = 0f;
                    }
                }
                if (a == null && b == null && pendingLen == 0) {
                    if (playing) {
                        track.stop();          // streaming mode: what is already written plays out
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
                    float duckTarget = ducked ? MUSIC_DUCK : 1f;
                    for (int i = 0; i < CHUNK; i++) {
                        float s = 0f;
                        if (a != null) {
                            if (aGain < 1f) aGain = Math.min(1f, aGain + step);
                            s = a[aPos] * aGain;
                            if (++aPos >= a.length) aPos = 0;
                        }
                        if (b != null) {
                            bGain -= step;
                            if (bGain <= 0f) {
                                b = null;
                            } else {
                                s += b[bPos] * bGain;
                                if (++bPos >= b.length) bPos = 0;
                            }
                        }
                        if (duck < duckTarget) duck = Math.min(duckTarget, duck + step);
                        else if (duck > duckTarget) duck = Math.max(duckTarget, duck - step);
                        s *= MUSIC_VOLUME * duck;
                        chunk[i] = (short) (s > 32767f ? 32767 : s < -32767f ? -32767 : (int) s);
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
                    pendingLen = 0;
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

    /**
     * The PCM of a track if it is in memory, else null; the first request starts a low-priority thread
     * that renders it (or reads it from the cache) once. Rendering takes a second or more on a phone,
     * so it must never happen on the UI thread or on the thread that feeds the AudioTrack.
     */
    private short[] ready(final int id) {
        synchronized (lock) {
            if (trackState[id] == 0) {
                trackState[id] = 1;
                try {
                    Thread t = new Thread(() -> {
                        short[] pcm;
                        synchronized (renderLock) {        // one render at a time: less heat, less memory
                            pcm = released ? null : loadMusic(id);
                        }
                        synchronized (lock) {
                            tracks[id] = pcm;
                            trackState[id] = pcm != null ? 2 : 3;
                            lock.notifyAll();
                        }
                    }, "ima-music-render");
                    t.setDaemon(true);
                    t.setPriority(Thread.MIN_PRIORITY);
                    t.start();
                } catch (Throwable t) {
                    Log.w(TAG, "music render thread did not start", t);
                    trackState[id] = 3;
                }
            }
            return tracks[id];
        }
    }

    private boolean trackFailed(int id) {
        synchronized (lock) {
            return trackState[id] == 3;
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
        File dir = audioDir();
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

    /** The checked cache folder (or null). Whichever loader thread comes first does the check; the other waits. */
    private File audioDir() {
        synchronized (cacheLock) {
            if (!cacheChecked) {
                cacheDir = prepareCacheDir();
                cacheChecked = true;
            }
            return cacheDir;
        }
    }

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
