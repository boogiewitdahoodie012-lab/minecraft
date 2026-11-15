package com.auroratunes;

import net.minecraft.client.Minecraft;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import javax.sound.sampled.*;
import java.nio.*;
import java.nio.file.*;

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

    private static void decodeAndPlay(Path file, String name, Run run) {
        ByteBuffer raw = null;
        ShortBuffer pcm = null;
        long vorbis = 0;
        SourceDataLine line = null;
        try {
            byte[] bytes = Files.readAllBytes(file);
            raw = MemoryUtil.memAlloc(bytes.length);
            raw.put(bytes).flip();

            int channels, rate;
            try (MemoryStack st = MemoryStack.stackPush()) {
                IntBuffer err = st.mallocInt(1);
                vorbis = STBVorbis.stb_vorbis_open_memory(raw, err, null);
                if (vorbis == 0L) return;
                STBVorbisInfo info = STBVorbisInfo.malloc(st);
                STBVorbis.stb_vorbis_get_info(vorbis, info);
                channels = info.channels();
                rate = info.sample_rate();
            }

            AudioFormat fmt = new AudioFormat(rate, 16, channels, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt);
            run.line = line;
            if (run.stop) return;
            line.start();
            if (current == run) title = name;

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
        } catch (Throwable ignored) {
        } finally {
            if (line != null) { try { line.stop(); line.close(); } catch (Throwable ignored) {} }
            if (vorbis != 0L) STBVorbis.stb_vorbis_close(vorbis);
            if (pcm != null) MemoryUtil.memFree(pcm);
            if (raw != null) MemoryUtil.memFree(raw);
            if (current == run) { current = null; title = null; }
        }
    }
}
