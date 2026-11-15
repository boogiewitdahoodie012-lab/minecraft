package com.auroratunes;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.*;
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
        try (MemoryStack st = MemoryStack.stackPush()) {
            PointerBuffer filters = st.mallocPointer(5);
            for (String f : new String[]{"*.ogg", "*.mp3", "*.wav", "*.flac", "*.m4a"}) filters.put(st.UTF8(f));
            filters.flip();
            String path = TinyFileDialogs.tinyfd_openFileDialog("Add song", "", filters, "Audio files", false);
            if (path != null) {
                Songs.importFile(Paths.get(path));
                rebuildWidgets();
            }
        }
    }
}
