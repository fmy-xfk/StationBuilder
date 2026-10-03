package cn.myfrank.stationbuilder.utils;

import net.minecraft.client.gui.DrawContext;
import cn.myfrank.stationbuilder.gui.GuiScreen;
import net.minecraft.client.gui.screen.Screen;

/**
 * Client-only version compatibility helpers.
 */
public final class ClientUtil {
    private ClientUtil() {}

    public static void renderBackground(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        screen.renderBackground(context, mouseX, mouseY, delta);
    }
}
