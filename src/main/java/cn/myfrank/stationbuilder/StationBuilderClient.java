package cn.myfrank.stationbuilder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Mod(value = StationBuilder.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = StationBuilder.MOD_ID, value = Dist.CLIENT)
public class StationBuilderClient {
    private static final RailPreviewCache previewCache = new RailPreviewCache();

    public StationBuilderClient(IEventBus modEventBus) {
        modEventBus.addListener(StationBuilderClient::registerKeyMappings);
        modEventBus.addListener(StationBuilderClient::registerPayloads);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(StationBuilderKeyBindings.CLEAR_RAIL_STATE);
        event.register(StationBuilderKeyBindings.UNDO_PLACER);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(StationBuilder.MOD_ID);

        registrar.playToClient(StationBuilder.SyncOpenStationPayload.TYPE, StationBuilder.SyncOpenStationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        Minecraft.getInstance().setScreen(new StationEditorScreen(
                                payload.pos(),
                                Direction.from2DDataValue(payload.facing()),
                                payload.nbt()
                        ))));

        registrar.playToClient(StationBuilder.SyncOpenRailPayload.TYPE, StationBuilder.SyncOpenRailPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        Minecraft.getInstance().setScreen(new RailBuilderScreen(payload.nbt()))));

        registrar.playToClient(StationBuilder.SyncOpenSelectorPayload.TYPE, StationBuilder.SyncOpenSelectorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        Minecraft.getInstance().setScreen(new BuildingSelectorScreen(payload.pos1(), payload.pos2()))));

        registrar.playToClient(StationBuilder.SyncOpenPlacerPayload.TYPE, StationBuilder.SyncOpenPlacerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        Minecraft.getInstance().setScreen(new BuildingPlacerScreen(payload.nbt()))));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (StationBuilderKeyBindings.CLEAR_RAIL_STATE.consumeClick()
                && client.player != null
                && client.player.getMainHandItem().getItem() instanceof RailBuilderItem) {
            PacketDistributor.sendToServer(new StationBuilder.ClearRailStatePayload());
        }

        if (StationBuilderKeyBindings.UNDO_PLACER.consumeClick()
                && client.player != null
                && client.player.getMainHandItem().getItem() instanceof BuildingPlacerItem) {
            PacketDistributor.sendToServer(new StationBuilder.UndoPlacerPayload());
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                && event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        ItemStack stack = client.player.getMainHandItem();
        BlockHitResult bhr = getBlockHit(client);

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            // 1. 选取工具 渲染
            if (stack.getItem() instanceof BuildingSelectorItem) {
                BlockPos p1 = BuildingSelectorItem.getPos1(stack);
                BlockPos p2 = BuildingSelectorItem.getPos2(stack);
                if (p1 != null || p2 != null) {
                    renderSelectionPreview(event, p1, p2);
                }
                return;
            }

            // 2. 放置工具 渲染
            if (stack.getItem() instanceof BuildingPlacerItem) {
                if (bhr != null) {
                    BlockPos pos = bhr.getBlockPos().relative(bhr.getDirection());
                    renderPlacerPreview(event, pos, stack);
                }
                return;
            }

            // 3. RailBuilder 渲染
            if (stack.getItem() instanceof RailBuilderItem) {
                if (bhr == null) return;
                var state = client.level.getBlockState(bhr.getBlockPos());
                var pos = bhr.getBlockPos();
                if (!StationBuilder.isSoftTransparent(state)) pos = pos.relative(bhr.getDirection());
                renderRailPreviewGeometry(event, client.player, pos, stack);
            }
        } else { // AFTER_TRANSLUCENTS
            if (!(stack.getItem() instanceof RailBuilderItem)) return;
            if (bhr == null) return;
            var state = client.level.getBlockState(bhr.getBlockPos());
            var pos = bhr.getBlockPos();
            if (!StationBuilder.isSoftTransparent(state)) pos = pos.relative(bhr.getDirection());
            renderRailPreviewText(event, client.player, pos, stack);
        }
    }

    private static BlockHitResult getBlockHit(Minecraft client) {
        HitResult hit = client.hitResult;
        if (hit instanceof BlockHitResult bhr) {
            return bhr;
        }
        return null;
    }

    private static void renderSelectionPreview(RenderLevelStageEvent event, BlockPos p1, BlockPos p2) {
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();

        if (p1 != null && p2 != null) {
            BlockPos min = new BlockPos(
                    Math.min(p1.getX(), p2.getX()),
                    Math.min(p1.getY(), p2.getY()),
                    Math.min(p1.getZ(), p2.getZ())
            );
            BlockPos max = new BlockPos(
                    Math.max(p1.getX(), p2.getX()),
                    Math.max(p1.getY(), p2.getY()),
                    Math.max(p1.getZ(), p2.getZ())
            );
            AABB box = new AABB(
                    min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1, max.getY() + 1, max.getZ() + 1
            ).move(-cam.x, -cam.y, -cam.z);
            ShapeRenderer.renderLineBox(poseStack, consumer, box, 0f, 1f, 0f, 0.4f); // 绿色高亮
        } else {
            BlockPos setPos = p1 != null ? p1 : p2;
            AABB box = new AABB(setPos).move(-cam.x, -cam.y, -cam.z);
            ShapeRenderer.renderLineBox(poseStack, consumer, box, 0f, 1f, 1f, 0.4f); // 青色高亮单个方块
        }
    }

    private static void renderPlacerPreview(RenderLevelStageEvent event, BlockPos targetPos, ItemStack stack) {
        BuildingPlacerConfig cfg = BuildingPlacerConfig.fromItem(stack);
        var templateOpt = BuildingTemplateManager.getTemplate(cfg.presetName);
        if (templateOpt.isPresent()) {
            StructureTemplate template = templateOpt.get();
            Vec3i rawSize = template.getSize();
            BlockPos sizePos = new BlockPos(rawSize.getX(), rawSize.getY(), rawSize.getZ());

            StructurePlaceSettings placementData = new StructurePlaceSettings()
                    .setRotation(cfg.rotation)
                    .setMirror(net.minecraft.world.level.block.Mirror.NONE);

            BlockPos rotatedSize = StructureTemplate.transform(sizePos, net.minecraft.world.level.block.Mirror.NONE, cfg.rotation, BlockPos.ZERO);

            double minX = targetPos.getX();
            double minY = targetPos.getY();
            double minZ = targetPos.getZ();

            double rotatedSizeX = rotatedSize.getX();
            double rotatedSizeY = rotatedSize.getY();
            double rotatedSizeZ = rotatedSize.getZ();

            double realMinX = Math.min(minX, minX + rotatedSizeX) + (rotatedSizeX < 0 ? 1 : 0);
            double realMaxX = Math.max(minX, minX + rotatedSizeX) + (rotatedSizeX < 0 ? 1 : 0);
            double realMinY = Math.min(minY, minY + rotatedSizeY) + (rotatedSizeY < 0 ? 1 : 0);
            double realMaxY = Math.max(minY, minY + rotatedSizeY) + (rotatedSizeY < 0 ? 1 : 0);
            double realMinZ = Math.min(minZ, minZ + rotatedSizeZ) + (rotatedSizeZ < 0 ? 1 : 0);
            double realMaxZ = Math.max(minZ, minZ + rotatedSizeZ) + (rotatedSizeZ < 0 ? 1 : 0);

            Vec3 cam = event.getCamera().getPosition();
            VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());
            PoseStack poseStack = event.getPoseStack();
            AABB box = new AABB(realMinX, realMinY, realMinZ, realMaxX, realMaxY, realMaxZ).move(-cam.x, -cam.y, -cam.z);
            ShapeRenderer.renderLineBox(poseStack, consumer, box, 1f, 0.5f, 0f, 0.4f);
        }
    }

    private static void renderRailPreviewGeometry(RenderLevelStageEvent event, Player player, BlockPos targetPos, ItemStack stack) {
        ArrayList<BlockPos> nodes = RailGenerator.calcRailNodes(
                targetPos, player.getYRot(), RailBuilderConfig.fromItem(stack)
        );
        if (nodes == null) return;

        VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
        if (lastPair == null || lastPair.left() == null) {
            for (BlockPos node : nodes) {
                drawBox(poseStack, consumer, node, cam, 0f, 1f, 1f, 0.6f);
            }
            return;
        }

        var lastNodes = lastPair.left();
        float lastAngle = lastPair.right();

        if (lastNodes.size() != nodes.size()) {
            return;
        }

        RailMath.adjustPointSequence(lastNodes, nodes);
        float angle = player.getYRot();

        for (int i = 0; i < lastNodes.size(); ++i) {
            var node = nodes.get(i);
            var lastNode = lastNodes.get(i);

            drawBox(poseStack, consumer, lastNode, cam, 0f, 1f, 1f, 0.6f);
            drawBox(poseStack, consumer, node, cam, 0f, 1f, 1f, 0.6f);

            if (StationBuilder.isMtrLoaded()) {
                var preview = previewCache.get(
                        lastNode, MTRIntegration.parseAngle(lastAngle),
                        node, MTRIntegration.parseAngle(angle)
                );
                if (preview == null) {
                    preview = MTRIntegration.testConnectRailNodes(lastAngle, angle, lastNode, node);
                    previewCache.put(
                            lastNode, MTRIntegration.parseAngle(lastAngle),
                            node, MTRIntegration.parseAngle(angle),
                            preview
                    );
                }
                if (preview != null && preview.success()) {
                    renderCurve(poseStack, Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera(), preview.positions());
                }
            }
        }
    }

    private static void renderRailPreviewText(RenderLevelStageEvent event, Player player, BlockPos targetPos, ItemStack stack) {
        ArrayList<BlockPos> nodes = RailGenerator.calcRailNodes(
                targetPos, player.getYRot(), RailBuilderConfig.fromItem(stack)
        );
        if (nodes == null) return;

        var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
        if (lastPair == null || lastPair.left() == null) return;

        var lastNodes = lastPair.left();
        float lastAngle = lastPair.right();

        if (lastNodes.size() != nodes.size()) {
            return;
        }

        RailMath.adjustPointSequence(lastNodes, nodes);
        float angle = player.getYRot();

        Vec3 textPos = getPreviewCenterPos(lastNodes, targetPos);
        Vec3i d = getDelta(lastNodes, targetPos);

        double minRadius = 1e9, minLength = 1e9;
        int successCount = 0;

        for (int i = 0; i < lastNodes.size(); ++i) {
            var lastNode = lastNodes.get(i);
            var node = nodes.get(i);

            if (!StationBuilder.isMtrLoaded()) continue;

            var preview = previewCache.get(
                    lastNode, MTRIntegration.parseAngle(lastAngle),
                    node, MTRIntegration.parseAngle(angle)
            );
            if (preview == null) {
                preview = MTRIntegration.testConnectRailNodes(lastAngle, angle, lastNode, node);
                previewCache.put(
                        lastNode, MTRIntegration.parseAngle(lastAngle),
                        node, MTRIntegration.parseAngle(angle),
                        preview
                );
            }

            if (preview != null && preview.success()) {
                successCount += 1;
                if (preview.radius() > 0) {
                    minRadius = Math.min(minRadius, preview.radius());
                    minLength = Math.min(minLength, preview.length());
                }
            }
        }

        String extras = "";
        int extraColor = 0xAAAAAA;

        if (successCount < lastNodes.size()) {
            extras = Component.translatable("message.stationbuilder.rail_builder.fail", lastNodes.size() - successCount).getString();
            if (successCount > 0) extras += ", ";
            extraColor = 0xFF5555;
        }

        if (successCount > 0) {
            if (minRadius < 1e9) {
                extras += Component.translatable(
                        "message.stationbuilder.rail_builder.min_radius",
                        String.format("%.2f", minRadius),
                        String.format("%.2f", minLength)
                ).getString();
            } else {
                extras += Component.translatable("message.stationbuilder.rail_builder.straight_line").getString();
            }
        }

        renderDeltaText3D(event, textPos, d, extras, extraColor);
    }

    private static void renderCurve(PoseStack poseStack, MultiBufferSource consumers, Camera camera, List<Vec3> points) {
        if (points.size() < 2) return;

        Vec3 camPos = camera.getPosition();
        VertexConsumer vc = consumers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = poseStack.last().pose();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p0 = points.get(i);
            Vec3 p1 = points.get(i + 1);

            vc.addVertex(mat, (float) p0.x, (float) p0.y, (float) p0.z)
                    .setColor(255, 0, 0, 255)
                    .setNormal(1, 0, 0);

            vc.addVertex(mat, (float) p1.x, (float) p1.y, (float) p1.z)
                    .setColor(255, 0, 0, 255)
                    .setNormal(1, 0, 0);
        }

        poseStack.popPose();
    }

    private static void drawBox(PoseStack poseStack, VertexConsumer consumer, BlockPos pos, Vec3 cam,
                                float r, float g, float b, float a) {
        AABB box = new AABB(pos).move(-cam.x, -cam.y, -cam.z);
        ShapeRenderer.renderLineBox(poseStack, consumer, box, r, g, b, a);
    }

    private static Vec3 getPreviewCenterPos(List<BlockPos> lastNodes, BlockPos anchor) {
        Vec3 lastCenter = Vec3.ZERO;
        for (BlockPos p : lastNodes) {
            lastCenter = lastCenter.add(p.getCenter());
        }
        lastCenter = lastCenter.scale(1.0 / lastNodes.size());

        Vec3 delta = anchor.getCenter().subtract(lastCenter);
        return lastCenter.add(delta).add(0, 0.5, 0);
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

    private static void renderDeltaText3D(RenderLevelStageEvent event, Vec3 worldPos, Vec3i d, String extras, int extraColor) {
        Minecraft client = Minecraft.getInstance();
        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();
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

        poseStack.pushPose();
        Vec3 camPos = camera.getPosition();
        poseStack.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        poseStack.mulPose(camera.rotation());
        float scale = 0.025F;
        poseStack.scale(scale, -scale, scale);

        float x = -font.width(text) / 2f;
        float y = 0;

        font.drawInBatch(
                text, x, y, color, false,
                poseStack.last().pose(),
                Minecraft.getInstance().renderBuffers().bufferSource(),
                Font.DisplayMode.SEE_THROUGH,
                0, 0xF000F0
        );

        if (extras != null && !extras.isEmpty()) {
            float x2 = -font.width(extras) / 2f;
            float y2 = y + font.lineHeight + 3;
            font.drawInBatch(
                    extras, x2, y2, extraColor, false,
                    poseStack.last().pose(),
                    Minecraft.getInstance().renderBuffers().bufferSource(),
                    Font.DisplayMode.SEE_THROUGH,
                    0, 0xF000F0
            );
        }

        poseStack.popPose();
    }
}
