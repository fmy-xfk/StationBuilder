package cn.myfrank.stationbuilder.vanilla;

import cn.myfrank.stationbuilder.utils.CommonUtil;
import cn.myfrank.stationbuilder.utils.CurveData;
import cn.myfrank.stationbuilder.utils.TestConnectResult;
import cn.myfrank.stationbuilder.vanilla.VanillaCurveData.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VanillaIntegration {
    public static boolean isRailNode(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof BaseRailBlock;
    }

    @Nullable
    public static RailShape getShapeFromState(BlockState state) {
        if (state.hasProperty(BlockStateProperties.RAIL_SHAPE)) {
            return state.getValue(BlockStateProperties.RAIL_SHAPE);
        } else if (state.hasProperty(BlockStateProperties.RAIL_SHAPE_STRAIGHT)) {
            return state.getValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT);
        }
        return null;
    }

    public static boolean isStraightRailNode(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BaseRailBlock)) return false;
        RailShape shape = getShapeFromState(state);
        return shape != null && (shape == RailShape.NORTH_SOUTH || shape == RailShape.EAST_WEST);
    }

    public static float getAngle(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        RailShape shape = getShapeFromState(state);
        if (shape == RailShape.NORTH_SOUTH) return 90.0f;
        if (shape == RailShape.EAST_WEST) return 0.0f;
        return -1.0f;
    }

    public static float parseAngle(float playerYaw) {
        Direction direction = Direction.fromYRot(playerYaw);
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
    public static void ensurePlacementConditions(ServerLevel world, BlockPos pos) {
        // 1. 确保自身及上方净空
        for (int y = 0; y <= 2; y++) {
            BlockPos checkPos = pos.above(y);
            BlockState state = world.getBlockState(checkPos);
            if (!state.isAir()) {
                world.setBlock(checkPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }

        // 2. 确保正下方1格有方块支撑（若为空气、液体或可替换杂物，则补齐方块）
        BlockPos below = pos.below();
        if (world.isInWorldBounds(below)) {
            BlockState belowState = world.getBlockState(below);
            if (belowState.isAir() || !belowState.getFluidState().isEmpty() || belowState.canBeReplaced()) {
                world.setBlock(below, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    /**
     * 根据配置的铁轨类型放置指定朝向的直轨（支持动力铁轨等各类特殊铁轨）
     */
    public static void placeRailNode(ServerLevel world, BlockPos pos, float angle, ResourceLocation railType) {
        ensurePlacementConditions(world, pos);

        Direction dir = Direction.fromYRot(angle);
        RailShape shape = (dir.getAxis() == Direction.Axis.X) ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        
        BlockState configuredRail = CommonUtil.getBlockState(railType);
        if (configuredRail.isAir() || !(configuredRail.getBlock() instanceof BaseRailBlock)) {
            configuredRail = Blocks.RAIL.defaultBlockState();
        }

        BlockState state = configuredRail;

        if (state.hasProperty(BlockStateProperties.RAIL_SHAPE_STRAIGHT)) {
            state = state.setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, shape);
        } else if (state.hasProperty(BlockStateProperties.RAIL_SHAPE)) {
            state = state.setValue(BlockStateProperties.RAIL_SHAPE, shape);
        } else {
            state = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape);
        }

        // 如果你的世界变量名叫 level，请把 world 改为 level
        world.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    public static TestConnectResult testConnectRailNodes(BlockPos startPos, float parsedStartAngle, BlockPos endPos, float parsedEndAngle) {
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
            ArrayList<Vec3> points = new ArrayList<>();
            for(double s = 0; s <= math.getLength(); s += 0.1) {
                points.add(math.getPosition(s, false));
            }
            return new TestConnectResult(true, radius, math.getLength(), points);
        }
        return new TestConnectResult(false, 0, 0, new ArrayList<>());
    }

    public static CurveData preconnectRailNodes(Player player, ServerLevel world, BlockPos a, BlockPos b) {
        if (!isStraightRailNode(world, a) || !isStraightRailNode(world, b)) {
            return null;
        }

        var angles = VanillaCurveData.getAngles(
            a, getAngle(world, a),
            b, getAngle(world, b)
        );
        var facingStart = angles.left();
        var facingEnd   = angles.right();

        VanillaCurveData curve = new VanillaCurveData(a, facingStart, b, facingEnd, Shape.QUADRATIC, 0.0);
        if (!curve.isValid()) {
            return null;
        }
        return curve;
    }

    public static void connectRailNodes(ServerLevel world, CurveData curve, ResourceLocation railType, ResourceLocation ballastBlock, BlockPos startPos, BlockPos endPos) {
        BlockState configuredRail = CommonUtil.getBlockState(railType);
        BlockState configuredBallast = CommonUtil.getBlockState(ballastBlock);
        if (configuredRail.isAir() || !(configuredRail.getBlock() instanceof BaseRailBlock)) {
            configuredRail = Blocks.RAIL.defaultBlockState();
        }

        double totalLen = curve.getLength();
        if (totalLen < 0.5) return;

        int startX = startPos.getX(), startY = startPos.getY(), startZ = startPos.getZ();
        int endX = endPos.getX(), endY = endPos.getY(), endZ = endPos.getZ();

        // 1. 密集均匀采样三维空间曲线点
        double sampleInterval = 0.25;
        int sampleCount = Math.max(10, (int) Math.ceil(totalLen / sampleInterval));
        List<Vec3> samples = new ArrayList<>();
        for (int i = 0; i <= sampleCount; i++) {
            samples.add(curve.getPosition((totalLen * i) / sampleCount));
        }

        // 2. 水平曼哈顿离散走线（严格锁定起点和终点，不越界）
        List<BlockPos> hPath = new ArrayList<>();
        int curX = startX;
        int curZ = startZ;
        hPath.add(new BlockPos(curX, 0, curZ));

        for (int i = 1; i < samples.size(); i++) {
            Vec3 pt = samples.get(i);
            int targetX = (i == samples.size() - 1) ? endX : (int) Math.floor(pt.x);
            int targetZ = (i == samples.size() - 1) ? endZ : (int) Math.floor(pt.z);

            while (curX != targetX || curZ != targetZ) {
                int dx = targetX - curX;
                int dz = targetZ - curZ;

                if (Math.abs(dx) >= Math.abs(dz) && dx != 0) {
                    curX += Integer.signum(dx);
                } else if (dz != 0) {
                    curZ += Integer.signum(dz);
                }
                hPath.add(new BlockPos(curX, 0, curZ));
            }
        }

        // 消除微小折返锯齿
        List<BlockPos> cleanHPath = new ArrayList<>();
        for (BlockPos p : hPath) {
            if (cleanHPath.isEmpty() || !cleanHPath.get(cleanHPath.size() - 1).equals(p)) {
                if (cleanHPath.size() >= 2 && cleanHPath.get(cleanHPath.size() - 2).equals(p)) {
                    cleanHPath.remove(cleanHPath.size() - 1);
                } else {
                    cleanHPath.add(p);
                }
            }
        }

        int n = cleanHPath.size();
        if (n < 2) return;

        // 3. 拐弯判定
        boolean[] isCorner = new boolean[n];
        for (int i = 1; i < n - 1; i++) {
            BlockPos pPrev = cleanHPath.get(i - 1);
            BlockPos pCurr = cleanHPath.get(i);
            BlockPos pNext = cleanHPath.get(i + 1);
            int dx1 = pCurr.getX() - pPrev.getX();
            int dz1 = pCurr.getZ() - pPrev.getZ();
            int dx2 = pNext.getX() - pCurr.getX();
            int dz2 = pNext.getZ() - pCurr.getZ();
            if (dx1 != dx2 || dz1 != dz2) {
                isCorner[i] = true;
            }
        }

        // 4. 自然曲线高度贴合（彻底解决“中途抬升过早”问题）
        int[] yPlan = new int[n];
        yPlan[0] = startY;
        yPlan[n - 1] = endY;

        // 直接取样曲线的真实几何高度（严禁机械的线性插值）
        for (int i = 1; i < n - 1; i++) {
            double progress = (double) i / (n - 1);
            int sampleIdx = (int) Math.round(progress * (samples.size() - 1));
            yPlan[i] = (int) Math.floor(samples.get(sampleIdx).y);
        }

        // 保证起点端点水平缓冲，避免在端点处立刻形成坡道
        if (n > 1) yPlan[1] = startY;
        if (n > 2) yPlan[n - 2] = endY;

        // 正向扫描：让台阶平滑跟随机械曲线的真实趋势
        int currentY = startY;
        for (int i = 0; i < n - 2; i++) {
            if (i <= 1 || isCorner[i]) {
                yPlan[i] = currentY;
                continue;
            }
            int targetY = yPlan[i];
            // 只有当曲线真实高度已经上升/下降时才步进，绝不提前抬升
            if (currentY != targetY) {
                currentY += Integer.signum(targetY - currentY);
            }
            yPlan[i] = currentY;
        }

        // 反向扫描回溯保证平滑对齐 endY
        currentY = endY;
        for (int i = n - 1; i >= 1; i--) {
            if (i >= n - 2 || isCorner[i]) {
                yPlan[i] = currentY;
                continue;
            }
            if (Math.abs(yPlan[i] - currentY) > 1) {
                yPlan[i] = currentY + Integer.signum(yPlan[i] - currentY);
            }
            if (i < n - 1 && Math.abs(yPlan[i] - yPlan[i + 1]) > 1) {
                yPlan[i] = yPlan[i + 1] + Integer.signum(yPlan[i] - yPlan[i + 1]);
            }
            currentY = yPlan[i];
        }

        // 5. 组合为最终三维路径
        List<BlockPos> finalPath = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BlockPos hp = cleanHPath.get(i);
            finalPath.add(new BlockPos(hp.getX(), yPlan[i], hp.getZ()));
        }
        finalPath.set(0, startPos);
        finalPath.set(n - 1, endPos);

        // 6. 第一阶段：空间深度净空与道砟铺设
        for (int i = 0; i < n; i++) {
            BlockPos pos = finalPath.get(i);
            boolean isTerminal = (i == 0 || i == n - 1);

            // 清理头顶通行空间
            int startClearY = isTerminal ? 1 : 0;
            for (int dy = startClearY; dy <= 2; dy++) {
                BlockPos airPos = pos.above(dy);
                if (!world.getBlockState(airPos).isAir()) {
                    world.setBlock(airPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }

            // 正下方垫起支撑道砟
            BlockPos below = pos.below();
            BlockState belowState = world.getBlockState(below);
            if (belowState.isAir() || !belowState.getFluidState().isEmpty() || belowState.canBeReplaced()) {
                world.setBlock(below, configuredBallast, 3);
            }

            // 坡道台阶背靠立面支撑
            BlockPos next = (i < n - 1) ? finalPath.get(i + 1) : null;
            if (next != null && next.getY() > pos.getY()) {
                world.setBlock(next.below(), configuredBallast, 3);
            }
            BlockPos prev = (i > 0) ? finalPath.get(i - 1) : null;
            if (prev != null && prev.getY() > pos.getY()) {
                world.setBlock(prev.below(), configuredBallast, 3);
            }
        }

        // 7. 第二阶段：铺设整条铁路（包含中间连接轨）
        for (int i = 1; i < n - 1; i++) {
            BlockPos current = finalPath.get(i);
            BlockPos prev = finalPath.get(i - 1);
            BlockPos next = finalPath.get(i + 1);

            RailShape shape = computeRailShape(prev, current, next);

            BlockState toPlace;
            if (isCurveShape(shape)) {
                toPlace = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape);
            } else {
                toPlace = configuredRail;
                if (toPlace.hasProperty(BlockStateProperties.RAIL_SHAPE_STRAIGHT)) {
                    toPlace = toPlace.setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, shape);
                } else if (toPlace.hasProperty(BlockStateProperties.RAIL_SHAPE)) {
                    toPlace = toPlace.setValue(BlockStateProperties.RAIL_SHAPE, shape);
                } else {
                    toPlace = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape);
                }
            }

            world.setBlock(current, toPlace, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        // 8. 终极保证：强制重新补放起点和终点端点（杜绝任何脱落或缺失）
        reinstallTerminalRail(world, startPos, finalPath.get(1), configuredRail);
        reinstallTerminalRail(world, endPos, finalPath.get(n - 2), configuredRail);
    }

    /**
     * 强制重新放置端点直轨，确保端点朝向完美且绝不消失
     */
    private static void reinstallTerminalRail(ServerLevel world, BlockPos terminalPos, BlockPos neighborPos, BlockState configuredRail) {
        Direction dir = getHorizontalStepDirection(terminalPos, neighborPos);
        RailShape shape = (dir.getAxis() == Direction.Axis.X) ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;

        BlockState state = configuredRail;
        if (state.hasProperty(BlockStateProperties.RAIL_SHAPE_STRAIGHT)) {
            state = state.setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, shape);
        } else if (state.hasProperty(BlockStateProperties.RAIL_SHAPE)) {
            state = state.setValue(BlockStateProperties.RAIL_SHAPE, shape);
        } else {
            state = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape);
        }

        // 确保端点下方有支撑，上方空气净空
        ensurePlacementConditions(world, terminalPos);
        world.setBlock(terminalPos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static RailShape computeRailShape(@Nullable BlockPos prev, BlockPos current, @Nullable BlockPos next) {
        if (prev == null && next == null) {
            return RailShape.NORTH_SOUTH;
        }

        // 核心：优先判断坡道形态（原版坡道位于低处方块，指向高处相邻方块）
        if (next != null && next.getY() > current.getY()) {
            Direction dir = getHorizontalStepDirection(current, next);
            RailShape asc = getAscendingShapeFor(dir);
            if (asc != null) return asc;
        } else if (prev != null && prev.getY() > current.getY()) {
            Direction dir = getHorizontalStepDirection(current, prev);
            RailShape asc = getAscendingShapeFor(dir);
            if (asc != null) return asc;
        }

        if (prev == null) return computeEndShape(current, next);
        if (next == null) return computeEndShape(current, prev);

        // 水平连通方向判断
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