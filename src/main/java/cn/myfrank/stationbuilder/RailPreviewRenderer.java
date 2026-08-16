package cn.myfrank.stationbuilder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = StationBuilder.MOD_ID, value = Dist.CLIENT)
public final class RailPreviewRenderer {

    private static final RailPreviewCache previewCache = new RailPreviewCache();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        ItemStack stack = client.player.getMainHandItem();
        if (!(stack.getItem() instanceof RailBuilderItem)) return;

        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) return;

        var state = client.level.getBlockState(bhr.getBlockPos());
        var pos = bhr.getBlockPos();
        if (!StationBuilder.isSoftTransparent(state)) pos = pos.relative(bhr.getDirection());

        renderRailPreview(event, client.player, pos, stack);
    }

    private static void renderRailPreview(
            RenderLevelStageEvent event,
            Player player,
            BlockPos targetPos,
            ItemStack stack
    ) {
        ArrayList<BlockPos> nodes = RailGenerator.calcRailNodes(
                targetPos, player.getYRot(), RailBuilderConfig.fromItem(stack)
        );
        if (nodes == null) return;

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        PoseStack matrices = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
        if (lastPair == null || lastPair.left() == null) {
            for (BlockPos node : nodes) {
                drawBox(matrices, consumer, node, cam, 0f, 1f, 1f, 0.6f); // 青色半透明
            }
        } else {
            var lastNodes = lastPair.left();
            float lastAngle = lastPair.right();

            if (lastNodes.size() != nodes.size()) {
                // 清除本地状态（物品NBT）
                RailBuilderState.clear(stack);
                stack.getOrCreateTag().remove("CustomModelData");
                // 通知服务端清除状态
                PlatformServices.sendToServer(StationBuilder.PACKET_CLEAR_RAIL, StationBuilder.buf(b -> {}));
                return;
            }

            RailMath.adjustPointSequence(lastNodes, nodes);
            float angle = player.getYRot();
            Vec3 textPos = getPreviewCenterPos(lastNodes, targetPos);
            Vec3i d = getDelta(lastNodes, targetPos);
            double minRadius = 1e9, minLength = 1e9;
            int successCount = 0;

            for (int i = 0; i < lastNodes.size(); ++i) {
                var node = nodes.get(i);
                var lastNode = lastNodes.get(i);
                drawBox(matrices, consumer, lastNode, cam, 0f, 1f, 1f, 0.6f); // 青色半透明
                drawBox(matrices, consumer, node, cam, 0f, 1f, 1f, 0.6f); // 青色半透明
                if (StationBuilder.isMtrLoaded()) {
                    var preview = previewCache.get(lastNode, MTRIntegration.parseAngle(lastAngle),
                            node, MTRIntegration.parseAngle(angle));
                    if (preview == null) {
                        preview = MTRIntegration.testConnectRailNodes(lastAngle, angle, lastNode, node);
                        previewCache.put(lastNode, MTRIntegration.parseAngle(lastAngle),
                                node, MTRIntegration.parseAngle(angle), preview);
                    }
                    if (preview.success()) {
                        successCount += 1;
                        renderCurve(matrices, bufferSource, event.getCamera(), preview.positions());
                        if (preview.radius() > 0) {
                            minRadius = Math.min(minRadius, preview.radius());
                            minLength = Math.min(minLength, preview.length());
                        }
                    }
                }
            }
            String extras = "";
            int extra_color = 0xAAAAAA;
            if (successCount < lastNodes.size()) {
                extras = Component.translatable("message.stationbuilder.rail_builder.fail", lastNodes.size() - successCount).getString();
                if (successCount > 0) {
                    extras += ", ";
                }
                extra_color = 0xFF5555;
            }
            if (successCount > 0) {
                if (minRadius < 1e9) {
                    extras += Component.translatable("message.stationbuilder.rail_builder.min_radius", String.format(Locale.ROOT, "%.2f", minRadius), String.format(Locale.ROOT, "%.2f", minLength)).getString();
                } else {
                    extras += Component.translatable("message.stationbuilder.rail_builder.straight_line").getString();
                }
            }
            renderDeltaText3D(event, textPos, d, extras, extra_color);
        }
    }

    private static void renderCurve(
            PoseStack matrices,
            MultiBufferSource consumers,
            Camera camera,
            List<Vec3> points
    ) {
        if (points.size() < 2) return;

        Vec3 camPos = camera.getPosition();
        VertexConsumer vc = consumers.getBuffer(RenderType.lines());

        matrices.pushPose();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f mat = matrices.last().pose();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p0 = points.get(i);
            Vec3 p1 = points.get(i + 1);

            vc.vertex(mat, (float) p0.x, (float) p0.y, (float) p0.z)
                    .color(255, 0, 0, 255)
                    .normal(1, 0, 0)
                    .endVertex();

            vc.vertex(mat, (float) p1.x, (float) p1.y, (float) p1.z)
                    .color(255, 0, 0, 255)
                    .normal(1, 0, 0)
                    .endVertex();
        }

        matrices.popPose();
    }

    private static void drawBox(
            PoseStack matrices,
            VertexConsumer consumer,
            BlockPos pos,
            Vec3 cam,
            float r, float g, float b, float a
    ) {
        AABB box = new AABB(pos).move(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(matrices, consumer, box, r, g, b, a);
    }

    private static Vec3 getPreviewCenterPos(
            List<BlockPos> lastNodes,
            BlockPos anchor
    ) {
        Vec3 lastCenter = Vec3.ZERO;
        for (BlockPos p : lastNodes) {
            lastCenter = lastCenter.add(new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
        }
        lastCenter = lastCenter.scale(1.0 / lastNodes.size());

        Vec3 anchorCenter = new Vec3(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
        Vec3 delta = anchorCenter.subtract(lastCenter);

        return lastCenter.add(delta).add(0, 0.5, 0); // 文本向上抬高一点
    }

    private static Vec3i getDelta(List<BlockPos> lastNodes, BlockPos anchor) {
        double sumX = 0, sumY = 0, sumZ = 0;
        for (BlockPos p : lastNodes) {
            sumX += p.getX();
            sumY += p.getY();
            sumZ += p.getZ();
        }
        int lastX = (int) Math.round(sumX / lastNodes.size());
        int lastY = (int) Math.round(sumY / lastNodes.size());
        int lastZ = (int) Math.round(sumZ / lastNodes.size());
        return new Vec3i(anchor.getX() - lastX, anchor.getY() - lastY, anchor.getZ() - lastZ);
    }

    private static void renderDeltaText3D(
            RenderLevelStageEvent event,
            Vec3 worldPos,
            Vec3i d,
            String extras,
            int extra_color
    ) {
        Minecraft client = Minecraft.getInstance();
        Camera camera = event.getCamera();
        PoseStack matrices = event.getPoseStack();

        Font font = client.font;

        String text;
        int color;

        if (d.getY() > 0) {
            text = d.getX() + ", ↑ +" + d.getY() + ", " + d.getZ();
            color = 0x00FF00;
        } else if (d.getY() < 0) {
            text = d.getX() + ", ↓ " + d.getY() + ", " + d.getZ();
            color = 0xFF5555;
        } else {
            text = d.getX() + ", = 0, " + d.getZ();
            color = 0xAAAAAA;
        }

        matrices.pushPose();

        // 局部相机空间相对偏移变换
        Vec3 camPos = camera.getPosition();
        matrices.translate(
                worldPos.x - camPos.x,
                worldPos.y - camPos.y,
                worldPos.z - camPos.z
        );

        // billboard：令其始终正面垂直朝向相机
        matrices.mulPose(camera.rotation());

        // 缩放世界文字比例
        float scale = 0.025F;
        matrices.scale(-scale, -scale, scale);

        float x = -font.width(text) / 2f;
        float y = 0;

        RenderSystem.disableDepthTest();

        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();

        font.drawInBatch(
                text,
                x,
                y,
                color,
                false,
                matrices.last().pose(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                0xF000F0
        );

        float x2 = -font.width(extras) / 2f;
        font.drawInBatch(
                extras,
                x2,
                y + font.lineHeight + 3,
                extra_color,
                false,
                matrices.last().pose(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                0xF000F0
        );

        RenderSystem.enableDepthTest();

        matrices.popPose();
    }
}