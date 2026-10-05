package cn.myfrank.stationbuilder.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.AABB;

public class ClientUtil {
    public static void renderLineBox(PoseStack poseStack, VertexConsumer consumer, AABB box, float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(poseStack, consumer, box, r, g, b, a);
    }
}
