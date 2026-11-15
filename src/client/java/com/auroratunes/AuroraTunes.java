package com.auroratunes;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class AuroraTunes implements ClientModInitializer {
    public static KeyMapping OPEN;

    @Override
    public void onInitializeClient() {
        Songs.installBuiltIns();
        OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.auroratunes.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_B,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("auroratunes", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.consumeClick()) {
                boolean shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                        || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
                if (mc.player != null && shift) {
                    mc.gui.setScreen(new MusicScreen());
                }
            }
        });

        // Song title floats big in the sky when you look up, shimmering purple/teal
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("auroratunes", "now_playing"),
                (g, tick) -> {
                    String name = Player.nowPlaying();
                    Minecraft mc = Minecraft.getInstance();
                    if (name == null || mc.player == null) return;
                    float pitch = mc.player.getXRot();
                    float look = Math.min(1f, Math.max(0f, (-pitch - 10f) / 30f));
                    double t = System.currentTimeMillis() / 1000.0;
                    float mix = (float) (0.5 + 0.5 * Math.sin(t * 1.5));
                    int r = (int) (199 + (100 - 199) * mix);
                    int gr = (int) (125 + (255 - 125) * mix);
                    int b = (int) (255 + (220 - 255) * mix);
                    int alpha = (int) (255 * Math.max(look, 0.35f));
                    int color = (alpha << 24) | (r << 16) | (gr << 8) | b;
                    String text = "\u266B " + name + " \u266B";
                    float scale = 1f + 2.5f * look;
                    float cx = g.guiWidth() / 2f;
                    float cy = 12f + look * (g.guiHeight() * 0.12f);
                    int w = mc.font.width(text);
                    g.pose().pushMatrix();
                    g.pose().translate(cx, cy);
                    g.pose().scale(scale, scale);
                    g.text(mc.font, text, -w / 2, 0, color, true);
                    g.pose().popMatrix();
                });
    }
}
