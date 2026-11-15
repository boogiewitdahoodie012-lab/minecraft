package com.auroratunes;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class MusicScreen extends Screen {
    private Button addButton;

    public MusicScreen() { super(Component.literal("Aurora Tunes")); }

    @Override
    protected void init() {
        int w = 220, x = (width - w) / 2, y = 30;
        List<Path> songs = Songs.list();
        int shown = Math.min(songs.size(), 9);
        for (int i = 0; i < shown; i++) {
            Path p = songs.get(i);
            addRenderableWidget(Button.builder(Component.literal("\u25B6 " + Songs.prettyName(p)), b -> Player.play(p))
                    .bounds(x, y + i * 22, w, 20).build());
        }
        int by = y + shown * 22 + 8;
        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> Player.stop()).bounds(x, by, 105, 20).build());
        addButton = addRenderableWidget(Button.builder(Component.literal("+ Add song"), b -> addSong()).bounds(x + 115, by, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Open songs folder"), b -> openFolder()).bounds(x, by + 22, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh list"), b -> this.minecraft.gui.setScreen(new MusicScreen())).bounds(x + 115, by + 22, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, by + 46, w, 20).build());
    }

    private void say(String s) {
        this.minecraft.execute(() -> { if (addButton != null) addButton.setMessage(Component.literal(s)); });
    }

    private void openFolder() {
        try {
            Desktop.getDesktop().open(Songs.dir().toFile());
            say("Folder opened");
        } catch (Throwable t) {
            say("Folder: " + Songs.dir());
        }
    }

    private void addSong() {
        if (GraphicsEnvironment.isHeadless()) {
            openFolder();
            say("Drop songs in folder");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                FileDialog fd = new FileDialog((Frame) null, "Add song (mp3, ogg, wav)", FileDialog.LOAD);
                fd.setVisible(true);
                if (fd.getFile() != null) {
                    String err = Songs.importFile(new File(fd.getDirectory(), fd.getFile()).toPath());
                    if (err == null) this.minecraft.execute(() -> this.minecraft.gui.setScreen(new MusicScreen()));
                    else say(err);
                }
            } catch (Throwable ex) {
                say("Picker failed: use Open folder");
            }
        }, "AuroraTunes-Picker");
        t.setDaemon(true);
        t.start();
    }
}
