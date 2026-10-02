package cn.myfrank.stationbuilder.utils;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Client-only compatibility helpers for Minecraft 1.20.4. */
public final class ClientUtil {
    private ClientUtil() {
    }

    public static void renderBackground(Screen screen, GuiGraphics graphics,
                                        int mouseX, int mouseY, float partialTick) {
        screen.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
}
