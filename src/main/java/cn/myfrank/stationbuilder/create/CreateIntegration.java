package cn.myfrank.stationbuilder.create;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.myfrank.stationbuilder.items.RailBuilderConfig;
import cn.myfrank.stationbuilder.utils.CurveData;
import cn.myfrank.stationbuilder.utils.PointProvider;
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

    private static Vec3 getRailCenter(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, 0.125, 0);
    }

    /**
     * 构建并返回起点 start 到终点 end 的复合曲线数学模型 CurveData
     */
    public static CreateCurveData getCenterLine(ServerLevel world, BlockPos start, BlockPos end) {
        CreateCurveData curveData = new CreateCurveData();
        Set<BlockPos> visited = new HashSet<>();

        BlockPos current = start;
        visited.add(current);

        int safetyLimit = 500;

        while (!current.equals(end) && safetyLimit-- > 0) {
            Vec3 currentCenter = getRailCenter(current);

            // 1. 检查是否有 BezierConnection
            BlockEntity be = world.getBlockEntity(current);
            boolean jumpedViaBezier = false;

            if (be instanceof TrackBlockEntity trackBE) {
                Map<BlockPos, BezierConnection> connections = trackBE.getConnections();
                if (connections != null && !connections.isEmpty()) {
                    for (Map.Entry<BlockPos, BezierConnection> entry : connections.entrySet()) {
                        BlockPos targetNode = entry.getKey();
                        BezierConnection bc = entry.getValue();

                        if (!visited.contains(targetNode)) {
                            Vec3 curveStart = bc.getPosition(0.0);
                            Vec3 curveEnd = bc.getPosition(1.0);
                            Vec3 targetCenter = getRailCenter(targetNode);

                            // (1) 当前方块中心 -> 曲线起点切点
                            curveData.addLine(currentCenter, curveStart);
                            // (2) 贝塞尔曲线自身
                            curveData.addBezier(bc);
                            // (3) 曲线终点切点 -> 目标方块中心
                            curveData.addLine(curveEnd, targetCenter);

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

            // 2. 直线/45°对角线延伸：寻找邻接的独立普通铁轨方块
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
                curveData.addLine(currentCenter, nextCenter);
                visited.add(nextPos);
                current = nextPos;
            } else {
                // 斜向 2 格跨度跃迁检测（针对部分 45° 跨步情况）
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
                    curveData.addLine(currentCenter, nextCenter);
                    visited.add(nextPos);
                    current = nextPos;
                } else {
                    break;
                }
            }
        }

        return curveData;
    }

    public static CurveData connectRailNodes(Player player, ServerLevel world, BlockPos s, BlockPos e) {
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

        // 2. 点击终点
        BlockHitResult hitEnd = new BlockHitResult(
                Vec3.atCenterOf(e),
                Direction.UP,
                e,
                false
        );
        UseOnContext endContext = new UseOnContext(world, player, InteractionHand.MAIN_HAND, trackStack, hitEnd);
        InteractionResult secondResult = trackStack.useOn(endContext);

        if (secondResult == InteractionResult.SUCCESS || secondResult == InteractionResult.CONSUME) {
            // 3. 获取复合曲线数学模型 CurveData
            return getCenterLine(world, s, e);
        }
        return null;
    }
}