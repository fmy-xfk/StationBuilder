package cn.myfrank.stationbuilder.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cn.myfrank.stationbuilder.utils.*;
import cn.myfrank.stationbuilder.vanilla.VanillaIntegration;
import cn.myfrank.stationbuilder.cee.CEEIntegration;
import cn.myfrank.stationbuilder.cpw.CPWAngle;
import cn.myfrank.stationbuilder.cpw.CPWIntegration;
import cn.myfrank.stationbuilder.create.CreateIntegration;
import cn.myfrank.stationbuilder.items.RailBuilderConfig;
import cn.myfrank.stationbuilder.items.RailBuilderState;
import cn.myfrank.stationbuilder.manager.PlacerHistoryManager;
import net.minecraft.server.level.ServerPlayer;
import cn.myfrank.stationbuilder.mtr.MSDIntegration;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
            return VanillaIntegration.isRailNode(world, pos);
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

    public static void placeRailNode(ServerLevel world, BlockPos pos, Player player, RailNodeType railNodeType, RailBuilderConfig config, boolean showInfo) {
        if (railNodeType == RailNodeType.MTR && CommonUtil.isMtrLoaded()){
            if (!MTRIntegration.isRailNode(world, pos)) {
                MTRIntegration.placeRailNode(world, pos, player.getYRot());
            } else {
                if(showInfo) System.out.println("Position is already a rail node: " + pos);
            }
        } else if (railNodeType == RailNodeType.CREATE && CommonUtil.isCreateLoaded()) {
            CreateIntegration.placeRailNode(world, pos, player.getYRot(), config.railType);
        } else if (railNodeType == RailNodeType.VANILLA) {
            VanillaIntegration.placeRailNode(world, pos, player.getYRot(), config.railType);
        } else {
            if(showInfo) System.out.println("Rail node type not supported or mod not loaded: " + railNodeType);
        }
    }

    public static void placeFirstRailNode(ServerLevel world, BlockPos pos, Player player, RailNodeType railNodeType, RailBuilderConfig config) {
        placeRailNode(world, pos, player, railNodeType, config, true);
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
        PlacerHistoryManager.Recording recording = beginRailRecording(player, world);
        try {
            ArrayList<BlockPos> nodes = calcRailNodes(pos, player.getYRot(), config);
            RailNodeType railNodeType = getRailNodeType(config);
            for (BlockPos p : nodes) {
                placeFirstRailNode(world, p, player, railNodeType, config);
            }
            return nodes;
        } finally {
            PlacerHistoryManager.finish(recording);
        }
    }

    /** Starts an undo recording for a server-side rail operation (no-op on the client). */
    private static PlacerHistoryManager.Recording beginRailRecording(Player player, ServerLevel world) {
        if (player instanceof ServerPlayer serverPlayer) {
            return PlacerHistoryManager.begin(serverPlayer, world, "rail");
        }
        return null;
    }

    @Nullable
    public static ArrayList<BlockPos> buildRails(
            ServerLevel world,
            ArrayList<BlockPos> startPositions,
            BlockPos endPos,
            RailBuilderConfig config,
            Player player
    ) {
        PlacerHistoryManager.Recording recording = beginRailRecording(player, world);
        boolean deferred = false;
        try {
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
                placeRailNode(world, e, player, railNodeType, config, false);
                anySuccess = true;
            } else {
                System.out.println("Start position is not a valid rail node: " + s);
            }
        }

        // Build rails
        TickScheduler.schedule(1, () -> {
            PlacerHistoryManager.resume(recording);
            var failToPlaceCatenaryNode = false;
            try {
                failToPlaceCatenaryNode = buildRails(startPositions, endPositions, player, world, config);
            } finally {
                PlacerHistoryManager.finish(recording);
            }
            if (failToPlaceCatenaryNode) {
                player.displayClientMessage(Component.translatable("message.stationbuilder.rail_builder.catenary_node_failed"), true);
            }
        });
        deferred = true;

        if(anySuccess) {
            return endPositions;
        } else {
            return null;
        }
        } finally {
            if (deferred) {
                PlacerHistoryManager.suspend(recording);
            } else {
                PlacerHistoryManager.finish(recording);
            }
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

    /**
     * 从 node 方块的中心沿 dir 方向走 distance 格，返回落点所在的方块。
     * 方块中心在 (x + 0.5, z + 0.5)，所以某一轴的步数是
     *   floor(0.5 + distance * |dir|)
     * 而不是 floor(distance * |dir|)。左右两侧使用相同的 |dir| 与 distance，
     * 再按方向取符号，得到的两端严格互为镜像，不会出现一侧多走一格。
     */
    private static BlockPos offsetAlong(BlockPos node, Vec3 dir, double distance) {
        double ax = Math.abs(dir.x);
        double az = Math.abs(dir.z);
        int ox = (int) Math.floor(0.5 + distance * ax + EPS);
        int oz = (int) Math.floor(0.5 + distance * az + EPS);
        return node.offset(dir.x < 0 ? -ox : ox, 0, dir.z < 0 ? -oz : oz);
    }

    /**
     * 多轨龙门架：右端从右轨节点向外走 3 格，左端从左轨节点向外走 3 格。
     * leftNode 是左轨在同一纵向位置的真实节点方块；为 null 时才退回按轨距估算。
     */
    private static void buildGantry(
            ServerLevel world, BlockPos rightNode, @Nullable BlockPos leftNode,
            Vec3 normal, double fallbackSpan, ResourceLocation pillarBlock
    ) {
        BlockPos rightEnd = offsetAlong(rightNode, normal, 3.0);
        BlockPos leftEnd = leftNode != null
                ? offsetAlong(leftNode, normal.scale(-1.0), 3.0)
                : offsetAlong(rightEnd, normal.scale(-1.0), fallbackSpan);

        drawLine(world, rightEnd, leftEnd, pillarBlock);
        buildPillarDown(world, rightEnd.below(), pillarBlock);
        buildPillarDown(world, leftEnd.below(), pillarBlock);
    }

    private static BlockPos addVanillaCatenaryNode(
            ServerLevel world, Vec3 center, Vec3 tangent, @Nullable Vec3 leftCenter,
            boolean isRightest, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height, ResourceLocation pillarBlock, ResourceLocation lineBlock
    ) {
        int blockY = (int) Math.floor(center.y);
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));

        if (isRightest) {
            var trussPos = catenaryPos.above();
            Vec3 normal = new Vec3(-tangent.z, 0, tangent.x).normalize();

            if (trackCount == 1) {
                // 单条轨道只在最右侧放置 L 型支架
                BlockPos rightEnd = offsetAlong(trussPos, normal, 3.0);
                drawLine(world, trussPos, rightEnd, pillarBlock);
                buildPillarDown(world, rightEnd.below(), pillarBlock);
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
        if (!includeCatenary && isCatenary(world, pos)) {
            return;
        }
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
                && config.catenaryTypeIndex == CatenaryType.CPW.ordinal()
                && CommonUtil.isCpwLoaded() && CommonUtil.isCreateLoaded()) {
            buildCpwHeadspanCatenary(
                    world, player, config, pps, count, segments, catenarySegCount,
                    catenarySpacing, ubm, isRightest, leftmostIndex
            );
            return failToPlaceCatenaryNode;
        }

        for (int i = 0; i < count; i++) {
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
                Vec3 leftCenter = (count > 1 && isRightest[i] && leftmostIndex >= 0)
                        ? pps[leftmostIndex].get(j).get(0)
                        : null;
                if (config.catenaryTypeIndex == CatenaryType.Vanilla.ordinal() ) {
                    lastCatenaryNode = addVanillaCatenaryNode(world, center, tangent, leftCenter,
                            isRightest[i], count, config.railSpacing,
                            lastCatenaryNode, config.tunnelHeight - 1,
                            thisUbm == BuildingMode.Up.Tunnel ? config.catenaryTunnelPillar : config.catenaryBridgePillar,
                            config.catenaryBlock);
                } else if (config.catenaryTypeIndex == CatenaryType.MSD.ordinal() && CommonUtil.isMtrLoaded() && CommonUtil.isMsdLoaded()){
                    if (isLeftest[i] || isRightest[i]) {
                        CatenaryTypeMapping type = CatenaryTypeMapping.values()[config.msdModeIndex];
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
                } else if (config.catenaryTypeIndex == CatenaryType.CEE.ordinal() && CommonUtil.isCeeLoaded() && CommonUtil.isCreateLoaded()) {
                    lastCatenaryNode = addCEECatenaryNode(
                            world, center, tangent, leftCenter, isLeftest[i], isRightest[i],
                            i, count, config.railSpacing,
                            lastCatenaryNode, config.tunnelHeight - 1,
                            config.catenaryBridgePillar, config.catenaryTunnelPillar, config.catenaryBlock,
                            player
                    );
                } else if (config.catenaryTypeIndex == CatenaryType.CPW.ordinal() && CommonUtil.isCpwLoaded() && CommonUtil.isCreateLoaded()) {
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
            ServerLevel world, Vec3 center, Vec3 tangent, @Nullable Vec3 leftCenter,
            boolean isLeftest, boolean isRightest,
            int trackIndex, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height,
            ResourceLocation gantryPillarBlock, ResourceLocation dropperPillarBlock,
            ResourceLocation spoolItem, Player player
    ) {
        int blockY = (int) Math.floor(center.y);
        int catenaryY = blockY + height;
        BlockPos catenaryPos = new BlockPos((int) Math.floor(center.x), catenaryY, (int) Math.floor(center.z));
        
        // ========================================================
        // 2. 正常生成新的接触网支柱（横梁高度 = catenaryY + 3）
        // ========================================================
        int trussY = catenaryY + 3;
        Vec3 normal = new Vec3(-tangent.z, 0, tangent.x).normalize();

        if (trackCount <= 2) {
            BlockPos poleNode = new BlockPos(catenaryPos.getX(), trussY - 1, catenaryPos.getZ());
            // 双轨时左轨在外侧（左侧）立柱
            if (trackCount == 2 && isLeftest) {
                BlockPos poleTop = offsetAlong(poleNode, normal.scale(-1.0), 3.0);
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
                Vec3 dropPosVec = dropOnLeft ? center.subtract(normal.scale(dropOffset)) : center.add(normal.scale(dropOffset));

                int dropX = (int) Math.floor(dropPosVec.x);
                int dropZ = (int) Math.floor(dropPosVec.z);
                var dropState = CommonUtil.getBlockState(dropperPillarBlock);

                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos dropPos = new BlockPos(dropX, trussY - dy, dropZ);
                    world.setBlock(dropPos, dropState, 3);
                }
            }
        }

        // 3. 放置新的 catenary_holder 节点
        CEEIntegration.placeCatenaryHolder(world, catenaryPos);

        // 4. 连接导线（从上一个节点连接到新节点）
        if (lastCatenaryNode != null) {
            CEEIntegration.connectWire(world, player, lastCatenaryNode, catenaryPos, spoolItem);
        }

        return catenaryPos;
    }

    private static BlockPos addCPWCatenaryNode(
            ServerLevel world, Vec3 center, Vec3 tangent, boolean isLeftest, boolean isRightest,
            int trackIndex, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height,
            ResourceLocation mastBlock, ResourceLocation cantileverBlock,
            ResourceLocation wireCoilItem, Player player, boolean isTunnel
    ) {
        int blockY = (int) Math.floor(center.y);
        int catenaryY = blockY + height;

        // 腕臂/下横跨承力索基准安装高度
        int cantileverY = catenaryY + 2;

        // 1. 立柱朝向：对齐铁轨切线方向
        CPWAngle mastAngle = CPWIntegration.getCPWAngles(tangent);
        Vec3 normal = new Vec3(-tangent.z, 0, tangent.x).normalize();

        BlockPos placedCantileverPos = null;

        if (trackCount <= 2) {
            // ========================================================
            // 1~2 股道：标准侧边 Mast + 悬臂伸入轨道
            // ========================================================
            boolean isSideMast = (trackCount == 2 && isLeftest) || isRightest;
            if (isSideMast) {
                boolean onRight = isRightest;
                Vec3 poleVec = onRight ? center.add(normal.scale(3)) : center.subtract(normal.scale(3));
                int poleX = (int) Math.floor(poleVec.x);
                int poleZ = (int) Math.floor(poleVec.z);
                BlockPos poleAtCantileverHeight = new BlockPos(poleX, cantileverY, poleZ);

                if (isTunnel) {
                    // 隧道：Mast 从天花板向下延伸
                    int ceilingY = cantileverY + 2;
                    while (ceilingY <= cantileverY + 6 && !world.isOutsideBuildHeight(new BlockPos(poleX, ceilingY, poleZ))) {
                        if (!CommonUtil.isSoftTransparent(world.getBlockState(new BlockPos(poleX, ceilingY, poleZ)))) {
                            break;
                        }
                        ceilingY++;
                    }
                    CPWIntegration.ensureFullBlockBelow(world, poleX, cantileverY - 1, poleZ);
                    CPWIntegration.buildMastColumn(world, poleX, poleZ, cantileverY - 1, Math.max(cantileverY, ceilingY - 1), mastAngle, mastBlock);
                } else {
                    // 露天与桥梁：Mast 向上延伸至 cantileverY + 1，向下打地基
                    int topY = cantileverY + 1;
                    int bottomY = blockY;
                    BlockPos p = new BlockPos(poleX, blockY, poleZ);
                    while (!world.isOutsideBuildHeight(p) && CommonUtil.isSoftTransparent(world.getBlockState(p))) {
                        bottomY = p.getY();
                        p = p.below();
                    }
                    CPWIntegration.ensureFullBlockBelow(world, poleX, bottomY, poleZ);
                    CPWIntegration.buildMastColumn(world, poleX, poleZ, bottomY, topY, mastAngle, mastBlock);
                }

                // 悬臂朝向：严格垂直于立柱伸向轨道（右侧立柱逆时针 90°，左侧立柱顺时针 90°）
                CPWAngle cantileverAngle = onRight ? mastAngle.getCounterClockWise() : mastAngle.getClockWise();
                placedCantileverPos = cantileverAngle.offset(poleAtCantileverHeight);
                CPWIntegration.placeCantilever(world, placedCantileverPos, cantileverAngle, cantileverBlock);
            }
        } else {
            // 不支持多股道模式下的 CPW 接触网生成
        }

        if (placedCantileverPos == null) {
            placedCantileverPos = new BlockPos((int) Math.floor(center.x), cantileverY, (int) Math.floor(center.z));
        }

        // 3. 沿轨道纵向连接导线（从上一个横截面的接触网挂点连接到本次挂点）
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
        final int nodeY;
        final float upperHeight;
        final float topHeight;
        final List<CPWIntegration.HeadspanDropper> droppers;
        final UUID[] dropperIds;
        final double[] fractions;

        private CpwHeadspanFrame(
                BlockPos mastA, BlockPos mastB, int nodeY,
                float upperHeight, float topHeight,
                List<CPWIntegration.HeadspanDropper> droppers,
                UUID[] dropperIds, double[] fractions
        ) {
            this.mastA = mastA;
            this.mastB = mastB;
            this.nodeY = nodeY;
            this.upperHeight = upperHeight;
            this.topHeight = topHeight;
            this.droppers = droppers;
            this.dropperIds = dropperIds;
            this.fractions = fractions;
        }
    }

    /**
     * CPW 多股道并行：每榀在左右外侧各立一根 headspan 支柱，
     * 在两根支柱之间创建 CATENARY_HEADSPAN，并按股道位置创建 dropper
     * 和 registration arm，最后沿股道连接 CATENARY_WIRE。
     */
    private static void buildCpwHeadspanCatenary(
            ServerLevel world, Player player, RailBuilderConfig config,
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

        ResourceLocation mastId = CPWIntegration.resolveHeadspanMastId(config.catenaryBridgePillar);
        ResourceLocation cantileverId = config.catenaryTunnelPillar;
        int catenaryHeight = config.tunnelHeight - 1;

        List<CpwHeadspanFrame> frames = new ArrayList<>();

        for (int k = 0; k <= catenarySegCount; k++) {
            int j = k < catenarySegCount ? (int) Math.floor(k * catenarySpacing) : segments;
            BuildingMode.Up up = BuildingMode.Up.fromValue(ubm[j]);
            if (up == BuildingMode.Up.Tunnel) {
                // 隧道内没有空间立高大的 headspan 支柱，跳过该榀
                continue;
            }

            var leftTuple = pps[leftmostIndex].get(j);
            var rightTuple = pps[rightmostIndex].get(j);
            Vec3 leftCenter = leftTuple.get(0);
            Vec3 rightCenter = rightTuple.get(0);
            Vec3 tangent = rightTuple.get(1);
            Vec3 normal = new Vec3(-tangent.z, 0, tangent.x).normalize();

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

            BlockPos leftPole = offsetAlong(leftBase, normal.scale(-1.0), 3.0);
            BlockPos rightPole = offsetAlong(rightBase, normal, 3.0);
            int nodeY = Math.max(leftBase.getY(), rightBase.getY()) + catenaryHeight + 1;

            Vec3 mastAWorld = new Vec3(leftPole.getX() + 0.5, nodeY + 0.5, leftPole.getZ() + 0.5);
            Vec3 mastBWorld = new Vec3(rightPole.getX() + 0.5, nodeY + 0.5, rightPole.getZ() + 0.5);
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
                Vec3 center = pps[i].get(j).get(0);
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
                    nodeY, upperHeight, topHeight, droppers, dropperIds, fractions
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
            ServerLevel world, BlockPos pole, int blockY, int nodeY, int mastTopY,
            CPWAngle angle, ResourceLocation mastId
    ) {
        int bottomY = blockY;
        BlockPos p = new BlockPos(pole.getX(), blockY, pole.getZ());
        while (!world.isOutsideBuildHeight(p) && CommonUtil.isSoftTransparent(world.getBlockState(p))) {
            bottomY = p.getY();
            p = p.below();
        }
        int topY = Math.max(mastTopY, nodeY + 1);
        CPWIntegration.ensureFullBlockBelow(world, pole.getX(), bottomY, pole.getZ());
        CPWIntegration.buildMastColumn(world, pole.getX(), pole.getZ(), bottomY, topY, angle, mastId);
    }

    public static boolean isCatenary(Level world, BlockPos pos) {
        var state = world.getBlockState(pos);
        if (state.isAir()) return false;
        var id = CommonUtil.getBlockId(state.getBlock());
        if (id == null) return false;

        // CEE 节点与杆件
        if (CommonUtil.isCeeLoaded()) {
            if (id.equals(CEEIntegration.CATENARY_HOLDER)
                    || id.getPath().contains("catenary") || id.getPath().contains("pole")) {
                return true;
            }
        }
        // CPW 节点与杆件（涵盖 mast、cantilever 等）
        if (CommonUtil.isCpwLoaded()) {
            if (id.getNamespace().equals("pantographsandwires")
                    || id.getPath().contains("mast") || id.getPath().contains("cantilever") || id.getPath().contains("pillar")) {
                return true;
            }
        }
        // MSD 节点
        if (CommonUtil.isMsdLoaded() && world instanceof ServerLevel serverLevel) {
            if (MSDIntegration.isCatenaryNode(serverLevel, pos)) {
                return true;
            }
        }
        return false;
    }
}
