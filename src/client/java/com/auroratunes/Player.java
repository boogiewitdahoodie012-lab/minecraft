package com.auroratunes;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import javax.sound.sampled.*;
import java.nio.*;
import java.nio.file.*;

public class Player {
    private static volatile Thread thread;
    private static volatile boolean stop;
    private static volatile String current;

    public static String nowPlaying() { return current; }

    public static synchronized void play(Path file) {
        stop();
        stop = false;
        String title = Songs.prettyName(file);
        thread = new Thread(() -> {
            ByteBuffer raw = null;
            try {
                byte[] bytes = Files.readAllBytes(file);
                raw = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
                try (MemoryStack st = MemoryStack.stackPush()) {
                    IntBuffer ch = st.mallocInt(1), rate = st.mallocInt(1);
                    ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(raw, ch, rate);
                    if (pcm == null) return;
                    AudioFormat fmt = new AudioFormat(rate.get(0), 16, ch.get(0), true, false);
                    SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                    line.open(fmt);
                    line.start();
                    current = title;
                    byte[] chunk = new byte[8192];
                    ByteBuffer bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
                    while (pcm.hasRemaining() && !stop) {
                        bb.clear();
                        while (bb.remaining() >= 2 && pcm.hasRemaining()) bb.putShort(pcm.get());
                        line.write(chunk, 0, bb.position());
                    }
                    if (!stop) line.drain();
                    line.close();
                    org.lwjgl.system.libc.LibCStdlib.free(pcm);
                }
            } catch (Exception ignored) {
            } finally {
                if (raw != null) MemoryUtil.memFree(raw);
                if (thread == Thread.currentThread()) current = null;
            }
        }, "AuroraTunes-Player");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        stop = true;
        Thread t = thread;
        if (t != null) { try { t.join(500); } catch (InterruptedException ignored) {} }
        current = null;
    }
}
