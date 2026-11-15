package com.auroratunes;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class MusicScreen extends Screen {
    public MusicScreen() { super(Component.literal("Aurora Tunes")); }

    @Override
    protected void init() {
        int w = 220, x = (width - w) / 2, y = 30;
        List<Path> songs = Songs.list();
        for (int i = 0; i < Math.min(songs.size(), 8); i++) {
            Path p = songs.get(i);
            addRenderableWidget(Button.builder(Component.literal("\u25B6 " + Songs.prettyName(p)), b -> Player.play(p))
                    .bounds(x, y + i * 24, w, 20).build());
        }
        int by = y + Math.min(songs.size(), 8) * 24 + 8;
        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> Player.stop()).bounds(x, by, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+ Add song"), b -> addSong()).bounds(x + 115, by, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, by + 24, w, 20).build());
    }

    private void addSong() {
        // Runs the file picker off the game thread, then re-opens this screen to refresh the list
        Thread t = new Thread(() -> {
            try {
                FileDialog fd = new FileDialog((Frame) null, "Add song (ogg, mp3, wav...)", FileDialog.LOAD);
                fd.setVisible(true);
                if (fd.getFile() != null) {
                    File f = new File(fd.getDirectory(), fd.getFile());
                    Songs.importFile(f.toPath());
                    this.minecraft.execute(() -> this.minecraft.gui.setScreen(new MusicScreen()));
                }
            } catch (Throwable ignored) {}
        }, "AuroraTunes-Picker");
        t.setDaemon(true);
        t.start();
    }
}
