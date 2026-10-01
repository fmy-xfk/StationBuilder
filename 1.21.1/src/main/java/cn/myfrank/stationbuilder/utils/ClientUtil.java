package cn.myfrank.stationbuilder.utils;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Box;

public final class ClientUtil {
    private ClientUtil() {}

    public static void drawBox(MatrixStack matrices, VertexConsumer consumer, Box box, float r, float g, float b, float a) {
        WorldRenderer.drawBox(matrices, consumer, box, r, g, b, a);
    }
}
