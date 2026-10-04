package cn.myfrank.stationbuilder.generator;

import java.util.ArrayList;
import java.util.UUID;

import cn.myfrank.stationbuilder.utils.*;
import cn.myfrank.stationbuilder.create.CreateIntegration;
import cn.myfrank.stationbuilder.items.RailBuilderConfig;
import cn.myfrank.stationbuilder.items.RailBuilderState;
import cn.myfrank.stationbuilder.mtr.MTRIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;


public class RailGenerator {
    private static final double EPS = 1e-6;

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static Vec3 toVec3d(BlockPos v) {
        return new Vec3(v.getX(), v.getY(), v.getZ());
    }

    public enum RailNodeType {
        VANILLA,
        CREATE,
        MTR,
        UNSUPPORTED
    }

    public static boolean isRailNode(ServerLevel world, BlockPos pos, RailNodeType railNodeType) {
        if (railNodeType == RailNodeType.MTR && CommonUtil.isMtrLoaded()) {
            return MTRIntegration.isRailNode(world, pos);
        } else if (railNodeType == RailNodeType.CREATE && CommonUtil.isCreateLoaded()) {
            return CreateIntegration.isRailNode(world, pos);
        } else if (railNodeType == RailNodeType.VANILLA) {
            BlockState state = world.getBlockState(pos);
            return state.is(Blocks.RAIL) || state.is(Blocks.POWERED_RAIL) || state.is(Blocks.DETECTOR_RAIL) || state.is(Blocks.ACTIVATOR_RAIL);
        } else {
            return false;
        }
    }

    public static boolean isRailNode(Level world, BlockPos pos) {
        if (CommonUtil.isMtrLoaded()) {
            if (MTRIntegration.isRailNode(world, pos)) return true;
        }
        if (CommonUtil.isCreateLoaded()) {
            if (CreateIntegration.isRailNode(world, pos)) return true;
        }
        BlockState state = world.getBlockState(pos);
        return (state.is(Blocks.RAIL) || state.is(Blocks.POWERED_RAIL) || state.is(Blocks.DETECTOR_RAIL) || state.is(Blocks.ACTIVATOR_RAIL));
    }

    public static RailNodeType getRailNodeType(RailBuilderConfig config) {
        switch (config.railType.getNamespace()) {
            case "mtr" -> {
                if (CommonUtil.isMtrLoaded()) {
                    if (MTRIntegration.isValidRailType(config.railType)) {
                        return RailNodeType.MTR;
                    } else {
                        return RailNodeType.UNSUPPORTED;
                    }
                } else {
                    return RailNodeType.UNSUPPORTED;
                }
            }
            case "create" -> {
                if (config.railType.getPath().equals("track")) {
                    return RailNodeType.CREATE;
                } else {
                    return RailNodeType.UNSUPPORTED;
                }
            }
            default -> {
                if (config.railType.getNamespace().equals("minecraft") && config.railType.getPath().contains("rail")) {
                    return RailNodeType.VANILLA;
                } else {
                    return RailNodeType.UNSUPPORTED;
                }
            }
        }
    }

    public static void placeRailNode(ServerLevel world, BlockPos pos, Player player, RailNodeType railNodeType, boolean showInfo) {
        if (railNodeType == RailNodeType.MTR && CommonUtil.isMtrLoaded()){
            if (!MTRIntegration.isRailNode(world, pos)) {
                MTRIntegration.placeRailNode(world, pos, player.getYRot());
            } else {
                if(showInfo) System.out.println("Position is already a rail node: " + pos);
            }
        } else if (railNodeType == RailNodeType.CREATE && CommonUtil.isCreateLoaded()) {
            CreateIntegration.placeRailNode(world, pos, player.getYRot());
        } else if (railNodeType == RailNodeType.VANILLA) {
            BlockState state = Blocks.RAIL.defaultBlockState();
            state.setValue(BlockStateProperties.RAIL_SHAPE, (int)(player.getYRot() / 90.0f + 0.5f) % 2 == 0 ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST);
        } else {
            if(showInfo) System.out.println("Rail node type not supported or mod not loaded: " + railNodeType);
        }
    }

    public static void placeFirstRailNode(ServerLevel world, BlockPos pos, Player player, RailNodeType railNodeType) {
        placeRailNode(world, pos, player, railNodeType, true);
    }

    public static ArrayList<BlockPos> calcRailNodes(BlockPos pos, float yaw, RailBuilderConfig config) {
        final ArrayList<BlockPos> placedPositions = new ArrayList<>();
        Vec3 normal = RailMath.normalFromYaw(yaw);

        int count = config.railCount;
        double spacing = config.railSpacing;

        for (int i = 0; i < count; i++) {
            double offsetIndex = i - (count - 1) / 2.0;
            double t = offsetIndex * spacing;
            Vec3 offset = normal.multiply(t, t, t);
            BlockPos s = RailMath.offsetPos(pos, offset);
            placedPositions.add(s);
        }
        return placedPositions;
    }

    public static ArrayList<BlockPos> placeFirstRailNodes(ServerLevel world, BlockPos pos, Player player, RailBuilderConfig config) {
        ArrayList<BlockPos> nodes = calcRailNodes(pos, player.getYRot(), config);
        RailNodeType railNodeType = getRailNodeType(config);
        for (BlockPos p : nodes) {
            placeFirstRailNode(world, p, player, railNodeType);
        }
        return nodes;
    }

    @Nullable
    public static ArrayList<BlockPos> buildRails(
            ServerLevel world,
            ArrayList<BlockPos> startPositions,
            BlockPos endPos,
            RailBuilderConfig config,
            Player player
    ) {
        RailNodeType railNodeType = getRailNodeType(config);
        if (railNodeType == RailNodeType.UNSUPPORTED) {
            player.displayClientMessage(Component.translatable("message.stationbuilder.rail_builder.invalid_rail",
                    config.railType.toString()), true);
            return null;
        }

        float yaw = player.getYRot();
        Vec3 normal = RailMath.normalFromYaw(yaw); // 右侧法向量
        int count = config.railCount;
        double spacing = config.railSpacing;

        // Calculate end positions
        ArrayList<BlockPos> endPositions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double offsetIndex = i - (count - 1) / 2.0;
            double t = offsetIndex * spacing;
            Vec3 offset = normal.multiply(t, t, t);
            BlockPos e = RailMath.offsetPos(endPos, offset);
            endPositions.add(e);
        }

        // Adjust positions
        RailMath.adjustPointSequence(startPositions, endPositions);

        if (startPositions.size() != endPositions.size()) {
            // 清除该玩家的轨道建造状态（服务端清除）
            ItemStack stack = player.getMainHandItem();
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
                placeRailNode(world, e, player, railNodeType, false);
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
                player.displayClientMessage(Component.translatable("message.stationbuilder.rail_builder.catenary_node_failed"), true);
            }
        });

        if(anySuccess) {
            return endPositions;
        } else {
            return null;
        }
    }

    private static void setBlockIfEmpty(ServerLevel world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (world.getBlockState(pos).isAir() || CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlock(pos, state, 3);
        }
    }

    private static void drawLine(ServerLevel world, BlockPos a, BlockPos b, ResourceLocation lineBlock) {
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

    private static void buildPillarDown(ServerLevel world, BlockPos topPos, ResourceLocation pillarBlock) {
        var state = CommonUtil.getBlockState(pillarBlock);
        BlockPos pos = topPos;
        while(!world.isOutsideBuildHeight(pos) && CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlock(pos, state, 3);
            pos = pos.relative(Direction.DOWN);
        }
    }

    private static BlockPos addVanillaCatenaryNode(
            ServerLevel world, Vec3 center, Vec3 tangent, boolean isRightest, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height, ResourceLocation pillarBlock, ResourceLocation lineBlock
    ) {
        int blockY = (int) Math.floor(center.y);
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));

        if (isRightest) {
            var trussPos = catenaryPos.above();
            Vec3 normal = new Vec3(-tangent.z, 0, tangent.x).normalize();

            if (trackCount == 1) {
                // 单条轨道只在最右侧放置 L 型支架
                BlockPos rightEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * 3.0),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * 3.0)
                );
                drawLine(world, trussPos, rightEnd, pillarBlock);
                buildPillarDown(world, rightEnd.below(), pillarBlock);
            } else {
                // 多条轨道时，跨越所有轨道总宽画一道拱门
                double leftSpan = -((trackCount - 1) * railSpacing + 3.0) + EPS;
                double rightSpan = 3.0 - EPS;

                BlockPos rightEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * rightSpan),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * rightSpan)
                );
                BlockPos leftEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * leftSpan),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * leftSpan)
                );

                drawLine(world, rightEnd, leftEnd, pillarBlock);
                buildPillarDown(world, rightEnd.below(), pillarBlock);
                buildPillarDown(world, leftEnd.below(), pillarBlock);
            }
        }

        // 每条轨道沿着轨道延伸接触线（蜘蛛网或铁栏杆）
        if (lastCatenaryNode != null) {
            drawLine(world, lastCatenaryNode, catenaryPos, lineBlock);
        }

        return catenaryPos;
    }

    public static void calcBuildingMode(
            CurveData rail, ServerLevel world, RailBuilderConfig config, boolean reverseMath, int segments,
            byte[] upBuildingModes, byte[] downBuildingModes
    ) {

        final int thickBallastHeight = config.ballastMaxThickness;
        final double halfTunnelWidth = config.tunnelWidth / 2.0 + EPS;
        final double halfBridgeWidth = config.bridgeWidth / 2.0 + EPS;

        // Determine building modes for upper and lower attachments
        int i = 0;
        for (var pp = new PointProvider(rail, segments, reverseMath); pp.notExhausted(); pp.next(), i++) {
            var tuple = pp.get();
            Vec3 center = tuple.get(0);
            Vec3 normal = tuple.get(2);

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
                    if (world.isOutsideBuildHeight(pos)) continue;
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

    private static void clearBlock(ServerLevel world, BlockPos pos, boolean includeCatenary) {
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private static void clearHeights(Vec3 center, Vec3 normal, ServerLevel world, RailBuilderConfig config,
                                     boolean isLeftest, boolean isRightest, boolean clearCatenary) {
        double halfWidth = config.ballastTopWidth / 2.0 + EPS;
        int baseY = (int) Math.floor(center.y);
        if (config.clearFullHeight) {
            var XZs = RailMath.getPositions(center, normal, config.tunnelHeight + 3);
            int height = 0;
            for (var xz : XZs) {
                BlockPos topPos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                        new BlockPos(xz.x(), baseY, xz.z()));
                height = Math.max(height, topPos.getY() - baseY);
            }
            double k = Math.max(1.0, (double) height / config.tunnelHeight);
            height = Math.max(height, config.tunnelHeight);
            for (int y = 0; y <= height; ++y) {
                Vec3 layerCenter = center.add(0, y, 0);
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
                Vec3 layerCenter = center.add(0, y, 0);
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
            Vec3 center, Vec3 normal, ServerLevel world, RailBuilderConfig config,
            boolean isLeftest, boolean isRightest
    ) {
        final int ballastHeight = config.ballastMaxThickness;
        double halfTopWidth = config.ballastTopWidth / 2.0 + EPS;
        double halfBottomWidth = config.ballastBottomWidth / 2.0 + EPS;
        for (int y = 0; y < ballastHeight; y++) {
            double halfWidth = lerp(halfTopWidth, halfBottomWidth, (double) y / ballastHeight);
            Vec3 layerCenter = center.add(0, -y - 1, 0);
            int blockY = (int) Math.floor(layerCenter.y);
            var blockXZs = RailMath.getPositions(center, normal,
                    isLeftest? halfWidth: halfTopWidth,
                    isRightest? halfWidth: halfTopWidth);
            for(var block: blockXZs) {
                var pos = new BlockPos(block.x(), blockY, block.z());
                world.setBlock(pos, CommonUtil.getBlockState(config.ballastBlock), 3);
            }
        }
    }

    private static void buildUpDown(
            Vec3 center, Vec3 normal, ServerLevel world, RailBuilderConfig config, boolean clearCatenary,
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
                        world.setBlock(pos, bridgeBlockState, 3);
                    }
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        world.setBlock(posU, CommonUtil.getBlockState(config.bridgeGuardRailBlock), 3);
                        overpass_walls.add(posU);
                    }
                } else {
                    var posD = new BlockPos(x, blockY - 2, z);
                    world.setBlock(posD, bridgeBlockState, 3);
                    var pos = new BlockPos(x, blockY - 1, z);
                    world.setBlock(pos, CommonUtil.getBlockState(config.ballastBlock), 3);
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        clearBlock(world, posU, clearCatenary);
                    }
                }
                if (pillar) {
                    Vec3 p = new Vec3(x + 0.5, blockY + 0.5, z + 0.5);
                    double dist = Math.abs((p.x - center.x) * normal.x + (p.z - center.z) * normal.z);
                    if (dist <= halfPillarWidth) {
                        var pos = new BlockPos(x, blockY - 2, z);
                        int solidCount = 0;
                        while(solidCount < 3 && !world.isOutsideBuildHeight(pos)) {
                            if (CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
                                world.setBlock(pos, CommonUtil.getBlockState(config.bridgePillarBlock), 3);
                                solidCount = 0;
                            } else {
                                solidCount++;
                            }
                            pos = pos.relative(Direction.DOWN);
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
                    world.setBlock(posTop, CommonUtil.getBlockState(config.tunnelWallBlock), 3);
                }
                var posBottom = new BlockPos(x, blockY - 1, z);
                if (!isRailNode(world, posBottom)) {
                    world.setBlock(posBottom, CommonUtil.getBlockState(config.ballastBlock), 3);
                }
                if ((isLeftest && i == 0) || (isRightest && i == tunnelXZs.size() - 1)) {
                    for(int y = 0; y < config.tunnelHeight; y++) {
                        world.setBlock(new BlockPos(x, blockY + y, z),
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
                    world.setBlock(pos, CommonUtil.getBlockState(config.ballastBlock), 3);
                }
            }
        }
        for(var pos: overpass_walls) {
            var state = world.getBlockState(pos);
            if (state.getBlock() instanceof WallBlock) {
                if (world.getBlockState(pos.relative(Direction.NORTH)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.NORTH_WALL, WallSide.LOW);
                }
                if (world.getBlockState(pos.relative(Direction.SOUTH)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.SOUTH_WALL, WallSide.LOW);
                }
                if (world.getBlockState(pos.relative(Direction.EAST)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.EAST_WALL, WallSide.LOW);
                }
                if (world.getBlockState(pos.relative(Direction.WEST)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.WEST_WALL, WallSide.LOW);
                }
                world.setBlock(pos, state, 3 | 16);
            }
        }
    }

    // >0: R is on left side of vector AB, <0 R is on right side of vector AB
    public static double getSide(Vec3 a, Vec3 b, Vec3 r) {
        Vec3 AB = b.subtract(a), AR = r.subtract(a);
        return AB.x() * AR.z() - AB.z() * AR.x();
    }

    public static boolean buildRails(
            ArrayList<BlockPos> startPositions, ArrayList<BlockPos> endPositions,
            Player player, ServerLevel world, RailBuilderConfig config) {
        int count = startPositions.size();
        UUID uuid = player.getUUID();
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
                rails[i] = CreateIntegration.connectRailNodes(player, world, pos1, pos2);
            } else {
                rails[i] = null;
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

        // Build catenary
        boolean failToPlaceCatenaryNode = false;
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
                if (config.useCatenary) {
                    if (config.isVanillaCatenary) {
                        lastCatenaryNode = addVanillaCatenaryNode(world, center, tangent, isRightest[i], count, config.railSpacing,
                                lastCatenaryNode, config.tunnelHeight - 1,
                                thisUbm == BuildingMode.Up.Tunnel ? config.catenaryTunnelPillar : config.catenaryBridgePillar,
                                config.catenaryBlock);
                    } else if (CommonUtil.isMtrLoaded() && CommonUtil.isMsdLoaded()){
                        if (isLeftest[i] || isRightest[i]) {
                            CatenaryTypeMapping type = CatenaryTypeMapping.values()[config.catenaryModeIndex];
                            ResourceLocation pillarBlock = thisUbm == BuildingMode.Up.Tunnel ? config.catenaryTunnelPillar : config.catenaryBridgePillar;

                            if (type == CatenaryTypeMapping.Auto) {
                                if (thisUbm == BuildingMode.Up.Tunnel) {
                                    pillarBlock = ResourceLocation.fromNamespaceAndPath("msd", "rigid_catenary_node");
                                } else {
                                    pillarBlock = ResourceLocation.fromNamespaceAndPath("msd", "catenary_with_long");
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
                    }
                }
            }
        }
        return failToPlaceCatenaryNode;
    }
}
