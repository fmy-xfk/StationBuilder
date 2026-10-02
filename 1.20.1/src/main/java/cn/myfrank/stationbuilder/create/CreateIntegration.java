package cn.myfrank.stationbuilder.create;

import java.util.*;

import cn.myfrank.stationbuilder.mixin.create.PlacementInfoAccessor;
import cn.myfrank.stationbuilder.utils.CurveData;
import cn.myfrank.stationbuilder.utils.PointProvider;
import com.simibubi.create.content.trains.track.*;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CreateIntegration {
    public record CreatePreviewResult(boolean success, double radius, double length, List<Vec3> positions) {}
    private static final TrackBlock TRACK_BLOCK = (TrackBlock) net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .get(ResourceLocation.fromNamespaceAndPath("create", "track"));

    public static Vec3 getTrackDirectionFromAngle(float angle) {
        int sector = Math.floorMod((int) Math.floor(angle / 45.0F + 0.5F), 8);
        return switch (sector) {
            case 0 -> new Vec3(0, 0, 1);
            case 1 -> new Vec3(-0.70710678, 0, 0.70710678);
            case 2 -> new Vec3(-1, 0, 0);
            case 3 -> new Vec3(-0.70710678, 0, -0.70710678);
            case 4 -> new Vec3(0, 0, -1);
            case 5 -> new Vec3(0.70710678, 0, -0.70710678);
            case 6 -> new Vec3(1, 0, 0);
            case 7 -> new Vec3(0.70710678, 0, 0.70710678);
            default -> new Vec3(0, 0, 1);
        };
    }

    public static BlockState getTrackBlockState(Direction facing) {
        BlockState state = TRACK_BLOCK.defaultBlockState();
        TrackShape shape = switch (facing) {
            case NORTH, SOUTH -> TrackShape.ZO;
            case EAST, WEST -> TrackShape.XO;
            default -> TrackShape.NONE;
        };
        if (shape != TrackShape.NONE) {
            state = state.setValue(TrackBlock.SHAPE, shape);
        }
        return state;
    }

    public static boolean isRailNode(Level world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock;
    }

    public static BlockState getTrackBlockState(float angle) {
        BlockState state = TRACK_BLOCK.defaultBlockState();
        int sector = Math.floorMod((int) Math.floor(angle / 45.0F + 0.5F), 8);
        TrackShape shape = switch (sector) {
            case 0, 4 -> TrackShape.ZO;
            case 2, 6 -> TrackShape.XO;
            case 1, 5 -> TrackShape.ND;
            case 3, 7 -> TrackShape.PD;
            default -> TrackShape.NONE;
        };
        if (shape != TrackShape.NONE) {
            state = state.setValue(TrackBlock.SHAPE, shape);
        }
        return state;
    }

    public static void placeRailNode(ServerLevel world, BlockPos pos, float angle) {
        var state = getTrackBlockState(angle);
        world.setBlock(pos, state, 3);
    }

    public static Vec3 getRailCenter(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, 0.125, 0);
    }

    public static void carveTunnelAndBuildBridge(ServerLevel world, CurveData curveData, BlockPos startPos, BlockPos endPos, int tunnelRadius) {
        int segments = Math.max(10, (int) Math.ceil(curveData.getLength() * 5));
        PointProvider provider = new PointProvider(curveData, segments, false);
        Set<BlockPos> modified = new HashSet<>();

        while (provider.notExhausted()) {
            List<Vec3> frame = provider.get();
            Vec3 center = frame.get(0);
            Vec3 normal = frame.get(2);

            for (int w = -tunnelRadius; w <= tunnelRadius; w++) {
                Vec3 slicePos = center.add(normal.scale(w));
                BlockPos columnBase = BlockPos.containing(slicePos.x, slicePos.y, slicePos.z);

                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos airPos = columnBase.above(dy);
                    if (airPos.equals(startPos) || airPos.equals(endPos)) continue;

                    if (modified.add(airPos)) {
                        BlockState state = world.getBlockState(airPos);
                        if (!state.isAir() && !(state.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock)) {
                            world.setBlock(airPos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }

                BlockPos floorPos = columnBase.below();
                if (modified.add(floorPos)) {
                    BlockState floorState = world.getBlockState(floorPos);
                    if (floorState.isAir() || !floorState.getFluidState().isEmpty()) {
                        world.setBlock(floorPos, Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
            provider.next();
        }
    }

    /**
     * 单段标准 Create 原生连接（两点距离在限制内时调用）
     */
    public static CurveData connectRailNodes(Player player, ServerLevel world, BlockPos s, BlockPos e) {
        BlockState state1 = world.getBlockState(s);
        BlockState state2 = world.getBlockState(e);

        if (!(state1.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock) ||
                !(state2.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock)) {
            return null;
        }

        ItemStack trackStack = new ItemStack(TRACK_BLOCK.asItem());

        BlockHitResult hitStart = new BlockHitResult(Vec3.atCenterOf(s), Direction.UP, s, false);
        UseOnContext startContext = new UseOnContext(world, player, InteractionHand.MAIN_HAND, trackStack, hitStart);
        InteractionResult firstResult = trackStack.useOn(startContext);
        if (firstResult != InteractionResult.SUCCESS && firstResult != InteractionResult.CONSUME) {
            return null;
        }

        TrackPlacement.PlacementInfo info;
        try {
            info = TrackPlacement.tryConnect(world, player, e, state2, trackStack, false, false);
        } catch (Throwable t) {
            return null;
        }

        if (info == null) return null;
        PlacementInfoAccessor accessor = (PlacementInfoAccessor) (Object) info;
        if (!accessor.isValid()) return null;

        CreateCurveData curveData = new CreateCurveData();
        Vec3 startCenter = getRailCenter(s);
        Vec3 endCenter = getRailCenter(e);
        BezierConnection curve = accessor.getCurve();

        if (curve != null) {
            Vec3 curveStart = curve.getPosition(0.0);
            Vec3 curveEnd = curve.getPosition(1.0);
            curveData.addLine(startCenter, curveStart);
            curveData.addBezier(curve);
            curveData.addLine(curveEnd, endCenter);
        } else {
            curveData.addLine(startCenter, endCenter);
        }

        carveTunnelAndBuildBridge(world, curveData, s, e, 1);

        BlockHitResult hitEnd = new BlockHitResult(Vec3.atCenterOf(e), Direction.UP, e, false);
        UseOnContext endContext = new UseOnContext(world, player, InteractionHand.MAIN_HAND, trackStack, hitEnd);
        InteractionResult secondResult = trackStack.useOn(endContext);

        if (secondResult == InteractionResult.SUCCESS || secondResult == InteractionResult.CONSUME) {
            return curveData;
        }
        return null;
    }

    public static CreatePreviewResult testConnectRailNodes(
            BlockPos startPos, float startAngle,
            BlockPos endPos, float endAngle) {

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return new CreatePreviewResult(false, 0, 0, List.of());
        }

        Level level = client.level;
        Player player = client.player;

        // 【关键防御 1】：如果起点已被挖掉（不是 Create 铁轨方块），坚决不执行 useOn，彻底根绝幽灵方块！
        BlockState stateStart = level.getBlockState(startPos);
        if (!(stateStart.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock)) {
            return new CreatePreviewResult(false, 0, 0, List.of());
        }

        // 终点虚拟状态：如果终点尚未放置铁轨，虚拟一个对应朝向的轨道状态给求解器
        BlockState stateEnd = level.getBlockState(endPos);
        if (!(stateEnd.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock)) {
            stateEnd = getTrackBlockState(endAngle);
        }

        // 构造虚拟铁轨物品
        ItemStack trackStack = new ItemStack(TRACK_BLOCK.asItem());

        // 【关键修复 2】：因为上面已经确认 startPos 是真实存在的铁轨，
        // 这里的 useOn 只会提取起点数据写入 trackStack，绝不会在空气中放置幽灵方块！
        BlockHitResult hitStart = new BlockHitResult(
                Vec3.atCenterOf(startPos),
                Direction.UP,
                startPos,
                false
        );
        UseOnContext startContext = new UseOnContext(level, player, InteractionHand.MAIN_HAND, trackStack, hitStart);
        InteractionResult firstResult = trackStack.useOn(startContext);
        if (firstResult != InteractionResult.SUCCESS && firstResult != InteractionResult.CONSUME) {
            return new CreatePreviewResult(false, 0, 0, List.of());
        }

        // 调用 Create 原生求解器（穿透模式 ignoreObstruction = true）
        TrackPlacement.PlacementInfo info;
        try {
            info = TrackPlacement.tryConnect(level, player, endPos, stateEnd, trackStack, false, true);
        } catch (Throwable t) {
            return new CreatePreviewResult(false, 0, 0, List.of());
        }

        if (info == null) {
            return new CreatePreviewResult(false, 0, 0, List.of());
        }

        PlacementInfoAccessor accessor = (PlacementInfoAccessor) (Object) info;
        if (!accessor.isValid()) {
            return new CreatePreviewResult(false, 0, 0, List.of());
        }

        // 提取几何信息与连续中心线
        BezierConnection curve = accessor.getCurve();
        Vec3 startCenter = getRailCenter(startPos);
        Vec3 endCenter = getRailCenter(endPos);

        List<Vec3> previewPoints = new ArrayList<>();
        double radius = 0.0;
        double length = 0.0;

        if (curve != null) {
            radius = curve.getRadius();
            length = curve.getLength();

            Vec3 curveStart = curve.getPosition(0.0);
            Vec3 curveEnd = curve.getPosition(1.0);
            double step = 0.25;

            // (1) 起点方块中心 -> 曲线切点 curveStart
            sampleSegment(previewPoints, startCenter, curveStart, step);

            // (2) 贝塞尔曲线段细分采样
            int curveSamples = Math.max(2, (int) Math.ceil(length / step));
            for (int i = 0; i <= curveSamples; i++) {
                double t = (double) i / (double) curveSamples;
                previewPoints.add(curve.getPosition(t));
            }

            // (3) 曲线切点 curveEnd -> 终点方块中心
            sampleSegment(previewPoints, curveEnd, endCenter, step);
            length += startCenter.distanceTo(curveStart) + curveEnd.distanceTo(endCenter);
        } else {
            length = startCenter.distanceTo(endCenter);
            radius = 0.0;
            sampleSegment(previewPoints, startCenter, endCenter, 0.5);
        }

        return new CreatePreviewResult(true, radius, length, previewPoints);
    }

    /**
     * 线性离散采样辅助方法
     */
    private static void sampleSegment(List<Vec3> points, Vec3 from, Vec3 to, double step) {
        double dist = from.distanceTo(to);
        if (dist < 1e-4) {
            if (points.isEmpty() || points.get(points.size() - 1).distanceTo(from) > 1e-4) {
                points.add(from);
            }
            return;
        }
        int samples = Math.max(1, (int) Math.ceil(dist / step));
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            Vec3 p = from.lerp(to, t);
            if (points.isEmpty() || points.get(points.size() - 1).distanceTo(p) > 1e-4) {
                points.add(p);
            }
        }
    }
}