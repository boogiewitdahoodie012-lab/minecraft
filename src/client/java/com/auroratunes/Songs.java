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

    public static boolean supported(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".ogg") || n.endsWith(".mp3") || n.endsWith(".wav");
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
            return s.filter(Songs::supported).sorted().toList();
        } catch (IOException e) { return List.of(); }
    }

    public static String prettyName(Path p) {
        String n = p.getFileName().toString().replaceAll("\\.[A-Za-z0-9]+$", "").replace('_', ' ').replace('-', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : n.split(" ")) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    /** Copies an ogg/mp3/wav into the songs folder. Returns null on success, else an error message. */
    public static String importFile(Path src) {
        if (!supported(src)) return "Use ogg, mp3 or wav";
        try {
            Files.copy(src, dir().resolve(src.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            return null;
        } catch (IOException e) {
            return "Could not copy file";
        }
    }
}
