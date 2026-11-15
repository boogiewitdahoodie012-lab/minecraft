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

        // Song title is pinned to fixed spots in the sky (4 around you), drifting slowly
        // and shimmering green/purple like the aurora. It moves with the world as you turn.
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("auroratunes", "now_playing"),
                (g, tick) -> {
                    String name = Player.nowPlaying();
                    Minecraft mc = Minecraft.getInstance();
                    if (name == null || mc.player == null) return;

                    double t = System.currentTimeMillis() / 1000.0;
                    String text = "\u266B " + name + " \u266B";
                    int tw = mc.font.width(text);
                    int sw = g.guiWidth(), sh = g.guiHeight();
                    double focal = (sh / 2.0) / Math.tan(Math.toRadians(35)); // ~70 degree vertical FOV

                    float playerYaw = mc.player.getYRot();
                    float playerPitch = mc.player.getXRot();

                    for (int i = 0; i < 4; i++) {
                        double yaw = i * 90.0 + t * 4.0;                         // slow drift around the sky
                        double pitch = -48.0 + 6.0 * Math.sin(t * 0.7 + i);       // gentle floating bob
                        double dyaw = wrap(yaw - playerYaw);
                        double dpitch = pitch - playerPitch;
                        if (Math.abs(dyaw) > 80 || Math.abs(dpitch) > 80) continue;

                        float x = (float) (sw / 2.0 + Math.tan(Math.toRadians(dyaw)) * focal);
                        float y = (float) (sh / 2.0 + Math.tan(Math.toRadians(dpitch)) * focal);

                        // aurora shimmer: green <-> purple, offset per copy
                        float mix = (float) (0.5 + 0.5 * Math.sin(t * 1.4 + i * 1.7));
                        int r = (int) (90 + (200 - 90) * mix);
                        int gr = (int) (255 + (100 - 255) * mix);
                        int b = (int) (190 + (255 - 190) * mix);
                        double edge = 1.0 - Math.min(1.0, Math.abs(dyaw) / 80.0);
                        int alpha = (int) (80 + 175 * Math.min(1.0, edge * 1.6));
                        int color = (alpha << 24) | (r << 16) | (gr << 8) | b;

                        float scale = 2.6f;
                        g.pose().pushMatrix();
                        g.pose().translate(x, y);
                        g.pose().scale(scale, scale);
                        g.text(mc.font, text, -tw / 2, 0, color, true);
                        g.pose().popMatrix();
                    }
                });
    }

    private static double wrap(double deg) {
        deg %= 360.0;
        if (deg >= 180.0) deg -= 360.0;
        if (deg < -180.0) deg += 360.0;
        return deg;
    }
}
