package cn.myfrank.stationbuilder.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cn.myfrank.stationbuilder.cee.CEEIntegration;
import cn.myfrank.stationbuilder.cpw.CPWAngle;
import cn.myfrank.stationbuilder.cpw.CPWIntegration;
import cn.myfrank.stationbuilder.create.CreateIntegration;
import cn.myfrank.stationbuilder.mtr.MSDIntegration;
import cn.myfrank.stationbuilder.mtr.MTRIntegration;
import cn.myfrank.stationbuilder.utils.*;
import cn.myfrank.stationbuilder.vanilla.VanillaIntegration;
import cn.myfrank.stationbuilder.items.RailBuilderConfig;
import cn.myfrank.stationbuilder.items.RailBuilderState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.WallShape;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class RailGenerator {
    private static final double EPS = 1e-6;

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static Vec3d toVec3d(BlockPos v) {
        return new Vec3d(v.getX(), v.getY(), v.getZ());
    }

    public enum RailNodeType {
        VANILLA,
        CREATE,
        MTR,
        UNSUPPORTED
    }

    public static boolean isRailNode(ServerWorld world, BlockPos pos, RailNodeType railNodeType) {
        if (railNodeType == RailNodeType.MTR && CommonUtil.isMtrLoaded()) {
            return MTRIntegration.isRailNode(world, pos);
        } else if (railNodeType == RailNodeType.CREATE && CommonUtil.isCreateLoaded()) {
            return CreateIntegration.isRailNode(world, pos);
        } else if (railNodeType == RailNodeType.VANILLA) {
            return VanillaIntegration.isRailNode(world, pos);
        } else {
            return false;
        }
    }

    public static boolean isRailNode(World world, BlockPos pos) {
        if (CommonUtil.isMtrLoaded()) {
            if (MTRIntegration.isRailNode(world, pos)) return true;
        }
        if (CommonUtil.isCreateLoaded()) {
            if (CreateIntegration.isRailNode(world, pos)) return true;
        }
        return VanillaIntegration.isRailNode(world, pos);
    }

    public static RailNodeType getRailNodeType(RailBuilderConfig config) {
        // 优先检查是否为 MTR 铁轨连接器（只要继承自 ItemRailModifier，不论属于哪个模组均可）
        if (CommonUtil.isMtrLoaded() && MTRIntegration.isValidRailType(config.railType)) {
            return RailNodeType.MTR;
        }
        if (CommonUtil.isCreateLoaded() && CreateIntegration.isValidRailType(config.railType)) {
            return RailNodeType.CREATE;
        }
        if (config.railType.getNamespace().equals("minecraft") && config.railType.getPath().contains("rail")) {
            return RailNodeType.VANILLA;
        }
        return RailNodeType.UNSUPPORTED;
    }

    public static void placeRailNode(ServerWorld world, BlockPos pos, PlayerEntity player, RailNodeType railNodeType, RailBuilderConfig config, boolean showInfo) {
        if (railNodeType == RailNodeType.MTR && CommonUtil.isMtrLoaded()){
            if (!MTRIntegration.isRailNode(world, pos)) {
                MTRIntegration.placeRailNode(world, pos, player.getYaw());
            } else {
                if(showInfo) System.out.println("Position is already a rail node: " + pos);
            }
        } else if (railNodeType == RailNodeType.CREATE && CommonUtil.isCreateLoaded()) {
            CreateIntegration.placeRailNode(world, pos, player.getYaw(), config.railType);
        } else if (railNodeType == RailNodeType.VANILLA) {
            VanillaIntegration.placeRailNode(world, pos, player.getYaw(), config.railType);
        } else {
            if(showInfo) System.out.println("Rail node type not supported or mod not loaded: " + railNodeType);
        }
    }

    public static void placeFirstRailNode(ServerWorld world, BlockPos pos, PlayerEntity player, RailNodeType railNodeType, RailBuilderConfig config) {
        placeRailNode(world, pos, player, railNodeType, config, true);
    }

    public static ArrayList<BlockPos> calcRailNodes(BlockPos pos, float yaw, RailBuilderConfig config) {
        final ArrayList<BlockPos> placedPositions = new ArrayList<>();
        Vec3d normal = RailMath.normalFromYaw(yaw);

        int count = config.railCount;
        double spacing = config.railSpacing;

        for (int i = 0; i < count; i++) {
            double offsetIndex = i - (count - 1) / 2.0;
            double t = offsetIndex * spacing;
            Vec3d offset = normal.multiply(t, t, t);
            BlockPos s = RailMath.offsetPos(pos, offset);
            placedPositions.add(s);
        }
        return placedPositions;
    }

    public static ArrayList<BlockPos> placeFirstRailNodes(ServerWorld world, BlockPos pos, PlayerEntity player, RailBuilderConfig config) {
        ArrayList<BlockPos> nodes = calcRailNodes(pos, player.getYaw(), config);
        RailNodeType railNodeType = getRailNodeType(config);
        for (BlockPos p : nodes) {
            placeFirstRailNode(world, p, player, railNodeType, config);
        }
        return nodes;
    }

    @Nullable
    public static ArrayList<BlockPos> buildRails(
            ServerWorld world,
            ArrayList<BlockPos> startPositions,
            BlockPos endPos,
            RailBuilderConfig config,
            PlayerEntity player
    ) {
        RailNodeType railNodeType = getRailNodeType(config);
        if (railNodeType == RailNodeType.UNSUPPORTED) {
            player.sendMessage(Text.translatable("message.stationbuilder.rail_builder.invalid_rail",
                    config.railType.toString()), true);
            return null;
        }

        float yaw = player.getYaw();
        Vec3d normal = RailMath.normalFromYaw(yaw); // 右侧法向量
        int count = config.railCount;
        double spacing = config.railSpacing;

        // Calculate end positions
        ArrayList<BlockPos> endPositions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double offsetIndex = i - (count - 1) / 2.0;
            double t = offsetIndex * spacing;
            Vec3d offset = normal.multiply(t, t, t);
            BlockPos e = RailMath.offsetPos(endPos, offset);
            endPositions.add(e);
        }

        // Adjust positions
        RailMath.adjustPointSequence(startPositions, endPositions);

        if (startPositions.size() != endPositions.size()) {
            // 清除该玩家的轨道建造状态（服务端清除）
            ItemStack stack = player.getMainHandStack();
            RailBuilderState.clear(stack);
            return null;
        }

        // Place BlockNodes
        boolean anySuccess = false;
        for (int i = 0; i < count; i++) {
            BlockPos s = startPositions.get(i);
            BlockPos e = endPositions.get(i);
            if(isRailNode(world, s, railNodeType)) {
                if (s.equals(e)) continue;
                placeRailNode(world, e, player, railNodeType, config, false);
                anySuccess = true;
            } else {
                System.out.println("Start position is not a valid rail node: " + s);
            }
        }

        // Build rails
        TickScheduler.schedule(1, () -> {
            var failToPlaceCatenaryNode = false;
            failToPlaceCatenaryNode = buildRails(startPositions, endPositions, player, world, config);
            if (failToPlaceCatenaryNode) {
                player.sendMessage(Text.translatable("message.stationbuilder.rail_builder.catenary_node_failed"), true);
            }
        });

        if(anySuccess) {
            return endPositions;
        } else {
            return null;
        }
    }

    private static void setBlockIfEmpty(ServerWorld world, BlockPos pos, BlockState state) {
        if (world.getBlockState(pos).isAir() || CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlockState(pos, state, 3);
        }
    }

    private static void drawLine(ServerWorld world, BlockPos a, BlockPos b, Identifier lineBlock) {
        var state = CommonUtil.getBlockState(lineBlock);
        int x1 = a.getX(), y1 = a.getY(), z1 = a.getZ();
        int x2 = b.getX(), y2 = b.getY(), z2 = b.getZ();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        int xs = x1 < x2 ? 1 : -1;
        int ys = y1 < y2 ? 1 : -1;
        int zs = z1 < z2 ? 1 : -1;

        int x = x1;
        int y = y1;
        int z = z1;

        setBlockIfEmpty(world, new BlockPos(x, y, z), state);

        int max = Math.max(dx, Math.max(dy, dz));
        if (max == 0) return;

        for (int i = 1; i <= max; i++) {
            int curX = x1 + Math.round((float)(i * (x2 - x1)) / max);
            int curY = y1 + Math.round((float)(i * (y2 - y1)) / max);
            int curZ = z1 + Math.round((float)(i * (z2 - z1)) / max);
            // 强制采取 6向（曼哈顿）步进移动，杜绝对角线产生不连接的孤立围栏
            while(x != curX || y != curY || z != curZ) {
                if (x != curX) x += xs;
                else if (z != curZ) z += zs;
                else if (y != curY) y += ys;
                setBlockIfEmpty(world, new BlockPos(x, y, z), state);
            }
        }
    }

    private static void buildPillarDown(ServerWorld world, BlockPos topPos, Identifier pillarBlock) {
        var state = CommonUtil.getBlockState(pillarBlock);
        BlockPos pos = topPos;
        while(world.isInBuildLimit(pos) && CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlockState(pos, state, 3);
            pos = pos.offset(Direction.DOWN);
        }
    }

    /**
     * 从 node 方块的中心沿 dir 方向走 distance 格，返回落点所在的方块。
     * 方块中心在 (x + 0.5, z + 0.5)，所以某一轴的步数是
     *   floor(0.5 + distance * |dir|)
     * 而不是 floor(distance * |dir|)。左右两侧使用相同的 |dir| 与 distance，
     * 再按方向取符号，得到的两端严格互为镜像，不会出现一侧多走一格。
     */
    private static BlockPos offsetAlong(BlockPos node, Vec3d dir, double distance) {
        double ax = Math.abs(dir.x);
        double az = Math.abs(dir.z);
        int ox = (int) Math.floor(0.5 + distance * ax + EPS);
        int oz = (int) Math.floor(0.5 + distance * az + EPS);
        int dx = dir.x < 0 ? -ox : ox;
        int dz = dir.z < 0 ? -oz : oz;
        return new BlockPos(node.getX() + dx, node.getY(), node.getZ() + dz);
    }

    /**
     * 多轨龙门架：右端从右轨节点向外走 3 格，左端从左轨节点向外走 3 格。
     * leftNode 是左轨在同一纵向位置的真实节点方块；为 null 时才退回按轨距估算。
     */
    private static void buildGantry(
            ServerWorld world, BlockPos rightNode, @Nullable BlockPos leftNode,
            Vec3d normal, double fallbackSpan, Identifier pillarBlock
    ) {
        BlockPos rightEnd = offsetAlong(rightNode, normal, 3.0);
        BlockPos leftEnd = leftNode != null
                ? offsetAlong(leftNode, normal.multiply(-1.0), 3.0)
                : offsetAlong(rightEnd, normal.multiply(-1.0), fallbackSpan);

        drawLine(world, rightEnd, leftEnd, pillarBlock);
        buildPillarDown(world, rightEnd.down(), pillarBlock);
        buildPillarDown(world, leftEnd.down(), pillarBlock);
    }

    private static BlockPos addVanillaCatenaryNode(
            ServerWorld world, Vec3d center, Vec3d tangent, @Nullable Vec3d leftCenter,
            boolean isRightest, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height, Identifier pillarBlock, Identifier lineBlock
    ) {
        int blockY = (int) Math.floor(center.y);
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));

        if (isRightest) {
            var trussPos = catenaryPos.up();
            Vec3d normal = new Vec3d(-tangent.z, 0, tangent.x).normalize();

            if (trackCount == 1) {
                // 单条轨道只在最右侧放置 L 型支架
                BlockPos rightEnd = offsetAlong(trussPos, normal, 3.0);
                drawLine(world, trussPos, rightEnd, pillarBlock);
                buildPillarDown(world, rightEnd.down(), pillarBlock);
            } else {
                // 多轨龙门架：左端使用左轨在同一 j 的真实节点，而不是 (count-1)*spacing 的估算值
                BlockPos leftNode = leftCenter == null ? null : new BlockPos(
                        (int) Math.floor(leftCenter.x),
                        trussPos.getY(),
                        (int) Math.floor(leftCenter.z)
                );
                buildGantry(world, trussPos, leftNode, normal, (trackCount - 1) * railSpacing, pillarBlock);
            }
        }

        // 每条轨道沿着轨道延伸接触线（蜘蛛网或铁栏杆）
        if (lastCatenaryNode != null) {
            drawLine(world, lastCatenaryNode, catenaryPos, lineBlock);
        }

        return catenaryPos;
    }

    public static void calcBuildingMode(
            CurveData rail, ServerWorld world, RailBuilderConfig config, boolean reverseMath, int segments,
            byte[] upBuildingModes, byte[] downBuildingModes
    ) {

        final int thickBallastHeight = config.ballastMaxThickness;
        final double halfTunnelWidth = config.tunnelWidth / 2.0 + EPS;
        final double halfBridgeWidth = config.bridgeWidth / 2.0 + EPS;

        // Determine building modes for upper and lower attachments
        int i = 0;
        for (var pp = new PointProvider(rail, segments, reverseMath); pp.notExhausted(); pp.next(), i++) {
            var tuple = pp.get();
            Vec3d center = tuple.get(0);
            Vec3d normal = tuple.get(2);

            // Stretch left and right from the center point, and get a bundle of BlockPos
            int blockY = (int) Math.floor(center.y);
            var blockXZs = RailMath.getPositions(center, normal, Math.max(halfTunnelWidth, halfBridgeWidth));

            // Determine building modes
            BuildingMode.Up ubm = BuildingMode.Up.Clear;
            BuildingMode.Down dbm = BuildingMode.Down.Ballast;
            int roofBlocks = 0, roofSolidBlocks = 0;
            int lowBlocks = 0, lowSolidBlocks = 0;

            for (var block: blockXZs) {
                // Calculate distance to center
                int floorBlocks = 0, floorSolidBlocks = 0;
                for (int y = -thickBallastHeight - 1; y <= config.tunnelHeight + 3; y++) {
                    var pos = new BlockPos(block.x(), blockY + y, block.z());
                    if (!world.isInBuildLimit(pos)) continue;
                    var state = world.getBlockState(pos);
                    boolean isRailNode = isRailNode(world, pos);
                    boolean isSoftTransparent = CommonUtil.isSoftTransparent(state);
                    if (y < -1) {
                        // 检查是否需要更厚的路基
                        if (y == -thickBallastHeight - 1) {
                            lowBlocks += 1;
                            if (!isRailNode && !isSoftTransparent) lowSolidBlocks += 1;
                        } else {
                            floorBlocks += 1;
                            if (!isRailNode && !isSoftTransparent) floorSolidBlocks += 1;
                        }
                    } else if (y >= config.tunnelHeight) {
                        roofBlocks++;
                        if (!state.isAir() && !isRailNode && !CommonUtil.isNotLiquidTransparent(state)) {
                            roofSolidBlocks++;
                        }
                    }
                }
                if ((double) floorSolidBlocks / floorBlocks <= 0.7) {
                    dbm = BuildingMode.Down.ThickBallast;
                }
            }
            if ((double) roofSolidBlocks / roofBlocks >= 0.6) {
                ubm = BuildingMode.Up.Tunnel;
            }
            if ((double) lowSolidBlocks / lowBlocks <= 0.6) {
                dbm = BuildingMode.Down.Bridge;
            }
            upBuildingModes[i] = ubm.getValue();
            downBuildingModes[i] = dbm.getValue();
        }
    }

    private static void clearBlock(ServerWorld world, BlockPos pos, boolean includeCatenary) {
        if (!includeCatenary && isCatenary(world, pos)) {
            return;
        }
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
    }

    private static void clearHeights(Vec3d center, Vec3d normal, ServerWorld world, RailBuilderConfig config,
                                     boolean isLeftest, boolean isRightest, boolean clearCatenary) {
        double halfWidth = config.ballastTopWidth / 2.0 + EPS;
        int baseY = (int) Math.floor(center.y);
        if (config.clearFullHeight) {
            var XZs = RailMath.getPositions(center, normal, config.tunnelHeight + 3);
            int height = 0;
            for (var xz : XZs) {
                BlockPos topPos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING,
                        new BlockPos(xz.x(), baseY, xz.z()));
                height = Math.max(height, topPos.getY() - baseY);
            }
            double k = Math.max(1.0, (double) height / config.tunnelHeight);
            height = Math.max(height, config.tunnelHeight);
            for (int y = 0; y <= height; ++y) {
                Vec3d layerCenter = center.add(0, y, 0);
                double halfWidth2 = halfWidth + y / k;
                int blockY = (int) Math.floor(layerCenter.y);
                var blockXZs = RailMath.getPositions(center, normal,
                        isLeftest ? halfWidth2 : halfWidth,
                        isRightest ? halfWidth2 : halfWidth);
                for (var block : blockXZs) {
                    var pos = new BlockPos(block.x(), blockY, block.z());
                    if (!isRailNode(world, pos)) {
                        clearBlock(world, pos, clearCatenary);
                    }
                }
            }
        } else {
            double clearHalfWidth = config.tunnelWidth / 2.0 + EPS;
            for (int y = 0; y <= config.tunnelHeight; ++y) {
                Vec3d layerCenter = center.add(0, y, 0);
                int blockY = (int) Math.floor(layerCenter.y);
                var blockXZs = RailMath.getPositions(center, normal,
                        isLeftest ? clearHalfWidth : halfWidth,
                        isRightest ? clearHalfWidth : halfWidth);
                for(var block: blockXZs) {
                    var pos = new BlockPos(block.x(), blockY, block.z());
                    if (!isRailNode(world, pos)) {
                        clearBlock(world, pos, clearCatenary);
                    }
                }
            }
        }
    }

    private static void buildThickerBallast(
            Vec3d center, Vec3d normal, ServerWorld world, RailBuilderConfig config,
            boolean isLeftest, boolean isRightest
    ) {
        final int ballastHeight = config.ballastMaxThickness;
        double halfTopWidth = config.ballastTopWidth / 2.0 + EPS;
        double halfBottomWidth = config.ballastBottomWidth / 2.0 + EPS;
        for (int y = 0; y < ballastHeight; y++) {
            double halfWidth = lerp(halfTopWidth, halfBottomWidth, (double) y / ballastHeight);
            Vec3d layerCenter = center.add(0, -y - 1, 0);
            int blockY = (int) Math.floor(layerCenter.y);
            var blockXZs = RailMath.getPositions(center, normal,
                    isLeftest? halfWidth: halfTopWidth,
                    isRightest? halfWidth: halfTopWidth);
            for(var block: blockXZs) {
                var pos = new BlockPos(block.x(), blockY, block.z());
                world.setBlockState(pos, CommonUtil.getBlockState(config.ballastBlock), 3);
            }
        }
    }

    private static void buildUpDown(
            Vec3d center, Vec3d normal, ServerWorld world, RailBuilderConfig config, boolean clearCatenary,
            BuildingMode.Up ubm, BuildingMode.Down dbm, boolean isLeftest, boolean isRightest, boolean pillar
    ) {
        double EPS = 1e-6;
        double halfBallastWidth = config.ballastTopWidth / 2.0 + EPS;
        double halfTunnelWidth = config.tunnelWidth / 2.0 + EPS;
        double halfBridgeWidth = config.bridgeWidth / 2.0 + EPS;
        double halfPillarWidth = 1.5;
        int blockY = (int) Math.floor(center.y);
        ArrayList<BlockPos> overpass_walls = new ArrayList<>();
        if (dbm == BuildingMode.Down.Bridge) {
            var bridgeXZs = RailMath.getPositions(center, normal, halfBridgeWidth);
            for (int i = 0; i < bridgeXZs.size(); i++) {
                var block = bridgeXZs.get(i);
                int x = block.x(), z = block.z();
                var bridgeBlockState = CommonUtil.getBlockState(config.bridgeBlock);
                if ((isLeftest && i == 0) || (isRightest && i == bridgeXZs.size() - 1)) {
                    var pos = new BlockPos(x, blockY - 1, z);
                    if (!isRailNode(world, pos)) {
                        world.setBlockState(pos, bridgeBlockState, 3);
                    }
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        world.setBlockState(posU, CommonUtil.getBlockState(config.bridgeGuardRailBlock), 3);
                        overpass_walls.add(posU);
                    }
                } else {
                    var posD = new BlockPos(x, blockY - 2, z);
                    world.setBlockState(posD, bridgeBlockState, 3);
                    var pos = new BlockPos(x, blockY - 1, z);
                    world.setBlockState(pos, CommonUtil.getBlockState(config.ballastBlock), 3);
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        clearBlock(world, posU, clearCatenary);
                    }
                }
                if (pillar) {
                    Vec3d p = new Vec3d(x + 0.5, blockY + 0.5, z + 0.5);
                    double dist = Math.abs((p.x - center.x) * normal.x + (p.z - center.z) * normal.z);
                    if (dist <= halfPillarWidth) {
                        var pos = new BlockPos(x, blockY - 2, z);
                        int solidCount = 0;
                        while(solidCount < 3 && world.isInBuildLimit(pos)) {
                            if (CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
                                world.setBlockState(pos, CommonUtil.getBlockState(config.bridgePillarBlock), 3);
                                solidCount = 0;
                            } else {
                                solidCount++;
                            }
                            pos = pos.offset(Direction.DOWN);
                        }
                    }
                }
            }
        }

        if (ubm == BuildingMode.Up.Tunnel) {
            var tunnelXZs = RailMath.getPositions(center, normal, halfTunnelWidth);
            for (int i = 0; i < tunnelXZs.size(); i++) {
                var block = tunnelXZs.get(i);
                int x = block.x(), z = block.z();
                var posTop = new BlockPos(x, blockY + config.tunnelHeight, z);
                if (!isRailNode(world, posTop)) {
                    world.setBlockState(posTop, CommonUtil.getBlockState(config.tunnelWallBlock), 3);
                }
                var posBottom = new BlockPos(x, blockY - 1, z);
                if (!isRailNode(world, posBottom)) {
                    world.setBlockState(posBottom, CommonUtil.getBlockState(config.ballastBlock), 3);
                }
                if ((isLeftest && i == 0) || (isRightest && i == tunnelXZs.size() - 1)) {
                    for(int y = 0; y < config.tunnelHeight; y++) {
                        world.setBlockState(new BlockPos(x, blockY + y, z),
                                CommonUtil.getBlockState(config.tunnelWallBlock), 3);
                    }
                } else {
                    for(int y = 0; y < config.tunnelHeight; y++) {
                        BlockPos airPos = new BlockPos(x, blockY + y, z);
                        if (!isRailNode(world, airPos)) {
                            clearBlock(world, airPos, clearCatenary);
                        }
                    }
                }
            }
        }
        if (ubm != BuildingMode.Up.Tunnel && dbm == BuildingMode.Down.Ballast) {
            var ballastXZs = RailMath.getPositions(center, normal, halfBallastWidth);
            for (var block : ballastXZs) {
                int x = block.x(), z = block.z();
                var pos = new BlockPos(x, blockY - 1, z);
                if (!isRailNode(world, pos)) {
                    world.setBlockState(pos, CommonUtil.getBlockState(config.ballastBlock), 3);
                }
            }
        }
        for(var pos: overpass_walls) {
            var state = world.getBlockState(pos);
            if (state.getBlock() instanceof WallBlock) {
                if (world.getBlockState(pos.offset(Direction.NORTH)).getBlock() instanceof WallBlock) {
                    state = state.with(Properties.NORTH_WALL_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.SOUTH)).getBlock() instanceof WallBlock) {
                    state = state.with(Properties.SOUTH_WALL_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.EAST)).getBlock() instanceof WallBlock) {
                    state = state.with(Properties.EAST_WALL_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.WEST)).getBlock() instanceof WallBlock) {
                    state = state.with(Properties.WEST_WALL_SHAPE, WallShape.LOW);
                }
                world.setBlockState(pos, state, 3 | 16);
            }
        }
    }

    // >0: R is on left side of vector AB, <0 R is on right side of vector AB
    public static double getSide(Vec3d a, Vec3d b, Vec3d r) {
        Vec3d AB = b.subtract(a), AR = r.subtract(a);
        return AB.x * AR.z - AB.z * AR.x;
    }

    public static boolean buildRails(
            ArrayList<BlockPos> startPositions, ArrayList<BlockPos> endPositions,
            PlayerEntity player, ServerWorld world, RailBuilderConfig config) {
        int count = startPositions.size();
        UUID uuid = player.getUuid();
        RailNodeType nodeType = getRailNodeType(config);
        assert endPositions.size() == count;

        // Connect rails
        CurveData[] rails = new CurveData[count];
        double maxLength = -1.0;
        for (int i = 0; i < count; i++) {
            var pos1 = startPositions.get(i);
            var pos2 = endPositions.get(i);
            if (i < count / 2) {
                var temp = pos1;
                pos1 = pos2;
                pos2 = temp;
            }
            if (nodeType == RailNodeType.MTR) {
                rails[i] = MTRIntegration.connectRailNodes(uuid, world, pos1, pos2, config.railType);
            } else if (nodeType == RailNodeType.CREATE) {
                rails[i] = CreateIntegration.connectRailNodes(player, world, pos1, pos2, config.railType);
            } else {
                rails[i] = VanillaIntegration.preconnectRailNodes(player, world, pos1, pos2);
            }
            if (rails[i] != null) {
                maxLength = Math.max(maxLength, rails[i].getLength());
            }
        }

        if (maxLength < 0) return false; // No rail created.
        int segments = (int) Math.floor(maxLength / 0.5);

        // First round: building modes detection
        byte[][] ubm2 = new byte[count][segments + 1];
        byte[][] dbm2 = new byte[count][segments + 1];
        boolean[] reverse = new boolean[count];
        boolean[] pillar = new boolean[segments + 1];
        pillar[0] = pillar[segments] = true;

        int pillarSegCount = (int) Math.ceil(maxLength / config.bridgeClearSpan);
        double pillarSpacing = segments / (double) pillarSegCount;
        for (int i = 0; i < pillarSegCount; i++) {
            pillar[(int)Math.floor(i * pillarSpacing)] = true;
        }

        int catenarySegCount = (int) Math.ceil(maxLength / config.catenarySpacing);
        double catenarySpacing = segments / (double) catenarySegCount;

        for (int i = 0; i < count; i++) {
            var rail = rails[i];
            if (rail == null) continue;

            var pa = toVec3d(startPositions.get(i));
            var pb = toVec3d(endPositions.get(i));
            var p1 = rail.getPosition(0);
            reverse[i] = p1.distanceTo(pa) > p1.distanceTo(pb);
            calcBuildingMode(rail, world, config, reverse[i], segments, ubm2[i], dbm2[i]);
        }
        var ubm = BuildingMode.smoothModes(ubm2);
        var dbm = BuildingMode.smoothModes(dbm2);

        // Second round: Build tunnel, bridge, pillar, thick ballast, catenary

        // Create PointProviders, and check left and right side
        PointProvider[] pps = new PointProvider[count];
        boolean[] isLeftest = new boolean[count], isRightest = new boolean[count];
        for (int i = 0; i < count; i++) {
            var rail = rails[i];
            if (rail == null) continue;
            pps[i] = new PointProvider(rail, segments, reverse[i]);
            if (count == 1) {
                isLeftest[i] = true; isRightest[i] = true;
            } else { // count > 1
                if (i == 0 || i == count - 1) {
                    var a = startPositions.get(i);
                    var b = endPositions.get(i);
                    BlockPos r = null;
                    if (i == 0) {
                        if (rails[i + 1] == null) {
                            isLeftest[i] = true; isRightest[i] = true;
                        } else {
                            r = startPositions.get(i + 1);
                        }
                    } else { // i == count - 1
                        if (rails[i - 1] == null) {
                            isLeftest[i] = true; isRightest[i] = true;
                        } else {
                            r = startPositions.get(i - 1);
                        }
                    }
                    if (r != null) {
                        isRightest[i] = getSide(toVec3d(a), toVec3d(b), toVec3d(r)) < 0;
                        isLeftest[i] = !isRightest[i];
                    }
                }
            }
        }

        // 多轨龙门架需要左轨在同一纵向位置的真实节点，而不是用 (count-1)*railSpacing 估算。
        int leftmostIndex = -1;
        for (int i = 0; i < count; i++) {
            if (pps[i] != null && isLeftest[i]) {
                leftmostIndex = i;
                break;
            }
        }

        // First pass: Clear heights according to building modes
        for (int j = 0; j <= segments; j++) {
            for(int i = 0; i < count; i++) {
                if (rails[i] == null) continue;
                var tuple = pps[i].get(j);
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                var center = tuple.get(0);
                var normal = tuple.get(2);
                if (thisUbm == BuildingMode.Up.Clear) {
                    clearHeights(center, normal, world, config, isLeftest[i], isRightest[i], j > 1);
                }
            }
        }

        // Second pass: Build structures
        for (int j = 0; j <= segments; j++) {
            for(int i = 0; i < count; i++) {
                if (rails[i] == null) continue;
                var tuple = pps[i].get(j);
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                var thisDbm = BuildingMode.Down.fromValue(dbm[j]);
                var center = tuple.get(0);
                var normal = tuple.get(2);
                buildUpDown(center, normal, world, config, j > 1,
                        thisUbm, thisDbm, isLeftest[i], isRightest[i], pillar[j]);
                if (thisDbm == BuildingMode.Down.ThickBallast) {
                    buildThickerBallast(center, normal, world, config, isLeftest[i], isRightest[i]);
                }
            }
        }

        if (nodeType == RailNodeType.VANILLA) {
            for(int i = 0; i < count; i++) {
                if (rails[i] == null) continue;
                var pos1 = startPositions.get(i);
                var pos2 = endPositions.get(i);
                if (i < count / 2) {
                    var temp = pos1;
                    pos1 = pos2;
                    pos2 = temp;
                }
                VanillaIntegration.connectRailNodes(world, rails[i], config.railType, config.ballastBlock, pos1, pos2);
            }
        }

        // Build catenary
        boolean failToPlaceCatenaryNode = false;

        // CPW 多股道并行走 headspan 模式；1~2 股道仍走 addCPWCatenaryNode 的侧柱 + 腕臂。
        if (count > 2
                && config.catenaryTypeIndex == CatenaryType.CPW.getValue()
                && CommonUtil.isCpwLoaded() && CommonUtil.isCreateLoaded()) {
            buildCpwHeadspanCatenary(
                    world, player, config, pps, count, segments, catenarySegCount,
                    catenarySpacing, ubm, isRightest, leftmostIndex
            );
            return failToPlaceCatenaryNode;
        }

        for(int i = 0; i < count; i++) {
            BlockPos lastCatenaryNode = null;
            BuildingMode.Up lastUbm = null;
            var rail = rails[i];
            if (rail == null) continue;
            PointProvider pp = new PointProvider(rail, segments, reverse[i]);
            for (int k = 0; k <= catenarySegCount; k++) {
                int j = k < catenarySegCount ? (int)Math.floor(k * catenarySpacing) : segments;
                var tuple = pp.get(j);
                var center = tuple.get(0);
                var tangent = tuple.get(1);
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                Vec3d leftCenter = (count > 1 && isRightest[i] && leftmostIndex >= 0)
                        ? pps[leftmostIndex].get(j).get(0)
                        : null;
                if (config.catenaryTypeIndex == CatenaryType.Vanilla.getValue()) {
                    lastCatenaryNode = addVanillaCatenaryNode(world, center, tangent, leftCenter,
                            isRightest[i], count, config.railSpacing,
                            lastCatenaryNode, config.tunnelHeight - 1,
                            thisUbm == BuildingMode.Up.Tunnel ? config.catenaryTunnelPillar : config.catenaryBridgePillar,
                            config.catenaryBlock);
                } else if (config.catenaryTypeIndex == CatenaryType.MSD.getValue() && CommonUtil.isMtrLoaded() && CommonUtil.isMsdLoaded()) {
                    if (isLeftest[i] || isRightest[i]) {
                        CatenaryTypeMapping type = CatenaryTypeMapping.values()[config.msdModeIndex];
                        Identifier pillarBlock = thisUbm == BuildingMode.Up.Tunnel ? config.catenaryTunnelPillar : config.catenaryBridgePillar;

                        if (type == CatenaryTypeMapping.Auto) {
                            if (thisUbm == BuildingMode.Up.Tunnel) {
                                pillarBlock = Identifier.of("msd", "rigid_catenary_node");
                            } else {
                                pillarBlock = Identifier.of("msd", "catenary_with_long");
                            }

                            if (lastUbm != null) {
                                if (lastUbm == BuildingMode.Up.Tunnel && thisUbm == BuildingMode.Up.Tunnel) {
                                    type = CatenaryTypeMapping.MSDRigidCatenary;
                                } else if (lastUbm == BuildingMode.Up.Clear && thisUbm == BuildingMode.Up.Clear) {
                                    type = CatenaryTypeMapping.MSDCatenary;
                                } else {
                                    type = CatenaryTypeMapping.MSDRigidSoftCatenary;
                                }
                            }
                        }

                        lastCatenaryNode = MTRIntegration.addCatenaryNode(world, center, tangent, isLeftest[i], isRightest[i],
                                lastCatenaryNode, pillarBlock, type, config.tunnelHeight - 1);
                        if (lastCatenaryNode == null) {
                            failToPlaceCatenaryNode = true;
                        }
                        lastUbm = thisUbm;
                    }
                } else if (config.catenaryTypeIndex == CatenaryType.CEE.getValue() && CommonUtil.isCreateLoaded() && CommonUtil.isCeeLoaded()) {
                    lastCatenaryNode = addCEECatenaryNode(
                            world, center, tangent, leftCenter, isLeftest[i], isRightest[i],
                            i, count, config.railSpacing,
                            lastCatenaryNode, config.tunnelHeight - 1,
                            config.catenaryBridgePillar, config.catenaryTunnelPillar, config.catenaryBlock,
                            player
                    );
                } else if (config.catenaryTypeIndex == CatenaryType.CPW.getValue() && CommonUtil.isCreateLoaded() && CommonUtil.isCpwLoaded()) {
                    lastCatenaryNode = addCPWCatenaryNode(
                            world, center, tangent, isLeftest[i], isRightest[i],
                            i, count, config.railSpacing,
                            lastCatenaryNode, config.tunnelHeight - 1,
                            config.catenaryBridgePillar, config.catenaryTunnelPillar, config.catenaryBlock,
                            player, thisUbm == BuildingMode.Up.Tunnel
                    );
                }
            }
        }
        return failToPlaceCatenaryNode;
    }

    private static BlockPos addCEECatenaryNode(
            ServerWorld world, Vec3d center, Vec3d tangent, @Nullable Vec3d leftCenter,
            boolean isLeftest, boolean isRightest,
            int trackIndex, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height,
            Identifier gantryPillarBlock, Identifier dropperPillarBlock,
            Identifier spoolItem, PlayerEntity player
    ) {
        int blockY = (int) Math.floor(center.y);
        int catenaryY = blockY + height;
        BlockPos catenaryPos = new BlockPos((int) Math.floor(center.x), catenaryY, (int) Math.floor(center.z));

        int trussY = catenaryY + 3;
        Vec3d normal = new Vec3d(-tangent.z, 0, tangent.x).normalize();

        if (trackCount <= 2) {
            BlockPos poleNode = new BlockPos(catenaryPos.getX(), trussY - 1, catenaryPos.getZ());
            // 双轨时左轨在外侧（左侧）立柱
            if (trackCount == 2 && isLeftest) {
                BlockPos poleTop = offsetAlong(poleNode, normal.multiply(-1.0), 3.0);
                buildPillarDown(world, poleTop, dropperPillarBlock);
            }
            // 单轨或双轨时右轨在外侧（右侧）立柱
            if (isRightest) {
                BlockPos poleTop = offsetAlong(poleNode, normal, 3.0);
                buildPillarDown(world, poleTop, dropperPillarBlock);
            }
        } else {
            // 多轨模式：龙门架横梁及两侧主立柱
            if (isRightest) {
                BlockPos rightNode = new BlockPos(catenaryPos.getX(), trussY, catenaryPos.getZ());
                BlockPos leftNode = leftCenter == null ? null : new BlockPos(
                        (int) Math.floor(leftCenter.x),
                        trussY,
                        (int) Math.floor(leftCenter.z)
                );

                buildGantry(world, rightNode, leftNode, normal, (trackCount - 1) * railSpacing, gantryPillarBlock);
            }

            // 仅中间内部轨道生成下垂 3 格的吊柱（底部正好平齐 catenaryY）
            if (!isLeftest && !isRightest) {
                double dropOffset = Math.min(2.5, railSpacing / 2.0);
                boolean dropOnLeft = trackIndex <= (trackCount - 1) / 2;
                Vec3d dropPosVec = dropOnLeft ? center.subtract(normal.multiply(dropOffset)) : center.add(normal.multiply(dropOffset));

                int dropX = (int) Math.floor(dropPosVec.x);
                int dropZ = (int) Math.floor(dropPosVec.z);
                BlockState dropState = CommonUtil.getBlockState(dropperPillarBlock);

                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos dropPos = new BlockPos(dropX, trussY - dy, dropZ);
                    world.setBlockState(dropPos, dropState, Block.NOTIFY_ALL);
                }
            }
        }

        CEEIntegration.placeCatenaryHolder(world, catenaryPos);

        if (lastCatenaryNode != null) {
            CEEIntegration.connectWire(world, player, lastCatenaryNode, catenaryPos, spoolItem);
        }

        return catenaryPos;
    }

    private static BlockPos addCPWCatenaryNode(
            ServerWorld world, Vec3d center, Vec3d tangent, boolean isLeftest, boolean isRightest,
            int trackIndex, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height,
            Identifier mastBlock, Identifier cantileverBlock,
            Identifier wireCoilItem, PlayerEntity player, boolean isTunnel
    ) {
        int blockY = (int) Math.floor(center.y);
        int catenaryY = blockY + height;
        int cantileverY = catenaryY + 2;

        CPWAngle mastAngle = CPWIntegration.getCPWAngles(tangent);
        Vec3d normal = new Vec3d(-tangent.z, 0, tangent.x).normalize();
        Identifier resolvedMast = CPWIntegration.resolveMastId(mastBlock);
        Identifier resolvedCantilever = CPWIntegration.resolveCantileverId(cantileverBlock);

        BlockPos placedCantileverPos = null;

        if (trackCount <= 2) {
            boolean isSideMast = (trackCount == 2 && isLeftest) || isRightest;
            if (isSideMast) {
                boolean onRight = isRightest;
                BlockPos poleNode = new BlockPos((int) Math.floor(center.x), cantileverY, (int) Math.floor(center.z));
                BlockPos pole = offsetAlong(poleNode, onRight ? normal : normal.multiply(-1.0), 3.0);
                int poleX = pole.getX();
                int poleZ = pole.getZ();
                BlockPos poleAtCantileverHeight = new BlockPos(poleX, cantileverY, poleZ);

                if (isTunnel) {
                    int ceilingY = cantileverY + 2;
                    while (ceilingY <= cantileverY + 6
                            && !world.isOutOfHeightLimit(new BlockPos(poleX, ceilingY, poleZ))) {
                        if (!CommonUtil.isSoftTransparent(world.getBlockState(new BlockPos(poleX, ceilingY, poleZ)))) {
                            break;
                        }
                        ceilingY++;
                    }
                    CPWIntegration.ensureFullBlockBelow(world, poleX, cantileverY - 1, poleZ);
                    CPWIntegration.buildMastColumn(
                            world, poleX, poleZ, cantileverY - 1,
                            Math.max(cantileverY, ceilingY - 1), mastAngle, resolvedMast
                    );
                } else {
                    int topY = cantileverY + 1;
                    int bottomY = blockY;
                    BlockPos p = new BlockPos(poleX, blockY, poleZ);
                    while (!world.isOutOfHeightLimit(p) && CommonUtil.isSoftTransparent(world.getBlockState(p))) {
                        bottomY = p.getY();
                        p = p.down();
                    }
                    CPWIntegration.ensureFullBlockBelow(world, poleX, bottomY, poleZ);
                    CPWIntegration.buildMastColumn(world, poleX, poleZ, bottomY, topY, mastAngle, resolvedMast);
                }

                CPWAngle cantileverAngle = onRight ? mastAngle.getCounterClockWise() : mastAngle.getClockWise();
                placedCantileverPos = cantileverAngle.offset(poleAtCantileverHeight);
                CPWIntegration.placeCantilever(world, placedCantileverPos, cantileverAngle, resolvedCantilever);
            }
        }

        if (placedCantileverPos == null) {
            placedCantileverPos = new BlockPos((int) Math.floor(center.x), cantileverY, (int) Math.floor(center.z));
        }

        if (lastCatenaryNode != null) {
            CPWIntegration.connectWire(world, player, lastCatenaryNode, placedCantileverPos, wireCoilItem);
        }

        return placedCantileverPos;
    }

    // ========================================================
    // CPW 多股道 headspan
    // ========================================================

    private static final class CpwHeadspanFrame {
        final BlockPos mastA;
        final BlockPos mastB;
        final float upperHeight;
        final float topHeight;
        final List<CPWIntegration.HeadspanDropper> droppers;
        final UUID[] dropperIds;
        final double[] fractions;

        private CpwHeadspanFrame(
                BlockPos mastA, BlockPos mastB,
                float upperHeight, float topHeight,
                List<CPWIntegration.HeadspanDropper> droppers,
                UUID[] dropperIds, double[] fractions
        ) {
            this.mastA = mastA;
            this.mastB = mastB;
            this.upperHeight = upperHeight;
            this.topHeight = topHeight;
            this.droppers = droppers;
            this.dropperIds = dropperIds;
            this.fractions = fractions;
        }
    }

    private static void buildCpwHeadspanCatenary(
            ServerWorld world, PlayerEntity player, RailBuilderConfig config,
            PointProvider[] pps, int count, int segments, int catenarySegCount,
            double catenarySpacing, byte[] ubm, boolean[] isRightest, int leftmostIndex
    ) {
        if (leftmostIndex < 0) return;

        int rightmostIndex = -1;
        for (int i = 0; i < count; i++) {
            if (pps[i] != null && isRightest[i]) {
                rightmostIndex = i;
                break;
            }
        }
        if (rightmostIndex < 0) return;

        Identifier mastId = CPWIntegration.resolveHeadspanMastId(config.catenaryBridgePillar);
        Identifier cantileverId = CPWIntegration.resolveCantileverId(config.catenaryTunnelPillar);
        int catenaryHeight = config.tunnelHeight - 1;

        List<CpwHeadspanFrame> frames = new ArrayList<>();

        for (int k = 0; k <= catenarySegCount; k++) {
            int j = k < catenarySegCount ? (int) Math.floor(k * catenarySpacing) : segments;
            BuildingMode.Up up = BuildingMode.Up.fromValue(ubm[j]);
            if (up == BuildingMode.Up.Tunnel) {
                continue;
            }

            var leftTuple = pps[leftmostIndex].get(j);
            var rightTuple = pps[rightmostIndex].get(j);
            Vec3d leftCenter = leftTuple.get(0);
            Vec3d rightCenter = rightTuple.get(0);
            Vec3d tangent = rightTuple.get(1);
            Vec3d normal = new Vec3d(-tangent.z, 0, tangent.x).normalize();

            BlockPos leftBase = new BlockPos(
                    (int) Math.floor(leftCenter.x),
                    (int) Math.floor(leftCenter.y),
                    (int) Math.floor(leftCenter.z)
            );
            BlockPos rightBase = new BlockPos(
                    (int) Math.floor(rightCenter.x),
                    (int) Math.floor(rightCenter.y),
                    (int) Math.floor(rightCenter.z)
            );

            BlockPos leftPole = offsetAlong(leftBase, normal.multiply(-1.0), 3.0);
            BlockPos rightPole = offsetAlong(rightBase, normal, 3.0);
            int nodeY = Math.max(leftBase.getY(), rightBase.getY()) + catenaryHeight + 1;

            Vec3d mastAWorld = new Vec3d(leftPole.getX() + 0.5, nodeY + 0.5, leftPole.getZ() + 0.5);
            Vec3d mastBWorld = new Vec3d(rightPole.getX() + 0.5, nodeY + 0.5, rightPole.getZ() + 0.5);
            double spanX = mastBWorld.x - mastAWorld.x;
            double spanZ = mastBWorld.z - mastAWorld.z;
            double spanLength = Math.sqrt(spanX * spanX + spanZ * spanZ);
            if (spanLength < 1.0) continue;

            // UpperWireHeight 取 CPW 允许的最小值 1，TopWireHeight 取最小允许值，
            // 避免顶部承力索下垂过深、看起来像多出来一条斜线。
            float upperHeight = 1.0F;
            float topHeight = (float) (Math.floor(spanLength / 16.0) + upperHeight);
            int mastTopY = (int) Math.ceil(nodeY + 0.5 + upperHeight + topHeight + 0.4) + 1;

            CPWAngle mastAngle = CPWIntegration.getCPWAngles(tangent);
            buildCpwHeadspanMast(world, leftPole, leftBase.getY(), nodeY, mastTopY, mastAngle, mastId);
            buildCpwHeadspanMast(world, rightPole, rightBase.getY(), nodeY, mastTopY, mastAngle, mastId);

            List<CPWIntegration.HeadspanDropper> droppers = new ArrayList<>();
            UUID[] dropperIds = new UUID[count];
            double[] fractions = new double[count];
            double spanLen2 = spanX * spanX + spanZ * spanZ;

            for (int i = 0; i < count; i++) {
                if (pps[i] == null) continue;
                Vec3d center = pps[i].get(j).get(0);
                double f = ((center.x - mastAWorld.x) * spanX + (center.z - mastAWorld.z) * spanZ) / spanLen2;
                f = Math.max(0.05, Math.min(0.95, f));
                UUID id = UUID.randomUUID();
                dropperIds[i] = id;
                fractions[i] = f;
                droppers.add(new CPWIntegration.HeadspanDropper(id, f));
            }

            frames.add(new CpwHeadspanFrame(
                    new BlockPos(leftPole.getX(), nodeY, leftPole.getZ()),
                    new BlockPos(rightPole.getX(), nodeY, rightPole.getZ()),
                    upperHeight, topHeight, droppers, dropperIds, fractions
            ));
        }

        if (frames.isEmpty()) return;

        TickScheduler.schedule(1, () -> {
            Object[] previousNodes = new Object[count];
            for (CpwHeadspanFrame frame : frames) {
                Object headspan = CPWIntegration.createHeadspanEdge(
                        world, frame.mastA, frame.mastB, frame.upperHeight, frame.topHeight, frame.droppers
                );
                if (headspan == null) {
                    previousNodes = new Object[count];
                    continue;
                }

                Object[] nodes = new Object[count];
                for (int i = 0; i < count; i++) {
                    UUID dropperId = frame.dropperIds[i];
                    if (dropperId == null) continue;

                    if (!CPWIntegration.addHeadspanRegistrationArm(
                            headspan, dropperId, frame.fractions[i], cantileverId
                    )) {
                        continue;
                    }
                    Object node = CPWIntegration.createHeadspanCatenaryNode(headspan, dropperId);
                    if (node == null) continue;
                    nodes[i] = node;
                    if (previousNodes[i] != null) {
                        CPWIntegration.connectHeadspanCatenary(world, previousNodes[i], node);
                    }
                }
                previousNodes = nodes;
            }
        });
    }

    private static void buildCpwHeadspanMast(
            ServerWorld world, BlockPos pole, int blockY, int nodeY, int mastTopY,
            CPWAngle angle, Identifier mastId
    ) {
        int bottomY = blockY;
        BlockPos p = new BlockPos(pole.getX(), blockY, pole.getZ());
        while (!world.isOutOfHeightLimit(p) && CommonUtil.isSoftTransparent(world.getBlockState(p))) {
            bottomY = p.getY();
            p = p.down();
        }
        int topY = Math.max(mastTopY, nodeY + 1);
        CPWIntegration.ensureFullBlockBelow(world, pole.getX(), bottomY, pole.getZ());
        CPWIntegration.buildMastColumn(world, pole.getX(), pole.getZ(), bottomY, topY, angle, mastId);
    }

    public static boolean isCatenary(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (id == null) return false;

        // CEE 节点与杆件
        if (CommonUtil.isCeeLoaded()) {
            if (id.equals(CEEIntegration.CATENARY_HOLDER)
                    || id.getPath().contains("catenary") || id.getPath().contains("pole")) {
                return true;
            }
        }
        // CPW 节点与杆件（建新路段时避免清掉上一段已有的 mast / cantilever）
        if (CommonUtil.isCpwLoaded()) {
            if (id.getNamespace().equals("pantographsandwires")
                    || id.getPath().contains("mast") || id.getPath().contains("cantilever") || id.getPath().contains("pillar")) {
                return true;
            }
        }
        // MSD 节点
        if (CommonUtil.isMsdLoaded() && world instanceof ServerWorld serverWorld) {
            if (MSDIntegration.isCatenaryNode(serverWorld, pos)) {
                return true;
            }
        }
        return false;
    }
}
