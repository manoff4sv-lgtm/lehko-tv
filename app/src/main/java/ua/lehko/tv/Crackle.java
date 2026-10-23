package ua.lehko.tv;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;

/**
 * A quiet crackling fire, made on the fly (no sound files): soft brown noise for the rumble of the flames and short
 * random pops, sometimes in little bursts. One small thread streams it into an AudioTrack. It gives way to music:
 * music started (or resumed with Play) while the fire burns takes the audio focus, and the crackle stops.
 */
final class Crackle {
    static final int RATE = 22050;

    private final AudioManager audio;
    private volatile boolean running;
    private Thread thread;

    Crackle(Context c) {
        audio = (AudioManager) c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            @Override public void run() {
                play();
            }
        }, "lehko-crackle");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops within a few milliseconds; the thread releases the AudioTrack itself. */
    void stop() {
        running = false;
    }

    private void play() {
        AudioTrack track = null;
        AudioFocusRequest focus = null;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            // "May duck": nothing else has to pause for it.
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                        @Override public void onAudioFocusChange(int change) {
                            // Something else plays (music, a video, the assistant): the crackle ends.
                            if (change < 0) running = false;
                        }
                    }, App.MAIN)
                    .build();
            if (audio == null || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(min, RATE / 5 * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
            Sound sound = new Sound(System.nanoTime());
            short[] buf = new short[RATE / 20];
            while (running) {
                sound.fill(buf);
                int off = 0;
                // Never block on the track, so stop() always gets through.
                while (running && off < buf.length) {
                    int n = track.write(buf, off, buf.length - off, AudioTrack.WRITE_NON_BLOCKING);
                    if (n < 0) return;
                    off += n;
                    if (off < buf.length) Thread.sleep(15);
                }
            }
        } catch (InterruptedException | RuntimeException e) {
            // No sound then: the fire still burns.
        } finally {
            running = false;
            if (track != null) {
                try {
                    track.pause();
                    track.flush();
                } catch (RuntimeException ignored) {
                    // Already stopped.
                }
                track.release();
            }
            if (focus != null && audio != null) audio.abandonAudioFocusRequest(focus);
        }
    }

    /** The sound itself, sample by sample: plain Java, so it can be tested. */
    static final class Sound {
        /** Overall loudness: quiet, it should sit under a conversation. */
        static final double VOLUME = 0.16;
        private int seed;
        private double brown, low, popAmp, popDecay;
        private int popLeft, burst;

        Sound(long seed) {
            this.seed = (int) (seed ^ (seed >>> 32)) | 1;
        }

        private double white() {
            int s = seed;
            s ^= s << 13;
            s ^= s >>> 17;
            s ^= s << 5;
            seed = s;
            return s / 2147483648.0;
        }

        void fill(short[] out) {
            for (int i = 0; i < out.length; i++) {
                double w = white();
                // Brown noise: a leaky sum of white noise, a soft low rumble.
                brown = (brown + 0.02 * w) / 1.02;
                double s = brown * 1.6;
                if (popLeft == 0) {
                    // About six pops a second, a third of them followed by a quick second one.
                    double chance = burst > 0 ? 1.0 / 600 : 6.0 / RATE;
                    if ((white() + 1) / 2 < chance) {
                        popLeft = 30 + (int) ((white() + 1) * 120);
                        popAmp = 0.35 + (white() + 1) * 0.3;
                        popDecay = Math.exp(-4.0 / popLeft);
                        burst = burst > 0 ? burst - 1 : (white() > 0.33 ? 2 : 0);
                    }
                } else {
                    // A pop: a short, slightly muffled burst of noise that dies away.
                    low += 0.55 * (w - low);
                    s += popAmp * low;
                    popAmp *= popDecay;
                    popLeft--;
                }
                double v = Math.max(-1, Math.min(1, s * VOLUME));
                out[i] = (short) Math.round(v * 32767);
            }
        }
    }
}
