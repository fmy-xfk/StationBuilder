package cn.myfrank.stationbuilder.generator;


import cn.myfrank.stationbuilder.elements.*;
import cn.myfrank.stationbuilder.mtr.MSDIntegration;
import cn.myfrank.stationbuilder.mtr.MTRIntegration;
import cn.myfrank.stationbuilder.utils.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.WallShape;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

public class RailGenerator {
    private static final double EPS = 1e-6;
    public static void placeFirstRailNode(ServerWorld world, BlockPos pos, PlayerEntity player) {
        if (CommonUtil.isMtrLoaded()){
            if (!MTRIntegration.isRailNode(world, pos)) {
                MTRIntegration.placeRailNode(world, pos, player.getYaw());
            } else {
                System.out.println("Position is already a rail node: " + pos);
            }
        }
    }

    public static ArrayList<BlockPos> calcRailNodes(BlockPos pos, float yaw, RailBuilderConfig config) {
        final ArrayList<BlockPos> placedPositions = new ArrayList<>();
        if (CommonUtil.isMtrLoaded()){
            Vec3d normal = RailMath.normalFromYaw(yaw);

            int count = config.railCount;
            double spacing = config.railSpacing;

            for (int i = 0; i < count; i++) {
                double offsetIndex = i - (count - 1) / 2.0;
                Vec3d offset = normal.multiply(offsetIndex * spacing);
                BlockPos s = RailMath.offsetPos(pos, offset);
                placedPositions.add(s);
            }
            return placedPositions;
        }
        return null;
    }

    public static ArrayList<BlockPos> placeFirstRailNodes(ServerWorld world, BlockPos pos, PlayerEntity player, RailBuilderConfig config) {
        ArrayList<BlockPos> nodes = calcRailNodes(pos, player.getYaw(), config);
        if (nodes != null) {
            for (BlockPos p : nodes) {
                placeFirstRailNode(world, p, player);
            }
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
        if (!CommonUtil.isMtrLoaded()) return null;

        float yaw = player.getYaw();
        Vec3d normal = RailMath.normalFromYaw(yaw); // 右侧法向量
        int count = config.railCount;
        double spacing = config.railSpacing;

        // Calculate end positions
        ArrayList<BlockPos> endPositions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double offsetIndex = i - (count - 1) / 2.0;
            Vec3d offset = normal.multiply(offsetIndex * spacing);
            BlockPos e = RailMath.offsetPos(endPos, offset);
            endPositions.add(e);
        }

        // Adjust positions
        RailMath.adjustPointSequence(startPositions, endPositions);

        if (startPositions.size() != endPositions.size()) {
            // 清除该玩家的轨道建造状态（服务端清除）
            ItemStack stack = player.getMainHandStack();
            RailBuilderState.clear(stack);
            stack.getOrCreateNbt().remove("CustomData"); // 若有其他标记
            return null;
        }

        // Place BlockNodes
        boolean anySuccess = false;
        for (int i = 0; i < count; i++) {
            BlockPos s = startPositions.get(i);
            BlockPos e = endPositions.get(i);
            if(MTRIntegration.isRailNode(world, s)) {
                boolean success = true;
                if (s.equals(e)) continue;
                if (!MTRIntegration.isRailNode(world, e)) {
                    MTRIntegration.placeRailNode(world, e, player.getYaw());
                }
                anySuccess |= success;
            } else {
                System.out.println("Start position is not a valid rail node: " + s);
            }
        }

        // Validate rail type
        if (!MTRIntegration.isValidRailType(config.railType)) {
            player.sendMessage(Text.translatable("message.stationbuilder.rail_builder.invalid_rail",
                    config.railType.toString()), true);
            config.railType = MTRIntegration.getDefaultRailType();
        }

        // Build rails
        TickScheduler.schedule(1, () -> {
            var failToPlaceCatenaryNode = RailGenerator.buildRails(startPositions, endPositions, player.getUuid(), world, config);
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
    private static void clearBlock(ServerWorld world, BlockPos pos, boolean includeCatenary) {
        if(CommonUtil.isMsdLoaded()) {
            if (MSDIntegration.isCatenaryNode(world, pos)) {
                if (includeCatenary) {
                    MSDIntegration.clearCatenary(world, pos);
                } else {
                    return;
                }
            }
        }
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    public static Direction horizontalDirectionFromVec(Vec3d v) {
        double x = v.x, z = v.z;
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private static BlockPos addCatenaryNode(
            ServerWorld world, Vec3d center, Vec3d tangent, boolean isLeftest, boolean isRightest,
            @Nullable BlockPos lastCatenaryNode, Identifier block, CatenaryTypeMapping type, int height
    ) {
        Direction dir = horizontalDirectionFromVec(tangent);
        double dirAngle = MTRIntegration.getAngleFromVec3d(tangent);
        int blockY = (int) Math.floor(center.getY());
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));
        if (isLeftest || isRightest) {
            if (isLeftest) {
                dir = dir.rotateYClockwise();
            } else {
                dir = dir.rotateYCounterclockwise();
            }
            if (!MSDIntegration.placeCatenaryNode(world, catenaryPos, dir, dirAngle, block)){
                return null;
            }
            if (lastCatenaryNode != null) {
                MSDIntegration.connectCatenary(world, lastCatenaryNode, catenaryPos, type);
            }
            return catenaryPos;
        }
        return null;
    }

    private static void drawLine(ServerWorld world, BlockPos a, BlockPos b, Identifier lineBlock) {
        var state = Registries.BLOCK.get(lineBlock).getDefaultState();
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
        var state = Registries.BLOCK.get(pillarBlock).getDefaultState();
        BlockPos pos = topPos;
        while(world.isInBuildLimit(pos) && CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlockState(pos, state, 3);
            pos = pos.offset(Direction.DOWN);
        }
    }

    private static void setBlockIfEmpty(ServerWorld world, BlockPos pos, net.minecraft.block.BlockState state) {
        if (world.getBlockState(pos).isAir() || CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlockState(pos, state, 3);
        }
    }

    private static BlockPos addVanillaCatenaryNode(
            ServerWorld world, Vec3d center, Vec3d tangent, boolean isRightest, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height, Identifier pillarBlock, Identifier lineBlock
    ) {
        int blockY = (int) Math.floor(center.y);
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));

        if (isRightest) {
            var trussPos = catenaryPos.up();
            Vec3d normal = new Vec3d(-tangent.z, 0, tangent.x).normalize();

            if (trackCount == 1) {
                // 单条轨道只在最右侧放置 L 型支架
                BlockPos rightEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * 3.0),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * 3.0)
                );
                drawLine(world, trussPos, rightEnd, pillarBlock);
                buildPillarDown(world, rightEnd.down(), pillarBlock);
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
                buildPillarDown(world, rightEnd.down(), pillarBlock);
                buildPillarDown(world, leftEnd.down(), pillarBlock);
            }
        }

        // 每条轨道沿着轨道延伸接触线（蜘蛛网或铁栏杆）
        if (lastCatenaryNode != null) {
            drawLine(world, lastCatenaryNode, catenaryPos, lineBlock);
        }

        return catenaryPos;
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
                    if (!MTRIntegration.isRailNode(world, pos)) {
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
                    if (!MTRIntegration.isRailNode(world, pos)) {
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
                world.setBlockState(pos, Registries.BLOCK.get(config.ballastBlock).getDefaultState(), 3);
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
                var bridgeBlockState = Registries.BLOCK.get(config.bridgeBlock).getDefaultState();
                if ((isLeftest && i == 0) || (isRightest && i == bridgeXZs.size() - 1)) {
                    var pos = new BlockPos(x, blockY - 1, z);
                    if (!MTRIntegration.isRailNode(world, pos)) {
                        world.setBlockState(pos, bridgeBlockState, 3);
                    }
                    var posU = new BlockPos(x, blockY, z);
                    if (!MTRIntegration.isRailNode(world, posU)) {
                        world.setBlockState(posU, Registries.BLOCK.get(config.bridgeGuardRailBlock).getDefaultState(), 3);
                        overpass_walls.add(posU);
                    }
                } else {
                    var posD = new BlockPos(x, blockY - 2, z);
                    world.setBlockState(posD, bridgeBlockState, 3);
                    var pos = new BlockPos(x, blockY - 1, z);
                    world.setBlockState(pos, Registries.BLOCK.get(config.ballastBlock).getDefaultState(), 3);
                    var posU = new BlockPos(x, blockY, z);
                    if (!MTRIntegration.isRailNode(world, posU)) {
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
                                world.setBlockState(pos, Registries.BLOCK.get(config.bridgePillarBlock).getDefaultState(), 3);
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
                if (!MTRIntegration.isRailNode(world, posTop)) {
                    world.setBlockState(posTop, Registries.BLOCK.get(config.tunnelWallBlock).getDefaultState(), 3);
                }
                var posBottom = new BlockPos(x, blockY - 1, z);
                if (!MTRIntegration.isRailNode(world, posBottom)) {
                    world.setBlockState(posBottom, Registries.BLOCK.get(config.ballastBlock).getDefaultState(), 3);
                }
                if ((isLeftest && i == 0) || (isRightest && i == tunnelXZs.size() - 1)) {
                    for(int y = 0; y < config.tunnelHeight; y++) {
                        world.setBlockState(new BlockPos(x, blockY + y, z),
                                Registries.BLOCK.get(config.tunnelWallBlock).getDefaultState(), 3);
                    }
                } else {
                    for(int y = 0; y < config.tunnelHeight; y++) {
                        clearBlock(world, new BlockPos(x, blockY + y, z), clearCatenary);
                    }
                }
            }
        }
        if (ubm != BuildingMode.Up.Tunnel && dbm == BuildingMode.Down.Ballast) {
            var ballastXZs = RailMath.getPositions(center, normal, halfBallastWidth);
            for (var block : ballastXZs) {
                int x = block.x(), z = block.z();
                var pos = new BlockPos(x, blockY - 1, z);
                if (!MTRIntegration.isRailNode(world, pos)) {
                    world.setBlockState(pos, Registries.BLOCK.get(config.ballastBlock).getDefaultState());
                }
            }
        }
        for(var pos: overpass_walls) {
            var state = world.getBlockState(pos);
            if (state.getBlock() instanceof WallBlock) {
                if (world.getBlockState(pos.offset(Direction.NORTH)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.NORTH_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.SOUTH)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.SOUTH_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.EAST)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.EAST_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.WEST)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.WEST_SHAPE, WallShape.LOW);
                }
                world.setBlockState(pos, state, 3 | 16);
            }
        }
    }
    private static Vec3d toVec3d(BlockPos pos) {
        return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
    }

    // >0: R is on left side of vector AB, <0 R is on right side of vector AB
    public static double getSide(Vec3d a, Vec3d b, Vec3d r) {
        Vec3d AB = b.subtract(a);
        Vec3d AR = r.subtract(a);
        return AB.x * AR.z - AB.z * AR.x;
    }

    public static boolean buildRails(
            ArrayList<BlockPos> startPositions, ArrayList<BlockPos> endPositions,
            UUID uuid, ServerWorld world, RailBuilderConfig config) {
        int count = startPositions.size();
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
            var rail = MTRIntegration.connectRailNodes(uuid, world, pos1, pos2, config.railType);
            rails[i] = rail;
            if (rail != null) {
                maxLength = Math.max(maxLength, rail.getLength());
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
                    } else {
                        if (isLeftest[i] || isRightest[i]) {
                            CatenaryTypeMapping type = CatenaryTypeMapping.values()[config.catenaryModeIndex];
                            Identifier pillarBlock = thisUbm == BuildingMode.Up.Tunnel ? config.catenaryTunnelPillar : config.catenaryBridgePillar;

                            if (type == CatenaryTypeMapping.Auto) {
                                if (thisUbm == BuildingMode.Up.Tunnel) {
                                    pillarBlock = new Identifier("msd", "rigid_catenary_node");
                                } else {
                                    pillarBlock = new Identifier("msd", "catenary_with_long");
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

                            lastCatenaryNode = addCatenaryNode(world, center, tangent, isLeftest[i], isRightest[i],
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

    public static void calcBuildingMode(
            CurveData math, ServerWorld world, RailBuilderConfig config, boolean reverseMath, int segments,
            byte[] upBuildingModes, byte[] downBuildingModes
    ) {
        final int thickBallastHeight = config.ballastMaxThickness;
        final double halfTunnelWidth = config.tunnelWidth / 2.0 + EPS;
        final double halfBridgeWidth = config.bridgeWidth / 2.0 + EPS;

        // Determine building modes for upper and lower attachments
        int i = 0;
        for (var pp = new PointProvider(math, segments, reverseMath); pp.notExhausted(); pp.next(), i++) {
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
                    boolean isRailNode = MTRIntegration.isRailNode(world, pos);
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
}
