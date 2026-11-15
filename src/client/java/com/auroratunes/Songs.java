package com.auroratunes;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Songs {
    static final String[] BUILT_IN = {
        "linkin_park_in_the_end.ogg", "katy_perry_last_friday_night.ogg",
        "coldplay_viva_la_vida.ogg", "miley_cyrus_party_in_the_usa.ogg" };

    public static Path dir() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("auroratunes");
        try { Files.createDirectories(p); } catch (IOException ignored) {}
        return p;
    }

    /** Copies bundled songs into config/auroratunes on first run. */
    public static void installBuiltIns() {
        for (String f : BUILT_IN) {
            Path out = dir().resolve(f);
            if (Files.exists(out)) continue;
            try (InputStream in = Songs.class.getResourceAsStream("/assets/auroratunes/songs/" + f)) {
                if (in != null) Files.copy(in, out);
            } catch (IOException ignored) {}
        }
    }

    public static List<Path> list() {
        try (var s = Files.list(dir())) {
            return s.filter(p -> p.toString().endsWith(".ogg")).sorted().toList();
        } catch (IOException e) { return List.of(); }
    }

    public static String prettyName(Path p) {
        String n = p.getFileName().toString().replaceAll("\\.ogg$", "").replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : n.split(" ")) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    /** Imports ogg directly; converts mp3/wav/m4a/flac via ffmpeg if it is installed. */
    public static String importFile(Path src) {
        String name = src.getFileName().toString();
        String base = name.replaceAll("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9_-]", "_");
        Path out = dir().resolve(base + ".ogg");
        try {
            if (name.toLowerCase().endsWith(".ogg")) { Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING); return null; }
            Process p = new ProcessBuilder("ffmpeg", "-y", "-i", src.toString(), "-vn", "-c:a", "libvorbis", "-q:a", "5", out.toString())
                    .redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            return p.waitFor() == 0 ? null : "ffmpeg failed";
        } catch (IOException e) {
            return "Needs .ogg, or install ffmpeg to import other formats";
        } catch (InterruptedException e) { return "Interrupted"; }
    }
}
