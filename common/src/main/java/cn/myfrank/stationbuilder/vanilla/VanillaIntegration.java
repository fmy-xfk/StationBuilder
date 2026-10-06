package cn.myfrank.stationbuilder.vanilla;

import cn.myfrank.stationbuilder.utils.CommonUtil;
import cn.myfrank.stationbuilder.utils.CurveData;
import cn.myfrank.stationbuilder.utils.TestConnectResult;
import cn.myfrank.stationbuilder.vanilla.VanillaCurveData.Shape;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VanillaIntegration {
    public static boolean isRailNode(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof AbstractRailBlock;
    }

    @Nullable
    public static RailShape getShapeFromState(BlockState state) {
        if (state.contains(Properties.RAIL_SHAPE)) {
            return state.get(Properties.RAIL_SHAPE);
        } else if (state.contains(Properties.STRAIGHT_RAIL_SHAPE)) {
            return state.get(Properties.STRAIGHT_RAIL_SHAPE);
        }
        return null;
    }

    public static boolean isStraightRailNode(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractRailBlock)) return false;
        RailShape shape = getShapeFromState(state);
        return shape != null && (shape == RailShape.NORTH_SOUTH || shape == RailShape.EAST_WEST);
    }

    public static float getAngle(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        RailShape shape = getShapeFromState(state);
        if (shape == RailShape.NORTH_SOUTH) return 90.0f;
        if (shape == RailShape.EAST_WEST) return 0.0f;
        return -1.0f;
    }


    public static float parseAngle(float playerYaw) {
        Direction direction = Direction.fromRotation(playerYaw);
        switch (direction) {
            case NORTH, SOUTH: return 90.0f;
            case EAST, WEST: return 0.0f;
            default: return -1.0f;
        }
    }

    public static boolean isCurveShape(RailShape shape) {
        return shape == RailShape.NORTH_EAST || shape == RailShape.NORTH_WEST
                || shape == RailShape.SOUTH_EAST || shape == RailShape.SOUTH_WEST;
    }

    /**
     * 确保铁轨所在位置及其上方清空，且正下方1格有实心方块支撑
     */
    public static void ensurePlacementConditions(ServerWorld world, BlockPos pos) {
        // 1. 确保自身及上方净空
        for (int y = 0; y <= 2; y++) {
            BlockPos checkPos = pos.up(y);
            BlockState state = world.getBlockState(checkPos);
            if (!state.isAir()) {
                world.setBlockState(checkPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
        }

        // 2. 确保正下方1格有方块支撑（若为空气、液体或可替换杂物，则补齐方块）
        BlockPos below = pos.down();
        if (world.isInBuildLimit(below)) {
            BlockState belowState = world.getBlockState(below);
            if (belowState.isAir() || !belowState.getFluidState().isEmpty() || belowState.isReplaceable()) {
                world.setBlockState(below, Blocks.SMOOTH_STONE.getDefaultState(), 3);
            }
        }
    }

    public static void placeRailNode(ServerWorld world, BlockPos pos, float angle) {
        // 先确保放置环境合格（上方净空，下方有实心方块支撑）
        ensurePlacementConditions(world, pos);

        Direction dir = Direction.fromRotation(angle);
        RailShape shape = (dir.getAxis() == Direction.Axis.X) ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        BlockState state = Blocks.RAIL.getDefaultState();
        if (state.contains(Properties.RAIL_SHAPE)) {
            state = state.with(Properties.RAIL_SHAPE, shape);
        }
        world.setBlockState(pos, state, 3);
    }

    public static TestConnectResult testConnectRailNodes(BlockPos startPos, float parsedStartAngle, BlockPos endPos, float parsedEndAngle) {
        // Truncate angle
        var angles = VanillaCurveData.getAngles(startPos, parsedStartAngle, endPos, parsedEndAngle);
        var facingStart = angles.left();
        var facingEnd   = angles.right();
        var math = new VanillaCurveData(startPos, facingStart, endPos, facingEnd, Shape.QUADRATIC, 0.0);
        if (math.isValid()) {
            var radii = math.getHorizontalRadii();
            double radius;
            if (radii.leftDouble() > 0) {
                if (radii.rightDouble() > 0) {
                    radius = Math.min(radii.leftDouble(), radii.rightDouble());
                } else {
                    radius = radii.leftDouble();
                }
            } else {
                radius = radii.rightDouble();
            }
            ArrayList<Vec3d> points = new ArrayList<>();
            for(double s = 0; s <= math.getLength(); s += 0.1) {
                points.add(math.getPosition(s, false));
            }
            return new TestConnectResult(true, radius, math.getLength(), points);
        }
        return new TestConnectResult(false, 0, 0, new ArrayList<>());
    }

    public static CurveData connectRailNodes(
            PlayerEntity player, ServerWorld world, BlockPos a, BlockPos b, Identifier railType
    ) {
        if (!isStraightRailNode(world, a) || !isStraightRailNode(world, b)) {
            return null;
        }

        var angles = VanillaCurveData.getAngles(
            a, getAngle(world, a),
            b, getAngle(world, b)
        );
        var facingStart = angles.left();
        var facingEnd   = angles.right();

        // 正向计算
        VanillaCurveData curve = new VanillaCurveData(a, facingStart, b, facingEnd, Shape.QUADRATIC, 0.0);

        if (!curve.isValid()) {
            return null;
        }

        placeVanillaRailsAlongCurve(world, curve, railType);

        return curve;
    }

    private static void placeVanillaRailsAlongCurve(ServerWorld world, CurveData curve, Identifier railType) {
        BlockState configuredRail = CommonUtil.getBlockState(railType);
        if (configuredRail.isAir() || !(configuredRail.getBlock() instanceof AbstractRailBlock)) {
            configuredRail = Blocks.RAIL.getDefaultState();
        }

        // 1. 高密度采样生成曲线的三维浮点路径
        double totalLen = curve.getLength();
        double sampleInterval = 0.05;
        int sampleCount = Math.max(10, (int) Math.ceil(totalLen / sampleInterval));
        List<Vec3d> trajectory = new ArrayList<>();
        for (int i = 0; i <= sampleCount; i++) {
            trajectory.add(curve.getPosition((totalLen * i) / sampleCount));
        }

        // 2. 栅格化步进：避免纯垂直步进断轨，高度差附着在水平位移上
        List<BlockPos> connectedPath = new ArrayList<>();
        if (!trajectory.isEmpty()) {
            BlockPos currentPos = BlockPos.ofFloored(trajectory.get(0).x, trajectory.get(0).y, trajectory.get(0).z);
            connectedPath.add(currentPos);

            for (int i = 1; i < trajectory.size(); i++) {
                Vec3d target = trajectory.get(i);
                BlockPos targetPos = BlockPos.ofFloored(target.x, target.y, target.z);

                while (!currentPos.equals(targetPos)) {
                    int dx = targetPos.getX() - currentPos.getX();
                    int dy = targetPos.getY() - currentPos.getY();
                    int dz = targetPos.getZ() - currentPos.getZ();

                    int stepX = 0;
                    int stepY = 0;
                    int stepZ = 0;

                    if (Math.abs(dx) >= Math.abs(dz) && dx != 0) {
                        stepX = Integer.signum(dx);
                    } else if (dz != 0) {
                        stepZ = Integer.signum(dz);
                    }

                    if (dy != 0 && (stepX != 0 || stepZ != 0)) {
                        stepY = Integer.signum(dy);
                    } else if (stepX == 0 && stepZ == 0 && dy != 0) {
                        stepY = Integer.signum(dy);
                    }

                    currentPos = currentPos.add(stepX, stepY, stepZ);
                    connectedPath.add(currentPos);
                }
            }
        }

        // 3. 冗余清理
        List<BlockPos> cleanPath = new ArrayList<>();
        for (BlockPos p : connectedPath) {
            if (cleanPath.isEmpty() || !cleanPath.get(cleanPath.size() - 1).equals(p)) {
                if (cleanPath.size() >= 2 && cleanPath.get(cleanPath.size() - 2).equals(p)) {
                    cleanPath.remove(cleanPath.size() - 1);
                } else {
                    cleanPath.add(p);
                }
            }
        }

        if (cleanPath.size() < 2) return;

        // 4. 清理上方空间 + 仅垫脚正下方 1 格
        for (BlockPos pos : cleanPath) {
            // 净空铁轨所在格及上方空间（穿山）
            for (int y = 0; y <= 3; y++) {
                BlockPos airPos = pos.up(y);
                BlockState state = world.getBlockState(airPos);
                if (!state.isAir()) {
                    world.setBlockState(airPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                }
            }

            // 仅对正下方 1 格进行垫脚，不再一直向下
            BlockPos groundPos = pos.down();
            if (world.isInBuildLimit(groundPos)) {
                BlockState groundState = world.getBlockState(groundPos);
                if (groundState.isAir() || !groundState.getFluidState().isEmpty() || groundState.isReplaceable()) {
                    world.setBlockState(groundPos, Blocks.SMOOTH_STONE.getDefaultState(), 3);
                }
            }
        }

        // 5. 计算形状并放置铁轨
        for (int i = 0; i < cleanPath.size(); i++) {
            BlockPos current = cleanPath.get(i);
            BlockPos prev = (i > 0) ? cleanPath.get(i - 1) : null;
            BlockPos next = (i < cleanPath.size() - 1) ? cleanPath.get(i + 1) : null;

            RailShape shape = computeRailShape(prev, current, next);

            BlockState toPlace;
            if (isCurveShape(shape) || shape.isAscending()) {
                toPlace = Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, shape);
            } else {
                toPlace = configuredRail;
                if (toPlace.contains(Properties.RAIL_SHAPE)) {
                    toPlace = toPlace.with(Properties.RAIL_SHAPE, shape);
                } else if (toPlace.contains(Properties.STRAIGHT_RAIL_SHAPE)) {
                    toPlace = toPlace.with(Properties.STRAIGHT_RAIL_SHAPE, shape);
                } else {
                    toPlace = Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, shape);
                }
            }

            world.setBlockState(current, toPlace, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
    }

    private static RailShape computeRailShape(@Nullable BlockPos prev, BlockPos current, @Nullable BlockPos next) {
        if (prev == null && next == null) {
            return RailShape.NORTH_SOUTH;
        }

        if (prev == null) return computeEndShape(current, next);
        if (next == null) return computeEndShape(current, prev);

        if (next.getY() > current.getY()) {
            Direction dir = getHorizontalStepDirection(current, next);
            RailShape asc = getAscendingShapeFor(dir);
            if (asc != null) return asc;
        } else if (prev.getY() > current.getY()) {
            Direction dir = getHorizontalStepDirection(current, prev);
            RailShape asc = getAscendingShapeFor(dir);
            if (asc != null) return asc;
        }

        Direction d1 = getHorizontalStepDirection(current, prev);
        Direction d2 = getHorizontalStepDirection(current, next);

        if (d1.getAxis() == d2.getAxis()) {
            return (d1.getAxis() == Direction.Axis.X) ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        }

        boolean north = (d1 == Direction.NORTH || d2 == Direction.NORTH);
        boolean south = (d1 == Direction.SOUTH || d2 == Direction.SOUTH);
        boolean east  = (d1 == Direction.EAST  || d2 == Direction.EAST);
        boolean west  = (d1 == Direction.WEST  || d2 == Direction.WEST);

        if (south && east) return RailShape.SOUTH_EAST;
        if (south && west) return RailShape.SOUTH_WEST;
        if (north && west) return RailShape.NORTH_WEST;
        if (north && east) return RailShape.NORTH_EAST;

        return RailShape.NORTH_SOUTH;
    }

    private static RailShape computeEndShape(BlockPos current, BlockPos neighbor) {
        if (neighbor.getY() > current.getY()) {
            Direction dir = getHorizontalStepDirection(current, neighbor);
            RailShape asc = getAscendingShapeFor(dir);
            if (asc != null) return asc;
        }
        Direction dir = getHorizontalStepDirection(current, neighbor);
        return (dir.getAxis() == Direction.Axis.X) ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
    }

    @Nullable
    private static RailShape getAscendingShapeFor(Direction dir) {
        return switch (dir) {
            case EAST -> RailShape.ASCENDING_EAST;
            case WEST -> RailShape.ASCENDING_WEST;
            case NORTH -> RailShape.ASCENDING_NORTH;
            case SOUTH -> RailShape.ASCENDING_SOUTH;
            default -> null;
        };
    }

    private static Direction getHorizontalStepDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
}