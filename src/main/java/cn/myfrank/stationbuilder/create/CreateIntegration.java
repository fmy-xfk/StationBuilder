package cn.myfrank.stationbuilder.create;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.myfrank.stationbuilder.items.RailBuilderConfig;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackShape;

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
        BlockState state = world.getBlockState(pos);
        return state.is(TRACK_BLOCK);
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

    /**
     * 获取铁轨方块中心的三维坐标
     */
    private static Vec3 getRailCenter(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, 0.125, 0);
    }

    /**
     * 高精度密集线性插值，将两点之间的路径填充到 points 中
     */
    private static void sampleSegment(List<Vec3> points, Vec3 from, Vec3 to, double step) {
        double dist = from.distanceTo(to);
        if (dist < 1e-4) return;
        int samples = Math.max(1, (int) Math.ceil(dist / step));
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            points.add(from.lerp(to, t));
        }
    }

    /**
     * 获取从 start 到 end 的完整连续中心线。
     * 本方法保证：返回的点集中，任意两点在空间上的间距均 <= step。
     * 映射到方块网格中时，切线与对角线过渡绝对不会漏格。
     */
    public static List<Vec3> getCompleteTrackCenterline(ServerLevel world, BlockPos start, BlockPos end, double step) {
        List<Vec3> centerline = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();

        BlockPos current = start;
        visited.add(current);

        int safetyLimit = 500;

        while (!current.equals(end) && safetyLimit-- > 0) {
            Vec3 currentCenter = getRailCenter(current);

            // 1. 检查当前铁轨是否有 BezierConnection
            BlockEntity be = world.getBlockEntity(current);
            boolean jumpedViaBezier = false;

            if (be instanceof TrackBlockEntity trackBE) {
                Map<BlockPos, BezierConnection> connections = trackBE.getConnections();
                if (connections != null && !connections.isEmpty()) {
                    for (Map.Entry<BlockPos, BezierConnection> entry : connections.entrySet()) {
                        BlockPos targetNode = entry.getKey();
                        BezierConnection bc = entry.getValue();

                        // 沿着未探索的分支向终点前进
                        if (!visited.contains(targetNode)) {
                            Vec3 curveStart = bc.getPosition(0.0);
                            Vec3 curveEnd = bc.getPosition(1.0);
                            Vec3 targetCenter = getRailCenter(targetNode);

                            // 【解决对角漏格的关键 1】：
                            // 45° 斜轨切入点 curveStart 距离当前方块中心可能有超过 1.0 格的切向距离，
                            // 必须先沿着直线段步进过渡到 curveStart
                            sampleSegment(centerline, currentCenter, curveStart, step);

                            // 【解决对角漏格的关键 2】：
                            // 密集采样贝塞尔曲线段自身
                            double curveLength = bc.getLength();
                            int curveSamples = Math.max(2, (int) Math.ceil(curveLength / step));
                            for (int i = 0; i <= curveSamples; i++) {
                                double t = (double) i / (double) curveSamples;
                                centerline.add(bc.getPosition(t));
                            }

                            // 【解决对角漏格的关键 3】：
                            // 从曲线切出点 curveEnd 密集过渡到目标铁轨方块中心
                            sampleSegment(centerline, curveEnd, targetCenter, step);

                            visited.add(targetNode);
                            current = targetNode;
                            jumpedViaBezier = true;
                            break;
                        }
                    }
                }
            }

            if (jumpedViaBezier) {
                continue;
            }

            // 2. 普通铁轨延伸（直轨或 45° 斜轨）：
            // 搜索相邻的独立铁轨方块（包含 26 邻域，支持高度差 ±1 及对角线 ±1）
            BlockPos nextPos = null;
            double minDistanceToEnd = Double.MAX_VALUE;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = current.offset(dx, dy, dz);
                        if (!visited.contains(neighbor) && isRailNode(world, neighbor)) {
                            double dist = neighbor.distSqr(end);
                            if (dist < minDistanceToEnd) {
                                minDistanceToEnd = dist;
                                nextPos = neighbor;
                            }
                        }
                    }
                }
            }

            if (nextPos != null) {
                Vec3 nextCenter = getRailCenter(nextPos);
                // 对两方块中心之间进行高精度插值采样（45° 方块之间距离为 1.414，插值可确保连续）
                sampleSegment(centerline, currentCenter, nextCenter, step);

                visited.add(nextPos);
                current = nextPos;
            } else {
                // 如果单步邻域断开，尝试在 2 格范围内探测（应对 45° 斜轨跨步方块）
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos jumpNeighbor = current.offset(dx, dy, dz);
                            if (!visited.contains(jumpNeighbor) && isRailNode(world, jumpNeighbor)) {
                                double dist = jumpNeighbor.distSqr(end);
                                if (dist < minDistanceToEnd) {
                                    minDistanceToEnd = dist;
                                    nextPos = jumpNeighbor;
                                }
                            }
                        }
                    }
                }

                if (nextPos != null) {
                    Vec3 nextCenter = getRailCenter(nextPos);
                    sampleSegment(centerline, currentCenter, nextCenter, step);
                    visited.add(nextPos);
                    current = nextPos;
                } else {
                    break;
                }
            }
        }

        return centerline;
    }

    public static boolean buildRails(
            ArrayList<BlockPos> startPositions, ArrayList<BlockPos> endPositions,
            Player player, ServerLevel world, RailBuilderConfig config) {

        int n = startPositions.size();
        if (n != endPositions.size()) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            BlockPos s = startPositions.get(i);
            BlockPos e = endPositions.get(i);

            ItemStack trackStack = new ItemStack(TRACK_BLOCK.asItem());

            BlockHitResult hitStart = new BlockHitResult(
                    Vec3.atCenterOf(s),
                    Direction.UP,
                    s,
                    false
            );
            UseOnContext startContext = new UseOnContext(world, player, InteractionHand.MAIN_HAND, trackStack, hitStart);
            InteractionResult firstResult = trackStack.useOn(startContext);

            if (firstResult != InteractionResult.SUCCESS && firstResult != InteractionResult.CONSUME) {
                continue;
            }

            BlockHitResult hitEnd = new BlockHitResult(
                    Vec3.atCenterOf(e),
                    Direction.UP,
                    e,
                    false
            );
            UseOnContext endContext = new UseOnContext(world, player, InteractionHand.MAIN_HAND, trackStack, hitEnd);
            InteractionResult secondResult = trackStack.useOn(endContext);

            if (secondResult == InteractionResult.SUCCESS || secondResult == InteractionResult.CONSUME) {
                List<Vec3> centerline = getCompleteTrackCenterline(world, s, e, 0.2);
                Set<BlockPos> placed = new LinkedHashSet<>();
                for (Vec3 pt : centerline) {
                    // 取铁轨正下方一格
                    BlockPos redstonePos = BlockPos.containing(pt.x, pt.y - 1.0, pt.z);

                    if (placed.add(redstonePos)) {
                        world.setBlock(redstonePos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                    }
                }
            }
        }

        return false;
    }
}