package cn.myfrank.stationbuilder.create;

import java.util.*;

import cn.myfrank.stationbuilder.mixin.create.PlacementInfoAccessor;
import cn.myfrank.stationbuilder.utils.CurveData;
import cn.myfrank.stationbuilder.utils.PointProvider;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackPlacement;
import com.simibubi.create.content.trains.track.TrackShape;

import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CreateIntegration {
    private static final TrackBlock TRACK_BLOCK = (TrackBlock) net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .get(ResourceLocation.fromNamespaceAndPath("create", "track"));

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

    public static boolean isRailNode(ServerLevel world, BlockPos pos) {
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

    public static void placeRailNode(ServerLevel world, BlockPos pos, Direction facing) {
        var state = getTrackBlockState(facing);
        world.setBlock(pos, state, 3);
    }

    public static void placeRailNode(ServerLevel world, BlockPos pos, float angle) {
        var state = getTrackBlockState(angle);
        world.setBlock(pos, state, 3);
    }

    private static Vec3 getRailCenter(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, 0.125, 0);
    }

    public static void carveTunnelAndBuildBridge(ServerLevel world, CurveData curveData, BlockPos startPos, BlockPos endPos, int tunnelRadius) {
        int segments = Math.max(10, (int) Math.ceil(curveData.getLength() * 5)); // 每 0.2 格推进一次
        PointProvider provider = new PointProvider(curveData, segments, false);

        Set<BlockPos> modified = new HashSet<>();

        while (provider.notExhausted()) {
            List<Vec3> frame = provider.get();
            Vec3 center = frame.get(0);
            Vec3 normal = frame.get(2); // 与前进方向垂直的水平法线向量

            // 沿着截面法线与垂直方向进行切片清理与桥梁铺设
            for (int w = -tunnelRadius; w <= tunnelRadius; w++) {
                Vec3 slicePos = center.add(normal.scale(w));
                BlockPos columnBase = BlockPos.containing(slicePos.x, slicePos.y, slicePos.z);

                // 1. 穿山：开辟行车净空（高度 0, +1, +2）
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos airPos = columnBase.above(dy);
                    if (airPos.equals(startPos) || airPos.equals(endPos)) continue;

                    if (modified.add(airPos)) {
                        BlockState state = world.getBlockState(airPos);
                        if (!state.isAir() && !state.is(TRACK_BLOCK)) {
                            world.setBlock(airPos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }

                // 2. 跨海/过虚空：在轨道下方一格（dy = -1）铺设桥面地基，排干水体
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

    public static CurveData connectRailNodes(Player player, ServerLevel world, BlockPos s, BlockPos e) {
        // 0. 防御性检查 1：检查起点与终点方块是否存在且确实属于 Create 的轨道
        BlockState state1 = world.getBlockState(s);
        BlockState state2 = world.getBlockState(e);

        if (!(state1.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock) ||
                !(state2.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock)) {
            // 如果某一边是 MTR Node、原版普通铁轨或其他方块，直接拒绝执行 Create 的连接
            return null;
        }

        ItemStack trackStack = new ItemStack(TRACK_BLOCK.asItem());

        // 1. 点击起点
        BlockHitResult hitStart = new BlockHitResult(
                Vec3.atCenterOf(s),
                Direction.UP,
                s,
                false
        );
        UseOnContext startContext = new UseOnContext(world, player, InteractionHand.MAIN_HAND, trackStack, hitStart);
        InteractionResult firstResult = trackStack.useOn(startContext);
        if (firstResult != InteractionResult.SUCCESS && firstResult != InteractionResult.CONSUME) {
            return null;
        }

        // 防御性检查 2：确保第一次右键确实把起点信息成功写入了 trackStack 的组件中
        if (!trackStack.has(com.simibubi.create.AllDataComponents.TRACK_CONNECTING_FROM)) {
            return null;
        }

        // 再次确认起点方块在右键后没有被替换或损坏，且仍然是 Create 铁轨
        if (!world.getBlockState(s).is(state1.getBlock())) {
            return null;
        }

        // 2. 调用 tryConnect 提取原生推导好的几何模型（包裹 try-catch，防止跨模组方块或非法角度引发 ClassCastException / NPE）
        TrackPlacement.PlacementInfo info;
        try {
            info = TrackPlacement.tryConnect(
                    world, player, e, state2, trackStack, false, false
            );
        } catch (Throwable t) {
            // 捕获任何第三方轨道碰撞或 Create 内部未能预料的异常
            return null;
        }

        if (info == null) {
            return null;
        }

        PlacementInfoAccessor accessor = (PlacementInfoAccessor) (Object) info;
        if (!accessor.isValid()) {
            return null;
        }

        // 3. 将计算完毕的延伸直线方块与贝塞尔曲线组合为完整的沿轨中心线
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

        // 4. 严格沿着推导出的轨道中心线开凿隧道与铺设桥面地基（穿山跨海）
        carveTunnelAndBuildBridge(world, curveData, s, e, 1);

        // 5. 地形已沿轨道打通/垫平，执行第二次右键完成真正的铁轨放置
        BlockHitResult hitEnd = new BlockHitResult(
                Vec3.atCenterOf(e),
                Direction.UP,
                e,
                false
        );
        UseOnContext endContext = new UseOnContext(world, player, InteractionHand.MAIN_HAND, trackStack, hitEnd);
        InteractionResult secondResult = trackStack.useOn(endContext);

        if (secondResult == InteractionResult.SUCCESS || secondResult == InteractionResult.CONSUME) {
            // 防御性检查 3：放置后再次验证终点与起点方块仍然完好，防止并发破坏
            if (world.getBlockState(s).getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock &&
                    world.getBlockState(e).getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock) {
                return curveData;
            }
        }

        return null;
    }

    private static final double MAX_DISTANCE = AllConfigs.server().trains.maxTrackPlacementLength.get();
    private static final double MAX_SAFE_DISTANCE = MAX_DISTANCE * 0.8;
    /**
     * 具有超长连接能力的高级铺轨入口
     * 如果 s 和 e 距离超过限制，会自动插桩铺设中间节点并连续分段连接
     */
    public static CurveData connectRailNodesLongDistance(Player player, ServerLevel world, BlockPos s, BlockPos e) {
        double dist = Math.sqrt(s.distSqr(e));

        // 1. 如果在安全距离内，直接走单段连接
        if (dist <= MAX_SAFE_DISTANCE) {
            return connectRailNodes(player, world, s, e);
        }

        // 2. 超出上限，计算需要拆分成多少个子区间
        int cuts = (int) Math.ceil(dist / MAX_SAFE_DISTANCE);
        Vec3 startVec = Vec3.atBottomCenterOf(s);
        Vec3 endVec = Vec3.atBottomCenterOf(e);

        List<BlockPos> waypoints = new ArrayList<>();
        waypoints.add(s);

        // 3. 计算并放置中间节点
        for (int i = 1; i < cuts; i++) {
            double t = (double) i / cuts;
            // 沿两点间插值（如果是纯平地/斜坡，可线性插值）
            Vec3 intermediateVec = startVec.lerp(endVec, t);
            BlockPos midPos = BlockPos.containing(intermediateVec);

            // 计算该段的前进朝向角度（偏航角）
            Vec3 dir = endVec.subtract(startVec).normalize();
            float angle = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));

            // 在中间位置放置正确的倾斜/直线节点
            placeRailNode(world, midPos, angle);
            waypoints.add(midPos);
        }
        waypoints.add(e);

        // 4. 顺次分段连接并融合成整条 CurveData
        CreateCurveData fullCurveData = new CreateCurveData();

        for (int i = 0; i < waypoints.size() - 1; i++) {
            BlockPos segStart = waypoints.get(i);
            BlockPos segEnd = waypoints.get(i + 1);

            CurveData segCurve = connectRailNodes(player, world, segStart, segEnd);
            if (segCurve instanceof CreateCurveData ccd) {
                fullCurveData.append(ccd);
            } else {
                // 若某一段放置失败，返回已完成的部分或 null
                return fullCurveData.getLength() > 0 ? fullCurveData : null;
            }
        }

        return fullCurveData;
    }
}