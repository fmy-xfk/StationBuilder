package cn.myfrank.stationbuilder.create;

import java.util.*;

import cn.myfrank.stationbuilder.StationBuilder;
import cn.myfrank.stationbuilder.mixin.create.PlacementInfoAccessor;
import cn.myfrank.stationbuilder.utils.CurveData;
import cn.myfrank.stationbuilder.utils.PointProvider;
import com.simibubi.create.content.trains.track.*;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.client.MinecraftClient;

public class CreateIntegration {
    public record CreatePreviewResult(boolean success, double radius, double length, List<Vec3d> positions) {}
    private static final Block TRACK_BLOCK = Registries.BLOCK.get(new Identifier("create", "track"));

    public static Vec3d getTrackDirectionFromAngle(float angle) {
        int sector = Math.floorMod((int) Math.floor(angle / 45.0F + 0.5F), 8);
        return switch (sector) {
            case 0 -> new Vec3d(0, 0, 1);
            case 1 -> new Vec3d(-0.70710678, 0, 0.70710678);
            case 2 -> new Vec3d(-1, 0, 0);
            case 3 -> new Vec3d(-0.70710678, 0, -0.70710678);
            case 4 -> new Vec3d(0, 0, -1);
            case 5 -> new Vec3d(0.70710678, 0, -0.70710678);
            case 6 -> new Vec3d(1, 0, 0);
            case 7 -> new Vec3d(0.70710678, 0, 0.70710678);
            default -> new Vec3d(0, 0, 1);
        };
    }

    public static BlockState getTrackBlockState(Direction facing) {
        BlockState state = TRACK_BLOCK.getDefaultState();
        TrackShape shape = switch (facing) {
            case NORTH, SOUTH -> TrackShape.ZO;
            case EAST, WEST -> TrackShape.XO;
            default -> TrackShape.NONE;
        };
        if (shape != TrackShape.NONE) {
            state = state.with(TrackBlock.SHAPE, shape);
        }
        return state;
    }

    public static boolean isRailNode(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock;
    }

    public static BlockState getTrackBlockState(float angle) {
        BlockState state = TRACK_BLOCK.getDefaultState();
        int sector = Math.floorMod((int) Math.floor(angle / 45.0F + 0.5F), 8);
        TrackShape shape = switch (sector) {
            case 0, 4 -> TrackShape.ZO;
            case 2, 6 -> TrackShape.XO;
            case 1, 5 -> TrackShape.ND;
            case 3, 7 -> TrackShape.PD;
            default -> TrackShape.NONE;
        };
        if (shape != TrackShape.NONE) {
            state = state.with(TrackBlock.SHAPE, shape);
        }
        return state;
    }

    public static void placeRailNode(ServerWorld world, BlockPos pos, float angle) {
        var state = getTrackBlockState(angle);
        world.setBlockState(pos, state, 3);
    }

    public static Vec3d getRailCenter(BlockPos pos) {
        return Vec3d.ofCenter(pos).add(0, 0.125, 0);
    }

    public static void carveTunnelAndBuildBridge(ServerWorld world, CurveData curveData, BlockPos startPos, BlockPos endPos, int tunnelRadius) {
        int segments = Math.max(10, (int) Math.ceil(curveData.getLength() * 5));
        PointProvider provider = new PointProvider(curveData, segments, false);
        Set<BlockPos> modified = new HashSet<>();

        while (provider.notExhausted()) {
            List<Vec3d> frame = provider.get();
            Vec3d center = frame.get(0);
            Vec3d normal = frame.get(2);

            for (int w = -tunnelRadius; w <= tunnelRadius; w++) {
                Vec3d slicePos = center.add(normal.multiply(w));
                BlockPos columnBase = BlockPos.ofFloored(slicePos.x, slicePos.y, slicePos.z);

                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos airPos = columnBase.up(dy);
                    if (airPos.equals(startPos) || airPos.equals(endPos)) continue;

                    if (modified.add(airPos)) {
                        BlockState state = world.getBlockState(airPos);
                        if (!state.isAir() && !(state.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock)) {
                            world.setBlockState(airPos, Blocks.AIR.getDefaultState(), 3);
                        }
                    }
                }

                BlockPos floorPos = columnBase.down();
                if (modified.add(floorPos)) {
                    BlockState floorState = world.getBlockState(floorPos);
                    if (floorState.isAir() || !floorState.getFluidState().isEmpty()) {
                        world.setBlockState(floorPos, Blocks.STONE.getDefaultState(), 3);
                    }
                }
            }
            provider.next();
        }
    }

    /**
     * 单段标准 Create 原生连接（两点距离在限制内时调用）
     */
    public static CurveData connectRailNodes(PlayerEntity player, ServerWorld world, BlockPos s, BlockPos e) {
        System.out.printf("Connect: %s %s\n", s, e);
        BlockState state1 = world.getBlockState(s);
        BlockState state2 = world.getBlockState(e);

        if (!(state1.getBlock() instanceof ITrackBlock) || !(state2.getBlock() instanceof ITrackBlock)) {
            return null;
        }

        ItemStack trackStack = new ItemStack(TRACK_BLOCK.asItem());

        BlockHitResult hitStart = new BlockHitResult(Vec3d.ofCenter(s), Direction.UP, s, false);
        ItemUsageContext startContext = new ItemUsageContext(world, player, Hand.MAIN_HAND, trackStack, hitStart);
        ActionResult firstResult = trackStack.useOnBlock(startContext);

        if (!firstResult.isAccepted()) {
            return null;
        }

        TrackPlacement.PlacementInfo info;
        try {
            info = TrackPlacement.tryConnect(world, player, e, state2, trackStack, false, true);
        } catch (Throwable t) {
            return null;
        }

        if (info == null) return null;
        PlacementInfoAccessor accessor = (PlacementInfoAccessor) info;
        if (!accessor.isValid()) return null;

        CreateCurveData curveData = new CreateCurveData();
        Vec3d startCenter = getRailCenter(s);
        Vec3d endCenter = getRailCenter(e);
        BezierConnection curve = accessor.getCurve();

        if (curve != null) {
            Vec3d curveStart = curve.getPosition(0.0);
            Vec3d curveEnd = curve.getPosition(1.0);
            curveData.addLine(startCenter, curveStart);
            curveData.addBezier(curve);
            curveData.addLine(curveEnd, endCenter);
        } else {
            curveData.addLine(startCenter, endCenter);
        }

        carveTunnelAndBuildBridge(world, curveData, s, e, 1);

        try {
            BlockState updatedState2 = world.getBlockState(e);
            TrackPlacement.tryConnect(world, player, e, updatedState2, trackStack, false, true);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return curveData;
    }

    public static CreatePreviewResult testConnectRailNodes(
            BlockPos startPos, float startAngle,
            BlockPos endPos, float endAngle) {

        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world == null || client.player == null) {
            return new CreatePreviewResult(false, 0, 0, List.of());
        }

        World level = client.world;
        PlayerEntity player = client.player;

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
                Vec3d.ofCenter(startPos),
                Direction.UP,
                startPos,
                false
        );
        ItemUsageContext startContext = new ItemUsageContext(level, player, Hand.MAIN_HAND, trackStack, hitStart);
        ActionResult firstResult = trackStack.useOnBlock(startContext);
        if (firstResult != ActionResult.SUCCESS && firstResult != ActionResult.CONSUME) {
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
        Vec3d startCenter = getRailCenter(startPos);
        Vec3d endCenter = getRailCenter(endPos);

        List<Vec3d> previewPoints = new ArrayList<>();
        double radius = 0.0;
        double length = 0.0;

        if (curve != null) {
            radius = curve.getRadius();
            length = curve.getLength();

            Vec3d curveStart = curve.getPosition(0.0);
            Vec3d curveEnd = curve.getPosition(1.0);
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
    private static void sampleSegment(List<Vec3d> points, Vec3d from, Vec3d to, double step) {
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
            Vec3d p = from.lerp(to, t);
            if (points.isEmpty() || points.get(points.size() - 1).distanceTo(p) > 1e-4) {
                points.add(p);
            }
        }
    }
}