package com.auroratunes;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.*;
import java.nio.file.*;
import java.util.Locale;

public class Player {
    private static final class Run {
        volatile boolean stop;
        volatile SourceDataLine line;
        Thread thread;
    }

    private static volatile Run current;
    private static volatile String title;
    /** Where in the sky the song title is pinned (set from your view when a song starts). */
    public static volatile float anchorYaw = 0f;

    public static String nowPlaying() { return title; }

    public static synchronized void play(Path file) {
        stop();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) anchorYaw = mc.player.getYRot();
        final Run run = new Run();
        current = run;
        final String name = Songs.prettyName(file);
        run.thread = new Thread(() -> decodeAndPlay(file, name, run), "AuroraTunes-Player");
        run.thread.setDaemon(true);
        run.thread.start();
    }

    public static synchronized void stop() {
        Run r = current;
        current = null;
        title = null;
        if (r == null) return;
        r.stop = true;
        SourceDataLine l = r.line;
        if (l != null) { try { l.stop(); l.flush(); } catch (Exception ignored) {} }
        try { r.thread.join(3000); } catch (InterruptedException ignored) {}
    }

    private static SourceDataLine open(Run run, int rate, int channels, String name) throws Exception {
        AudioFormat fmt = new AudioFormat(rate, 16, channels, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
        line.open(fmt);
        run.line = line;
        line.start();
        if (current == run) title = name;
        return line;
    }

    private static void decodeAndPlay(Path file, String name, Run run) {
        String fn = file.getFileName().toString().toLowerCase(Locale.ROOT);
        SourceDataLine line = null;
        try {
            if (fn.endsWith(".ogg")) line = playOgg(file, name, run);
            else if (fn.endsWith(".mp3")) line = playMp3(file, name, run);
            else if (fn.endsWith(".wav")) line = playWav(file, name, run);
        } catch (Throwable ignored) {
        } finally {
            SourceDataLine l = run.line;
            if (l != null) { try { l.stop(); l.close(); } catch (Throwable ignored) {} }
            if (current == run) { current = null; title = null; }
        }
    }

    private static SourceDataLine playMp3(Path file, String name, Run run) throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            Bitstream bs = new Bitstream(in);
            Decoder dec = new Decoder();
            SourceDataLine line = null;
            byte[] out = new byte[0];
            Header h;
            while (!run.stop && (h = bs.readFrame()) != null) {
                SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                if (line == null) line = open(run, sb.getSampleFrequency(), sb.getChannelCount(), name);
                short[] buf = sb.getBuffer();
                int len = sb.getBufferLength();
                if (out.length < len * 2) out = new byte[len * 2];
                for (int i = 0; i < len; i++) {
                    out[i * 2] = (byte) (buf[i] & 0xFF);
                    out[i * 2 + 1] = (byte) ((buf[i] >> 8) & 0xFF);
                }
                line.write(out, 0, len * 2);
                bs.closeFrame();
            }
            if (line != null && !run.stop) line.drain();
            bs.close();
            return line;
        }
    }

    private static SourceDataLine playWav(Path file, String name, Run run) throws Exception {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat base = raw.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16,
                    base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
            try (AudioInputStream in = AudioSystem.getAudioInputStream(pcm, raw)) {
                SourceDataLine line = open(run, (int) pcm.getSampleRate(), pcm.getChannels(), name);
                byte[] buf = new byte[8192];
                int n;
                while (!run.stop && (n = in.read(buf)) > 0) line.write(buf, 0, n);
                if (!run.stop) line.drain();
                return line;
            }
        }
    }

    private static SourceDataLine playOgg(Path file, String name, Run run) throws Exception {
        ByteBuffer raw = null;
        ShortBuffer pcm = null;
        long vorbis = 0;
        try {
            byte[] bytes = Files.readAllBytes(file);
            raw = MemoryUtil.memAlloc(bytes.length);
            raw.put(bytes).flip();

            int channels, rate;
            try (MemoryStack st = MemoryStack.stackPush()) {
                IntBuffer err = st.mallocInt(1);
                vorbis = STBVorbis.stb_vorbis_open_memory(raw, err, null);
                if (vorbis == 0L) return null;
                STBVorbisInfo info = STBVorbisInfo.malloc(st);
                STBVorbis.stb_vorbis_get_info(vorbis, info);
                channels = info.channels();
                rate = info.sample_rate();
            }
            SourceDataLine line = open(run, rate, channels, name);
            pcm = MemoryUtil.memAllocShort(4096 * channels);
            byte[] out = new byte[4096 * channels * 2];
            while (!run.stop) {
                pcm.clear();
                int n = STBVorbis.stb_vorbis_get_samples_short_interleaved(vorbis, channels, pcm);
                if (n <= 0) break;
                int count = n * channels;
                for (int i = 0; i < count; i++) {
                    short s = pcm.get(i);
                    out[i * 2] = (byte) (s & 0xFF);
                    out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
                }
                line.write(out, 0, count * 2);
            }
            if (!run.stop) line.drain();
            return line;
        } finally {
            if (vorbis != 0L) STBVorbis.stb_vorbis_close(vorbis);
            if (pcm != null) MemoryUtil.memFree(pcm);
            if (raw != null) MemoryUtil.memFree(raw);
        }
    }
}
